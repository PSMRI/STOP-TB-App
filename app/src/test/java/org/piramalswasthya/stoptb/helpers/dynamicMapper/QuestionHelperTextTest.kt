package org.piramalswasthya.stoptb.helpers.dynamicMapper

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.DynamicFormMetadataDao
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity
import java.lang.reflect.Proxy

/**
 * STOP-504: Counselling Section C sends the NPY / DOTS guidance as one HELPER_TEXT_ENGLISH and one
 * HELPER_TEXT_HINDI validation row per bullet. Counselling ingests with HASH_BASED ids, which used to be
 * hash(fieldId_type) — every bullet of a question collided on one id and the upsert kept only the last.
 */
class QuestionHelperTextTest {

    private val npyEnglish = listOf(
        "NPY: DBT of Rs. 1000/month for the duration of treatment.",
        "Cash transfer (DBT) in the patient's Aadhaar-linked bank account, credited monthly for the full treatment duration.",
        "Requirements: Aadhaar-linked bank account and correct patient details in Nikshay.",
        "No separate application needed if enrolled in Nikshay."
    )
    private val npyHindi = listOf("हिंदी 1", "हिंदी 2", "हिंदी 3", "हिंदी 4")

    private fun schemaJson(reverseRows: Boolean = false): String {
        fun row(id: Int, type: String, text: String) =
            """{"validationId":$id,"validationType":"$type","validationParam":"$text","errorMessage":"$text"}"""
        val inServerOrder = npyEnglish.mapIndexed { index, text -> row(100 + index, "HELPER_TEXT_ENGLISH", text) } +
            npyHindi.mapIndexed { index, text -> row(200 + index, "HELPER_TEXT_HINDI", text) } +
            row(300, "MAX_LENGTH", "10")
        val rows = if (reverseRows) inServerOrder.reversed() else inServerOrder
        return """
            {"formId":"7","formUuid":"TB_COUNSELLING_V2","formName":"TB Counselling","versionNumber":2,
             "sections":[{"sectionId":"40","sectionUuid":"TB2_SEC_C","sectionName":"Government Schemes",
               "questions":[{"questionUuid":"TB2_C_Q1","questionId":501,"questionText":"NPY","questionType":"CHECKBOX",
                 "validations":[${rows.joinToString(",")}]}]}]}
        """.trimIndent()
    }

    // Records insertValidations; every other DAO call is a no-op (they all return Unit here).
    private fun ingest(reverseRows: Boolean = false): List<QuestionValidationEntity> = runBlocking {
        val inserted = mutableListOf<QuestionValidationEntity>()
        val dao = Proxy.newProxyInstance(
            DynamicFormMetadataDao::class.java.classLoader,
            arrayOf(DynamicFormMetadataDao::class.java)
        ) { _, method, args ->
            if (method.name == "insertValidations") {
                @Suppress("UNCHECKED_CAST")
                inserted += args[0] as List<QuestionValidationEntity>
            }
            Unit
        } as DynamicFormMetadataDao
        storeFormSchemaInDb(
            metadataDao = dao,
            gson = Gson(),
            apiSchema = FormSchemaDto.fromJson(schemaJson(reverseRows)),
            idStrategy = QuestionIdStrategy.HASH_BASED,
            wipeExistingVersions = false
        )
        inserted
    }

    @Test
    fun `every helper text bullet gets its own local id`() {
        val stored = ingest()
        assertEquals(9, stored.size)
        assertEquals(9, stored.map { row -> row.validationId }.toSet().size)
    }

    @Test
    fun `a single row of a type keeps the id it had before multi-row support`() {
        val maxLength = ingest().single { row -> row.validationType == "MAX_LENGTH" }
        assertEquals("TB2_C_Q1_MAX_LENGTH".hashCode(), maxLength.validationId)
    }

    @Test
    fun `english bullets come back in server order even when stored shuffled`() {
        val shuffled = ingest().sortedBy { row -> row.validationValue }.reversed()
        assertEquals(npyEnglish, QuestionHelperText.resolve("TB2_C_Q1", shuffled, isHindi = false))
    }

    @Test
    fun `bullets follow server validationId order even when the response lists them out of order`() {
        assertEquals(npyEnglish, QuestionHelperText.resolve("TB2_C_Q1", ingest(reverseRows = true), isHindi = false))
        assertEquals(npyHindi, QuestionHelperText.resolve("TB2_C_Q1", ingest(reverseRows = true), isHindi = true))
    }

    @Test
    fun `hindi bullets are used when the app language is hindi`() {
        assertEquals(npyHindi, QuestionHelperText.resolve("TB2_C_Q1", ingest(), isHindi = true))
    }

    @Test
    fun `hindi falls back to english when the question has no hindi rows`() {
        val englishOnly = ingest().filter { row -> row.validationType != QuestionHelperText.HELPER_TEXT_HINDI }
        assertEquals(npyEnglish, QuestionHelperText.resolve("TB2_C_Q1", englishOnly, isHindi = true))
    }

    @Test
    fun `position within type is exact when the id addition wraps past Int MAX_VALUE`() {
        val base = Int.MAX_VALUE - 1
        val ids = (0 until 3).map { index -> base + index } // MAX_VALUE - 1, MAX_VALUE, MIN_VALUE
        assertEquals(listOf(0, 1, 2), ids.map { id -> QuestionHelperText.positionWithinType(id, base) })
    }

    @Test
    fun `a question without helper rows has no helper text`() {
        val rows = listOf(QuestionValidationEntity(1, 1, "MAX_LENGTH", "10", ""))
        assertTrue(QuestionHelperText.resolve("ANY", rows, isHindi = false).isEmpty())
    }
}
