package org.piramalswasthya.stoptb.model

import android.content.Context
import androidx.room.ColumnInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.utils.toGpsTimestampLong
import org.piramalswasthya.stoptb.model.Gender.FEMALE
import org.piramalswasthya.stoptb.model.Gender.MALE
import org.piramalswasthya.stoptb.model.Gender.TRANSGENDER
import org.piramalswasthya.stoptb.model.Gender.PREFER_NOT_TO_SAY

@JsonClass(generateAdapter = true)
data class BeneficiaryDataSending(
    @Json(name = "ageAtMarriage")
    val ageAtMarriage: Int?,

    @Json(name = "benImage")
    val benImage: String,

    @Json(name = "isDeath")
    val isDeath: Boolean,

    @Json(name = "isDeathValue")
    val isDeathValue: String,

    @Json(name = "dateOfDeath")
    val dateOfDeath: String,

    @Json(name = "timeOfDeath")
    val timeOfDeath: String,

    @Json(name = "reasonOfDeath")
    val reasonOfDeath: String,

    @Json(name = "reasonOfDeathId")
    val reasonOfDeathId: Int,

    @Json(name = "placeOfDeath")
    val placeOfDeath: String,

    @Json(name = "placeOfDeathId")
    val placeOfDeathId: Int,

    @Json(name = "otherPlaceOfDeath")
    val otherPlaceOfDeath: String,


    @Json(name = "benPhoneMaps")
    val benPhoneMaps: Array<BenPhoneMaps>,

    @Json(name = "beneficiaryIdentities")
    val beneficiaryIdentities: Array<BeneficiaryIdentities>,

    @Json(name = "createdBy")
    val createdBy: String,

    @Json(name = "dOB")
    val dob: String,

    @Json(name = "dob")
    val dobLowerCase: String,

    @Json(name = "age")
    val age: Int,

    @Json(name = "ageUnits")
    val ageUnits: String,

    @Json(name = "email")
    val email: String,

    @Json(name = "emergencyRegistration")
    val isEmergencyRegistration: Boolean = false,

    @Json(name = "fatherName")
    val fatherName: String,

    @Json(name = "firstName")
    val firstName: String,


    @Json(name = "genderID")
    val genderID: Int = 0,

    @Json(name = "genderName")
    val genderName: String,

    @Json(name = "govtIdentityNo")
    val govtIdentityNo: String?,

    @Json(name = "govtIdentityTypeID")
    val govtIdentityTypeID: String?,

    @Json(name = "i_bendemographics")
    val benDemographics: BenDemographics,

    @Json(name = "lastName")
    val lastName: String,


    @ColumnInfo(name = "marriageDate")
    val marriageDate: String?,

    @Json(name = "spouseName")
    val spouseName: String?,

    @Json(name = "titleId")
    val titleId: String?,


//    @Json(name = "parkingPlaceID")
//    val parkingPlaceID: Int = 0,

    @Json(name = "bankName")
    val bankName: String? = null,
//
    @Json(name = "providerServiceMapID")
    val providerServiceMapID: Int,

    @Json(name = "maritalStatusID")
    val maritalStatusID: String? = null,

    @Json(name = "vanID")
    val vanID: Int = 0,


    @Json(name = "accountNo")
    val accountNo: String? = null,


    @Json(name = "ifscCode")
    val ifscCode: String? = null,


    @Json(name = "motherName")
    val motherName: String,

    @Json(name = "facilitySelection")
    val facilitySelection: String? = null,

    @Json(name = "branchName")
    val branchName: String? = null,

    @Json(name = "providerServiceMapId")
    val providerServiceMapId: Int,

    @Json(name = "visitCategory")
    val visitCategory: String = "Stop TB",


    @Json(name = "maritalStatusName")
    val maritalStatusName: String? = null,


    @Json(name = "beneficiaryConsent")
    val beneficiaryConsent: Boolean = false,

    @Json(name = "personFrom")
    val personFrom: String? = null,

    @Json(name = "personFromId")
    val personFromId: Int? = null,

    @Json(name = "caseFindingType")
    val typeOfCaseFinding: String? = null,

    @Json(name = "typeOfCaseFindingId")
    val typeOfCaseFindingId: Int? = null,

    @Json(name = "isMobileAvailable")
    val mobileNumberAvailable: Boolean? = null,

    @Json(name = "tuId")
    val tuId: Int? = null,

    @Json(name = "tuName")
    val tuName: String? = null,

    @Json(name = "healthFacilityId")
    val healthFacilityId: Int? = null,

    @Json(name = "healthFacilityName")
    val healthFacilityName: String? = null,

    @Json(name = "address")
    val address: String? = null,

    @Json(name = "height")
    val height: Double? = null,

    @Json(name = "weight")
    val weight: Double? = null,

    @Json(name = "bmi")
    val bmi: Double? = null,
    @Json(name = "temperatureValue")
    val temperature: Double? = null,

    )

data class BenDemographics(
    @Json(name = "addressLine1")
    var addressLine1: String,
    @Json(name = "addressLine2")
    var addressLine2: String,
    @Json(name = "addressLine3")
    var addressLine3: String,
    @Json(name = "blockID")
    var blockID: Int,

    @Json(name = "tuId")
    var tuId: Int? = null,

    @Json(name = "tuName")
    var tuName: String? = null,

    @Json(name = "healthFacilityId")
    var healthFacilityId: Int? = null,

    @Json(name = "healthFacilityName")
    var healthFacilityName: String? = null,

    @Json(name = "communityID")
    var communityID: Int?,
    @Json(name = "communityName")
    var communityName: String,

    @Json(name = "countryID")
    var countryID: Int,
    @Json(name = "countryName")
    var countryName: String,
    @Json(name = "districtBranchID")
    var districtBranchID: Int,
    @Json(name = "districtBranchName")
    var districtBranchName: String,


    @Json(name = "districtID")
    var districtID: Int,
//    @Json(name = "parkingPlaceID")
//    var parkingPlaceID: Int,
//    @Json(name = "parkingPlaceName")
//    var parkingPlaceName: String,

    @Json(name = "religionID")
    var religionID: Int?,

    @Json(name = "religionName")
    var religionName: String,

//    @Json(name = "servicePointID")
//    var servicePointID: String,
//    @Json(name = "servicePointName")
//    var servicePointName: String,

    @Json(name = "stateID")
    var stateID: Int,

    @Json(name = "stateName")
    var stateName: String,

//    @Json(name = "zoneID")
//    var zoneID: Int = 0,
//
//    @Json(name = "zoneName")
//    var zoneName: String,

//Nullable Fields, I think...
    @Json(name = "incomeStatusName")
    var incomeStatusName: String? = null,
    @Json(name = "blockName")
    var blockName: String? = null,
    @Json(name = "occupationName")
    var occupationName: String? = null,
    @Json(name = "incomeStatusID")
    var incomeStatusID: String? = null,
    @Json(name = "educationName")
    var educationName: String? = null,
    @Json(name = "districtName")
    var districtName: String? = null,
    @Json(name = "habitation")
    var habitation: String? = null,
    @Json(name = "educationID")
    var educationID: String? = null,
    @Json(name = "occupationID")
    var occupationID: String? = null,
    @Json(name = "pinCode")
    var pinCode: String? = null,

    @Json(name = "address")
    var address: String? = null,

    @Json(name = "occupation")
    var occupation: String? = null,

    @Json(name = "economicStatus")
    var economicStatus: String? = null,

    @Json(name = "economicStatusId")
    var economicStatusId: Int? = null,

    @Json(name = "residentialArea")
    var residentialArea: String? = null,

    @Json(name = "residentialAreaId")
    var residentialAreaId: Int? = null,

    @Json(name = "otherResidentialArea")
    var otherResidentialArea: String? = null,

    @Json(name = "latitude")
    var latitude: Double? = null,

    @Json(name = "longitude")
    var longitude: Double? = null,

    @Json(name = "digipin")
    var digipin: String? = null,

    @Json(name = "gpsTimestamp")
    var gpsTimestamp: Long? = null,

    @Json(name = "isGpsUnavailable")
    var isGpsUnavailable: Boolean? = null,

    @Json(name = "gpsUnavailableReason")
    var gpsUnavailableReason: String? = null,

    @Json(name = "createdBy")
    var createdBy: String? = null,

    )


data class BeneficiaryIdentities(
    @Json(name = "govtIdentityNo")
    var govtIdentityNo: Int = 0,

    @Json(name = "govtIdentityTypeID")
    var govtIdentityTypeID: Int = 0,

    @Json(name = "govtIdentityTypeName")
    var govtIdentityTypeName: String? = null,

    @Json(name = "identityType")
    var identityType: String,

    @Json(name = "createdBy")
    var createdBy: String,
)


data class BenPhoneMaps(

    @Json(name = "createdBy")
    var createdBy: String,

    @Json(name = "phoneNo")
    var phoneNo: String,

    @Json(name = "phoneTypeID")
    var phoneTypeID: Int = 1

)

fun BenRegCache.asNetworkSendingModel(
    user: User,
    locationRecord: LocationRecord,
    context: Context,
    household: HouseholdCache? = null
): BeneficiaryDataSending {
    val isKid = (ageUnit != null && (ageUnit != AgeUnit.YEARS || age < 15))

    return BeneficiaryDataSending(

        isDeath=isDeath,
        isDeathValue=isDeathValue?: "",
        dateOfDeath=dateOfDeath?: "",
        timeOfDeath=timeOfDeath?: "",
        reasonOfDeath=reasonOfDeath?: "",
        reasonOfDeathId=reasonOfDeathId?: 0,
        placeOfDeath=placeOfDeath?: "",
        placeOfDeathId=placeOfDeathId?: 0,
        otherPlaceOfDeath=otherPlaceOfDeath?: "",

        benImage = ImageUtils.getEncodedStringForBenImage(context, beneficiaryId)
            ?: "", //Base64.encodeToString(userImageBlob, Base64.DEFAULT),
        firstName = firstName!!,
        lastName = lastName ?: "",
        dob = getDateTimeStringFromLong(dob) ?: "",
        dobLowerCase = getDateTimeStringFromLong(dob) ?: "",
        age = age,
        ageUnits = ageUnit.asApiValue(),
        fatherName = fatherName ?: "",
        motherName = motherName ?: "",
        facilitySelection = locationRecord.block.name.takeIf { it.isNotBlank() },
        spouseName = genDetails?.spouseName ?: "",
        govtIdentityNo = null,
        govtIdentityTypeID = null,
        isEmergencyRegistration = false,
        titleId = null,
        //benImage = null,
        bankName = nameOfBank,
        branchName = nameOfBranch,
        ifscCode = ifscCode ?: "",
        accountNo = bankAccount,
        ageAtMarriage = genDetails?.ageAtMarriage?.takeIf { it > 0 },
        marriageDate = genDetails?.marriageDate?.takeIf { it > 0 }?.let { getDateTimeStringFromLong(it) },
        // Server gender table only supports 1=Male, 2=Female, 3=Transgender.
        // Map PREFER_NOT_TO_SAY (local genderId=4) to 3 (Transgender) for server compatibility.
        genderID = if (genderId == 4) 3 else genderId,
        genderName = when (gender) {
            MALE -> "Male"
            FEMALE -> "Female"
            TRANSGENDER -> "Transgender"
            PREFER_NOT_TO_SAY -> "Transgender"  // mapped to server-accepted value
            null -> "NA"
        },
        maritalStatusID = if (isKid) null else genDetails?.maritalStatusId?.takeIf { it > 0 }?.toString(),
        maritalStatusName = if (isKid) null else genDetails?.maritalStatus?.takeIf { it.isNotBlank() },
        email = "",
        providerServiceMapID = user.serviceMapId,
        providerServiceMapId = user.serviceMapId,
        benDemographics = BenDemographics(
            communityID = communityId.takeIf { it > 0 },
            communityName = community ?: "",
            religionID = religionId.takeIf { it > 0 },
            religionName = religion ?: "",
            countryID = 1,
            countryName = "India",
            stateID = locationRecord.state.id,
            stateName = locationRecord.state.name,
            districtID = locationRecord.district.id,
            districtName = locationRecord.district.name,
            blockID = locationRecord.block.id,
            tuId = null,
            tuName = null,
            healthFacilityId = null,
            healthFacilityName = null,
            districtBranchID = locationRecord.village.id,
            districtBranchName = locationRecord.village.name,
//            zoneID = user.zoneId,
//            zoneName = user.zoneName,
//            parkingPlaceName = user.parkingPlaceName,
//            parkingPlaceID = user.parkingPlaceId,
//            servicePointID = user.servicePointId.toString(),
//            servicePointName = user.servicePointName,
            address = null,
            addressLine1 = address ?: "",
            addressLine2 = "",
            addressLine3 = "",
            occupation = occupation ?: "unknown",
            economicStatus = economicStatus,
            economicStatusId = economicStatusId,
            residentialArea = residentialArea,
            residentialAreaId = residentialAreaId,
            otherResidentialArea = otherResidentialArea,
            latitude = gpsLatitude ?: household?.gpsLatitude ?: latitude,
            longitude = gpsLongitude ?: household?.gpsLongitude ?: longitude,
            digipin = digipin ?: household?.digipin,
            gpsTimestamp = (gpsTimestamp ?: household?.gpsTimestamp).toGpsTimestampLong(),
            isGpsUnavailable = if (gpsLatitude != null || household?.gpsLatitude != null || gpsLongitude != null || household?.gpsLongitude != null) false else (isGpsUnavailable || (household?.isGpsUnavailable ?: false)),
            gpsUnavailableReason = gpsUnavailableReason ?: household?.gpsUnavailableReason,
            createdBy = user.userName,
        ),
        benPhoneMaps = arrayOf(
            BenPhoneMaps(
                phoneNo = contactNumber?.toString() ?: "0",
                createdBy = user.userName,
            )
        ),
        beneficiaryIdentities = emptyArray(),
//        vanID = user.vanId,
//        parkingPlaceID = user.parkingPlaceId,
        createdBy = user.userName,
        beneficiaryConsent = isConsent,
        personFrom = personFrom,
        personFromId = personFromId,
        typeOfCaseFinding = typeOfCaseFinding,
        typeOfCaseFindingId = typeOfCaseFindingId,
        mobileNumberAvailable = mobileNumberAvailable,
        tuId = locationRecord.tu?.id,
        tuName = locationRecord.tu?.name,
        healthFacilityId = locationRecord.healthFacility?.id,
        healthFacilityName = locationRecord.healthFacility?.name,
        address = address,
        height = height,
        weight = weight,
        bmi = bmi,
        temperature = temperature,

    )
}

private fun AgeUnit?.asApiValue(): String = when (this) {
    AgeUnit.DAYS -> "Days"
    AgeUnit.MONTHS -> "Months"
    AgeUnit.YEARS, null -> "Years"
}
