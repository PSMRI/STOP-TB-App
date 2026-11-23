package org.piramalswasthya.stoptb.repositories.contactTracing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.stubbing.Answer
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.CounsellingFormResponseDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.DynamicFormMetadataDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseEntity
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository

/**
 * The form's submit waits for syncMutex while the sweep sends the same response. Once it gets
 * the lock, the row is SYNCED with the content it holds, so the form's call - with or without a
 * phase filter - must not POST it again. A row still UNSYNCED (new content) is still sent.
 *
 * Plain Mockito with answers by method name: suspend functions carry a continuation argument,
 * which makes ordinary stubbing and verification of them unreliable.
 */
class ContactTracingSubmitOnceTest {

    private var storedSyncStatus = "SYNCED"

    private val responseDao = mock(CounsellingFormResponseDao::class.java, Answer { invocation ->
        if (invocation.method.name == "getFormResponseById") response(storedSyncStatus)
        else RETURNS_DEFAULTS.answer(invocation)
    })
    private val metadataDao = mock(DynamicFormMetadataDao::class.java)
    private val api = mock(AmritApiService::class.java)
    private val db = mock(InAppDb::class.java, Answer { invocation ->
        when (invocation.method.name) {
            "counsellingFormResponseDao" -> responseDao
            "dynamicFormMetadataDao" -> metadataDao
            else -> RETURNS_DEFAULTS.answer(invocation)
        }
    })
    private val repo = ContactTracingRepositoryImpl(
        db, mock(ICounsellingRepository::class.java), api, mock(PreferenceDao::class.java)
    )

    private fun response(syncStatus: String) = CompleteFormResponse(
        FormResponseEntity(
            responseId = RESPONSE_ID, beneficiaryId = 500_001L, formVersionId = 7, status = "SUBMITTED",
            lastVisitedSectionId = null, syncStatus = syncStatus
        ),
        emptyList()
    )

    private fun formDefinitionReads() =
        mockingDetails(metadataDao).invocations.count { call -> call.method.name == "getFormDefinitionByVersionId" }

    @Test
    fun aResponseTheSweepAlreadySentIsNotPostedAgainForAnyPhase() = runBlocking {
        storedSyncStatus = "SYNCED"

        assertTrue(repo.submitResponseBulk(RESPONSE_ID, "POST_SUBMIT"))
        assertTrue(repo.submitResponseBulk(RESPONSE_ID, "PRE_SUBMIT"))
        assertTrue(repo.submitResponseBulk(RESPONSE_ID, null))

        verifyNoInteractions(api)
        assertEquals(0, formDefinitionReads())
    }

    @Test
    fun anUnsentResponseStillGoesAheadWithItsPhase() = runBlocking {
        storedSyncStatus = "UNSYNCED"

        repo.submitResponseBulk(RESPONSE_ID, "POST_SUBMIT")

        // Past the already-sent check: it builds the payload from the form definition.
        assertEquals(1, formDefinitionReads())
    }

    private companion object {
        const val RESPONSE_ID = 42L
    }
}
