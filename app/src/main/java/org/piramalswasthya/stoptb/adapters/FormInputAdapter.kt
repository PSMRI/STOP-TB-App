package org.piramalswasthya.stoptb.adapters

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.AllCaps
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import org.piramalswasthya.stoptb.utils.scrollToFormValidationError
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.LayoutMultiFileUploadBinding
import org.piramalswasthya.stoptb.databinding.LayoutUploafFormBinding
import org.piramalswasthya.stoptb.databinding.RvItemFormAgePickerViewV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormBtnBinding
import org.piramalswasthya.stoptb.databinding.RvItemFormCheckV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormDatepickerV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormDropdownV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormEditTextV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormHeadlineV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormImageViewV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormNumberPickerBinding
import org.piramalswasthya.stoptb.databinding.RvItemFormRadioV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormTextViewV2Binding
import org.piramalswasthya.stoptb.databinding.RvItemFormTimepickerV2Binding
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.getDateString
import org.piramalswasthya.stoptb.helpers.isInternetAvailable
import org.piramalswasthya.stoptb.model.AgeUnitDTO
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.InputType.AGE_PICKER
import org.piramalswasthya.stoptb.model.InputType.CHECKBOXES
import org.piramalswasthya.stoptb.model.InputType.DATE_PICKER
import org.piramalswasthya.stoptb.model.InputType.DROPDOWN
import org.piramalswasthya.stoptb.model.InputType.EDIT_TEXT
import org.piramalswasthya.stoptb.model.InputType.HEADLINE
import org.piramalswasthya.stoptb.model.InputType.IMAGE_VIEW
import org.piramalswasthya.stoptb.model.InputType.RADIO
import org.piramalswasthya.stoptb.model.InputType.TEXT_VIEW
import org.piramalswasthya.stoptb.model.InputType.TIME_PICKER
import org.piramalswasthya.stoptb.model.InputType.values
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.AgePickerDialog
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel.Companion.isOtpVerified
import org.piramalswasthya.stoptb.utils.HelperUtil
import org.piramalswasthya.stoptb.utils.HelperUtil.findFragmentActivity
import org.piramalswasthya.stoptb.utils.HelperUtil.getAgeStrFromAgeUnit
import org.piramalswasthya.stoptb.utils.HelperUtil.getDobFromAge
import org.piramalswasthya.stoptb.utils.HelperUtil.getLongFromDate
import org.piramalswasthya.stoptb.utils.HelperUtil.updateAgeDTO
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun parseFormDate(value: String?): Calendar? {
    if (value.isNullOrBlank()) return null
    val parsedDate = runCatching {
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).apply { isLenient = false }
            .parse(value.trim())
    }.getOrNull() ?: return null
    return Calendar.getInstance().apply { time = parsedDate }
}

private fun DatePickerDialog.showOnlyOkButton() {
    setOnShowListener {
        getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            visibility = View.VISIBLE
            setText(android.R.string.ok)
            setTextColor(Color.rgb(0, 121, 107))
        }
        getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility = View.GONE
        getButton(DialogInterface.BUTTON_NEUTRAL)?.visibility = View.GONE
    }
}

class FormInputAdapter(
    private val imageClickListener: ImageClickListener? = null,
    private val ageClickListener: AgeClickListener? = null,
    private val sendOtpClickListener: SendOtpClickListener? = null,
    private val formValueListener: FormValueListener? = null,
    var isEnabled: Boolean = true,
    private val selectImageClickListener: SelectUploadImageClickListener? = null,
    private val viewDocumentListner: ViewDocumentOnClick? = null,
    var  fileList: MutableList<Uri>? = null,
) : ListAdapter<FormElement, ViewHolder>(FormInputDiffCallBack) {
    var disableUpload = false

    object FormInputDiffCallBack : DiffUtil.ItemCallback<FormElement>() {
        override fun areItemsTheSame(oldItem: FormElement, newItem: FormElement) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FormElement, newItem: FormElement): Boolean {
            Timber.d("${oldItem.id} error:${oldItem.errorText} value:${oldItem.value}")
            return oldItem.errorText == newItem.errorText &&
                    oldItem.value == newItem.value &&
                    oldItem.isEnabled == newItem.isEnabled &&
                    oldItem.required == newItem.required &&
                    oldItem.title == newItem.title
        }
    }


    class EditTextInputViewHolder private constructor(private val binding: RvItemFormEditTextV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormEditTextV2Binding.inflate(layoutInflater, parent, false)
                return EditTextInputViewHolder(binding)
            }
        }

        fun bind(item: FormElement, isEnabled: Boolean, formValueListener: FormValueListener?) {
            val effectiveEnabled = isEnabled && item.isEnabled
            Timber.d("binding triggered!!! $effectiveEnabled ${item.id}")
            if (!effectiveEnabled) {
                binding.et.isEnabled = false
                binding.et.isClickable = false
                binding.et.isFocusable = false
                binding.et.isFocusableInTouchMode = false
                binding.et.isCursorVisible = false
                binding.tilEditText.endIconDrawable = null
                binding.tilEditText.setEndIconOnClickListener(null)
                handleHintLength(item)
                binding.form = item
                binding.et.setText(item.value)
                binding.tilEditText.isErrorEnabled = item.errorText != null
                binding.tilEditText.error = item.errorText
                binding.executePendingBindings()
                return
            } else {
                binding.et.isEnabled = true
                binding.et.isClickable = true
                binding.et.isFocusable = true
                binding.et.isFocusableInTouchMode = true
                binding.et.isCursorVisible = true
            }
            if (isOtpVerified && item.id == 44 && item.title.equals("Contact Number")) {
                binding.et.isClickable = false
                binding.et.isFocusable = false
            }

            if (item.title.contains("first name", true) ||
                item.title.contains("last name", true) ||
                item.title.contains("father's name", true) ||
                item.title.contains("mother's name", true)
            ) {
//                edittext.setFilters(arrayOf<InputFilter>(AllCaps()))
                val editFilters = binding.et.filters
                var newFilters = arrayOfNulls<InputFilter>(editFilters.size + 1)
                editFilters.forEachIndexed { index, inputFilter ->
                    newFilters[index] = editFilters[index]
                }
                newFilters[editFilters.size] = AllCaps()
//                newFilters.set(editFilters.size, AllCaps())
//                binding.et.filters = arrayOf<InputFilter>(AllCaps())
                binding.et.filters = newFilters
            }
            binding.form = item
            if (item.errorText == null) binding.tilEditText.isErrorEnabled = false
            Timber.d("Bound EditText item ${item.title} with ${item.required}")
            binding.tilEditText.error = item.errorText
            handleHintLength(item)
            if (item.hasSpeechToText) {
                binding.tilEditText.endIconDrawable =
                    AppCompatResources.getDrawable(binding.root.context, R.drawable.ic_mic)
                binding.tilEditText.setEndIconOnClickListener {
                    formValueListener?.onValueChanged(item, Konstants.micClickIndex)
                }
            } else {
                binding.tilEditText.endIconDrawable = null
                binding.tilEditText.setEndIconOnClickListener(null)
            }


            //binding.et.setText(item.value.value)
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(editable: Editable?) {
//                    editable?.length?.let {
//                        if (it > item.etMaxLength) {
////                            editable.delete(item.etMaxLength + 1, it)
//                            "This field cannot have more than ${item.etMaxLength} characters".let {
//                                item.errorText = it
//                                binding.tilEditText.error = it
//                            }
//                            return
//                        } else
//                            item.errorText = null
//                    }
                    item.value = editable?.toString()
                    val emptyError = binding.root.context.getString(R.string.form_input_empty_error)
                    if (!item.value.isNullOrBlank() && item.errorText == emptyError) {
                        item.errorText = null
                        binding.tilEditText.isErrorEnabled = false
                        binding.tilEditText.error = null
                    }
                    Timber.d("editable : $editable Current value : ${item.value}  isNull: ${item.value == null} isEmpty: ${item.value == ""}")
                    formValueListener?.onValueChanged(item, -1)
                    if (item.errorText != binding.tilEditText.error) {
                        binding.tilEditText.isErrorEnabled = item.errorText != null
                        binding.tilEditText.error = item.errorText
                    }
//                    binding.tilEditText.error = null
//                    else if(item.errorText!= null && binding.tilEditText.error==null)
//                        binding.tilEditText.error = item.errorText


//                    if(item.etInputType == InputType.TYPE_CLASS_NUMBER && (item.hasDependants|| item.hasAlertError)){
//                        formValueListener?.onValueChanged(item,-1)
//                    }

//                    editable.let { item.value = it.toString() }
//                    item.value = editable.toString()
//                    Timber.d("Item ET : $item")
//                    if (item.isMobileNumber) {
//                        if (item.etMaxLength == 10) {
//                            if (editable.first().toString()
//                                    .toInt() < 6 || editable.length != item.etMaxLength
//                            ) {
//                                item.errorText = "Invalid Mobile Number !"
//                                binding.tilEditText.error = item.errorText
//                            } else {
//                                item.errorText = null
//                                binding.tilEditText.error = item.errorText
//                            }
//                        } else if (item.etMaxLength == 12) {
//                            if (editable.first().toString()
//                                    .toInt() == 0 || editable.length != item.etMaxLength
//                            ) {
//                                item.errorText = "Invalid ${item.title} !"
//                                binding.tilEditText.error = item.errorText
//                            } else {
//                                item.errorText = null
//                                binding.tilEditText.error = item.errorText
//                            }
//                        }
//                    }
//                else if (item.etInputType == InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) {
//                        val entered = editable.toString().toDouble()
//                        item.minDecimal?.let {
//                            if (entered < it) {
//                                binding.tilEditText.error = "Field value has to be at least $it"
//                                item.errorText = binding.tilEditText.error.toString()
//                            }
//                        }
//                        item.maxDecimal?.let {
//                            if (entered > it) {
//                                binding.tilEditText.error =
//                                    "Field value has to be less than $it"
//                                item.errorText = binding.tilEditText.error.toString()
//                            }
//                        }
//                        if (item.minDecimal != null && item.maxDecimal != null && entered >= item.minDecimal!! && entered <= item.maxDecimal!!) {
//                            binding.tilEditText.error = null
//                            item.errorText = null
//                        }

//                    } else if (item.etInputType == (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL)) {
//                        val age = editable.toString().toLong()
//                        item.min?.let {
//                            if (age < it) {
//                                binding.tilEditText.error = "Field value has to be at least $it"
//                                item.errorText = binding.tilEditText.error.toString()
//                            }
//                        }
//                        item.max?.let {
//                            if (age > it) {
//                                binding.tilEditText.error =
//                                    "Field value has to be less than $it"
//                                item.errorText = binding.tilEditText.error.toString()
//                            }
//                        }
//                        if (item.min != null && item.max != null && age >= item.min!! && age <= item.max!!) {
//                            binding.tilEditText.error = null
//                            item.errorText = null
//                        }
//                    } else {
//                        if (item.errorText != null && editable.isNotBlank()) {
//                            item.errorText = null
//                            binding.tilEditText.error = null
//                        }

//                    }

                }
            }
            binding.et.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus){
                    binding.et.requestFocus()
                    binding.et.addTextChangedListener(textWatcher)
                    val imm =
                        binding.root.context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm!!.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                } else {
                    binding.et.removeTextChangedListener(textWatcher)
                    binding.et.clearFocus()
                    val imm =
                        binding.root.context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                }
            }
            binding.et.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                    v.clearFocus()
                    val imm = v.context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
            binding.et.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && (event.action == KeyEvent.ACTION_UP || event.action == KeyEvent.ACTION_DOWN)) {
                    v.clearFocus()
                    return@OnKeyListener true
                }
                false
            })

//            item.errorText?.also { binding.tilEditText.error = it }
//                ?: run { binding.tilEditText.error = null }
//            val etFilters = mutableListOf<InputFilter>(InputFilter.LengthFilter(item.etMaxLength))
//            binding.et.inputType = item.etInputType
//            if (item.etInputType == InputType.TYPE_CLASS_TEXT && item.allCaps) {
//                etFilters.add(AllCaps())
//                etFilters.add(FormEditTextDefaultInputFilter)
//            }
//            else if(item.etInputType == InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
//                etFilters.add(DecimalDigitsInputFilter)
//            binding.et.filters = etFilters.toTypedArray()
            binding.executePendingBindings()
        }

        private fun handleHintLength(item: FormElement) {
            if (item.title.length > Konstants.editTextHintLimit) {
                binding.tvHint.visibility = View.VISIBLE
                binding.et.hint = null
                binding.tilEditText.hint = null
                binding.tilEditText.isHintEnabled = false
            } else {
                binding.tvHint.visibility = View.GONE
                binding.tilEditText.isHintEnabled = true
            }
        }
    }

    class DropDownInputViewHolder private constructor(private val binding: RvItemFormDropdownV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormDropdownV2Binding.inflate(layoutInflater, parent, false)
                return DropDownInputViewHolder(binding)
            }
        }

        fun bind(item: FormElement, isEnabled: Boolean, formValueListener: FormValueListener?) {
            binding.tilRvDropdown.clearFocus()
            binding.tilEditText.clearFocus()
            binding.et.clearFocus()
            binding.actvRvDropdown.clearFocus()
            binding.actvRvDropdown.showSoftInputOnFocus = false
            binding.form = item
            if (item.errorText == null) {
                binding.tilRvDropdown.error = null
                binding.tilRvDropdown.isErrorEnabled = false
            }
            if (!isEnabled) {
                binding.tilRvDropdown.visibility = View.GONE
                binding.tilEditText.visibility = View.VISIBLE
                binding.et.isFocusable = false
                binding.et.isClickable = false
                binding.executePendingBindings()
                return
            }

            binding.tilRvDropdown.visibility = View.VISIBLE
            binding.tilEditText.visibility = View.GONE
            binding.et.isFocusable = true
            binding.et.isClickable = true
            hideKeyboardImmediately()


            binding.actvRvDropdown.setOnItemClickListener { _, _, index, _ ->
                hideKeyboardWithRetry()
                item.value = item.entries?.get(index)
                Timber.d("Item DD : $item")
//                if (item.hasDependants || item.hasAlertError) {
                formValueListener?.onValueChanged(item, index)
//                }
                binding.tilRvDropdown.isErrorEnabled = item.errorText != null
                binding.tilRvDropdown.error = item.errorText
            }

            binding.actvRvDropdown.setOnClickListener {
                hideKeyboardWithRetry()
            }

            item.errorText?.let { binding.tilRvDropdown.error = it }
            binding.executePendingBindings()

        }

        private fun hideKeyboardImmediately() {
            val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }

        private fun hideKeyboardWithRetry() {
            val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)

            binding.root.postDelayed({
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            }, 50)

            binding.root.postDelayed({
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            }, 200)
        }

    }

    class RadioInputViewHolder private constructor(private val binding: RvItemFormRadioV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormRadioV2Binding.inflate(layoutInflater, parent, false)
                return RadioInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement, isEnabled: Boolean, formValueListener: FormValueListener?
        ) {
            val effectiveEnabled = isEnabled && item.isEnabled
            if (!effectiveEnabled) {
                binding.rg.isClickable = false
                binding.rg.isFocusable = false
            }
//            binding.rg.isEnabled = isEnabled
            binding.invalidateAll()
            binding.form = item

            binding.rg.removeAllViews()

            binding.rg.apply {
                item.entries?.let { items ->
                    orientation = item.orientation ?: LinearLayout.HORIZONTAL
                    weightSum = items.size.toFloat()
                    items.forEach {
                        val rdBtn = RadioButton(this.context)
                        rdBtn.layoutParams = RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.WRAP_CONTENT,
                            RadioGroup.LayoutParams.WRAP_CONTENT,
                            1.0F
                        ).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                        rdBtn.id = View.generateViewId()
                        val colorStateList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            ColorStateList(
                                arrayOf(
                                    intArrayOf(-android.R.attr.state_checked),
                                    intArrayOf(android.R.attr.state_checked)
                                ), intArrayOf(
                                    binding.root.resources.getColor(
                                        android.R.color.darker_gray,
                                        binding.root.context.theme
                                    ),  // disabled
                                    binding.root.resources.getColor(
                                        android.R.color.darker_gray,
                                        binding.root.context.theme
                                    ) // enabled
                                )
                            )
                        } else {
                            ColorStateList(
                                arrayOf(
                                    intArrayOf(-android.R.attr.state_checked),
                                    intArrayOf(android.R.attr.state_checked)
                                ), intArrayOf(
                                    binding.root.resources.getColor(
                                        android.R.color.darker_gray,
                                    ),  // disabled
                                    binding.root.resources.getColor(
                                        android.R.color.darker_gray,
                                    ) // enabled
                                )
                            )
                        }

                        if (!effectiveEnabled) rdBtn.buttonTintList = colorStateList
                        rdBtn.text = it
                        addView(rdBtn)
                    }
                    var applyingProgrammaticSelection = false
                    item.value?.let { selected ->
                        val selectedIndex = item.entries?.indexOfFirst { entry ->
                            entry == selected || entry.equals(selected, ignoreCase = true)
                        } ?: -1
                        if (selectedIndex in 0 until childCount) {
                            (getChildAt(selectedIndex) as? RadioButton)?.let { radio ->
                                applyingProgrammaticSelection = true
                                post {
                                    check(radio.id)
                                    applyingProgrammaticSelection = false
                                }
                            }
                        }
                    }
                    children.forEach { child ->
                        (child as? RadioButton)?.setOnCheckedChangeListener { _, isChecked ->
                            if (!isChecked || applyingProgrammaticSelection) return@setOnCheckedChangeListener
                            val selectedLabel = (child as RadioButton).text.toString()
                            binding.root.clearFocus()
                            val imm = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
                            item.value = selectedLabel
                            if (!effectiveEnabled) return@setOnCheckedChangeListener
                            if (item.hasDependants || item.hasAlertError) {
                                val selectedIndex = item.entries?.indexOfFirst { entry ->
                                    entry == selectedLabel || entry.equals(selectedLabel, ignoreCase = true)
                                } ?: -1
                                if (selectedIndex >= 0) {
                                    Timber.d("listener trigger : ${item.id} $selectedIndex $selectedLabel")
                                    formValueListener?.onValueChanged(item, selectedIndex)
                                }
                            }
                            item.errorText = null
                            binding.llContent.setBackgroundResource(0)
                        }
                    }
                }
            }

            if (!effectiveEnabled) {
                binding.rg.children.forEach {
                    it.isClickable = false
                }
            }
            if (item.errorText != null) binding.llContent.setBackgroundResource(R.drawable.state_errored)
            else binding.llContent.setBackgroundResource(0)

            //item.errorText?.let { binding.rg.error = it }
            binding.executePendingBindings()
            val str = binding.tvNullable.text
//            val str = binding.tvNullableHr.text
            val spannableString = SpannableString(str)

            val colorSpan = ForegroundColorSpan(Color.parseColor("#B00020"))
            val sizeSpan = RelativeSizeSpan(1.2f)

            if (item.required && item.doubleStar) {
                spannableString.setSpan(
                    colorSpan,
                    str.length - 2,
                    str.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
//                spannableString.setSpan(sizeSpan, str.length - 2, str.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvNullable.text = spannableString
                binding.tvNullableHr.text = spannableString
            } else if (item.required) {
                spannableString.setSpan(
                    colorSpan,
                    str.length - 1,
                    str.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
//                spannableString.setSpan(sizeSpan, str.length - 1, str.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.tvNullableHr.text = spannableString
                binding.tvNullable.text = spannableString
            }

        }
    }

    class CheckBoxesInputViewHolder private constructor(private val binding: RvItemFormCheckV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormCheckV2Binding.inflate(layoutInflater, parent, false)
                return CheckBoxesInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement,
            isEnabled: Boolean,
            formValueListener: FormValueListener?
        ) {
            binding.form = item
            binding.llChecks.removeAllViews()
            val effectiveEnabled = isEnabled && item.isEnabled

            val selectedIndexes = item.value
                ?.split("|")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()

            if (item.showAsMultiSelectDialog) {
                bindMultiSelectDialog(item, effectiveEnabled, selectedIndexes, formValueListener)
                return
            }

            binding.tvTitle.visibility = View.VISIBLE
            binding.llChecks.visibility = View.VISIBLE
            binding.tilMultiSelect.visibility = View.GONE

            item.entries?.forEachIndexed { index, text ->

                val cbx = CheckBox(binding.root.context)
                cbx.text = text
                cbx.isEnabled = effectiveEnabled
                cbx.isChecked = selectedIndexes.contains(index)
                cbx.setOnCheckedChangeListener { _, isChecked ->

                    if (isChecked) {
                        selectedIndexes.add(index)
                    } else {
                        selectedIndexes.remove(index)
                    }

                    item.value =
                        if (selectedIndexes.isEmpty()) null
                        else selectedIndexes.sorted().joinToString("|")

                    item.errorText = null
                    binding.clRi.setBackgroundResource(0)

                    val callbackIndex = if (isChecked) index else -(index + 1)
                    formValueListener?.onValueChanged(item, callbackIndex)
                }

                binding.llChecks.addView(cbx)
            }

            binding.executePendingBindings()
        }

        private fun bindMultiSelectDialog(
            item: FormElement,
            effectiveEnabled: Boolean,
            selectedIndexes: MutableSet<Int>,
            formValueListener: FormValueListener?
        ) {
            binding.tvTitle.visibility = View.GONE
            binding.llChecks.visibility = View.GONE
            binding.tilMultiSelect.visibility = View.VISIBLE
            binding.tilMultiSelect.hint = item.title
            binding.tilMultiSelect.isEnabled = effectiveEnabled
            binding.etMultiSelect.isEnabled = effectiveEnabled
            binding.etMultiSelect.isClickable = effectiveEnabled
            binding.etMultiSelect.isFocusable = false
            binding.etMultiSelect.setText(selectedIndexes.toDisplayText(item))
            binding.tilMultiSelect.error = item.errorText

            val showDialog = View.OnClickListener {
                if (!effectiveEnabled) return@OnClickListener
                val labels = item.entries ?: emptyArray()
                val checkedItems = BooleanArray(labels.size) { index -> selectedIndexes.contains(index) }

                AlertDialog.Builder(binding.root.context)
                    .setTitle(item.title)
                    .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        selectedIndexes.clear()
                        checkedItems.forEachIndexed { index, checked ->
                            if (checked) selectedIndexes.add(index)
                        }
                        item.value =
                            if (selectedIndexes.isEmpty()) null
                            else selectedIndexes.sorted().joinToString("|")
                        item.errorText = null
                        binding.tilMultiSelect.error = null
                        binding.clRi.setBackgroundResource(0)
                        binding.etMultiSelect.setText(selectedIndexes.toDisplayText(item))
                        formValueListener?.onValueChanged(item, -1)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            binding.etMultiSelect.setOnClickListener(showDialog)
            binding.tilMultiSelect.setEndIconOnClickListener(showDialog)
            binding.executePendingBindings()
        }

        private fun Set<Int>.toDisplayText(item: FormElement): String =
            sorted()
                .mapNotNull { index -> item.entries?.getOrNull(index) }
                .joinToString(", ")

    }

    class ButtonInputViewHolder private constructor(private val binding: RvItemFormBtnBinding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormBtnBinding.inflate(layoutInflater, parent, false)
                return ButtonInputViewHolder(binding)
            }
        }

        fun bind(item: FormElement, isEnabled: Boolean, formValueListener: SendOtpClickListener?) {
            binding.form = item
            isOtpVerified(isEnabled, isInternetAvailable(binding.root.context))

            binding.generateOtp.setOnClickListener {
                formValueListener!!.onButtonClick(item,binding.generateOtp,binding.timerInSec,binding.tilEditText,isEnabled,adapterPosition,binding.et)
            }



        }

        private fun isOtpVerified(isEnabled: Boolean, internetAvailable: Boolean) {
            if(isOtpVerified) {
                binding.generateOtp.text = binding.generateOtp.resources.getString(R.string.verified)
                binding.generateOtp.isEnabled = isEnabled

            } else {
                binding.generateOtp.text = binding.generateOtp.resources.getString(R.string.send_otp)
                if (internetAvailable){
                    binding.generateOtp.isEnabled = isEnabled

                } else {
                    binding.generateOtp.isEnabled = !isEnabled

                }

            }
        }


    }



    class SelectUploadImageClickListener(private val selectImageClick: (formId: Int) -> Unit) {

        fun onSelectImageClick(form: FormElement) = selectImageClick(form.id)

    }

    class ViewDocumentOnClick(private val viewDocument: (formId: Int) -> Unit) {

        fun onViewDocumentClick(form: FormElement) = viewDocument(form.id)

    }

    class DatePickerInputViewHolder private constructor(private val binding: RvItemFormDatepickerV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormDatepickerV2Binding.inflate(layoutInflater, parent, false)
                return DatePickerInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement, isEnabled: Boolean, formValueListener: FormValueListener?
        ) {
            binding.form = item
            binding.invalidateAll()
            if (!isEnabled) {
                binding.et.isFocusable = false
                binding.et.isClickable = false
                binding.executePendingBindings()
                return
            }
            val today = Calendar.getInstance()
            var thisYear = today.get(Calendar.YEAR)
            var thisMonth = today.get(Calendar.MONTH)
            var thisDay = today.get(Calendar.DAY_OF_MONTH)

            item.errorText?.also { binding.tilEditText.error = it }
                ?: run { binding.tilEditText.error = null }
            binding.et.setOnClickListener {
                val activity = binding.et.context.findFragmentActivity()
                    ?: return@setOnClickListener
                val originalLocale = Locale.getDefault()
                HelperUtil.setEnLocaleForDatePicker(activity)

                parseFormDate(item.value)?.let { selectedDate ->
                    thisYear = selectedDate.get(Calendar.YEAR)
                    thisMonth = selectedDate.get(Calendar.MONTH)
                    thisDay = selectedDate.get(Calendar.DAY_OF_MONTH)
                }
                val datePickerDialog = DatePickerDialog(
                    it.context, R.style.AppSpinnerDatePickerDialog, { _, year, month, day ->
                        val millis = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                        }.timeInMillis
                        if (item.min != null && millis < item.min!!) {
                            item.value = getDateString(item.min)
                        } else if (item.max != null && millis > item.max!!)
                            item.value = getDateString(item.max)
                        else
                            item.value = getDateString(millis)
                        binding.invalidateAll()
                        if (item.hasDependants) formValueListener?.onValueChanged(item, -1)
                    }, thisYear, thisMonth, thisDay
                )
                runCatching {
                    datePickerDialog.datePicker.calendarViewShown = false
                    datePickerDialog.datePicker.spinnersShown = true
                }
                datePickerDialog.showOnlyOkButton()
                item.errorText = null
                binding.tilEditText.error = null
                val effectiveMin = item.min
                val effectiveMax = item.max
                when {
                    effectiveMin != null && effectiveMax != null && effectiveMin > effectiveMax -> {
                        datePickerDialog.datePicker.minDate = effectiveMax
                        datePickerDialog.datePicker.maxDate = effectiveMax
                    }
                    else -> {
                        effectiveMin?.let { datePickerDialog.datePicker.minDate = it }
                        effectiveMax?.let { datePickerDialog.datePicker.maxDate = it }
                    }
                }
                if (item.showYearFirstInDatePicker)
                    datePickerDialog.datePicker.touchables[0].performClick()
                datePickerDialog.show()
                datePickerDialog.setOnDismissListener {
                    HelperUtil.setOriginalLocaleForDatePicker(activity,originalLocale)
                }
            }
            binding.executePendingBindings()

        }
    }

    class TimePickerInputViewHolder private constructor(private val binding: RvItemFormTimepickerV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormTimepickerV2Binding.inflate(layoutInflater, parent, false)
                return TimePickerInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement, isEnabled: Boolean
        ) {
            binding.form = item
            binding.et.isEnabled = isEnabled

            binding.et.apply {
                isFocusable = false           // Cannot focus manually
                isFocusableInTouchMode = false
                isClickable = true            // Still clickable for TimePicker
                isCursorVisible = false       // Hide cursor
                showSoftInputOnFocus = false  // Prevent keyboard on API 21+
                setTextIsSelectable(false)    // No selection
            }
            binding.et.setOnClickListener {
                val hour: Int
                val minute: Int
                if (item.value == null) {
                    val currentTime = Calendar.getInstance()
                    hour = currentTime.get(Calendar.HOUR_OF_DAY)
                    minute = currentTime.get(Calendar.MINUTE)
                } else {
                    hour = item.value!!.substringBefore(":").toInt()
                    minute = item.value!!.substringAfter(":").toInt()
                    Timber.d("Time picker hour min : $hour $minute")
                }


                val mTimePicker = TimePickerDialog(
                    it.context,
                    //android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                    R.style.AppTimePickerTheme,
                    { _, hourOfDay, minuteOfHour ->
                        val hh = if (hourOfDay < 10) "0$hourOfDay" else "$hourOfDay"
                        val mm = if (minuteOfHour < 10) "0$minuteOfHour" else "$minuteOfHour"
                        item.value = "$hh:$mm"
                        binding.invalidateAll()
                    },
                    hour,
                    minute,
                    false
                )



                mTimePicker.setTitle(it.context.getString(R.string.select_time))
                mTimePicker.show()
                val color = ContextCompat.getColor(it.context, R.color.md_theme_dark_inversePrimary) // your theme color
                mTimePicker.getButton(TimePickerDialog.BUTTON_POSITIVE)?.setTextColor(color)
                mTimePicker.getButton(TimePickerDialog.BUTTON_NEGATIVE)?.setTextColor(color)
            }
            binding.executePendingBindings()

        }
    }

    class TextViewInputViewHolder private constructor(private val binding: RvItemFormTextViewV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormTextViewV2Binding.inflate(layoutInflater, parent, false)
                return TextViewInputViewHolder(binding)
            }
        }

        fun bind(item: FormElement) {
            binding.form = item
            binding.executePendingBindings()
        }
    }

    class ImageViewInputViewHolder private constructor(private val binding: RvItemFormImageViewV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormImageViewV2Binding.inflate(layoutInflater, parent, false)
                return ImageViewInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement, clickListener: ImageClickListener?, isEnabled: Boolean
        ) {
            binding.form = item
            if (isEnabled) {
                binding.clickListener = clickListener
                if (item.errorText != null) binding.clRi.setBackgroundResource(R.drawable.state_errored)
                else binding.clRi.setBackgroundResource(0)
            }
            binding.executePendingBindings()

        }
    }

    class AgePickerViewInputViewHolder private constructor(private val binding: RvItemFormAgePickerViewV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding =
                    RvItemFormAgePickerViewV2Binding.inflate(layoutInflater, parent, false)
                return AgePickerViewInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement, isEnabled: Boolean, formValueListener: FormValueListener?
        ) {
            binding.form = item
            binding.invalidateAll()
            val agePicker = AgePickerDialog(binding.root.context)

            val calDob = Calendar.getInstance()
            val ageUnitDTO = AgeUnitDTO(0, 0, 0)
            val isOk = true
            if (item.value.isNullOrBlank()) {
                binding.etNum.setText("")
            } else {
                item.value?.let {
                    calDob.timeInMillis = getLongFromDate(it)
                    updateAgeDTO(ageUnitDTO, calDob)
                    binding.etNum.setText(getAgeStrFromAgeUnit(ageUnitDTO))
                }
            }

            if (isEnabled) {
                agePicker.onAgeConfirmed = { confirmed ->
                    ageUnitDTO.years = confirmed.years
                    ageUnitDTO.months = confirmed.months
                    ageUnitDTO.days = confirmed.days
                    binding.etNum.setText(getAgeStrFromAgeUnit(ageUnitDTO))
                    calDob.timeInMillis = getDobFromAge(ageUnitDTO)
                    binding.etDate.setText(getDateString(calDob.timeInMillis))
                    item.value = getDateString(calDob.timeInMillis)
                    item.errorText = null
                    applyAgePickerFieldError(binding, null)
                    if (item.hasDependants) formValueListener?.onValueChanged(item, -1)
                }
                binding.etNum.setOnClickListener {
                    val calNow = Calendar.getInstance()
                    val calMin = Calendar.getInstance()
                    val calMax = Calendar.getInstance()
                    item.min?.let {
                        calMin.timeInMillis = it
                    }
                    item.max?.let {
                        calMax.timeInMillis = it
                    }

                    agePicker.setLimitsAndShow(
                        calNow.get(Calendar.YEAR) - calMax.get(Calendar.YEAR),
                        calNow.get(Calendar.YEAR) - calMin.get(Calendar.YEAR),
                        0,
                        11,
                        0,
                        30,
                        ageUnitDTO,
                        isOk
                    )
                }
            }


            if (!isEnabled) {
                binding.etDate.isFocusable = false
                binding.etNum.isFocusable = false
                binding.etDate.isClickable = false
                binding.etNum.isClickable = false
                binding.executePendingBindings()
                return
            }
            val today = Calendar.getInstance()
            var thisYear = today.get(Calendar.YEAR)
            var thisMonth = today.get(Calendar.MONTH)
            var thisDay = today.get(Calendar.DAY_OF_MONTH)

            applyAgePickerFieldError(binding, item.errorText)
            binding.etDate.setOnClickListener {
                val activity = binding.etDate.context.findFragmentActivity()
                    ?: return@setOnClickListener
                val originalLocale = Locale.getDefault()
                HelperUtil.setEnLocaleForDatePicker(activity)

                parseFormDate(item.value)?.let { selectedDate ->
                    thisYear = selectedDate.get(Calendar.YEAR)
                    thisMonth = selectedDate.get(Calendar.MONTH)
                    thisDay = selectedDate.get(Calendar.DAY_OF_MONTH)
                }
                val datePickerDialog = DatePickerDialog(
                    it.context, R.style.AppSpinnerDatePickerDialog, { _, year, month, day ->
                        val millisCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        val millis = millisCal.timeInMillis
                        if (item.min != null && millis < item.min!!) {
                            item.value = getDateString(item.min)
                        } else if (item.max != null && millis > item.max!!)
                            item.value = getDateString(item.max)
                        else
                            item.value = getDateString(millis)

                        updateAgeDTO(ageUnitDTO, millisCal)
                        binding.etNum.setText(getAgeStrFromAgeUnit(ageUnitDTO))
                        item.errorText = null
                        applyAgePickerFieldError(binding, null)
                        binding.invalidateAll()
                        if (item.hasDependants) formValueListener?.onValueChanged(item, -1)
                    }, thisYear, thisMonth, thisDay
                )
                runCatching {
                    datePickerDialog.datePicker.calendarViewShown = false
                    datePickerDialog.datePicker.spinnersShown = true
                }
                datePickerDialog.showOnlyOkButton()
                item.errorText = null
                applyAgePickerFieldError(binding, null)
                val effectiveMin = item.min
                val effectiveMax = item.max
                when {
                    effectiveMin != null && effectiveMax != null && effectiveMin > effectiveMax -> {
                        datePickerDialog.datePicker.minDate = effectiveMax
                        datePickerDialog.datePicker.maxDate = effectiveMax
                    }
                    else -> {
                        effectiveMin?.let { datePickerDialog.datePicker.minDate = it }
                        effectiveMax?.let { datePickerDialog.datePicker.maxDate = it }
                    }
                }
                if (item.showYearFirstInDatePicker)
                    datePickerDialog.datePicker.touchables[0].performClick()
                datePickerDialog.show()
                datePickerDialog.setOnDismissListener {
                    HelperUtil.setOriginalLocaleForDatePicker(activity,originalLocale)
                }
            }
            binding.executePendingBindings()

        }
        private fun applyAgePickerFieldError(
            binding: RvItemFormAgePickerViewV2Binding,
            error: String?,
        ) {
            binding.tilEditTextDate.apply {
                isErrorEnabled = error != null
                this.error = error
            }
            binding.tilEditTextNum.apply {
                isErrorEnabled = error != null
                this.error = error
            }
        }
    }

    class HeadlineViewHolder private constructor(private val binding: RvItemFormHeadlineV2Binding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormHeadlineV2Binding.inflate(layoutInflater, parent, false)
                return HeadlineViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement,
            formValueListener: FormValueListener?,
        ) {
            binding.form = item
            if (item.subtitle == null)
                binding.textView8.visibility = View.GONE
            formValueListener?.onValueChanged(item, -1)
            binding.executePendingBindings()

        }
    }

    class ImageClickListener(private val imageClick: (formId: Int) -> Unit) {
        fun onImageClick(form: FormElement) = imageClick(form.id)
    }

    class SendOtpClickListener(private val btnClick: (formId: Int,generateOtp:MaterialButton,timerInsec: TextView,tilEditText:TextInputLayout, isEnabled: Boolean,adapterPosition:Int,otpField: TextInputEditText) -> Unit) {

        fun onButtonClick(
            form: FormElement,
            generateOtp: MaterialButton,
            timerInSec: TextView,
            tilEditText: TextInputLayout,
            isEnabled: Boolean,
            adapterPosition: Int,
            otpField: TextInputEditText
        ) = btnClick(form.id,generateOtp,timerInSec,tilEditText,isEnabled,adapterPosition,otpField)

    }

    class AgeClickListener(private val ageClick: (formId: Int) -> Unit) {

        fun onAgeClick(form: FormElement) = ageClick(form.id)

    }

    class FormValueListener(private val valueChanged: (id: Int, value: Int) -> Unit) {

        fun onValueChanged(form: FormElement, index: Int) {
            valueChanged(form.id, index)

        }

    }
    class NumberPickerInputViewHolder private constructor(
        private val binding: RvItemFormNumberPickerBinding
    ) : ViewHolder(binding.root) {

        private var textWatcher: TextWatcher? = null
        private var internalUpdate = false

        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemFormNumberPickerBinding.inflate(layoutInflater, parent, false)
                return NumberPickerInputViewHolder(binding)
            }
        }

        fun bind(
            item: FormElement,
            isEnabled: Boolean,
            formValueListener: FormValueListener?
        ) {
            binding.form = item

            binding.etNumberInput.isEnabled = isEnabled
            binding.btnDecrement.isEnabled = isEnabled
            binding.btnIncrement.isEnabled = isEnabled
            if (!isEnabled) {
                hideError()
                return
            }

            val minValue = item.min?.toInt() ?: 0
            val maxValue = item.max?.toInt()
            val allowNegative = item.minDecimal != null && item.minDecimal!! < 0

            binding.etNumberInput.setText(minValue.toString())
            binding.etNumberInput.setSelection(binding.etNumberInput.text!!.length)
            var currentValue = item.value?.toIntOrNull() ?: minValue

            textWatcher?.let { binding.etNumberInput.removeTextChangedListener(it) }

            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // No action required for this implementation.
                    // This method is implemented to satisfy the interface contract.


                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // No action required for this implementation.
                    // This method is implemented to satisfy the interface contract.

                }




                override fun afterTextChanged(editable: Editable?) {

                    if (internalUpdate) return
                    if (editable.isNullOrBlank()) {
                        item.errorText = binding.root.resources.getString(R.string.value_cannot_be_empty)
                        showError(item.errorText!!)
                        return
                    }

                    val inputValue = editable.toString().toIntOrNull()
                    if (inputValue == null) {
                        item.errorText = binding.root.resources.getString(R.string.enter_a_valid_number)
                        showError(item.errorText!!)
                        return
                    }

                    val validated = validateValue(inputValue, minValue, maxValue, allowNegative)

                    if (validated != inputValue) {
                        val msg = if (maxValue != null)
                            binding.root.resources.getString(R.string.allowed_range, minValue, maxValue)
                        else
                            binding.root.resources.getString(R.string.minimum_allowed_value, minValue)
                        item.errorText = msg
                        showError(msg)

                        updateDisplay(validated)
                        updateValue(validated, item, formValueListener)
                        return
                    }

                    item.errorText = null
                    hideError()

                    currentValue = validated
                    updateValue(currentValue, item, formValueListener)
                }
            }

            updateDisplay(currentValue)

            binding.etNumberInput.addTextChangedListener(textWatcher)



            binding.btnDecrement.setOnClickListener {
                currentValue = (item.value?.toIntOrNull() ?: minValue) - 1
                currentValue = validateValue(currentValue, minValue, maxValue, allowNegative)
                hideError()
                updateValue(currentValue, item, formValueListener)
                updateDisplay(currentValue)
            }

            binding.btnIncrement.setOnClickListener {
                currentValue = (item.value?.toIntOrNull() ?: minValue) + 1
                currentValue = validateValue(currentValue, minValue, maxValue, allowNegative)
                hideError()
                updateValue(currentValue, item, formValueListener)
                updateDisplay(currentValue)
            }
        }


        private fun validateValue(value: Int, min: Int, max: Int?, allowNegative: Boolean): Int {
            var newValue = value

            if (!allowNegative && newValue < min) newValue = min
            if (max != null && newValue > max) newValue = max

            return newValue
        }

        private fun updateDisplay(value: Int) {
            internalUpdate = true
            binding.etNumberInput.setText(value.toString())
            binding.etNumberInput.setSelection(binding.etNumberInput.text!!.length)
            internalUpdate = false

        }

        private fun updateValue(
            newValue: Int,
            item: FormElement,
            formValueListener: FormValueListener?
        ) {
            item.value = newValue.toString()
            formValueListener?.onValueChanged(item,newValue)
        }

        private fun showError(message: String) {
            binding.tvError.apply {
                text = message
                visibility = View.VISIBLE
            }
        }

        private fun hideError() {
            binding.tvError.visibility = View.GONE
        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inputTypes = values()
        val safeType = inputTypes.getOrNull(viewType) ?: TEXT_VIEW

        return when (safeType) {
            EDIT_TEXT -> EditTextInputViewHolder.from(parent)
            DROPDOWN -> DropDownInputViewHolder.from(parent)
            RADIO -> RadioInputViewHolder.from(parent)
            DATE_PICKER -> DatePickerInputViewHolder.from(parent)
            TEXT_VIEW -> TextViewInputViewHolder.from(parent)
            IMAGE_VIEW -> ImageViewInputViewHolder.from(parent)
            CHECKBOXES -> CheckBoxesInputViewHolder.from(parent)
            TIME_PICKER -> TimePickerInputViewHolder.from(parent)
            HEADLINE -> HeadlineViewHolder.from(parent)
            AGE_PICKER -> AgePickerViewInputViewHolder.from(parent)
            InputType.BUTTON -> ButtonInputViewHolder.from(parent)
            InputType.FILE_UPLOAD -> FileUploadInputViewHolder.from(parent)
            InputType.NUMBER_PICKER -> NumberPickerInputViewHolder.from(parent)
            InputType.MULTIFILE_UPLOAD -> MultiFileUploadInputViewHolder.from(parent)
        }
    }

    fun updateFileList(newList: List<Uri>) {
        this.fileList?.addAll(newList)
        notifyDataSetChanged()
    }
    class MultiFileUploadInputViewHolder private constructor(private val binding: LayoutMultiFileUploadBinding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = LayoutMultiFileUploadBinding.inflate(layoutInflater, parent, false)
                return MultiFileUploadInputViewHolder(binding)
            }
        }

        private lateinit var fileAdapter: FileListAdapter

        fun bind(
            item: FormElement,
            clickListener: SelectUploadImageClickListener?,
            documentOnClick: ViewDocumentOnClick?,
            isEnabled: Boolean,
            fileList : MutableList<Uri>?
        ) {
            val items = fileList ?: mutableListOf()
            fileAdapter = FileListAdapter(items)
            binding.rvFiles.adapter = fileAdapter
            fileAdapter.updateFileList(items)
            fileAdapter.notifyDataSetChanged()

            binding.btnSelectFiles.isEnabled = isEnabled
            binding.btnSelectFiles.alpha = if (isEnabled) 1f else 0.5f

            binding.btnSelectFiles.setOnClickListener {
                clickListener?.onSelectImageClick(item)
            }
        }

    }



    class FileUploadInputViewHolder private constructor(private val binding: LayoutUploafFormBinding) :
        ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): ViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = LayoutUploafFormBinding.inflate(layoutInflater, parent, false)
                return FileUploadInputViewHolder(binding)
            }
        }


        fun bind(
            item: FormElement,
            clickListener: SelectUploadImageClickListener?,
            documentOnClick: ViewDocumentOnClick?,
            isEnabled: Boolean
        ) {
            binding.form = item
            binding.tvTitle.text = item.title
            binding.clickListener = clickListener
            binding.documentclickListener = documentOnClick
            binding.btnView.visibility = if (!item.value.isNullOrEmpty()) View.VISIBLE else View.GONE

            if (isEnabled) {
                binding.addFile.visibility = View.VISIBLE
//                binding.addFile.isEnabled = true
//                binding.addFile.alpha = 1f
            } else {
                binding.addFile.visibility = View.GONE
//                binding.addFile.isEnabled = false
//                binding.addFile.alpha = 0.5f
            }
        }

    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
//        I was getting crash that's why i have moved this line inside try block
//        val isEnabled = if (isEnabled) item.isEnabled else false
        try {
            val isEnabled = if (isEnabled) item.isEnabled else false
            when (item.inputType) {
                EDIT_TEXT -> (holder as EditTextInputViewHolder).bind(
                    item, isEnabled, formValueListener
                )

                DROPDOWN -> (holder as DropDownInputViewHolder).bind(
                    item,
                    isEnabled,
                    formValueListener
                )

                RADIO -> (holder as RadioInputViewHolder).bind(item, isEnabled, formValueListener)
                DATE_PICKER -> (holder as DatePickerInputViewHolder).bind(
                    item, isEnabled, formValueListener
                )

                TEXT_VIEW -> (holder as TextViewInputViewHolder).bind(item)
                IMAGE_VIEW -> (holder as ImageViewInputViewHolder).bind(
                    item, imageClickListener, isEnabled
                )

                CHECKBOXES -> (holder as CheckBoxesInputViewHolder).bind(
                    item,
                    isEnabled,
                    formValueListener
                )

                TIME_PICKER -> (holder as TimePickerInputViewHolder).bind(item, isEnabled)
                HEADLINE -> (holder as HeadlineViewHolder).bind(item, formValueListener)
                AGE_PICKER -> (holder as AgePickerViewInputViewHolder).bind(
                    item,
                    isEnabled,
                    formValueListener
                )
                InputType.BUTTON -> (holder as ButtonInputViewHolder).bind(
                    item,
                    isEnabled,
                    sendOtpClickListener
                )

                InputType.FILE_UPLOAD -> (holder as FileUploadInputViewHolder).bind(item,selectImageClickListener,viewDocumentListner,isEnabled = !disableUpload)
                InputType.MULTIFILE_UPLOAD -> (holder as MultiFileUploadInputViewHolder).bind(item,selectImageClickListener,viewDocumentListner,isEnabled = !disableUpload,fileList)

                InputType.NUMBER_PICKER -> (holder as NumberPickerInputViewHolder).bind(
                    item, isEnabled, formValueListener
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    //override fun getItemViewType(position: Int) = getItem(position).inputType.ordinal
    //THis changes done to solve crashalytics issue
    override fun getItemViewType(position: Int): Int {
        if (position < 0 || position >= itemCount) {
            return TEXT_VIEW.ordinal // fallback view type
        }

        val item = getItem(position) ?: return TEXT_VIEW.ordinal
        return item.inputType.ordinal
    }
    /**
     * Validation Result : -1 -> all good
     * else index of element creating trouble
     *
     * Required empty fields are always re-evaluated first. Previously, returning early on any
     * existing [FormElement.errorText] skipped this pass — e.g. after DOB click cleared its error,
     * another field could still hold stale errorText and submit would never re-set the DOB error.
     */
    fun validateInput(resources: Resources, formRecyclerView: RecyclerView? = null): Int {
        if (!isEnabled) return -1
        val emptyError = resources.getString(R.string.form_input_empty_error)

        // Pass 1: apply / clear required-empty errors
        currentList.forEachIndexed { index, element ->
            if (element.inputType == TEXT_VIEW) return@forEachIndexed
            val filledWhileDisabled = !element.isEnabled && !element.value.isNullOrBlank()
            when {
                element.required && element.value.isNullOrBlank() && !filledWhileDisabled -> {
                    element.errorText = emptyError
                }
                !element.value.isNullOrBlank() && element.errorText == emptyError -> {
                    element.errorText = null
                }
                filledWhileDisabled && element.errorText != null -> {
                    element.errorText = null
                }
            }
        }

        // Pass 2: first invalid field from top (empty required, then other errors in form order)
        var firstInvalidIndex = -1
        currentList.forEachIndexed { index, element ->
            if (element.inputType == TEXT_VIEW) return@forEachIndexed
            if (!isInvalidForSubmit(element)) return@forEachIndexed
            notifyItemChanged(index)
            if (firstInvalidIndex == -1) firstInvalidIndex = index
        }

        if (firstInvalidIndex != -1) {
            formRecyclerView?.post {
                formRecyclerView.scrollToFormValidationError(firstInvalidIndex)
            }
            return firstInvalidIndex
        }
        return -1
    }

    private fun isInvalidForSubmit(element: FormElement): Boolean {
        if (!element.isEnabled && !element.value.isNullOrBlank()) return false
        if (element.required && element.value.isNullOrBlank()) return true
        return element.errorText != null
    }



}
