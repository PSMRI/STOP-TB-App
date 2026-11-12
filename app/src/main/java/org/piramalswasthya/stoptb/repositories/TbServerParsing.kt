package org.piramalswasthya.stoptb.repositories

import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.utils.ServerDateParser
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * Parsing helpers for STOP-TB server responses (dates, nullable JSON fields), moved out of
 * TBRepo's companion object unchanged. Same package, so TBRepo calls them without qualification.
 */

// New formatters per call: SimpleDateFormat is not thread-safe, and these helpers run from
// concurrent pulls on Dispatchers.IO (a shared instance could garble a date filter).
internal fun getCurrentDate(millis: Long = System.currentTimeMillis()): String {
    val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(millis)
    val timeString = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(millis)
    return "${dateString}T${timeString}.000Z"
}

internal val singleVisitDatePattern = listOf("MMM d, yyyy h:mm:ss a")

internal fun getLongFromDate(dateString: String): Long {
    //Jul 22, 2023 8:17:23 AM"
    return ServerDateParser.parse(dateString, singleVisitDatePattern)
        ?: throw IllegalStateException("Invalid date for dateReg")
}

internal fun getLongFromDateMultipleSupport(dateString: String?): Long {
    if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) {
        return 0L
    }
    return ServerDateParser.parse(dateString, visitDatePatterns)
        ?: run {
            Timber.w("TB_DATE_PARSE: failed to parse visitDate='$dateString'")
            0L
        }
}

internal val visitDatePatterns = listOf(
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
internal fun getServerUpdatedDate(jsonObject: JSONObject): Long {
    return parseServerUpdateDate(
        jsonObject.optStringOrNull("updateDate")
            ?: jsonObject.optStringOrNull("updatedDate")
    )
}

internal fun parseServerUpdateDate(dateString: String?): Long {
    if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return 0L
    // Same U+202F separator problem as visitDate above, but this one failed silently:
    // an unparseable updateDate became 0L, and shouldApplyServerRecord() treats 0L as
    // "no server timestamp, apply it" - so every record was rewritten on every pull
    // instead of only the changed ones, invalidating every observing query each time.
    return ServerDateParser.parse(dateString, serverUpdateDatePatterns) ?: run {
        // Still 0L (apply the record), but no longer silent: a format the patterns don't know
        // makes every pull rewrite those records. Logged once per distinct string.
        val firstTime = synchronized(unparseableUpdateDates) {
            unparseableUpdateDates.size < MAX_LOGGED_UNPARSEABLE_DATES && unparseableUpdateDates.add(dateString)
        }
        if (firstTime) {
            Timber.w("Unparseable server updateDate \"%s\": add its format to serverUpdateDatePatterns", dateString)
        }
        0L
    }
}

// Distinct unparseable strings already logged; bounded, so a large pull can't grow it. After
// the cap, further new formats are not logged.
private val unparseableUpdateDates = HashSet<String>()
private const val MAX_LOGGED_UNPARSEABLE_DATES = 20

internal val serverUpdateDatePatterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "MMM dd, yyyy, h:mm:ss a",
    "MMM d, yyyy, h:mm:ss a",
    "MMM dd, yyyy h:mm:ss a",
    "MMM d, yyyy h:mm:ss a"
)

internal fun shouldApplyServerRecord(
    existingSyncState: SyncState?,
    savedServerUpdatedDate: Long?,
    serverUpdatedDate: Long
): Boolean {
    if (existingSyncState != null && existingSyncState != SyncState.SYNCED) return false
    if (serverUpdatedDate <= 0L) return true
    return serverUpdatedDate > (savedServerUpdatedDate ?: 0L)
}

internal fun JSONObject.optNullableBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return optBoolean(name)
}

internal fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

internal fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

internal fun JSONObject.optStringListOrNull(name: String): List<String>? {
    if (!has(name) || isNull(name)) return null
    val value = opt(name)
    return when (value) {
        is JSONArray -> List(value.length()) { index -> value.optString(index) }
            .filter { it.isNotBlank() }
        is String -> runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrNull()
        else -> null
    }
}

internal fun getStopTbDataArray(jsonObj: JSONObject): JSONArray {
    return when (val data = jsonObj.opt("data")) {
        is JSONArray -> data
        is JSONObject -> data.optJSONArray("data") ?: JSONArray()
        else -> JSONArray()
    }
}

internal fun JSONObject.optIntListOrNull(name: String): List<Int>? {
    if (!has(name) || isNull(name)) return null
    return when (val value = opt(name)) {
        is JSONArray -> List(value.length()) { index -> value.optInt(index) }
        is String -> runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optInt(index) }
        }.getOrNull()
        else -> null
    }
}

internal fun String?.asValidOrderId(): String? =
    this?.trim()?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
