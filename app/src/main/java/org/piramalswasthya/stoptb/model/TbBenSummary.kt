package org.piramalswasthya.stoptb.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per beneficiary with TB data: which TB / Referral lists they belong to and their sort
 * keys, derived from TB_SCREENING, TB_SUSPECTED, TB_DIAGNOSTICS, BEN_VITALS and
 * TB_CONFIRMED_TREATMENT. Written only by the triggers in [org.piramalswasthya.stoptb.database.room.TbBenSummarySql];
 * the app never writes it directly.
 *
 * The lists used to work this out per beneficiary on every open (1M device DB: X-ray 52 s,
 * Clinical Assessment 63 s). With this table they read an index.
 */
@Entity(
    tableName = "TB_BEN_SUMMARY",
    indices = [
        Index(
            name = "ind_tbsum_xray",
            value = ["xrayTier", "xrayKey", "beneficiaryId"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC]
        ),
        Index(
            name = "ind_tbsum_tn",
            value = ["tnTier", "tnKey", "beneficiaryId"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC]
        ),
        Index(name = "ind_tbsum_hwc", value = ["hwc"]),
        Index(name = "ind_tbsum_lc", value = ["lc"]),
        Index(name = "ind_tbsum_ca", value = ["caFixed", "caD0"]),
        Index(name = "ind_tbsum_presumptive", value = ["presumptive"]),
        Index(name = "ind_tbsum_tpt", value = ["tptIndex"]),
        Index(name = "ind_tbsum_known_tb", value = ["knownTbCase"]),
    ]
)
data class TbBenSummary(
    @PrimaryKey @ColumnInfo(name = "beneficiaryId") val beneficiaryId: Long,
    /** Has at least one TB_SCREENING row. */
    val hasScreening: Int,
    /** A BEN_VITALS row lists PREGNANCY as a key-population risk factor. */
    val pregVitals: Int,
    /** TB part of the Digital Chest X-ray referral rule (source 6). */
    val xrayList: Int,
    /** X-ray order tier on the latest diagnostics row: 0 failed/manual, 1 in flight, 2 no order, 3 completed, 4 closed. */
    val xrayTier: Int,
    /** Latest TB visit date, negated for tier 0 so the oldest comes first there. */
    val xrayKey: Long,
    /** TB part of the TrueNat referral rule (source 7) other than the pregnancy branch. */
    val tnOther: Int,
    val tnTier: Int,
    val tnKey: Long,
    /** TB/vitals part of the HWC referral rule (source 5); temperature comes from BENEFICIARY. */
    val hwc: Int,
    /** Liquid Culture referral rule (source 8). */
    val lc: Int,
    /** Clinical Assessment branches that use TB rows only. */
    val caFixed: Int,
    /** Clinical Assessment "no chest X-ray" branch, TB-row part; ben columns are checked in the query. */
    val caD0: Int,
    val caSym: Int,
    val caElig: Int,
    /** Presumptive TB (TbCaseSql.PRESUMPTIVE, any visit date). */
    val presumptive: Int,
    /** Confirmed case that makes household members TPT-eligible (source 9). */
    val tptIndex: Int,
    /** Confirmed case for the "Contact of Known TB Patients" risk factor. */
    val knownTbCase: Int,
)
