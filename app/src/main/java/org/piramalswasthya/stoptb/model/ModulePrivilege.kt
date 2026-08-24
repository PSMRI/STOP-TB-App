package org.piramalswasthya.stoptb.model

enum class SyncRowFilter { REGISTRAR_ROWS_ONLY, COUNSELLING_ROWS_ONLY, ALL_EXCEPT_COUNSELLING }
enum class ExamineRowSet { ANTHROPOMETRY_AND_TB_SCREENING_ONLY, ALL_FOUR }
enum class ExamineDenominatorRule { REGISTRAR_TWO, COUNSELLING_DYNAMIC, GENERIC_FOUR }

data class ModulePrivilege(

    //Controls which primary feature modules/icons are visible on the app's Main Home Dashboard Screen.
    val homeModules: Set<AppModule>,

    //Toggle the visibility of summary statistics counter cards displayed in the Scheduler Dashboard (Task List).
    val schedulerShowNonHousehold: Boolean,
    val schedulerShowAllBenCard: Boolean,
    val schedulerShowTbCard: Boolean,
    val schedulerShowReferralsCard: Boolean,

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
    val allowQuickRefresh: Boolean
)
