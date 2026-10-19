package org.piramalswasthya.stoptb.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao

internal data class CampConnectionSnapshot(
    val hub: String, val current: CampConnectionObservation?, val currentSession: Boolean,
    val connects: Long, val disconnects: Long, val unreachable: Long,
    val lastConnect: Long?, val lastDisconnect: Long?, val lastReason: String?, val lastHub: String?,
    val since: Long?, val events: List<CampConnectionObservation>, val available: Boolean,
    val hubChanges: Long = 0, val hubs: List<CampHubSummary> = emptyList(),
    val changes: List<CampHubChange> = emptyList(), val hubTrackingSince: Long? = null
)

/** Serial, off-main diagnostic writes only. Never changes connection flags or sync scheduling. */
internal object CampConnectionDiagnostics {
    private const val DEVICE_SCOPE = "DEVICE"
    private val session = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "camp-diagnostics").apply { isDaemon = true } }
    private var helper: Journal? = null
    @Volatile private var writeFailed = false
    private val queued = linkedMapOf<String, CampConnectionObservation>()
    fun failure(context: Context, hub: String?, error: Throwable, source: String) {
        if (error is kotlinx.coroutines.CancellationException || error is InterruptedException ||
            (error is java.io.InterruptedIOException && error !is java.net.SocketTimeoutException)) return
        observe(context, hub, false, campConnectionFailure(error), source)
    }
    private fun journal(context: Context) = helper ?: Journal(context.applicationContext).also { helper = it }
    fun addressChanged(context: Context, old: String?, new: String?) {
        runCatching {
            val to = campDiagnosticHub(new)
            if (!to.startsWith("http://") && !to.startsWith("https://")) return
            val app = context.applicationContext
            val scope = DEVICE_SCOPE
            val at = System.currentTimeMillis()
            executor.execute {
                runCatching {
                    val db = journal(app).writableDatabase
                    db.beginTransaction()
                    try {
                        val retained = db.rawQuery("SELECT hub FROM configured_hub WHERE id=1", null).use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                        val from = campPreviousHub(old, retained)
                        val changed = campHubAddressChanged(from, to)
                        val day = syncDay(at)
                        ensureHub(db, scope, day, to, at)
                        db.execSQL("INSERT OR REPLACE INTO configured_hub(id,hub) VALUES(1,?)", arrayOf(to))
                        if (changed) {
                            ensureHub(db, scope, day, from, at)
                            db.execSQL("INSERT INTO address_changes(scope,day,at,from_hub,to_hub) VALUES(?,?,?,?,?)", arrayOf(scope, day, at, from, to))
                            db.execSQL("INSERT OR IGNORE INTO address_daily(scope,day,changes) VALUES(?,?,0)", arrayOf(scope, day))
                            db.execSQL("UPDATE address_daily SET changes=changes+1 WHERE scope=? AND day=?", arrayOf(scope, day))
                            db.execSQL("DELETE FROM address_changes WHERE scope=? AND id NOT IN (SELECT id FROM address_changes WHERE scope=? ORDER BY id DESC LIMIT 200)", arrayOf(scope, scope))
                        }
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }.onFailure { writeFailed = true }
            }
        }
    }

    private fun ensureHub(db: SQLiteDatabase, scope: String, day: String, hub: String, at: Long) {
        if (!hub.startsWith("http://") && !hub.startsWith("https://")) return
        db.execSQL("INSERT OR IGNORE INTO hub_tracking(scope,at) VALUES(?,?)", arrayOf(scope, at))
        db.execSQL("INSERT OR IGNORE INTO hub_daily(scope,day,hub,seen_at) VALUES(?,?,?,?)", arrayOf(scope, day, hub, at))
        db.execSQL("UPDATE hub_daily SET seen_at=MAX(seen_at,?) WHERE scope=? AND day=? AND hub=?", arrayOf(at, scope, day, hub))
    }

    fun observe(context: Context, rawHub: String?, connected: Boolean, reason: String, source: String) {
        runCatching {
            val app = context.applicationContext
            val scope = DEVICE_SCOPE
            val event = CampConnectionObservation(campDiagnosticHub(rawHub), connected,
                System.currentTimeMillis(), if (connected) "REACHABLE" else reason, source, session)
            synchronized(queued) {
                val key = "$scope/${event.hub}"
                val previous = queued[key]
                if (previous != null && previous.connected == event.connected && previous.reason == event.reason &&
                    event.at - previous.at in 0L until 1000L) return
                queued[key] = event
                while (queued.size > 50) queued.remove(queued.keys.first())
                executor.execute { runCatching { write(journal(app).writableDatabase, scope, event) }.onFailure { writeFailed = true } }
            }
        }
    }

    private fun write(db: SQLiteDatabase, scope: String, event: CampConnectionObservation) {
        db.beginTransaction()
        try {
            val previous = db.query("state", null, "scope=? AND hub=?", arrayOf(scope, event.hub), null, null, null).use {
                if (it.moveToFirst()) observation(it) else null
            }
            db.execSQL("INSERT OR IGNORE INTO metadata(scope,at) VALUES(?,?)", arrayOf(scope, event.at))
            val day = syncDay(event.at)
            ensureHub(db, scope, day, event.hub, event.at)
            check(db.insertWithOnConflict("state", null, values(scope, event).apply { put("session", session) },
                SQLiteDatabase.CONFLICT_REPLACE) >= 0)
            if (campConnectionTransition(previous, event)) {
                check(db.insert("events", null, values(scope, event).apply { put("day", day) }) >= 0)
                db.execSQL("INSERT OR IGNORE INTO daily(scope,day) VALUES(?,?)", arrayOf(scope, day))
                if (event.connected) db.execSQL("UPDATE daily SET connects=connects+1,last_connect=? WHERE scope=? AND day=?",
                    arrayOf(event.at, scope, day))
                else db.execSQL("UPDATE daily SET disconnects=disconnects+1,unreachable=unreachable+?,last_disconnect=?,last_reason=?,last_hub=? WHERE scope=? AND day=?",
                    arrayOf(if (campNetworkFailure(event.reason)) 1 else 0, event.at, event.reason, event.hub, scope, day))
                db.execSQL("UPDATE hub_daily SET connects=connects+?,disconnects=disconnects+?,unreachable=unreachable+? WHERE scope=? AND day=? AND hub=?",
                    arrayOf(if (event.connected) 1 else 0, if (event.connected) 0 else 1,
                        if (!event.connected && campNetworkFailure(event.reason)) 1 else 0, scope, day, event.hub))
                db.execSQL("DELETE FROM events WHERE scope=? AND id NOT IN (SELECT id FROM events WHERE scope=? ORDER BY id DESC LIMIT 200)", arrayOf(scope, scope))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun values(scope: String, event: CampConnectionObservation) = ContentValues().apply {
        put("scope", scope); put("hub", event.hub); put("connected", if (event.connected) 1 else 0)
        put("at", event.at); put("reason", event.reason); put("source", event.source)
    }
    private fun observation(cursor: Cursor) = CampConnectionObservation(
        cursor.getString(cursor.getColumnIndexOrThrow("hub")), cursor.getInt(cursor.getColumnIndexOrThrow("connected")) == 1,
        cursor.getLong(cursor.getColumnIndexOrThrow("at")), cursor.getString(cursor.getColumnIndexOrThrow("reason")),
        cursor.getString(cursor.getColumnIndexOrThrow("source")),
        cursor.getColumnIndex("session").let { if (it < 0) "" else cursor.getString(it) })

    suspend fun snapshot(context: Context, filter: SyncDayFilter): CampConnectionSnapshot = suspendCancellableCoroutine { continuation ->
        val app = context.applicationContext
        val pref = PreferenceDao(app)
        val hub = campDiagnosticHub(runCatching { pref.getStoredCampHubUrl() }.getOrNull())
        val scope = DEVICE_SCOPE
        val day = selectedSyncDay(filter, System.currentTimeMillis())
        executor.execute {
            val snapshot = runCatching {
                check(!writeFailed)
                val db = journal(app).readableDatabase
                val where = "scope=?" + if (day == null) "" else " AND day=?"
                val args = if (day == null) arrayOf(scope) else arrayOf(scope, day)
                val current = db.query("state", null, "scope=? AND hub=?", arrayOf(scope, hub), null, null, null).use {
                    if (it.moveToFirst()) observation(it) else null
                }
                val counts = db.rawQuery("SELECT COALESCE(SUM(connects),0),COALESCE(SUM(disconnects),0),COALESCE(SUM(unreachable),0),MAX(last_connect),MAX(last_disconnect) FROM daily WHERE $where", args).use {
                    it.moveToFirst(); (0..4).map { index -> it.getLong(index) }
                }
                val last = db.query("daily", arrayOf("last_reason", "last_hub"), "$where AND disconnects>0", args,
                    null, null, "last_disconnect DESC", "1").use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
                val events = mutableListOf<CampConnectionObservation>()
                db.query("events", null, where, args, null, null, "id DESC", "200").use {
                    while (it.moveToNext()) events.add(observation(it))
                }
                val since = db.query("metadata", arrayOf("at"), "scope=?", arrayOf(scope), null, null, null).use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }
                val hubs = mutableListOf<CampHubSummary>()
                db.rawQuery("SELECT hub,SUM(connects),SUM(disconnects),SUM(unreachable),MAX(seen_at),MAX(partial) FROM hub_daily WHERE $where GROUP BY hub ORDER BY MAX(seen_at) DESC", args).use {
                    while (it.moveToNext()) hubs.add(CampHubSummary(it.getString(0), it.getLong(1), it.getLong(2), it.getLong(3), it.getLong(4), it.getInt(5) == 1))
                }
                val hubChanges = db.rawQuery("SELECT COALESCE(SUM(changes),0) FROM address_daily WHERE $where", args).use {
                    it.moveToFirst(); it.getLong(0)
                }
                val changes = mutableListOf<CampHubChange>()
                db.query("address_changes", arrayOf("at", "from_hub", "to_hub"), where, args, null, null, "id DESC", "200").use {
                    while (it.moveToNext()) changes.add(CampHubChange(it.getLong(0), it.getString(1), it.getString(2)))
                }
                val hubSince = db.query("hub_tracking", arrayOf("at"), "scope=?", arrayOf(scope), null, null, null).use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }
                CampConnectionSnapshot(hub, current, current?.session == session, counts[0], counts[1], counts[2],
                    counts[3].takeIf { it > 0 }, counts[4].takeIf { it > 0 }, last?.first, last?.second, since, events, true,
                    hubChanges, hubs, changes, hubSince)
            }.getOrElse { CampConnectionSnapshot(hub, null, false, 0, 0, 0, null, null, null, null, null, emptyList(), false) }
            if (continuation.isActive) continuation.resume(snapshot)
        }
    }

    private class Journal(context: Context) : SQLiteOpenHelper(context, "camp-connection-diagnostics.db", null, 3) {
        private fun createDeviceTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS configured_hub(id INTEGER PRIMARY KEY CHECK(id=1),hub TEXT NOT NULL)")
        }
        private fun createHubTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hub_daily(scope TEXT NOT NULL,day TEXT NOT NULL,hub TEXT NOT NULL,connects INTEGER NOT NULL DEFAULT 0,disconnects INTEGER NOT NULL DEFAULT 0,unreachable INTEGER NOT NULL DEFAULT 0,seen_at INTEGER NOT NULL,partial INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(scope,day,hub))")
            db.execSQL("CREATE TABLE IF NOT EXISTS address_changes(id INTEGER PRIMARY KEY AUTOINCREMENT,scope TEXT NOT NULL,day TEXT NOT NULL,at INTEGER NOT NULL,from_hub TEXT NOT NULL,to_hub TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS address_changes_scope ON address_changes(scope,day,id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS address_daily(scope TEXT NOT NULL,day TEXT NOT NULL,changes INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(scope,day))")
            db.execSQL("CREATE TABLE IF NOT EXISTS hub_tracking(scope TEXT PRIMARY KEY,at INTEGER NOT NULL)")
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE state(scope TEXT NOT NULL,hub TEXT NOT NULL,connected INTEGER NOT NULL,at INTEGER NOT NULL,reason TEXT NOT NULL,source TEXT NOT NULL,session TEXT NOT NULL,PRIMARY KEY(scope,hub))")
            db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,scope TEXT NOT NULL,day TEXT NOT NULL,hub TEXT NOT NULL,connected INTEGER NOT NULL,at INTEGER NOT NULL,reason TEXT NOT NULL,source TEXT NOT NULL)")
            db.execSQL("CREATE INDEX events_scope ON events(scope,day,id)")
            db.execSQL("CREATE TABLE daily(scope TEXT NOT NULL,day TEXT NOT NULL,connects INTEGER NOT NULL DEFAULT 0,disconnects INTEGER NOT NULL DEFAULT 0,unreachable INTEGER NOT NULL DEFAULT 0,last_connect INTEGER,last_disconnect INTEGER,last_reason TEXT,last_hub TEXT,PRIMARY KEY(scope,day))")
            db.execSQL("CREATE TABLE metadata(scope TEXT PRIMARY KEY,at INTEGER NOT NULL)")
            createHubTables(db)
            createDeviceTables(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createHubTables(db)
                db.execSQL("INSERT OR IGNORE INTO hub_tracking(scope,at) SELECT scope,? FROM metadata", arrayOf(System.currentTimeMillis()))
                // Retained legacy events are a lower bound, never a complete historical breakdown.
                db.execSQL("INSERT INTO hub_daily(scope,day,hub,connects,disconnects,unreachable,seen_at,partial) SELECT scope,day,hub,SUM(connected),SUM(CASE WHEN connected=0 THEN 1 ELSE 0 END),SUM(CASE WHEN connected=0 AND reason IN ('TIMEOUT','HOST_LOOKUP_FAILED','NETWORK_UNREACHABLE','CONNECTION_FAILED','WIFI_UNAVAILABLE') THEN 1 ELSE 0 END),MAX(at),1 FROM events GROUP BY scope,day,hub")
                db.query("state", arrayOf("scope", "hub", "at"), null, null, null, null, null).use {
                    while (it.moveToNext()) db.execSQL("INSERT OR IGNORE INTO hub_daily(scope,day,hub,seen_at,partial) VALUES(?,?,?,?,1)",
                        arrayOf(it.getString(0), syncDay(it.getLong(2)), it.getString(1), it.getLong(2)))
                }
            }
            if (oldVersion < 3) {
                createDeviceTables(db)
                // Merge the recorded village histories once; never reconstruct missing transitions.
                db.execSQL("INSERT INTO state(scope,hub,connected,at,reason,source,session) SELECT 'DEVICE',s.hub,s.connected,s.at,s.reason,s.source,s.session FROM state s WHERE s.rowid=(SELECT t.rowid FROM state t WHERE t.hub=s.hub ORDER BY t.at DESC,t.rowid DESC LIMIT 1)")
                db.execSQL("INSERT INTO daily(scope,day,connects,disconnects,unreachable,last_connect,last_disconnect) SELECT 'DEVICE',day,SUM(connects),SUM(disconnects),SUM(unreachable),MAX(last_connect),MAX(last_disconnect) FROM daily GROUP BY day")
                db.execSQL("UPDATE daily SET last_reason=(SELECT d.last_reason FROM daily d WHERE d.scope<>'DEVICE' AND d.day=daily.day AND d.disconnects>0 ORDER BY d.last_disconnect DESC,d.rowid DESC LIMIT 1),last_hub=(SELECT d.last_hub FROM daily d WHERE d.scope<>'DEVICE' AND d.day=daily.day AND d.disconnects>0 ORDER BY d.last_disconnect DESC,d.rowid DESC LIMIT 1) WHERE scope='DEVICE'")
                db.execSQL("INSERT INTO hub_daily(scope,day,hub,connects,disconnects,unreachable,seen_at,partial) SELECT 'DEVICE',day,hub,SUM(connects),SUM(disconnects),SUM(unreachable),MAX(seen_at),MAX(partial) FROM hub_daily GROUP BY day,hub")
                db.execSQL("INSERT INTO address_daily(scope,day,changes) SELECT 'DEVICE',day,SUM(changes) FROM address_daily GROUP BY day")
                db.execSQL("INSERT INTO metadata(scope,at) SELECT 'DEVICE',MIN(at) FROM metadata HAVING COUNT(*)>0")
                db.execSQL("INSERT INTO hub_tracking(scope,at) SELECT 'DEVICE',MIN(at) FROM hub_tracking HAVING COUNT(*)>0")
                db.execSQL("UPDATE events SET scope='DEVICE'")
                db.execSQL("UPDATE address_changes SET scope='DEVICE'")
                for (table in listOf("state", "daily", "hub_daily", "address_daily", "metadata", "hub_tracking")) {
                    db.delete(table, "scope<>?", arrayOf(DEVICE_SCOPE))
                }
                db.execSQL("INSERT OR IGNORE INTO configured_hub(id,hub) SELECT 1,to_hub FROM address_changes ORDER BY at DESC,id DESC LIMIT 1")
                db.execSQL("INSERT OR IGNORE INTO configured_hub(id,hub) SELECT 1,hub FROM hub_daily WHERE hub LIKE 'http://%' OR hub LIKE 'https://%' ORDER BY seen_at DESC,rowid DESC LIMIT 1")
                for (table in listOf("events", "address_changes")) {
                    db.execSQL("DELETE FROM $table WHERE id NOT IN (SELECT id FROM $table ORDER BY id DESC LIMIT 200)")
                }
            }
        }
    }
}
