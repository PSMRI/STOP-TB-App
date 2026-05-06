package org.piramalswasthya.stoptb.model

import androidx.room.Embedded

data class LocationRecord(
    @Embedded(prefix = "country_")
    val country: LocationEntity,
    @Embedded(prefix = "state_")
    val state: LocationEntity,
    @Embedded(prefix = "district_")
    val district: LocationEntity,
    @Embedded(prefix = "block_")
    val block: LocationEntity,
    @Embedded(prefix = "village_")
    val village: LocationEntity,
    @Embedded(prefix = "tu_")
    val tu: LocationEntity? = null,
    @Embedded(prefix = "healthFacility_")
    val healthFacility: LocationEntity? = null,
) : java.io.Serializable

data class LocationEntity(
    val id: Int,
    val name: String,
    val nameHindi: String? = null,
    val nameAssamese: String? = null
) : java.io.Serializable
