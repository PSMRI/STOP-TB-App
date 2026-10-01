package org.piramalswasthya.stoptb.ui.service_location_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.piramalswasthya.stoptb.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.ActivityServiceTypeBinding
import org.piramalswasthya.stoptb.helpers.MyContextWrapper
import org.piramalswasthya.stoptb.helpers.TapjackingProtectionHelper
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber

@AndroidEntryPoint
class ServiceLocationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_HOME_SWITCH = "fromHomeSwitch"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WrapperEntryPoint {
        val pref: PreferenceDao
    }

    private var _binding: ActivityServiceTypeBinding? = null
    private val binding: ActivityServiceTypeBinding
        get() = _binding!!

    private val viewModel: ServiceTypeViewModel by viewModels()
    private val onBackPressedCallback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getBooleanExtra(EXTRA_FROM_HOME_SWITCH, false)) {
                    finish()
                    return
                }
                if (viewModel.isLocationSet()) {
                    finish()
                    startActivity(Intent(this@ServiceLocationActivity, VolunteerActivity::class.java))
                } else
                    if (!exitAlert.isShowing)
                        exitAlert.show()

            }
        }
    }
    private val incompleteLocationAlert by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.missing_detail_title))
            .setMessage(getString(R.string.missing_detail_message))
            .setPositiveButton(getString(R.string.understood)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
    private val exitAlert by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Application")
            .setMessage("Do you want to exit application")
            .setPositiveButton("Yes") { _, _ ->
                finish()
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
            }
            .create()
    }

    override fun attachBaseContext(newBase: Context) {
        val pref = EntryPointAccessors.fromApplication(
            newBase, WrapperEntryPoint::class.java
        ).pref
        super.attachBaseContext(
            MyContextWrapper.wrap(
                newBase,
                newBase.applicationContext,
                pref.getCurrentLanguage().symbol
            )
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return if (TapjackingProtectionHelper.isTouchAllowed(this, ev)) {
            super.dispatchTouchEvent(ev)
        } else {
            false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        TapjackingProtectionHelper.applyWindowSecurity(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityServiceTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Timber.d("onViewCreated() called!")
        TapjackingProtectionHelper.enableTouchFiltering(this)
        binding.lifecycleOwner = this


        onBackPressedDispatcher.addCallback(
            this, onBackPressedCallback
        )

        binding.btnContinue.setOnClickListener {
            if (dataValid()) {
                viewModel.saveCurrentLocation()
                finish()
                startActivity(Intent(this@ServiceLocationActivity, VolunteerActivity::class.java))
            } else
                incompleteLocationAlert.show()
        }
        viewModel.state.observe(this) {
            it?.let {
                when (it) {
                    ServiceTypeViewModel.State.IDLE -> {}//TODO()
                    ServiceTypeViewModel.State.LOADING -> {}//TODO()
                    ServiceTypeViewModel.State.SUCCESS -> {
                        binding.viewModel = viewModel

                        binding.tilTuDropdown.visibility = View.GONE
                        binding.tilHealthFacilityDropdown.visibility = View.GONE

                        binding.actvStateDropdown.apply {
                            isEnabled = false
                            setText(viewModel.stateList.first())
                        }
                        binding.actvDistrictDropdown.apply {
                            isEnabled = false
                            setText(viewModel.districtList.first())
                        }
                        binding.actvBlockDropdown.apply {
                            isEnabled = false
                            setText(viewModel.blockList.first())
                        }
//                        binding.actvTuDropdown.apply {
//                            isEnabled = false
//                            setText(viewModel.selectedTuName)
//                            if (viewModel.tuList.size == 1) {
//                                setText(viewModel.tuList.first())
//                                viewModel.setTu(0)
//                            }
//                        }
//                        binding.actvHealthFacilityDropdown.apply {
//                            isEnabled = false
//                            setText(viewModel.selectedHealthFacilityName)
//                            if (viewModel.healthFacilityList.size == 1) {
//                                setText(viewModel.healthFacilityList.first())
//                                viewModel.setHealthFacility(0)
//                            }
//                        }
                        binding.actvVillageDropdown.apply {
                            if (viewModel.villageList.size == 1) {
                                setText(viewModel.villageList.first())
                                viewModel.setVillage(0)
                            } else {
                                setText(viewModel.selectedVillageName.orEmpty())
                            }
                        }
                        val showVillageDialog = View.OnClickListener {
                            showSearchableVillageDialog()
                        }
                        binding.actvVillageDropdown.setOnClickListener(showVillageDialog)
                        binding.tilVillageDropdown.setEndIconOnClickListener(showVillageDialog)
                    }
                }
            }
        }
    }

    private fun showSearchableVillageDialog() {
        val labels = viewModel.villageList
        if (labels.isEmpty()) return

        val density = resources.displayMetrics.density
        val maxListHeightPx = (resources.displayMetrics.heightPixels * 0.58f).toInt()
        val rowHeightPx = (48 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
        }
        val searchInput = EditText(this).apply {
            hint = getString(R.string.household_search)
            setSingleLine(true)
        }
        val listView = ListView(this)
        container.addView(
            searchInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        container.addView(
            listView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0)
        )

        var filteredIndices = labels.indices.toList()
        lateinit var dialog: AlertDialog

        fun refreshFilteredList(query: String) {
            filteredIndices = labels.indices.filter { labels[it].contains(query, ignoreCase = true) }
            listView.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                filteredIndices.map { labels[it] }
            )
            listView.layoutParams = listView.layoutParams.apply {
                height = minOf(filteredIndices.size * rowHeightPx, maxListHeightPx)
            }
        }

        refreshFilteredList("")

        listView.setOnItemClickListener { _, _, position, _ ->
            val originalIndex = filteredIndices[position]
            viewModel.setVillage(originalIndex)
            binding.actvVillageDropdown.setText(labels[originalIndex])
            dialog.dismiss()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshFilteredList(s?.toString().orEmpty())
            }
        })

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.service_type_dd_village_text)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val buttonPadding = (16 * density).toInt()
        val actionTextColor = ContextCompat.getColor(this, R.color.md_theme_light_primary)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            background = null
            minWidth = 0
            minimumWidth = 0
            setTextColor(actionTextColor)
            setPadding(buttonPadding, 0, buttonPadding, 0)
        }
    }

    private fun dataValid(): Boolean {
        return !(binding.actvStateDropdown.text.isNullOrBlank() ||
                binding.actvDistrictDropdown.text.isNullOrBlank() ||
                binding.actvBlockDropdown.text.isNullOrBlank() ||
//                (viewModel.isTuRequired() && binding.actvTuDropdown.text.isNullOrBlank()) ||
//                (viewModel.isHealthFacilityRequired() && binding.actvHealthFacilityDropdown.text.isNullOrBlank()) ||
                binding.actvVillageDropdown.text.isNullOrBlank())

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


}
