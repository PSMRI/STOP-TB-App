package org.piramalswasthya.stoptb.database.room

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQL that keeps TB_BEN_SUMMARY ([org.piramalswasthya.stoptb.model.TbBenSummary]) in step with
 * the TB tables. Each column repeats the rule of the list it serves (BenSearchQueries source
 * branches, ClinicalAssessmentSql, TbCaseSql); TbBenSummaryEquivalenceTest checks that the lists
 * built on this table return the same rows, in the same order, as those rules.
 *
 * Triggers recompute one beneficiary's row whenever one of their source rows is inserted,
 * deleted, or updated in a column the rules read. Updates of other columns (syncState after a
 * push) do not fire them.
 */
object TbBenSummarySql {

    const val TABLE = "TB_BEN_SUMMARY"

    private const val PRESUMPTIVE_XRAY = "('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')"
    private const val POSITIVE_NAAT = "('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')"
    private const val NEGATIVE_NAAT = "('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')"
    private const val CA_XRAY_A = "('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')"
    private const val CA_XRAY_C = "('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE','ABNORMAL (NON-TB PRESUMPTIVE)','ABNORMAL','POSITIVE')"
    private const val CA_NAAT_NEG = "('TB NEGATIVE','MTB NOT DETECTED')"
    private const val CA_NAAT_POS2 = "('TB POSITIVE','MTB DETECTED','MTB POSITIVE','POSITIVE','DETECTED')"
    private const val CA_NAAT_NEG2 = "('TB NEGATIVE','MTB NOT DETECTED','MTB NEGATIVE','NEGATIVE','NOT DETECTED')"

    /** Table each alias used below refers to. */
    private val ALIASES = mapOf(
        "s" to "TB_SCREENING", "scr" to "TB_SCREENING", "tbs" to "TB_SCREENING",
        "ts" to "TB_SUSPECTED", "sus" to "TB_SUSPECTED", "su" to "TB_SUSPECTED",
        "td" to "TB_DIAGNOSTICS", "dx" to "TB_DIAGNOSTICS", "d2" to "TB_DIAGNOSTICS", "d" to "TB_DIAGNOSTICS",
        "v" to "BEN_VITALS",
        "tc" to "TB_CONFIRMED_TREATMENT",
    )

    private val SOURCE_TABLES = listOf("TB_SCREENING", "TB_SUSPECTED", "TB_DIAGNOSTICS", "BEN_VITALS", "TB_CONFIRMED_TREATMENT")

    private fun ex(table: String, alias: String, cond: String) =
        "EXISTS (SELECT 1 FROM $table $alias WHERE $alias.benId = x.benId AND $cond)"

    private fun any(table: String, alias: String) =
        "EXISTS (SELECT 1 FROM $table $alias WHERE $alias.benId = x.benId)"

    private fun u(expr: String) = "UPPER(TRIM(IFNULL($expr, '')))"

    private fun risk(a: String) =
        "(TRIM(IFNULL($a.keyPopulationRiskFactors,'')) != '' AND REPLACE(REPLACE(UPPER($a.keyPopulationRiskFactors), 'NOT_APPLICABLE', ''), '|', '') != '')"

    /** Order tier of the latest diagnostics row `d`: same CASE as the X-ray / TrueNat list order. */
    private fun tier(status: String, orderId: String) =
        "CASE IFNULL(d.$status, '') WHEN 'FAILED' THEN 0 WHEN 'MANUAL_ENTRY' THEN 0" +
            " WHEN '' THEN (CASE WHEN d.$orderId IS NOT NULL THEN 1 ELSE 2 END)" +
            " WHEN 'COMPLETED' THEN 3 WHEN 'CLOSED' THEN 4 ELSE 1 END"

    private fun key(status: String) =
        "(CASE WHEN d.$status IN ('FAILED', 'MANUAL_ENTRY') THEN -1 ELSE 1 END) * COALESCE(x.lastVisit, 0)"

    private const val SYMPTOMATIC = "(scr.coughMoreThan2Weeks = 1 OR scr.bloodInSputum = 1" +
        " OR scr.feverMoreThan2Weeks = 1 OR scr.riseOfFever = 1 OR scr.lossOfAppetite = 1" +
        " OR scr.lossOfWeight = 1 OR scr.nightSweats = 1 OR scr.historyOfTb = 1" +
        " OR scr.takingAntiTBDrugs = 1 OR scr.familySufferingFromTB = 1" +
        " OR scr.chestPain = 1 OR scr.shortnessOfBreath = 1 OR scr.fatigue = 1 OR scr.otherSymptoms = 1" +
        " OR scr.failureToGainWeightInChildren = 1 OR scr.decreasedActivityOrPlayfulnessInChildren = 1)"

    private const val ABNORMAL_VITALS = "((v.pulseRate IS NOT NULL AND v.pulseRate < 60)" +
        " OR (v.pulseRate IS NOT NULL AND v.pulseRate > 90)" +
        " OR (v.bpSystolic IS NOT NULL AND v.bpSystolic < 90)" +
        " OR (v.bpSystolic IS NOT NULL AND v.bpSystolic >= 140)" +
        " OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic < 60)" +
        " OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic >= 90)" +
        " OR (v.rbs IS NOT NULL AND v.rbs >= 100)" +
        " OR (v.pallorId = 1 OR UPPER(TRIM(IFNULL(v.pallor, ''))) IN ('PRESENT', 'YES'))" +
        " OR (v.icterusId = 1 OR UPPER(TRIM(IFNULL(v.icterus, ''))) IN ('PRESENT', 'YES'))" +
        " OR (v.cyanosisId = 1 OR UPPER(TRIM(IFNULL(v.cyanosis, ''))) IN ('PRESENT', 'YES'))" +
        " OR (v.clubbingId = 1 OR UPPER(TRIM(IFNULL(v.clubbing, ''))) IN ('PRESENT', 'YES'))" +
        " OR (v.lymphadenopathyId = 1 OR UPPER(TRIM(IFNULL(v.lymphadenopathy, ''))) IN ('PRESENT', 'YES'))" +
        " OR (v.oedemaId = 1 OR UPPER(TRIM(IFNULL(v.oedema, ''))) IN ('PRESENT', 'YES')))"

    /** Column name to the expression that computes it for beneficiary x.benId. Order = insert order. */
    internal val COLUMNS: LinkedHashMap<String, String> = linkedMapOf(
        "hasScreening" to any("TB_SCREENING", "s"),
        "pregVitals" to ex("BEN_VITALS", "v", "v.keyPopulationRiskFactors LIKE '%PREGNANCY%'"),
        // BenSearchQueries source 6
        "xrayList" to "(" + ex("TB_SUSPECTED", "ts", "ts.isChestXRayDone IS NOT NULL") +
            " OR " + ex("TB_DIAGNOSTICS", "td", "td.isChestXRayDone IS NOT NULL") +
            " OR " + any("TB_SCREENING", "s") + ")",
        "xrayTier" to tier("xrayOrderStatus", "xrayOrderId"),
        "xrayKey" to key("xrayOrderStatus"),
        // BenSearchQueries source 7, all but the pregnancy branch (that one needs reproductiveStatusId)
        "tnOther" to "(" +
            ex("TB_SUSPECTED", "ts", "(ts.isNaatConducted IS NOT NULL OR UPPER(IFNULL(ts.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY)") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "td",
                "(td.trueNatOrderId IS NOT NULL OR td.trueNatOrderStatus IS NOT NULL OR td.isNaatConducted IS NOT NULL" +
                    " OR UPPER(IFNULL(td.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY)"
            ) +
            " OR " + ex(
                "TB_SCREENING", "tbs",
                "(tbs.recommendedForTruenatTest = 1 OR tbs.referredForSputumCollection = 1 OR tbs.bloodInSputum = 1" +
                    " OR tbs.coughMoreThan2Weeks = 1 OR tbs.feverMoreThan2Weeks = 1 OR tbs.riseOfFever = 1" +
                    " OR tbs.lossOfAppetite = 1 OR tbs.lossOfWeight = 1 OR tbs.nightSweats = 1 OR tbs.historyOfTb = 1" +
                    " OR tbs.takingAntiTBDrugs = 1 OR tbs.familySufferingFromTB = 1 OR UPPER(IFNULL(tbs.asymptomatic, '')) = 'NO')"
            ) + ")",
        "tnTier" to tier("trueNatOrderStatus", "trueNatOrderId"),
        "tnKey" to key("trueNatOrderStatus"),
        // BenSearchQueries source 5, all but BENEFICIARY.temperature
        "hwc" to "(" + ex("BEN_VITALS", "v", ABNORMAL_VITALS) +
            " OR " + ex("TB_SUSPECTED", "ts", "UPPER(IFNULL(ts.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY AND UPPER(IFNULL(ts.naatResult, '')) IN $NEGATIVE_NAAT") +
            " OR " + ex("TB_DIAGNOSTICS", "td", "UPPER(IFNULL(td.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY AND UPPER(IFNULL(td.naatResult, '')) IN $NEGATIVE_NAAT") + ")",
        // LIQUID_CULTURE_REFERRAL_CONDITION (source 8)
        "lc" to "(" + ex("TB_SUSPECTED", "ts", "ts.isLiquidCultureConducted IS NOT NULL") +
            " OR " + ex("TB_DIAGNOSTICS", "td", "td.isLiquidCultureConducted IS NOT NULL") +
            " OR " + ex(
                "TB_SCREENING", "tbs",
                "(tbs.recommendedForLiquidCultureTest = 1 OR tbs.historyOfTb = 1 OR tbs.takingAntiTBDrugs = 1 OR tbs.familySufferingFromTB = 1)"
            ) + ")",
        // ClinicalAssessmentSql.MATCHING_BEN_IDS: first three branches
        "caFixed" to "(" +
            ex("TB_SUSPECTED", "su", "${u("su.chestXRayResult")} IN $CA_XRAY_A AND ${u("su.naatResult")} IN $CA_NAAT_NEG") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "d2",
                "((${u("d2.chestXRayResult")} IN $CA_XRAY_A AND ${u("d2.naatResult")} IN $CA_NAAT_NEG) OR ${u("d2.trueNatRifResult")} = 'INDETERMINATE')"
            ) +
            " OR ((" + ex("TB_DIAGNOSTICS", "d2", "${u("d2.chestXRayResult")} IN $CA_XRAY_C") +
            " OR " + ex("TB_SUSPECTED", "su", "${u("su.chestXRayResult")} IN $CA_XRAY_C") + ")" +
            " AND NOT (" + ex("TB_DIAGNOSTICS", "d2", "${u("d2.naatResult")} IN $CA_NAAT_POS2") +
            " OR " + ex("TB_SUSPECTED", "su", "${u("su.naatResult")} IN $CA_NAAT_POS2") + ")" +
            " AND (" + ex("TB_DIAGNOSTICS", "d2", "${u("d2.naatResult")} IN $CA_NAAT_NEG2") +
            " OR " + ex("TB_SUSPECTED", "su", "${u("su.naatResult")} IN $CA_NAAT_NEG2") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "d2",
                "(TRIM(IFNULL(d2.reasonNotConductedNaat,'')) != '' OR (d2.isNaatConducted = 0 AND UPPER(TRIM(IFNULL(d2.trueNatOrderStatus,''))) = 'CLOSED'))"
            ) +
            " OR " + ex("TB_SUSPECTED", "su", "TRIM(IFNULL(su.reasonForRefusalMTB,'')) != ''") + ")))",
        // ClinicalAssessmentSql.MATCHING_BEN_IDS: "no chest X-ray" branch, split around the ben columns
        "caD0" to "((" + any("TB_SCREENING", "s") + " OR " + any("TB_SUSPECTED", "su") + " OR " + any("TB_DIAGNOSTICS", "d2") + ")" +
            " AND NOT " + ex("TB_DIAGNOSTICS", "d2", "(TRIM(IFNULL(d2.chestXRayResult,'')) != '' OR d2.isChestXRayDone = 1)") +
            " AND NOT " + ex("TB_SUSPECTED", "su", "(TRIM(IFNULL(su.chestXRayResult,'')) != '' OR su.isChestXRayDone = 1)") + ")",
        "caSym" to "(" + ex(
            "TB_SCREENING", "s",
            "(s.coughMoreThan2Weeks = 1 OR s.bloodInSputum = 1 OR s.feverMoreThan2Weeks = 1 OR s.riseOfFever = 1" +
                " OR s.lossOfAppetite = 1 OR s.lossOfWeight = 1 OR s.nightSweats = 1 OR s.historyOfTb = 1" +
                " OR s.takingAntiTBDrugs = 1 OR s.familySufferingFromTB = 1 OR s.chestPain = 1 OR s.shortnessOfBreath = 1" +
                " OR s.fatigue = 1 OR s.failureToGainWeightInChildren = 1 OR s.decreasedActivityOrPlayfulnessInChildren = 1" +
                " OR s.otherSymptoms = 1 OR UPPER(TRIM(IFNULL(s.asymptomatic,''))) = 'NO')"
        ) +
            " OR " + ex("TB_SUSPECTED", "su", "su.hasSymptoms = 1") +
            " OR " + ex("TB_SCREENING", "s", risk("s")) +
            " OR " + ex("BEN_VITALS", "v", risk("v")) + ")",
        "caElig" to "(" + ex("TB_SCREENING", "s", "UPPER(IFNULL(s.keyPopulationRiskFactors,'')) LIKE '%PREGNANCY%'") +
            " OR " + ex("BEN_VITALS", "v", "UPPER(IFNULL(v.keyPopulationRiskFactors,'')) LIKE '%PREGNANCY%'") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "d2",
                "(d2.isChestXRayDone = 0 OR TRIM(IFNULL(d2.reasonNotConductedChestXray,'')) != ''" +
                    " OR TRIM(IFNULL(d2.reasonNotConductedChestXrayOther,'')) != '' OR TRIM(IFNULL(d2.reasonForDenialChestXray,'')) != ''" +
                    " OR TRIM(IFNULL(d2.reasonForDenialChestXrayOther,'')) != '')"
            ) +
            " OR " + ex("TB_SUSPECTED", "su", "(su.isChestXRayDone = 0 OR TRIM(IFNULL(su.reasonForRefusalXray,'')) != '')") + ")",
        // TbCaseSql.PRESUMPTIVE, ignoring visit dates
        "presumptive" to "(" + any("TB_SCREENING", "scr") +
            " AND (" + ex("TB_SCREENING", "scr", SYMPTOMATIC) +
            " OR " + ex("TB_SUSPECTED", "sus", "UPPER(IFNULL(sus.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY") +
            " OR " + ex("TB_DIAGNOSTICS", "dx", "UPPER(IFNULL(dx.chestXRayResult, '')) IN $PRESUMPTIVE_XRAY") + ")" +
            " AND NOT " + ex(
                "TB_SUSPECTED", "sus",
                "(IFNULL(sus.isConfirmed, 0) = 1 OR IFNULL(sus.isTBConfirmed, 0) = 1" +
                    " OR UPPER(IFNULL(sus.naatResult, '')) IN $POSITIVE_NAAT OR UPPER(IFNULL(sus.liquidCultureResult, '')) = 'POSITIVE')"
            ) +
            " AND NOT " + ex(
                "TB_DIAGNOSTICS", "dx",
                "(IFNULL(dx.isConfirmed, 0) = 1 OR IFNULL(dx.isTBConfirmed, 0) = 1" +
                    " OR UPPER(IFNULL(dx.naatResult, '')) IN $POSITIVE_NAAT OR UPPER(IFNULL(dx.liquidCultureResult, '')) = 'POSITIVE')"
            ) +
            " AND NOT " + any("TB_CONFIRMED_TREATMENT", "tc") + ")",
        // BenSearchQueries source 9 (narrower than TbCaseSql.CONFIRMED on purpose)
        "tptIndex" to "(" + ex("TB_SUSPECTED", "ts", "ts.isTbConfirmed = 1") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "td",
                "(td.isTbConfirmed = 1 OR UPPER(IFNULL(td.naatResult, '')) IN $POSITIVE_NAAT OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE')"
            ) + ")",
        // TBDao.getScreeningsForKnownTbContacts
        "knownTbCase" to "(" + ex("TB_SUSPECTED", "ts", "(IFNULL(ts.isTBConfirmed, 0) = 1 OR IFNULL(ts.isConfirmed, 0) = 1)") +
            " OR " + ex(
                "TB_DIAGNOSTICS", "td",
                "(IFNULL(td.isTBConfirmed, 0) = 1 OR IFNULL(td.isConfirmed, 0) = 1" +
                    " OR UPPER(IFNULL(td.naatResult, '')) IN $POSITIVE_NAAT OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE')"
            ) +
            " OR " + any("TB_CONFIRMED_TREATMENT", "tc") + ")",
    )

    /** Summary rows for the beneficiary ids returned by [idsSql] (one column named benId). */
    private fun selectFor(idsSql: String) =
        "SELECT x.benId, " + COLUMNS.values.joinToString(", ") +
            " FROM (SELECT i.benId, (SELECT MAX(m) FROM (" +
            "SELECT MAX(visitDate) m FROM TB_SCREENING WHERE benId = i.benId" +
            " UNION ALL SELECT MAX(visitDate) FROM TB_DIAGNOSTICS WHERE benId = i.benId" +
            " UNION ALL SELECT MAX(visitDate) FROM TB_SUSPECTED WHERE benId = i.benId)) AS lastVisit" +
            " FROM ($idsSql) i) x" +
            " LEFT JOIN TB_DIAGNOSTICS d ON d.id = (SELECT id FROM TB_DIAGNOSTICS WHERE benId = x.benId ORDER BY id DESC LIMIT 1)"

    private val INSERT_HEAD = "INSERT OR REPLACE INTO $TABLE (beneficiaryId, ${COLUMNS.keys.joinToString(", ")}) "

    private fun refresh(benExpr: String) = INSERT_HEAD + selectFor("SELECT $benExpr AS benId")

    /** Fills the table for every beneficiary that has a row in a source table. */
    val FILL: String = INSERT_HEAD + selectFor(SOURCE_TABLES.joinToString(" UNION ") { "SELECT benId FROM $it" })

    /** Columns of [table] the rules read; an UPDATE touching none of them leaves the summary as is. */
    internal fun columnsRead(table: String): List<String> {
        val cols = sortedSetOf("benId")
        if (table in setOf("TB_SCREENING", "TB_SUSPECTED", "TB_DIAGNOSTICS")) cols += "visitDate"
        val text = COLUMNS.values.joinToString(" ")
        Regex("\\b([a-z0-9]+)\\.([A-Za-z0-9_]+)").findAll(text).forEach { m ->
            if (ALIASES[m.groupValues[1]] == table) cols += m.groupValues[2]
        }
        return cols.toList()
    }

    private fun triggerName(table: String, op: String) = "trg_tbsum_${table.lowercase()}_$op"

    val TRIGGERS: List<String> = SOURCE_TABLES.flatMap { t ->
        val updateOf = columnsRead(t).joinToString(", ") { "`$it`" }
        listOf(
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(t, "ins")} AFTER INSERT ON `$t` BEGIN ${refresh("NEW.benId")}; END",
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(t, "upd")} AFTER UPDATE OF $updateOf ON `$t` BEGIN ${refresh("NEW.benId")}; END",
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(t, "upd_old")} AFTER UPDATE OF `benId` ON `$t`" +
                " WHEN OLD.benId != NEW.benId BEGIN ${refresh("OLD.benId")}; END",
            "CREATE TRIGGER IF NOT EXISTS ${triggerName(t, "del")} AFTER DELETE ON `$t` BEGIN ${refresh("OLD.benId")}; END",
        )
    }

    /** Creates the triggers if missing. Idempotent; runs on every open so a fresh install gets them too. */
    fun createTriggers(db: SupportSQLiteDatabase) {
        TRIGGERS.forEach { db.execSQL(it) }
    }

    /** Database callback that installs the triggers on open (app and tests). */
    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) = createTriggers(db)
    }
}
