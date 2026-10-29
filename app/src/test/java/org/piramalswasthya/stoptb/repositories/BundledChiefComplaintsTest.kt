package org.piramalswasthya.stoptb.repositories

import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest

class BundledChiefComplaintsTest {
    @Test
    fun suppliedIdsAreCompleteAndResolutionPreservesSelectionOrder() {
        assertEquals((1..21).toList(), BundledChiefComplaints.items.map { it.chiefComplaintId })
        assertEquals(21, BundledChiefComplaints.items.map { it.chiefComplaint }.distinct().size)
        assertEquals(listOf(21, 1, 12), BundledChiefComplaints.resolveIds(listOf("Others", "Fever", "Abdominal pain")))
        assertEquals(listOf(1, 9), BundledChiefComplaints.resolveIds(listOf(" Fever ", "cOuGh")))
    }

    @Test
    fun unknownNamesCannotGetAnInventedOrApproximateId() {
        assertNull(BundledChiefComplaints.resolveIds(listOf("Fever", "Body pain")))
        assertNull(BundledChiefComplaints.resolveIds(listOf("")))
        assertEquals(emptyList<Int>(), BundledChiefComplaints.resolveIds(emptyList()))
    }

    @Test
    fun pendingRecordCanBuildSavePayloadWithTheSuppliedStaticIds() {
        val names = listOf("Fever", "Headache", "Others")
        val pending = GeneralOpdCache(benId = 1L, submissionId = "pending-static-test", chiefComplaints = names)
        val ready = pending.copy(chiefComplaintIds = BundledChiefComplaints.resolveIds(names))
        val request = GeneralOpdSaveRequest.from(ready, emptyList(), 1L, 1744, "test")
        assertEquals(listOf(1, 2, 21), request.chiefComplaints.map { it.chiefComplaintID })
        assertEquals(names, request.chiefComplaints.map { it.chiefComplaint })
        assertEquals("pending-static-test", request.submissionId)
        assertEquals(1744, request.providerServiceMapID)
    }
}
