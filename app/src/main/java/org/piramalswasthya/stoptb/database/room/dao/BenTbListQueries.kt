package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * TB screening, presumptive, confirmed and referral list queries.
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenTbListQueries {

    @Transaction
    // tbsnFilled is a literal in the view now - these two genuinely mean "has a screening
    // row", so they ask TB_SCREENING directly and go on observing it, which is correct here.
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and isDeactivate=0 and EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = BEN_BASIC_CACHE.benId) ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getPendingTbScreeningBen(selectedVillage: Int): Flow<List<BenWithTbScreeningCache>>

    @Transaction
    @Query(PRESUMPTIVE_LIST_SQL + " ORDER BY b.updatedDate DESC")
    fun getTbScreeningList(villageId: Int): Flow<List<BenWithTbSuspectedCache>>

    @Transaction
    @Query(CONFIRMED_LIST_SQL + " ORDER BY b.updatedDate DESC")
    fun getTbConfirmedList(
        villageId: Int
    ): Flow<List<BenWithTbSuspectedCache>>

    /**
     * PR 6: the three TB list screens page from here (Room loads each page's @Relation children
     * with it) and search in SQL ([BenListSearchSql]); `query` is lowercased, '' = all.
     * The Flow lists above stay as the reference the home-card counts are tested against.
     */
    @Transaction
    @Query(TB_SCREENING_PAGED_SQL)
    fun getTbScreeningBenPaged(villageId: Int, query: String): PagingSource<Int, BenWithTbScreeningCache>

    @Transaction
    @Query(PRESUMPTIVE_PAGED_SQL)
    fun getTbPresumptivePaged(villageId: Int, query: String): PagingSource<Int, BenWithTbSuspectedCache>

    @Transaction
    @Query(CONFIRMED_PAGED_SQL)
    fun getTbConfirmedPaged(villageId: Int, query: String): PagingSource<Int, BenWithTbSuspectedCache>

    /** Home card count for the Presumptive list: same rows as [getTbScreeningList], counted in SQL. */
    @Query("SELECT COUNT(*) FROM (" + PRESUMPTIVE_LIST_SQL + ")")
    fun getTbPresumptiveListCount(villageId: Int): Flow<Int>

    /** Home card count for the Confirmed list: same rows as [getTbConfirmedList], counted in SQL. */
    @Query("SELECT COUNT(*) FROM (" + CONFIRMED_LIST_SQL + ")")
    fun getTbConfirmedListCount(villageId: Int): Flow<Int>



    @Transaction
    @Query("""
    SELECT  r.*, b.*
   FROM BEN_BASIC_CACHE b
   INNER JOIN NCD_REFER r ON b.benId = r.benId
   WHERE b.villageId = :selectedVillage
     AND b.isDeactivate = 0
     AND (
       r.type = "TB"
       OR (
         CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= :min
         AND b.reproductiveStatusId != 2
       )
     )
   ORDER BY b.regDate DESC
""")
    fun getBenWithReferredCbac(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForNcd
    ): Flow<List<BenWithCbacAndReferalCache>>

    @Query("""
    SELECT  r.*, b.*
    FROM BEN_BASIC_CACHE b
    INNER JOIN NCD_REFER r ON b.benId = r.benId
      AND b.villageId = :selectedVillage
      AND b.isDeactivate=0
      AND r.type = "MATERNAL"
        ORDER BY b.updatedDate DESC
    """)
    fun getReferredHWCBenList(
        selectedVillage: Int,
    ):  Flow<List<BenWithCbacAndReferalCache>>

    @Query("UPDATE BENEFICIARY SET chestXrayDoneDate = :date, screeningStatus = 'CHEST_XRAY_DONE', syncState = :unsynced, processed = 'U', serverUpdatedStatus = 2 WHERE beneficiaryId = :benId")
    suspend fun markChestXrayDone(benId: Long, date: Long = System.currentTimeMillis(), unsynced: SyncState = SyncState.UNSYNCED)

    companion object {
        /**
         * Shared by the Presumptive list and its home-card count so the two can't drift apart.
         * The list adds ORDER BY; the count wraps it in COUNT(*). Definition: [TbCaseSql.PRESUMPTIVE].
         */
        private const val PRESUMPTIVE_WHERE_SQL = "SELECT b.* FROM BEN_BASIC_CACHE b" +
            " INNER JOIN TB_SCREENING scr ON b.benId = scr.benId" +
            " LEFT JOIN TB_SUSPECTED sus ON b.benId = sus.benId" +
            " LEFT JOIN TB_DIAGNOSTICS dx ON b.benId = dx.benId" +
            " WHERE b.villageId = :villageId AND b.isDeactivate = 0 AND b.isDeath = 0" +
            " AND " + TbCaseSql.PRESUMPTIVE

        const val PRESUMPTIVE_LIST_SQL = PRESUMPTIVE_WHERE_SQL + " GROUP BY b.benId"

        /** List order for the paged TB lists; benId breaks ties so LIMIT/OFFSET pages are stable. */
        private const val PAGED_ORDER = " ORDER BY b.updatedDate DESC, b.benId DESC"

        const val TB_SCREENING_PAGED_SQL = "SELECT * FROM BEN_BASIC_CACHE b" +
            " WHERE b.villageId = :villageId AND b.isDeath = 0 AND b.isDeactivate = 0" +
            " AND " + BenListSearchSql.MATCHES + PAGED_ORDER

        const val PRESUMPTIVE_PAGED_SQL = PRESUMPTIVE_WHERE_SQL + " AND " + BenListSearchSql.MATCHES +
            " GROUP BY b.benId" + PAGED_ORDER

        /** Shared by the Confirmed list and its home-card count. Definition: [TbCaseSql.CONFIRMED]. */
        const val CONFIRMED_LIST_SQL = "SELECT DISTINCT b.* FROM BEN_BASIC_CACHE b" +
            " LEFT JOIN TB_SUSPECTED sus ON b.benId = sus.benId" +
            " LEFT JOIN TB_DIAGNOSTICS dx ON b.benId = dx.benId" +
            " WHERE b.villageId = :villageId AND b.isDeactivate = 0 AND b.isDeath = 0" +
            " AND " + TbCaseSql.CONFIRMED

        const val CONFIRMED_PAGED_SQL = CONFIRMED_LIST_SQL + " AND " + BenListSearchSql.MATCHES + PAGED_ORDER
    }
}
