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
    // ── Digital Chest X-Ray ──────────────────────────────────────────────────
    var isReferredForDigitalChestXray: Boolean? = null,
    var reasonForDenialChestXray: String? = null,       // pipe-sep English values
    var reasonForDenialChestXrayOther: String? = null,
    var isChestXRayDone: Boolean? = null,
    var reasonNotConductedChestXray: String? = null,
    var reasonNotConductedChestXrayOther: String? = null,
    var chestXRayResult: String? = null,
    // ── Sputum Collection ────────────────────────────────────────────────────
    var isSputumCollected: Boolean? = null,             // used as "referred for sputum"
    var reasonForDenialSputum: String? = null,          // pipe-sep English values
    var reasonForDenialSputumOther: String? = null,
    var sputumSubmittedAt: String? = null,
    // ── NAAT / TrueNAT ──────────────────────────────────────────────────────
    var isNaatConducted: Boolean? = null,
    var reasonNotConductedNaat: String? = null,
    var reasonNotConductedNaatOther: String? = null,
    var naatResult: String? = null,
    // ── Liquid Culture ───────────────────────────────────────────────────────
    var recommendedForLiquidCultureTest: Boolean? = null,
    var isLiquidCultureConducted: Boolean? = null,
    var liquidCultureResult: String? = null,
    // ── Outcome ─────────────────────────────────────────────────────────────
    var isTBConfirmed: Boolean? = null,
    var isConfirmed: Boolean = false,
    // ── Meta ─────────────────────────────────────────────────────────────────
    var latitude: Double? = null,
    var longitude: Double? = null,
    var address: String? = null,
    var serverUpdatedDate: Long? = null,
    var syncState: SyncState = SyncState.UNSYNCED
) : FormDataModel {
    fun toDTO(): TBDiagnosticsDTO = TBDiagnosticsDTO(
        id = id.toLong(),
        benId = benId,
        visitDate = getDateTimeStringFromLong(visitDate),
        nikshayId = nikshayId,
        isReferredForDigitalChestXray = isReferredForDigitalChestXray,
        reasonForDenialChestXray = reasonForDenialChestXray,
        reasonForDenialChestXrayOther = reasonForDenialChestXrayOther,
        isChestXRayDone = isChestXRayDone,
        reasonNotConductedChestXray = reasonNotConductedChestXray,
        reasonNotConductedChestXrayOther = reasonNotConductedChestXrayOther,
        chestXRayResult = chestXRayResult,
        isSputumCollected = isSputumCollected,
        reasonForDenialSputum = reasonForDenialSputum,
        reasonForDenialSputumOther = reasonForDenialSputumOther,
        sputumSubmittedAt = sputumSubmittedAt,
        isNaatConducted = isNaatConducted,
        reasonNotConductedNaat = reasonNotConductedNaat,
        reasonNotConductedNaatOther = reasonNotConductedNaatOther,
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
