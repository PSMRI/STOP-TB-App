package org.piramalswasthya.stoptb.helpers.dynamicMapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import org.piramalswasthya.stoptb.utils.StringMappingUtil
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseJsonEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSubmitRequest
import org.piramalswasthya.stoptb.model.dynamicEntity.NCDReferalFormResponseJsonEntity

object FormSubmitRequestMapper {

    fun fromEntity(entity: FormResponseJsonEntity, userName: String): FormSubmitRequest? =
        mapCommon(entity.formDataJson, userName)


    fun fromEntity(entity: NCDReferalFormResponseJsonEntity, userName: String): FormSubmitRequest? =
        mapCommon(entity.formDataJson, userName)

    private fun mapCommon(formDataJson: String, userName: String): FormSubmitRequest? {
        return try {
            val jsonObj = JSONObject(formDataJson)
            val fieldsObj = jsonObj.optJSONObject("fields")
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val fieldsMap: Map<String, Any?> = Gson().fromJson(fieldsObj.toString(), type)
            val englishFieldsMap = fieldsMap.mapValues { (_, v) ->
                if (v is String) StringMappingUtil.convertDigits(v) else v
            }
            FormSubmitRequest(
                userName = userName,
                formId = jsonObj.optString("formId"),
                beneficiaryId = jsonObj.optLong("beneficiaryId"),
                houseHoldId = jsonObj.optLong("houseHoldId"),
                visitDate = StringMappingUtil.convertDigits(jsonObj.optString("visitDate")),
                fields = englishFieldsMap
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}