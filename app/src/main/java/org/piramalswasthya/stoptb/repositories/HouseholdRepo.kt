package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.HouseholdCache
import javax.inject.Inject

class HouseholdRepo @Inject constructor(
    private val dao: HouseholdDao,
    private val benDao: BenDao
) {
    suspend fun getDraftRecord(): HouseholdCache? = withContext(Dispatchers.IO) {
        dao.getDraftHousehold()
    }

    suspend fun getRecord(hhId: Long): HouseholdCache? = withContext(Dispatchers.IO) {
        dao.getHousehold(hhId)
    }

    suspend fun persistRecord(householdCache: HouseholdCache?) = withContext(Dispatchers.IO) {
        householdCache?.let {
            if (dao.getHousehold(it.householdId) == null) {
                dao.upsert(it)
            } else {
                dao.update(it)
            }
        }
    }

    suspend fun substituteHouseholdIdForDraft(household: HouseholdCache) = withContext(Dispatchers.IO) {
        dao.substituteHouseholdId(0, household.householdId)
        household.isDraft = false
    }

    suspend fun getAllBenOfHousehold(householdId: Long): List<BenRegCache> =
        withContext(Dispatchers.IO) {
            benDao.getAllBenForHousehold(householdId)
        }

    suspend fun deleteHouseholdDraft() = withContext(Dispatchers.IO) {
        dao.deleteDraftHousehold()
    }

    /** Returns the DIGIPIN stored against a household, or null if not yet captured. */
    suspend fun getDigipinForHousehold(householdId: Long): String? = withContext(Dispatchers.IO) {
        dao.getHousehold(householdId)?.digipin
    }

    /** Total family members declared at HH registration; null for legacy households with no cap. */
    suspend fun getTotalHhMembers(householdId: Long): Int? = withContext(Dispatchers.IO) {
        dao.getHousehold(householdId)?.family?.totalHhMembers
    }

    /** True once registered members reach the declared total — blocks adding further members. */
    suspend fun isMemberLimitReached(householdId: Long): Boolean = withContext(Dispatchers.IO) {
        val total = dao.getHousehold(householdId)?.family?.totalHhMembers ?: return@withContext false
        benDao.getAllBenForHousehold(householdId).size >= total
    }
}
