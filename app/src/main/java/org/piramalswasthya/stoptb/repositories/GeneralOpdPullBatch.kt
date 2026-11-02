package org.piramalswasthya.stoptb.repositories

import com.squareup.moshi.JsonDataException
import java.io.IOException

/** Isolate malformed records without swallowing cancellation or database failures. */
internal suspend fun <T, P> processGeneralOpdPullBatch(
    records: Iterable<T>,
    parse: (T) -> P,
    save: suspend (T, P) -> Unit,
    onInvalid: (Int, Exception) -> Unit
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
