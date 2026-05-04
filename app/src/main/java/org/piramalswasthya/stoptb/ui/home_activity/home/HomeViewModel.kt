package org.piramalswasthya.stoptb.ui.home_activity.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.repositories.UserRepo
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val database: InAppDb,
    private val pref: PreferenceDao,
    private val userRepo: UserRepo,
) : ViewModel() {

    private val _devModeState: MutableLiveData<Boolean> = MutableLiveData(pref.isDevModeEnabled)
    val devModeEnabled: LiveData<Boolean> get() = _devModeState

    val currentUser = pref.getLoggedInUser()
    val numBenIdsAvail = database.benIdGenDao.liveCount()

    var profilePicUri: Uri?
        get() = pref.getProfilePicUri()
        set(value) = pref.saveProfilePicUri(value)

    val scope: CoroutineScope get() = viewModelScope

    private var _unprocessedRecordsCount: MutableLiveData<Int> = MutableLiveData(0)
    val unprocessedRecordsCount: LiveData<Int> get() = _unprocessedRecordsCount

    val homeToolbarTitle: String?
        get() = pref.getLocationRecord()?.village?.let { village ->
            when (pref.getCurrentLanguage()) {
                Languages.ENGLISH -> village.name
                Languages.HINDI -> village.nameHindi ?: village.name
                Languages.ASSAMESE -> village.nameAssamese ?: village.name
            }
        }

    private val _navigateToLoginPage = MutableLiveData(false)
    val navigateToLoginPage: MutableLiveData<Boolean> get() = _navigateToLoginPage

    private val _restoredProfilePicUri = MutableLiveData<Uri?>()
    val restoredProfilePicUri: LiveData<Uri?> get() = _restoredProfilePicUri

    init {
        viewModelScope.launch {
            launch {
                userRepo.unProcessedRecordCount.collect { value ->
                    _unprocessedRecordsCount.value =
                        value.filter { it.syncState != SyncState.SYNCED }.sumOf { it.count }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            pref.deleteLoginCred()
            _navigateToLoginPage.value = true
        }
    }

    fun navigateToLoginPageComplete() {
        _navigateToLoginPage.value = false
    }

    fun setDevMode(boolean: Boolean) {
        pref.isDevModeEnabled = boolean
        _devModeState.value = boolean
    }

    fun getDebMode() = pref.isDevModeEnabled

    fun saveProfilePicFromGallery(context: Context, galleryUri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                currentUser?.let { user ->
                    val persistedUri = ImageUtils.saveBenImageFromCameraToStorage(
                        context = context,
                        uriString = galleryUri.toString(),
                        benId = user.userId.toLong()
                    )
                    if (persistedUri != null) {
                        val uri = Uri.parse(persistedUri)
                        pref.saveProfilePicUri(uri)
                    }
                }
            }
        }
    }
}
