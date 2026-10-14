package org.piramalswasthya.stoptb.repositories

import com.squareup.moshi.JsonDataException
import java.io.IOException

/** Allowlisted validation descriptions; never display exception text containing server values. */
internal fun generalOpdSkipReason(error: Exception): String = when (error.message) {
    "Invalid General OPD record" -> "INVALID_RECORD_OBJECT"
    "Invalid General OPD drugs array" -> "INVALID_DRUGS_ARRAY"
    "Invalid General OPD drug identity" -> "DRUG_ID_INVALID_OR_DRUG_NAME_BLANK"
    "Invalid General OPD prescription details" -> "DURATION_NOT_POSITIVE_OR_FREQUENCY_OR_UNIT_BLANK"
    else -> "MALFORMED_RECORD_OR_PRESCRIPTION_${error.javaClass.simpleName}"
}

/** Isolate malformed records without swallowing cancellation or database failures. */
internal suspend fun <T, P> processGeneralOpdPullBatch(
    records: Iterable<T>,
    parse: (T) -> P,
    save: suspend (T, P) -> Unit,
    onInvalid: suspend (Int, Exception) -> Unit
): Boolean {
    var complete = true
    for ((index, record) in records.withIndex()) {
        val parsed = try {
            parse(record)
        } catch (e: JsonDataException) {
            complete = false
            onInvalid(index, e)
            continue
        } catch (e: IllegalArgumentException) {
            complete = false
            onInvalid(index, e)
            continue
        } catch (e: IOException) {
            complete = false
            onInvalid(index, e)
            continue
        }
        save(record, parsed)
    }
    return complete
}
