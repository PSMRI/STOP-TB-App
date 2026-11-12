package org.piramalswasthya.stoptb.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The strings here are copied verbatim out of a captured Amrit pull (28 Oct 2026) and out of the
 * device logcat that reported them as unparseable, U+202F and all.
 */
class ServerDateParserTest {

    private val visitDatePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "MMM d, yyyy, h:mm:ss a",
        "MMM dd, yyyy, h:mm:ss a",
        "MMM d, yyyy h:mm:ss a",
        "MMM dd, yyyy h:mm:ss a",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    private val serverUpdateDatePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "MMM dd, yyyy, h:mm:ss a",
        "MMM d, yyyy, h:mm:ss a",
        "MMM dd, yyyy h:mm:ss a",
        "MMM d, yyyy h:mm:ss a"
    )

    /** What the old code did: build a formatter per pattern and let a throw advance the list. */
    private fun parseTheOldWay(dateString: String, patterns: List<String>): Long? {
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).parse(dateString)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    @Test
    fun `narrow no-break space before AM-PM used to break parsing and now does not`() {
        val fromLogcat = "Sep 29, 2026, 3:34:00 PM"

        assertThat(parseTheOldWay(fromLogcat, visitDatePatterns)).isNull()
        assertThat(ServerDateParser.parse(fromLogcat, visitDatePatterns)).isNotNull()
    }

    @Test
    fun `U+202F and plain-space forms of the same instant parse to the same millis`() {
        val narrow = ServerDateParser.parse("Oct 19, 2026, 4:44:00 PM", visitDatePatterns)
        val plain = ServerDateParser.parse("Oct 19, 2026, 4:44:00 PM", visitDatePatterns)

        assertThat(narrow).isNotNull()
        assertThat(narrow).isEqualTo(plain)
    }

    @Test
    fun `every date shape seen in the captured pull parses`() {
        val captured = listOf(
            "Oct 19, 2026, 4:44:00 PM",      // visitDate / createdDate, U+202F form
            "Oct 19, 2026 4:44:00 PM",            // visitDate / createdDate, plain form
            "Oct 28, 2026, 12:33:09 AM",     // updateDate, U+202F form
            "Oct 27, 2026 9:28:20 PM",            // updateDate, plain form
            "Oct 28, 2026 12:32:50 AM",           // updatedDate, plain form
            "Oct 08, 2026 1:07:00 PM"             // visitDate, zero-padded day
        )
        captured.forEach { dateString ->
            assertThat(ServerDateParser.parse(dateString, visitDatePatterns)).isNotNull()
        }
    }

    @Test
    fun `updateDate comma-after-year form parses, which is what left serverUpdatedDate at zero`() {
        val withComma = "Oct 28, 2026, 12:33:09 AM"

        assertThat(parseTheOldWay(withComma, serverUpdateDatePatterns)).isNull()
        assertThat(ServerDateParser.parse(withComma, serverUpdateDatePatterns)).isNotNull()
    }

    @Test
    fun `results agree with the old parser on every string the old parser could handle`() {
        val parseable = listOf(
            "Oct 19, 2026 4:44:00 PM",
            "Oct 08, 2026 1:07:00 PM",
            "2026-10-19T16:44:00.000Z",
            "2026-10-19 16:44:00",
            "2026-10-19"
        )
        parseable.forEach { dateString ->
            assertThat(ServerDateParser.parse(dateString, visitDatePatterns))
                .isEqualTo(parseTheOldWay(dateString, visitDatePatterns))
        }
    }

    @Test
    fun `blank, null and literal-null stay unparsed rather than becoming an epoch`() {
        assertThat(ServerDateParser.parse(null, visitDatePatterns)).isNull()
        assertThat(ServerDateParser.parse("", visitDatePatterns)).isNull()
        assertThat(ServerDateParser.parse("   ", visitDatePatterns)).isNull()
        assertThat(ServerDateParser.parse("null", visitDatePatterns)).isNull()
        assertThat(ServerDateParser.parse("not a date", visitDatePatterns)).isNull()
    }

    @Test
    fun `normalize leaves an already-plain string as the same instance`() {
        val plain = "Oct 19, 2026 4:44:00 PM"
        assertThat(ServerDateParser.normalize(plain)).isSameInstanceAs(plain)
        assertThat(ServerDateParser.normalize("a b")).isEqualTo("a b")
    }
}
