package org.piramalswasthya.stoptb.ui.contact_tracing

const val QUESTION_UUID_REGIMEN_ADVISED = "TFU_REGIMEN_ADVISED"

/** Answer options for TFU_REGIMEN_ADVISED. [aliases] are older optionValues still accepted for
 * the same regimen, so answers saved before a value was renamed (e.g. 3HR -> 3RH) still resolve. */
enum class RegimenAdvised(
    val optionValue: String,
    val requiredFollowUpCount: Int,
    val durationMonths: Int,
    val aliases: List<String> = emptyList()
) {
    REGIMEN_6H("6H", 6, 6),
    REGIMEN_3RH("3RH", 3, 3, aliases = listOf("3HR")),
    REGIMEN_3HP("3HP", 3, 3),
    REGIMEN_6LFX("6LFX", 6, 6),
    REGIMEN_4R("4R", 4, 4),
    REGIMEN_1HP("1HP", 1, 1),

    // Legacy option from TPT_FOLLOW_UP v1, kept so responses saved under v1 still resolve.
    REGIMEN_6H_MODIFIED_DOSE("6H_MODIFIED_DOSE", 6, 6);

    companion object {
        fun fromValue(value: String?): RegimenAdvised? =
            entries.find { entry ->
                entry.optionValue.equals(value, ignoreCase = true) ||
                    entry.aliases.any { it.equals(value, ignoreCase = true) }
            }
    }
}
