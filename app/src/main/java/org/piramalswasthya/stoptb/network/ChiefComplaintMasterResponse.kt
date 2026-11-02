package org.piramalswasthya.stoptb.network

data class HwcMasterResponse<T>(
    val data: T? = null,
    val statusCode: Int? = null,
    val errorMessage: String? = null,
    val status: String? = null
)

data class VisitReasonAndCategoriesResponse(
    val visitCategories: List<VisitCategoryNetwork> = emptyList()
)

data class VisitCategoryNetwork(
    val visitCategoryID: Int,
    val visitCategory: String
)

data class NurseMasterDataResponse(
    val chiefComplaintMaster: List<ChiefComplaintNetwork> = emptyList()
)

data class ChiefComplaintNetwork(
    val chiefComplaintID: Int,
    val chiefComplaint: String
)
