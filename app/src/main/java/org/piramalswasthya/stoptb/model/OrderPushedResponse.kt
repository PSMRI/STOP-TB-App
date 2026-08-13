package org.piramalswasthya.stoptb.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderPushedResponse(
    val data: OrderPushedData = OrderPushedData(),
    val errorMessage: String? = null,
    val status: String? = null,
    val statusCode: Int = 0
)

@JsonClass(generateAdapter = true)
data class OrderPushedData(
    val beneficiaryId: Long? = null,
    val createdDate: String? = null,
    val deleted: Boolean? = null,
    val externalOrderId: String? = null,
    val id: Int? = null,
    val lastModDate: String? = null,
    val orderEvent: String? = null,
    val orderType: String? = null,
    val parkingPlaceID: Int? = null,
    val patientDateOfBirth: String? = null,
    val patientFirstName: String? = null,
    val patientLastName: String? = null,
    val patientSex: String? = null,
    val processed: String? = null,
    val providerCode: String? = null,
    val providerOrderId: String? = null,
    val providerServiceName: String? = null,
    val pushResponseJson: String? = null,
    val retryCount: Int? = null,
    val status: String? = null,
    val visitCode: Int? = null
)