package org.piramalswasthya.stoptb.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao

/** Separate diagnostic journal; never migrates or updates the clinical Room database. */
internal object SyncDailyStore {
    @Volatile private var app: Context? = null
    @Volatile private var helper: Journal? = null
    @Volatile private var writeFailed = false
    fun initialize(context: Context) { app = context.applicationContext }
    @Synchronized private fun journal(): Journal? = helper ?: app?.let { Journal(it).also { db -> helper = db } }
    private fun scope(): String? = app?.let { PreferenceDao(it).getLoggedInUser() }
        ?.let { "${it.userId}/${it.serviceMapId}" }
    fun accountScope(context: Context): String? = runCatching {
        PreferenceDao(context.applicationContext).getLoggedInUser()?.let { "${it.userId}/${it.serviceMapId}" }
    }.getOrNull()

    suspend fun record(module: String, ids: Iterable<Any>, action: String, outcome: String,
                       reason: String = "") {
        if (outcome !in setOf("SUCCESS", "FAILURE")) return
        val trace = currentCoroutineContext()[SyncTrace]
        val run = trace?.runId ?: "LOCAL_SAVE"
        withContext(Dispatchers.IO) {
            try {
                val scope = trace?.accountScope ?: scope() ?: return@withContext
                val db = journal()?.writableDatabase ?: return@withContext
                val at = System.currentTimeMillis()
                val day = syncDay(at)
                val keys = ids.map { it.toString() }.filter { it.isNotBlank() && it != "0" }.distinct()
                if (keys.isEmpty()) return@withContext
                if (action == "SAVE" && trace == null) runCatching {
                    timber.log.Timber.tag("SyncTrace").i("LOCAL SAVE | module=%s | records=%d | outcome=%s", module, keys.size, outcome)
                }
                db.beginTransaction()
                try {
                    db.execSQL("INSERT OR IGNORE INTO metadata(scope,started_at) VALUES(?,?)", arrayOf(scope, at))
                    for (id in keys) {
                        val values = ContentValues().apply {
                            put("scope", scope); put("day", day); put("module", module); put("record_id", id)
                            put("action", action); put("outcome", outcome); put("reason", reason.take(160))
                            put("run_id", run); put("at", at)
                        }
                        check(db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0)
                        if (outcome == "SUCCESS") {
                            // Preserve successful observations when a later edit/retry fails on the same day.
                            check(db.insertWithOnConflict("successes", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0)
                        }
                        if (outcome == "FAILURE") {
                            check(db.insertWithOnConflict("record_failures", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0)
                        }
                        if (outcome == "FAILURE" && id == keys.first()) {
                            check(db.insertWithOnConflict("last_failure", null, ContentValues(values).apply { put("record_id", keys.take(20).joinToString(",")) }, SQLiteDatabase.CONFLICT_REPLACE) >= 0)
                        }
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { writeFailed = true }
        }
    }

    suspend fun response(module: String, ids: Iterable<Any>, http: Int, body: String?, action: String = "PUSH"): Boolean {
        val backend = runCatching { body?.let { JSONObject(it).optInt("statusCode", -1) } }.getOrNull()
        if (http != 200 || backend != 200) {
            val auth = runCatching { body?.let { JSONObject(it).optString("errorMessage").contains("401") } == true }.getOrDefault(false)
            val reason = when {
                http == 401 || backend == 401 || backend == 5002 || auth -> "AUTHORIZATION_REJECTED (HTTP=$http, backend=$backend)"
                http != 200 -> "HTTP_REJECTED_$http"
                body == null -> "EMPTY_RESPONSE_BODY"
                backend == null || backend == -1 -> "MALFORMED_RESPONSE"
                else -> "BACKEND_REJECTED_$backend"
            }
            record(module, ids, action, "FAILURE", reason)
            return true
        }
        return false
    }

    suspend fun observedBody(module: String, ids: Iterable<Any>, response: retrofit2.Response<okhttp3.ResponseBody>): String? {
        val body = response.body()?.string()
        this.response(module, ids, response.code(), body)
        return body
    }

    suspend fun alias(module: String, oldId: Any, newId: Any) = withContext(Dispatchers.IO) {
        runCatching {
            val scope = currentCoroutineContext()[SyncTrace]?.accountScope ?: scope() ?: return@runCatching
            journal()?.writableDatabase?.insertWithOnConflict("aliases", null, ContentValues().apply {
                put("scope", scope); put("module", module); put("old_id", oldId.toString()); put("new_id", newId.toString())
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        Unit
    }

    data class Snapshot(val modules: List<SyncDailyModule>, val lastFailure: SyncDailyEvent?, val trackingSince: Long?, val available: Boolean)
    suspend fun snapshot(filter: SyncDayFilter, records: Set<Pair<String, String>>): Snapshot = withContext(Dispatchers.IO) {
        try {
            if (writeFailed) return@withContext Snapshot(emptyList(), null, null, false)
            val scope = scope() ?: return@withContext Snapshot(emptyList(), null, null, false)
            val db = journal()?.readableDatabase ?: return@withContext Snapshot(emptyList(), null, null, false)
            val day = selectedSyncDay(filter, System.currentTimeMillis())
            // Uploads on a later day may resolve a selected day's local-save cohort.
            val where = "scope=? AND action IN ('SAVE','PUSH','REGISTER')"
            val args = arrayOf(scope)
            val events = mutableListOf<SyncDailyEvent>()
            for (table in listOf("successes", "events")) db.query(table, null, where, args, null, null, "at ASC").use { cursor ->
                while (cursor.moveToNext()) events.add(cursorEvent(cursor))
            }
            val failures = mutableListOf<SyncDailyEvent>()
            for (table in listOf("last_failure", "record_failures")) db.query(table, null, "scope=?", arrayOf(scope), null, null, null).use {
                while (it.moveToNext()) failures.add(cursorEvent(it))
            }
            val since = db.query("metadata", arrayOf("started_at"), "scope=?", arrayOf(scope), null, null, null).use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
            val aliases = mutableMapOf<Pair<String, String>, String>()
            db.query("aliases", null, "scope=?", arrayOf(scope), null, null, null).use {
                while (it.moveToNext()) aliases[it.getString(1) to it.getString(2)] = it.getString(3)
            }
            Snapshot(syncDailyModules(villageDailyEvents(events, records, aliases), day),
                villageLastFailure(failures + events, records, aliases), since, true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Snapshot(emptyList(), null, null, false) }
    }
    private fun cursorEvent(c: android.database.Cursor) = SyncDailyEvent(
        c.getString(c.getColumnIndexOrThrow("module")), c.getString(c.getColumnIndexOrThrow("record_id")),
        c.getString(c.getColumnIndexOrThrow("action")), c.getString(c.getColumnIndexOrThrow("outcome")),
        c.getString(c.getColumnIndexOrThrow("reason")), c.getString(c.getColumnIndexOrThrow("run_id")),
        c.getLong(c.getColumnIndexOrThrow("at")), c.getString(c.getColumnIndexOrThrow("day")))
    private class Journal(context: Context) : SQLiteOpenHelper(context, "sync-daily-diagnostics.db", null, 2) {
        private fun createFailureHistory(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS record_failures (scope TEXT NOT NULL,day TEXT NOT NULL,module TEXT NOT NULL,record_id TEXT NOT NULL,action TEXT NOT NULL,outcome TEXT NOT NULL,reason TEXT NOT NULL,run_id TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(scope,module,record_id))")
        }
        override fun onCreate(db: SQLiteDatabase) {
            val columns = "scope TEXT NOT NULL,day TEXT NOT NULL,module TEXT NOT NULL,record_id TEXT NOT NULL,action TEXT NOT NULL,outcome TEXT NOT NULL,reason TEXT NOT NULL,run_id TEXT NOT NULL,at INTEGER NOT NULL"
            for (table in listOf("events", "successes")) {
                db.execSQL("CREATE TABLE $table ($columns,PRIMARY KEY(scope,day,module,record_id,action))")
                db.execSQL("CREATE INDEX ${table}_day ON $table(scope,day)")
            }
            db.execSQL("CREATE TABLE last_failure ($columns,PRIMARY KEY(scope,day))")
            db.execSQL("CREATE TABLE metadata(scope TEXT PRIMARY KEY,started_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE aliases(scope TEXT NOT NULL,module TEXT NOT NULL,old_id TEXT NOT NULL,new_id TEXT NOT NULL,PRIMARY KEY(scope,module,old_id))")
            createFailureHistory(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createFailureHistory(db)
        }
    }
}
