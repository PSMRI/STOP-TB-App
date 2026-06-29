package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.annotations.SerializedName

data class CounsellingSyncRequest(
    @SerializedName("formCode") val formCode: String,
    @SerializedName("beneficiaryId") val beneficiaryId: Long,
    @SerializedName("officerId") val officerId: Long,
    @SerializedName("sections") val sections: List<SectionPayload>
)

data class SectionPayload(
    @SerializedName("sectionCode") val sectionCode: String,
    @SerializedName("answers") val answers: List<AnswerPayload>
)

data class AnswerPayload(
    @SerializedName("questionCode") val questionCode: String,
    @SerializedName("optionValue") val optionValue: String? = null,
    @SerializedName("optionValues") val optionValues: List<String>? = null,
    @SerializedName("answerText") val answerText: String? = null,
    @SerializedName("answerNumber") val answerNumber: Double? = null,
    @SerializedName("answerDate") val answerDate: String? = null
)

data class CounsellingBulkSubmitRequest(
    @SerializedName("formUuid") val formUuid: String,
    @SerializedName("beneficiaryId") val beneficiaryId: Long,
    @SerializedName("officerId") val officerId: Long,
    @SerializedName("sections") val sections: List<BulkSectionPayload>
)

data class BulkSectionPayload(
    @SerializedName("sectionUuid") val sectionUuid: String,
    @SerializedName("answers") val answers: List<BulkAnswerPayload>
)

data class BulkAnswerPayload(
    @SerializedName("questionUuid") val questionUuid: String,
    @SerializedName("optionValue") val optionValue: String? = null,
    @SerializedName("optionValues") val optionValues: List<String>? = null,
    @SerializedName("answerText") val answerText: String? = null,
    @SerializedName("answerNumber") val answerNumber: Double? = null,
    @SerializedName("answerDate") val answerDate: String? = null
)

data class ServerCounsellingResponseDto(
    @SerializedName("responseId") val responseId: Long,
    @SerializedName("beneficiaryId") val beneficiaryId: Long,
    @SerializedName("formId") val formId: Int,
    @SerializedName("versionId") val versionId: Int,
    @SerializedName("officerId") val officerId: Long,
    @SerializedName("status") val status: String? = null,
    @SerializedName("submittedAt") val submittedAt: String? = null,
    @SerializedName("sections") val sections: List<ServerSectionResponseDto> = emptyList()
)

data class ServerSectionResponseDto(
    @SerializedName("sectionResponseId") val sectionResponseId: Long,
    @SerializedName("sectionId") val sectionId: Int,
    @SerializedName("status") val status: String? = null,
    @SerializedName("answers") val answers: List<ServerAnswerDto> = emptyList()
)

data class ServerAnswerDto(
    @SerializedName("questionResponseId") val questionResponseId: Long,
    @SerializedName("questionId") val questionId: Int,
    @SerializedName("optionId") val optionId: Int? = null,
    @SerializedName("answerText") val answerText: String? = null
)


