package org.piramalswasthya.stoptb.helpers.dynamicMapper

import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType

object PayloadBuilder {

    private val sectionCodeMap = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val questionCodeMap = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private fun populateMappings(formDef: CompleteFormDefinition) {
        formDef.versions.forEach { version ->
            version.sections.forEach { sec ->
                sec.section.sectionUuid?.let { uuid ->
                    sectionCodeMap[sec.section.sectionId] = uuid
                }
                sec.questions.forEach { qDetails ->
                    qDetails.question.questionUuid?.let { uuid ->
                        questionCodeMap[qDetails.question.questionId] = uuid
                    }
                }
            }
        }
    }

    fun getSectionCode(sectionId: Int): String {
        return sectionCodeMap[sectionId] ?: when (sectionId) {
            5 -> "section-awareness"
            6 -> "section-pre-submit-final"
            7 -> "section-awareness"
            8 -> "section-pre-submit-final"
            9 -> "section-followup"
            else -> "section-$sectionId"
        }
    }

    fun getQuestionCode(questionId: Int): String {
        return questionCodeMap[questionId] ?: when (questionId) {
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

        if (formDef == null) {
            timber.log.Timber.w("PayloadBuilder: formDef is null for formVersionId=$formVersionId, using fallback mappings")
        } else {
            populateMappings(formDef)
        }

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
                    /*"MCQ", "CHECKBOX", "CHECKBOX_MULTI" -> {
                        val optionValues = responses.mapNotNull { it.optionId?.let { optId -> optionsMap[optId] } }
                        AnswerPayload(
                            questionCode = qCode,
                            optionValues = optionValues
                        )
                    }*/
                    "NUMBER" -> {
                        val numText = responses.firstOrNull()?.answerText
                        AnswerPayload(
                            questionCode = qCode,
                            answerText = numText
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
        officerId: Long,
        phaseFilter: String? = null,
        sectionIdFilter: Int? = null
    ): CounsellingBulkSubmitRequest {
        val formVersionId = response.formResponse.formVersionId
        val formCode = formDef?.form?.formUuid ?: "counselling-form-v1"

        if (formDef == null) {
            timber.log.Timber.w("PayloadBuilder: formDef is null for formVersionId=$formVersionId, using fallback mappings")
        } else {
            populateMappings(formDef)
        }

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

        val activeVersion = formDef?.versions?.find { it.version.versionId == formVersionId }
        val sectionsPayload = response.sectionResponses.mapNotNull { secResponseWithQuestions ->
            val sectionId = secResponseWithQuestions.sectionResponse.sectionId

            val secDef = activeVersion?.sections?.find { it.section.sectionId == sectionId }
            if (phaseFilter != null) {
                if (secDef?.section?.sectionPhase != phaseFilter) {
                    return@mapNotNull null
                }
            }
            if (sectionIdFilter != null && sectionId != sectionIdFilter) {
                return@mapNotNull null
            }

            // A section explicitly targeted via sectionIdFilter must always be included, even
            // with no answers (e.g. an optional final section left blank) — only drop empty
            // sections when building a broad, non-targeted (whole-form/phase) payload.
            if (sectionIdFilter == null && secResponseWithQuestions.questionResponses.isEmpty()) {
                return@mapNotNull null
            }

            val groupedResponses = secResponseWithQuestions.questionResponses.groupBy { it.questionId }

            val answersPayload = groupedResponses.map { (questionId, responses) ->
                val questionUuid = questionsUuidMap[questionId] ?: getQuestionCode(questionId)
                val optionIds = responses.map { it.optionId }
                val answerTexts = responses.map { it.answerText }

                when (QuestionType.from(questionsMap[questionId])) {
                    QuestionType.RADIO,
                    QuestionType.DROPDOWN -> {
                        val optId = optionIds.firstOrNull()
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            optionValue = optId?.let { optionsMap[it] }
                        )
                    }

                    QuestionType.CHECKBOX -> {
                        val optionValue = optionIds.firstOrNull()?.let { optionsMap[it] }
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            optionValue = optionValue
                        )
                    }

                    QuestionType.CHECKBOX_MULTI,
                    QuestionType.DROPDOWN_MULTI -> {
                        val optionValues = optionIds.mapNotNull { it?.let { id -> optionsMap[id] } }
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            optionValues = optionValues
                        )
                    }

                    QuestionType.NUMBER,
                    QuestionType.NUMBER_PICKER -> {
                        val numText = answerTexts.firstOrNull()
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            answerText = numText
                        )
                    }

                    QuestionType.DATE -> {
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            answerDate = answerTexts.firstOrNull()
                        )
                    }

                    else -> {
                        BulkAnswerPayload(
                            questionUuid = questionUuid,
                            answerText = answerTexts.firstOrNull()
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
