package org.piramalswasthya.stoptb.work

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * When each pending diagnostic order is next worth asking `order/result` about, for the poll's
 * fallback path (hub without a changes feed). With the feed, results arrive with each pull and
 * nothing is polled per order.
 *
 * Each order starts at a base delay for its test (results take minutes for an X-ray, longer for
 * TrueNat and RIF), the delay doubles every time the result comes back unchanged, up to
 * [MAX_DELAY_MS], with +/-20% jitter so a camp's tablets don't poll in lock-step. A change resets
 * it. Kept in memory: after a process restart every order is checked once, then backs off again.
 */
@Singleton
class ResultPollSchedule @Inject constructor() {

    private data class Slot(val nextPollAt: Long, val unchangedCount: Int)

    private val slots = ConcurrentHashMap<String, Slot>()

    private fun key(benId: Long, orderType: String) = "$benId|${orderType.uppercase()}"

    fun isDue(benId: Long, orderType: String, now: Long): Boolean =
        slots[key(benId, orderType)]?.let { slot -> now >= slot.nextPollAt } ?: true

    /** The result is still pending (or the call failed): ask again later, backing off. */
    fun recordUnchanged(benId: Long, orderType: String, now: Long) {
        slots.compute(key(benId, orderType)) { _, previous ->
            val count = (previous?.unchangedCount ?: 0) + 1
            Slot(now + delayFor(orderType, count), count)
        }
    }

    /** The order changed (completed, failed, re-ordered): forget its backoff. */
    fun recordChanged(benId: Long, orderType: String) {
        slots.remove(key(benId, orderType))
    }

    /** Drops orders that are no longer pending. */
    fun retainOnly(pending: Collection<Pair<Long, String>>) {
        val keep = pending.mapTo(HashSet()) { (benId, type) -> key(benId, type) }
        slots.keys.retainAll(keep)
    }

    /** The earliest time any tracked order is due, or null if none is tracked. */
    fun earliestDue(): Long? = slots.values.minOfOrNull { slot -> slot.nextPollAt }

    companion object {
        const val MAX_DELAY_MS = 60 * 60_000L

        fun baseDelayFor(orderType: String): Long = when (orderType.uppercase()) {
            "XRAY_CHEST" -> 2 * 60_000L
            "SPUTUM_TRUENAT", "MTB" -> 10 * 60_000L
            "MDR_RIF" -> 20 * 60_000L
            else -> 10 * 60_000L
        }

        internal fun delayFor(orderType: String, unchangedCount: Int, random: Random = Random.Default): Long {
            val doublings = (unchangedCount - 1).coerceIn(0, 10)
            val delay = (baseDelayFor(orderType) shl doublings).coerceAtMost(MAX_DELAY_MS)
            val jitter = 0.8 + random.nextDouble() * 0.4
            return (delay * jitter).toLong()
        }
    }
}
