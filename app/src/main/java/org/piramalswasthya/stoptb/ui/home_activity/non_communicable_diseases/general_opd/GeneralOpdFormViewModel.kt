package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.GeneralOpdDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GeneralOpdFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
    private val tbRepo: TBRepo,
    private val drugMasterRepo: DrugMasterRepo
) : ViewModel() {

    val benId = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val viewOnly = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).viewOnly
    val autoFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow
    val generalOpdFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).generalOpdFlow

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED, SKIP_SUCCESS
    }

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _recordExists = MutableLiveData<Boolean>()
    val recordExists: LiveData<Boolean> = _recordExists

    private val dataset = GeneralOpdDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private var generalOpdCache = GeneralOpdCache(benId = benId)
    private val _medicines = MutableLiveData<List<OpdMedicineDraft>>()
    val medicines: LiveData<List<OpdMedicineDraft>> = _medicines
    private val _prescriptionVisible = MutableLiveData(false)
    val prescriptionVisible: LiveData<Boolean> = _prescriptionVisible
    private val _notes = MutableLiveData<String>()
    val notes: LiveData<String> = _notes
    private val _savedChiefComplaintText = MutableLiveData<String>()
    val savedChiefComplaintText: LiveData<String> = _savedChiefComplaintText
    private val _drugMasters = MutableLiveData<OpdDrugMasters>()
    val drugMasters: LiveData<OpdDrugMasters> = _drugMasters

    fun updateMedicines(rows: List<OpdMedicineDraft>) {
        _medicines.value = rows.map { it.copy() }
        savedStateHandle["opdMedicines"] = ArrayList(rows.map { row -> Bundle().apply {
            putString("medicine", row.medicine)
            putString("frequency", row.frequency)
            putInt("durationCount", row.durationCount)
            putString("durationUnit", row.durationUnit)
            putString("instruction", row.instruction)
            row.drugId?.let { putInt("drugId", it) }
            putString("drugName", row.drugName)
            row.itemFormId?.let { putInt("itemFormId", it) }
            putString("drugForm", row.drugForm)
        } })
    }

    fun updateNotes(value: String) {
        _notes.value = value
        savedStateHandle["opdNotes"] = value
    }

    fun requiresMedicine() = dataset.hasChiefComplaint()

    init {
        viewModelScope.launch {
                val ben = benRepo.getBenFromId(benId)
                ben?.let {
                    _benName.value = "${it.firstName} ${it.lastName.orEmpty()}".trim()
                    _benAgeGender.value = it.getAgeGenderDisplayString()
                    generalOpdCache = GeneralOpdCache(benId = it.beneficiaryId)
                }

                dataset.setChiefComplaintEntries(tbRepo.getCachedChiefComplaintNames())
                _drugMasters.value = drugMasterRepo.getCachedMasters()

                val existing = tbRepo.getGeneralOpd(benId)
                val prescriptions = existing?.let { tbRepo.getGeneralOpdPrescriptions(it.id) }.orEmpty()
                existing?.let { generalOpdCache = it }
                _savedChiefComplaintText.value = generalOpdCache.chiefComplaints.orEmpty()
                    .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

                val restored = savedStateHandle.get<ArrayList<Bundle>>("opdMedicines")
                val loadedMedicines = restored?.map { bundle -> OpdMedicineDraft(
                    bundle.getString("medicine").orEmpty(), bundle.getString("frequency").orEmpty(),
                    bundle.getInt("durationCount", 1), bundle.getString("durationUnit").orEmpty(),
                    bundle.getString("instruction").orEmpty(),
                    if (bundle.containsKey("drugId")) bundle.getInt("drugId") else null,
                    bundle.getString("drugName"),
                    if (bundle.containsKey("itemFormId")) bundle.getInt("itemFormId") else null,
                    bundle.getString("drugForm")
                ) } ?: prescriptions.map { row -> OpdMedicineDraft(
                    row.medicine, row.frequency, row.durationCount, row.durationUnit, row.instruction,
                    row.drugId, row.drugName, row.itemFormId, row.drugForm
                ) }.ifEmpty {
                    generalOpdCache.medications.orEmpty().map { medicine ->
                        val duration = generalOpdCache.duration.orEmpty()
                        OpdMedicineDraft(medicine, generalOpdCache.frequency.orEmpty(),
                            Regex("\\d+").find(duration)?.value?.toIntOrNull() ?: 1,
                            when {
                                duration.isBlank() -> ""
                                duration.contains("month", ignoreCase = true) -> "Month(s)"
                                duration.contains("week", ignoreCase = true) -> "Week(s)"
                                else -> "Day(s)"
                            })
                    }.ifEmpty { if (existing != null || viewOnly) emptyList() else listOf(OpdMedicineDraft()) }
                }
                val restoredComplaints = if (existing == null && !viewOnly)
                    savedStateHandle.get<ArrayList<String>>("opdChiefComplaints") else null
                dataset.setUpPage(generalOpdCache.copy(chiefComplaints = restoredComplaints ?: generalOpdCache.chiefComplaints))
                _recordExists.value = existing != null
                _medicines.value = loadedMedicines
                _notes.value = savedStateHandle.get<String>("opdNotes") ?: generalOpdCache.notes.orEmpty()
                _prescriptionVisible.value = dataset.hasChiefComplaint() ||
                    _medicines.value.orEmpty().any { it.hasData() }
                // Show cached options immediately; a failed network refresh must not block the form.
                if (existing == null && !viewOnly) {
                    if (drugMasterRepo.refreshMasters()) _drugMasters.value = drugMasterRepo.getCachedMasters()
                }
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
            dataset.mapValues(generalOpdCache)
            savedStateHandle["opdChiefComplaints"] = ArrayList(generalOpdCache.chiefComplaints.orEmpty())
            _prescriptionVisible.value = dataset.hasChiefComplaint() ||
                _medicines.value.orEmpty().any { it.hasData() }
        }
    }

    fun saveForm() {
        if (_recordExists.value == null || _recordExists.value == true || viewOnly ||
            _state.value == State.SAVING || _state.value == State.SAVE_SUCCESS) return
        dataset.mapValues(generalOpdCache)
        val rows = _medicines.value.orEmpty().filter { it.medicine.isNotBlank() }.map { it.copy() }
        generalOpdCache.medications = rows.map { it.medicine }.takeIf { it.isNotEmpty() }
        // The legacy API has one common frequency/duration; keep differing details locally.
        generalOpdCache.frequency = rows.map { it.frequency }.distinct().singleOrNull()
        generalOpdCache.duration = rows.map { row ->
            val unit = when (row.durationUnit.lowercase(java.util.Locale.ENGLISH)) {
                "day(s)", "day", "days" -> "day"
                "week(s)", "week", "weeks" -> "week"
                "month(s)", "month", "months" -> "month"
                else -> row.durationUnit
            }
            val suffix = if (unit in listOf("day", "week", "month") && row.durationCount != 1) "s" else ""
            "${row.durationCount} $unit$suffix"
        }.distinct().singleOrNull()
        generalOpdCache.notes = _notes.value?.trim()?.takeIf { it.isNotEmpty() }
        val cache = generalOpdCache.copy(syncState = SyncState.UNSYNCED)
        _state.value = State.SAVING
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (!cache.chiefComplaints.isNullOrEmpty() || rows.isNotEmpty() || !cache.notes.isNullOrBlank()) {
                        tbRepo.saveGeneralOpdWithPrescriptions(cache, rows)
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "saving general opd data failed")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun skipForm() {
        _state.value = State.SKIP_SUCCESS
    }
}
