package org.piramalswasthya.stoptb.helpers

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.R
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.textfield.TextInputLayout
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
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


object QuestionRenderer {


    const val CT_RELATIONSHIP_COUNT_PREFIX = "CCT_RELATIONSHIP_COUNT_"
    // Upper bound when probing a picker's REGEX for its range; must cover Area of Shared Space's 5000.
    private const val NUMBER_PICKER_PROBE_MAX = 5000
    // Matches both OCT_AREA_OF_SHARED_SPACE (Occupational) and CCT_AREA_OF_SHARED_SPACE_* (per relationship).
    private const val AREA_OF_SHARED_SPACE_UUID_PART = "AREA_OF_SHARED_SPACE"

    fun showLabel(tvQuestion: TextView, question: CounsellingQuestionDto, prefix: String = "") {
        tvQuestion.text = buildLabel(question, prefix)
    }

    fun showLabel(til: TextInputLayout, question: CounsellingQuestionDto, prefix: String = "") {
        til.hint = buildLabel(question, prefix)
    }

    private fun buildLabel(
        question: CounsellingQuestionDto,
        prefix: String
    ): SpannableString {

        val mandatory = if (question.isMandatory) "\u00A0*" else ""
        val text = "$prefix${question.questionText}$mandatory"

        val spannable = SpannableString(text)

        if (question.isMandatory) {
            val start = text.length - 1
            val end = text.length

            spannable.setSpan(
                ForegroundColorSpan(Color.RED),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    // ?? Text input
    fun showTextView(
        binding: ItemCounsellingTextBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        applyLatinFilter: Boolean = true,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        showLabel(binding.tilInput, question, prefix)
        binding.tilInput.error = question.errorMessage
        binding.etInput.isEnabled = isEditable

        val maxLength = question.maxLength
        val filters = mutableListOf<InputFilter>()
        if (applyLatinFilter) filters.add(LatinInputFilter())
        if (maxLength != null) filters.add(InputFilter.LengthFilter(maxLength))
        binding.etInput.filters = filters.toTypedArray()

        if (maxLength != null) {
            binding.tilInput.isCounterEnabled = true
            binding.tilInput.counterMaxLength = maxLength
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

        // Only the count fields contribute; hours / Type of Space are also SHOW_QUESTION targets of the same options.
        val computedValue = allQuestions
            .filter { it.questionId in countFieldIds && it.questionUuid.startsWith(CT_RELATIONSHIP_COUNT_PREFIX) }
            .sumOf { it.value?.toString()?.toIntOrNull() ?: 0 }
            .toString()

        question.value = computedValue

        if ((computedValue.toIntOrNull() ?: 0) > 0 && question.errorMessage != null) {
            question.errorMessage = null
        }

        showTextView(binding, question, prefix, false, applyLatinFilter = false) {}
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
                        inlineDependants(listOf(opt), allQuestions).forEach { it.value = null }
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

        if (isChecked) addInlineTargets(context, container, opt, allQuestions, isEditable, onValueChanged)
        return container
    }

    // Adds opt's SHOW_QUESTION targets to container, then recurses into each target's selected options,
    // so nested dependants (Type of Space -> Approximate Area of Shared Space) stay under their relationship.
    private fun addInlineTargets(
        context: android.content.Context,
        container: LinearLayout,
        opt: CounsellingOptionDto,
        allQuestions: List<CounsellingQuestionDto>,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        showQuestionTargets(opt, allQuestions).forEach { target ->
            // Render each target by its own questionType (e.g. Type of Space is RADIO, hours is NUMBER_PICKER).
            val fieldView = when (QuestionType.from(target.questionType)) {
                QuestionType.RADIO -> ItemCounsellingRadioBinding.inflate(inflater, container, false).also {
                    showRadio(it, target, "", isEditable, onValueChanged)
                }.root
                QuestionType.NUMBER_PICKER -> ItemCtNumberPickerBinding.inflate(inflater, container, false).also {
                    showNumberPicker(it, target, "", isEditable, onValueChanged)
                }.root
                QuestionType.NUMBER -> ItemCtNumberBinding.inflate(inflater, container, false).also {
                    showNumber(it, target, "", isEditable, onValueChanged)
                }.root
                else -> ItemCounsellingTextBinding.inflate(inflater, container, false).also {
                    showTextView(it, target, "", isEditable, applyLatinFilter = false, onValueChanged = onValueChanged)
                }.root
            }
            container.addView(fieldView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val selectedValues = when (val v = target.value) {
                is List<*> -> v.mapNotNull { it?.toString() }
                null -> emptyList()
                else -> listOf(v.toString())
            }
            target.options.orEmpty()
                .filter { it.optionValue in selectedValues }
                .forEach { addInlineTargets(context, container, it, allQuestions, isEditable, onValueChanged) }
        }
    }

    private fun showQuestionTargets(
        opt: CounsellingOptionDto,
        allQuestions: List<CounsellingQuestionDto>
    ): List<CounsellingQuestionDto> =
        opt.conditions.orEmpty()
            .filter { it.actionType == ActionType.SHOW_QUESTION.value }
            .mapNotNull { it.targetQuestionId }
            .mapNotNull { targetId -> allQuestions.firstOrNull { it.questionId == targetId } }
            // Conditions come back from Room in no guaranteed order; the schema's displayOrder decides the layout.
            .sortedBy { it.displayOrder }

    // Every question rendered inline under the given options, at any depth (count, hours, Type of Space, Area of Shared Space...).
    fun inlineDependants(
        options: List<CounsellingOptionDto>?,
        allQuestions: List<CounsellingQuestionDto>
    ): List<CounsellingQuestionDto> =
        options.orEmpty()
            .flatMap { showQuestionTargets(it, allQuestions) }
            .flatMap { listOf(it) + inlineDependants(it.options, allQuestions) }


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

    // Dropdown — single-select from a popup list, or multi-select from a checkbox dialog for DROPDOWN_MULTI.
    // Shares the same layout/binding as single DROPDOWN; only the tap behaviour and stored value differ.
    fun showDropdown(
        binding: ItemCounsellingDropdownBinding,
        question: CounsellingQuestionDto,
        prefix: String,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit,
        allQuestions: List<CounsellingQuestionDto> = emptyList()
    ) {
        showLabel(binding.tilDropdown, question, prefix)
        binding.tilDropdown.error = question.errorMessage
        binding.tilDropdown.isEnabled = isEditable
        binding.actDropdown.isEnabled = isEditable

        val options = question.options?.sortedBy { it.displayOrder } ?: emptyList()
        val isMultiSelect = question.questionType == QuestionType.DROPDOWN_MULTI.value

        if (isMultiSelect) {
            binding.actDropdown.setOnItemClickListener(null)
            binding.actDropdown.setAdapter(null)

            val currentValues = (question.value as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()
            binding.actDropdown.setText(
                options.filter { it.optionValue in currentValues }.joinToString(", ") { it.optionLabel },
                false
            )

            binding.actDropdown.setOnClickListener {
                if (!isEditable) return@setOnClickListener
                val latestValues = (question.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val checkedItems = BooleanArray(options.size) { i -> options[i].optionValue in latestValues }

                AlertDialog.Builder(binding.root.context)
                    .setTitle(buildLabel(question, prefix))
                    .setMultiChoiceItems(options.map { it.optionLabel }.toTypedArray(), checkedItems) { _, which, checked ->
                        checkedItems[which] = checked
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val selected = options.filterIndexed { i, _ -> checkedItems[i] }
                        // Same as unchecking in showMCQ: a deselected relationship drops its dependent answers.
                        if (question.questionUuid == "CCT_RELATIONSHIP") {
                            inlineDependants(options.filter { it !in selected }, allQuestions).forEach { it.value = null }
                        }
                        question.value = selected.map { it.optionValue }
                        binding.actDropdown.setText(selected.joinToString(", ") { it.optionLabel }, false)
                        onValueChanged(question)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } else {
            binding.actDropdown.setOnClickListener(null)

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
        }

        // Hide tvError to avoid duplicating the TextInputLayout's error message.
        binding.tvError.visibility = View.GONE

        showDropdownInlineTargets(binding, question, options, allQuestions, isEditable, onValueChanged)
    }

    // DROPDOWN_MULTI counterpart of showMCQ's inline rows: the adapter keeps CCT_RELATIONSHIP's dependants out of the
    // main list, so they must be drawn here — one block per selected option, headed by its label.
    private fun showDropdownInlineTargets(
        binding: ItemCounsellingDropdownBinding,
        question: CounsellingQuestionDto,
        options: List<CounsellingOptionDto>,
        allQuestions: List<CounsellingQuestionDto>,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        val container = binding.llInline
        container.removeAllViews()

        val selectedValues = (question.value as? List<*>)?.filterIsInstance<String>().orEmpty()
        val selectedOptions = if (question.questionUuid == "CCT_RELATIONSHIP") {
            options.filter { it.optionValue in selectedValues }
        } else {
            emptyList()
        }
        container.visibility = if (selectedOptions.isEmpty()) View.GONE else View.VISIBLE

        val context = binding.root.context
        val density = context.resources.displayMetrics.density
        selectedOptions.forEach { opt ->
            val header = TextView(context).apply {
                text = opt.optionLabel
                setTextAppearance(R.style.TextAppearance_Material3_TitleSmall)
                setPadding(0, (12 * density).toInt(), 0, 0)
            }
            container.addView(header, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addInlineTargets(context, container, opt, allQuestions, isEditable, onValueChanged)
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
        // Area of Shared Space spans 50–5000, so it also gets a typed field; every other picker stays stepper-only.
        val allowTyping = question.questionUuid.contains(AREA_OF_SHARED_SPACE_UUID_PART)

        var current = question.value?.toString()?.toIntOrNull()
        // A typed out-of-range value is kept so its validation error shows, instead of being silently reset.
        if (current == null || (current !in min..max && !allowTyping)) {
            current = 1.coerceIn(min, max)
            question.value = current.toString()
        }
        binding.tvValue.text = current.toString()
        binding.tvValue.visibility = if (allowTyping) View.GONE else View.VISIBLE
        binding.etValue.visibility = if (allowTyping) View.VISIBLE else View.GONE
        applyNumberPickerUnit(binding, question)

        binding.btnDecrement.isEnabled = isEditable && current > min
        binding.btnIncrement.isEnabled = isEditable && current < max

        binding.btnDecrement.setOnClickListener {
            val value = question.value?.toString()?.toIntOrNull() ?: min
            if (value > min) {
                question.value = (value - 1).coerceAtMost(max).toString()
                onValueChanged(question)
            }
        }
        binding.btnIncrement.setOnClickListener {
            val value = question.value?.toString()?.toIntOrNull() ?: min
            if (value < max) {
                question.value = (value + 1).coerceAtLeast(min).toString()
                onValueChanged(question)
            }
        }

        if (allowTyping) bindNumberPickerInput(binding, question, min, max, isEditable, onValueChanged)
    }

    // Typed entry for the picker. Keystrokes only update question.value and the range error; onValueChanged (which
    // re-evaluates the form and rebinds this row, dropping the keyboard) fires once on Done or focus loss.
    private fun bindNumberPickerInput(
        binding: ItemCtNumberPickerBinding,
        question: CounsellingQuestionDto,
        min: Int,
        max: Int,
        isEditable: Boolean,
        onValueChanged: (CounsellingQuestionDto) -> Unit
    ) {
        val et = binding.etValue
        et.isEnabled = isEditable
        et.filters = arrayOf(InputFilter.LengthFilter(max.toString().length))

        (et.tag as? TextWatcher)?.let { et.removeTextChangedListener(it) }
        val newValue = question.value?.toString() ?: ""
        if (et.text?.toString() != newValue) {
            et.setText(newValue)
            et.setSelection(newValue.length)
        }

        var committedValue = question.value
        fun commit() {
            if (question.value == committedValue) return
            committedValue = question.value
            onValueChanged(question)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                question.value = s?.toString()?.takeIf { it.isNotEmpty() }
                applyNumberPickerRangeError(binding, question, min, max, isEditable)
                applyNumberPickerUnit(binding, question)
            }
        }
        et.addTextChangedListener(watcher)
        et.tag = watcher

        et.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                commit()
            }
            false
        }
        et.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                scrollToView(v)
            } else {
                // Focus is also lost when a rebind removes this row mid-layout (e.g. on Submit); committing then
                // would notify the RecyclerView during layout and crash. Defer, and skip if the view was removed —
                // question.value is already up to date, so nothing is lost.
                v.post { if (v.isAttachedToWindow) commit() }
            }
        }
    }

    // Shows the question's UNIT validation param (e.g. "sq. m.") after the value, once a value is present.
    private fun applyNumberPickerUnit(binding: ItemCtNumberPickerBinding, question: CounsellingQuestionDto) {
        val unit = question.validations
            ?.firstOrNull { it.validationType == "UNIT" }
            ?.validationParam
            ?.takeIf { it.isNotBlank() }
        binding.tvUnit.text = unit
        binding.tvUnit.visibility =
            if (unit != null && !question.value?.toString().isNullOrBlank()) View.VISIBLE else View.GONE
    }

    // Checks a typed value against the same min..max the stepper enforces, live, since the ViewModel only
    // surfaces a new error on Next. Also keeps the steppers' enabled state in sync with the typed value.
    private fun applyNumberPickerRangeError(
        binding: ItemCtNumberPickerBinding,
        question: CounsellingQuestionDto,
        min: Int,
        max: Int,
        isEditable: Boolean
    ) {
        val typed = question.value?.toString()?.toIntOrNull()
        val rangeError = question.validations
            ?.firstOrNull { it.validationType == "REGEX" }?.errorMessage
            ?: "Enter a value between $min and $max."
        question.errorMessage = if (typed != null && typed !in min..max) rangeError else null

        binding.tvError.text = question.errorMessage
        binding.tvError.visibility = if (question.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.btnDecrement.isEnabled = isEditable && typed != null && typed > min
        binding.btnIncrement.isEnabled = isEditable && (typed == null || typed < max)
    }

    // Probing is repeated on every rebind, so ranges are cached per pattern.
    private val numberPickerRangeCache = mutableMapOf<String, Pair<Int, Int>>()

    // Determines a numeric field's valid range by testing sequential integers against its regex, rather than parsing the pattern directly.
    private fun numberPickerRange(question: CounsellingQuestionDto): Pair<Int, Int> {
        val pattern = question.validations
            ?.firstOrNull { it.validationType == "REGEX" }
            ?.validationParam
            ?: return 0 to 100

        return numberPickerRangeCache.getOrPut(pattern) {
            val regex = runCatching { pattern.toRegex() }.getOrNull() ?: return@getOrPut 0 to 100
            val matches = (0..NUMBER_PICKER_PROBE_MAX).filter { regex.matches(it.toString()) }
            if (matches.isEmpty()) 0 to 100 else matches.first() to matches.last()
        }
    }
}