package org.piramalswasthya.stoptb.network

import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingApiResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicModel.ApiResponse
import retrofit2.http.GET

typealias CounsellingAllFormsApiResponse = ApiResponse<List<FormSchemaDto>>

//interface TestingApiService {
//    @GET("flw-api/dynamicForm/getDefinition?formId=12")
//    suspend fun getFormSchema() : CounsellingApiResponse
//
//    @GET("flw-api/dynamicForm/getAllForms")
//    suspend fun getAllForms() : CounsellingAllFormsApiResponse
//}