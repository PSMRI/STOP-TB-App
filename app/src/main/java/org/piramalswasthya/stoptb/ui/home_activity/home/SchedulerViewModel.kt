package org.piramalswasthya.stoptb.ui.home_activity.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SchedulerViewModel @Inject constructor(
    recordsRepo: RecordsRepo,
    preferenceDao: PreferenceDao
) : ViewModel() {

    enum class State { LOADING, LOADED }

    private val _state = MutableLiveData(State.LOADING)
    val state: LiveData<State> get() = _state

    val abhaGeneratedCount: Flow<Int> = recordsRepo.benWithAbhaListCount
    val abhaOldGeneratedCount: Flow<Int> = recordsRepo.benWithOldAbhaListCount
    val abhaNewGeneratedCount: Flow<Int> = recordsRepo.benWithNewAbhaListCount
    val rchIdCount: Flow<Int> = recordsRepo.benWithRchListCount
    val allBenCount: Flow<Int> = recordsRepo.allBenListCount
    val tbCount: Flow<Int> = recordsRepo.tbScreeningListCount
    val ncdCount: Flow<Int> = recordsRepo.getNcdEligibleListCount
    val referralCount: Flow<Int> = recordsRepo.getNcdrefferedListCount

    private val firstName = preferenceDao.getLoggedInUser()
        ?.name
        ?.trim()
        ?.substringBefore(" ")
        ?.takeIf { it.isNotEmpty() }
        ?: "User"

    private val _date = MutableLiveData(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
    )
    val date: LiveData<Long> get() = _date

    fun getFirstName(): String = firstName

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM", Locale.ENGLISH).format(_date.value ?: System.currentTimeMillis())

    fun setDate(dateLong: Long) {
        _date.value = dateLong
        _state.value = State.LOADING
        viewModelScope.launch {
            delay(500)
            updateData()
        }
    }

    init {
        viewModelScope.launch { updateData() }
    }

    private suspend fun updateData() {
        _state.value = State.LOADED
    }
}
