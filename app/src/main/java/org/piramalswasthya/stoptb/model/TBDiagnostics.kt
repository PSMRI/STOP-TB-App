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
    var isReferredForSputum: Boolean? = null,           // referral decision (see MIGRATION_50_51)
    var isSputumCollected: Boolean? = null,             // was a sample physically collected
    var reasonForDenialSputum: String? = null,          // pipe-sep English values
    var reasonForDenialSputumOther: String? = null,
    var sputumSubmittedAt: String? = null,
    // ── NAAT / TrueNAT ──────────────────────────────────────────────────────
    var isNaatConducted: Boolean? = null,
    var reasonNotConductedNaat: String? = null,
    var reasonNotConductedNaatOther: String? = null,
    var naatResult: String? = null,
    // ── RIF "Not Conducted" — RIF's own reason fields, distinct from NAAT's above ─────────────
    var reasonNotConductedRif: String? = null,
    var reasonNotConductedRifOther: String? = null,
    // ── Liquid Culture ───────────────────────────────────────────────────────
    var recommendedForLiquidCultureTest: Boolean? = null,
    var isLiquidCultureConducted: Boolean? = null,
    var liquidCultureResult: String? = null,
    // ── Outcome ─────────────────────────────────────────────────────────────
    var isTBConfirmed: Boolean? = null,
    var isConfirmed: Boolean = false,
    // TrueNat (MTB) & RIF order lifecycle redesign: "Confirmed DR-TB Case" must be
    // distinguishable from the generic isConfirmed/isTBConfirmed above (set for RIF DR TB only).
    var isDrTbConfirmed: Boolean? = null,
    // ── Device Orders / Integration ─────────────────────────────────────────
    var xrayOrderId: String? = null,
    var xrayOrderStatus: String? = null,
    var trueNatOrderId: String? = null,
    var trueNatOrderStatus: String? = null,
    var trueNatRifResult: String? = null,
    var rifOrderId: String? = null,
    var rifOrderStatus: String? = null,
    // ── Meta ─────────────────────────────────────────────────────────────────
    var latitude: Double? = null,
    var longitude: Double? = null,
    var address: String? = null,
    var serverUpdatedDate: Long? = null,
    var syncState: SyncState = SyncState.UNSYNCED,

    // ── Error Msg ─────────────────────────────────────────────────────────────────
    var errorMsgXray: String? = null,
    var errorMsgTrueNat : String? = null,
    var errorMsgRif : String? = null,

    // ── Manual result offline-first sync ────────────────────────────────────────
    // True when a manually-entered result/not-conducted-reason for this test type was
    // written locally (chestXRayResult/naatResult/trueNatRifResult or the matching
    // reasonNotConductedX field already holds it) but the order/manualResult call that
    // confirms it with the backend hasn't succeeded yet — set by TBRepo.submitManualResult()
    // when it falls back to its offline-first path, cleared once that call is replayed
    // successfully (DiagnosticResultPollWorker's retry sweep).
    var xrayManualResultPendingSync: Boolean? = null,
    var trueNatManualResultPendingSync: Boolean? = null,
    var rifManualResultPendingSync: Boolean? = null

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
        xrayOrderId = xrayOrderId,
        xrayOrderStatus = xrayOrderStatus,
        trueNatOrderId = trueNatOrderId,
        trueNatOrderStatus = trueNatOrderStatus,
        trueNatRifResult = trueNatRifResult,
        rifOrderId = rifOrderId,
        rifOrderStatus = rifOrderStatus,
        latitude = latitude,
        longitude = longitude,
        address = address
    )
}
