package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * Push/pull bookkeeping: sync state, unsynced/unprocessed rows and server ids.
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenSyncQueries {

    @Query("UPDATE  BENEFICIARY SET syncState = :unsynced ,processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId")
    suspend fun updateBenToSync(
        householdId: Long,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )


    @Query("UPDATE BENEFICIARY SET syncState = :syncState WHERE beneficiaryId =:benId AND householdId IS :hhId")
    suspend fun setSyncState(hhId: Long?, benId: Long, syncState: SyncState)

    @Query("UPDATE BENEFICIARY SET serverUpdatedStatus = 1 , beneficiaryId = :newId , benRegId =:newBenRegId ,processed = 'U', userImage = :imageUri  WHERE householdId IS :hhId AND beneficiaryId =:oldId")
    suspend fun updateToFinalBenId(hhId: Long?, oldId: Long, newId: Long, imageUri: String? = null,newBenRegId:Long)

    @Query("SELECT * FROM BENEFICIARY WHERE isDraft = 0 AND processed = 'N' AND syncState =:unsynced ")
    suspend fun getAllUnprocessedBen(unsynced: SyncState = SyncState.UNSYNCED): List<BenRegCache>

    @Query("SELECT * FROM BENEFICIARY WHERE isDraft = 0 AND (processed = 'N' OR processed = 'U') AND syncState =:unsynced ")
    suspend fun getAllUnsyncedBen(unsynced: SyncState = SyncState.UNSYNCED): List<BenRegCache>

    //    @Query("SELECT * FROM BENEFICIARY WHERE isDraft = 0 AND processed = 'U' AND syncState =:unsynced AND isConsent = 1")
    @Query("SELECT * FROM BENEFICIARY WHERE isDraft = 0 AND processed = 'U' AND syncState =:unsynced")
    suspend fun getAllBenForSyncWithServer(unsynced: SyncState = SyncState.UNSYNCED): List<BenRegCache>

    // Only rows still SYNCING are touched: a row edited while its upload was in
    // flight is back to UNSYNCED and must stay queued for the next push.
    // Claims records for a push: only rows still UNSYNCED become SYNCING. Together with
    // getSyncingBens, the payload is built from the rows as they are when claimed, so an edit
    // made before the claim is sent and one made after it (UNSYNCED again) is not marked synced.
    @Query("UPDATE BENEFICIARY SET syncState = 1 WHERE beneficiaryId IN (:benIds) AND syncState = 0 AND isDraft = 0 AND processed = 'U'")
    suspend fun claimForSync(benIds: List<Long>)

    @Query("SELECT * FROM BENEFICIARY WHERE beneficiaryId IN (:benIds) AND syncState = 1")
    suspend fun getSyncingBens(benIds: List<Long>): List<BenRegCache>

    @Query("UPDATE BENEFICIARY SET processed = 'P' , syncState = 2 WHERE beneficiaryId in (:benId) AND syncState = 1")
    suspend fun benSyncedWithServer(vararg benId: Long)

    @Query("UPDATE BENEFICIARY SET processed = 'U' , syncState = 0 WHERE beneficiaryId in (:benId) AND syncState = 1")
    suspend fun benSyncWithServerFailed(vararg benId: Long)

    @Query("UPDATE BENEFICIARY SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToUnsynced()
}
