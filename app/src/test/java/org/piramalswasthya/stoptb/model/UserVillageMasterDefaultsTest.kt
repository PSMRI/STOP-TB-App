package org.piramalswasthya.stoptb.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserVillageMasterDefaultsTest {

    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(UserNetworkResponse::class.java)

    private fun parse(villageDetailsJson: String?): UserDetailsInResponse {
        val villageDetailsField = villageDetailsJson?.let { json -> ""","villageDetails":$json""" }.orEmpty()
        val responseJson = """
            {"success":true,"message":null,"data":{"userId":5427,"name":"Kam Reg","userName":"kamreg1",
            "stateId":18,"stateName":"Karnataka","serviceProviderId":1,"roleId":2,"roleName":"Registrar",
            "providerServiceMapId":1744,"blockId":1352,"blockName":"DEODURGA 1",
            "villageId":"6677,9625","villageName":"Advi Khanapur (6677),Alkur (9625)"$villageDetailsField}}
        """.trimIndent()
        return adapter.fromJson(responseJson)!!.data
    }

    @Test
    fun responseWithoutVillageDetailsStillParsesAndHasNoMasterValues() {
        val userDetails = parse(villageDetailsJson = null)
        assertEquals(2, userDetails.toUser("password").villages.size)
        assertTrue(userDetails.villageMasterDefaults().isEmpty())
    }

    @Test
    fun readsValuesSentAsStringsOrNumbers() {
        val userDetails = parse(
            """[{"villageId":6677,"pinCode":"585443","residentialAreaId":3,"residentialArea":"Rural"},
                {"villageId":"9625","pinCode":584101,"residentialAreaId":"4"}]"""
        )
        assertEquals(
            mapOf(
                6677 to VillageDefaults(pinCode = "585443", residentialAreaId = 3),
                9625 to VillageDefaults(pinCode = "584101", residentialAreaId = 4)
            ),
            userDetails.villageMasterDefaults()
        )
    }

    @Test
    fun dropsInvalidValuesAndKeepsTheValidPart() {
        val userDetails = parse(
            """[{"villageId":6677,"pinCode":"5854","residentialAreaId":3},
                {"villageId":9625,"pinCode":"584101","residentialAreaId":9},
                {"villageId":null,"pinCode":"584102","residentialAreaId":2},
                {"villageId":7484,"pinCode":null,"residentialAreaId":null}]"""
        )
        assertEquals(
            mapOf(
                6677 to VillageDefaults(pinCode = null, residentialAreaId = 3),
                9625 to VillageDefaults(pinCode = "584101", residentialAreaId = 0)
            ),
            userDetails.villageMasterDefaults()
        )
    }
}
