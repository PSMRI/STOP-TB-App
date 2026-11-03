package org.piramalswasthya.stoptb.repositories

import org.piramalswasthya.stoptb.model.*

/**
 * Product-provided local General OPD masters, updated on 2026-10-29.
 * Shared across service maps. The supplied itemId also serves as the unique
 * in-memory row id because the local list has no inventory row ids.
 * Stock is unknown; never persist these defaults into the API cache.
 */
internal object BundledDrugMasters {
    fun forScope(scope: String): OpdDrugMasters = OpdDrugMasters(
        items = listOf(
            DrugItemMasterCache(scope, 900001, 900001, "Tab Paracetamol", "500", "mg", null, 1, null, null),
            DrugItemMasterCache(scope, 900002, 900002, "Tab Digene", "", "", null, 1, null, null),
            DrugItemMasterCache(scope, 900003, 900003, "Tab Cetrizine", "10", "mg", null, 1, null, null),
            DrugItemMasterCache(scope, 900004, 900004, "Tab Sporolac DS 120M", "", "", null, 1, null, null),
            DrugItemMasterCache(scope, 900005, 900005, "ORS sachets", "", "", null, 11, null, null),
            DrugItemMasterCache(scope, 900006, 900006, "Tab Multivitamin", "", "", null, 1, null, null),
            DrugItemMasterCache(scope, 900007, 900007, "Syp Paracetamol 250mg", "60", "ml", null, 3, null, null),
            DrugItemMasterCache(scope, 900008, 900008, "Syp Cetrizine", "60", "ml", null, 3, null, null),
            DrugItemMasterCache(scope, 900009, 900009, "Syp Zinc", "60", "ml", null, 3, null, null),
        ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item: DrugItemMasterCache -> item.itemName }
            .thenBy { it.id }),
        forms = listOf(
            DrugFormMasterCache(scope, 1, "Tablet"),
            DrugFormMasterCache(scope, 3, "Syrup"),
            DrugFormMasterCache(scope, 11, "Sachet"),
        ).sortedBy { it.itemFormId },
        frequencies = listOf(
            DrugFrequencyMasterCache(scope, 1, "Once Daily(OD)"),
            DrugFrequencyMasterCache(scope, 2, "Twice Daily(BD)"),
            DrugFrequencyMasterCache(scope, 3, "Thrice Daily (TID)"),
            DrugFrequencyMasterCache(scope, 4, "Four Times in a Day (QID)"),
            DrugFrequencyMasterCache(scope, 5, "Single Dose"),
            DrugFrequencyMasterCache(scope, 6, "Stat Dose"),
            DrugFrequencyMasterCache(scope, 7, "Once in a Week"),
            DrugFrequencyMasterCache(scope, 8, "SOS"),
        ).sortedBy { it.drugFrequencyId },
        durationUnits = listOf(
            DrugDurationUnitMasterCache(scope, 1, "Day(s)"),
            DrugDurationUnitMasterCache(scope, 2, "Week(s)"),
            DrugDurationUnitMasterCache(scope, 3, "Month(s)"),
        ).sortedBy { it.drugDurationId }
    )
}
