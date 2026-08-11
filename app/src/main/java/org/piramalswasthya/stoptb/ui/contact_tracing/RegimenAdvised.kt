package org.piramalswasthya.stoptb.ui.contact_tracing

const val QUESTION_UUID_REGIMEN_ADVISED = "TFU_REGIMEN_ADVISED"
enum class RegimenAdvised(val optionValue: String, val requiredFollowUpCount: Int) {
    REGIMEN_6H("6H", 6),
    REGIMEN_3HP("3HP", 3),
    REGIMEN_3HR("3HR", 3),
    REGIMEN_1HP("1HP", 1),
    REGIMEN_6H_MODIFIED_DOSE("6H_MODIFIED_DOSE", 6);

    companion object {
        fun fromValue(value: String?): RegimenAdvised? =
            entries.find { it.optionValue.equals(value, ignoreCase = true) }
    }
}
