package org.piramalswasthya.stoptb.adapters

import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagingDataAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms

class BenPagingAdapter(
    private val clickListener: BenListAdapter.BenClickListener? = null,
    private val showBeneficiaries: Boolean = false,
    private val showRegistrationDate: Boolean = false,
    private val showSyncIcon: Boolean = false,
    private val showAbha: Boolean = false,
    private val showCall: Boolean = false,
    private val role: Int? = 0,
    private val pref: PreferenceDao? = null,
    var context: FragmentActivity,
    private val roleManager: RoleManager? = null,
    private val isSoftDeleteEnabled: Boolean = false,
    private val showActionButtons: Boolean = false,
    private val showResultButton: Boolean = false,
    private val showAnthropometryButton: Boolean = false,
    private val showExamineButton: Boolean = true,
    private val showScreeningStatus: Boolean = false,   // ADD THIS
    private val showRedesignedCard: Boolean = false,
    private val source: Int = 0,
    private val showContactTracingForms: Boolean = false,
    private val showAddMemberButton: Boolean = false

) :
    PagingDataAdapter<BenBasicDomain, BenListAdapter.BenViewHolder>(BenListAdapter.BenDiffUtilCallBack) {

    // Sets: bind() checks every row against each of these (~15 lookups per row); with lists each
    // lookup was a linear scan. Each submit swaps in a new set (one pass over the list) and
    // rebinds only loaded rows whose membership changed - no full-set copies or diffs on Main.
    private var benIds: Set<Long> = emptySet()
    private var tbScreeningBenIds: Set<Long> = emptySet()
    private var generalOpdBenIds: Set<Long> = emptySet()
    private var anthropometryBenIds: Set<Long> = emptySet()
    private var unsyncedVitalBenIds: Set<Long> = emptySet()
    private var unsyncedTbScreeningBenIds: Set<Long> = emptySet()
    private var unsyncedGeneralOpdBenIds: Set<Long> = emptySet()
    private var syncingVitalBenIds: Set<Long> = emptySet()
    private var syncingTbScreeningBenIds: Set<Long> = emptySet()
    private var syncingGeneralOpdBenIds: Set<Long> = emptySet()
    private var diagnosisIds: Set<Long> = emptySet()
    private var contactFollowUpDoneIds: Set<Long> = emptySet()
    private var tptFollowUpDoneIds: Set<Long> = emptySet()
    private var tptEligibleIds: Set<Long> = emptySet()
    private val childCountMap = mutableMapOf<Long, Int>()
    private val householdMemberCountMap = mutableMapOf<Long, Int>()
    private val householdHeadNameMap = mutableMapOf<Long, String>()
    private val tbDiagnosticsList = mutableListOf<TbDiagnosticsCardFields>()

    /**
     * The same rows as [tbDiagnosticsList], keyed by benId, because that is the only shape
     * bind() reads them in. Without it every card falls back to an empty map and the X-Ray and
     * TrueNat tiles render "Unscreened" with a blank result for beneficiaries that do have one.
     */
    private val tbDiagnosticsByBenId = mutableMapOf<Long, TbDiagnosticsCardFields>()

    /** Set by [submitTBDiagnostics]; until then a missing row means "not loaded yet". */
    private var tbDiagnosticsLoaded = false

    private var tbScreeningMap: Map<Long, TbScreeningSymptoms> = emptyMap()
    private var retryingBenIds: Set<Long> = emptySet()

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ) = BenListAdapter.BenViewHolder.from(parent)

    override fun onBindViewHolder(holder: BenListAdapter.BenViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(
            item,
            clickListener,
            showAbha,
            showSyncIcon,
            showRegistrationDate,
            showBeneficiaries,
            role,
            showCall,
            isSoftDeleteEnabled,
            pref,
            context,
            benIds,
            tbScreeningBenIds,
            generalOpdBenIds,
            anthropometryBenIds,
            unsyncedVitalBenIds,
            unsyncedTbScreeningBenIds,
            unsyncedGeneralOpdBenIds,
            syncingVitalBenIds,
            syncingTbScreeningBenIds,
            syncingGeneralOpdBenIds,
            diagnosisIds,
            contactFollowUpDoneIds,
            tptFollowUpDoneIds,
            tptEligibleIds,
            childCountMap,
            showActionButtons = showActionButtons,
            showResultButton = showResultButton,
            showAnthropometryButton = showAnthropometryButton,
            showExamineButton = showExamineButton,
            tbDiagnosticsByBenId = tbDiagnosticsByBenId,
            tbDiagnosticsLoaded = tbDiagnosticsLoaded,
            tbScreeningMap = tbScreeningMap,
            showScreeningStatus = showScreeningStatus,   // ADD THIS
            showRedesignedCard = showRedesignedCard,
            householdMemberCountMap = householdMemberCountMap,
            householdHeadNameMap = householdHeadNameMap,
            source = source,
            retryingBenIds = retryingBenIds,
            showContactTracingForms = showContactTracingForms,
            roleManager = roleManager,
            showAddMemberButton = showAddMemberButton
        )
    }

    fun submitTBDiagnostics(list: List<TbDiagnosticsCardFields>) {
        // Diff instead of notifyDataSetChanged(), same pattern as the submit*() functions below
        // — a blanket refresh here rebinds every visible row on every emission, which was the
        // root cause of cards needing multiple taps to register.
        val oldByBenId = tbDiagnosticsList.associateBy { it.benId }
        val newByBenId = list.associateBy { it.benId }
        tbDiagnosticsList.clear()
        tbDiagnosticsList.addAll(list)
        tbDiagnosticsByBenId.clear()
        tbDiagnosticsByBenId.putAll(newByBenId)
        // A beneficiary with no diagnostics row at all appears in neither map, so the diff below
        // never names it; before the first emission such a card renders the "not loaded yet"
        // placeholder and would stay on that disabled placeholder for good. Only those rows need
        // the extra nudge - a blanket notifyDataSetChanged() here would rebind every visible row
        // and swallow a tap in flight, which is what the targeted diff exists to avoid.
        val firstLoad = !tbDiagnosticsLoaded
        tbDiagnosticsLoaded = true
        val changedBenIds = (oldByBenId.keys + newByBenId.keys)
            .filterTo(mutableSetOf()) { benId -> oldByBenId[benId] != newByBenId[benId] }
        if (changedBenIds.isNotEmpty() || firstLoad) {
            val items = snapshot()
            items.forEachIndexed { index, item ->
                if (item != null &&
                    (item.benId in changedBenIds || (firstLoad && item.benId !in newByBenId))
                ) {
                    notifyItemChanged(index)
                }
            }
        }
    }


    fun submitTbScreeningList(list: List<TbScreeningSymptoms>) {
        // Rebind only loaded rows whose screening changed (was notifyDataSetChanged on every
        // emission, i.e. every TB_SCREENING write rebound the whole visible list).
        val oldMap = tbScreeningMap
        val newMap = list.associateBy { screening -> screening.benId }
        tbScreeningMap = newMap
        notifyLoadedRowsWhere { benId -> oldMap[benId] != newMap[benId] }
    }

    fun submitBenIds(list: List<Long>) { benIds = swapIds(benIds, list) }
    fun submitTbScreeningBenIds(list: List<Long>) { tbScreeningBenIds = swapIds(tbScreeningBenIds, list) }
    fun submitGeneralOpdBenIds(list: List<Long>) { generalOpdBenIds = swapIds(generalOpdBenIds, list) }
    fun submitAnthropometryBenIds(list: List<Long>) { anthropometryBenIds = swapIds(anthropometryBenIds, list) }
    fun submitUnsyncedVitalBenIds(list: List<Long>) { unsyncedVitalBenIds = swapIds(unsyncedVitalBenIds, list) }
    fun submitUnsyncedTbScreeningBenIds(list: List<Long>) { unsyncedTbScreeningBenIds = swapIds(unsyncedTbScreeningBenIds, list) }
    fun submitUnsyncedGeneralOpdBenIds(list: List<Long>) { unsyncedGeneralOpdBenIds = swapIds(unsyncedGeneralOpdBenIds, list) }
    fun submitSyncingVitalBenIds(list: List<Long>) { syncingVitalBenIds = swapIds(syncingVitalBenIds, list) }
    fun submitSyncingTbScreeningBenIds(list: List<Long>) { syncingTbScreeningBenIds = swapIds(syncingTbScreeningBenIds, list) }
    fun submitSyncingGeneralOpdBenIds(list: List<Long>) { syncingGeneralOpdBenIds = swapIds(syncingGeneralOpdBenIds, list) }
    fun submitRetryingBenIds(list: List<Long>) { retryingBenIds = swapIds(retryingBenIds, list) }
    fun submitDiagnosisBenIds(list: List<Long>) { diagnosisIds = swapIds(diagnosisIds, list) }
    fun submitContactFollowUpDoneBenIds(list: List<Long>) { contactFollowUpDoneIds = swapIds(contactFollowUpDoneIds, list) }
    fun submitTptFollowUpDoneBenIds(list: List<Long>) { tptFollowUpDoneIds = swapIds(tptFollowUpDoneIds, list) }
    fun submitTptEligibleBenIds(list: List<Long>) { tptEligibleIds = swapIds(tptEligibleIds, list) }

    /**
     * Returns the new id set for [list] and rebinds the loaded rows whose membership changed.
     * The caller assigns the result before the rebind runs (notifyItemChanged only schedules it).
     */
    private fun swapIds(old: Set<Long>, list: List<Long>): Set<Long> {
        val updated: Set<Long> = if (list.isEmpty()) emptySet() else list.toHashSet()
        notifyLoadedRowsWhere { benId -> (benId in old) != (benId in updated) }
        return updated
    }

    /** Rebinds loaded rows for which [changed] is true; cost is the number of loaded rows. */
    private inline fun notifyLoadedRowsWhere(changed: (Long) -> Boolean) {
        snapshot().forEachIndexed { index, item ->
            if (item != null && changed(item.benId)) notifyItemChanged(index)
        }
    }

    fun submitChildCounts(map: Map<Long, Int>) {
        val old = childCountMap.toMap()
        childCountMap.clear()
        childCountMap.putAll(map)
        val changed = map.entries.filter { (k, v) -> old[k] != v }.map { it.key }.toSet() +
            old.entries.filter { (k, v) -> map[k] != v }.map { it.key }.toSet()
        if (changed.isNotEmpty()) {
            val items = snapshot()
            items.forEachIndexed { index, item ->
                if (item != null && item.benId in changed) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun submitHouseholdMemberCounts(map: Map<Long, Int>) {
        val old = householdMemberCountMap.toMap()
        householdMemberCountMap.clear()
        householdMemberCountMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            snapshot().forEachIndexed { index, item ->
                if (item != null && item.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

    fun submitHouseholdHeadNames(map: Map<Long, String>) {
        val old = householdHeadNameMap.toMap()
        householdHeadNameMap.clear()
        householdHeadNameMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            snapshot().forEachIndexed { index, item ->
                if (item != null && item.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

}
