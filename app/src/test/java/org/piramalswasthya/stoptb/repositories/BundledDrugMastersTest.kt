package org.piramalswasthya.stoptb.repositories

import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.network.*
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo.Companion.isUsable
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo.Companion.selectMasters
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo.Companion.toCache

class BundledDrugMastersTest {
    @Test
    fun emptyCacheUsesSameCompleteDefaultsForAnyServiceMap() {
        val first = selectMasters(OpdDrugMasters(), "6/1738/Male/0/134")
        val other = selectMasters(OpdDrugMasters(), "6/9999/Male/0/134")
        assertTrue(first.isUsable())
        assertEquals(9, first.items.size)
        assertEquals(3, first.forms.size)
        assertEquals(8, first.frequencies.size)
        assertEquals(3, first.durationUnits.size)
        assertEquals(first.medicineLabels(), other.medicineLabels())
        assertEquals(first.items.map { it.itemId }, other.items.map { it.itemId })
        assertTrue(other.items.all { it.scope == "6/9999/Male/0/134" })
        assertTrue(other.forms.all { it.scope == "6/9999/Male/0/134" })
        assertTrue(other.frequencies.all { it.scope == "6/9999/Male/0/134" })
        assertTrue(other.durationUnits.all { it.scope == "6/9999/Male/0/134" })
        assertTrue(other.items.all { it.quantityInHand == null && it.facilityId == null && it.routeId == null })
        assertTrue(other.medicineLabels().none { other.isOutOfStock(it) })
        assertEquals((900001..900009).toSet(), first.items.map { it.itemId }.toSet())
        assertEquals(5, first.forForm(1).items.size)
        assertEquals(3, first.forForm(3).items.size)
        assertEquals(1, first.forForm(11).items.size)
        assertEquals("ORS sachets", first.forForm(11).items.single().itemName)
        val syrup = first.items.single { it.itemId == 900007 }
        assertEquals("Syp Paracetamol 250mg", syrup.itemName)
        assertEquals("60", syrup.strength)
        assertEquals("ml", syrup.unitOfMeasurement)
    }

    @Test
    fun validCacheIncludingZeroStockAlwaysWinsWithoutMixingDefaults() {
        val defaults = BundledDrugMasters.forScope("scope")
        val cached = defaults.copy(items = listOf(defaults.items.first().copy(quantityInHand = 0.0)))
        val selected = selectMasters(cached, "scope")
        assertSame(cached, selected)
        assertTrue(selected.isOutOfStock(selected.medicineLabels().single()))
    }

    @Test
    fun incompleteCacheFallsBackAsOneSet() {
        val defaults = BundledDrugMasters.forScope("scope")
        listOf(defaults.copy(items = emptyList()), defaults.copy(forms = emptyList()),
            defaults.copy(frequencies = emptyList()), defaults.copy(durationUnits = emptyList()),
            defaults.copy(items = listOf(defaults.items.first().copy(itemFormId = -1))))
            .forEach { assertEquals(defaults, selectMasters(it, "scope")) }
    }

    @Test
    fun emptyPartialOrUnlinkedApiResponsesCannotReplaceCache() {
        val data = DoctorDrugMasterData(
            listOf(DrugItemNetwork(1, 1350, "Allopurinol", "100", "mg", 0.0, 1, 1, 134)),
            listOf(DrugFormNetwork(1, "Tablet")),
            listOf(DrugFrequencyNetwork(1, "Once Daily(OD)")),
            listOf(DrugDurationNetwork(1, "Day(s)")))
        assertNotNull(data.toCache("scope"))
        assertNull(data.copy(itemMaster = emptyList()).toCache("scope"))
        assertNull(data.copy(drugFormMaster = emptyList()).toCache("scope"))
        assertNull(data.copy(drugFrequencyMaster = emptyList()).toCache("scope"))
        assertNull(data.copy(drugDurationUnitMaster = emptyList()).toCache("scope"))
        assertNull(data.copy(drugFormMaster = listOf(DrugFormNetwork(2, "Syrup"))).toCache("scope"))
        assertNull(data.copy(itemMaster = data.itemMaster!!.let { it + it }).toCache("scope"))
    }

    @Test
    fun fallbackPrescriptionRetainsProvidedIdsAndUsesActualSubmissionServiceMap() {
        val masters = BundledDrugMasters.forScope("6/9999/Male/0/134")
        val item = masters.items.single { it.itemId == 900001 }
        val form = masters.forms.single { it.itemFormId == item.itemFormId }
        val label = masters.medicineLabels()[masters.items.indexOf(item)]
        val row = GeneralOpdPrescription(1, 0, label, "Once Daily(OD)", 1, "Week(s)", "Before food",
            item.itemId, item.itemName, form.itemFormId, form.itemFormName)
        val cache = GeneralOpdCache(benId = 1L, submissionId = "fallback-test", medications = listOf(label))
        val request = GeneralOpdSaveRequest.from(cache, listOf(row), 1L, 9999, "test")
        assertEquals(9999, request.providerServiceMapID)
        assertEquals(900001, request.prescription.drugs.single().drugID)
        assertEquals(1, request.prescription.drugs.single().itemFormID)
        assertEquals("Tab Paracetamol", request.prescription.drugs.single().drugName)
        assertEquals("Tablet Tab Paracetamol (500 mg)", label)
    }
}
