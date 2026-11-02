package org.piramalswasthya.stoptb.model

import androidx.room.Entity

// Scope includes the endpoint's service, category, gender, van and requested facility.
@Entity(tableName = "DRUG_ITEM_MASTER", primaryKeys = ["scope", "id"])
data class DrugItemMasterCache(
    val scope: String,
    val id: Int,
    val itemId: Int,
    val itemName: String,
    val strength: String?,
    val unitOfMeasurement: String?,
    val quantityInHand: Double?,
    val itemFormId: Int?,
    val routeId: Int?,
    val facilityId: Int?
)

@Entity(tableName = "DRUG_FORM_MASTER", primaryKeys = ["scope", "itemFormId"])
data class DrugFormMasterCache(val scope: String, val itemFormId: Int, val itemFormName: String)

@Entity(tableName = "DRUG_FREQUENCY_MASTER", primaryKeys = ["scope", "drugFrequencyId"])
data class DrugFrequencyMasterCache(val scope: String, val drugFrequencyId: Int, val frequency: String)

@Entity(tableName = "DRUG_DURATION_UNIT_MASTER", primaryKeys = ["scope", "drugDurationId"])
data class DrugDurationUnitMasterCache(val scope: String, val drugDurationId: Int, val drugDuration: String)

data class OpdDrugMasters(
    val items: List<DrugItemMasterCache> = emptyList(),
    val forms: List<DrugFormMasterCache> = emptyList(),
    val frequencies: List<DrugFrequencyMasterCache> = emptyList(),
    val durationUnits: List<DrugDurationUnitMasterCache> = emptyList()
) {
    fun forForm(itemFormId: Int?): OpdDrugMasters = copy(items = items.filter {
        itemFormId != null && it.itemFormId == itemFormId
    })

    fun isOutOfStock(medicine: String): Boolean {
        val matches = medicineLabels().mapIndexedNotNull { index, label ->
            items[index].takeIf { label == medicine }
        }
        // Label-only legacy prescriptions must not infer zero stock from unknown quantities.
        return matches.isNotEmpty() && matches.all { it.quantityInHand == 0.0 }
    }

    fun medicineLabels(): List<String> = items.map { item ->
        val form = forms.firstOrNull { it.itemFormId == item.itemFormId }?.itemFormName.orEmpty()
        val strength = listOfNotNull(item.strength?.takeIf { it.isNotBlank() },
            item.unitOfMeasurement?.takeIf { it.isNotBlank() }).joinToString(" ")
        listOf(form, item.itemName).filter { it.isNotBlank() }.joinToString(" ") +
            if (strength.isNotBlank()) " ($strength)" else ""
    }
}
