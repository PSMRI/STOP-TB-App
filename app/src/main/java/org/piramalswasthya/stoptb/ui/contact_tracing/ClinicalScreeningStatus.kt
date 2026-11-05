package org.piramalswasthya.stoptb.ui.contact_tracing

const val QUESTION_UUID_CLINICAL_SCREENING_STATUS = "CFU_CLINICAL_SCREENING_STATUS"

/** Answer options for CFU_CLINICAL_SCREENING_STATUS ("Status of Clinical Screening"),
 * asked only on the CONTACT_FOLLOW_UP form — see ContactTracingFormViewModel.onSubmit().
 * Each entry lists the legacy optionValue first, then the current backend one, so responses
 * saved under either schema still resolve. */
enum class ClinicalScreeningStatus(vararg val optionValues: String) {
    FULL_TREATMENT("FULL_TREATMENT", "RECOMMEND_FOR_ATT"),
    TPT_ELIGIBLE("TPT_ELIGIBLE", "RECOMMEND_FOR_TPT"),
    NO_TREATMENT("NO_TREATMENT", "TPT_NOT_RECOMMENDED");

    companion object {
        fun fromValue(value: String?): ClinicalScreeningStatus? =
            entries.find { entry -> entry.optionValues.any { it.equals(value, ignoreCase = true) } }
    }
}
