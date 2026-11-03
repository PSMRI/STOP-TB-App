package org.piramalswasthya.stoptb.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "USER")
data class UserCache(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Int,

    @ColumnInfo(name = "username")
    val userName: String,

    @ColumnInfo(name = "Password")
    val password: String,

    @ColumnInfo(name = "service_map_id")
    val serviceMapId : Int,

//    @ColumnInfo(name = "service_id")
//    val serviceId : Int,

//    @ColumnInfo(name = "service_point_id")
//    val servicePointId : Int,

//    @ColumnInfo(name = "service_point_name")
//    val servicePointName : String,

//    @ColumnInfo(name = "parking_place_id")
//    val parkingPlaceId : Int,

//    @ColumnInfo(name = "parking_place_name")
//    val parkingPlaceName : String,

//    @ColumnInfo(name = "zone_id")
//    val zoneId : Int,
//
//    @ColumnInfo(name = "zone_name")
//    val zoneName : String,

//    @ColumnInfo(name = "van_id")
//    val vanId : Int,

    @Embedded(prefix = "country_")
    val country: LocationEntity,

//    val stateIds: List<Int>,
//
//    val districtIds:List<Int>,
//
//    val blockIds:List<Int> ,
//
//    val villageIds:List<Int>,

    val states: List<LocationEntity>,

    val districts: List<LocationEntity>,

    val blocks: List<LocationEntity>,

    val villages: List<LocationEntity>,

    @ColumnInfo(name = "emergency_contact_number")
    val emergencyContactNo: String,

    @ColumnInfo(name = "user_type")
    val userType: String,

    val serverUpdatedDate: Long? = null,

    @ColumnInfo(name = "logged_in")
    val loggedIn: Boolean
) {
    fun asDomainModel(): UserDomain {
        return UserDomain(
            userId = userId,
            userName = userName,
            password = password,
//            serviceMapId = serviceMapId,
//            servicePointId = servicePointId,
//            serviceId = serviceId,
//            servicePointName = servicePointName,
//            vanId = vanId,
//            zoneId = zoneId,
//            zoneName = zoneName,
//            parkingPlaceId = parkingPlaceId,
//            parkingPlaceName = parkingPlaceName,
            country = country,
            states = states,
            districts = districts,
            blocks = blocks,
            villages = villages,

//            countryId = countryId,
//            stateIds = stateIds,
//            districtIds = districtIds,
//            blockIds = blockIds,
//            villageIds = villageIds,
//            stateEnglish = stateEnglish,
//            stateHindi = stateHindi,
//            stateAssamese = emptyList(),
//            districtEnglish = districtEnglish,
//            districtHindi = districtHindi,
//            districtAssamese = emptyList(),
//            blockEnglish = blockEnglish,
//            blockHindi = blockHindi,
//            blockAssamese = emptyList(),
//            villageEnglish = villageEnglish,
//            villageHindi = villageHindi,
//            villageAssamese = emptyList(),

            contactNo = emergencyContactNo,
            userType = userType,
            loggedIn = loggedIn
        )
    }
}


data class UserDomain(
    val userId: Int,
    val userName: String,
    val password: String,
//    val serviceMapId: Int,
//    val serviceId: Int,
//    val servicePointId: Int,
//    val servicePointName: String,
//    val parkingPlaceId: Int,
//    val parkingPlaceName: String,
//    val zoneId: Int,
//    val zoneName: String,
//    val vanId: Int,
    val country: LocationEntity,
    val states: List<LocationEntity>,
    val districts: List<LocationEntity>,
    val blocks: List<LocationEntity>,
    val villages: List<LocationEntity>,
//    val stateIds: List<Int>,
//    val districtIds: List<Int>,
//    val blockIds: List<Int>,
//    val villageIds: List<Int>,
//
//    val stateEnglish: List<String>,
//    val stateHindi: List<String>,
//    val stateAssamese: List<String>,
//    val districtEnglish: List<String>,
//    val districtHindi: List<String>,
//    val districtAssamese: List<String>,
//    val blockEnglish: List<String>,
//    val blockHindi: List<String>,
//    val blockAssamese: List<String>,
//    val villageEnglish: List<String>,
//    val villageHindi: List<String>,
//    val villageAssamese: List<String>,
    val contactNo: String,
    val userType: String,
    val loggedIn: Boolean,

    )


data class UserNetwork(
    val userId: Int,
    val userName: String,
    val password: String,
    var serviceMapId: Int = -1,
    var serviceId: Int = -1,
    var servicePointId: Int = -1,
    var parkingPlaceId: Int = -1,
    var zoneId: Int = -1,
    var vanId: Int = -1,

    var parkingPlaceName: String? = null,
    var servicePointName: String? = null,
    var zoneName: String? = null,

    var country: LocationEntity? = null,

    var states: MutableList<LocationEntity> = mutableListOf(),
    var districts: MutableList<LocationEntity> = mutableListOf(),
    var blocks: MutableList<LocationEntity> = mutableListOf(),
    var villages: MutableList<LocationEntity> = mutableListOf(),


//    var stateIds: MutableList<Int> = mutableListOf(),
//    var stateEnglish: MutableList<String> = mutableListOf(),
//    var stateHindi: MutableList<String> = mutableListOf(),
//
//    var districtIds:MutableList<Int> = mutableListOf(),
//    var districtEnglish:MutableList<String> = mutableListOf(),
//    var districtHindi:MutableList<String> = mutableListOf(),
//
//    var blockIds:MutableList<Int> = mutableListOf(),
//    var blockEnglish:MutableList<String> = mutableListOf(),
//    var blockHindi:MutableList<String> = mutableListOf(),
//
//    var villageIds:MutableList<Int> = mutableListOf(),
//    var villageEnglish:MutableList<String> = mutableListOf(),
//    var villageHindi:MutableList<String> = mutableListOf(),

//    var countryId : Int = -1,

    var emergencyContactNo: String? = null,
    var userType: String? = null,
    var loggedIn: Boolean = false
) {
    fun asCacheModel(): UserCache {
        return UserCache(
            userId = userId,
            userName = userName,
            password = password,
            serviceMapId = serviceMapId,
//            servicePointId = servicePointId,
//            serviceId = serviceId,
//            servicePointName = servicePointName?:"",
//            vanId = vanId,
//            zoneId = zoneId,
//            zoneName = zoneName?:"",
//            parkingPlaceId = parkingPlaceId,
//            parkingPlaceName = parkingPlaceName?:"",
            country = country ?: LocationEntity(1, "India"),
            states = states,
            districts = districts,
            blocks = blocks,
            villages = villages,


//            countryId = countryId,
//
//            stateIds = stateIds,
//            districtIds = districtIds,
//            blockIds = blockIds,
//            villageIds = villageIds,
//
//            stateEnglish = stateEnglish,
//            stateHindi = stateHindi,
//            districtEnglish = districtEnglish,
//            districtHindi = districtHindi,
//            blockEnglish = blockEnglish,
//            blockHindi = blockHindi,
//            villageEnglish = villageEnglish,
//            villageHindi = villageHindi,

            emergencyContactNo = emergencyContactNo ?: "",
            userType = userType ?: "",
            loggedIn = loggedIn

        )
    }
}

data class User(
    val userId: Int,
    val name: String,
    val userName: String,
    val password: String,
    val role: String,
    val serviceMapId: Int,
    val serverUpdatedDate: Long? = null,
    var vanId: Int = 0,
    val state: LocationEntity,
    val district: LocationEntity,
    val block: LocationEntity,
    val tus: List<LocationEntity>? = emptyList(),
    val healthFacilities: List<LocationEntity>? = emptyList(),
    val villages: List<LocationEntity>,
    val subCentre: String? = null,
    val assignedRoleScreenNames: List<String> = emptyList()
)


/** One village's master Pincode / Residential Area in getUserDetail. Fields are Any? to accept number or string. */
@JsonClass(generateAdapter = true)
data class VillageDetailResponse(
    val villageId: Any? = null,
    val villageName: String? = null,
    val pinCode: Any? = null,
    val residentialAreaId: Any? = null,
    val residentialArea: String? = null
)

@JsonClass(generateAdapter = true)
data class UserNetworkResponse(
    val success: Boolean,
    val message: String?,
    val data: UserDetailsInResponse
)

@JsonClass(generateAdapter = true)
data class UserDetailsInResponse(
    val userId: Int,
    val name: String,
    val userName: String,
    val stateId: Int,
    val stateName: String,
    val workingDistrictId: Int?=0,
    val workingDistrictName: String?=null,
    val serviceProviderId: Int,
    val roleId: Int,
    val roleName: String,
    val providerServiceMapId: Int,
    val blockId: Int,
    val blockName: String,
    val villageId: String,
    val villageName: String,
    val tuId: String? = null,
    val tuName: String? = null,
    val healthFacilityId: String? = null,
    val healthFacilityName: String? = null,
    val updateDate: String? = null,
    // Pincode / Residential Area per village (master data). Optional: absent until the backend sends it.
    val villageDetails: List<VillageDetailResponse>? = null
) {
    /**
     * Valid Pincode / Residential Area per village id from [villageDetails]. Invalid values are dropped,
     * so the app asks for them on the village selection page instead.
     */
    fun villageMasterDefaults(): Map<Int, VillageDefaults> =
        villageDetails.orEmpty().mapNotNull { villageDetail ->
            val villageId = villageDetail.villageId.toPlainString()?.toIntOrNull() ?: return@mapNotNull null
            val pinCode = villageDetail.pinCode.toPlainString()
                ?.takeIf { pin -> pin.length == VillageDefaults.PIN_CODE_LENGTH && pin.all(Char::isDigit) }
            val residentialAreaId = villageDetail.residentialAreaId.toPlainString()?.toIntOrNull()
                ?.takeIf { areaId -> areaId in VillageDefaults.RESIDENTIAL_AREA_IDS } ?: 0
            VillageDefaults(pinCode = pinCode, residentialAreaId = residentialAreaId)
                .takeIf { villageDefaults -> villageDefaults.hasPinCode || villageDefaults.hasResidentialArea }
                ?.let { villageDefaults -> villageId to villageDefaults }
        }.toMap()

    // Accepts a JSON number or string (Moshi reads numbers in Any as Double), so a type mismatch from
    // the server can't fail the whole login.
    private fun Any?.toPlainString(): String? = when (this) {
        is Number -> toDouble().takeIf { number -> number % 1.0 == 0.0 }?.toLong()?.toString()
        is String -> trim().takeIf { text -> text.isNotEmpty() }
        else -> null
    }

    fun toUser(password: String, subCentre: String? = null): User {
        val healthFacilityList = getLocationEntityList(healthFacilityId, healthFacilityName)
        return User(
            userId = userId,
            name = name,
            userName = userName,
            password = password,
            role = roleName,
            serviceMapId = providerServiceMapId,
            serverUpdatedDate = updateDate?.let { parseServerUpdateDate(it) }?.takeIf { it > 0L },
            state = LocationEntity(id = stateId, name = stateName),
            district = LocationEntity(
                id = workingDistrictId ?: 1,
                name = workingDistrictName?.takeIf { it.isNotBlank() } ?: blockName
            ),
            block = LocationEntity(id = blockId, name = blockName),
            tus = getLocationEntityList(tuId, tuName),
            healthFacilities = healthFacilityList.ifEmpty {
                subCentre?.takeIf { it.isNotBlank() }?.let {
                    listOf(LocationEntity(id = 0, name = it))
                } ?: emptyList()
            },
            villages = getLocationEntityList(villageId, villageName),
            subCentre = subCentre
        )
    }

    private fun getLocationEntityList(
        locationId: String?,
        locationName: String?
    ): List<LocationEntity> {
        if (locationId.isNullOrBlank() || locationName.isNullOrBlank()) return emptyList()
        val idList = locationId.split(",").mapNotNull { it.trim().toIntOrNull() }
        val nameList = locationName.split(",").map { it.trim() }
        val locationEntityList = mutableListOf<LocationEntity>()
        if (idList.size == nameList.size) {
            for (index: Int in idList.indices) {
                locationEntityList.add(LocationEntity(id = idList[index], name = nameList[index]))
            }
        }
        return locationEntityList
    }

    private fun parseServerUpdateDate(dateString: String): Long {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "MMM dd, yyyy h:mm:ss a",
            "MMM d, yyyy h:mm:ss a"
        )
        patterns.forEach { pattern ->
            runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).parse(dateString)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }
}

