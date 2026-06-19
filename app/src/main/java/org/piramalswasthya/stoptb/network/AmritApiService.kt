package org.piramalswasthya.stoptb.network

import okhttp3.ResponseBody
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.*
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingApiResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormNCDFollowUpSubmitRequest
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSubmitRequest
import org.piramalswasthya.stoptb.model.dynamicEntity.NCDFollowUpResponse
import org.piramalswasthya.stoptb.model.dynamicModel.ApiResponse
import org.piramalswasthya.stoptb.model.dynamicModel.HBNCVisitListResponse
import org.piramalswasthya.stoptb.model.dynamicModel.HBNCVisitRequest
import retrofit2.Response
import retrofit2.http.*

import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingSyncRequest
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingBulkSubmitRequest

interface AmritApiService {

    @Headers("No-Auth: true", "User-Agent: okhttp")
    @POST("common-api/user/userAuthenticate")
    suspend fun getJwtToken(@Body json: TmcAuthUserRequest): Response<ResponseBody>

    @Headers("No-Auth: true", "User-Agent: okhttp")
    @POST("common-api/user/refreshToken")
    suspend fun getRefreshToken(@Body json: TmcRefreshTokenRequest): Response<ResponseBody>

    @GET("flw-api/user/getUserDetail")
    suspend fun getUserDetailsById(
        @Query("userId") userId: Int
    ): UserNetworkResponse

    @POST("common-api/firebaseNotification/userToken")
    suspend fun saveFirebaseToken(@Body json: Map<String, Any>): Response<ResponseBody>

    @POST("tm-api/registrar/registrarBeneficaryRegistrationNew")
    suspend fun getBenIdFromBeneficiarySending(@Body beneficiaryDataSending: BeneficiaryDataSending): Response<ResponseBody>

    @POST("identity-api/rmnch/syncDataToAmrit")
    suspend fun submitRmnchDataAmrit(@Body sendingRMNCHData: SendingRMNCHData): Response<ResponseBody>

    @POST("flw-api/beneficiary/getBeneficiaryData")
    suspend fun getBeneficiaries(@Body userDetail: GetDataPaginatedRequests): Response<ResponseBody>

//    @POST("flw-api/stoptb/nurse/worklist")
//    suspend fun getNurseWorklist(@Body request: NurseWorklistRequest): Response<ResponseBody>
//
//    @POST("flw-api/stoptb/registrar/worklist")
//    suspend fun getRegistrarWorklist(@Body request: NurseWorklistRequest): Response<ResponseBody>

    @POST("common-api/beneficiaryConsent/sendConsent")
    suspend fun sendOtp(@Body sendOtpRequest: sendOtpRequest): Response<ResponseBody>

    @POST("common-api/beneficiaryConsent/resendConsent")
    suspend fun resendOtp(@Body sendOtpRequest: sendOtpRequest): Response<ResponseBody>

    @POST("common-api/beneficiaryConsent/validateConsent")
    suspend fun validateOtp(@Body validateOtp: ValidateOtpRequest): Response<ResponseBody>

    // Unused — CBAC pull not used in StopTB flow
//    @POST("flw-api/cbac/getAll")
//    suspend fun getCbacs(@Body userDetail: GetDataPaginatedRequest): Response<ResponseBody>

    @POST("/hwc-api/NCD/getByUserCbacDetails")
    suspend fun getCbacData(@Body getcbacRequest: GetCBACRequest): Response<ResponseBody>

    // Unused — referral pull not needed in StopTB
//    @POST("/hwc-api/common/getBenReferDetailsByCreatedBy")
//    suspend fun getCbacReferData(@Body getcbacRequest: GetCBACRequest): Response<ResponseBody>

    @POST("hwc-api/NCD/save/nurseData")
    suspend fun postCbacs(@Body list: CbacRequest): Response<ResponseBody>

    @POST("hwc-api/NCD/save/referDetails")
    suspend fun postRefer(@Body list: ReferralRequest): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/tbScreening/getAll")
    suspend fun getTBScreeningData(@Body request: StopTbVillageRequest): Response<ResponseBody>

    @POST("flw-api/tb/suspected/getAll")
    suspend fun getTBSuspectedData(@Body userDetail: GetDataPaginatedRequest): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/diagnostics/getAll")
    suspend fun getTBDiagnosticsData(@Body request: StopTbVillageRequest): Response<ResponseBody>

    @GET("flw-api/tb/confirmed/getAll")
    suspend fun getTBConfirmedData(): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/tbScreening/save")
    suspend fun saveTBScreeningData(@Body request: List<TBScreeningSaveRequest>): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/generalOpd/getAll")
    suspend fun getGeneralOpdData(@Body request: StopTbVillageRequest): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/generalOpd/save")
    suspend fun saveGeneralOpdData(@Body request: List<GeneralOpdSaveRequest>): Response<ResponseBody>

    @POST("flw-api/disease/kalaAzar/saveAll")
    suspend fun saveKalaAzarScreeningData(@Body kalaAzarScreenRequestDTO: KalaAzarScreeningRequestDTO): Response<ResponseBody>

    @POST("flw-api/disease/malaria/saveAll")
    suspend fun saveMalariaScreeningData(@Body malariaScreeningRequestDTO: MalariaScreeningRequestDTO): Response<ResponseBody>

    @POST("flw-api/disease/leprosy/saveAll")
    suspend fun saveLeprosyScreeningData(@Body leprosyScreeningRequestDTO: LeprosyScreeningRequestDTO): Response<ResponseBody>

    @POST("flw-api/disease/aesJe/saveAll")
    suspend fun saveAESScreeningData(@Body aesScreeningRequestDTO: AESScreeningRequestDTO): Response<ResponseBody>

    @POST("flw-api/disease/filaria/saveAll")
    suspend fun saveFilariaScreeningData(@Body filariaScreeningRequestDTO: FilariaScreeningRequestDTO): Response<ResponseBody>

    @POST("flw-api/disease/leprosy/getAll")
    suspend fun getAllLeprosyData(@Body request: GetDataPaginatedRequestForDisease): Response<ResponseBody>

    @POST("flw-api/disease/leprosy/followUp/getAll")
    suspend fun getAllLeprosyFollowUpData(@Body request: GetDataPaginatedRequestForDisease): Response<ResponseBody>

    @POST("flw-api/disease/leprosy/followUp/saveAll")
    suspend fun saveLeprosyFollowUpData(@Body request: List<LeprosyFollowUpDTO>): Response<ResponseBody>

    @POST("flw-api/disease/getAllDisease")
    suspend fun getMalariaScreeningData(@Body userDetail: GetDataPaginatedRequestForDisease): Response<ResponseBody>

    @POST("flw-api/tb/suspected/saveAll")
    suspend fun saveTBSuspectedData(@Body tbSuspectedRequestDTO: TBSuspectedRequestDTO): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/diagnostics/save")
    suspend fun saveTBDiagnosticsData(@Body request: List<TBDiagnosticsSaveRequest>): Response<ResponseBody>

    @POST("flw-api/tb/confirmed/save")
    suspend fun saveTBConfirmedData(@Body tbConfirmedRequestDTO: TBConfirmedRequestDTO): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/generalExamination/save")
    suspend fun saveGeneralExamination(@Body request: List<GeneralExaminationSaveRequest>): Response<ResponseBody>

    @POST("flw-api/stoptb/nurse/generalExamination/getAll")
    suspend fun getGeneralExaminations(@Body request: GeneralExaminationGetRequest): Response<ResponseBody>

    // Unused — no caller found in codebase
//    @POST("/hwc-api/sync/generalOPDNurseFormDataToServer")
//    suspend fun saveVitalNurseData(@Body patientVisitInfo: VitalNurseDataRequest): Response<ResponseBody>

    @POST("flw-api/follow-up/save")
    suspend fun saveMalariaConfirmedData(@Body malariaConfirmedRequestDTO: MalariaConfirmedRequestDTO): Response<ResponseBody>

    @POST("flw-api/follow-up/get")
    suspend fun getMalariaConfirmedData(@Body malariaConfirmedRequestDTO: GetDataPaginatedRequestForDisease): Response<ResponseBody>

    // Unused — BenRepo.getBeneficiaryWithId() calls getBenHealthID instead
//    @POST("identity-api/id/getByBenId")
//    suspend fun getBeneficiaryWithId(@Query("benId") benId: Long): Response<ResponseBody>

    @POST("fhir-api/healthIDWithUID/createHealthIDWithUID")
    suspend fun createHid(@Body createHealthIdRequest: CreateHealthIdRequest): Response<ResponseBody>

    @POST("fhir-api/healthID/getBenhealthID")
    suspend fun getBenHealthID(@Body getBenHealthIdRequest: GetBenHealthIdRequest): Response<ResponseBody>

    @POST("fhir-api/healthIDRecord/mapHealthIDToBeneficiary")
    suspend fun mapHealthIDToBeneficiary(@Body mapHIDtoBeneficiary: MapHIDtoBeneficiary): Response<ResponseBody>

    @POST("fhir-api/healthIDRecord/addHealthIdRecord")
    suspend fun addHealthIdRecord(@Body addHealthIdRecord: AddHealthIdRecord): Response<ResponseBody>

    @POST("fhir-api/healthIDCard/generateOTP")
    suspend fun generateOtpHealthId(@Body generateOtpHid: GenerateOtpHid): Response<ResponseBody>

    @POST("fhir-api/healthIDCard/verifyOTPAndGenerateHealthCard")
    suspend fun verifyOtpAndGenerateHealthCard(@Body validateOtpHid: ValidateOtpHid): Response<ResponseBody>

    @GET("common-api/dynamicForm/form/{formId}/fields")
    suspend fun fetchFormSchema(
        @Path("formId") formId: String,
        @Query("lang") lang: String
    ): Response<ApiResponse<FormSchemaDto>>
    

    @GET("flw-api/dynamicForm/getAllForms")
    suspend fun getAllForms(
        @Header("Authorization") authHeader: String,
    ): Response<ApiResponse<List<FormSchemaDto>>>

    @POST("flw-api/child-care/hbncVisit/saveAll")
    suspend fun submitForm(
        @Body request: List<FormSubmitRequest>
    ): Response<Unit>

    @POST("flw-api/counselling/save")
    suspend fun submitCounselling(
        @Body request: CounsellingSyncRequest
    ): Response<okhttp3.ResponseBody>

    @POST("flw-api/dynamicForm/response/submitBulk")
    suspend fun submitBulkCounselling(
        @Header("Authorization") authHeader: String,
        @Body request: List<CounsellingBulkSubmitRequest>
    ): Response<okhttp3.ResponseBody>

    @POST("flw-api/disease/cdtfVisit/saveAll")
    suspend fun submitNCDFollowUp(
        @Body request: List<FormNCDFollowUpSubmitRequest>
    ): Response<Unit>

    @POST("flw-api/disease/cdtfVisit/getAll")
    suspend fun getAllFormNCDFollowUp(
        @Body request: HBNCVisitRequest
    ): Response<NCDFollowUpResponse>

    // Unused — disease mosquito form APIs not used in StopTB flow
//    @POST("flw-api/disease/{formName}/saveAll")
//    suspend fun submitDiseaseMosquitoForm(
//        @Path("formName") formName: String,
//        @Body request: List<FormSubmitRequest>
//    ): Response<Unit>
//
//    @POST("flw-api/disease/{formName}/getAll")
//    suspend fun getAllDiseaseMosquitoFormVisits(
//        @Path("formName") formName: String,
//        @Body request: HBNCVisitRequest
//    ): Response<HBNCVisitListResponse>

}
