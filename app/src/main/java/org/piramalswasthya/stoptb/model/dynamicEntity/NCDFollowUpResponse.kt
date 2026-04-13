package org.piramalswasthya.stoptb.model.dynamicEntity

data class NCDFollowUpResponse(
    val statusCode: Int,
    val data: List<FormNCDFollowUpSubmitRequest>
)
