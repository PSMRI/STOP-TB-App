package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.InputType

class GeneralOpdDataset(context: Context, currentLanguage: Languages) : Dataset(context, currentLanguage) {
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

    suspend fun setUpPage(saved: GeneralOpdCache?) {
        val entries = (chiefComplaint.entries.orEmpty().toList() + saved?.chiefComplaints.orEmpty())
            .distinct().toTypedArray()
        chiefComplaint.entries = entries
        chiefComplaint.value = saved?.chiefComplaints?.mapNotNull { name ->
            entries.indexOf(name).takeIf { it >= 0 }
        }?.takeIf { it.isNotEmpty() }?.joinToString("|")
        setUpPage(listOf(chiefComplaint))
    }

    fun setChiefComplaintEntries(entries: List<String>) {
        chiefComplaint.entries = entries.toTypedArray()
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int = -1

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        val entries = chiefComplaint.entries.orEmpty()
        (cacheModel as GeneralOpdCache).chiefComplaints = chiefComplaint.value?.split("|")
            ?.mapNotNull { it.toIntOrNull()?.let(entries::getOrNull) }
            ?.takeIf { it.isNotEmpty() }
    }

    fun hasChiefComplaint() = !chiefComplaint.value.isNullOrBlank()
}
