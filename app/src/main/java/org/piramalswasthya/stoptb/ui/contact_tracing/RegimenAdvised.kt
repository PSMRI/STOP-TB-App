package org.piramalswasthya.stoptb.ui.contact_tracing

const val QUESTION_UUID_REGIMEN_ADVISED = "TFU_REGIMEN_ADVISED"
enum class RegimenAdvised(val optionValue: String, val requiredFollowUpCount: Int) {
    REGIMEN_6H("6H", 6),                                   // Isoniazid daily x 6 months — 1 per MONTH x 6
    REGIMEN_3HP("3HP", 12),                                // Isoniazid + Rifapentine weekly x 3 months — 1 per WEEK x 12
    REGIMEN_3HR("3HR", 90),                                // Isoniazid + Rifampicin daily x 3 months — 1 per DAY x 90
    REGIMEN_1HP("1HP", 30),                                // Isoniazid + Rifapentine daily x 1 month — 1 per DAY x 30
    REGIMEN_6H_MODIFIED_DOSE("6H_MODIFIED_DOSE", 180);     // Isoniazid daily x 6 months, dose-adjusted — 1 per DAY x 180

    companion object {
        fun fromValue(value: String?): RegimenAdvised? =
            entries.find { it.optionValue.equals(value, ignoreCase = true) }
    }
}
