package org.piramalswasthya.stoptb.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.ReferralCardCounts
import org.piramalswasthya.stoptb.database.room.dao.TbCardCounts
import org.piramalswasthya.stoptb.database.room.dao.VillageBenCounts
import org.piramalswasthya.stoptb.di.ApplicationScope
import org.piramalswasthya.stoptb.utils.throttledQuery
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Card counts for the home, Tuberculosis and Referrals screens and the home glance. Each group is
 * one query, shared by every screen that shows it, re-run at most once a second after writes.
 * The last counts are kept on disk and shown at once on the next start, then replaced by the
 * fresh count (at 1M each group is a full pass over the village: several seconds on a tablet).
 */
@Singleton
class VillageCountsRepo @Inject constructor(
    @ApplicationContext context: Context,
    private val database: InAppDb,
    private val benDao: BenDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    // Aggregate numbers only, no personal data.
    private val lastCounts = context.getSharedPreferences("village_counts", Context.MODE_PRIVATE)
    private val shared = ConcurrentHashMap<String, Flow<*>>()

    fun counts(villageId: Int): Flow<VillageBenCounts> =
        cachedCounts("$villageId", BEN_TABLES, { benDao.villageBenCounts(villageId) },
            { listOf(it.allBen, it.nonHH, it.population, it.unscreened) },
            { VillageBenCounts(it[0], it[1], it[2], it[3]) })

    /** Presumptive TB count of [villageIds] for the home glance. */
    fun presumptive(villageIds: List<Int>): Flow<Int> =
        cachedCounts("presumptive:" + villageIds.joinToString(","), arrayOf("BENEFICIARY", "TB_BEN_SUMMARY"),
            { benDao.villagePresumptiveCount(villageIds) }, { listOf(it) }, { it[0] })

    fun tbCards(villageId: Int): Flow<TbCardCounts> =
        cachedCounts("tb:$villageId", TB_TABLES, { benDao.villageTbCardCounts(villageId) },
            { listOf(it.screened, it.presumptive, it.confirmed) },
            { TbCardCounts(it[0], it[1], it[2]) })

    fun referralCards(villageId: Int): Flow<ReferralCardCounts> =
        cachedCounts("referral:$villageId", REFERRAL_TABLES, { benDao.villageReferralCardCounts(villageId) },
            { listOf(it.xray, it.trueNat, it.hwc, it.liquidCulture, it.clinical) },
            { ReferralCardCounts(it[0], it[1], it[2], it[3], it[4]) })

    @Suppress("UNCHECKED_CAST")
    private fun <T> cachedCounts(
        key: String,
        tables: Array<String>,
        query: suspend () -> T,
        encode: (T) -> List<Int>,
        decode: (List<Int>) -> T,
    ): Flow<T> = shared.getOrPut(key) {
        flow {
            val size = encode(decode(List(MAX_FIELDS) { 0 })).size
            lastCounts.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == size }
                ?.let { emit(decode(it)) }
            database.throttledQuery(tables, PERIOD_MS, query).collect { value ->
                lastCounts.edit().putString(key, encode(value).joinToString(",")).apply()
                emit(value)
            }
        }.shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    } as Flow<T>

    private companion object {
        const val PERIOD_MS = 1_000L
        const val MAX_FIELDS = 8
        val BEN_TABLES = arrayOf("BENEFICIARY", "HOUSEHOLD", "TB_SCREENING")
        val TB_TABLES = arrayOf("BENEFICIARY", "HOUSEHOLD", "TB_BEN_SUMMARY", "TB_SUSPECTED", "TB_DIAGNOSTICS")
        val REFERRAL_TABLES = arrayOf("BENEFICIARY", "HOUSEHOLD", "TB_BEN_SUMMARY")
    }
}
