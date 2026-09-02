package org.piramalswasthya.stoptb.model

// SHOW_ALL only ever arises from union(): REGISTRAR_ROWS_ONLY and COUNSELLING_ROWS_ONLY are
// each a strict whitelist of mutually exclusive rows, so a Registrar+Counsellor user can't be
// represented by either single-role value — showing everything is the only correct union.
enum class SyncRowFilter { REGISTRAR_ROWS_ONLY, COUNSELLING_ROWS_ONLY, ALL_EXCEPT_COUNSELLING, SHOW_ALL }
enum class ExamineRowSet { ANTHROPOMETRY_AND_TB_SCREENING_ONLY, ALL_FOUR }
enum class ExamineDenominatorRule { REGISTRAR_TWO, COUNSELLING_DYNAMIC, GENERIC_FOUR }

// NONE = module not reachable at all. VIEW = existing records/forms can be opened and read but
// not created/saved. FULL = View+Add+Edit+Update, per the acceptance criteria's own bundling —
// it never distinguishes Add from Edit from Update, so a 3-level enum is sufficient. Ordinal
// order matters: it's used as the permissiveness ordering in union()'s maxPermission().
enum class Permission { NONE, VIEW, FULL }

data class ModulePrivilege(

    //Controls which primary feature modules/icons are visible on the app's Main Home Dashboard Screen
    //for a SINGLE-role user — preserves the app's existing/legacy card set unchanged.
    val homeModules: Set<AppModule>,

    //Controls which Home-screen cards are shown for this role's bottom-nav tab when the user has
    //MULTIPLE assigned roles — a narrower, role-exclusive set per the product spec, independent of
    //[homeModules]. Only consulted when RoleManager.assignedRoles.size > 1.
    val multiRoleHomeModules: Set<AppModule>,

    //Determines if the Counselling synchronization progress indicator row is shown in the sync progress panel.
    val syncShowCounsellingStatusRow: Boolean,

    //Filters which categories of unsynced database items appear inside the Synchronization bottom sheet.
    val syncBottomSheetRowFilter: SyncRowFilter,

    //Configures the forms shown inside the "Examine Beneficiary" Bottom Sheet checklist.
    val examineRowSet: ExamineRowSet,

    //Controls the visual ordering of form buttons in the Examine checklist UI.
    val examineReorderTbScreeningBeforeAnthropometry: Boolean,

    //Enforces clinical prerequisite rules by locking downstream forms until the entry screening form is completed.
    val examineLockGeneralFormsBehindTbScreening: Boolean,

    //Displays or hides the Contact Tracing form checklists within the Examine screen.
    val examineShowContactTracingRows: Boolean,

    //Determines the target denominator (e.g., X / Y) shown on the beneficiary progress cards.
    val examineDenominatorRule: ExamineDenominatorRule,
    val canActOnReferral: Boolean,
    val showRegisterSpouseButtons: Boolean,
    val showTbConfirmedCounsellingUi: Boolean,
    val showAbhaButton: Boolean,
    val showCallButton: Boolean,
    val showExamineButtonDefault: Boolean,
    val allowQuickRefresh: Boolean,

    // Gap 2 — real Add/Edit/View/Update enforcement per the acceptance criteria's per-module
    // table. Gates the "Add new" entry points (visible only at FULL) and the Submit/Save button
    // on an existing record's form (locked whenever permission != FULL), following the same
    // "gate the submit button only" pattern TBScreeningFormFragment/GeneralOpdFormFragment
    // already used for their own viewOnly flag — see RoleManager/RoleModuleConfig docs.
    //
    // General Examination and General OPD forms deliberately have NO Permission field here:
    // per the acceptance table only Nurse has any access to them (always FULL — there is no
    // Nurse-View-only case), and Registrar/Counsellor have no entry at all (NONE). Since NONE
    // is already enforced by examineRowSet hiding those rows entirely (only Nurse's presence in
    // a role union ever produces ExamineRowSet.ALL_FOUR), a dedicated permission field would be
    // redundant — modeling only what's actually reachable, per this file's existing convention.
    val householdPermission: Permission,
    val beneficiaryPermission: Permission,
    val nonHouseholdPermission: Permission,
    val anthropometryPermission: Permission,
    val tbScreeningPermission: Permission
) {
    companion object {

        /**
         * Combines the [ModulePrivilege] of every role a multi-role user is assigned —
         * every field except [homeModules]/[multiRoleHomeModules] becomes the union (most
         * permissive) across all assigned roles, not just whichever bottom-nav tab is active.
         * [homeModules]/[multiRoleHomeModules] are deliberately zeroed out here (not unioned)
         * — Home-card display stays tied to the active tab via
         * RoleManager.privilegesForActiveRole(), a genuinely different concern. Reading
         * homeModules off a unioned ModulePrivilege is a misuse this makes fail loudly
         * (empty set) rather than silently returning a plausible-looking wrong answer.
         */
        fun union(privileges: List<ModulePrivilege>): ModulePrivilege {
            require(privileges.isNotEmpty()) { "union() requires at least one ModulePrivilege" }
            return privileges.reduce { a, b ->
                ModulePrivilege(
                    homeModules = emptySet(),
                    multiRoleHomeModules = emptySet(),
                    syncShowCounsellingStatusRow = a.syncShowCounsellingStatusRow || b.syncShowCounsellingStatusRow,
                    syncBottomSheetRowFilter = combineSyncRowFilter(a.syncBottomSheetRowFilter, b.syncBottomSheetRowFilter),
                    examineRowSet = if (a.examineRowSet == ExamineRowSet.ALL_FOUR || b.examineRowSet == ExamineRowSet.ALL_FOUR)
                        ExamineRowSet.ALL_FOUR else ExamineRowSet.ANTHROPOMETRY_AND_TB_SCREENING_ONLY,
                    examineReorderTbScreeningBeforeAnthropometry = a.examineReorderTbScreeningBeforeAnthropometry || b.examineReorderTbScreeningBeforeAnthropometry,
                    examineLockGeneralFormsBehindTbScreening = a.examineLockGeneralFormsBehindTbScreening || b.examineLockGeneralFormsBehindTbScreening,
                    examineShowContactTracingRows = a.examineShowContactTracingRows || b.examineShowContactTracingRows,
                    examineDenominatorRule = combineDenominatorRule(a.examineDenominatorRule, b.examineDenominatorRule),
                    canActOnReferral = a.canActOnReferral || b.canActOnReferral,
                    showRegisterSpouseButtons = a.showRegisterSpouseButtons || b.showRegisterSpouseButtons,
                    showTbConfirmedCounsellingUi = a.showTbConfirmedCounsellingUi || b.showTbConfirmedCounsellingUi,
                    showAbhaButton = a.showAbhaButton || b.showAbhaButton,
                    showCallButton = a.showCallButton || b.showCallButton,
                    showExamineButtonDefault = a.showExamineButtonDefault || b.showExamineButtonDefault,
                    allowQuickRefresh = a.allowQuickRefresh || b.allowQuickRefresh,
                    householdPermission = maxPermission(a.householdPermission, b.householdPermission),
                    beneficiaryPermission = maxPermission(a.beneficiaryPermission, b.beneficiaryPermission),
                    nonHouseholdPermission = maxPermission(a.nonHouseholdPermission, b.nonHouseholdPermission),
                    anthropometryPermission = maxPermission(a.anthropometryPermission, b.anthropometryPermission),
                    tbScreeningPermission = maxPermission(a.tbScreeningPermission, b.tbScreeningPermission)
                )
            }
        }

        private fun maxPermission(a: Permission, b: Permission): Permission =
            if (a.ordinal >= b.ordinal) a else b

        private fun combineSyncRowFilter(a: SyncRowFilter, b: SyncRowFilter): SyncRowFilter =
            if (a == b) a else SyncRowFilter.SHOW_ALL

        // This union answers "what's relevant in the GENERAL (non-Counselling-module) workflow"
        // — GENERIC_FOUR wins whenever Nurse is present, since Nurse's General Exam/OPD access
        // is always relevant there, matching examineRowSet's own OR-based combine (which already
        // stays ALL_FOUR whenever Nurse is present). COUNSELLING_DYNAMIC is deliberately lowest
        // priority here: it only produces a different (TPT-specific) answer when the caller is
        // actually inside the Counselling/TPT workflow, which is NOT a role-union question — see
        // RoleManager.examinePrivilegesFor(), which overrides both this field and examineRowSet
        // to Counselling's own values when showContactTracingForms is true, bypassing this union
        // entirely for that case. Previously COUNSELLING_DYNAMIC won unconditionally here, which
        // silently collapsed the denominator to 2 for any Registrar/Nurse+Counsellor combo even
        // on the general beneficiary list, contradicting examineRowSet's correct ALL_FOUR.
        private fun combineDenominatorRule(a: ExamineDenominatorRule, b: ExamineDenominatorRule): ExamineDenominatorRule {
            if (a == b) return a
            if (a == ExamineDenominatorRule.GENERIC_FOUR || b == ExamineDenominatorRule.GENERIC_FOUR) {
                return ExamineDenominatorRule.GENERIC_FOUR
            }
            return ExamineDenominatorRule.REGISTRAR_TWO
        }
    }
}
