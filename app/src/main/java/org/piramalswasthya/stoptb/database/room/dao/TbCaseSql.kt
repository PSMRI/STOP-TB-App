package org.piramalswasthya.stoptb.database.room.dao

/**
 * The one definition of "Presumptive TB" and "TB Confirmed", shared by the lists, their home-card
 * counts and the dashboard tiles so the numbers can't disagree.
 *
 * Fragments are plain SQL, spliced into @Query strings. They expect these table aliases:
 * `scr` = TB_SCREENING, `sus` = TB_SUSPECTED, `dx` = TB_DIAGNOSTICS (sus and dx LEFT JOINed).
 *
 * Rules (product, 2026-11-10):
 * - Presumptive: any TB Screening symptom or "Other Details" answer is Yes (the same 10 answers as
 *   TBScreeningDataset.TbScreeningUtils.isPresumptive), or the chest X-ray is Positive /
 *   TB Presumptive / Abnormal but not TB Presumptive - and the person is not confirmed and not
 *   on treatment.
 * - Confirmed: either confirmed flag on the suspected or diagnostics record, or NAAT/TrueNat
 *   positive (MTB Detected), or liquid culture positive. The app sets both flags when MTB is
 *   detected; checking the results too covers records synced from elsewhere.
 */
object TbCaseSql {

    const val SYMPTOMATIC = "(scr.coughMoreThan2Weeks = 1 OR scr.bloodInSputum = 1" +
        " OR scr.feverMoreThan2Weeks = 1 OR scr.riseOfFever = 1 OR scr.lossOfAppetite = 1" +
        " OR scr.lossOfWeight = 1 OR scr.nightSweats = 1 OR scr.historyOfTb = 1" +
        " OR scr.takingAntiTBDrugs = 1 OR scr.familySufferingFromTB = 1)"

    private const val PRESUMPTIVE_XRAY_VALUES = "('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')"

    const val XRAY_PRESUMPTIVE = "(UPPER(IFNULL(sus.chestXRayResult, '')) IN " + PRESUMPTIVE_XRAY_VALUES +
        " OR UPPER(IFNULL(dx.chestXRayResult, '')) IN " + PRESUMPTIVE_XRAY_VALUES + ")"

    /** Confirmed through the suspected record (its own visit date applies on the dashboard). */
    const val SUSPECTED_CONFIRMED = "(IFNULL(sus.isConfirmed, 0) = 1 OR IFNULL(sus.isTBConfirmed, 0) = 1)"

    /** Confirmed through the diagnostics record: a flag or a positive lab result. */
    const val DIAGNOSTICS_CONFIRMED = "(IFNULL(dx.isConfirmed, 0) = 1 OR IFNULL(dx.isTBConfirmed, 0) = 1" +
        " OR UPPER(IFNULL(dx.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')" +
        " OR UPPER(IFNULL(dx.liquidCultureResult, '')) = 'POSITIVE')"

    const val CONFIRMED = "(" + SUSPECTED_CONFIRMED + " OR " + DIAGNOSTICS_CONFIRMED + ")"

    const val PRESUMPTIVE = "((" + SYMPTOMATIC + " OR " + XRAY_PRESUMPTIVE + ")" +
        " AND NOT " + CONFIRMED +
        " AND NOT EXISTS (SELECT 1 FROM TB_CONFIRMED_TREATMENT tc WHERE tc.benId = scr.benId))"
}
