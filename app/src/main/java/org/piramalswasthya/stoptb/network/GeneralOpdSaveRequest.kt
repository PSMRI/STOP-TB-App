package org.piramalswasthya.stoptb.network

import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import java.util.Locale

data class GeneralOpdComplaintRequest(val chiefComplaintID: Int, val chiefComplaint: String)

data class GeneralOpdDrugRequest(
    val drugID: Int,
    val drugName: String,
    val qtyPrescribed: Int,
    val frequency: String,
    val duration: Int,
    val durationUnit: String,
    val instructions: String,
    val itemFormID: Int,
    val drugForm: String
)

data class GeneralOpdPrescriptionRequest(
    val instruction: String,
    val drugs: List<GeneralOpdDrugRequest>
)

data class GeneralOpdSaveRequest(
    val submissionId: String,
    val beneficiaryRegID: Long,
    val providerServiceMapID: Int,
    val createdBy: String,
    val chiefComplaints: List<GeneralOpdComplaintRequest>,
    val prescription: GeneralOpdPrescriptionRequest
) {
    companion object {
        fun from(cache: GeneralOpdCache, prescriptions: List<GeneralOpdPrescription>,
                 beneficiaryRegID: Long, providerServiceMapID: Int, createdBy: String): GeneralOpdSaveRequest {
            val submissionId = requireNotNull(cache.submissionId).also { require(it.isNotBlank()) }
            val names = cache.chiefComplaints.orEmpty()
            val ids = cache.chiefComplaintIds.orEmpty()
            require(ids.size == names.size && ids.all { it > 0 }) { "Unresolved chief complaint IDs" }
            require(beneficiaryRegID > 0 && providerServiceMapID > 0 && createdBy.isNotBlank())
            val drugs = prescriptions.sortedBy { it.position }.map { row ->
                val drugId = requireNotNull(row.drugId).also { require(it > 0) }
                val drugName = requireNotNull(row.drugName).also { require(it.isNotBlank()) }
                val formId = requireNotNull(row.itemFormId).also { require(it > 0) }
                val formName = requireNotNull(row.drugForm).also { require(it.isNotBlank()) }
                require(row.frequency.isNotBlank() && row.durationCount > 0 && row.durationUnit.isNotBlank())
                GeneralOpdDrugRequest(drugId, drugName, 0, row.frequency, row.durationCount,
                    durationUnit(row.durationUnit), row.instruction, formId, formName)
            }
            require(drugs.size == cache.medications.orEmpty().size) { "Incomplete saved prescriptions" }
            require(prescriptions.sortedBy { it.position }.map { it.medicine } == cache.medications.orEmpty())
            return GeneralOpdSaveRequest(submissionId, beneficiaryRegID, providerServiceMapID, createdBy,
                names.mapIndexed { index, name -> GeneralOpdComplaintRequest(ids[index], name) },
                GeneralOpdPrescriptionRequest(cache.notes.orEmpty(), drugs))
        }

        internal fun durationUnit(value: String): String = when (value.trim().lowercase(Locale.ENGLISH)) {
            "day", "days", "day(s)" -> "Day(s)"
            "week", "weeks", "week(s)" -> "Week(s)"
            "month", "months", "month(s)" -> "Month(s)"
            else -> value
        }
    }
}
