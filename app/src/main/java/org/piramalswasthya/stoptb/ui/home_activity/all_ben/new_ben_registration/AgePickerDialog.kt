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

            binding.dialogNumberPickerYears.apply {
                minValue = safeYearsMin
                maxValue = safeYearsMax
                value = ageUnitDTO.years.coerceIn(safeYearsMin, safeYearsMax)
                forceLatinDigits(this)
            }

            binding.dialogNumberPickerMonths.apply {
                minValue = safeMonthsMin
                maxValue = safeMonthsMax
                value = ageUnitDTO.months.coerceIn(safeMonthsMin, safeMonthsMax)
                forceLatinDigits(this)
            }

            binding.dialogNumberPickerDays.apply {
                maxValue = safeDaysMax
                minValue = resolveDaysMin(
                    years = ageUnitDTO.years.coerceIn(safeYearsMin, safeYearsMax),
                    months = ageUnitDTO.months.coerceIn(safeMonthsMin, safeMonthsMax),
                    defaultDaysMin = safeDaysMin
                )
                value = ageUnitDTO.days.coerceIn(minValue, safeDaysMax)
                forceLatinDigits(this)
            }

            val syncDaysMin: () -> Unit = {
                val resolvedDaysMin = resolveDaysMin(
                    years = binding.dialogNumberPickerYears.value,
                    months = binding.dialogNumberPickerMonths.value,
                    defaultDaysMin = safeDaysMin
                )
                if (binding.dialogNumberPickerDays.minValue != resolvedDaysMin) {
                    binding.dialogNumberPickerDays.minValue = resolvedDaysMin
                    if (binding.dialogNumberPickerDays.value < resolvedDaysMin) {
                        binding.dialogNumberPickerDays.value = resolvedDaysMin
                    }
                    forceLatinDigits(binding.dialogNumberPickerDays)
                }
            }

            binding.dialogNumberPickerYears.setOnValueChangedListener { _, _, _ -> syncDaysMin() }
            binding.dialogNumberPickerMonths.setOnValueChangedListener { _, _, _ -> syncDaysMin() }

            binding.btnOk.setOnClickListener {
                val mInputTextYears: EditText = binding.dialogNumberPickerYears.findViewById(
                    Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
                )
                ageUnitDTO.years = mInputTextYears.text.toString().toInt()

                val mInputTextMonths: EditText = binding.dialogNumberPickerMonths.findViewById(
                    Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
                )
                ageUnitDTO.months = mInputTextMonths.text.toString().toInt()

                val mInputTextDays: EditText = binding.dialogNumberPickerDays.findViewById(
                    Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
                )
                ageUnitDTO.days = mInputTextDays.text.toString().toInt()
                selectionConfirmed = true
                dismiss()
            }

            binding.btnCancel.setOnClickListener {
                cancel()
            }
        }
    }

    private fun forceLatinDigits(picker: NumberPicker) {
        val min = picker.minValue
        val max = picker.maxValue
        picker.displayedValues = null
        picker.displayedValues = (min..max).map { it.toString() }.toTypedArray()
        val editTextId = Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
        val input = picker.findViewById<EditText>(editTextId)
        input?.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            keyListener = DigitsKeyListener.getInstance("0123456789")
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
        }
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
