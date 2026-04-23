package org.piramalswasthya.stoptb.network

import com.squareup.moshi.JsonClass
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.model.VitalCache

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
    val providerServiceMapID: String?,
    val pulseRate: String?,
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
                providerServiceMapID = user.serviceMapId.toString(),
                pulseRate = vital.pulseRate?.toString(),
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
