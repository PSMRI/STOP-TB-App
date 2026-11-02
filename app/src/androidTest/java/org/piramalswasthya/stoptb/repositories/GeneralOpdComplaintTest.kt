package org.piramalswasthya.stoptb.repositories

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.network.GeneralOpdDrugResponse
import org.piramalswasthya.stoptb.repositories.TBRepo.Companion.optChiefComplaintsOrNull

@RunWith(AndroidJUnit4::class)
class GeneralOpdComplaintTest {
    @Test
    fun getAllNestedResponseRestoresStructuredDrugsAndLegacyComplaints() {
        val response = JSONObject("""
            {"data":{"data":[
              {"beneficiaryRegID":9065129,"chiefComplaint":"[\"Abdominal Distention\"]",
               "drugs":[{"drugID":1345,"drugName":"Acetylsalicylic acid",
                "itemFormID":1,"drugForm":"Tablet","frequency":"Twice Daily(BD)",
                "duration":5,"durationUnit":"Day(s)","qtyPrescribed":10,"instructions":"After food"}]},
              {"beneficiaryRegID":35133,"chiefComplaint":"[\"Cough\",\"Fever\"]",
               "medication":"Paracetamol, Cough Syrup","frequency":"Twice Daily(BD)",
               "duration":"5 Days","drugs":[]}
            ],"count":2},"statusCode":200}
        """.trimIndent())
        val records = response.getJSONObject("data").getJSONArray("data")
        val current = records.getJSONObject(0)
        assertEquals(listOf("Abdominal Distention"), current.optChiefComplaintsOrNull())
        val rows = GeneralOpdDrugResponse.parsePrescriptions(current.getJSONArray("drugs").toString(), 7)
        assertEquals("Tablet", rows.single().drugForm)
        assertEquals(5, rows.single().durationCount)
        val legacy = records.getJSONObject(1)
        assertEquals(listOf("Cough", "Fever"), legacy.optChiefComplaintsOrNull())
        assertTrue(GeneralOpdDrugResponse.parsePrescriptions(legacy.getJSONArray("drugs").toString(), 8).isEmpty())
        assertEquals("Paracetamol, Cough Syrup", legacy.getString("medication"))
    }

    @Test
    fun sameRevisionDrugRecoveryDoesNotOverwritePendingLocalOrNewerData() {
        val saved = GeneralOpdCache(benId = 1L, syncState = SyncState.SYNCED, serverUpdatedDate = 100L)
        val incoming = GeneralOpdDrugResponse.parsePrescriptions("""
            [{"drugID":1,"drugName":"Medicine","frequency":"Once daily","duration":2,"durationUnit":"Day(s)"}]
        """.trimIndent(), 7)
        assertTrue(TBRepo.canRecoverGeneralOpdDrugs(saved, incoming, 100L))
        assertFalse(TBRepo.canRecoverGeneralOpdDrugs(saved, incoming, 99L))
        assertFalse(TBRepo.canRecoverGeneralOpdDrugs(saved, incoming, 0L))
        assertFalse(TBRepo.canRecoverGeneralOpdDrugs(saved, emptyList(), 100L))
        assertFalse(TBRepo.canRecoverGeneralOpdDrugs(saved.copy(syncState = SyncState.UNSYNCED), incoming, 100L))
        assertFalse(TBRepo.canRecoverGeneralOpdDrugs(saved.copy(syncState = SyncState.SYNCING), incoming, 100L))
    }

    @Test
    fun parsesCurrentAndLegacyComplaintFormats() {
        for (key in listOf("chiefComplaint", "chiefComplaints")) {
            for (value in listOf<Any>(" Cough ", JSONArray().put("Cough"), "[\"Cough\"]")) {
                assertEquals(listOf("Cough"), JSONObject().put(key, value).optChiefComplaintsOrNull())
            }
        }
        assertNull(JSONObject().put("chiefComplaint", JSONArray()).optChiefComplaintsOrNull())
        assertEquals(listOf("Cough"), JSONObject().put("chiefComplaint", JSONArray())
            .put("chiefComplaints", "Cough").optChiefComplaintsOrNull())
    }

    @Test
    fun recoversMissingComplaintFromSameServerRevisionOnly() {
        val saved = GeneralOpdCache(benId = 1L, syncState = SyncState.SYNCED, serverUpdatedDate = 100L)
        val incoming = listOf("Cough")
        assertTrue(TBRepo.canRecoverChiefComplaints(saved, incoming, 100L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved, incoming, 99L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved, incoming, 0L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved, emptyList(), 100L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved.copy(chiefComplaints = listOf("Fever")), incoming, 100L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved.copy(syncState = SyncState.UNSYNCED), incoming, 100L))
        assertFalse(TBRepo.canRecoverChiefComplaints(saved.copy(syncState = SyncState.SYNCING), incoming, 100L))
    }
}
