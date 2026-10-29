package org.piramalswasthya.stoptb.utils

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses the date strings Amrit sends back, and does two things the previous inline parsers did not.
 *
 * 1. Normalises the separator before AM/PM. The backend echoes dates that were formatted by a
 *    CLDR 42 / ICU 72+ runtime, which emits a NARROW NO-BREAK SPACE (U+202F) there instead of a
 *    plain space - "Oct 19, 2026, 4:44:00 PM". Every pattern in this app spells that
 *    separator as an ASCII space, and SimpleDateFormat matches pattern literals exactly, so those
 *    strings never parse. In one captured pull that was 70,000 of ~95,000 date strings (20,000
 *    visitDate, 30,000 updateDate, 20,000 createdDate). Both forms arrive in the same payload, so
 *    this normalises the input rather than switching the patterns over to U+202F.
 *
 * 2. Reuses formatters and fails without throwing. The old parsers built a fresh SimpleDateFormat
 *    per pattern per record and relied on a thrown ParseException to move to the next pattern;
 *    with 70,000 unparseable strings tried against up to 10 patterns each, that was roughly half a
 *    million formatter allocations and half a million exceptions - each capturing a stack trace -
 *    per pull cycle. [parse] uses a thread-confined cached formatter and the non-throwing
 *    [SimpleDateFormat.parse] overload instead.
 *
 * SimpleDateFormat is not thread-safe and the pull runs these on several threads at once, so the
 * cache is per-thread. It is bounded by the number of distinct patterns in use (about 15).
 */
object ServerDateParser {

    // NOT ThreadLocal.withInitial: that is API 26 and this module is minSdk 25 with no core
    // library desugaring, so it would throw NoSuchMethodError on an API 25 device the first
    // time anything parsed a server date - which is every pull and most JSON deserialization.
    private val formatterCache = object : ThreadLocal<MutableMap<CacheKey, SimpleDateFormat>>() {
        override fun initialValue(): MutableMap<CacheKey, SimpleDateFormat> = mutableMapOf()
    }

    private data class CacheKey(val pattern: String, val lenient: Boolean)

    private fun formatterFor(pattern: String, lenient: Boolean): SimpleDateFormat =
        formatterCache.get()!!.getOrPut(CacheKey(pattern, lenient)) {
            SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = lenient }
        }

    /**
     * Folds the two non-breaking spaces that show up in server dates (U+202F narrow no-break and
     * U+00A0 no-break) down to a plain space. Returns the original instance when there is nothing
     * to change, so the common already-plain case allocates nothing.
     */
    fun normalize(dateString: String): String =
        if (dateString.indexOf(' ') < 0 && dateString.indexOf(' ') < 0) {
            dateString
        } else {
            dateString.replace(' ', ' ').replace(' ', ' ')
        }

    /**
     * Tries [patterns] in order and returns the first match as epoch millis, or null.
     *
     * A pattern counts as matching when it consumes at least one character, which is exactly what
     * the single-argument [SimpleDateFormat.parse] accepts - it throws only when nothing at all was
     * consumed. Keeping that rule means a pattern that matches a prefix of a longer string still
     * wins here exactly as it did before, so pattern lists whose order relies on that (and this app
     * has some) keep behaving identically.
     */
    fun parse(dateString: String?, patterns: List<String>, lenient: Boolean = true): Long? {
        if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return null
        val normalized = normalize(dateString)
        patterns.forEach { pattern ->
            val position = ParsePosition(0)
            val parsed = formatterFor(pattern, lenient).parse(normalized, position)
            if (parsed != null && position.index > 0) return parsed.time
        }
        return null
    }
}
