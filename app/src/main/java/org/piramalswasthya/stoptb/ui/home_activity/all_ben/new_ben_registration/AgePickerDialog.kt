package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.EditText
import android.widget.NumberPicker
import org.piramalswasthya.stoptb.databinding.AlertAgePickerBinding
import org.piramalswasthya.stoptb.model.AgeUnitDTO


class AgePickerDialog(context: Context) : AlertDialog(context) {

    private var _binding: AlertAgePickerBinding? = null

    private val binding: AlertAgePickerBinding
        get() = _binding!!

    private var yearsMin: Int = 0
    private var yearsMax: Int = 0
    private var montsMin: Int = 0
    private var monthsMax: Int = 0
    private var daysMin: Int = 0
    private var daysMax: Int = 0
    private var selectionConfirmed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        _binding = AlertAgePickerBinding.inflate(layoutInflater, null, false)
        setContentView(binding.root)
    }

    /**
     * age picker dialog
     * - setting min and max values
     * - setting default values from dto
     * - trigger show to open the dialog
     */
    fun setLimitsAndShow(
        yearsMin: Int,
        yearsMax: Int,
        monthsMin: Int,
        monthsMax: Int,
        daysMin: Int,
        daysMax: Int,
        ageUnitDTO: AgeUnitDTO,
        isOk: Boolean
    ) {
        this.yearsMin = yearsMin
        this.yearsMax = yearsMax
        this.montsMin = monthsMin
        this.monthsMax = monthsMax
        this.daysMin = daysMin
        this.daysMax = daysMax
        show(ageUnitDTO, isOk)
    }

    fun show(ageUnitDTO: AgeUnitDTO, isOk: Boolean) {
        Handler(Looper.getMainLooper()).post {
            selectionConfirmed = false
            super.show()
            val safeYearsMin = yearsMin.coerceAtLeast(0)
            val safeYearsMax = yearsMax.coerceAtLeast(safeYearsMin)

            val safeMonthsMin = montsMin.coerceAtLeast(0)
            val safeMonthsMax = monthsMax.coerceAtLeast(safeMonthsMin)

            val safeDaysMin = daysMin.coerceAtLeast(0)
            val safeDaysMax = daysMax.coerceAtLeast(safeDaysMin)

            val years = ageUnitDTO.years.coerceIn(safeYearsMin, safeYearsMax)
            val months = ageUnitDTO.months.coerceIn(safeMonthsMin, safeMonthsMax)

            bindPicker(binding.dialogNumberPickerYears, safeYearsMin, safeYearsMax, years)
            bindPicker(binding.dialogNumberPickerMonths, safeMonthsMin, safeMonthsMax, months)
            bindPicker(
                binding.dialogNumberPickerDays,
                resolveDaysMin(years, months, safeDaysMin),
                safeDaysMax,
                ageUnitDTO.days
            )

            val syncDaysMin: () -> Unit = {
                val resolvedDaysMin = resolveDaysMin(
                    years = binding.dialogNumberPickerYears.value,
                    months = binding.dialogNumberPickerMonths.value,
                    defaultDaysMin = safeDaysMin
                )
                val daysPicker = binding.dialogNumberPickerDays
                if (daysPicker.minValue != resolvedDaysMin) {
                    bindPicker(daysPicker, resolvedDaysMin, safeDaysMax, daysPicker.value)
                }
            }

            binding.dialogNumberPickerYears.setOnValueChangedListener { _, _, _ -> syncDaysMin() }
            binding.dialogNumberPickerMonths.setOnValueChangedListener { _, _, _ -> syncDaysMin() }

            binding.btnOk.setOnClickListener {
                ageUnitDTO.years = readPickerValue(binding.dialogNumberPickerYears)
                ageUnitDTO.months = readPickerValue(binding.dialogNumberPickerMonths)
                ageUnitDTO.days = readPickerValue(binding.dialogNumberPickerDays)
                selectionConfirmed = true
                dismiss()
            }

            binding.btnCancel.setOnClickListener {
                cancel()
            }
        }
    }


    private fun bindPicker(picker: NumberPicker, min: Int, max: Int, value: Int) {
        val safeMin = min.coerceAtLeast(0)
        val safeMax = max.coerceAtLeast(safeMin)
        val safeValue = value.coerceIn(safeMin, safeMax)

        picker.displayedValues = null
        if (picker.maxValue < safeMax) {
            picker.maxValue = safeMax
        }
        if (picker.minValue > safeMin) {
            picker.minValue = safeMin
        }
        picker.minValue = safeMin
        picker.maxValue = safeMax
        picker.wrapSelectorWheel = false
        picker.value = safeValue
        picker.displayedValues = (safeMin..safeMax).map { it.toString() }.toTypedArray()
        picker.wrapSelectorWheel = false
        forceLatinDigitInput(picker)
    }

    private fun forceLatinDigitInput(picker: NumberPicker) {
        val input = picker.findViewById<EditText>(numberPickerInputId()) ?: return
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.keyListener = DigitsKeyListener.getInstance("0123456789")
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.isCursorVisible = true
    }

    private fun readPickerValue(picker: NumberPicker): Int {
        val typed = picker.findViewById<EditText>(numberPickerInputId())
            ?.text
            ?.toString()
            ?.trim()
            ?.toIntOrNull()
        val resolved = typed ?: picker.value
        val min = picker.minValue
        val max = picker.maxValue
        return if (max < min) resolved.coerceAtLeast(0) else resolved.coerceIn(min, max)
    }

    private fun numberPickerInputId(): Int {
        return Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
    }

    private fun resolveDaysMin(years: Int, months: Int, defaultDaysMin: Int): Int {
        return if (years == 0 && months == 0) {
            defaultDaysMin.coerceAtLeast(1)
        } else {
            defaultDaysMin.coerceAtLeast(0)
        }
    }

    fun consumeSelectionConfirmed(): Boolean {
        val wasConfirmed = selectionConfirmed
        selectionConfirmed = false
        return wasConfirmed
    }


    companion object {
        const val TAG = "AgePickerDialog"
    }
}
