package org.piramalswasthya.stoptb.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * TEMPORARY PERFORMANCE INSTRUMENTATION - remove once the slow-screen investigation is closed.
 *
 * Everything is logged under one tag so a single capture shows screen lifecycle, Room flow
 * emissions, Paging loads, API calls and main-thread stalls interleaved on one timeline:
 *
 *     adb logcat -c && adb logcat -s STOPTB_PERF
 *
 * Marks carry two numbers:
 *     +total  ms since the card was tapped
 *     +step   ms since the previous mark on that screen
 *
 * Nothing here changes app behaviour: it reads the clock and writes to logcat.
 */
object ScreenLoadTrace {

    const val TAG = "STOPTB_PERF"

    private val startedAt = HashMap<String, Long>()
    private val lastMarkAt = HashMap<String, Long>()

    /** The card tapped most recently; screens mark against this. */
    @Volatile
    private var currentScreen: String = "unknown"

    /** elapsedRealtime of the most recent card tap, for stamping non-screen events. */
    @Volatile
    private var currentScreenStartedAt: Long = 0L

    private fun now() = SystemClock.elapsedRealtime()

    /** ms since the current screen was opened, or -1 when no screen is being timed. */
    private fun sinceStart(): Long =
        if (currentScreenStartedAt == 0L) -1 else now() - currentScreenStartedAt

    // ── screen lifecycle ──────────────────────────────────────────────────────

    @JvmStatic
    fun start(screen: String) {
        val t = now()
        currentScreen = screen
        currentScreenStartedAt = t
        startedAt[screen] = t
        lastMarkAt[screen] = t
        flowCounts.clear()
        apiCount.set(0)
        apiTotalMs.set(0)
        bindCount.set(0)
        bindTotalUs.set(0)
        Log.d(TAG, "══════════ [$screen] START (card tapped) ══════════")
    }

    @JvmStatic
    fun mark(screen: String, label: String) {
        val t = now()
        val start = startedAt[screen]
        if (start == null) {
            startedAt[screen] = t
            lastMarkAt[screen] = t
            currentScreenStartedAt = t
            Log.d(TAG, "[$screen] $label (no START seen - timing from here)")
            return
        }
        val previous = lastMarkAt[screen] ?: start
        lastMarkAt[screen] = t
        Log.d(TAG, "[$screen] $label  +total=${t - start}ms  +step=${t - previous}ms")
    }

    @JvmStatic
    fun end(screen: String, label: String) {
        val start = startedAt[screen]
        if (start == null) {
            Log.d(TAG, "[$screen] $label (no START seen)")
            return
        }
        val total = now() - start
        Log.d(
            TAG,
            "[$screen] $label  ===> TOTAL ${total}ms <===  " +
                    "(api calls=${apiCount.get()} totalling ${apiTotalMs.get()}ms, " +
                    "row binds=${bindCount.get()} totalling ${bindTotalUs.get() / 1000}ms, " +
                    "flow emissions=${flowCounts.values.sumOf { it.get() }})"
        )
        startedAt.remove(screen)
        lastMarkAt.remove(screen)
    }

    @JvmStatic
    fun markCurrent(label: String) = mark(currentScreen, label)

    @JvmStatic
    fun endCurrent(label: String) = end(currentScreen, label)

    @JvmStatic
    fun isTrackingCurrent() = startedAt.containsKey(currentScreen)

    // ── Room flow emissions ───────────────────────────────────────────────────

    private val flowCounts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * One emission of a Room Flow the open screen collects. The running count is the useful
     * part: a flow emitting repeatedly means its tables are being written repeatedly, and
     * every emission re-runs the query and re-diffs the adapter.
     */
    @JvmStatic
    fun flowEmission(name: String, itemCount: Int) {
        val n = flowCounts.getOrPut(name) { AtomicInteger(0) }.incrementAndGet()
        Log.d(TAG, "  [flow] $name  items=$itemCount  emission#$n  +${sinceStart()}ms")
    }

    // ── Paging ────────────────────────────────────────────────────────────────

    @JvmStatic
    fun pagingSourceCreated(query: String, source: Int, generation: Int) {
        Log.d(
            TAG,
            "  [paging] NEW PagingSource #$generation (query='$query' source=$source) +${sinceStart()}ms"
        )
    }

    @JvmStatic
    fun pagingLoad(
        kind: String,
        durationMs: Long,
        rows: Int,
        invalidated: Boolean,
        queueWaitBeforeMs: Long = -1,
        queueWaitAfterMs: Long = -1
    ) {
        // queueWait* are round trips of a "SELECT 1" taken either side of the real load. They do
        // no work, so whatever they cost is executor contention; durationMs minus that is the
        // load's own query + row-mapping time.
        val wait = if (queueWaitBeforeMs < 0) "" else
            "  [queueWait before=${queueWaitBeforeMs}ms after=${queueWaitAfterMs}ms]"
        Log.d(
            TAG,
            "  [paging] load $kind took ${durationMs}ms rows=$rows$wait" +
                    (if (invalidated) "  *** INVALIDATED - Paging will restart ***" else "") +
                    "  +${sinceStart()}ms"
        )
    }

    // ── API ───────────────────────────────────────────────────────────────────

    private val apiCount = AtomicInteger(0)
    private val apiTotalMs = AtomicLong(0)

    @JvmStatic
    fun apiCall(method: String, path: String, code: Int, durationMs: Long, bytes: Long) {
        val n = apiCount.incrementAndGet()
        apiTotalMs.addAndGet(durationMs)
        Log.d(
            TAG,
            "  [api] #$n $method $path -> $code  ${durationMs}ms  ${bytes}B  +${sinceStart()}ms"
        )
    }

    // ── RecyclerView binds (aggregated; per-bind logging would itself be the bottleneck) ──

    private val bindCount = AtomicInteger(0)
    private val bindTotalUs = AtomicLong(0)
    private var lastBindReportAt = 0L

    @JvmStatic
    fun rowBind(durationUs: Long) {
        bindCount.incrementAndGet()
        bindTotalUs.addAndGet(durationUs)
        val t = now()
        if (t - lastBindReportAt >= 1000L) {
            lastBindReportAt = t
            Log.d(
                TAG,
                "  [ui] row binds so far=${bindCount.get()} totalling ${bindTotalUs.get() / 1000}ms" +
                        "  (avg ${bindTotalUs.get() / maxOf(1, bindCount.get())}us)  +${sinceStart()}ms"
            )
        }
    }

    // ── main-thread stall watchdog ────────────────────────────────────────────

    private const val TICK_MS = 200L
    private const val STALL_THRESHOLD_MS = 500L

    private var watchdogRunning = false
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var expectedTickAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val t = now()
            val overshoot = t - expectedTickAt
            if (expectedTickAt != 0L && overshoot > STALL_THRESHOLD_MS) {
                Log.w(TAG, "  *** MAIN-THREAD STALL: blocked ~${overshoot}ms  +${sinceStart()}ms")
            }
            expectedTickAt = t + TICK_MS
            watchdogHandler.postDelayed(this, TICK_MS)
        }
    }

    @JvmStatic
    fun startWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        expectedTickAt = now() + TICK_MS
        watchdogHandler.postDelayed(tick, TICK_MS)
        Log.d(TAG, "main-thread stall watchdog started (reports blocks > ${STALL_THRESHOLD_MS}ms)")
    }
}
