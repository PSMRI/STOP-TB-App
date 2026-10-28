package org.piramalswasthya.stoptb.database.shared_preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retry state for records the server rejected, kept in the app's encrypted
 * shared preferences (no Room schema change).
 *
 * A record here is never dropped from sync: it stays UNSYNCED in its own table
 * and is retried individually with a growing delay, capped at an hour. The
 * entry is removed as soon as the record syncs. Entries also drive the
 * "needs attention" view and the long-unsynced alert.
 */
@Singleton
class SyncRetryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Entry(
        val entity: String,
        val id: Long,
        val attempts: Int,
        val lastError: String,
        val firstFailedAt: Long,
        val nextRetryAt: Long,
        val lastReportedAt: Long = 0L,
    )

    companion object {
        const val ENTITY_BENEFICIARY = "BENEFICIARY"
        const val ENTITY_REGISTRATION = "REGISTRATION"
        const val ENTITY_TB_DIAGNOSTICS = "TB_DIAGNOSTICS"
        const val ENTITY_TB_SUSPECTED = "TB_SUSPECTED"
        const val ENTITY_TB_CONFIRMED = "TB_CONFIRMED"

        private const val PREF_KEY = "sync_retry_entries"
        private const val MAX_ERROR_LENGTH = 300
        private const val MINUTE = 60_000L

        // Delay before the next individual retry, by number of failed attempts:
        // next cycle, 1 min, 5 min, 15 min, then hourly. There is no attempt limit.
        fun backoffMillis(attempts: Int): Long = when {
            attempts <= 1 -> 0L
            attempts == 2 -> MINUTE
            attempts == 3 -> 5 * MINUTE
            attempts == 4 -> 15 * MINUTE
            else -> 60 * MINUTE
        }
    }

    private val prefs get() = PreferenceManager.getInstance(context)

    @Synchronized
    fun recordFailure(entity: String, id: Long, error: String, now: Long = System.currentTimeMillis()) {
        val entries = load()
        val existing = entries[key(entity, id)]
        val attempts = (existing?.attempts ?: 0) + 1
        entries[key(entity, id)] = Entry(
            entity = entity,
            id = id,
            attempts = attempts,
            lastError = error.take(MAX_ERROR_LENGTH),
            firstFailedAt = existing?.firstFailedAt ?: now,
            nextRetryAt = now + backoffMillis(attempts),
            lastReportedAt = existing?.lastReportedAt ?: 0L,
        )
        save(entries)
    }

    @Synchronized
    fun clear(entity: String, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val entries = load()
        val removed = ids.count { entries.remove(key(entity, it)) != null }
        if (removed > 0) save(entries)
    }

    @Synchronized
    fun isWaiting(entity: String, id: Long, now: Long = System.currentTimeMillis()): Boolean =
        load()[key(entity, id)]?.let { it.nextRetryAt > now } ?: false

    @Synchronized
    fun contains(entity: String, id: Long): Boolean = load().containsKey(key(entity, id))

    /** Makes every entry due now, e.g. when a sync succeeds, the hub reconnects or the user taps Retry. */
    @Synchronized
    fun resetBackoff() {
        val entries = load()
        if (entries.values.none { it.nextRetryAt > 0L }) return
        entries.replaceAll { _, e -> e.copy(nextRetryAt = 0L) }
        save(entries)
    }

    @Synchronized
    fun markReported(entity: String, id: Long, now: Long = System.currentTimeMillis()) {
        val entries = load()
        val e = entries[key(entity, id)] ?: return
        entries[key(entity, id)] = e.copy(lastReportedAt = now)
        save(entries)
    }

    @Synchronized
    fun entries(): List<Entry> = load().values.sortedBy { it.firstFailedAt }

    private fun key(entity: String, id: Long) = "$entity:$id"

    private fun load(): MutableMap<String, Entry> {
        val raw = prefs.getString(PREF_KEY, null) ?: return mutableMapOf()
        return try {
            val array = JSONArray(raw)
            val map = mutableMapOf<String, Entry>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val e = Entry(
                    entity = o.getString("entity"),
                    id = o.getLong("id"),
                    attempts = o.getInt("attempts"),
                    lastError = o.optString("lastError"),
                    firstFailedAt = o.getLong("firstFailedAt"),
                    nextRetryAt = o.getLong("nextRetryAt"),
                    lastReportedAt = o.optLong("lastReportedAt", 0L),
                )
                map[key(e.entity, e.id)] = e
            }
            map
        } catch (e: Exception) {
            Timber.e(e, "SyncRetryStore: unreadable entries, starting fresh")
            mutableMapOf()
        }
    }

    private fun save(entries: Map<String, Entry>) {
        val array = JSONArray()
        entries.values.forEach { e ->
            array.put(
                JSONObject()
                    .put("entity", e.entity)
                    .put("id", e.id)
                    .put("attempts", e.attempts)
                    .put("lastError", e.lastError)
                    .put("firstFailedAt", e.firstFailedAt)
                    .put("nextRetryAt", e.nextRetryAt)
                    .put("lastReportedAt", e.lastReportedAt)
            )
        }
        prefs.edit().putString(PREF_KEY, array.toString()).apply()
    }
}
