package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "GENERAL_OPD_PRESCRIPTION",
    primaryKeys = ["opdId", "position"],
    foreignKeys = [ForeignKey(
        entity = GeneralOpdCache::class,
        parentColumns = ["id"],
        childColumns = ["opdId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["opdId"])]
)
data class GeneralOpdPrescription(
    val opdId: Int,
    val position: Int,
    val medicine: String,
    val frequency: String,
    val durationCount: Int,
    val durationUnit: String,
    val instruction: String,
    val drugId: Int? = null,
    val drugName: String? = null,
    val itemFormId: Int? = null,
    val drugForm: String? = null
)

data class OpdMedicineDraft(
    var medicine: String = "",
    var frequency: String = "",
    var durationCount: Int = 1,
    var durationUnit: String = "",
    var instruction: String = "",
    var drugId: Int? = null,
    var drugName: String? = null,
    var itemFormId: Int? = null,
    var drugForm: String? = null
) {
    fun hasData() = medicine.isNotBlank() || frequency.isNotBlank() ||
        durationUnit.isNotBlank() || instruction.isNotBlank() || durationCount != 1 || itemFormId != null
}
