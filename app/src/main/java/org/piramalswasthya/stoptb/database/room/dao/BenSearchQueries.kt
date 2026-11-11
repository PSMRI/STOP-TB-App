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
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenSearchQueries {


    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId IN (:villageIds)
        AND isDeactivate = 0
        AND (:source = 0
            OR (:source = 1 AND abhaId IS NOT NULL)
            OR (:source = 2 AND rchId IS NOT NULL AND rchId != '')
            OR (:source = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:source = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
            OR (:source = 5 AND isDeath = 0 AND benId IN (
                SELECT b2.beneficiaryId FROM BENEFICIARY b2
                WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0
                UNION
                SELECT v.benId FROM BEN_VITALS v
                WHERE (v.pulseRate IS NOT NULL AND v.pulseRate < 60)
                   OR (v.pulseRate IS NOT NULL AND v.pulseRate > 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic < 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic >= 140)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic < 60)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic >= 90)
                   OR (v.rbs IS NOT NULL AND v.rbs >= 100)
                UNION
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(ts.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(td.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
            ))
            OR (:source = 6 AND isDeath = 0 AND reproductiveStatusId != 1 AND benId NOT IN (SELECT v.benId FROM BEN_VITALS v WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%') AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                )
            ))
            OR (:source = 7 AND isDeath = 0 AND (
                ((reproductiveStatusId = 1 OR benId IN (
                    SELECT v.benId FROM BEN_VITALS v
                    WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%'
                )) AND benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                ))
                OR benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    -- An order the backend already holds makes this beneficiary a member,
                    -- with or without local clinical evidence. Every bucket
                    -- getBeneficiariesByStatus reports - awaitingProviderResult, completed,
                    -- awaitingManualEntry, failed, closed - means an order exists, yet a
                    -- beneficiary awaiting a provider result has isNaatConducted still NULL and
                    -- no result, so none of the clinical clauses match. One referred from
                    -- another tab can reach this device with the order and no screening row of
                    -- its own and was then missing from the list entirely.
                    --
                    -- Uncorrelated, so SQLite materialises it once per query, not once per row.
                    WHERE td.trueNatOrderId IS NOT NULL
                       OR td.trueNatOrderStatus IS NOT NULL
                       OR td.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE
                        tbs.recommendedForTruenatTest = 1
                        OR tbs.referredForSputumCollection = 1
                        OR (
                            tbs.bloodInSputum = 1
                            OR tbs.coughMoreThan2Weeks = 1
                            OR tbs.feverMoreThan2Weeks = 1
                            OR tbs.riseOfFever = 1
                            OR tbs.lossOfAppetite = 1
                            OR tbs.lossOfWeight = 1
                            OR tbs.nightSweats = 1
                            OR tbs.historyOfTb = 1
                            OR tbs.takingAntiTBDrugs = 1
                            OR tbs.familySufferingFromTB = 1
                            OR UPPER(IFNULL(tbs.asymptomatic, '')) = 'NO'
                        )
                )
            ))
            OR (:source = 8 AND isDeath = 0 AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE tbs.recommendedForLiquidCultureTest = 1
                )
            ))
            OR (:source = 9 AND isDeath = 0 AND hhId IS NOT NULL AND EXISTS (
                SELECT 1
                FROM BEN_BASIC_CACHE b2
                LEFT JOIN TB_SUSPECTED ts ON b2.benId = ts.benId
                LEFT JOIN TB_DIAGNOSTICS td ON b2.benId = td.benId
                WHERE b2.hhId = BEN_BASIC_CACHE.hhId
                  AND b2.isDeactivate = 0
                  AND b2.isDeath = 0
                  AND (
                        ts.isTbConfirmed = 1
                        OR td.isTbConfirmed = 1
                        OR UPPER(IFNULL(td.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
                        OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE'
                  )
            ))
            OR (:source = 10 AND isDeath = 0 AND benId IN (
                -- TB_SUSPECTED has no RIF field of its own (mdrRifResult is only ever mirrored
                -- here from TB_DIAGNOSTICS.trueNatRifResult by syncTBSuspectedFromDiagnostics, so
                -- checking it would be redundant with the td.trueNatRifResult check below) — the
                -- older SuspectedTBDataset form only ever writes chestXRayResult/naatResult here
                -- directly, with no TB_DIAGNOSTICS row at all, so that's the gap this union closes.
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(TRIM(IFNULL(ts.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(TRIM(IFNULL(ts.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE (UPPER(TRIM(IFNULL(td.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                       AND UPPER(TRIM(IFNULL(td.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED'))
                   OR UPPER(TRIM(IFNULL(td.trueNatRifResult,''))) = 'INDETERMINATE'
            ))
        )
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR
            benName LIKE '%' || :query || '%'
            OR benSurname LIKE '%' || :query || '%'
            OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR REPLACE(IFNULL(abhaId, ''), '-', '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR IFNULL(familyHeadName, '') LIKE '%' || :query || '%'
            OR IFNULL(spouseName, '') LIKE '%' || :query || '%'
            OR IFNULL(fatherName, '') LIKE '%' || :query || '%'
            OR CAST(benId AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR CAST(hhId AS TEXT) LIKE '%' || :query || '%'
            OR IFNULL(rchId, '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
        )
        ORDER BY CASE
            WHEN isDeath = 0 AND isDeactivate = 0 THEN 0
            WHEN isDeath = 1 AND isDeactivate = 0 THEN 1
            WHEN isDeactivate = 1 THEN 2
            ELSE 3
        END ASC,
        CASE
            WHEN :source = 6 OR :source = 7 THEN COALESCE(
                (
                    SELECT MAX(visitDate) FROM (
                        SELECT visitDate FROM TB_SCREENING WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_SUSPECTED WHERE benId = BEN_BASIC_CACHE.benId
                    )
                ),
                0
            )
            ELSE COALESCE(createdDate, regDate, 0)
        END DESC,
        benId DESC
    """)
    fun searchBen(villageIds: List<Int>, source: Int, filterType: Int, query: String): Flow<List<BenBasicCache>>

    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId IN (:villageIds)
        AND isDeactivate = 0
        AND (:source = 0
            OR (:source = 1 AND abhaId IS NOT NULL)
            OR (:source = 2 AND rchId IS NOT NULL AND rchId != '')
            OR (:source = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:source = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
            OR (:source = 5 AND isDeath = 0 AND benId IN (
                SELECT b2.beneficiaryId FROM BENEFICIARY b2
                WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0
                UNION
                SELECT v.benId FROM BEN_VITALS v
                WHERE (v.pulseRate IS NOT NULL AND v.pulseRate < 60)
                   OR (v.pulseRate IS NOT NULL AND v.pulseRate > 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic < 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic >= 140)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic < 60)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic >= 90)
                   OR (v.rbs IS NOT NULL AND v.rbs >= 100)
                UNION
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(ts.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(td.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
            ))
            OR (:source = 6 AND isDeath = 0 AND reproductiveStatusId != 1 AND benId NOT IN (SELECT v.benId FROM BEN_VITALS v WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%') AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                )
            ))
            OR (:source = 7 AND isDeath = 0 AND (
                ((reproductiveStatusId = 1 OR benId IN (
                    SELECT v.benId FROM BEN_VITALS v
                    WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%'
                )) AND benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                ))
                OR benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    -- An order the backend already holds makes this beneficiary a member,
                    -- with or without local clinical evidence. Every bucket
                    -- getBeneficiariesByStatus reports - awaitingProviderResult, completed,
                    -- awaitingManualEntry, failed, closed - means an order exists, yet a
                    -- beneficiary awaiting a provider result has isNaatConducted still NULL and
                    -- no result, so none of the clinical clauses match. One referred from
                    -- another tab can reach this device with the order and no screening row of
                    -- its own and was then missing from the list entirely.
                    --
                    -- Uncorrelated, so SQLite materialises it once per query, not once per row.
                    WHERE td.trueNatOrderId IS NOT NULL
                       OR td.trueNatOrderStatus IS NOT NULL
                       OR td.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE
                        tbs.recommendedForTruenatTest = 1
                        OR tbs.referredForSputumCollection = 1
                        OR (
                            tbs.bloodInSputum = 1
                            OR tbs.coughMoreThan2Weeks = 1
                            OR tbs.feverMoreThan2Weeks = 1
                            OR tbs.riseOfFever = 1
                            OR tbs.lossOfAppetite = 1
                            OR tbs.lossOfWeight = 1
                            OR tbs.nightSweats = 1
                            OR tbs.historyOfTb = 1
                            OR tbs.takingAntiTBDrugs = 1
                            OR tbs.familySufferingFromTB = 1
                            OR UPPER(IFNULL(tbs.asymptomatic, '')) = 'NO'
                        )
                )
            ))
            OR (:source = 8 AND isDeath = 0 AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE tbs.recommendedForLiquidCultureTest = 1
                )
            ))
            OR (:source = 9 AND isDeath = 0 AND hhId IS NOT NULL AND EXISTS (
                SELECT 1
                FROM BEN_BASIC_CACHE b2
                LEFT JOIN TB_SUSPECTED ts ON b2.benId = ts.benId
                LEFT JOIN TB_DIAGNOSTICS td ON b2.benId = td.benId
                WHERE b2.hhId = BEN_BASIC_CACHE.hhId
                  AND b2.isDeactivate = 0
                  AND b2.isDeath = 0
                  AND (
                        ts.isTbConfirmed = 1
                        OR td.isTbConfirmed = 1
                        OR UPPER(IFNULL(td.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
                        OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE'
                  )
            ))
            OR (:source = 10 AND isDeath = 0 AND benId IN (
                -- TB_SUSPECTED has no RIF field of its own (mdrRifResult is only ever mirrored
                -- here from TB_DIAGNOSTICS.trueNatRifResult by syncTBSuspectedFromDiagnostics, so
                -- checking it would be redundant with the td.trueNatRifResult check below) — the
                -- older SuspectedTBDataset form only ever writes chestXRayResult/naatResult here
                -- directly, with no TB_DIAGNOSTICS row at all, so that's the gap this union closes.
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(TRIM(IFNULL(ts.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(TRIM(IFNULL(ts.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE (UPPER(TRIM(IFNULL(td.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                       AND UPPER(TRIM(IFNULL(td.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED'))
                   OR UPPER(TRIM(IFNULL(td.trueNatRifResult,''))) = 'INDETERMINATE'
            ))
        )
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR
            benName LIKE '%' || :query || '%'
            OR benSurname LIKE '%' || :query || '%'
            OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR REPLACE(IFNULL(abhaId, ''), '-', '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR IFNULL(familyHeadName, '') LIKE '%' || :query || '%'
            OR IFNULL(spouseName, '') LIKE '%' || :query || '%'
            OR IFNULL(fatherName, '') LIKE '%' || :query || '%'
            OR CAST(benId AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR CAST(hhId AS TEXT) LIKE '%' || :query || '%'
            OR IFNULL(rchId, '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
        )
        ORDER BY CASE
            WHEN isDeath = 0 AND isDeactivate = 0 THEN 0
            WHEN isDeath = 1 AND isDeactivate = 0 THEN 1
            WHEN isDeactivate = 1 THEN 2
            ELSE 3
        END ASC,
        -- X-ray/TrueNat lists only (source 6/7): surface rows needing action (Failed/Manual
        -- Entry) above merely-pending ones, which rank above resolved (Completed), which rank
        -- above terminal (Closed) — instead of ordering purely by recency, which could bury a
        -- beneficiary whose order genuinely needs a retry under one that's already resolved.
        -- Every other source falls into the ELSE branch, unaffected, same as before.
        --
        -- An order that is placed and awaiting a result (tier 1) now ranks above a beneficiary
        -- who has no order at all (tier 2). Both used to share one tier, so a live order sat
        -- among everyone who merely qualifies for the test - on a full village that is most of
        -- the list, and the one beneficiary actually waiting on a device was indistinguishable.
        -- Completed and Closed keep their existing position below both.
        --
        -- IFNULL is what separates them: a bare CASE cannot match NULL (NULL = NULL is never
        -- true), so "no order" and "order pending" both fell through to the same ELSE. Folding
        -- NULL to '' costs nothing and keeps this a simple CASE, which evaluates its subject
        -- expression exactly once - the subquery count per row is unchanged.
        CASE
            WHEN :source = 6 THEN CASE IFNULL(
                (SELECT xrayOrderStatus FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1), '')
                WHEN 'FAILED' THEN 0
                WHEN 'MANUAL_ENTRY' THEN 0
                -- No status, but an order id means the order exists and is in flight: rank it
                -- with the other in-flight orders, not with beneficiaries who have no order.
                -- The pull can store id and status independently. The subquery only runs for
                -- rows that reached this branch, i.e. those with no status at all.
                WHEN '' THEN (CASE WHEN (SELECT xrayOrderId FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1) IS NOT NULL THEN 1 ELSE 2 END)
                WHEN 'COMPLETED' THEN 3
                WHEN 'CLOSED' THEN 4
                ELSE 1
            END
            WHEN :source = 7 THEN CASE IFNULL(
                (SELECT trueNatOrderStatus FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1), '')
                WHEN 'FAILED' THEN 0
                WHEN 'MANUAL_ENTRY' THEN 0
                -- No status, but an order id means the order exists and is in flight: rank it
                -- with the other in-flight orders, not with beneficiaries who have no order.
                -- The pull can store id and status independently. The subquery only runs for
                -- rows that reached this branch, i.e. those with no status at all.
                WHEN '' THEN (CASE WHEN (SELECT trueNatOrderId FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1) IS NOT NULL THEN 1 ELSE 2 END)
                WHEN 'COMPLETED' THEN 3
                WHEN 'CLOSED' THEN 4
                ELSE 1
            END
            ELSE 1
        END ASC,
        CASE
            WHEN :source = 6 OR :source = 7 THEN
                -- Within the "needs action" tier (Failed/Manual Entry), oldest first so a
                -- beneficiary doesn't silently age out of view; every other tier keeps the
                -- existing most-recent-visit-first order.
                (CASE WHEN (
                    CASE
                        WHEN :source = 6 THEN CASE
                            (SELECT xrayOrderStatus FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1)
                            WHEN 'FAILED' THEN 0
                            WHEN 'MANUAL_ENTRY' THEN 0
                            ELSE 1
                        END
                        WHEN :source = 7 THEN CASE
                            (SELECT trueNatOrderStatus FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId ORDER BY id DESC LIMIT 1)
                            WHEN 'FAILED' THEN 0
                            WHEN 'MANUAL_ENTRY' THEN 0
                            ELSE 1
                        END
                    END = 0
                ) THEN -1 ELSE 1 END) * COALESCE(
                    (
                        SELECT MAX(visitDate) FROM (
                            SELECT visitDate FROM TB_SCREENING WHERE benId = BEN_BASIC_CACHE.benId
                            UNION ALL
                            SELECT visitDate FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId
                            UNION ALL
                            SELECT visitDate FROM TB_SUSPECTED WHERE benId = BEN_BASIC_CACHE.benId
                        )
                    ),
                    0
                )
            ELSE COALESCE(createdDate, regDate, 0)
        END DESC,
        benId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchBenPage(villageIds: List<Int>, source: Int, filterType: Int, query: String, limit: Int, offset: Int): List<BenBasicCache>

    /**
     * The beneficiary list query for the sources that have no TB predicate: 0 All, 1 ABHA,
     * 2 RCH, 3 age 30+, 4 eligible couple.
     *
     * searchBenPaged() handles every source in one statement, and its :source = 5..10 branches
     * name TB_SUSPECTED, TB_DIAGNOSTICS, TB_SCREENING and BEN_VITALS. Room derives a query's
     * observed tables from the SQL text, not from which branch runs, so the All Beneficiaries list
     * was invalidated by every TB write even though it can never take those branches - and with
     * the sync writing every couple of seconds the list was cancelled and restarted faster than it
     * could load, leaving a spinner that never resolved.
     *
     * This statement mentions only BEN_BASIC_CACHE, so it observes BENEFICIARY and HOUSEHOLD and
     * nothing else. The WHERE/ORDER BY are otherwise identical to searchBenPaged's for these
     * sources, with the 6/7-only ORDER BY branches dropped because they collapse to the ELSE arm.
     */
    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId IN (:villageIds)
        AND isDeactivate = 0
        AND (:source = 0
            OR (:source = 1 AND abhaId IS NOT NULL)
            OR (:source = 2 AND rchId IS NOT NULL AND rchId != '')
            OR (:source = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:source = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR
            benName LIKE '%' || :query || '%'
            OR benSurname LIKE '%' || :query || '%'
            OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR REPLACE(IFNULL(abhaId, ''), '-', '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR IFNULL(familyHeadName, '') LIKE '%' || :query || '%'
            OR IFNULL(spouseName, '') LIKE '%' || :query || '%'
            OR IFNULL(fatherName, '') LIKE '%' || :query || '%'
            OR CAST(benId AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR CAST(hhId AS TEXT) LIKE '%' || :query || '%'
            OR IFNULL(rchId, '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
        )
        ORDER BY CASE
            WHEN isDeath = 0 AND isDeactivate = 0 THEN 0
            WHEN isDeath = 1 AND isDeactivate = 0 THEN 1
            WHEN isDeactivate = 1 THEN 2
            ELSE 3
        END ASC,
        COALESCE(createdDate, regDate, 0) DESC,
        benId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchBenPageWithoutTbTables(villageIds: List<Int>, source: Int, filterType: Int, query: String, limit: Int, offset: Int): List<BenBasicCache>



    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId IN (:villageIds)
        AND isDeactivate = 0
        AND (:source = 0
            OR (:source = 1 AND abhaId IS NOT NULL)
            OR (:source = 2 AND rchId IS NOT NULL AND rchId != '')
            OR (:source = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:source = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
            OR (:source = 5 AND isDeath = 0 AND benId IN (
                SELECT b2.beneficiaryId FROM BENEFICIARY b2
                WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0
                UNION
                SELECT v.benId FROM BEN_VITALS v
                WHERE (v.pulseRate IS NOT NULL AND v.pulseRate < 60)
                   OR (v.pulseRate IS NOT NULL AND v.pulseRate > 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic < 90)
                   OR (v.bpSystolic IS NOT NULL AND v.bpSystolic >= 140)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic < 60)
                   OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic >= 90)
                   OR (v.rbs IS NOT NULL AND v.rbs >= 100)
                UNION
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(ts.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(IFNULL(td.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
            ))
            OR (:source = 6 AND isDeath = 0 AND reproductiveStatusId != 1 AND benId NOT IN (SELECT v.benId FROM BEN_VITALS v WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%') AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isChestXRayDone IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                )
            ))
            OR (:source = 7 AND isDeath = 0 AND (
                ((reproductiveStatusId = 1 OR benId IN (
                    SELECT v.benId FROM BEN_VITALS v
                    WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%'
                )) AND benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                ))
                OR benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    -- An order the backend already holds makes this beneficiary a member,
                    -- with or without local clinical evidence. Every bucket
                    -- getBeneficiariesByStatus reports - awaitingProviderResult, completed,
                    -- awaitingManualEntry, failed, closed - means an order exists, yet a
                    -- beneficiary awaiting a provider result has isNaatConducted still NULL and
                    -- no result, so none of the clinical clauses match. One referred from
                    -- another tab can reach this device with the order and no screening row of
                    -- its own and was then missing from the list entirely.
                    --
                    -- Uncorrelated, so SQLite materialises it once per query, not once per row.
                    WHERE td.trueNatOrderId IS NOT NULL
                       OR td.trueNatOrderStatus IS NOT NULL
                       OR td.isNaatConducted IS NOT NULL
                       OR UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE
                        tbs.recommendedForTruenatTest = 1
                        OR tbs.referredForSputumCollection = 1
                        OR (
                            tbs.bloodInSputum = 1
                            OR tbs.coughMoreThan2Weeks = 1
                            OR tbs.feverMoreThan2Weeks = 1
                            OR tbs.riseOfFever = 1
                            OR tbs.lossOfAppetite = 1
                            OR tbs.lossOfWeight = 1
                            OR tbs.nightSweats = 1
                            OR tbs.historyOfTb = 1
                            OR tbs.takingAntiTBDrugs = 1
                            OR tbs.familySufferingFromTB = 1
                            OR UPPER(IFNULL(tbs.asymptomatic, '')) = 'NO'
                        )
                )
            ))
            OR (:source = 8 AND isDeath = 0 AND (
                benId IN (
                    SELECT ts.benId FROM TB_SUSPECTED ts
                    WHERE ts.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT td.benId FROM TB_DIAGNOSTICS td
                    WHERE td.isLiquidCultureConducted IS NOT NULL
                )
                OR benId IN (
                    SELECT tbs.benId FROM TB_SCREENING tbs
                    WHERE tbs.recommendedForLiquidCultureTest = 1
                )
            ))
            OR (:source = 9 AND isDeath = 0 AND hhId IS NOT NULL AND EXISTS (
                SELECT 1
                FROM BEN_BASIC_CACHE b2
                LEFT JOIN TB_SUSPECTED ts ON b2.benId = ts.benId
                LEFT JOIN TB_DIAGNOSTICS td ON b2.benId = td.benId
                WHERE b2.hhId = BEN_BASIC_CACHE.hhId
                  AND b2.isDeactivate = 0
                  AND b2.isDeath = 0
                  AND (
                        ts.isTbConfirmed = 1
                        OR td.isTbConfirmed = 1
                        OR UPPER(IFNULL(td.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
                        OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE'
                  )
            ))
            OR (:source = 10 AND isDeath = 0 AND benId IN (
                -- TB_SUSPECTED has no RIF field of its own (mdrRifResult is only ever mirrored
                -- here from TB_DIAGNOSTICS.trueNatRifResult by syncTBSuspectedFromDiagnostics, so
                -- checking it would be redundant with the td.trueNatRifResult check below) — the
                -- older SuspectedTBDataset form only ever writes chestXRayResult/naatResult here
                -- directly, with no TB_DIAGNOSTICS row at all, so that's the gap this union closes.
                SELECT ts.benId FROM TB_SUSPECTED ts
                WHERE UPPER(TRIM(IFNULL(ts.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                  AND UPPER(TRIM(IFNULL(ts.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
                UNION
                SELECT td.benId FROM TB_DIAGNOSTICS td
                WHERE (UPPER(TRIM(IFNULL(td.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                       AND UPPER(TRIM(IFNULL(td.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED'))
                   OR UPPER(TRIM(IFNULL(td.trueNatRifResult,''))) = 'INDETERMINATE'
            ))
        )
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR
            benName LIKE '%' || :query || '%'
            OR benSurname LIKE '%' || :query || '%'
            OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR REPLACE(IFNULL(abhaId, ''), '-', '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR IFNULL(familyHeadName, '') LIKE '%' || :query || '%'
            OR IFNULL(spouseName, '') LIKE '%' || :query || '%'
            OR IFNULL(fatherName, '') LIKE '%' || :query || '%'
            OR CAST(benId AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR CAST(hhId AS TEXT) LIKE '%' || :query || '%'
            OR IFNULL(rchId, '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
        )
       ORDER BY CASE
            WHEN isDeath = 0 AND isDeactivate = 0 THEN 0
            WHEN isDeath = 1 AND isDeactivate = 0 THEN 1
            WHEN isDeactivate = 1 THEN 2
            ELSE 3
        END ASC,
        CASE
            WHEN :source = 6 OR :source = 7 THEN COALESCE(
                (
                    SELECT MAX(visitDate) FROM (
                        SELECT visitDate FROM TB_SCREENING WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_SUSPECTED WHERE benId = BEN_BASIC_CACHE.benId
                    )
                ),
                0
            )
            WHEN :source = 8 THEN COALESCE(
                (
                    SELECT MAX(visitDate) FROM (
                        SELECT visitDate FROM TB_SCREENING WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_DIAGNOSTICS WHERE benId = BEN_BASIC_CACHE.benId
                        UNION ALL
                        SELECT visitDate FROM TB_SUSPECTED WHERE benId = BEN_BASIC_CACHE.benId
                    )
                ),
                0
            )
            ELSE COALESCE(createdDate, regDate, 0)
        END DESC,
        benId DESC
    """)
    suspend fun searchBenOnce(villageIds: List<Int>, source: Int, filterType: Int, query: String): List<BenBasicCache>

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
