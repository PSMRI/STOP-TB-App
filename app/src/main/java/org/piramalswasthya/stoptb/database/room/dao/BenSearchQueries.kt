package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * Beneficiary search and paged list queries (All Beneficiaries, Non-Household, search box).
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl.
 */
interface BenSearchQueries {

    // One page query per beneficiary list (RecordsRepo.benListPage picks it by source). The
    // pieces are in [BenListSql]. :query is the typed text ('' = no search), :ftsQuery its FTS form.

    /** Source 0: All Beneficiaries. */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun allBenPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 1: with an ABHA number. */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.ABHA + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun abhaBenPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 2: with an RCH id. */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.RCH + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun rchBenPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 3: aged 30 or more. */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.AGE_30_PLUS + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun age30PlusPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 4: eligible couples. */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.ELIGIBLE_COUPLE + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun eligibleCouplePage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 5: Health and Wellness Centre referral. */
    @Query(BenListSql.HWC_PAGE)
    suspend fun hwcReferralPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 6: Digital Chest X-ray referral. */
    @Query(BenListSql.XRAY_PAGE)
    suspend fun xrayReferralPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 7: TrueNat referral. */
    @Query(BenListSql.TRUENAT_PAGE)
    suspend fun trueNatReferralPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 8: Liquid Culture referral. */
    @Query(BenListSql.LIQUID_CULTURE_PAGE)
    suspend fun liquidCultureReferralPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 9: TPT (household contacts of a confirmed case). */
    @Query(BenListSql.FROM_VILLAGES + BenListSql.TPT + BenListSql.FILTERS + BenListSql.DEFAULT_ORDER + BenListSql.PAGE)
    suspend fun tptPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Source 10: Clinical Assessment & Diagnosis referral. */
    @Query(BenListSql.CLINICAL_ASSESSMENT_PAGE)
    suspend fun clinicalAssessmentPage(villageIds: List<Int>, filterType: Int, query: String, ftsQuery: String, limit: Int, offset: Int): List<BenBasicCache>


    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE villageId = :selectedVillage AND isDeactivate = 0 AND isNonHH = 1 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getNonHHBeneficiaries(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE villageId = :selectedVillage AND isDeactivate = 0 AND isNonHH = 1")
    fun getNonHHCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage AND isDeactivate = 0 AND isNonHH = 1
        AND (:query = '' OR benName LIKE '%' || :query || '%' OR benSurname LIKE '%' || :query || '%' OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%' OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%')
        ORDER BY BEN_BASIC_CACHE.updatedDate DESC
    """)
    fun searchNonHHBeneficiaries(selectedVillage: Int, query: String): Flow<List<BenBasicCache>>

    /**
     * Paged form of [searchNonHHBeneficiaries]. The Flow variant materialises every matching
     * row - on a camp device that is the whole village, ~20k rows of 92 columns, rebuilt on
     * every database invalidation. That allocation is what pushes the heap into continuous GC,
     * so the screen loads a page at a time instead.
     *
     * The ORDER BY is required, not cosmetic. A PagingSource walks this with LIMIT/OFFSET, and
     * OFFSET only identifies the same rows twice if the statement has a total order to offset
     * into. Without one the order is whatever the plan happens to produce, so a plan change -
     * a different index chosen once the search term is non-empty, say - can silently hand the
     * adapter a row it has already shown or skip one it has not.
     *
     * Recently created first, the same order as the All Beneficiaries list
     * (searchBenPagedWithoutTbTables), so a worker finds the person they just registered at the
     * top. A pull stores createdDate = 0 when the server sends none, so NULLIF lets regDate
     * stand in for it. benId DESC breaks ties so the order is total.
     *
     * This replaces the earlier benId ASC order. benId is a server-assigned id, not a date, so
     * ASC put new registrations at the bottom. ASC was chosen because rows inserted ahead of the
     * pages already read shift the offsets under the adapter (a pull test showed duplicates under
     * benId DESC). Pulled rows carry their server createdDate, so they mostly sort below the
     * worker's recent registrations, and Room's PagingSource invalidates and reloads around the
     * anchor on every table change, so this matches the trade-off All Beneficiaries already makes.
     */
    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage AND isDeactivate = 0 AND isNonHH = 1
        AND (:query = '' OR benName LIKE '%' || :query || '%' OR benSurname LIKE '%' || :query || '%' OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%' OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%')
        ORDER BY COALESCE(NULLIF(createdDate, 0), regDate, 0) DESC, benId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchNonHHBeneficiariesPage(selectedVillage: Int, query: String, limit: Int, offset: Int): List<BenBasicCache>
}
