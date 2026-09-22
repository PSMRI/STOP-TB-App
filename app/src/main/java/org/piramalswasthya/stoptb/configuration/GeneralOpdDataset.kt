package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.InputType

class GeneralOpdDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val chiefComplaint = FormElement(
        id = 1,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.chief_complaint),
        arrayId = R.array.general_opd_chief_complaint_array,
        entries = emptyArray(),
        required = false,
        hasDependants = true,
        showAsMultiSelectDialog = true,
        enableSearchInMultiSelect = true
    )

    private var chiefComplaintEnglishEntries = emptyArray<String>()

    private val medication = FormElement(
        id = 2,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.medication),
        arrayId = R.array.general_opd_medication_array,
        entries = resources.getStringArray(R.array.general_opd_medication_array),
        required = false,
        hasDependants = true,
        showAsMultiSelectDialog = true
    )

    private val dosage = FormElement(
        id = 3,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.dosage),
        required = false,
        etMaxLength = 100,
        hasDependants = true
    )

    private val frequency = FormElement(
        id = 4,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.frequency),
        arrayId = R.array.general_opd_frequency_array,
        entries = resources.getStringArray(R.array.general_opd_frequency_array),
        required = false,
        hasDependants = true
    )

    private val duration = FormElement(
        id = 5,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.duration),
        arrayId = R.array.general_opd_duration_array,
        entries = resources.getStringArray(R.array.general_opd_duration_array),
        required = false,
        hasDependants = true
    )

    private val notes = FormElement(
        id = 6,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.notes_remarks),
        required = false,
        etMaxLength = 250,
        multiLine = true
    )

    suspend fun setUpPage(saved: GeneralOpdCache?) {
        saved?.let {
            chiefComplaint.value =
                valuesToSelectionIndexes(it.chiefComplaints, chiefComplaintEnglishEntries)
            medication.value =
                englishValuesToSelectionIndexes(it.medications, R.array.general_opd_medication_array)
            dosage.value = it.dosage
            frequency.value = getLocalValueInArray(R.array.general_opd_frequency_array, it.frequency)
            duration.value = getLocalValueInArray(R.array.general_opd_duration_array, it.duration)
            notes.value = it.notes
        }
        syncRequiredFlags()
//        setUpPage(listOf(chiefComplaint, medication, dosage, frequency, duration, notes))
        setUpPage(listOf(chiefComplaint, medication, frequency, duration, notes))
    }

    fun setChiefComplaintEntries(entries: List<String>) {
        chiefComplaint.entries = entries.toTypedArray()
        chiefComplaintEnglishEntries = entries.toTypedArray()
    }

    suspend fun refreshChiefComplaintEntries(entries: List<String>) {
        setChiefComplaintEntries(entries)
        // Re-emit the current form elements without resetting values the user may have entered.
        setUpPage(listOf(chiefComplaint, medication, frequency, duration, notes))
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        syncRequiredFlags()
        return when (formId) {
            chiefComplaint.id -> listFlow.value.indexOf(medication)
            medication.id -> listFlow.value.indexOf(dosage)
            else -> -1
        }
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as GeneralOpdCache).let { form ->
            form.chiefComplaints = getSelectedValues(chiefComplaint, chiefComplaintEnglishEntries)
            form.medications = getSelectedEnglishValues(
                medication,
                R.array.general_opd_medication_array
            )
            form.dosage = dosage.value?.takeIf { it.isNotBlank() }
            form.frequency = getEnglishValueInArray(R.array.general_opd_frequency_array, frequency.value)
            form.duration = getEnglishValueInArray(R.array.general_opd_duration_array, duration.value)
            form.notes = notes.value?.takeIf { it.isNotBlank() }
        }
    }

    fun validateBusinessRules(): Int {
        syncRequiredFlags()
        if (hasChiefComplaint() && !hasMedication()) {
            medication.errorText = resources.getString(R.string.general_opd_medication_required_error)
            return listFlow.value.indexOf(medication)
        }
        medication.errorText = null
        return -1
    }

    fun hasAnyData(): Boolean =
        listOf(chiefComplaint, medication, dosage, frequency, duration, notes)
            .any { !it.value.isNullOrBlank() }

    private fun syncRequiredFlags() {
        val chiefComplaintSelected = hasChiefComplaint()
        medication.required = chiefComplaintSelected
        val medicationSelected = hasMedication()
        dosage.required = medicationSelected
        frequency.required = medicationSelected
        duration.required = medicationSelected

        if (!chiefComplaintSelected || medicationSelected) {
            medication.errorText = null
        }
        if (!medicationSelected) {
            listOf(dosage, frequency, duration).forEach {
                it.errorText = null
            }
        }
    }

    private fun hasChiefComplaint(): Boolean = !chiefComplaint.value.isNullOrBlank()

    private fun hasMedication(): Boolean = !medication.value.isNullOrBlank()

    private fun getSelectedEnglishValues(formElement: FormElement, arrayId: Int): List<String>? {
        return getSelectedValues(formElement, englishResources.getStringArray(arrayId))
    }

    private fun getSelectedValues(formElement: FormElement, entries: Array<String>): List<String>? {
        val selectedIndexes = formElement.value
            ?.split("|")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        if (selectedIndexes.isEmpty()) return null

        return selectedIndexes.mapNotNull { idx -> entries.getOrNull(idx) }
    }

    private fun englishValuesToSelectionIndexes(values: List<String>?, arrayId: Int): String? {
        return valuesToSelectionIndexes(values, englishResources.getStringArray(arrayId))
    }

    private fun valuesToSelectionIndexes(values: List<String>?, entries: Array<String>): String? {
        if (values.isNullOrEmpty()) return null

        val selectedIndexes = values.mapNotNull { value ->
            entries.indexOf(value).takeIf { it >= 0 }
        }
        return selectedIndexes.takeIf { it.isNotEmpty() }?.joinToString("|")
    }
}
