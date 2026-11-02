package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.annotations.SerializedName

// One entry of flw-api/dynamicForm/getLatestFormVersions. versionId is the backend's own id and does
// not match the local t_form_version.versionId (formId * 1000 + versionNumber) — compare versionNumber.
data class LatestFormVersionDto(
    @SerializedName("formId")
    val formId: Int,

    @SerializedName("formUuid")
    val formUuid: String,

    @SerializedName("formName")
    val formName: String? = null,

    @SerializedName("versionId")
    val versionId: Int,

    @SerializedName("currentVersionNumber")
    val currentVersionNumber: Int
)
