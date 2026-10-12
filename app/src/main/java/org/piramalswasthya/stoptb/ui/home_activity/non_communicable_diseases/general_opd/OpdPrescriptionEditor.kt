package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import java.util.Locale

/** General OPD owns these cards; shared form controls are unaffected. */
class OpdPrescriptionEditor @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private var medicines = mutableListOf<OpdMedicineDraft>()
    private var editable = true
    private var masters = OpdDrugMasters()
    private val fields = mutableListOf<Triple<TextInputLayout, TextInputLayout, TextInputLayout>>()
    private val formFields = mutableListOf<TextInputLayout>()
    var onChanged: (List<OpdMedicineDraft>) -> Unit = {}

    init { orientation = VERTICAL }

    fun setMasters(value: OpdDrugMasters) {
        if (masters == value) return
        masters = value
        render()
    }

    fun bind(rows: List<OpdMedicineDraft>, isEditable: Boolean) {
        medicines = rows.map { it.copy() }.toMutableList()
        if (medicines.isEmpty() && isEditable) medicines.add(OpdMedicineDraft())
        editable = isEditable
        render()
    }

    fun validate(requireMedicine: Boolean): Boolean {
        var valid = true
        medicines.forEachIndexed { index, row ->
            val required = requireMedicine || row.hasData()
            val (medicine, frequency, unit) = fields[index]
            val form = formFields[index]
            form.error = if (required && row.itemFormId == null) context.getString(R.string.opd_drug_form) else null
            medicine.error = if (required && row.medicine.isBlank()) context.getString(R.string.opd_medicine_hint) else null
            frequency.error = if (required && row.frequency.isBlank()) context.getString(R.string.frequency) else null
            unit.error = if (required && row.durationUnit.isBlank()) context.getString(R.string.opd_duration_unit) else null
            if (form.error != null || medicine.error != null || frequency.error != null || unit.error != null) {
                if (valid) medicine.requestRectangleOnScreen(android.graphics.Rect(0, 0, medicine.width, medicine.height), false)
                valid = false
            }
        }
        return valid && (!requireMedicine || medicines.isNotEmpty())
    }

    private fun changed() = onChanged(medicines.map { it.copy() })

    private fun render() {
        removeAllViews()
        fields.clear()
        formFields.clear()
        medicines.forEachIndexed { index, row ->
            // Legacy saved medicines can recover their form from an unambiguous master match.
            if (row.itemFormId == null && row.medicine.isNotBlank()) {
                val labels = masters.medicineLabels()
                val matchingForms = masters.items.filterIndexed { i, item ->
                    if (row.drugId != null) item.itemId == row.drugId else labels[i] == row.medicine
                }.map { it.itemFormId }.distinct()
                matchingForms.singleOrNull()?.let { id ->
                    masters.forms.singleOrNull { it.itemFormId == id }?.let {
                        row.itemFormId = it.itemFormId
                        row.drugForm = it.itemFormName
                    }
                }
            }
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(12), dp(16), dp(12), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(dp(2), ContextCompat.getColor(context, R.color.dashboard_ink))
                }
            }
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            val header = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            header.addView(TextView(context).apply {
                text = context.getString(R.string.opd_medicine_title, index + 1)
                textSize = 20f
                setTextColor(ContextCompat.getColor(context, R.color.dashboard_ink))
                setTypeface(typeface, Typeface.BOLD)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val stockLabel = TextView(context).apply {
                text = context.getString(R.string.opd_out_of_stock)
                textSize = 16f
                setTextColor(Color.RED)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                visibility = if (masters.isOutOfStock(row.medicine)) View.VISIBLE else View.GONE
            }
            header.addView(stockLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
            val forms = masters.forms
            formFields.add(dropdown(card, R.string.opd_drug_form, -1, row.drugForm.orEmpty(),
                masterChoices = forms.map { it.itemFormName }, onMasterSelected = { selectedIndex ->
                    val selected = forms[selectedIndex]
                    if (row.itemFormId != selected.itemFormId) {
                        row.medicine = ""
                        row.drugId = null
                        row.drugName = null
                    }
                    row.itemFormId = selected.itemFormId
                    row.drugForm = selected.itemFormName
                }) { changed(); render() })
            val filteredMasters = masters.forForm(row.itemFormId)
            val drugItems = filteredMasters.items
            val medicine = dropdown(card, R.string.opd_medicine_hint, -1, row.medicine,
                searchable = true, masterChoices = filteredMasters.medicineLabels(), onMasterSelected = { selectedIndex ->
                    row.drugId = drugItems[selectedIndex].itemId
                    row.drugName = drugItems[selectedIndex].itemName
                }) {
                row.medicine = it
                stockLabel.visibility = if (masters.isOutOfStock(it)) View.VISIBLE else View.GONE
                changed()
                render()
            }
            medicine.isEnabled = editable && row.itemFormId != null
            val frequency = dropdown(card, R.string.frequency, -1, row.frequency,
                masterChoices = masters.frequencies.map { it.frequency }) {
                row.frequency = it; changed()
            }
            val counter = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = HORIZONTAL }
            card.addView(counter, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            val value = TextView(context).apply {
                text = row.durationCount.toString(); textSize = 22f; gravity = Gravity.CENTER
                contentDescription = context.getString(R.string.duration)
            }
            val minus = MaterialButton(context).apply {
                text = "-"; textSize = 26f
                contentDescription = context.getString(R.string.opd_decrease_duration)
                isEnabled = editable && row.durationCount > 1
            }
            val plus = MaterialButton(context).apply {
                text = "+"; textSize = 26f
                contentDescription = context.getString(R.string.opd_increase_duration)
                isEnabled = editable && row.durationCount < 999
            }
            fun updateCounter() {
                value.text = row.durationCount.toString()
                minus.isEnabled = editable && row.durationCount > 1
                plus.isEnabled = editable && row.durationCount < 999
                changed()
            }
            minus.setOnClickListener { if (row.durationCount > 1) { row.durationCount--; updateCounter() } }
            plus.setOnClickListener { if (row.durationCount < 999) { row.durationCount++; updateCounter() } }
            counter.addView(minus, LayoutParams(dp(64), dp(56)))
            counter.addView(value, LayoutParams(dp(100), dp(56)))
            counter.addView(plus, LayoutParams(dp(64), dp(56)))
            val unit = dropdown(card, R.string.opd_duration_unit, -1, row.durationUnit,
                masterChoices = masters.durationUnits.map { it.drugDuration }) {
                row.durationUnit = it; changed()
            }
            dropdown(card, R.string.opd_instruction, R.array.opd_instructions, row.instruction) {
                row.instruction = it; changed()
            }
            fields.add(Triple(medicine, frequency, unit))
            if (editable) {
                val actions = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = HORIZONTAL }
                card.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
                action(actions, R.drawable.ic_refresh, R.color.dashboard_icon_orange, R.string.opd_reset_medicine) {
                    medicines[index] = OpdMedicineDraft(); changed(); render()
                }
                action(actions, R.drawable.ic_opd_plus, R.color.holo_green_dark, R.string.opd_add_medicine) {
                    medicines.add(index + 1, OpdMedicineDraft()); changed(); render()
                    getChildAt(index + 1)?.post {
                        getChildAt(index + 1)?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, dp(100)), false) }
                    }
                }
                action(actions, R.drawable.ic_close, R.color.holo_red_light, R.string.opd_remove_medicine) {
                    medicines.removeAt(index)
                    if (medicines.isEmpty()) medicines.add(OpdMedicineDraft())
                    changed(); render()
                }
            }
        }
    }

    private fun dropdown(parent: LinearLayout, title: Int, array: Int, selected: String,
                         searchable: Boolean = false, masterChoices: List<String>? = null,
                         onMasterSelected: (Int) -> Unit = {},
                         update: (String) -> Unit): TextInputLayout {
        val layout = TextInputLayout(context).apply {
            hint = context.getString(title)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = if (searchable) TextInputLayout.END_ICON_CUSTOM else TextInputLayout.END_ICON_DROPDOWN_MENU
            isEnabled = editable
        }
        val choices = masterChoices?.toTypedArray() ?: resources.getStringArray(array)
        val english = masterChoices?.toTypedArray() ?: context.createConfigurationContext(Configuration(resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).resources.getStringArray(array)
        val field = MaterialAutoCompleteTextView(context).apply {
            inputType = android.text.InputType.TYPE_NULL
            if (!searchable) setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, choices))
            val selectedIndex = english.indexOf(selected)
            setText(choices.getOrNull(selectedIndex) ?: selected, false)
            setOnItemClickListener { _, _, position, _ ->
                onMasterSelected(position)
                update(english[position]); layout.error = null
            }
            isEnabled = editable
        }
        layout.addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (searchable) {
            // This field opens a picker; it must not consume the first tap to gain input focus.
            field.isFocusable = false
            field.isFocusableInTouchMode = false
            field.isCursorVisible = false
            layout.setEndIconDrawable(R.drawable.ic_arrow_drop_down)
            layout.endIconContentDescription = context.getString(title)
            val openSearch = {
                if (editable) showMedicineSearch(choices) { originalIndex ->
                    onMasterSelected(originalIndex)
                    field.setText(choices[originalIndex], false)
                    update(english[originalIndex])
                    layout.error = null
                }
            }
            field.setOnClickListener { openSearch() }
            layout.setEndIconOnClickListener { openSearch() }
        }
        parent.addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return layout
    }

    private fun showMedicineSearch(labels: Array<String>, selected: (Int) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val search = EditText(context).apply {
            hint = context.getString(R.string.household_search)
            setSingleLine(true)
        }
        val list = ListView(context)
        val empty = TextView(context).apply {
            text = context.getString(if (labels.isEmpty()) R.string.opd_drug_master_unavailable else R.string.opd_no_medicine_matches)
            setPadding(0, dp(16), 0, dp(16))
        }
        container.addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        container.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        var filteredIndices = labels.indices.toList()
        fun filter(query: String) {
            filteredIndices = labels.indices.filter { labels[it].contains(query.trim(), ignoreCase = true) }
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, filteredIndices.map { labels[it] })
            list.layoutParams = list.layoutParams.apply {
                height = minOf(filteredIndices.size * dp(56), (resources.displayMetrics.heightPixels * 0.5f).toInt())
            }
            empty.visibility = if (filteredIndices.isEmpty()) View.VISIBLE else View.GONE
        }
        filter("")
        search.doAfterTextChanged { filter(it?.toString().orEmpty()) }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.opd_medicine_hint)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            selected(filteredIndices[position])
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun action(parent: LinearLayout, icon: Int, color: Int, description: Int, clicked: () -> Unit) {
        parent.addView(ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ContextCompat.getColor(context, color)) }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = context.getString(description)
            setOnClickListener { clicked() }
        }, LayoutParams(dp(56), dp(56)).apply { marginStart = dp(10); marginEnd = dp(10) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
