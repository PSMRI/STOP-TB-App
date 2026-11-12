package org.piramalswasthya.stoptb.helpers.dynamicMapper

import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity

/**
 * Helper (explanatory) text shown under a dynamic-form question, e.g. the NPY / DOTS guidance in
 * Counselling Section C (STOP-504).
 *
 * The server sends it as validation rows: one HELPER_TEXT_ENGLISH / HELPER_TEXT_HINDI row per bullet,
 * to be shown in server validationId order. Under QuestionIdStrategy.HASH_BASED the local id used to be
 * hash(fieldId_type), so every bullet of a question got the same id and the upsert kept only the last.
 * [hashValidationId] gives the n-th row of a type id = hash(fieldId_type) + n: the first row keeps its
 * old id (no change for MIN/MAX/UNIT/... rows), later rows are distinct, and the bullet order can be
 * recovered as id - hash(fieldId_type), which is exact even when the Int addition wraps.
 */
object QuestionHelperText {

    const val HELPER_TEXT_ENGLISH = "HELPER_TEXT_ENGLISH"
    const val HELPER_TEXT_HINDI = "HELPER_TEXT_HINDI"

    private fun baseHash(fieldId: String, validationType: String): Int =
        (fieldId + "_" + validationType).hashCode()

    fun hashValidationId(fieldId: String, validationType: String, indexWithinType: Int): Int =
        baseHash(fieldId, validationType) + indexWithinType

    fun isHelperText(validationType: String): Boolean =
        validationType == HELPER_TEXT_ENGLISH || validationType == HELPER_TEXT_HINDI

    fun <T> helperRowIndexes(rows: List<T>, typeAndId: (T) -> Pair<String, Int>): Map<Int, Int> {
        val indexByPosition = mutableMapOf<Int, Int>()
        rows.withIndex()
            .filter { indexedRow -> isHelperText(typeAndId(indexedRow.value).first) }
            .groupBy { indexedRow -> typeAndId(indexedRow.value).first }
            .values
            .forEach { sameTypeRows ->
                sameTypeRows.sortedBy { indexedRow -> typeAndId(indexedRow.value).second }
                    .forEachIndexed { rank, indexedRow -> indexByPosition[indexedRow.index] = rank }
            }
        return indexByPosition
    }

    /** Inverse of [hashValidationId]; Int arithmetic wraps both ways, so this is exact near Int.MAX_VALUE. */
    internal fun positionWithinType(validationId: Int, baseHash: Int): Int = validationId - baseHash

    /**
     * The bullets for [fieldId] in display order, in Hindi when [isHindi] and Hindi rows exist,
     * otherwise in English. [fieldId] null (RAW_SERVER_ID forms) orders by the server validationId.
     */
    fun resolve(
        fieldId: String?,
        validations: List<QuestionValidationEntity>,
        isHindi: Boolean
    ): List<String> {
        val hindi = bullets(fieldId, validations, HELPER_TEXT_HINDI)
        return if (isHindi && hindi.isNotEmpty()) hindi else bullets(fieldId, validations, HELPER_TEXT_ENGLISH)
    }

    private fun bullets(fieldId: String?, validations: List<QuestionValidationEntity>, type: String): List<String> {
        val rows = validations.filter { row -> row.validationType == type }
        val ordered = if (fieldId.isNullOrEmpty()) {
            rows.sortedBy { row -> row.validationId }
        } else {
            val base = baseHash(fieldId, type)
            rows.sortedBy { row -> positionWithinType(row.validationId, base) }
        }
        return ordered.mapNotNull { row -> row.validationValue?.trim()?.takeIf(String::isNotEmpty) }
    }
}
