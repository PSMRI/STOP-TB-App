package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.helpers.setToStartOfTheDay
import java.util.Calendar

class TBConfirmedDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val yesValue get() = resources.getStringArray(R.array.yes_no)[0]
    private val noValue get() = resources.getStringArray(R.array.yes_no)[1]

    private fun getOneYearBeforeCurrentDate(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private var suspectedVisitDate: Long = 0L
    private var treatmentAlreadyStarted: Boolean = false
    private var treatmentStartDateLong: Long = 0L
    private var lastFollowUpDateLong: Long = 0L
    private var nextFollowUpMonthStart: Long = 0L
    private var followUpCount = 0
    private var treatmentStartDateValue: Long? = null
    private var regimenTypeValue: String? = null


    // Old stored outcome -> new outcome (English). Cured is unchanged.
    private val legacyOutcomeMap = mapOf(
        "Death" to "Died",
        "Failed" to "Treatment Failure",
        "Lost to Follow-up (LFU)" to "Lost to Follow Up"
    )

    private val treatmentType = FormElement(
        id = 1,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.type_of_treatment),
        arrayId = R.array.tb_regimen_types,
        entries = resources.getStringArray(R.array.tb_regimen_types),
        required = true,
        hasDependants = true,
        isEnabled = true
    )

    private val dsTbRegimen = FormElement(
        id = 16, inputType = InputType.TEXT_VIEW, title = resources.getString(R.string.ds_tb_regimen),
        required = true, isEnabled = true
    )

    private val pmdtRegimen = FormElement(
        id = 17, inputType = InputType.DROPDOWN, title = resources.getString(R.string.pmdt_regimen),
        arrayId = R.array.tb_pmdt_regimens, entries = resources.getStringArray(R.array.tb_pmdt_regimens),
        required = true, isEnabled = true
    )

    private val treatmentStartDate = FormElement(
        id = 2,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.treatment_start_date),
        required = true,
        max = System.currentTimeMillis(),
        hasDependants = true,
        isEnabled = true
    )

    private val expectedTreatmentCompletionDate = FormElement(
        id = 3,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.expected_treatment_completion_date),
        required = false,
        hasDependants = true,
        isEnabled = true
    )

    private val followUpDate = FormElement(
        id = 4,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.followupdate),
        required = true,
        max = System.currentTimeMillis(),
        min = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000),
        hasDependants = true,
        isEnabled = false
    )

    private val monthlyFollowUpDone = FormElement(
        id = 5,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.monthly_follow_up_done),
        required = false,
        isEnabled = true
    )

    private val anyDiscomfort = FormElement(
        id = 7,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.any_discomfort),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true,
        isEnabled = true
    )

    private val sideEffects = FormElement(
        id = 15,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.side_effects),
        arrayId = R.array.tb_side_effects,
        entries = resources.getStringArray(R.array.tb_side_effects),
        required = true,
        showAsMultiSelectDialog = true,
        enableSearchInMultiSelect = true,
        isEnabled = true
    )

    private val treatmentCompleted = FormElement(
        id = 8,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.treatment_completed),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true,
        isEnabled = true
    )

    private val actualTreatmentCompletionDate = FormElement(
        id = 9,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.actual_treatment_completion_date),
        required = true,
        max = System.currentTimeMillis(),
        isEnabled = true
    )

    private val treatmentOutcome = FormElement(
        id = 10,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.treatment_outcome),
        arrayId = R.array.tb_treatment_outcomes,
        entries = resources.getStringArray(R.array.tb_treatment_outcomes),
        required = false,
        hasDependants = true,
        isEnabled = true
    )

    private val dateOfDeath = FormElement(
        id = 11,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.date_of_death),
        required = true,
        max = System.currentTimeMillis(),
        isEnabled = true
    )

    private val placeOfDeath = FormElement(
        id = 12,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.place_of_death),
        arrayId = R.array.place_of_death,
        entries = resources.getStringArray(R.array.place_of_death),
        required = false,
        isEnabled = true
    )

    private val reasonForDeath = FormElement(
        id = 13,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.reason_for_death),
        required = false,
        isEnabled = true
    )

    private val reasonForNotCompleting = FormElement(
        id = 14,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.reason_for_not_completing),
        required = true,
        isEnabled = true

    )

    private val remarks = FormElement(
        id = 15,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.remarks),
        required = false,
        etMaxLength = 400,
        isEnabled = true
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        saved: TBConfirmedTreatmentCache?,
        suspectedTb: TBSuspectedCache?
    ) {
        val baseList = mutableListOf<FormElement>()
        suspectedVisitDate = suspectedTb?.visitDate ?: ben?.regDate ?: 0L
        treatmentAlreadyStarted = saved != null
        treatmentStartDate.min = suspectedVisitDate.takeIf { it > 0L } ?: getOneYearBeforeCurrentDate()
        treatmentStartDate.max = System.currentTimeMillis()

        if (saved == null) {
            dsTbRegimen.value = resources.getString(R.string.ds_tb_regimen_value)
            treatmentStartDate.value = getDateFromLong(System.currentTimeMillis())
            reasonForDeath.value = resources.getString(R.string.tuberculosis)

            baseList.addAll(
                listOf(
                    treatmentType,
                    treatmentStartDate,
                    expectedTreatmentCompletionDate,
                    followUpDate,
                    monthlyFollowUpDone,
                    anyDiscomfort
                )
            )

            treatmentStartDate.max = System.currentTimeMillis()
            treatmentStartDate.min = suspectedTb?.visitDate?.takeIf { it > 0 } ?: getOneYearBeforeCurrentDate()

            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            treatmentStartDateValue = todayStart
            treatmentStartDateLong = todayStart
            followUpDate.min = todayStart
            followUpDate.isEnabled = true
        } else {
            val legacyRegimen = saved.regimenType.orEmpty()
            treatmentType.value = getLocalValueInArray(R.array.tb_regimen_types, saved.treatmentType)
                ?: if (legacyRegimen.contains("DS-TB", true) || legacyRegimen.contains("2 HRZE", true))
                    treatmentType.entries?.get(0) else treatmentType.entries?.get(1)
            dsTbRegimen.value = saved.dsTbRegimen ?: resources.getString(R.string.ds_tb_regimen_value)
            pmdtRegimen.value = getLocalValueInArray(R.array.tb_pmdt_regimens, saved.pmdtRegimen)
                ?: when {
                    legacyRegimen.contains("INH", true) || legacyRegimen.contains("H mono", true) -> resources.getStringArray(R.array.tb_pmdt_regimens)[0]
                    legacyRegimen.contains("Longer", true) -> resources.getStringArray(R.array.tb_pmdt_regimens)[1]
                    legacyRegimen.contains("Shorter", true) -> resources.getStringArray(R.array.tb_pmdt_regimens)[2]
                    legacyRegimen.contains("BPa", true) -> resources.getStringArray(R.array.tb_pmdt_regimens)[3]
                    else -> null
                }
            regimenTypeValue = pmdtRegimen.value ?: dsTbRegimen.value
            followUpDate.isEnabled = true
            treatmentStartDateLong = millisAtStartOfDay(saved.treatmentStartDate)
            lastFollowUpDateLong = saved.followUpDate?.let { millisAtStartOfDay(it) } ?: 0L

            treatmentType.isEnabled = saved.followUpDate == null
            dsTbRegimen.isEnabled = treatmentType.isEnabled
            pmdtRegimen.isEnabled = treatmentType.isEnabled

            saved.treatmentStartDate.takeIf { it > 0 }?.let {
                treatmentStartDate.value = getDateFromLong(it)
                treatmentStartDate.isEnabled = false
                updateFollowUpDateConstraints()
            }
            expectedTreatmentCompletionDate.value = saved.expectedTreatmentCompletionDate?.let { getDateFromLong(it) }
            followUpDate.value = saved.followUpDate?.let { getDateFromLong(it) }
            monthlyFollowUpDone.value = saved.monthlyFollowUpDone
            anyDiscomfort.value = saved.anyDiscomfort?.let { if (it) yesValue else noValue }
            sideEffects.value = getCheckboxIndexesFromValues(R.array.tb_side_effects, saved.sideEffects)
            treatmentCompleted.value = saved.treatmentCompleted?.let { if (it) yesValue else noValue }
            actualTreatmentCompletionDate.value =
                saved.actualTreatmentCompletionDate?.let { getDateFromLong(it) }
            val outcomeEn = legacyOutcomeMap[saved.treatmentOutcome] ?: saved.treatmentOutcome
            treatmentOutcome.value =
                getLocalValueInArray(R.array.tb_treatment_outcomes, outcomeEn)
            dateOfDeath.value = saved.dateOfDeath?.let { getDateFromLong(it) }
            placeOfDeath.value = getLocalValueInArray(R.array.place_of_death, saved.placeOfDeath)
            reasonForDeath.value = saved.reasonForDeath ?: resources.getString(R.string.tuberculosis)
            reasonForNotCompleting.value = saved.reasonForNotCompleting
            remarks.value = saved.remarks

            treatmentStartDateValue =
                if (saved.treatmentStartDate > 0) millisAtStartOfDay(saved.treatmentStartDate) else null

            updateFollowUpDateConstraints()
            calculateExpectedCompletionDate()
            followUpDate.errorText = null

            baseList.addAll(
                listOf(
                    treatmentType,
                    if (treatmentType.value == treatmentType.entries?.get(0)) dsTbRegimen else pmdtRegimen,
                    treatmentStartDate,
                    expectedTreatmentCompletionDate
                )
            )

            if (saved.followUpDate != null) {
                baseList.addAll(
                    listOf(
                        followUpDate,
                        monthlyFollowUpDone,
                        anyDiscomfort
                    )
                )
                if (saved.anyDiscomfort == true) baseList.add(sideEffects)
            } else {
                baseList.add(followUpDate)
            }

            if (saved.treatmentCompleted != null) {
                baseList.add(treatmentCompleted)

                if (saved.treatmentCompleted == true) {
                    baseList.add(actualTreatmentCompletionDate)

                    if (saved.treatmentOutcome != null) {
                        baseList.add(treatmentOutcome)
                    }

                    // Remarks is independent of the outcome, so it is always shown
                    baseList.add(remarks)

                    if (saved.treatmentOutcome != null &&
                        treatmentOutcome.value == treatmentOutcome.entries!![1]
                    ) {
                        baseList.addAll(
                            listOf(
                                dateOfDeath,
                                placeOfDeath,
                                reasonForDeath
                            )
                        )
                    }
                } else {
                    baseList.add(reasonForNotCompleting)
                }
            }

            treatmentOutcome.value?.let { outcome ->
                if (outcome == treatmentOutcome.entries!![5] || outcome == treatmentOutcome.entries!![1]) {
                    baseList.forEach { it.isEnabled = false }
                    // A record saved before Side Effects existed has none, and a locked form can't
                    // fill it, so don't let the empty field block the save (Confirmed with Rohit).
                    if (sideEffects.value.isNullOrEmpty()) sideEffects.required = false
                }
            }
        }

        setUpPage(baseList)
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        when (formId) {
            treatmentType.id -> {
                dsTbRegimen.value = resources.getString(R.string.ds_tb_regimen_value)
                pmdtRegimen.value = null
                val isDs = treatmentType.value == treatmentType.entries?.get(0)
                regimenTypeValue = if (isDs) dsTbRegimen.value else null
                calculateExpectedCompletionDate()
                updateFollowUpDateConstraints()
                checkAndEnableTreatmentCompletion()
                return triggerDependants(
                    source = treatmentType,
                    removeItems = listOf(if (isDs) pmdtRegimen else dsTbRegimen),
                    addItems = listOf(if (isDs) dsTbRegimen else pmdtRegimen),
                    position = -2
                )
            }

            pmdtRegimen.id -> {
                regimenTypeValue = pmdtRegimen.value
                calculateExpectedCompletionDate()
                updateFollowUpDateConstraints()
                checkAndEnableTreatmentCompletion()
            }

            treatmentStartDate.id -> {
                val dateLong = getLongFromDate(treatmentStartDate.value)
                treatmentStartDateValue = dateLong
                treatmentStartDateLong = dateLong
                calculateExpectedCompletionDate()
                updateFollowUpDateConstraints()
            }

            followUpDate.id -> {
                val dateLong = getLongFromDate(followUpDate.value)
                updateMonthlyFollowUpCount(dateLong)
                followUpDate.errorText = validateFollowUpDate(dateLong)
                checkAndEnableTreatmentCompletion()
            }

            anyDiscomfort.id -> {
                return if (index == 0) {
                    triggerDependants(
                        source = anyDiscomfort,
                        removeItems = listOf(),
                        addItems = listOf(sideEffects),
                        position = -2
                    )
                } else {
                    sideEffects.value = null
                    sideEffects.errorText = null
                    triggerDependants(
                        source = anyDiscomfort,
                        removeItems = listOf(sideEffects),
                        addItems = listOf(),
                        position = -2
                    )
                }
            }

            treatmentCompleted.id -> {
                return if (index == 0) {
                    triggerDependants(
                        source = treatmentCompleted,
                        removeItems = listOf(reasonForNotCompleting),
                        addItems = listOf(actualTreatmentCompletionDate, treatmentOutcome,remarks),
                        position = -2
                    )
                } else {
                    triggerDependants(
                        source = treatmentCompleted,
                        removeItems = listOf(
                            actualTreatmentCompletionDate,
                            treatmentOutcome,
                            remarks,
                            dateOfDeath,
                            placeOfDeath,
                            reasonForDeath
                        ),
                        addItems = listOf(reasonForNotCompleting),
                        position = -2
                    )
                }
            }

            treatmentOutcome.id -> {
                val treatmentOutcomes = resources.getStringArray(R.array.tb_treatment_outcomes)
                return if (treatmentOutcome.value == treatmentOutcomes[1]) {
                    triggerDependants(
                        source = treatmentOutcome,
                        removeItems = listOf(),
                        addItems = listOf(dateOfDeath, placeOfDeath, reasonForDeath),
                        position = -2
                    )
                } else {
                    triggerDependants(
                        source = treatmentOutcome,
                        removeItems = listOf(dateOfDeath, placeOfDeath, reasonForDeath),
                        addItems = listOf(),
                        position = -2
                    )
                }
            }
        }
        return 0
    }

    private fun updateFollowUpDateConstraints() {
        if (treatmentStartDateLong == 0L) {
            followUpDate.isEnabled = false
            return
        }

        // Cap follow-up date max to expected completion date if known
        val expectedCompletionMax = calculateExpectedCompletionDateLong()
        followUpDate.max = if (expectedCompletionMax > 0L)
            minOf(System.currentTimeMillis(), expectedCompletionMax)
        else
            System.currentTimeMillis()

        if (lastFollowUpDateLong == 0L) {
            followUpDate.min = treatmentStartDateLong
            followUpDate.isEnabled = true
        } else {
            nextFollowUpMonthStart = getFirstDayOfNextMonth(lastFollowUpDateLong)
            followUpDate.min = nextFollowUpMonthStart
            followUpDate.isEnabled = System.currentTimeMillis() >= nextFollowUpMonthStart
        }
    }


    fun validateAllFields(): Boolean {
        var isValid = true

        if (treatmentType.required && treatmentType.value.isNullOrEmpty()) {
            treatmentType.errorText = "Type of treatment is required"
            isValid = false
        }
        val activeRegimen = if (treatmentType.value == treatmentType.entries?.get(0)) dsTbRegimen else pmdtRegimen
        if (treatmentType.value != null && activeRegimen.required && activeRegimen.value.isNullOrEmpty()) {
            activeRegimen.errorText = "Regimen is required"
            isValid = false
        }

        if (treatmentStartDate.required && treatmentStartDate.value.isNullOrEmpty()) {
            treatmentStartDate.errorText = "Treatment start date is required"
            isValid = false
        } else if (!treatmentStartDate.value.isNullOrEmpty()) {
            val start = getLongFromDate(treatmentStartDate.value)
            if (start > System.currentTimeMillis()) {
                treatmentStartDate.errorText = "Treatment start date cannot be in the future"
                isValid = false
            } /*else if (!treatmentAlreadyStarted && suspectedVisitDate > 0L &&
                start < millisAtStartOfDay(suspectedVisitDate)) {
                treatmentStartDate.errorText = "Treatment start date must be on or after diagnosis visit date"
                isValid = false
            }*/ else treatmentStartDate.errorText = null
        }

        if (!validateCurrentFollowUpDate()) {
            isValid = false
        }

        if (anyDiscomfort.required &&
            listFlow.value.contains(anyDiscomfort) &&
            anyDiscomfort.value.isNullOrEmpty()) {
            anyDiscomfort.errorText = "This field is required"
            isValid = false
        }

        if (sideEffects.required &&
            listFlow.value.contains(sideEffects) &&
            sideEffects.value.isNullOrEmpty()) {
            sideEffects.errorText = "Side effects is required"
            isValid = false
        }

        if (actualTreatmentCompletionDate.required &&
            listFlow.value.contains(actualTreatmentCompletionDate)) {
            if (actualTreatmentCompletionDate.value.isNullOrEmpty()) {
                actualTreatmentCompletionDate.errorText =
                    "Actual treatment completion date is required"
                isValid = false
            } else {
                val actualDate = getLongFromDate(actualTreatmentCompletionDate.value)
                val lastFollowUp = getLongFromDate(followUpDate.value)
                if (actualDate > 0 && lastFollowUp > 0 && actualDate <= lastFollowUp) {
                    actualTreatmentCompletionDate.errorText =
                        "Actual treatment completion date must be after the last follow-up date"
                    isValid = false
                } else {
                    actualTreatmentCompletionDate.errorText = null
                }
            }
        }

        if (dateOfDeath.required &&
            listFlow.value.contains(dateOfDeath) &&
            dateOfDeath.value.isNullOrEmpty()) {
            dateOfDeath.errorText = "Date of death is required"
            isValid = false
        }

        if (reasonForNotCompleting.required &&
            listFlow.value.contains(reasonForNotCompleting) &&
            reasonForNotCompleting.value.isNullOrEmpty()) {
            reasonForNotCompleting.errorText = "Reason for not completing is required"
            isValid = false
        }
        if (!remarks.value.isNullOrEmpty() && remarks.value!!.length > 400) {
            remarks.errorText = "Remarks cannot exceed 400 characters"
            isValid = false
        } else {
            remarks.errorText = null
        }

        return isValid
    }
    fun validateCurrentFollowUpDate(): Boolean {
        val dateString = followUpDate.value
        if (dateString.isNullOrEmpty()) {
            followUpDate.errorText = "Follow-up date is required"
            return false
        }
        val dateLong = getLongFromDate(dateString)
        // If this date equals lastFollowUpDateLong it means it was already
        // saved as the current follow-up — treat as valid, clear error
//        if (dateLong == lastFollowUpDateLong && lastFollowUpDateLong > 0) {
//            followUpDate.errorText = null
//            return true
//        }
        val error = validateFollowUpDate(dateLong)
        followUpDate.errorText = error
        return error == null
    }

    private fun validateFollowUpDate(selectedDate: Long): String? {

        if (selectedDate > System.currentTimeMillis()) {
            return "Follow-up date cannot be in the future"
        }

        if (selectedDate < treatmentStartDateLong) {
            return "Follow-up date cannot be before treatment start date"
        }

        val expectedCompletionDate = calculateExpectedCompletionDateLong()
        if (expectedCompletionDate > 0L && selectedDate > expectedCompletionDate) {
            return "Follow-up date cannot be after treatment end date"
        }

        if (lastFollowUpDateLong > 0) {
            if (selectedDate <= lastFollowUpDateLong) {
                return "Follow-up date must be after last follow-up date"
            }

            if (isSameMonth(selectedDate, lastFollowUpDateLong)) {
                return "Only one follow-up is allowed per month"
            }
        }

        return null
    }
    fun getFollowUpDateLong(): Long = getLongFromDate(followUpDate.value)

    /** True when the last saved follow-up falls in the current month — the next one is only allowed from next month. */
    fun isFollowUpDoneForCurrentMonth(): Boolean =
        lastFollowUpDateLong > 0L && isSameMonth(lastFollowUpDateLong, System.currentTimeMillis())


    private fun calculateExpectedCompletionDate() {
        val start = treatmentStartDateValue ?: return
        val range = regimenMonthRange() ?: run {
            expectedTreatmentCompletionDate.value = null
            return
        }
        val startDate = datePlusMonths(start, range.first)
        val endDate = datePlusMonths(start, range.last)
        expectedTreatmentCompletionDate.value = if (range.first == range.last) getDateFromLong(startDate)
        else "${getDateFromLong(startDate)} to ${getDateFromLong(endDate)}"
    }

    private fun calculateExpectedCompletionDateLong(): Long {
        val start = treatmentStartDateValue ?: return 0L
        val range = regimenMonthRange() ?: return 0L
        return datePlusMonths(start, range.last)
    }

    private fun regimenMonthRange(): IntRange? {
        val pmdt = resources.getStringArray(R.array.tb_pmdt_regimens)
        return when (regimenTypeValue) {
            resources.getString(R.string.ds_tb_regimen_value) -> 6..6
            pmdt.getOrNull(0) -> 6..9
            pmdt.getOrNull(1) -> 18..20
            pmdt.getOrNull(2) -> 9..11
            pmdt.getOrNull(3) -> 6..10
            else -> null
        }
    }

    private fun datePlusMonths(start: Long, months: Int): Long = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.MONTH, months)
    }.timeInMillis

    private fun checkAndEnableTreatmentCompletion() {
        if (regimenTypeValue == null || followUpCount == 0) {
            treatmentCompleted.isEnabled = false
            treatmentCompleted.value = null
            triggerDependants(
                source = treatmentCompleted,
                removeItems = listOf(actualTreatmentCompletionDate, treatmentOutcome, remarks,
                    reasonForNotCompleting, dateOfDeath, placeOfDeath, reasonForDeath),
                addItems = emptyList(),
                position = -2
            )
            return
        }

        val pmdt = resources.getStringArray(R.array.tb_pmdt_regimens)
        val requiredFollowUps = when (regimenTypeValue) {
            resources.getString(R.string.ds_tb_regimen_value), pmdt[0], pmdt[3] -> 5
            pmdt[2] -> 9
            pmdt[1] -> 18
            else -> 0
        }

        if (followUpCount >= requiredFollowUps) {
            treatmentCompleted.isEnabled = true
            triggerDependants(
                source = followUpDate,
                addItems = listOf(treatmentCompleted),
                removeItems = listOf(),
                position = -2
            )
        } else {
            treatmentCompleted.isEnabled = false
            treatmentCompleted.value = null

            triggerDependants(
                source = treatmentCompleted,
                removeItems = listOf(
                    actualTreatmentCompletionDate,
                    treatmentOutcome,
                    remarks,
                    reasonForNotCompleting,
                    dateOfDeath,
                    placeOfDeath,
                    reasonForDeath
                ),
                addItems = listOf(),
                position = -2
            )
        }
    }

    private fun millisAtStartOfDay(timeMs: Long): Long {
        if (timeMs <= 0L) return 0L
        return Calendar.getInstance().apply {
            timeInMillis = timeMs
        }.setToStartOfTheDay().timeInMillis
    }

    private fun updateMonthlyFollowUpCount(followUpTimestamp: Long) {
        if (followUpTimestamp == 0L || treatmentStartDateValue == null) {
            monthlyFollowUpDone.value = resources.getString(R.string.month_format, followUpCount)
            return
        }

        val calendarStart = Calendar.getInstance().apply {
            timeInMillis = treatmentStartDateValue!!
        }
        val calendarFollowUp = Calendar.getInstance().apply {
            timeInMillis = followUpTimestamp
        }

        val yearDiff = calendarFollowUp.get(Calendar.YEAR) - calendarStart.get(Calendar.YEAR)
        val monthDiff = calendarFollowUp.get(Calendar.MONTH) - calendarStart.get(Calendar.MONTH)

        followUpCount = (yearDiff * 12) + monthDiff + 1
        monthlyFollowUpDone.value = resources.getString(R.string.month_format, followUpCount)
        checkAndEnableTreatmentCompletion()
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBConfirmedTreatmentCache).let { form ->
            form.treatmentType = getEnglishValueInArray(R.array.tb_regimen_types, treatmentType.value)
            form.dsTbRegimen = dsTbRegimen.value?.takeIf { treatmentType.value == treatmentType.entries?.get(0) }
            form.pmdtRegimen = getEnglishValueInArray(R.array.tb_pmdt_regimens, pmdtRegimen.value)
                ?.takeIf { treatmentType.value == treatmentType.entries?.get(1) }
            form.regimenType = form.pmdtRegimen ?: form.dsTbRegimen
            form.treatmentStartDate = getLongFromDate(treatmentStartDate.value)
            form.expectedTreatmentCompletionDate = calculateExpectedCompletionDateLong().takeIf { it > 0L }
            form.followUpDate = getLongFromDate(followUpDate.value)
            form.monthlyFollowUpDone = monthlyFollowUpDone.value
            form.anyDiscomfort = anyDiscomfort.value == anyDiscomfort.entries!![0]
            form.sideEffects = if (form.anyDiscomfort == true)
                getEnglishCheckboxValues(R.array.tb_side_effects, sideEffects.value)
            else null
            form.treatmentCompleted = treatmentCompleted.value?.let {
                it == treatmentCompleted.entries!![0]
            }
            form.actualTreatmentCompletionDate = getLongFromDate(actualTreatmentCompletionDate.value)
            form.treatmentOutcome =
                getEnglishValueInArray(R.array.tb_treatment_outcomes, treatmentOutcome.value)
            val died = form.treatmentOutcome == "Died"
            form.dateOfDeath = if (died) getLongFromDate(dateOfDeath.value).takeIf { it > 0 } else null
            form.placeOfDeath = if (died) getEnglishValueInArray(R.array.place_of_death, placeOfDeath.value) else null
            form.reasonForDeath = reasonForDeath.value ?: resources.getString(R.string.tuberculosis)
            form.reasonForNotCompleting = reasonForNotCompleting.value
            form.remarks = remarks.value?.takeIf { it.isNotBlank() }
        }
    }

    private fun getFirstDayOfNextMonth(date: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun isSameMonth(date1: Long, date2: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.timeInMillis = date1
        cal2.timeInMillis = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }
}
