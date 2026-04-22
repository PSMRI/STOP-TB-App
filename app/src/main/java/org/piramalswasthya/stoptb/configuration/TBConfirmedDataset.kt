package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import java.util.Calendar

class TBConfirmedDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val yesValue get() = resources.getStringArray(R.array.yes_no)[0]
    private val noValue get() = resources.getStringArray(R.array.yes_no)[1]

    private var suspectedVisitDate: Long = 0L
    private var treatmentStartDateLong: Long = 0L
    private var lastFollowUpDateLong: Long = 0L
    private var followUpCount = 0
    private var regimenTypeValue: String? = null

    private val regimenType = FormElement(
        id = 1,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.regimen_type),
        arrayId = R.array.tb_regimen_types,
        entries = resources.getStringArray(R.array.tb_regimen_types),
        required = true,
        hasDependants = true
    )

    private val treatmentStartDate = FormElement(
        id = 2,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.treatment_start_date),
        required = true,
        max = System.currentTimeMillis(),
        hasDependants = true
    )

    private val expectedTreatmentCompletionDate = FormElement(
        id = 3,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.expected_treatment_completion_date),
        required = false
    )

    private val followUpDate = FormElement(
        id = 4,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.follow_up_date),
        required = true,
        max = System.currentTimeMillis(),
        hasDependants = true
    )

    private val monthlyFollowUpDone = FormElement(
        id = 5,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.monthly_follow_up_done),
        required = false
    )

    private val adherenceToMedicines = FormElement(
        id = 6,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.adherence_to_medicines),
        arrayId = R.array.adherence_options,
        entries = resources.getStringArray(R.array.adherence_options),
        required = true
    )

    private val anyDiscomfort = FormElement(
        id = 7,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.any_discomfort),
        entries = resources.getStringArray(R.array.yes_no),
        required = true
    )

    private val treatmentCompleted = FormElement(
        id = 8,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.treatment_completed),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true
    )

    private val actualTreatmentCompletionDate = FormElement(
        id = 9,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.actual_treatment_completion_date),
        required = false,
        max = System.currentTimeMillis()
    )

    private val treatmentOutcome = FormElement(
        id = 10,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.treatment_outcome),
        arrayId = R.array.tb_treatment_outcomes,
        entries = resources.getStringArray(R.array.tb_treatment_outcomes),
        required = false,
        hasDependants = true
    )

    private val dateOfDeath = FormElement(
        id = 11,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.date_of_death),
        required = false,
        max = System.currentTimeMillis()
    )

    private val placeOfDeath = FormElement(
        id = 12,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.place_of_death),
        arrayId = R.array.place_of_death,
        entries = resources.getStringArray(R.array.place_of_death),
        required = false
    )

    private val reasonForDeath = FormElement(
        id = 13,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.reason_for_death),
        required = false
    )

    private val reasonForNotCompleting = FormElement(
        id = 14,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.reason_for_not_completing),
        required = false
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        saved: TBConfirmedTreatmentCache?,
        suspectedTb: TBSuspectedCache?
    ) {
        suspectedVisitDate = suspectedTb?.visitDate ?: ben?.regDate ?: 0L

        reasonForDeath.value = resources.getString(R.string.tuberculosis)
        regimenType.value = null
        treatmentStartDate.value = null
        expectedTreatmentCompletionDate.value = null
        followUpDate.value = null
        monthlyFollowUpDone.value = null
        adherenceToMedicines.value = null
        anyDiscomfort.value = null
        treatmentCompleted.value = null
        actualTreatmentCompletionDate.value = null
        treatmentOutcome.value = null
        dateOfDeath.value = null
        placeOfDeath.value = null
        reasonForNotCompleting.value = null

        treatmentStartDate.min = suspectedVisitDate.takeIf { it > 0 }
        treatmentStartDate.max = System.currentTimeMillis()
        dateOfDeath.max = System.currentTimeMillis()
        actualTreatmentCompletionDate.max = System.currentTimeMillis()

        if (saved == null) {
            treatmentStartDate.value = getDateFromLong(System.currentTimeMillis())
            treatmentStartDateLong = getLongFromDate(treatmentStartDate.value!!)
            updateExpectedCompletionDate()
        } else {
            regimenType.value = getLocalValueInArray(R.array.tb_regimen_types, saved.regimenType)
            regimenTypeValue = regimenType.value
            treatmentStartDate.value = getDateFromLong(saved.treatmentStartDate)
            treatmentStartDateLong = saved.treatmentStartDate
            expectedTreatmentCompletionDate.value =
                saved.expectedTreatmentCompletionDate?.let { getDateFromLong(it) }
            followUpDate.value = saved.followUpDate?.let { getDateFromLong(it) }
            lastFollowUpDateLong = saved.followUpDate ?: 0L
            monthlyFollowUpDone.value = saved.monthlyFollowUpDone
            adherenceToMedicines.value =
                getLocalValueInArray(R.array.adherence_options, saved.adherenceToMedicines)
            anyDiscomfort.value = saved.anyDiscomfort?.let { if (it) yesValue else noValue }
            treatmentCompleted.value = saved.treatmentCompleted?.let { if (it) yesValue else noValue }
            actualTreatmentCompletionDate.value =
                saved.actualTreatmentCompletionDate?.let { getDateFromLong(it) }
            treatmentOutcome.value =
                getLocalValueInArray(R.array.tb_treatment_outcomes, saved.treatmentOutcome)
            dateOfDeath.value = saved.dateOfDeath?.let { getDateFromLong(it) }
            placeOfDeath.value = getLocalValueInArray(R.array.place_of_death, saved.placeOfDeath)
            reasonForDeath.value = saved.reasonForDeath.ifBlank { resources.getString(R.string.tuberculosis) }
            reasonForNotCompleting.value = saved.reasonForNotCompleting

            if (!saved.regimenType.isNullOrBlank()) regimenType.isEnabled = false
            if (saved.treatmentStartDate > 0) treatmentStartDate.isEnabled = false
        }

        updateMonthlyFollowUpCount()
        updateFollowUpDateConstraints()
        syncConditionalStates()
        setUpPage(buildFormList())
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        when (formId) {
            regimenType.id -> {
                regimenTypeValue = regimenType.value
                updateExpectedCompletionDate()
            }

            treatmentStartDate.id -> {
                treatmentStartDateLong = getLongFromDate(treatmentStartDate.value!!)
                updateExpectedCompletionDate()
                updateMonthlyFollowUpCount()
                updateFollowUpDateConstraints()
            }

            followUpDate.id -> {
                followUpDate.errorText = validateFollowUpDate(getLongFromDate(followUpDate.value!!))
                updateMonthlyFollowUpCount()
            }

            treatmentCompleted.id -> Unit
            treatmentOutcome.id -> Unit
        }
        syncConditionalStates()
        setUpPage(buildFormList())
        return 0
    }

    private fun buildFormList(): List<FormElement> = buildList {
        add(regimenType)
        add(treatmentStartDate)
        add(expectedTreatmentCompletionDate)
        add(followUpDate)
        add(monthlyFollowUpDone)
        add(adherenceToMedicines)
        add(anyDiscomfort)

        if (shouldShowTreatmentCompletionSection()) {
            add(treatmentCompleted)

            when (treatmentCompleted.value) {
                yesValue -> {
                    add(actualTreatmentCompletionDate)
                    add(treatmentOutcome)
                    if (isDeathOutcome()) {
                        add(dateOfDeath)
                        add(placeOfDeath)
                        add(reasonForDeath)
                    }
                }

                noValue -> add(reasonForNotCompleting)
            }
        }
    }

    private fun syncConditionalStates() {
        val completionEnabled = shouldShowTreatmentCompletionSection()
        treatmentCompleted.isEnabled = completionEnabled
        treatmentCompleted.required = completionEnabled
        if (!completionEnabled) resetField(treatmentCompleted)

        val completionYes = treatmentCompleted.value == yesValue
        actualTreatmentCompletionDate.isEnabled = completionYes
        actualTreatmentCompletionDate.required = completionYes
        if (!completionYes) resetField(actualTreatmentCompletionDate)

        treatmentOutcome.isEnabled = completionYes
        treatmentOutcome.required = completionYes
        if (!completionYes) resetField(treatmentOutcome)

        val completionNo = treatmentCompleted.value == noValue
        reasonForNotCompleting.isEnabled = completionNo
        reasonForNotCompleting.required = completionNo
        if (!completionNo) resetField(reasonForNotCompleting)

        val deathSelected = isDeathOutcome()
        dateOfDeath.isEnabled = deathSelected
        dateOfDeath.required = deathSelected
        if (!deathSelected) resetField(dateOfDeath)

        placeOfDeath.isEnabled = deathSelected
        placeOfDeath.required = deathSelected
        if (!deathSelected) resetField(placeOfDeath)

        reasonForDeath.isEnabled = deathSelected
        if (deathSelected) {
            reasonForDeath.value = resources.getString(R.string.tuberculosis)
        } else {
            reasonForDeath.value = resources.getString(R.string.tuberculosis)
            reasonForDeath.errorText = null
        }
    }

    private fun updateExpectedCompletionDate() {
        if (regimenTypeValue.isNullOrBlank() || treatmentStartDateLong == 0L) {
            expectedTreatmentCompletionDate.value = null
            return
        }

        val start = Calendar.getInstance().apply { timeInMillis = treatmentStartDateLong }
        val regimenEntries = resources.getStringArray(R.array.tb_regimen_types)
        expectedTreatmentCompletionDate.value = when (regimenTypeValue) {
            regimenEntries[0], regimenEntries[3], regimenEntries[4] -> {
                start.add(Calendar.MONTH, 6)
                getDateFromLong(start.timeInMillis)
            }

            regimenEntries[1] -> {
                start.add(Calendar.MONTH, 9)
                getDateFromLong(start.timeInMillis)
            }

            regimenEntries[2] -> {
                start.add(Calendar.MONTH, 18)
                getDateFromLong(start.timeInMillis)
            }

            else -> null
        }
    }

    private fun updateFollowUpDateConstraints() {
        val maxDate = System.currentTimeMillis()
        val minDate = when {
            lastFollowUpDateLong > 0L -> getFirstDayOfNextMonth(lastFollowUpDateLong)
            treatmentStartDateLong > 0L -> treatmentStartDateLong
            else -> suspectedVisitDate
        }
        followUpDate.max = maxDate
        followUpDate.min = minDate
        followUpDate.isEnabled = treatmentStartDateLong > 0L && minDate <= maxDate
    }

    private fun updateMonthlyFollowUpCount() {
        if (followUpDate.value.isNullOrBlank() || treatmentStartDateLong == 0L) {
            followUpCount = 0
            monthlyFollowUpDone.value = null
            return
        }
        val followUpLong = getLongFromDate(followUpDate.value!!)
        val calendarStart = Calendar.getInstance().apply { timeInMillis = treatmentStartDateLong }
        val calendarFollowUp = Calendar.getInstance().apply { timeInMillis = followUpLong }
        val yearDiff = calendarFollowUp.get(Calendar.YEAR) - calendarStart.get(Calendar.YEAR)
        val monthDiff = calendarFollowUp.get(Calendar.MONTH) - calendarStart.get(Calendar.MONTH)
        followUpCount = (yearDiff * 12) + monthDiff + 1
        monthlyFollowUpDone.value = resources.getString(R.string.month_format, followUpCount)
    }

    private fun requiredFollowUpsForCompletion(): Int = when (regimenTypeValue) {
        resources.getStringArray(R.array.tb_regimen_types)[0],
        resources.getStringArray(R.array.tb_regimen_types)[3],
        resources.getStringArray(R.array.tb_regimen_types)[4] -> 5

        resources.getStringArray(R.array.tb_regimen_types)[1] -> 9
        resources.getStringArray(R.array.tb_regimen_types)[2] -> 18
        else -> Int.MAX_VALUE
    }

    private fun shouldShowTreatmentCompletionSection(): Boolean =
        followUpCount >= requiredFollowUpsForCompletion()

    private fun isDeathOutcome(): Boolean =
        treatmentOutcome.value == resources.getStringArray(R.array.tb_treatment_outcomes).getOrNull(3)

    private fun validateFollowUpDate(selectedDate: Long): String? {
        if (selectedDate > System.currentTimeMillis()) {
            return resources.getString(R.string.follow_up_cannot_be_future_month)
        }
        if (treatmentStartDateLong > 0 && selectedDate < treatmentStartDateLong) {
            return resources.getString(
                R.string.follow_up_must_be_after_treatment,
                getDateFromLong(treatmentStartDateLong)
            )
        }
        if (lastFollowUpDateLong > 0L && selectedDate <= lastFollowUpDateLong) {
            return resources.getString(R.string.field_date_after, followUpDate.title, getDateFromLong(lastFollowUpDateLong))
        }
        if (lastFollowUpDateLong > 0L && isSameMonth(selectedDate, lastFollowUpDateLong)) {
            return resources.getString(R.string.follow_up_must_be_next_month)
        }
        return null
    }

    fun validateAllFields(): Boolean {
        regimenType.errorText = if (regimenType.value.isNullOrBlank())
            resources.getString(R.string.field_is_required, regimenType.title) else null

        treatmentStartDate.errorText = when {
            treatmentStartDate.value.isNullOrBlank() ->
                resources.getString(R.string.field_is_required, treatmentStartDate.title)
            getLongFromDate(treatmentStartDate.value!!) > System.currentTimeMillis() ->
                resources.getString(R.string.field_date_after, treatmentStartDate.title, getDateFromLong(System.currentTimeMillis()))
            suspectedVisitDate > 0L && getLongFromDate(treatmentStartDate.value!!) < suspectedVisitDate ->
                resources.getString(R.string.field_date_before, treatmentStartDate.title, getDateFromLong(suspectedVisitDate))
            else -> null
        }

        followUpDate.errorText = if (followUpDate.value.isNullOrBlank()) {
            resources.getString(R.string.field_is_required, followUpDate.title)
        } else {
            validateFollowUpDate(getLongFromDate(followUpDate.value!!))
        }

        adherenceToMedicines.errorText =
            if (adherenceToMedicines.value.isNullOrBlank()) resources.getString(
                R.string.field_is_required,
                adherenceToMedicines.title
            ) else null

        anyDiscomfort.errorText =
            if (anyDiscomfort.value.isNullOrBlank()) resources.getString(
                R.string.field_is_required,
                anyDiscomfort.title
            ) else null

        if (shouldShowTreatmentCompletionSection()) {
            treatmentCompleted.errorText =
                if (treatmentCompleted.value.isNullOrBlank()) resources.getString(
                    R.string.field_is_required,
                    treatmentCompleted.title
                ) else null
        }

        if (treatmentCompleted.value == yesValue) {
            actualTreatmentCompletionDate.errorText = when {
                actualTreatmentCompletionDate.value.isNullOrBlank() ->
                    resources.getString(R.string.field_is_required, actualTreatmentCompletionDate.title)
                followUpDate.value?.isNotBlank() == true &&
                    getLongFromDate(actualTreatmentCompletionDate.value!!) <= getLongFromDate(followUpDate.value!!) ->
                    resources.getString(
                        R.string.field_date_after,
                        actualTreatmentCompletionDate.title,
                        followUpDate.value!!
                    )
                else -> null
            }

            treatmentOutcome.errorText =
                if (treatmentOutcome.value.isNullOrBlank()) resources.getString(
                    R.string.field_is_required,
                    treatmentOutcome.title
                ) else null
        }

        if (treatmentCompleted.value == noValue) {
            reasonForNotCompleting.errorText =
                if (reasonForNotCompleting.value.isNullOrBlank()) resources.getString(
                    R.string.field_is_required,
                    reasonForNotCompleting.title
                ) else null
        }

        if (isDeathOutcome()) {
            dateOfDeath.errorText = when {
                dateOfDeath.value.isNullOrBlank() ->
                    resources.getString(R.string.field_is_required, dateOfDeath.title)
                getLongFromDate(dateOfDeath.value!!) > System.currentTimeMillis() ->
                    resources.getString(R.string.field_date_after, dateOfDeath.title, getDateFromLong(System.currentTimeMillis()))
                followUpDate.value?.isNotBlank() == true &&
                    getLongFromDate(dateOfDeath.value!!) < getLongFromDate(followUpDate.value!!) ->
                    resources.getString(R.string.field_date_before, dateOfDeath.title, followUpDate.value!!)
                else -> null
            }
            placeOfDeath.errorText =
                if (placeOfDeath.value.isNullOrBlank()) resources.getString(
                    R.string.field_is_required,
                    placeOfDeath.title
                ) else null
        }

        return allFormElements().all { it.errorText == null }
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBConfirmedTreatmentCache).let { form ->
            form.regimenType = getEnglishValueInArray(R.array.tb_regimen_types, regimenType.value)
            form.treatmentStartDate = getLongFromDate(treatmentStartDate.value!!)
            form.expectedTreatmentCompletionDate =
                expectedTreatmentCompletionDate.value?.let { getLongFromDate(it) }
            form.followUpDate = getLongFromDate(followUpDate.value!!)
            form.monthlyFollowUpDone = monthlyFollowUpDone.value
            form.adherenceToMedicines =
                getEnglishValueInArray(R.array.adherence_options, adherenceToMedicines.value)
            form.anyDiscomfort = anyDiscomfort.value == yesValue
            form.treatmentCompleted = treatmentCompleted.value?.let { it == yesValue }
            form.actualTreatmentCompletionDate = actualTreatmentCompletionDate.value?.let { getLongFromDate(it) }
            form.treatmentOutcome =
                getEnglishValueInArray(R.array.tb_treatment_outcomes, treatmentOutcome.value)
            form.dateOfDeath = dateOfDeath.value?.let { getLongFromDate(it) }
            form.placeOfDeath = getEnglishValueInArray(R.array.place_of_death, placeOfDeath.value)
            form.reasonForDeath = resources.getString(R.string.tuberculosis)
            form.reasonForNotCompleting = reasonForNotCompleting.value?.trim()?.takeIf { it.isNotEmpty() }
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

    private fun resetField(item: FormElement) {
        item.value = null
        item.errorText = null
    }

    private fun allFormElements(): List<FormElement> = listOf(
        regimenType,
        treatmentStartDate,
        expectedTreatmentCompletionDate,
        followUpDate,
        monthlyFollowUpDone,
        adherenceToMedicines,
        anyDiscomfort,
        treatmentCompleted,
        actualTreatmentCompletionDate,
        treatmentOutcome,
        dateOfDeath,
        placeOfDeath,
        reasonForDeath,
        reasonForNotCompleting
    )
}
