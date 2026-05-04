package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.form

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.annotation.SuppressLint
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
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber

@AndroidEntryPoint
class TBScreeningFormFragment : Fragment() {

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
        viewModel.recordExists.observe(viewLifecycleOwner) { notIt ->
            notIt?.let { recordExists ->
                val adapter = FormInputAdapter(
                    formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                        viewModel.updateListOnValueChanged(formId, index)
                    }, isEnabled = !(recordExists || viewModel.viewOnly)
                )
                binding.btnSubmit.isEnabled = !(recordExists || viewModel.viewOnly)
                binding.btnSubmit.visibility = if (viewModel.viewOnly) View.GONE else View.VISIBLE
                binding.form.rvInputForm.adapter = adapter
                lifecycleScope.launch {
                    viewModel.formList.collect {
                        if (it.isNotEmpty()) {
                            adapter.notifyItemChanged(viewModel.getIndexOfDate())
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
        captureGeolocation()
        binding.btnSubmit.setOnClickListener {
            submitTBScreeningForm()
        }

        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                TBScreeningFormViewModel.State.SAVE_SUCCESS -> {
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

    private fun handleSaveSuccessNavigation() {
        Toast.makeText(
            requireContext(),
            resources.getString(R.string.tb_screening_submitted), Toast.LENGTH_SHORT
        ).show()
        if (viewModel.autoFlow) {
            findNavController().navigate(
                R.id.vitalScreenFragment,
                bundleOf(
                    "benId" to viewModel.benId,
                    "benRegId" to viewModel.benRegId,
                    "autoFlow" to true
                )
            )
        } else {
            findNavController().navigateUp()
        }
    }

    private fun validateCurrentPage(): Boolean {
        val result = binding.form.rvInputForm.adapter?.let {
            (it as FormInputAdapter).validateInput(resources)
        }
        Timber.d("Validation : $result")
        return if (result == -1) true
        else {
            if (result != null) {
                binding.form.rvInputForm.scrollToPosition(result)
            }
            false
        }
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
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_screening_form)
                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_screening_form)
                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (viewModel.autoFlow) {
            (activity as? HomeActivity)?.setToolbarNavigationVisible(true)
            (activity as? VolunteerActivity)?.setToolbarNavigationVisible(true)
        }
        _binding = null
    }

}
