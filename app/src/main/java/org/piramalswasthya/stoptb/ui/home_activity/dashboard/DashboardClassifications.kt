package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import androidx.annotation.StringRes
import org.piramalswasthya.stoptb.R

data class DashboardClassDef(
    @StringRes val labelRes: Int,
    val ratio: Double,
)

object DashboardClassifications {
    val palette = listOf(
        R.color.dashboard_class_1,
        R.color.dashboard_class_2,
        R.color.dashboard_class_3,
        R.color.dashboard_class_4,
        R.color.dashboard_class_5,
        R.color.dashboard_class_6,
    )

    val chestXray = listOf(
        DashboardClassDef(R.string.dashboard_class_xray_normal, 0.55),
        DashboardClassDef(R.string.dashboard_class_xray_abnormal, 0.20),
        DashboardClassDef(R.string.dashboard_class_xray_tb_presumptive, 0.20),
        DashboardClassDef(R.string.dashboard_class_xray_invalid, 0.0),
    )
    val sputum = listOf(
        DashboardClassDef(R.string.dashboard_class_sputum_collected, 0.85),
        DashboardClassDef(R.string.dashboard_class_sputum_not_collected, 0.10),
        DashboardClassDef(R.string.dashboard_class_sputum_rejected, 0.0),
    )
    val mtb = listOf(
        DashboardClassDef(R.string.dashboard_class_mtb_positive, 0.27),
        DashboardClassDef(R.string.dashboard_class_mtb_negative, 0.18),
        DashboardClassDef(R.string.dashboard_class_mtb_invalid, 0.04),
        DashboardClassDef(R.string.dashboard_class_mtb_neg_xray_normal, 0.28),
        DashboardClassDef(R.string.dashboard_class_mtb_neg_xray_abnormal, 0.13),
        DashboardClassDef(R.string.dashboard_class_mtb_neg_xray_presumptive, 0.0),
    )
    val rif = listOf(
        DashboardClassDef(R.string.dashboard_class_rif_dr, 0.08),
        DashboardClassDef(R.string.dashboard_class_rif_non_dr, 0.85),
        DashboardClassDef(R.string.dashboard_class_rif_indeterminate, 0.04),
        DashboardClassDef(R.string.dashboard_class_rif_invalid, 0.0),
    )
    val liquidCulture = listOf(
        DashboardClassDef(R.string.dashboard_class_lc_positive, 0.22),
        DashboardClassDef(R.string.dashboard_class_lc_negative, 0.55),
        DashboardClassDef(R.string.dashboard_class_lc_contaminated, 0.08),
        DashboardClassDef(R.string.dashboard_class_lc_invalid, 0.05),
        DashboardClassDef(R.string.dashboard_class_lc_pending, 0.0),
    )
    val clinical = listOf(
        DashboardClassDef(R.string.dashboard_class_clinical_diagnosed, 0.30),
        DashboardClassDef(R.string.dashboard_class_clinical_not_diagnosed, 0.35),
        DashboardClassDef(R.string.dashboard_class_clinical_pending, 0.15),
        DashboardClassDef(R.string.dashboard_class_clinical_referred, 0.12),
        DashboardClassDef(R.string.dashboard_class_clinical_dr, 0.0),
    )
    val confirmed = listOf(
        DashboardClassDef(R.string.dashboard_class_confirmed_micro, 0.55),
        DashboardClassDef(R.string.dashboard_class_confirmed_clinical, 0.20),
        DashboardClassDef(R.string.dashboard_class_confirmed_ds, 0.18),
        DashboardClassDef(R.string.dashboard_class_confirmed_dr, 0.0),
    )
    val tpt = listOf(
        DashboardClassDef(R.string.dashboard_class_tpt_eligible, 0.35),
        DashboardClassDef(R.string.dashboard_class_tpt_initiated, 0.25),
        DashboardClassDef(R.string.dashboard_class_tpt_ongoing, 0.15),
        DashboardClassDef(R.string.dashboard_class_tpt_completed, 0.12),
        DashboardClassDef(R.string.dashboard_class_tpt_discontinued, 0.05),
        DashboardClassDef(R.string.dashboard_class_tpt_not_eligible, 0.0),
    )

    fun split(count: Int, defs: List<DashboardClassDef>): List<Pair<DashboardClassDef, Int>> {
        val n = count.coerceAtLeast(0)
        var used = 0
        return defs.mapIndexed { index, def ->
            val value = if (index == defs.lastIndex) {
                (n - used).coerceAtLeast(0)
            } else {
                Math.round(n * def.ratio).toInt().also { used += it }
            }
            def to value
        }
    }
}
