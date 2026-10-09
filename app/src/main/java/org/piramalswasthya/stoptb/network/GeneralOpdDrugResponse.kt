package org.piramalswasthya.stoptb.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription

data class GeneralOpdDrugResponse(
    val drugID: Int,
    val drugName: String,
    val frequency: String,
    val duration: Int,
    val durationUnit: String,
    val itemFormID: Int? = null,
    val drugForm: String? = null,
    val qtyPrescribed: Int? = null,
    val instructions: String? = null
) {
    fun toPrescription(opdId: Int, position: Int): GeneralOpdPrescription {
        require(drugID > 0 && drugName.isNotBlank()) { "Invalid General OPD drug identity" }
        require(duration > 0 && frequency.isNotBlank() && durationUnit.isNotBlank()) {
            "Invalid General OPD prescription details"
        }
        val label = listOf(drugForm.orEmpty(), drugName).filter { it.isNotBlank() }.joinToString(" ")
        return GeneralOpdPrescription(opdId, position, label, frequency, duration,
            GeneralOpdSaveRequest.durationUnit(durationUnit), instructions.orEmpty(),
            drugID, drugName, itemFormID, drugForm)
    }

    companion object {
        private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter<List<GeneralOpdDrugResponse>>(Types.newParameterizedType(List::class.java,
                GeneralOpdDrugResponse::class.java))

        fun parsePrescriptions(json: String, opdId: Int): List<GeneralOpdPrescription> =
            requireNotNull(adapter.fromJson(json)).mapIndexed { position, drug ->
                drug.toPrescription(opdId, position)
            }
    }
}
