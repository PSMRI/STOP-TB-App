package org.piramalswasthya.stoptb.helpers

import android.app.DatePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.textfield.TextInputLayout
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDateBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingDropdownBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingMcqBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingRadioBinding
import org.piramalswasthya.stoptb.databinding.ItemCounsellingTextBinding
import org.piramalswasthya.stoptb.databinding.ItemCtNumberBinding
import org.piramalswasthya.stoptb.databinding.ItemCtNumberPickerBinding
import org.piramalswasthya.stoptb.databinding.ItemCtReadonlyBinding
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingOptionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.ui.counselling_activity.ActionType
import org.piramalswasthya.stoptb.utils.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


object QuestionRenderer {

    fun showLabel(tvQuestion: TextView, question: CounsellingQuestionDto, prefix: String = "") {
        tvQuestion.text = buildLabel(question, prefix)
    }

    fun showLabel(til: TextInputLayout, question: CounsellingQuestionDto, prefix: String = "") {
        til.hint = buildLabel(question, prefix)
    }

    private fun buildLabel(question: CounsellingQuestionDto, prefix: String): String {
        val mandatory = if (question.isMandatory) "\u00A0*" else ""
        return "$prefix${question.questionText}$mandatory"
    }

    // ?? Text input
    fun showTextView(
        binding: ItemCounsellingTextBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tilInput, question, prefix)
        binding.tilInput.error = question.errorMessage
        binding.etInput.isEnabled = isEditable
        binding.etInput.filters = arrayOf(LatinInputFilter())

        if (question.maxLength != null) {
            binding.tilInput.isCounterEnabled = true
            binding.tilInput.counterMaxLength = question.maxLength
        } else {
            binding.tilInput.isCounterEnabled = false
        }

        val oldWatcher = binding.etInput.tag as? TextWatcher
        if (oldWatcher != null) binding.etInput.removeTextChangedListener(oldWatcher)

        var isProgrammaticSet = false
        val newValue = question.value?.toString() ?: ""
        if (binding.etInput.text?.toString() != newValue) {
            isProgrammaticSet = true
            binding.etInput.setText(newValue)
            binding.etInput.setSelection(newValue.length)
            isProgrammaticSet = false
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticSet) return
                question.value = s?.toString()
                onValueChanged(question)
                binding.tilInput.error = question.errorMessage
            }
        }
        binding.etInput.addTextChangedListener(watcher)
        binding.etInput.tag = watcher
        binding.etInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                scrollToView(v)
            }
        }
        binding.etInput.setOnClickListener { v ->
            scrollToView(v)
        }
        if (binding.etInput.hasFocus()) {
            scrollToView(binding.etInput)
        }
        binding.tvError.visibility = View.GONE
    }

    // Recomputes CCT_NO_OF_CONTACTS as the live sum of CCT_RELATIONSHIP's linked count fields, treating empty/non-numeric values as 0.
    fun showComputedNoOfContacts(
        binding: ItemCounsellingTextBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        allQuestions: List<CounsellingQuestionDto>
    ) {
        val countFieldIds = allQuestions
            .firstOrNull { it.questionUuid == "CCT_RELATIONSHIP" }
            ?.options
            .orEmpty()
            .flatMap { it.conditions.orEmpty() }
            .filter { it.actionType == ActionType.SHOW_QUESTION.value }
            .mapNotNull { it.targetQuestionId }
            .toSet()

        question.value = allQuestions
            .filter { it.questionId in countFieldIds }
            .sumOf { it.value?.toString()?.toIntOrNull() ?: 0 }
            .toString()

        showTextView(binding, question, prefix, false, {})
    }

    private fun scrollToView(v: View) {
        v.postDelayed({
            var parentView = v.parent
            while (parentView != null) {
                if (parentView is androidx.core.widget.NestedScrollView) {
                    val rect = android.graphics.Rect()
                    v.getDrawingRect(rect)
                    try {
                        parentView.offsetDescendantRectToMyCoords(v, rect)
                        val scrollY = (rect.top - (50 * v.resources.displayMetrics.density).toInt()).coerceAtLeast(0)
                        parentView.smoothScrollTo(0, scrollY)
                    } catch (e: IllegalArgumentException) {
                        parentView.requestChildFocus(v, v)
                    }
                    break
                }
                parentView = parentView.parent
            }
        }, 200)
    }


    // ?? Radio (single-select)
    fun showRadio(
        binding: ItemCounsellingRadioBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tvQuestion, question, prefix)

        val container: FlexboxLayout = binding.rgOptions
        container.removeAllViews()

        val density = binding.root.context.resources.displayMetrics.density
        val marginEndPx = (16 * density).toInt()
        val marginBottomPx = (4 * density).toInt()

        question.options?.sortedBy { it.displayOrder }?.forEach { opt ->
            val rb = RadioButton(binding.root.context).apply {
                text = opt.optionLabel
                tag = opt.optionValue
                isChecked = question.value == opt.optionValue
                isEnabled = isEditable
                layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).also { lp ->
                    lp.setMargins(0, 0, marginEndPx, marginBottomPx)
                }
                setOnClickListener {
                    if (!isEditable) return@setOnClickListener
                    // Deselect every sibling, then mark this one checked
                    for (i in 0 until container.childCount) {
                        (container.getChildAt(i) as? RadioButton)?.isChecked = false
                    }
                    isChecked = true
                    question.value = opt.optionValue
                    onValueChanged(question)
                }
            }
            container.addView(rb)
        }
        if (!question.errorMessage.isNullOrEmpty()) {
            binding.tvError.text = question.errorMessage
            binding.tvError.visibility = View.VISIBLE
        } else {
            binding.tvError.visibility = View.GONE
        }
    }

    fun showMCQ(
        binding: ItemCounsellingMcqBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit,
        allQuestions: List<CounsellingQuestionDto> = emptyList()
    ) {
        val isSingleCheckbox = question.questionType == "CHECKBOX" && question.options?.size == 1

        if (isSingleCheckbox) {
            binding.tvQuestion.visibility = View.GONE
        } else {
            binding.tvQuestion.visibility = View.VISIBLE
            showLabel(binding.tvQuestion, question, prefix)
        }
        binding.llCheckboxes.removeAllViews()

        val currentValues = (question.value as? List<*>)
            ?.filterIsInstance<String>()
            ?.toMutableList()
            ?: mutableListOf()

        // Inline TextField shown only for the Relationship question in the Community form.
        val showInline = question.questionUuid == "CCT_RELATIONSHIP"

        question.options?.sortedBy { it.displayOrder }?.forEach { opt ->
            val isChecked = currentValues.contains(opt.optionValue)
            val cb = CheckBox(binding.root.context).apply {
                if (isSingleCheckbox) {
                    text = buildLabel(question, prefix)
                    textSize = 16f
                } else {
                    text = opt.optionLabel
                }
                this.isChecked = isChecked
                isEnabled = isEditable
                setOnCheckedChangeListener { _, checked ->
                    if (!isEditable) return@setOnCheckedChangeListener
                    if (checked) {
                        if (!currentValues.contains(opt.optionValue)) currentValues.add(opt.optionValue)
                    } else {
                        currentValues.remove(opt.optionValue)
                    }
                    question.value = currentValues.toList()

                    if (showInline && !checked) {
                        opt.conditions.orEmpty()
                            .filter { it.actionType == ActionType.SHOW_QUESTION.value }
                            .mapNotNull { it.targetQuestionId }
                            .forEach { targetId ->
                                allQuestions.firstOrNull { it.questionId == targetId }?.value = null
                            }
                    }

                    onValueChanged(question)
                }
            }

            if (showInline) {
                binding.llCheckboxes.addView(
                    buildInlineOptionRow(binding.root.context, cb, opt, isChecked, allQuestions, isEditable, onValueChanged),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                binding.llCheckboxes.addView(cb)
            }
        }
        if (!question.errorMessage.isNullOrEmpty()) {
            binding.tvError.text = question.errorMessage
            binding.tvError.visibility = View.VISIBLE
        } else {
            binding.tvError.visibility = View.GONE
        }
    }

    // Renders one CCT_RELATIONSHIP option's checkbox plus its SHOW_QUESTION targets (e.g. count Text field, or label+count for "Other") when checked.
    private fun buildInlineOptionRow(
        context: android.content.Context,
        checkbox: CheckBox,
        opt: CounsellingOptionDto,
        isChecked: Boolean,
        allQuestions: List<CounsellingQuestionDto>,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(checkbox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (isChecked) {
            opt.conditions.orEmpty()
                .filter { it.actionType == ActionType.SHOW_QUESTION.value }
                .mapNotNull { it.targetQuestionId }
                .mapNotNull { targetId -> allQuestions.firstOrNull { it.questionId == targetId } }
                .forEach { target ->
                    val fieldBinding = ItemCounsellingTextBinding.inflate(LayoutInflater.from(context), container, false)
                    showTextView(fieldBinding, target, "", isEditable, onValueChanged)
                    container.addView(fieldBinding.root, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ))
                }
        }
        return container
    }


    fun showDate(
        binding: ItemCounsellingDateBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tilDate, question, prefix)
        binding.tilDate.error = question.errorMessage
        binding.etDate.setText(question.value?.toString() ?: "")
        binding.etDate.isEnabled = isEditable

        binding.etDate.setOnClickListener(null)
        if (isEditable) {
            binding.etDate.setOnClickListener {

            val cal = Calendar.getInstance()

            val dpd = DatePickerDialog(
                binding.root.context,
                { _, year, month, day ->
                    val selected = Calendar.getInstance().apply {
                        set(year, month, day)
                    }

                    val formatted = SimpleDateFormat(
                        "dd-MM-yyyy",
                        Locale.ENGLISH
                    ).format(selected.time)

                    question.value = formatted
                    binding.etDate.setText(formatted)
                    onValueChanged(question)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )

            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

            question.validations?.forEach { validation ->
                when (validation.validationType) {

                    "MIN_DATE" -> {
                        if (validation.validationParam.equals("TODAY", true)) {
                            dpd.datePicker.minDate = System.currentTimeMillis()
                        } else {
                            try {
                                apiDateFormat.parse(validation.validationParam)?.let {
                                    dpd.datePicker.minDate = it.time
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }

                    "MAX_DATE" -> {
                        if (validation.validationParam.equals("TODAY", true)) {
                            dpd.datePicker.maxDate = System.currentTimeMillis()
                        } else {
                            try {
                                apiDateFormat.parse(validation.validationParam)?.let {
                                    dpd.datePicker.maxDate = it.time
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            dpd.show()
            }
        }
        binding.tvError.visibility = View.GONE
    }

    // Dropdown (single-select, from a popup list)
    fun showDropdown(
        binding: ItemCounsellingDropdownBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tilDropdown, question, prefix)
        binding.tilDropdown.error = question.errorMessage
        binding.tilDropdown.isEnabled = isEditable
        binding.actDropdown.isEnabled = isEditable

        val options = question.options?.sortedBy { it.displayOrder } ?: emptyList()
        val labels = options.map { it.optionLabel }

        binding.actDropdown.setAdapter(
            ArrayAdapter(binding.root.context, android.R.layout.simple_list_item_1, labels)
        )

        val selectedOption = options.firstOrNull { it.optionValue == question.value }
        binding.actDropdown.setText(selectedOption?.optionLabel ?: "", false)

        binding.actDropdown.setOnItemClickListener { _, _, position, _ ->
            if (!isEditable) return@setOnItemClickListener
            val opt = options[position]
            question.value = opt.optionValue
            onValueChanged(question)
        }

        if (!question.errorMessage.isNullOrEmpty()) {
            binding.tvError.text = question.errorMessage
            binding.tvError.visibility = View.VISIBLE
        } else {
            binding.tvError.visibility = View.GONE
        }
    }

    // Numeric-only input (age, hours, counts). New — no existing function covers this type.
    fun showNumber(
        binding: ItemCtNumberBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tilNumber, question, prefix)
        binding.tilNumber.error = question.errorMessage
        binding.etNumber.isEnabled = isEditable

        val exactLength = question.validations
            ?.firstOrNull { it.validationType == "EXACT_LENGTH" }
            ?.validationParam?.toIntOrNull()
        binding.etNumber.filters = if (exactLength != null) {
            arrayOf(android.text.InputFilter.LengthFilter(exactLength))
        } else {
            emptyArray()
        }

        val oldWatcher = binding.etNumber.tag as? TextWatcher
        if (oldWatcher != null) binding.etNumber.removeTextChangedListener(oldWatcher)

        var isProgrammaticSet = false
        val newValue = question.value?.toString() ?: ""
        if (binding.etNumber.text?.toString() != newValue) {
            isProgrammaticSet = true
            binding.etNumber.setText(newValue)
            binding.etNumber.setSelection(newValue.length)
            isProgrammaticSet = false
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticSet) return
                question.value = s?.toString()
                onValueChanged(question)
            }
        }
        binding.etNumber.addTextChangedListener(watcher)
        binding.etNumber.tag = watcher
        binding.etNumber.setOnFocusChangeListener { v, hasFocus -> if (hasFocus) scrollToView(v) }
        binding.tvError.visibility = View.GONE
    }

    // Read-only auto-populated display (GPS lat/long, DigiPin, timestamp, visit number).
    // New — no existing function covers this type; nothing here is ever edited or validated.
    fun showReadOnly(
        binding: ItemCtReadonlyBinding,
        question: CounsellingQuestionDto,
        prefix: String
    ) {
        binding.tvLabel.text = "$prefix${question.questionText}"
        binding.tvValue.text = question.value?.toString()?.takeIf { it.isNotBlank() } ?: "—"
    }

    // show NumberPicker (- 1 +)
    fun showNumberPicker(
        binding: ItemCtNumberPickerBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tvQuestion, question, prefix)
        binding.tvError.text = question.errorMessage
        binding.tvError.visibility = if (question.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE

        val (min, max) = numberPickerRange(question)

        var current = question.value?.toString()?.toIntOrNull()
        if (current == null || current !in min..max) {
            current = 1.coerceIn(min, max)
            question.value = current.toString()
        }
        binding.tvValue.text = current.toString()

        binding.btnDecrement.isEnabled = isEditable && current > min
        binding.btnIncrement.isEnabled = isEditable && current < max

        binding.btnDecrement.setOnClickListener {
            val value = question.value?.toString()?.toIntOrNull() ?: min
            if (value > min) {
                question.value = (value - 1).toString()
                onValueChanged(question)
            }
        }
        binding.btnIncrement.setOnClickListener {
            val value = question.value?.toString()?.toIntOrNull() ?: min
            if (value < max) {
                question.value = (value + 1).toString()
                onValueChanged(question)
            }
        }
    }

    // Determines a numeric field's valid range by testing sequential integers against its regex, rather than parsing the pattern directly.
    private fun numberPickerRange(question: CounsellingQuestionDto): Pair<Int, Int> {
        val regex = question.validations
            ?.firstOrNull { it.validationType == "REGEX" }
            ?.validationParam
            ?.let { runCatching { it.toRegex() }.getOrNull() }
            ?: return 0 to 100

        val matches = (0..1000).filter { regex.matches(it.toString()) }
        return if (matches.isEmpty()) 0 to 100 else matches.first() to matches.last()
    }
}