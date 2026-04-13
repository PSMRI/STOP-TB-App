package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.dao.ABHAGenratedDao
import org.piramalswasthya.stoptb.model.ABHAModel
import javax.inject.Inject

class ABHAGenratedRepo @Inject constructor(
    private val abhaGenratedDao: ABHAGenratedDao,
) {
    suspend fun saveAbhaGenrated(abhaModel: ABHAModel) {
        withContext(Dispatchers.IO) {
            abhaGenratedDao.saveABHA(abhaModel)
        }
    }

    suspend fun deleteAbhaByBenId(benId: Long) {
        withContext(Dispatchers.IO) {
            abhaGenratedDao.deleteAbhaByBenId(benId)
        }
    }

}