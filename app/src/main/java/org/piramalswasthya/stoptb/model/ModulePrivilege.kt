package org.piramalswasthya.stoptb.model

// SHOW_ALL is a union-only result: REGISTRAR_ROWS_ONLY and COUNSELLING_ROWS_ONLY are mutually
// exclusive whitelists, so a Registrar+Counsellor user's row set can't be either one alone.
enum class SyncRowFilter { REGISTRAR_ROWS_ONLY, COUNSELLING_ROWS_ONLY, ALL_EXCEPT_COUNSELLING, SHOW_ALL }
enum class ExamineRowSet { ANTHROPOMETRY_AND_TB_SCREENING_ONLY, ALL_FOUR }
enum class ExamineDenominatorRule { REGISTRAR_TWO, COUNSELLING_DYNAMIC, GENERIC_FOUR }

// NONE = not reachable. VIEW = can open/read but not create/save. FULL = View+Add+Edit+Update
// (never split further). Ordinal order is used directly as the permissiveness ranking below.
enum class Permission { NONE, VIEW, FULL }

data class ModulePrivilege(

    // Home-screen cards for a SINGLE-role user.
    val homeModules: Set<AppModule>,

    // Home-screen cards for this role's bottom-nav tab when the user has MULTIPLE roles —
    // narrower than [homeModules]. Only used when RoleManager.assignedRoles.size > 1.
    val multiRoleHomeModules: Set<AppModule>,

    val syncShowCounsellingStatusRow: Boolean,
    val syncBottomSheetRowFilter: SyncRowFilter,

    // Which forms show in the Examine bottom sheet.
    val examineRowSet: ExamineRowSet,
    val examineReorderTbScreeningBeforeAnthropometry: Boolean,
    val examineLockGeneralFormsBehindTbScreening: Boolean,
    val examineShowContactTracingRows: Boolean,
    // The "x/y" denominator shown on the Examine button.
    val examineDenominatorRule: ExamineDenominatorRule,

    val canActOnReferral: Boolean,
    val showRegisterSpouseButtons: Boolean,
    val showTbConfirmedCounsellingUi: Boolean,
    val showAbhaButton: Boolean,
    val showCallButton: Boolean,
    val showExamineButtonDefault: Boolean,
    val allowQuickRefresh: Boolean,

    // Add/Edit/View enforcement per module. No field for General Exam/OPD: only Nurse ever has
    // access (always FULL), and NONE is already enforced by examineRowSet hiding those rows.
    val householdPermission: Permission,
    val beneficiaryPermission: Permission,
    val nonHouseholdPermission: Permission,
    val anthropometryPermission: Permission,
    val tbScreeningPermission: Permission
) {
    companion object {

        /**
         * Combines every assigned role's privileges into one effective (most-permissive)
         * result. [homeModules]/[multiRoleHomeModules] are zeroed out — Home-card display stays
         * tab-scoped via RoleManager.privilegesForActiveRole(), not unioned.
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

        // GENERIC_FOUR wins whenever Nurse is present, matching examineRowSet's own combine.
        // COUNSELLING_DYNAMIC is lowest priority: it only applies inside the Counselling/TPT
        // workflow specifically, which RoleManager.examinePrivilegesFor() handles separately by
        // overriding this field directly — not through this general-context union.
        private fun combineDenominatorRule(a: ExamineDenominatorRule, b: ExamineDenominatorRule): ExamineDenominatorRule {
            if (a == b) return a
            if (a == ExamineDenominatorRule.GENERIC_FOUR || b == ExamineDenominatorRule.GENERIC_FOUR) {
                return ExamineDenominatorRule.GENERIC_FOUR
            }
            return ExamineDenominatorRule.REGISTRAR_TWO
        }
    }
}
