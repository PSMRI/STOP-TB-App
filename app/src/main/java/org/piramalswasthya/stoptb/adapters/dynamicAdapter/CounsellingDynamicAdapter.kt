package org.piramalswasthya.stoptb.adapters.dynamicAdapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDateBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDropdownBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingMcqBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingRadioBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingTextBinding
import org.piramalswasthya.stoptb.databinding.ItemCtNumberBinding
import org.piramalswasthya.stoptb.databinding.ItemCtNumberPickerBinding
import org.piramalswasthya.stoptb.databinding.ItemCtReadonlyBinding
import org.piramalswasthya.stoptb.helpers.QuestionRenderer
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.ui.counselling_activity.ActionType
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType

/**
 * RecyclerView adapter for dynamic counselling form questions.
 *
 * Responsibilities:
 *   - Map questionType → ViewHolder type
 *   - Inflate the correct item layout
 *   - Compute the display prefix (numbered for non-TEXT questions)
 *   - Delegate all actual rendering to QuestionRenderer
 *
 * All binding logic lives in QuestionRenderer so it can be reused outside this list.
 */
class CounsellingDynamicAdapter(
    private var questions: List<CounsellingQuestionDto>,
    private val onValueChanged: (CounsellingQuestionDto) -> Unit,
    private var isEditable: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    companion object {
        private const val TYPE_TEXT = 1
        private const val TYPE_RADIO = 2
        private const val TYPE_CHECKBOX_MULTI = 3
        private const val TYPE_DATE = 4
        private const val TYPE_DROPDOWN = 5
        // Contact Tracing types — additive, Counselling schemas never produce these strings.
        private const val TYPE_NUMBER = 6
        private const val TYPE_READONLY = 7
        private const val TYPE_NUMBER_PICKER = 8
        private const val TYPE_CHECKBOX = 9
        private const val  CT_RELATIONSHIP_UUID  = "CCT_RELATIONSHIP"
        private const val  CT_NO_OF_CONTACTS_UUID  = "CCT_NO_OF_CONTACTS"
        private const val TFU_REGISTRATION_DATE_UUID = "TFU_REGISTRATION_DATE"
    }
    private var relationshipCountFieldIds: Set<Int> = emptySet()

    private var visibleQuestions: List<CounsellingQuestionDto> = computeVisibleQuestions(questions)
    private var lastSnapshot: Map<Int, Triple<Any?, Boolean, String?>> = snapshotOf(questions)

    private fun snapshotOf(list: List<CounsellingQuestionDto>): Map<Int, Triple<Any?, Boolean, String?>> =
        list.associate { it.questionId to Triple(it.value, it.visible, it.errorMessage) }

    private fun computeVisibleQuestions(all: List<CounsellingQuestionDto>): List<CounsellingQuestionDto> {
        relationshipCountFieldIds = all
            .firstOrNull { it.questionUuid == CT_RELATIONSHIP_UUID }
            ?.options
            .orEmpty()
            .flatMap { it.conditions.orEmpty() }
            .filter { it.actionType == ActionType.SHOW_QUESTION.value }
            .mapNotNull { it.targetQuestionId }
            .toSet()

        return all
            .filter { it.visible && it.questionId !in relationshipCountFieldIds }
            .sortedBy { it.displayOrder }
    }
    fun submitList(newList: List<CounsellingQuestionDto>, editable: Boolean = true) {
        questions = newList
        val editableChanged = editable != isEditable
        isEditable = editable
        val newVisible = computeVisibleQuestions(questions)

        if (editableChanged) {
            visibleQuestions = newVisible
            lastSnapshot = snapshotOf(questions)
            notifyDataSetChanged()
            return
        }

        val oldVisible = visibleQuestions
        val oldSnapshot = lastSnapshot
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldVisible.size
            override fun getNewListSize() = newVisible.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                oldVisible[oldPos].questionId == newVisible[newPos].questionId
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val newQ = newVisible[newPos]
                val prior = oldSnapshot[newQ.questionId] ?: return false
                if (prior != Triple(newQ.value, newQ.visible, newQ.errorMessage)) return false

                if (newQ.questionUuid == CT_RELATIONSHIP_UUID ) {
                    val contributorChanged = relationshipCountFieldIds.any { id ->
                        val contributor = questions.firstOrNull { it.questionId == id }
                        contributor != null && oldSnapshot[id] != Triple(contributor.value, contributor.visible, contributor.errorMessage)
                    }
                    if (contributorChanged) return false
                }
                return true
            }
        })
        visibleQuestions = newVisible
        lastSnapshot = snapshotOf(questions)
        diffResult.dispatchUpdatesTo(this)
    }
    private fun refreshNoOfContactsIfNeeded(updated: CounsellingQuestionDto) {
        if (updated.questionUuid != CT_NO_OF_CONTACTS_UUID && updated.questionId !in relationshipCountFieldIds) return
        val position = visibleQuestions.indexOfFirst { it.questionUuid == CT_NO_OF_CONTACTS_UUID }
        if (position >= 0) notifyItemChanged(position)
    }


    override fun getItemViewType(position: Int): Int {
        return when (QuestionType.from(visibleQuestions[position].questionType)) {
            QuestionType.TEXT -> TYPE_TEXT
            QuestionType.RADIO -> TYPE_RADIO
            QuestionType.CHECKBOX_MULTI-> TYPE_CHECKBOX_MULTI
            QuestionType.DATE -> TYPE_DATE
            QuestionType.DROPDOWN -> TYPE_DROPDOWN
            QuestionType.CHECKBOX -> TYPE_CHECKBOX
            QuestionType.NUMBER -> TYPE_NUMBER
            QuestionType.READONLY_NUMBER,
            QuestionType.READONLY_TEXT -> TYPE_READONLY
            QuestionType.NUMBER_PICKER -> TYPE_NUMBER_PICKER
            QuestionType.MCQ,
            null -> TYPE_TEXT
        }
    }

    // rebinding the rest of the list, so an unrelated row's focus/keyboard isn't disturbed.
    fun notifyQuestionUpdated(questionId: Int) {
        val index = visibleQuestions.indexOfFirst { it.questionId == questionId }
        if (index != -1) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT -> TextViewHolder(ItemCounsellingTextBinding.inflate(inflater, parent, false))
            TYPE_RADIO -> RadioViewHolder(ItemCounsellingRadioBinding.inflate(inflater, parent, false))
            TYPE_CHECKBOX_MULTI, TYPE_CHECKBOX -> McqViewHolder(ItemCounsellingMcqBinding.inflate(inflater, parent, false))
            TYPE_DATE -> DateViewHolder(ItemCounsellingDateBinding.inflate(inflater, parent, false))
            TYPE_DROPDOWN -> DropdownViewHolder(ItemCounsellingDropdownBinding.inflate(inflater, parent, false))
            TYPE_NUMBER -> NumberViewHolder(ItemCtNumberBinding.inflate(inflater, parent, false))
            TYPE_READONLY -> ReadOnlyViewHolder(ItemCtReadonlyBinding.inflate(inflater, parent, false))
            TYPE_NUMBER_PICKER -> NumberPickerViewHolder(ItemCtNumberPickerBinding.inflate(inflater, parent, false))
            else -> TextViewHolder(ItemCounsellingTextBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val q = visibleQuestions[position]
        val questionNumber = visibleQuestions.take(position + 1).count { it.questionType != "TEXT" }
        val prefix = if (q.questionType != "TEXT") "$questionNumber. " else ""

        when (holder) {
            is TextViewHolder -> holder.bind(q, prefix)
            is RadioViewHolder -> holder.bind(q, prefix)
            is McqViewHolder -> holder.bind(q, prefix)
            is DateViewHolder -> holder.bind(q, prefix)
            is DropdownViewHolder -> holder.bind(q, prefix)
            is NumberViewHolder -> holder.bind(q, prefix)
            is ReadOnlyViewHolder -> holder.bind(q, prefix)
            is NumberPickerViewHolder -> holder.bind(q, prefix)
        }
    }

    override fun getItemCount(): Int = visibleQuestions.size

    inner class TextViewHolder(private val binding: ItemCounsellingTextBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) {
            if (q.questionUuid == CT_NO_OF_CONTACTS_UUID) {
                QuestionRenderer.showComputedNoOfContacts(binding, q, prefix, questions)
            } else if (q.questionUuid == TFU_REGISTRATION_DATE_UUID) {
                QuestionRenderer.showTextView(binding, q, prefix, false, onValueChanged)
            } else {
                QuestionRenderer.showTextView(binding, q, prefix, isEditable) { updated ->
                    onValueChanged(updated)
                    refreshNoOfContactsIfNeeded(updated)
                }
            }
        }
    }

    inner class RadioViewHolder(private val binding: ItemCounsellingRadioBinding) :
        RecyclerView.ViewHolder(binding.root) {
            val errorMsg = binding.tvError
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showRadio(binding, q, prefix, isEditable, onValueChanged)
    }

    inner class McqViewHolder(private val binding: ItemCounsellingMcqBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showMCQ(binding, q, prefix, isEditable, { updated ->
                onValueChanged(updated)
                refreshNoOfContactsIfNeeded(updated)
            }, questions)
    }

    inner class DateViewHolder(private val binding: ItemCounsellingDateBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showDate(binding, q, prefix, isEditable, onValueChanged)
    }
    inner class DropdownViewHolder(private val binding: ItemCounsellingDropdownBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showDropdown(binding, q, prefix, isEditable, onValueChanged)
    }

    inner class NumberViewHolder(private val binding: ItemCtNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showNumber(binding, q, prefix, isEditable, onValueChanged)
    }

    inner class ReadOnlyViewHolder(private val binding: ItemCtReadonlyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showReadOnly(binding, q, prefix)
    }

    inner class NumberPickerViewHolder(private val binding: ItemCtNumberPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showNumberPicker(binding, q, prefix, isEditable, onValueChanged)
    }
}
