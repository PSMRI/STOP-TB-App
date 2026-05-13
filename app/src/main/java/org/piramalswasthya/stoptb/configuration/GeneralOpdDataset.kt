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
        entries = resources.getStringArray(R.array.general_opd_chief_complaint_array),
        required = false,
        hasDependants = true,
        showAsMultiSelectDialog = true
    )

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
                englishValuesToSelectionIndexes(it.chiefComplaints, R.array.general_opd_chief_complaint_array)
            medication.value =
                englishValuesToSelectionIndexes(it.medications, R.array.general_opd_medication_array)
            dosage.value = it.dosage
            frequency.value = getLocalValueInArray(R.array.general_opd_frequency_array, it.frequency)
            duration.value = getLocalValueInArray(R.array.general_opd_duration_array, it.duration)
            notes.value = it.notes
        }
        syncRequiredFlags()
        setUpPage(listOf(chiefComplaint, medication, dosage, frequency, duration, notes))
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
            form.chiefComplaints = getSelectedEnglishValues(
                chiefComplaint,
                R.array.general_opd_chief_complaint_array
            )
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
        medication.required = hasChiefComplaint()
        val medicationSelected = hasMedication()
        dosage.required = medicationSelected
        frequency.required = medicationSelected
        duration.required = medicationSelected
    }

    private fun hasChiefComplaint(): Boolean = !chiefComplaint.value.isNullOrBlank()

    private fun hasMedication(): Boolean = !medication.value.isNullOrBlank()

    private fun getSelectedEnglishValues(formElement: FormElement, arrayId: Int): List<String>? {
        val selectedIndexes = formElement.value
            ?.split("|")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        if (selectedIndexes.isEmpty()) return null

        val englishEntries = englishResources.getStringArray(arrayId)
        return selectedIndexes.mapNotNull { idx -> englishEntries.getOrNull(idx) }
    }

    private fun englishValuesToSelectionIndexes(values: List<String>?, arrayId: Int): String? {
        if (values.isNullOrEmpty()) return null

        val englishEntries = englishResources.getStringArray(arrayId)
        val selectedIndexes = values.mapNotNull { value ->
            englishEntries.indexOf(value).takeIf { it >= 0 }
        }
        return selectedIndexes.takeIf { it.isNotEmpty() }?.joinToString("|")
    }
}
