package org.piramalswasthya.stoptb.repositories.tb

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.GeneralOpdMasterConfig
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.VisitCategoryMasterCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest
import org.piramalswasthya.stoptb.repositories.BundledChiefComplaints
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * General OPD records, their prescriptions, and the chief complaint / visit category masters,
 * moved out of TBRepo unchanged. TBRepo's OPD push builds its request with
 * [prepareGeneralOpdRequest].
 */
@Singleton
class GeneralOpdRepo @Inject constructor(
    private val tbDao: TBDao,
    private val database: InAppDb,
    private val drugMasterRepo: DrugMasterRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val preferenceDao: PreferenceDao
) {

    private companion object {
        const val GENERAL_OPD_CATEGORY_FALLBACK = 6
    }

    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getGeneralOpd(benId)
        }
    }

    suspend fun getCachedChiefComplaintNames(): List<String> =
        if (GeneralOpdMasterConfig.USE_API_MASTERS) {
            database.chiefComplaintMasterDao.getChiefComplaints().map { it.chiefComplaint }
        } else {
            // The dataset supplies the canonical static resource list.
            emptyList()
        }

    suspend fun getGeneralOpdPrescriptions(opdId: Int) = withContext(Dispatchers.IO) {
        tbDao.getGeneralOpdPrescriptions(opdId)
    }

    suspend fun saveGeneralOpdWithPrescriptions(cache: GeneralOpdCache, medicines: List<OpdMedicineDraft>) =
        withContext(Dispatchers.IO) {
            val masters = drugMasterRepo.getCachedMasters()
            var diagnosticSavedId = 0
            database.withTransaction {
                val complaints = resolveChiefComplaintIds(cache)
                val opdId = tbDao.saveGeneralOpd(cache.copy(chiefComplaintIds = complaints)).toInt()
                diagnosticSavedId = opdId
                tbDao.ensureGeneralOpdSubmissionId(opdId)
                tbDao.deleteGeneralOpdPrescriptions(opdId)
                tbDao.insertGeneralOpdPrescriptions(medicines.mapIndexed { index, medicine ->
                    GeneralOpdPrescription(opdId, index, medicine.medicine, medicine.frequency,
                        medicine.durationCount, medicine.durationUnit, medicine.instruction,
                        medicine.drugId, medicine.drugName, medicine.itemFormId, medicine.drugForm).resolveDrug(masters)
                })
            }
            org.piramalswasthya.stoptb.helpers.SyncDailyStore.record("GENERAL_OPD", listOf(diagnosticSavedId), "SAVE", "SUCCESS")
        }

    internal suspend fun resolveChiefComplaintIds(cache: GeneralOpdCache): List<Int>? {
        val names = cache.chiefComplaints.orEmpty()
        cache.chiefComplaintIds?.takeIf { it.size == names.size && it.all { id -> id > 0 } }?.let { return it }
        // Preserve existing IDs. Resolve missing IDs on both local save and push retry
        // using the supplied static mapping, never the obsolete API cache.
        if (!GeneralOpdMasterConfig.USE_API_MASTERS) return BundledChiefComplaints.resolveIds(names)
        val master = database.chiefComplaintMasterDao.getChiefComplaints()
        val ids = names.map { name ->
            master.singleOrNull { it.chiefComplaint.trim().equals(name.trim(), ignoreCase = true) }
                ?.chiefComplaintId ?: return null
        }
        return ids
    }

    private fun GeneralOpdPrescription.resolveDrug(masters: OpdDrugMasters): GeneralOpdPrescription {
        if (drugId != null && drugId > 0 && !drugName.isNullOrBlank() &&
            itemFormId != null && itemFormId > 0 && !drugForm.isNullOrBlank()) return this
        val labels = masters.medicineLabels()
        val matches = masters.items.filterIndexed { index, item ->
            (if (drugId != null) drugId == item.itemId else labels[index] == medicine) &&
                (itemFormId == null || itemFormId == item.itemFormId)
        }.distinctBy { it.itemId to it.itemFormId }
        val item = matches.singleOrNull() ?: return this
        val form = masters.forms.singleOrNull { it.itemFormId == item.itemFormId }
        return copy(drugId = drugId ?: item.itemId, drugName = drugName ?: item.itemName,
            itemFormId = itemFormId ?: form?.itemFormId, drugForm = drugForm ?: form?.itemFormName)
    }

    internal suspend fun prepareGeneralOpdRequest(opdId: Int, beneficiaryRegId: Long,
                                               serviceMapId: Int, userName: String): Pair<GeneralOpdCache, GeneralOpdSaveRequest> {
        val masters = drugMasterRepo.getCachedMasters()
        return database.withTransaction {
            val saved = requireNotNull(tbDao.getGeneralOpdById(opdId))
            require(saved.syncState == SyncState.UNSYNCED)
            val snapshot = saved.copy(submissionId = tbDao.ensureGeneralOpdSubmissionId(opdId),
                chiefComplaintIds = resolveChiefComplaintIds(saved))
            val stored = tbDao.getGeneralOpdPrescriptions(opdId)
            // Old records only have a common duration. Never guess missing per-medicine data.
            val rows = stored.ifEmpty {
                val duration = Regex("^(\\d+)\\s+(day\\(s\\)|days?|week\\(s\\)|weeks?|month\\(s\\)|months?)$",
                    RegexOption.IGNORE_CASE).matchEntire(saved.duration.orEmpty().trim())
                saved.medications.orEmpty().mapIndexed { index, medicine ->
                    GeneralOpdPrescription(opdId, index, medicine, saved.frequency.orEmpty(),
                        duration?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                        duration?.groupValues?.get(2).orEmpty(), "")
                }
            }.map { it.resolveDrug(masters) }
            val request = GeneralOpdSaveRequest.from(snapshot, rows, beneficiaryRegId, serviceMapId, userName)
            tbDao.updateGeneralOpd(snapshot)
            if (rows != stored) {
                tbDao.deleteGeneralOpdPrescriptions(opdId)
                tbDao.insertGeneralOpdPrescriptions(rows)
            }
            snapshot to request
        }
    }

    suspend fun refreshVisitCategories(): Boolean {
        if (!GeneralOpdMasterConfig.USE_API_MASTERS) return false
        return try {
            val categoriesResponse = tmcNetworkApiService.getVisitReasonAndCategories()
            val categoriesBody = categoriesResponse.body()
            if (!categoriesResponse.isSuccessful || categoriesBody?.statusCode != 200) return false

            val categories = categoriesBody.data?.visitCategories.orEmpty()
            if (categories.isEmpty()) return false

            database.withTransaction {
                database.chiefComplaintMasterDao.deleteVisitCategories()
                database.chiefComplaintMasterDao.insertVisitCategories(
                    categories.map { VisitCategoryMasterCache(it.visitCategoryID, it.visitCategory) }
                )
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Visit category master refresh failed")
            false
        }
    }

    suspend fun refreshChiefComplaintMasters(): Boolean {
        if (!GeneralOpdMasterConfig.USE_API_MASTERS) return false
        val loggedInUser = preferenceDao.getLoggedInUser() ?: return false

        return try {
            val generalOpdCategoryId = database.chiefComplaintMasterDao.getGeneralOpdCategoryId()
                ?: GENERAL_OPD_CATEGORY_FALLBACK

            val complaintsResponse = tmcNetworkApiService.getChiefComplaintMaster(
                visitCategoryId = generalOpdCategoryId,
                providerServiceMapId = loggedInUser.serviceMapId
            )
            val complaintsBody = complaintsResponse.body()
            if (!complaintsResponse.isSuccessful || complaintsBody?.statusCode != 200) return false

            val complaints = complaintsBody.data?.chiefComplaintMaster.orEmpty()
            if (complaints.isEmpty()) return false

            database.withTransaction {
                database.chiefComplaintMasterDao.deleteChiefComplaints()
                database.chiefComplaintMasterDao.insertChiefComplaints(
                    complaints.map { ChiefComplaintMasterCache(it.chiefComplaintID, it.chiefComplaint) }
                )
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Chief complaint master refresh failed")
            false
        }
    }
}
