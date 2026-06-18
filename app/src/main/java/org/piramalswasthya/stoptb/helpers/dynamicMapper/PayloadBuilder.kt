package org.piramalswasthya.stoptb.helpers.dynamicMapper

import org.piramalswasthya.stoptb.model.dynamicEntity.*

object PayloadBuilder {

    fun getSectionCode(sectionId: Int): String {
        return when (sectionId) {
            5 -> "section-awareness"
            6 -> "section-pre-submit-final"
            7 -> "section-awareness"
            8 -> "section-pre-submit-final"
            9 -> "section-followup"
            else -> "section-$sectionId"
        }
    }

    fun getQuestionCode(questionId: Int): String {
        return when (questionId) {
            9 -> "q-aware-tb"
            10 -> "q-treatment-history"
            11 -> "q-counselled"
            12 -> "q-aware-tb"
            13 -> "q-symptoms"
            14 -> "q-notes"
            15 -> "q-counselled"
            16 -> "q-followup-date"
            else -> "q-$questionId"
        }
    }

    fun buildPayload(
        response: CompleteFormResponse,
        formDef: CompleteFormDefinition?,
        officerId: Long
    ): CounsellingSyncRequest {
        val formVersionId = response.formResponse.formVersionId
        val formCode = formDef?.form?.formUuid ?: "counselling-form-v1"

        val questionsMap = mutableMapOf<Int, String>() // questionId to questionType
        val optionsMap = mutableMapOf<Int, String>() // optionId to optionValue

        formDef?.versions?.find { it.version.versionId == formVersionId }?.sections?.forEach { sec ->
            sec.questions.forEach { qDetails ->
                questionsMap[qDetails.question.questionId] = qDetails.question.questionType
                qDetails.options.forEach { optDetails ->
                    optionsMap[optDetails.option.optionId] = optDetails.option.optionValue
                }
            }
        }

        val sectionsPayload = response.sectionResponses.map { secResponseWithQuestions ->
            val sectionId = secResponseWithQuestions.sectionResponse.sectionId
            val groupedResponses = secResponseWithQuestions.questionResponses.groupBy { it.questionId }

            val answersPayload = groupedResponses.map { (questionId, responses) ->
                val qType = questionsMap[questionId]
                val qCode = getQuestionCode(questionId)

                when (qType) {
                    "RADIO", "DROPDOWN" -> {
                        val optId = responses.firstOrNull()?.optionId
                        AnswerPayload(
                            questionCode = qCode,
                            optionValue = optId?.let { optionsMap[it] }
                        )
                    }
                    "MCQ", "CHECKBOX" -> {
                        val optionValues = responses.mapNotNull { it.optionId?.let { optId -> optionsMap[optId] } }
                        AnswerPayload(
                            questionCode = qCode,
                            optionValues = optionValues
                        )
                    }
                    "NUMBER" -> {
                        val numText = responses.firstOrNull()?.answerText
                        AnswerPayload(
                            questionCode = qCode,
                            answerNumber = numText?.toDoubleOrNull()
                        )
                    }
                    "DATE" -> {
                        AnswerPayload(
                            questionCode = qCode,
                            answerDate = responses.firstOrNull()?.answerText
                        )
                    }
                    else -> {
                        AnswerPayload(
                            questionCode = qCode,
                            answerText = responses.firstOrNull()?.answerText
                        )
                    }
                }
            }

            SectionPayload(
                sectionCode = getSectionCode(sectionId),
                answers = answersPayload
            )
        }

        return CounsellingSyncRequest(
            formCode = formCode,
            beneficiaryId = response.formResponse.beneficiaryId,
            officerId = officerId,
            sections = sectionsPayload
        )
    }

    fun buildBulkPayload(
        response: CompleteFormResponse,
        formDef: CompleteFormDefinition?,
        officerId: Long
    ): CounsellingBulkSubmitRequest {
        val formVersionId = response.formResponse.formVersionId
        val formCode = formDef?.form?.formUuid ?: "counselling-form-v1"

        val questionsMap = mutableMapOf<Int, String>()
        val optionsMap = mutableMapOf<Int, String>()
        val questionsUuidMap = mutableMapOf<Int, String>()
        val sectionsUuidMap = mutableMapOf<Int, String>()

        formDef?.versions?.find { it.version.versionId == formVersionId }?.sections?.forEach { sec ->
            sectionsUuidMap[sec.section.sectionId] = sec.section.sectionUuid ?: getSectionCode(sec.section.sectionId)
            sec.questions.forEach { qDetails ->
                questionsMap[qDetails.question.questionId] = qDetails.question.questionType
                questionsUuidMap[qDetails.question.questionId] = qDetails.question.questionUuid ?: getQuestionCode(qDetails.question.questionId)
                qDetails.options.forEach { optDetails ->
                    optionsMap[optDetails.option.optionId] = optDetails.option.optionValue
                }
            }
        }

        val sectionsPayload = response.sectionResponses.map { secResponseWithQuestions ->
            val sectionId = secResponseWithQuestions.sectionResponse.sectionId
            val groupedResponses = secResponseWithQuestions.questionResponses.groupBy { it.questionId }

            val answersPayload = groupedResponses.map { (questionId, responses) ->
                val qType = questionsMap[questionId]
                val qUuid = questionsUuidMap[questionId] ?: getQuestionCode(questionId)

                when (qType) {
                    "RADIO", "DROPDOWN" -> {
                        val optId = responses.firstOrNull()?.optionId
                        BulkAnswerPayload(
                            questionUuid = qUuid,
                            optionValue = optId?.let { optionsMap[it] }
                        )
                    }
                    "MCQ", "CHECKBOX" -> {
                        val optionValues = responses.mapNotNull { it.optionId?.let { optId -> optionsMap[optId] } }
                        BulkAnswerPayload(
                            questionUuid = qUuid,
                            optionValues = optionValues
                        )
                    }
                    "NUMBER" -> {
                        val numText = responses.firstOrNull()?.answerText
                        BulkAnswerPayload(
                            questionUuid = qUuid,
                            answerNumber = numText?.toDoubleOrNull()
                        )
                    }
                    "DATE" -> {
                        BulkAnswerPayload(
                            questionUuid = qUuid,
                            answerDate = responses.firstOrNull()?.answerText
                        )
                    }
                    else -> {
                        BulkAnswerPayload(
                            questionUuid = qUuid,
                            answerText = responses.firstOrNull()?.answerText
                        )
                    }
                }
            }

            val sUuid = sectionsUuidMap[sectionId] ?: getSectionCode(sectionId)
            BulkSectionPayload(
                sectionUuid = sUuid,
                answers = answersPayload
            )
        }

        return CounsellingBulkSubmitRequest(
            formUuid = formCode,
            beneficiaryId = response.formResponse.beneficiaryId,
            officerId = officerId,
            sections = sectionsPayload
        )
    }
}
