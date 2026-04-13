package org.piramalswasthya.stoptb.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.piramalswasthya.stoptb.configuration.FormDataModel
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.network.MalariaConfirmedDTO

@Entity(
    tableName = "MALARIA_CONFIRMED",
    foreignKeys = [ForeignKey(
        entity = BenRegCache::class,
        parentColumns = arrayOf("beneficiaryId"/* "householdId"*/),
        childColumns = arrayOf("benId" /*"hhId"*/),
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(name = "ind_malariacs", value = ["benId"/* "hhId"*/])]
)
data class MalariaConfirmedCasesCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val diseaseId: Int = 0,
    val benId: Long,
    val houseHoldDetailsId: Long,
    var dateOfDiagnosis: Long = System.currentTimeMillis(),
    var treatmentStartDate: Long = System.currentTimeMillis(),
    var treatmentCompletionDate: Long = System.currentTimeMillis(),
    var treatmentGiven: String? = null,
    var referralDate: Long = System.currentTimeMillis(),
    var day: String? = null,
    var syncState: SyncState = SyncState.UNSYNCED,
) : FormDataModel {
    fun toDTO(): MalariaConfirmedDTO {
        return MalariaConfirmedDTO(
            id = 0,
            benId = benId,
            dateOfDiagnosis = getDateTimeStringFromLong(dateOfDiagnosis).toString(),
            treatmentStartDate = getDateTimeStringFromLong(treatmentStartDate).toString(),
            treatmentCompletionDate = getDateTimeStringFromLong(treatmentCompletionDate).toString(),
            referralDate = getDateTimeStringFromLong(referralDate).toString(),
            day = day.toString(),
            houseHoldDetailsId = houseHoldDetailsId,
            treatmentGiven = treatmentGiven.toString(),
        )
    }

}
data class BenWithMalariaConfirmedCache(
    @Embedded
    val ben: BenBasicCache,
    @Relation(
        parentColumn = "benId", entityColumn = "benId"
    )
    val malariaConfirmed: MalariaConfirmedCasesCache?,
    val slideTestName: String?

    ) {
    fun asMalariaConfirmedDomainModel(): BenWithMalariaConfirmedDomain {
        return BenWithMalariaConfirmedDomain(
            ben = ben.asBasicDomainModel(),
            malariaConfirmed = malariaConfirmed,
            slideTestName = slideTestName
        )
    }
}

data class BenWithMalariaConfirmedDomain(
    val ben: BenBasicDomain,
    val malariaConfirmed: MalariaConfirmedCasesCache?,
    val slideTestName: String?
)