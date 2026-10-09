package org.piramalswasthya.stoptb.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription

class GeneralOpdSaveRequestTest {
    private val saved = GeneralOpdCache(id = 10, benId = 1, chiefComplaints = listOf("Fever"),
        chiefComplaintIds = listOf(167), medications = listOf("Tablet Paracetamol (500 mg)", "Tablet Aluminium Hydroxide"),
        notes = "Patient remarks", submissionId = "3f1c9a2e-7b4d-4c1a-9a0e-2d5b8c6f1e11")
    private val rows = listOf(
        GeneralOpdPrescription(10, 0, saved.medications!![0], "Twice Daily(BD)", 3, "Day(s)", "Before food", 1341, "Paracetamol", 1, "Tablet"),
        GeneralOpdPrescription(10, 1, saved.medications!![1], "Stat Dose", 2, "Week(s)", "After food", 712, "Aluminium Hydroxide", 1, "Tablet")
    )

    @Test
    fun mapsMultipleDrugsAndSerializesExactConfirmedFieldNames() {
        val request = GeneralOpdSaveRequest.from(saved, rows.reversed(), 9065125, 1738, "kastbnurse1")
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(GeneralOpdSaveRequest::class.java)
        val json = adapter.toJson(request)
        @Suppress("UNCHECKED_CAST")
        val root = moshi.adapter(Map::class.java).fromJson(json) as Map<String, Any>
        assertEquals(saved.submissionId, root["submissionId"])
        @Suppress("UNCHECKED_CAST")
        val complaints = root["chiefComplaints"] as List<Map<String, Any>>
        assertEquals(setOf("chiefComplaintID", "chiefComplaint"), complaints.single().keys)
        assertEquals(167.0, complaints.single()["chiefComplaintID"])
        @Suppress("UNCHECKED_CAST")
        val prescription = root["prescription"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val serializedDrugs = prescription["drugs"] as List<Map<String, Any>>
        assertEquals(setOf("drugID", "drugName", "qtyPrescribed", "frequency", "duration",
            "durationUnit", "instructions", "itemFormID", "drugForm"), serializedDrugs.first().keys)
        assertFalse(root.containsKey("medication"))
        assertFalse(root.containsKey("notes"))
        assertEquals("Patient remarks", request.prescription.instruction)
        assertEquals(listOf(1341, 712), request.prescription.drugs.map { it.drugID })
        assertEquals(listOf(0, 0), request.prescription.drugs.map { it.qtyPrescribed })
        assertEquals(listOf(1, 1), request.prescription.drugs.map { it.itemFormID })
        assertEquals(listOf("Tablet", "Tablet"), request.prescription.drugs.map { it.drugForm })
        assertTrue(json.contains("\"itemFormID\":1"))
        assertTrue(json.contains("\"drugForm\":\"Tablet\""))
        assertEquals(listOf(3, 2), request.prescription.drugs.map { it.duration })
        assertEquals(listOf("Day(s)", "Week(s)"), request.prescription.drugs.map { it.durationUnit })
        assertEquals(listOf("Before food", "After food"), request.prescription.drugs.map { it.instructions })
        assertTrue(json.contains("\"instructions\":\"Before food\""))
        assertFalse(json.contains("\"dose\""))
        assertEquals(json, adapter.toJson(GeneralOpdSaveRequest.from(saved, rows, 9065125, 1738, "kastbnurse1")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unresolvedDrugFormMustNotBeSubmitted() {
        GeneralOpdSaveRequest.from(saved, rows.map { it.copy(itemFormId = null) }, 9065125, 1738, "kastbnurse1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unresolvedDrugIdMustNotBeSubmitted() {
        GeneralOpdSaveRequest.from(saved, rows.map { it.copy(drugId = null) }, 9065125, 1738, "kastbnurse1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unresolvedComplaintIdMustNotBeSubmitted() {
        GeneralOpdSaveRequest.from(saved.copy(chiefComplaintIds = null), rows, 9065125, 1738, "kastbnurse1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteMedicineListMustNotBeSubmitted() {
        GeneralOpdSaveRequest.from(saved, rows.take(1), 9065125, 1738, "kastbnurse1")
    }
}
