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

    companion object {
        const val PIN_CODE_LENGTH = 6

        // Indian pincodes: six digits, first digit 1-9
        val PIN_CODE_RANGE = 100000..999999

        fun isValidPinCode(pinCode: String?): Boolean =
            pinCode != null && pinCode.length == PIN_CODE_LENGTH && pinCode.all(Char::isDigit) &&
                    pinCode.toInt() in PIN_CODE_RANGE

        // Ids in R.array.nbr_residential_area_array; the server stores the same ids in
        // i_beneficiarydetails.residentialAreaId
        val RESIDENTIAL_AREA_IDS = 1..6
    }
}
