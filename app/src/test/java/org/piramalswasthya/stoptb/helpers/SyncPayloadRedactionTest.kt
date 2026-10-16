package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test

class SyncPayloadRedactionTest {
    @Test fun masksCredentialsAndPersonalFieldsButKeepsDebugFields() {
        val safe = SyncPayloadRedaction.body("""{"beneficiaryRegID":9065129,"drugName":"Paracetamol","password":"HIDE_PASS","data":{"access_token":"HIDE_TOKEN","mobileNo":"HIDE_PHONE","benName":"HIDE_NAME","dob":"HIDE_DOB"},"statusCode":5000,"errorMessage":"Required duration missing"}""")
        listOf("HIDE_PASS", "HIDE_TOKEN", "HIDE_PHONE", "HIDE_NAME", "HIDE_DOB").forEach { assertFalse(safe.contains(it)) }
        assertTrue(safe.contains("9065129"))
        assertTrue(safe.contains("Paracetamol"))
        assertTrue(safe.contains("Required duration missing"))
        assertEquals(5000, SyncPayloadRedaction.backendStatus(safe))
    }
    @Test fun keepsLargeIdsIntegersNullsAndNestedJsonStringShape() {
        val safe = SyncPayloadRedaction.body("""{"id":9007199254740993,"qtyPrescribed":2,"duration":null,"data":"{\"token\":\"HIDE_NESTED\",\"statusCode\":5000}"}""")
        assertTrue(safe.contains("9007199254740993"))
        assertTrue(safe.contains("\"qtyPrescribed\": 2"))
        assertTrue(safe.contains("\"duration\": null"))
        assertFalse(safe.contains("HIDE_NESTED"))
        assertTrue(safe.contains("\"data\": \""))
    }
    @Test fun unparseableBodiesAndSecretTextAreNotReturnedRaw() {
        assertFalse(SyncPayloadRedaction.body("{\"token\":\"HIDE_BROKEN").contains("HIDE_BROKEN"))
        assertFalse(SyncPayloadRedaction.body("<html>HIDE_HTML</html>").contains("HIDE_HTML"))
        val safe = SyncPayloadRedaction.body("""{"errorMessage":"password=HIDE_WORDS with spaces","details":"Bearer HIDE_BEARER"}""")
        assertFalse(safe.contains("HIDE_WORDS"))
        assertFalse(safe.contains("HIDE_BEARER"))
    }
}
