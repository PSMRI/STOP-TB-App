package org.piramalswasthya.stoptb.repositories

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.DoctorDrugMasterData
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo.Companion.toCache
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DrugMasterNetworkTest {
    @Test
    fun medicineFilterUsesFormIdAndKeepsOutOfStockItemsSelectable() {
        val tablet = org.piramalswasthya.stoptb.model.DrugItemMasterCache(
            "scope", 1, 712, "Medicine", null, null, 0.0, 1, null, null)
        val syrup = tablet.copy(id = 2, itemId = 713, itemFormId = 2)
        val masters = org.piramalswasthya.stoptb.model.OpdDrugMasters(items = listOf(tablet, syrup))
        assertEquals(listOf(tablet), masters.forForm(1).items)
        assertEquals(listOf(syrup), masters.forForm(2).items)
        assertTrue(masters.forForm(null).items.isEmpty())
        assertTrue(masters.forForm(99).items.isEmpty())
    }

    @Test
    fun endpointAndMoshiMapping_matchProvidedDrugMasterContract() = runBlocking {
        var path: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            path = chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("""
                    {"data": {
                      "itemMaster": [{"id":5,"itemID":712,"itemName":"Aluminium Hydroxide",
                        "strength":"250","unitOfMeasurement":"mg","quantityInHand":100,
                        "itemFormID":1,"routeID":1,"facilityID":127},
                        {"id":9,"itemID":792,"itemName":"Amlodipine","strength":"2.5",
                         "unitOfMeasurement":"mg","quantityInHand":0,"itemFormID":1,"routeID":1,"facilityID":127}],
                      "drugFormMaster":[{"itemFormID":1,"itemFormName":"Tablet"}],
                      "drugFrequencyMaster":[{"drugFrequencyID":1,"frequency":"Once daily"}],
                      "drugDurationUnitMaster":[{"drugDurationID":1,"drugDuration":"Day(s)"}]
                    },"statusCode":200,"status":"Success"}
                """.trimIndent().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.invalid/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build().create(AmritApiService::class.java)
        val response = api.getDrugMaster(6, 1734, "Male", 0, 134)
        assertEquals("/tm-api/master/doctor/masterData/6/1734/Male/0/134", path)
        val cache = response.body()!!.data!!.toCache("scope")!!
        assertEquals(listOf("Tablet Aluminium Hydroxide (250 mg)", "Tablet Amlodipine (2.5 mg)"), cache.medicineLabels())
        assertEquals(712, cache.items.first().itemId)
        assertEquals(127, cache.items.first().facilityId)
        assertEquals(0.0, cache.items.last().quantityInHand!!, 0.0)
        assertTrue(cache.isOutOfStock("Tablet Amlodipine (2.5 mg)"))
        assertFalse(cache.isOutOfStock("Tablet Aluminium Hydroxide (250 mg)"))
        assertFalse(cache.isOutOfStock("Legacy medicine"))
        assertFalse(cache.copy(items = cache.items.map { it.copy(quantityInHand = null) })
            .isOutOfStock("Tablet Amlodipine (2.5 mg)"))
        assertEquals("Once daily", cache.frequencies.single().frequency)
        assertEquals("Day(s)", cache.durationUnits.single().drugDuration)
    }

    @Test
    fun missingMasterArrays_areNotAcceptedAsAnEmptyRefresh() {
        assertNull(DoctorDrugMasterData().toCache("scope"))
    }
}
