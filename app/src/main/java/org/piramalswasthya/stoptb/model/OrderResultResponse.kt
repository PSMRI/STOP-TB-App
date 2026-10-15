package org.piramalswasthya.stoptb.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderResultResponse(
    val data: OrderResultData = OrderResultData(),
    val errorMessage: String? = null,
    val status: String? = null,
    val statusCode: Int = 0
)

@JsonClass(generateAdapter = true)
data class OrderResultData(
    val externalOrderId: String? = null,
    val orderType: String? = null,
    val status: String? = null,
    val providerStatus: String? = null,
    val resultSummary: String? = null,
    val tbPresence: Boolean? = null,
    val drugResistancePresence: Boolean? = null,
    val errorMessage: String? = null
)
