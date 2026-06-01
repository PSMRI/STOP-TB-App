package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.form

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.annotation.SuppressLint
import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.helpers.applyAutoFlowBackPolicyOnResume
import org.piramalswasthya.stoptb.helpers.blockBackNavigationInAutoFlow
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TBScreeningFormFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding
        get() = _binding!!

    private val viewModel: TBScreeningFormViewModel by viewModels()

    private val familyContactAlert by lazy {
        AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.tb_screening))
            .setMessage("it")
            .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                handleSaveSuccessNavigation()
            }
            .create()
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchLocation()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        blockBackNavigationInAutoFlow(viewModel.autoFlow)
        binding.btnCancel.visibility = View.GONE
        viewModel.recordExists.observe(viewLifecycleOwner) { notIt ->
            notIt?.let { recordExists ->
                val adapter = FormInputAdapter(
                    formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                        viewModel.updateListOnValueChanged(formId, index)
                        hardCodedListUpdate(16)
                    }, isEnabled = !(recordExists || viewModel.viewOnly)
                )
                binding.btnSubmit.isEnabled = !(recordExists || viewModel.viewOnly)
                binding.btnSubmit.visibility = if (recordExists || viewModel.viewOnly) View.GONE else View.VISIBLE
                binding.form.rvInputForm.adapter = adapter
                lifecycleScope.launch {
                    viewModel.formList.collect {
                        if (it.isNotEmpty()) {
                            adapter.notifyItemChanged(viewModel.getIndexOfDate())
                            // isAsymptomatic is auto-computed in-place (same object ref),
                            // DiffUtil won't detect the change — force-rebind it directly.
                            val asymptomaticIdx = viewModel.getIndexOfAsymptomatic()
                            if (asymptomaticIdx >= 0) adapter.notifyItemChanged(asymptomaticIdx)
                            adapter.submitList(it)
                        }

                    }
                }
            }
        }
        viewModel.benName.observe(viewLifecycleOwner) {
            binding.tvBenName.text = it
        }
        viewModel.benAgeGender.observe(viewLifecycleOwner) {
            binding.tvAgeGender.text = it
        }
        // TB Screening: show title above questions and legend (with red * markers) just below title
        binding.tvFormTitle.visibility = View.VISIBLE
        binding.tvFormTitle.text = getString(R.string.check_if_the_person_has_any_of_these_symptoms)
        binding.tvFormFooter.visibility = View.VISIBLE
        val line1 = getString(R.string.tb_screening_legend_xray_sputum) // "* Refer..."
        val line2 = getString(R.string.tb_screening_legend_family_members) // "** Advise..."
        val legendSpan = SpannableStringBuilder("$line1\n$line2")
        val starColor = Color.parseColor("#B00020")
        // Color the leading "*" in line 1
        legendSpan.setSpan(ForegroundColorSpan(starColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Color the leading "**" in line 2
        val line2Start = line1.length + 1 // +1 for \n
        legendSpan.setSpan(ForegroundColorSpan(starColor), line2Start, line2Start + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvFormFooter.text = legendSpan

        captureGeolocation()
        binding.btnSubmit.setOnClickListener {
            submitTBScreeningForm()
        }

        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                TBScreeningFormViewModel.State.SAVE_SUCCESS -> {
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    val alertMessage = viewModel.getFamilyContactAlert()
                    if (alertMessage.isNullOrBlank()) {
                        handleSaveSuccessNavigation()
                    } else {
                        familyContactAlert.setMessage(alertMessage)
                        familyContactAlert.show()
                    }
                }

                TBScreeningFormViewModel.State.SAVE_FAILED -> {
                    Toast.makeText(
                        requireContext(),
                        resources.getString(R.string.something_went_wrong_try_again),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {}
            }
        }
    }

    private fun submitTBScreeningForm() {
        if (validateCurrentPage()) {
            viewModel.saveForm()
        }
    }
    private fun hardCodedListUpdate(formId: Int) {
        binding.form.rvInputForm.adapter?.apply {
            when (formId) {
                1,2,3,4,5,6,7,8,9,10,11,16-> {
                    notifyDataSetChanged()

                }

            }
        }
    }

    private fun handleSaveSuccessNavigation() {
        Toast.makeText(
            requireContext(),
            resources.getString(R.string.tb_screening_submitted), Toast.LENGTH_SHORT
        ).show()
        if (viewModel.autoFlow) {
            // Examine flow — return to AllBenFragment so user picks the next form
            val popped = findNavController().popBackStack(R.id.allBenFragment, false)
            if (!popped) findNavController().navigate(R.id.allBenFragment, bundleOf("source" to 0))
        } else {
            findNavController().navigateUp()
        }
    }

    private fun validateCurrentPage(): Boolean {
        val result = binding.form.rvInputForm.adapter?.let {
            (it as FormInputAdapter).validateInput(resources, binding.form.rvInputForm)
        } ?: -1
        Timber.d("Validation : $result")
        return result == -1
    }

    private fun captureGeolocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        location?.let {
            viewModel.capturedLatitude = it.latitude
            viewModel.capturedLongitude = it.longitude
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
//                is HomeActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.tb_screening_form)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
//                is VolunteerActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.tb_screening_form)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }

                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_screening_form)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_screening_form)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always show back button — matches VitalScreen behaviour.
        // autoFlow only controls the forward-chain (auto-navigate to General OPD
        // after submit), not whether the user can go back.
        applyAutoFlowBackPolicyOnResume(
            isAutoFlow = viewModel.autoFlow,
            allowBack = true
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
