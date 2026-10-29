package org.piramalswasthya.stoptb.utils

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * TEMPORARY PERFORMANCE INSTRUMENTATION - remove with the rest of the trace scaffolding.
 *
 * Answers one question: when a list load takes far longer than the query costs, is the time spent
 * doing work or waiting for Room's query executor?
 *
 * It repeatedly runs a query that does no work ("SELECT 1"), so whatever it costs is queueing.
 * Sampled from its own coroutine rather than from inside PagingSource.load() - issuing a second
 * Room query inside a load stopped the delegate's REFRESH from running at all, and the list never
 * populated.
 *
 * Only the samples that exceed [reportThresholdMs] are logged, plus a summary when it stops, so a
 * long run does not drown the log it is meant to explain.
 */
object DbContentionSampler {

    private const val sampleIntervalMs = 250L
    private const val reportThresholdMs = 50L

    private var job: Job? = null

    fun start(scope: CoroutineScope, probe: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            var samples = 0
            var slowSamples = 0
            var totalMs = 0L
            var worstMs = 0L
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val probeStartedAt = SystemClock.elapsedRealtime()
                runCatching { probe() }
                val waitedMs = SystemClock.elapsedRealtime() - probeStartedAt
                samples++
                totalMs += waitedMs
                if (waitedMs > worstMs) worstMs = waitedMs
                if (waitedMs >= reportThresholdMs) {
                    slowSamples++
                    Log.d(ScreenLoadTrace.TAG, "  [db] executor made a no-op query wait ${waitedMs}ms")
                    if (waitedMs >= cullpritDumpThresholdMs) dumpDbThreads(waitedMs)
                }
                if (samples % 40 == 0) {
                    Log.d(
                        ScreenLoadTrace.TAG,
                        "  [db] contention over ${(SystemClock.elapsedRealtime() - startedAt) / 1000}s: " +
                                "$samples samples, mean ${totalMs / samples}ms, worst ${worstMs}ms, " +
                                "$slowSamples over ${reportThresholdMs}ms"
                    )
                }
                delay(sampleIntervalMs)
            }
        }
    }

    /**
     * A wait this long is not scheduling noise, so name whoever is holding the database.
     * Dumps the top frames of every Room query/transaction thread plus anything parked inside
     * SQLite, which is enough to tell a slow query apart from a blocked connection acquire.
     */
    private const val cullpritDumpThresholdMs = 1_000L

    private fun dumpDbThreads(waitedMs: Long) {
        val interesting = Thread.getAllStackTraces().filterKeys { thread ->
            thread.name.startsWith("stoptb-room-") || thread.name.startsWith("arch_disk_io")
        }
        Log.d(ScreenLoadTrace.TAG, "  [db] ---- ${waitedMs}ms wait: ${interesting.size} room threads ----")
        interesting.forEach { (thread, frames) ->
            val top = frames.take(6).joinToString(" <- ") { frame ->
                "${frame.className.substringAfterLast('.')}.${frame.methodName}"
            }
            Log.d(ScreenLoadTrace.TAG, "  [db]   ${thread.name} ${thread.state}: $top")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
