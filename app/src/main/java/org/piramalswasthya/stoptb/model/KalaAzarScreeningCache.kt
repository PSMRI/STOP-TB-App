package org.piramalswasthya.stoptb.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.piramalswasthya.stoptb.configuration.FormDataModel
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.network.KALAZARScreeningDTO

@Entity(
    tableName = "KALAZAR_SCREENING",
    foreignKeys = [ForeignKey(
        entity = BenRegCache::class,
        parentColumns = arrayOf("beneficiaryId"/* "householdId"*/),
        childColumns = arrayOf("benId" /*"hhId"*/),
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(name = "ind_kalazarsn", value = ["benId"/* "hhId"*/])]
)
data class KalaAzarScreeningCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val benId: Long,
    var visitDate: Long = System.currentTimeMillis(),
    val houseHoldDetailsId: Long,
    var beneficiaryStatus: String ? = "",
    var beneficiaryStatusId: Int = 0,
    var dateOfDeath: Long = System.currentTimeMillis(),
    var placeOfDeath: String ? = "",
    var otherPlaceOfDeath: String ? = "",
    var reasonForDeath: String ?  = "",
    var otherReasonForDeath: String ?  = "",
    var rapidDiagnosticTest: String ? = "",
    var dateOfRdt: Long = System.currentTimeMillis(),
    var kalaAzarCaseStatus: String ? = "",
    var referredTo: Int ? = 0,
    var referToName: String ? = "",
    var otherReferredFacility: String ? = "",
    var diseaseTypeID: Int ? = 0,
    var createdDate: Long = System.currentTimeMillis(),
    var createdBy: String ? = "",
    var followUpPoint: Int ? = 0,
    var syncState: SyncState = SyncState.UNSYNCED,
): FormDataModel {
    fun toDTO(): KALAZARScreeningDTO {
        return KALAZARScreeningDTO(
            id = 0,
            benId = benId,
            visitDate = getDateTimeStringFromLong(visitDate).toString(),
            kalaAzarCaseStatus = kalaAzarCaseStatus,
            houseHoldDetailsId = houseHoldDetailsId,
            referredTo = referredTo,
            referToName = referToName.toString(),
            otherReferredFacility = otherReferredFacility.toString(),
            createdDate = getDateTimeStringFromLong(createdDate).toString(),
            syncState = SyncState.SYNCED,
            diseaseTypeID = diseaseTypeID,
            beneficiaryStatusId = beneficiaryStatusId,
            beneficiaryStatus = beneficiaryStatus.toString(),
            createdBy = createdBy.toString(),
            rapidDiagnosticTest = rapidDiagnosticTest.toString(),
            dateOfRdt = getDateTimeStringFromLong(dateOfRdt).toString(),
            dateOfDeath = getDateTimeStringFromLong(dateOfDeath).toString(),
            reasonForDeath = reasonForDeath.toString(),
            otherReasonForDeath = otherReasonForDeath.toString(),
            otherPlaceOfDeath = otherPlaceOfDeath.toString(),
            placeOfDeath = placeOfDeath.toString(),
            followUpPoint = followUpPoint

        )
    }
}

data class BenWithKALAZARScreeningCache(
    @Embedded
    val ben: BenBasicCache,
    @Relation(
        parentColumn = "benId", entityColumn = "benId"
    )
    val kalazarScreening: KalaAzarScreeningCache?,

    ) {
    fun asKALAZARScreeningDomainModel(): BenWithKALAZARScreeningDomain {
        return BenWithKALAZARScreeningDomain(
            ben = ben.asBasicDomainModel(),
            kala = kalazarScreening
        )
    }
}

data class BenWithKALAZARScreeningDomain(
    val ben: BenBasicDomain,
    val kala: KalaAzarScreeningCache?
)