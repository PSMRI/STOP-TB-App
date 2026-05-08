package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.piramalswasthya.stoptb.configuration.FormDataModel
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.network.TBDiagnosticsDTO

@Entity(
    tableName = "TB_DIAGNOSTICS",
    foreignKeys = [ForeignKey(
        entity = BenRegCache::class,
        parentColumns = ["beneficiaryId"],
        childColumns = ["benId"],
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(name = "ind_tb_diagnostics_ben", value = ["benId"])]
)
data class TBDiagnosticsCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val benId: Long,
    var visitDate: Long = System.currentTimeMillis(),
    var nikshayId: String? = null,
    var isChestXRayDone: Boolean? = null,
    var chestXRayResult: String? = null,
    var isSputumCollected: Boolean? = null,
    var sputumSubmittedAt: String? = null,
    var isNaatConducted: Boolean? = null,
    var naatResult: String? = null,
    var recommendedForLiquidCultureTest: Boolean? = null,
    var isLiquidCultureConducted: Boolean? = null,
    var liquidCultureResult: String? = null,
    var isTBConfirmed: Boolean? = null,
    var isConfirmed: Boolean = false,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var address: String? = null,
    var syncState: SyncState = SyncState.UNSYNCED
) : FormDataModel {
    fun toDTO(): TBDiagnosticsDTO = TBDiagnosticsDTO(
        id = id.toLong(),
        benId = benId,
        visitDate = getDateTimeStringFromLong(visitDate),
        nikshayId = nikshayId,
        isChestXRayDone = isChestXRayDone,
        chestXRayResult = chestXRayResult,
        isSputumCollected = isSputumCollected,
        sputumSubmittedAt = sputumSubmittedAt,
        isNaatConducted = isNaatConducted,
        naatResult = naatResult,
        recommendedForLiquidCultureTest = recommendedForLiquidCultureTest,
        isLiquidCultureConducted = isLiquidCultureConducted,
        liquidCultureResult = liquidCultureResult,
        isTBConfirmed = isTBConfirmed,
        isConfirmed = isConfirmed,
        latitude = latitude,
        longitude = longitude,
        address = address
    )
}
