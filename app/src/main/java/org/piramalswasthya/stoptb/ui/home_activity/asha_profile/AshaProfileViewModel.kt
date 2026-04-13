package org.piramalswasthya.stoptb.ui.home_activity.asha_profile

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.HouseholdRepo
import org.piramalswasthya.stoptb.repositories.UserRepo
import javax.inject.Inject

@HiltViewModel
class AshaProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferenceDao: PreferenceDao,
    @ApplicationContext var context: Context,
    private val householdRepo: HouseholdRepo,
    userRepo: UserRepo
) : ViewModel() {

    val currentUser = preferenceDao.getLoggedInUser()
}