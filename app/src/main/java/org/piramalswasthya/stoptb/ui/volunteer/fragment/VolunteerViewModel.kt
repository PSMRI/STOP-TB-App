package org.piramalswasthya.stoptb.ui.volunteer.fragment

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import javax.inject.Inject

@HiltViewModel
class VolunteerViewModel @Inject constructor(
    private val pref: PreferenceDao,
    private val counsellingRepository: ICounsellingRepository
) : ViewModel() {

    val currentUser = pref.getLoggedInUser()

    private var formVersionCheckJob: Job? = null

    /** Background check on entering the Counselling tab; failures keep the forms already in Room. */
    fun checkFormVersions() {
        if (formVersionCheckJob?.isActive == true) return
        formVersionCheckJob = viewModelScope.launch(Dispatchers.IO) {
            counsellingRepository.refreshFormsIfOutdated(
                listOf(FormType.COMMUNITY_CONTACT_TRACING, FormType.OCCUPATION_CONTACT_TRACING)
            )
        }
    }

    private val _navigateToLoginPage = MutableLiveData(false)
    val navigateToLoginPage: MutableLiveData<Boolean>
        get() = _navigateToLoginPage

    fun logout() {
        viewModelScope.launch {
            val rememberedUser = pref.getRememberedUserName()
            val rememberedPwd = pref.getRememberedPassword()
            val restoreUsernameOnly =
                !rememberedUser.isNullOrBlank() && !rememberedPwd.isNullOrBlank()
            pref.deleteForLogout()
            pref.setLastSyncedTimeStamp(Konstants.defaultTimeStamp)
            if (restoreUsernameOnly) {
                pref.setRememberedUsernameOnly(rememberedUser!!.trim())
            }
            _navigateToLoginPage.value = true
        }
    }

    fun navigateToLoginPageComplete() {
        _navigateToLoginPage.value = false
    }
}