package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.DrugItemMasterCache
import org.piramalswasthya.stoptb.model.DrugFormMasterCache
import org.piramalswasthya.stoptb.model.DrugFrequencyMasterCache
import org.piramalswasthya.stoptb.model.DrugDurationUnitMasterCache
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.DoctorDrugMasterData
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugMasterRepo @Inject constructor(
    private val database: InAppDb,
    private val preferenceDao: PreferenceDao,
    private val api: AmritApiService
) {
    private val refreshMutex = Mutex()

    private suspend fun currentRequest(): Request? {
        val user = preferenceDao.getLoggedInUser() ?: return null
        val category = database.chiefComplaintMasterDao.getGeneralOpdCategoryId() ?: 6
        return Request(category, user.serviceMapId)
    }

    suspend fun getCachedMasters(): OpdDrugMasters = withContext(Dispatchers.IO) {
        val request = currentRequest() ?: return@withContext OpdDrugMasters()
        database.drugMasterDao.getMasters(request.scope)
    }

    suspend fun refreshMasters(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val request = currentRequest() ?: return@withLock false
            try {
                val response = api.getDrugMaster(request.category, request.serviceMapId,
                    MASTER_GENDER, MASTER_VAN_ID, MASTER_FACILITY_ID)
                val body = response.body()
                if (!response.isSuccessful || body?.statusCode != 200) return@withLock false
                val masters = body.data?.toCache(request.scope) ?: return@withLock false
                // Never publish a previous user's in-flight response into the current UI scope.
                if (currentRequest() != request) return@withLock false
                database.drugMasterDao.replaceMasters(request.scope, masters)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Drug master refresh failed; keeping cached data")
                false
            }
        }
    }

    private data class Request(val category: Int, val serviceMapId: Int) {
        val scope: String get() = "$category/$serviceMapId/$MASTER_GENDER/$MASTER_VAN_ID/$MASTER_FACILITY_ID"
    }

    companion object {
        // Temporary values explicitly requested for the inventory master endpoint.
        private const val MASTER_VAN_ID = 0
        private const val MASTER_FACILITY_ID = 134
        private const val MASTER_GENDER = "Male"

        internal fun DoctorDrugMasterData.toCache(scope: String): OpdDrugMasters? {
            val items = itemMaster ?: return null
            val forms = drugFormMaster ?: return null
            val frequencies = drugFrequencyMaster ?: return null
            val durations = drugDurationUnitMaster ?: return null
            if (items.any { it.id <= 0 || it.itemID <= 0 || it.itemName.isNullOrBlank() } ||
                forms.any { it.itemFormID <= 0 || it.itemFormName.isNullOrBlank() } ||
                frequencies.any { it.drugFrequencyID <= 0 || it.frequency.isNullOrBlank() } ||
                durations.any { it.drugDurationID <= 0 || it.drugDuration.isNullOrBlank() }) return null
            return OpdDrugMasters(
                items.map { DrugItemMasterCache(scope, it.id, it.itemID, it.itemName!!.trim(),
                    it.strength, it.unitOfMeasurement, it.quantityInHand, it.itemFormID, it.routeID, it.facilityID) },
                forms.map { DrugFormMasterCache(scope, it.itemFormID, it.itemFormName!!.trim()) },
                frequencies.map { DrugFrequencyMasterCache(scope, it.drugFrequencyID, it.frequency!!.trim()) },
                durations.map { DrugDurationUnitMasterCache(scope, it.drugDurationID, it.drugDuration!!.trim()) }
            )
        }
    }
}
