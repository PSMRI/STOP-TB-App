package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.SyncChangesRequest
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls through the hub's changes feed (flw-api/stoptb/sync/changes) instead of the per-dataset
 * getAll endpoints. The first call downloads everything in scope in pages; later calls return only
 * what changed since the saved token, so a pull with nothing new is one small request.
 *
 * Records arrive in the same JSON each getAll endpoint returns and are saved through the same repo
 * functions, so local edits are protected exactly as in the full pull. The token is saved only after
 * a page has been applied: if the app is killed halfway, the next pull re-reads that page.
 *
 * Any failure while saving a page throws, so the token isn't saved past unsaved records and the worker
 * falls back to the full pull. Records whose beneficiary isn't on the tablet are skipped, as in the
 * full pull; the feed sends a beneficiary before its records within a page, and a snapshot sends all
 * beneficiaries first.
 *
 * Form responses and diagnostic orders are not taken from the feed yet; PullTBFromAmritWorker still
 * pulls them through their own endpoints.
 */
@Singleton
class ChangesFeedRepo @Inject constructor(
    private val api: AmritApiService,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val benRepo: BenRepo,
    private val tbRepo: TBRepo,
    private val vitalRepo: VitalRepo,
) {

    enum class Outcome {
        /** Everything up to the hub's current state is applied. */
        DONE,
        /** This server has no changes feed: use the getAll endpoints. */
        UNSUPPORTED,
    }

    companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES_PER_RUN = 500

        // Applied in this order within a page, so a beneficiary is saved before its TB records.
        val APPLIED_TYPES = listOf(
            "beneficiary", "tbScreening", "generalExamination", "generalOpd", "generalOpdLegacy",
            "diagnostics", "tbSuspected", "tbConfirmed"
        )
    }

    /**
     * Pulls every page until the hub has nothing more. Throws IOException on network errors (the
     * worker retries) and IllegalStateException when the server rejects the request.
     */
    suspend fun pull(): Outcome = withContext(Dispatchers.IO) {
        val user = preferenceDao.getLoggedInUser() ?: throw IllegalStateException("No user logged in")
        val villageId = preferenceDao.getLocationRecord()?.village?.id
            ?: throw IllegalStateException("No village selected")
        val villages = listOf(villageId)
        val scope = "${user.serviceMapId}|${villages.sorted().joinToString(",")}|${APPLIED_TYPES.joinToString(",")}"

        var token = preferenceDao.getChangesFeedToken(scope)
        var refreshedToken = false
        var pages = 0
        while (true) {
            val response = api.getSyncChanges(
                SyncChangesRequest(token, user.serviceMapId, villages, APPLIED_TYPES, PAGE_SIZE)
            )
            if (!response.isSuccessful) {
                when (val code = response.code()) {
                    // The server doesn't have the feed yet: use the full pull.
                    404, 405, 501 -> return@withContext Outcome.UNSUPPORTED
                    // Hub busy or unreachable through a proxy: retry later.
                    408, 429, 502, 503, 504 -> throw IOException("Changes feed HTTP $code")
                    // Any other server error: the worker falls back to the full pull.
                    else -> throw IllegalStateException("Changes feed HTTP $code")
                }
            }
            val body = JSONObject(response.body()?.string() ?: throw IOException("Changes feed: empty body"))
            when (val status = body.optInt("statusCode")) {
                200 -> Unit
                401, 5002 -> {
                    if (!refreshedToken && userRepo.refreshTokenTmc(user.userName, user.password)) {
                        refreshedToken = true
                        continue
                    }
                    throw IllegalStateException("Changes feed: not authorised ($status)")
                }
                else -> throw IllegalStateException(
                    "Changes feed failed: $status ${body.optString("errorMessage")}"
                )
            }
            val data = when (val d = body.opt("data")) {
                is JSONObject -> d
                is String -> JSONObject(d)
                else -> throw IllegalStateException("Changes feed: no data")
            }
            if (!data.optBoolean("supported", false)) return@withContext Outcome.UNSUPPORTED

            val items = data.optJSONArray("items") ?: JSONArray()
            applyPage(items)
            token = data.getString("nextToken")
            preferenceDao.setChangesFeedToken(scope, token)
            pages++
            Timber.d(
                "Changes feed page $pages: ${items.length()} items, snapshot=${data.optBoolean("snapshot")}, " +
                        "hasMore=${data.optBoolean("hasMore")}"
            )
            if (!data.optBoolean("hasMore")) return@withContext Outcome.DONE
            if (pages >= MAX_PAGES_PER_RUN) {
                // Keep each run bounded; the next pull continues from the saved token.
                Timber.w("Changes feed: stopping after $pages pages, continuing next pull")
                return@withContext Outcome.DONE
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Outcome.DONE
    }

    private suspend fun applyPage(items: JSONArray) {
        val upserts = linkedMapOf<String, JSONArray>()
        var deletes = 0
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val type = item.optString("type")
            if (item.optString("op") == "delete") {
                // Removing records on the tablet is not decided yet; the next full download after
                // re-login leaves them out.
                deletes++
                continue
            }
            val record = item.optJSONObject("data") ?: continue
            upserts.getOrPut(type) { JSONArray() }.put(record)
        }
        if (deletes > 0) Timber.i("Changes feed: $deletes deleted records not removed locally")

        for (type in APPLIED_TYPES) {
            val records = upserts[type] ?: continue
            try {
                when (type) {
                    "beneficiary" -> benRepo.applyFeedBeneficiaries(records)
                    "tbScreening" -> tbRepo.applyFeedTbScreenings(records)
                    "generalExamination" -> vitalRepo.applyFeedGeneralExaminations(records)
                    "generalOpd", "generalOpdLegacy" -> tbRepo.applyFeedGeneralOpd(records)
                    "diagnostics" -> tbRepo.applyFeedDiagnostics(records)
                    "tbSuspected" -> tbRepo.applyFeedTbSuspected(records)
                    "tbConfirmed" -> tbRepo.applyFeedTbConfirmed(records)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't move the token past records that weren't saved.
                throw IllegalStateException("Changes feed: could not save $type records", e)
            }
        }
    }
}
