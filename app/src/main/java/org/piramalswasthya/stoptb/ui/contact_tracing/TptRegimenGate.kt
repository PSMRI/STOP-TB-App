package org.piramalswasthya.stoptb.ui.contact_tracing

import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto

/** TPT_FOLLOW_UP PRE_SUBMIT (STOP-620): Regimen Advised stays disabled until TPT Start Date is
 * entered. While disabled, tapping an option shows a "fill the start date first" hint, and a
 * regimen left without a start date is cleared (a locked saved answer is never touched). */
object TptRegimenGate {

    fun apply(byUuid: Map<String, CounsellingQuestionDto>) {
        val regimenQuestion = byUuid[QUESTION_UUID_REGIMEN_ADVISED] ?: return
        val startDateQuestion = byUuid[QUESTION_UUID_TPT_START_DATE] ?: return
        val hasStartDate = !startDateQuestion.value?.toString().isNullOrBlank()

        regimenQuestion.options.orEmpty().forEach { it.enabled = hasStartDate }
        regimenQuestion.disabledOptionHintRes =
            if (hasStartDate) null else R.string.tpt_regimen_requires_start_date

        if (!hasStartDate && regimenQuestion.value != null && !regimenQuestion.lockedAnswer) {
            regimenQuestion.value = null
            regimenQuestion.errorMessage = null
        }
    }
}
