package org.piramalswasthya.stoptb.ui.service_location_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber

@AndroidEntryPoint
class ServiceLocationActivity : AppCompatActivity() {

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
                if (viewModel.isLocationSet()) {
                    finish()
                    val targetClass = if (intent.getBooleanExtra("fromVolunteer", false))
                        VolunteerActivity::class.java else HomeActivity::class.java
                    startActivity(Intent(this@ServiceLocationActivity, targetClass))
                } else
                    if (!exitAlert.isShowing)
                        exitAlert.show()

            }
        }
    }
    private val incompleteLocationAlert by lazy {
        MaterialAlertDialogBuilder(this)
            .setTitle("Missing Detail")
            .setMessage("At least one of the following is missing value:\n \n\tState\n\tDistrict\n\tBlock\n\tTU\n\tHealth Facility\n\tVillage")
            .setPositiveButton("Understood") { dialog, _ ->
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
                val targetClass = if (intent.getBooleanExtra("fromVolunteer", false))
                    VolunteerActivity::class.java else VolunteerActivity ::class.java
                startActivity(Intent(this@ServiceLocationActivity, targetClass))
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
                        binding.actvTuDropdown.apply {
                            isEnabled = false
                            setText(viewModel.selectedTuName)
                            if (viewModel.tuList.size == 1) {
                                setText(viewModel.tuList.first())
                                viewModel.setTu(0)
                            }
                        }
                        binding.actvHealthFacilityDropdown.apply {
                            isEnabled = false
                            setText(viewModel.selectedHealthFacilityName)
                            if (viewModel.healthFacilityList.size == 1) {
                                setText(viewModel.healthFacilityList.first())
                                viewModel.setHealthFacility(0)
                            }
                        }
                        binding.actvVillageDropdown.apply {
                            setText(viewModel.selectedVillageName)
                            if (viewModel.villageList.size == 1) {
                                setText(viewModel.villageList.first())
                                viewModel.setVillage(0)
                            }
                            setOnItemClickListener { _, _, i, _ ->
                                viewModel.setVillage(i)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun dataValid(): Boolean {
        return !(binding.actvStateDropdown.text.isNullOrBlank() ||
                binding.actvDistrictDropdown.text.isNullOrBlank() ||
                binding.actvBlockDropdown.text.isNullOrBlank() ||
                (viewModel.isTuRequired() && binding.actvTuDropdown.text.isNullOrBlank()) ||
                (viewModel.isHealthFacilityRequired() && binding.actvHealthFacilityDropdown.text.isNullOrBlank()) ||
                binding.actvVillageDropdown.text.isNullOrBlank())

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


}
