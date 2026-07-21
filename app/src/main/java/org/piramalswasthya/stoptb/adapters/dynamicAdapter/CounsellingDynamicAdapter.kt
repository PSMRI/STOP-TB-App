package org.piramalswasthya.stoptb.adapters.dynamicAdapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDateBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDropdownBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingMcqBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingRadioBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingTextBinding
import org.piramalswasthya.stoptb.databinding.ItemCtNumberBinding
import org.piramalswasthya.stoptb.databinding.ItemCtReadonlyBinding
import org.piramalswasthya.stoptb.helpers.QuestionRenderer
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto

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
        private const val TYPE_MCQ = 3
        private const val TYPE_DATE = 4
        private const val TYPE_DROPDOWN = 5
        // Contact Tracing types — additive, Counselling schemas never produce these strings.
        private const val TYPE_NUMBER = 6
        private const val TYPE_READONLY = 7
    }

    private var visibleQuestions: List<CounsellingQuestionDto> =
        questions.filter { it.visible }.sortedBy { it.displayOrder }


    fun submitList(newList: List<CounsellingQuestionDto>, editable: Boolean = true) {
        questions = newList
        isEditable = editable
        visibleQuestions = questions.filter { it.visible }.sortedBy { it.displayOrder }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (visibleQuestions[position].questionType) {
        "TEXT" -> TYPE_TEXT
        "RADIO" -> TYPE_RADIO
        "MCQ" -> TYPE_MCQ
        "DATE" -> TYPE_DATE
        "DROPDOWN" -> TYPE_DROPDOWN
        "MCQ", "CHECKBOX" -> TYPE_MCQ
        "NUMBER" -> TYPE_NUMBER
        "READONLY_NUMBER", "READONLY_TEXT" -> TYPE_READONLY
        else -> TYPE_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT -> TextViewHolder(ItemCounsellingTextBinding.inflate(inflater, parent, false))
            TYPE_RADIO -> RadioViewHolder(ItemCounsellingRadioBinding.inflate(inflater, parent, false))
            TYPE_MCQ -> McqViewHolder(ItemCounsellingMcqBinding.inflate(inflater, parent, false))
            TYPE_DATE -> DateViewHolder(ItemCounsellingDateBinding.inflate(inflater, parent, false))
            TYPE_DROPDOWN -> DropdownViewHolder(ItemCounsellingDropdownBinding.inflate(inflater, parent, false))
            TYPE_NUMBER -> NumberViewHolder(ItemCtNumberBinding.inflate(inflater, parent, false))
            TYPE_READONLY -> ReadOnlyViewHolder(ItemCtReadonlyBinding.inflate(inflater, parent, false))
            else -> TextViewHolder(ItemCounsellingTextBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val q = visibleQuestions[position]
        // Non-TEXT questions get a sequential number prefix ("1. ", "2. ", …).
        // TEXT questions are treated as plain input fields without a number.
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
        }
    }

    override fun getItemCount(): Int = visibleQuestions.size

    inner class TextViewHolder(private val binding: ItemCounsellingTextBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(q: CounsellingQuestionDto, prefix: String) =
            QuestionRenderer.showTextView(binding, q, prefix, isEditable, onValueChanged)
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
            QuestionRenderer.showMCQ(binding, q, prefix, isEditable, onValueChanged)
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
}
