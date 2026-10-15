package org.piramalswasthya.stoptb.ui.contact_tracing

const val QUESTION_UUID_CLINICAL_SCREENING_STATUS = "CFU_CLINICAL_SCREENING_STATUS"

/** Answer options for CFU_CLINICAL_SCREENING_STATUS ("Status of Clinical Screening"),
 * asked only on the CONTACT_FOLLOW_UP form — see ContactTracingFormViewModel.onSubmit(). */
enum class ClinicalScreeningStatus(val optionValue: String) {
    FULL_TREATMENT("FULL_TREATMENT"),
    TPT_ELIGIBLE("TPT_ELIGIBLE"),
    NO_TREATMENT("NO_TREATMENT");

    companion object {
        fun fromValue(value: String?): ClinicalScreeningStatus? =
            entries.find { it.optionValue.equals(value, ignoreCase = true) }
    }
}
