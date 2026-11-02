package org.piramalswasthya.stoptb.ui.home_activity.vital_screen

object VitalHwcReferralRules {

    private const val PRESENT_OPTION_ID = 1

    fun getReferralTriggers(
        temperature: Double?,
        pulse: Int?,
        systolic: Int?,
        diastolic: Int?,
        rbs: Double?,
        presentExaminationFindings: List<String>
    ): List<String> {
        val triggers = mutableListOf<String>()
        if (temperature != null && temperature >= 100.0) {
            triggers += "High Temperature: ${temperature.stripTrailingZeros()} F"
        }

        if (pulse != null && pulse > 90) {
            triggers += "High Pulse: $pulse BPM"
        } else if (pulse != null && pulse < 60) {
            triggers += "Low Pulse: $pulse BPM"
        }

        if (systolic != null && systolic >= 140) {
            triggers += "High Systolic BP: $systolic mmHg"
        } else if (systolic != null && systolic < 90) {
            triggers += "Low Systolic BP: $systolic mmHg"
        }
        if (diastolic != null && diastolic >= 90) {
            triggers += "High Diastolic BP: $diastolic mmHg"
        } else if (diastolic != null && diastolic < 60) {
            triggers += "Low Diastolic BP: $diastolic mmHg"
        }

        if (rbs != null && rbs >= 100.0) {
            triggers += "High RBS: ${rbs.stripTrailingZeros()} mg/dl"
        }

        triggers += presentExaminationFindings.map { finding -> "$finding: Present" }
        return triggers
    }

    fun getPresentExaminationFindings(
        pallorId: Int?,
        pallor: String?,
        icterusId: Int?,
        icterus: String?,
        cyanosisId: Int?,
        cyanosis: String?,
        clubbingId: Int?,
        clubbing: String?,
        lymphadenopathyId: Int?,
        lymphadenopathy: String?,
        oedemaId: Int?,
        oedema: String?
    ): List<String> = listOfNotNull(
        "Pallor".takeIf { isFindingPresent(pallorId, pallor) },
        "Icterus".takeIf { isFindingPresent(icterusId, icterus) },
        "Cyanosis".takeIf { isFindingPresent(cyanosisId, cyanosis) },
        "Clubbing".takeIf { isFindingPresent(clubbingId, clubbing) },
        "Lymphadenopathy".takeIf { isFindingPresent(lymphadenopathyId, lymphadenopathy) },
        "Oedema".takeIf { isFindingPresent(oedemaId, oedema) }
    )

    fun isFindingPresent(findingId: Int?, findingCode: String?): Boolean =
        findingId == PRESENT_OPTION_ID ||
            findingCode?.trim()?.uppercase() in listOf("PRESENT", "YES")

    private fun Double.stripTrailingZeros(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()
}
