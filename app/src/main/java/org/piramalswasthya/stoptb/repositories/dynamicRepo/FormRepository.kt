package org.piramalswasthya.stoptb.repositories.dynamicRepo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.dynamicMapper.FormSubmitRequestMapper
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseJsonEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaEntity
import org.piramalswasthya.stoptb.model.dynamicModel.HBNCVisitResponse
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.utils.dynamicFormConstants.FormConstants
import org.piramalswasthya.stoptb.utils.dynamicFormConstants.FormConstants.HBNC_FORM_ID
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FormRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceDao: PreferenceDao,
    @Named("gsonAmritApi") private val amritApiService: AmritApiService,
    private val db: InAppDb
) {
    private val formSchemaDao = db.formSchemaDao()
    private val jsonResponseDao = db.formResponseJsonDao()

    val ALL_FORM_IDS = listOf(
        FormConstants.HBNC_FORM_ID,
        FormConstants.CDTF_001,
    )

    suspend fun downloadAllFormsSchemas(lang: String) = withContext(Dispatchers.IO) {
        ALL_FORM_IDS.forEach { formId ->
            try {
                val response = amritApiService.fetchFormSchema(formId, lang)
                if (response.isSuccessful) {
                    val apiSchema = response.body()?.data ?: return@forEach
                    val local = getSavedSchema(formId)
                    if (local == null || local.version < apiSchema.version || local.language != lang) {
                        saveFormSchemaToDb(apiSchema, lang)
                        Log.d("FORM_SYNC", "Updated schema → $formId")
                    } else {
                        Log.d("FORM_SYNC", "Already latest → $formId")
                    }
                } else {
                    Log.e("FORM_SYNC", "Server error → $formId")
                }
            } catch (e: Exception) {
                Log.e("FORM_SYNC", "Exception → $formId", e)
            }
        }
    }

    suspend fun getFormSchema(formId: String, lang: String): FormSchemaDto? = withContext(Dispatchers.IO) {
        try {
            val response = amritApiService.fetchFormSchema(formId, lang)
            if (response.isSuccessful) {
                val apiSchema = response.body()?.data
                apiSchema?.let {
                    val localSchema = getSavedSchema(it.formId)
                    if (localSchema == null || localSchema.version < it.version || localSchema.language != lang) {
                        saveFormSchemaToDb(it, lang)
                    }
                    return@withContext it
                }
            }
        } catch (e: Exception) { }
        return@withContext formSchemaDao.getSchema(formId)?.let { FormSchemaDto.fromJson(it.schemaJson) }
    }

    suspend fun saveFormSchemaToDb(schema: FormSchemaDto, lang: String) {
        formSchemaDao.insertOrUpdate(FormSchemaEntity(
            formId = schema.formId,
            formName = schema.formName,
            language = lang,
            version = schema.version,
            schemaJson = schema.toJson()
        ))
    }

    suspend fun getSavedSchema(formId: String) = formSchemaDao.getSchema(formId)

    suspend fun getInfantByRchId(benId: Long) = jsonResponseDao.getSyncedVisitsByRchId(benId)

    suspend fun getSyncedVisitsByRchId(benId: Long): List<FormResponseJsonEntity> =
        jsonResponseDao.getSyncedVisitsByRchId(benId)

    suspend fun insertOrUpdateFormResponse(entity: FormResponseJsonEntity) {
        val existing = jsonResponseDao.getFormResponse(entity.benId, entity.visitDay)
        val updated = existing?.let { entity.copy(id = it.id) } ?: entity
        jsonResponseDao.insertFormResponse(updated)
    }

    suspend fun insertFormResponse(entity: FormResponseJsonEntity) =
        jsonResponseDao.insertFormResponse(entity)

    suspend fun loadFormResponseJson(benId: Long, visitDay: String): String? =
        jsonResponseDao.getFormResponse(benId, visitDay)?.formDataJson

    suspend fun getUnsyncedForms(): List<FormResponseJsonEntity> =
        jsonResponseDao.getUnsyncedForms()

    suspend fun syncFormToServer(form: FormResponseJsonEntity): Boolean {
        return try {
            val request = FormSubmitRequestMapper.fromEntity(form, preferenceDao.getLoggedInUser()!!.userName) ?: return false
            val response = amritApiService.submitForm(listOf(request))
            response.isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun markFormAsSynced(id: Int) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        jsonResponseDao.markAsSynced(id, timestamp)
    }

//    suspend fun getAllHbncVisits(request: HBNCVisitRequest): Response<HBNCVisitListResponse> =
//        amritApiService.getAllHbncVisits(request)

    suspend fun saveDownloadedVisitList(list: List<HBNCVisitResponse>) {
        for (item in list) {
            try {
                if (item.fields == null) continue
                val visitDay = item.fields["visit_day"]?.asString ?: continue
                val visitDate = item.visitDate ?: "-"
                val benId = item.beneficiaryId
                val hhId = item.houseHoldId

                val fieldsJson = JSONObject()
                item.fields.entrySet().forEach { (key, jsonElement) ->
                    val value = if (jsonElement.isJsonNull) JSONObject.NULL else jsonElement.asString
                    fieldsJson.put(key, value)
                }

                val fullJson = JSONObject().apply {
                    put("formId", HBNC_FORM_ID)
                    put("beneficiaryId", benId)
                    put("houseHoldId", hhId)
                    put("visitDate", visitDate)
                    put("fields", fieldsJson)
                }

                insertOrUpdateFormResponse(FormResponseJsonEntity(
                    benId = benId,
                    hhId = hhId,
                    visitDay = visitDay,
                    visitDate = visitDate,
                    formId = HBNC_FORM_ID,
                    version = 1,
                    formDataJson = fullJson.toString(),
                    isSynced = true
                ))
            } catch (e: Exception) {
                Timber.e(e, "Failed to save HBNC visit")
            }
        }
    }
}