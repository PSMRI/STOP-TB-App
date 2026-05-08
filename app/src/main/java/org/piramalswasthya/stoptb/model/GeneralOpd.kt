package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.piramalswasthya.stoptb.configuration.FormDataModel
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.network.GeneralOpdDTO

@Entity(
    tableName = "GENERAL_OPD",
    foreignKeys = [ForeignKey(
        entity = BenRegCache::class,
        parentColumns = ["beneficiaryId"],
        childColumns = ["benId"],
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(name = "ind_general_opd_ben", value = ["benId"])]
)
data class GeneralOpdCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val benId: Long,
    var visitDate: Long = System.currentTimeMillis(),
    var chiefComplaints: List<String>? = null,
    var medications: List<String>? = null,
    var dosage: String? = null,
    var frequency: String? = null,
    var duration: String? = null,
    var notes: String? = null,
    var syncState: SyncState = SyncState.UNSYNCED
) : FormDataModel {
    fun toDTO(): GeneralOpdDTO = GeneralOpdDTO(
        id = id.toLong(),
        benId = benId,
        visitDate = getDateTimeStringFromLong(visitDate),
        chiefComplaints = chiefComplaints,
        medications = medications,
        dosage = dosage,
        frequency = frequency,
        duration = duration,
        notes = notes
    )
}
