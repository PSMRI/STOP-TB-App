package org.piramalswasthya.stoptb.utils.sync

/**
 * Finds the items a server rejects without holding back the rest of the batch.
 *
 * The batch is sent as one request; if the server rejects it, it is split in
 * halves and each half is sent again, down to single items. Items in accepted
 * requests are returned as sent, the single items still rejected as rejected.
 * Network failures should be thrown by [sendBatch], not returned as false, so the
 * caller can retry the whole run later.
 *
 * Cost: one request when the batch is accepted, at most 2n - 1 when it is not.
 */
object BatchIsolation {

    data class Outcome<T>(val sent: List<T>, val rejected: List<T>) {
        /** Everything was rejected: more likely a server-side problem than bad records. */
        val nothingAccepted: Boolean get() = sent.isEmpty() && rejected.isNotEmpty()
    }

    suspend fun <T> send(items: List<T>, sendBatch: suspend (List<T>) -> Boolean): Outcome<T> {
        if (items.isEmpty()) return Outcome(emptyList(), emptyList())
        if (sendBatch(items)) return Outcome(items, emptyList())
        if (items.size == 1) return Outcome(emptyList(), items)
        val mid = items.size / 2
        val first = send(items.subList(0, mid), sendBatch)
        val second = send(items.subList(mid, items.size), sendBatch)
        return Outcome(first.sent + second.sent, first.rejected + second.rejected)
    }
}
