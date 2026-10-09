package org.piramalswasthya.stoptb.network

data class DoctorDrugMasterData(
    val itemMaster: List<DrugItemNetwork>? = null,
    val drugFormMaster: List<DrugFormNetwork>? = null,
    val drugFrequencyMaster: List<DrugFrequencyNetwork>? = null,
    val drugDurationUnitMaster: List<DrugDurationNetwork>? = null
)

data class DrugItemNetwork(
    val id: Int,
    val itemID: Int,
    val itemName: String?,
    val strength: String?,
    val unitOfMeasurement: String?,
    val quantityInHand: Double?,
    val itemFormID: Int?,
    val routeID: Int?,
    val facilityID: Int?
)

data class DrugFormNetwork(val itemFormID: Int, val itemFormName: String?)
data class DrugFrequencyNetwork(val drugFrequencyID: Int, val frequency: String?)
data class DrugDurationNetwork(val drugDurationID: Int, val drugDuration: String?)
