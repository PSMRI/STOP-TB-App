package org.piramalswasthya.stoptb.repositories

import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AmritApiService
import javax.inject.Inject

class AshaRepo @Inject constructor(
    private val amritApiService: AmritApiService,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo
)