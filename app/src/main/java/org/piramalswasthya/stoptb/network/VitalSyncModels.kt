package org.piramalswasthya.stoptb.network

import com.squareup.moshi.JsonClass
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.model.VitalCache

@JsonClass(generateAdapter = true)
data class GeneralExaminationSaveRequest(
    val beneficiaryRegID: Long,
    val createdBy: String?,
    val providerServiceMapID: Int,
    val pulseRate: Int? = null,
    val systolicBP: Int? = null,
    val diastolicBP: Int? = null,
    val randomBloodSugar: Double? = null,
    val pallorId: Int? = null,
    val pallor: String? = null,
    val icterusId: Int? = null,
    val icterus: String? = null,
    val cyanosisId: Int? = null,
    val cyanosis: String? = null,
    val clubbingId: Int? = null,
    val clubbing: String? = null,
    val lymphadenopathyId: Int? = null,
    val lymphadenopathy: String? = null,
    val oedemaId: Int? = null,
    val oedema: String? = null,
    val keyPopulationRiskFactorIds: List<Int>? = null,
    val keyPopulationRiskFactors: List<String>? = null,
    val hivStatusId: Int? = null,
    val hivStatus: String? = null,
    val referralToHWCNeeded: Boolean? = null
) {
    companion object {
        fun from(vital: VitalCache, user: User): GeneralExaminationSaveRequest {
            return GeneralExaminationSaveRequest(
                beneficiaryRegID = vital.benRegId,
                createdBy = user.userName,
                providerServiceMapID = user.serviceMapId,
                pulseRate = vital.pulseRate,
                systolicBP = vital.bpSystolic,
                diastolicBP = vital.bpDiastolic,
                randomBloodSugar = vital.rbs,
                pallorId = vital.pallorId,
                pallor = vital.pallor,
                icterusId = vital.icterusId,
                icterus = vital.icterus,
                cyanosisId = vital.cyanosisId,
                cyanosis = vital.cyanosis,
                clubbingId = vital.clubbingId,
                clubbing = vital.clubbing,
                lymphadenopathyId = vital.lymphadenopathyId,
                lymphadenopathy = vital.lymphadenopathy,
                oedemaId = vital.oedemaId,
                oedema = vital.oedema,
                keyPopulationRiskFactorIds = vital.keyPopulationRiskFactorIds,
                keyPopulationRiskFactors = vital.keyPopulationRiskFactors,
                hivStatusId = vital.hivStatusId,
                hivStatus = vital.hivStatus,
                referralToHWCNeeded = vital.referralToHwcNeeded
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class GeneralExaminationGetRequest(
    val providerServiceMapID: Int,
    val villageID: Int
)
//
//@JsonClass(generateAdapter = true)
//data class GeneralExaminationResponse(
//    val statusCode: Int,
//    val status: String? = null,
//    val message: String? = null,
//    val data: List<GeneralExaminationRecord>? = null
//)
//
//@JsonClass(generateAdapter = true)
//data class GeneralExaminationRecord(
//    val id: Long? = null,
//    val beneficiaryID: Long,
//    val beneficiaryRegID: Long,
//    val createdBy: String? = null,
//    val providerServiceMapID: Int? = null,
//    val pulseRate: Int? = null,
//    val systolicBP: Int? = null,
//    val diastolicBP: Int? = null,
//    val randomBloodSugar: Double? = null,
//    val pallorId: Int? = null,
//    val pallor: String? = null,
//    val icterusId: Int? = null,
//    val icterus: String? = null,
//    val cyanosisId: Int? = null,
//    val cyanosis: String? = null,
//    val clubbingId: Int? = null,
//    val clubbing: String? = null,
//    val lymphadenopathyId: Int? = null,
//    val lymphadenopathy: String? = null,
//    val oedemaId: Int? = null,
//    val oedema: String? = null,
//    val keyPopulationRiskFactorIds: List<Int>? = null,
//    val keyPopulationRiskFactors: List<String>? = null,
//    val hivStatusId: Int? = null,
//    val hivStatus: String? = null,
//    val createdDate: String? = null
//)

@JsonClass(generateAdapter = true)
data class GeneralExaminationResponse(
    val statusCode: Int,
    val status: String? = null,
    val message: String? = null,
    val data: GeneralExaminationData? = null
)

@JsonClass(generateAdapter = true)
data class GeneralExaminationData(
    val count: Int? = null,
    val data: List<GeneralExaminationRecord>? = null
)

@JsonClass(generateAdapter = true)
data class GeneralExaminationRecord(
    val id: Long? = null,

    // nullable rakha hai safety ke liye
    val beneficiaryID: Long? = null,
    val beneficiaryRegID: Long? = null,

    val createdBy: String? = null,
    val providerServiceMapID: Int? = null,

    val pulseRate: Int? = null,
    val systolicBP: Int? = null,
    val diastolicBP: Int? = null,
    val randomBloodSugar: Double? = null,

    val pallorId: Int? = null,
    val pallor: String? = null,

    val icterusId: Int? = null,
    val icterus: String? = null,

    val cyanosisId: Int? = null,
    val cyanosis: String? = null,

    val clubbingId: Int? = null,
    val clubbing: String? = null,

    val lymphadenopathyId: Int? = null,
    val lymphadenopathy: String? = null,

    val oedemaId: Int? = null,
    val oedema: String? = null,

    // backend string bhej raha hai
    val keyPopulationRiskFactorIds: String? = null,
    val keyPopulationRiskFactors: String? = null,

    val hivStatusId: Int? = null,
    val hivStatus: String? = null,
    val referralToHWCNeeded: Boolean? = null,

    val createdDate: String? = null
)

@JsonClass(generateAdapter = true)
data class VitalNurseDataRequest(
    val benFlowID: String? = null,
    val beneficiaryID: String?,
    val beneficiaryRegID: String?,
    val createdBy: String?,
    val examinationDetails: Any? = null,
    val historyDetails: Any? = null,
    val parkingPlaceID: Int? = null,
    val providerServiceMapID: String?,
    val serviceID: String? = null,
    val sessionID: String? = "3",
    val tcRequest: String? = null,
    val vanID: Int?,
    val visitDetails: Any? = null,
    val vitalDetails: VitalDetailsRequest?,
) {
    companion object {
        fun from(vital: VitalCache, user: User): VitalNurseDataRequest {
            val benIdStr = vital.benId.toString()
            val benRegIdStr = vital.benRegId.toString()
            return VitalNurseDataRequest(
                beneficiaryID = benIdStr,
                beneficiaryRegID = benRegIdStr,
                createdBy = user.userName,
                providerServiceMapID = user.serviceMapId.toString(),
                vanID = user.vanId,
                vitalDetails = VitalDetailsRequest.from(vital, user)
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class VitalDetailsRequest(
    val bMI: Double?,
    val benVisitID: String? = null,
    val beneficiaryRegID: String?,
    val bloodGlucose_2hr_PP: String? = null,
    val bloodGlucose_Fasting: String? = null,
    val bloodGlucose_Random: String? = null,
    val coughAtNightChecked: String? = null,
    val createdBy: String?,
    val diastolicBP_1stReading: String?,
    val frequentCoughChecked: String? = null,
    val headCircumference_cm: String? = null,
    val height_cm: String?,
    val hemoglobin: String? = null,
    val hipCircumference_cm: String? = null,
    val midUpperArmCircumference_MUAC_cm: String? = null,
    val painInChestChecked: String? = null,
    val parkingPlaceID: Int? = null,
    val pallorId: Int? = null,
    val pallor: String? = null,
    val providerServiceMapID: String?,
    val pulseRate: String?,
    val icterusId: Int? = null,
    val icterus: String? = null,
    val cyanosisId: Int? = null,
    val cyanosis: String? = null,
    val clubbingId: Int? = null,
    val clubbing: String? = null,
    val lymphadenopathyId: Int? = null,
    val lymphadenopathy: String? = null,
    val oedemaId: Int? = null,
    val oedema: String? = null,
    val keyPopulationRiskFactorIds: List<Int>? = null,
    val keyPopulationRiskFactors: List<String>? = null,
    val hivStatusId: Int? = null,
    val hivStatus: String? = null,
    val referralToHwcNeeded: Boolean? = null,
    val referralTriggers: List<String>? = null,
    val rbsCheckBox: Boolean?,
    val rbsTestRemarks: String? = null,
    val rbsTestResult: String?,
    val respiratoryRate: String?,
    val sPO2: String?,
    val shortnessOfBreathChecked: String? = null,
    val sputumChecked: String? = null,
    val systolicBP_1stReading: String?,
    val temperature: String?,
    val vanID: Int?,
    val waistCircumference_cm: String? = null,
    val waistHipRatio: String? = null,
    val weight_Kg: String?,
    val wheezingChecked: String? = null,
) {
    companion object {
        fun from(vital: VitalCache, user: User): VitalDetailsRequest {
            return VitalDetailsRequest(
                bMI = vital.bmi,
                beneficiaryRegID = vital.benRegId.toString(),
                createdBy = user.userName,
                diastolicBP_1stReading = vital.bpDiastolic?.toString(),
                height_cm = vital.height?.toString(),
                pallorId = vital.pallorId,
                pallor = vital.pallor,
                providerServiceMapID = user.serviceMapId.toString(),
                pulseRate = vital.pulseRate?.toString(),
                icterusId = vital.icterusId,
                icterus = vital.icterus,
                cyanosisId = vital.cyanosisId,
                cyanosis = vital.cyanosis,
                clubbingId = vital.clubbingId,
                clubbing = vital.clubbing,
                lymphadenopathyId = vital.lymphadenopathyId,
                lymphadenopathy = vital.lymphadenopathy,
                oedemaId = vital.oedemaId,
                oedema = vital.oedema,
                keyPopulationRiskFactorIds = vital.keyPopulationRiskFactorIds,
                keyPopulationRiskFactors = vital.keyPopulationRiskFactors,
                hivStatusId = vital.hivStatusId,
                hivStatus = vital.hivStatus,
                referralToHwcNeeded = vital.referralToHwcNeeded,
                referralTriggers = vital.referralTriggers,
                rbsCheckBox = vital.rbs != null,
                rbsTestResult = vital.rbs?.toString(),
                respiratoryRate = vital.respiratoryRate?.toString(),
                sPO2 = vital.spo2?.toString(),
                systolicBP_1stReading = vital.bpSystolic?.toString(),
                temperature = vital.temperature?.toString(),
                vanID = user.vanId,
                weight_Kg = vital.weight?.toString()
            )
        }
    }
}
