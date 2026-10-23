package org.piramalswasthya.stoptb.model

data class VillageDefaults(
    val pinCode: String? = null,
    // 1-based position in R.array.nbr_residential_area_array (0 = not captured)
    val residentialAreaId: Int = 0 // 1 = Tribal NPVTG, 2 = Tribal-PVTG, 3 = Rural, 4 = Urban, 5 = Urban Slum, 6 = Unknown
) {
    val hasPinCode: Boolean
        get() = !pinCode.isNullOrBlank()

    val hasResidentialArea: Boolean
        get() = residentialAreaId > 0

    val isComplete: Boolean
        get() = hasPinCode && hasResidentialArea
}
