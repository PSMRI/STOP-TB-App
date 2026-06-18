package org.piramalswasthya.stoptb.ui.home_activity.new_household_registration

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentNewHouseholdBinding
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.model.LocationState
import org.piramalswasthya.stoptb.ui.home_activity.new_household_registration.NewHouseholdViewModel.State
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NewHouseholdFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentNewHouseholdBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewHouseholdViewModel by viewModels()

    private var micClickedElementId: Int = -1
    private var editMode: Boolean = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val formatted = value.uppercase()
        viewModel.updateValueByIdAndReturnListIndex(micClickedElementId, formatted)
            .takeIf { it >= 0 }
            ?.let { binding.form.rvInputForm.adapter?.notifyItemChanged(it) }
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                checkSettingsAndFetch()
            } else {
                viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
                val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!showRationale) {
                    showOpenSettingsDialog()
                }
            }
        }

    private val resolveGpsSettings =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                fetchLocationNow()
            } else {
                viewModel.onLocationFailed(LocationState.Failed.GpsDisabled)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewHouseholdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (activity as? VolunteerActivity)?.updateActionBar(
            R.drawable.ic__hh,
            getString(R.string.frag_nhhr_title)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupReasonDropdown()
        setupFormAdapter()
        setupButtons()
        observeViewModel()

        // Auto-capture on first open (prompts for permission if missing)
        if (viewModel.readRecord.value == false) {
            autoCaptureLocation()
        }
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setupReasonDropdown() {
        val reasons = resources.getStringArray(R.array.loc_gps_unavailable_reasons)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reasons)
        binding.acvReason.setAdapter(adapter)
        binding.acvReason.setOnItemClickListener { _, _, position, _ ->
            viewModel.onGpsUnavailableReasonSelected(reasons[position])
            binding.tilReason.error = null
        }
    }

    private fun setupFormAdapter() {
        val adapter = FormInputAdapter(
            formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                when (index) {
                    Konstants.micClickIndex -> {
                        micClickedElementId = formId
                        sttContract.launch(Unit)
                    }
                    else -> viewModel.updateListOnValueChanged(formId, index)
                }
            },
            isEnabled = true
        )
        binding.form.rvInputForm.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formList.collect { list ->
                if (list.isNotEmpty()) adapter.submitList(list)
            }
        }
    }

    private fun setupButtons() {
        // Cancel — show "Are you Sure?" dialog
        binding.btnCancel.setOnClickListener {
            showCancelDialog()
        }

        // Submit
        binding.btnSubmit.setOnClickListener {
            submitForm()
        }

        // Refresh location
        binding.btnRefreshLocation.setOnClickListener {
            refreshLocation()
        }

        // GPS Unavailable checkbox
        binding.cbGpsUnavailable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onGpsUnavailableToggled(isChecked)
            binding.llGpsReason.visibility = if (isChecked) View.VISIBLE else View.GONE
            toggleGpsFields(!isChecked)
            if (!isChecked) {
                binding.acvReason.setText("", false)
                autoCaptureLocation()
            }
        }

        // Edit FAB
        binding.fabEdit.setOnClickListener {
            editMode = true
            viewModel.enableEditMode()
            viewModel.setRecordExists(false)
        }
    }

    private fun observeViewModel() {
        // Record exists → drives view/edit mode
        viewModel.readRecord.observe(viewLifecycleOwner) { recordExists ->
            (activity as? VolunteerActivity)?.updateActionBar(
                R.drawable.ic__hh,
                if (recordExists) getString(R.string.view_household_information)
                else getString(R.string.frag_nhhr_title)
            )
            val isNurse = prefDao.getLoggedInUser()?.role.isNurseRole()
            binding.fabEdit.visibility = if (recordExists && !isNurse) View.VISIBLE else View.GONE
            binding.btnSubmit.visibility = if (!recordExists) View.VISIBLE else View.GONE
            binding.btnCancel.visibility = if (!recordExists) View.VISIBLE else View.GONE
            binding.btnRefreshLocation.isEnabled = !recordExists
            binding.cbGpsUnavailable.isEnabled = !recordExists
            binding.acvReason.isEnabled = !recordExists
            val adapter = binding.form.rvInputForm.adapter as? FormInputAdapter
            adapter?.isEnabled = !recordExists
            adapter?.notifyDataSetChanged()
        }

        // Save state
        viewModel.state.observe(viewLifecycleOwner) { s ->
            when (s) {
                State.IDLE -> Unit
                State.SAVING -> {
                    binding.llContent.visibility = View.GONE
                    binding.pbForm.visibility = View.VISIBLE
                }
                State.SAVE_SUCCESS -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.save_successful), Toast.LENGTH_LONG).show()
                    if (!editMode) {
                        viewModel.setRecordExists(true)
                        showNextScreenAlert()
                        viewModel.resetState()
                    } else {
                        findNavController().navigateUp()
                    }
                }
                State.SAVE_FAILED -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                    Toast.makeText(
                        context,
                        getString(R.string.something_wend_wong_contact_testing),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Location state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locationState.collect { state ->
                updateLocationUI(state)
            }
        }

        // GPS Unavailable state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isGpsUnavailable.collect { unavailable ->
                binding.cbGpsUnavailable.isChecked = unavailable
                binding.llGpsReason.visibility = if (unavailable) View.VISIBLE else View.GONE
                toggleGpsFields(!unavailable)
            }
        }

        // GPS Unavailable reason
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gpsUnavailableReason.collect { reason ->
                if (!reason.isNullOrBlank() && binding.acvReason.text.toString() != reason) {
                    binding.acvReason.setText(reason, false)
                }
            }
        }
    }

    private fun updateLocationUI(state: LocationState) {
        val isEditMode = viewModel.readRecord.value == false
        when (state) {
            is LocationState.Idle -> {
                setStatusText("", "#FF9800")
            }
            is LocationState.Fetching -> {
                setStatusText(getString(R.string.loc_status_fetching), "#FF9800")
                binding.btnRefreshLocation.isEnabled = false
            }
            is LocationState.Captured -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_captured), "#4CAF50")
                binding.etLatitude.setText(String.format(java.util.Locale.ENGLISH, "%.6f", state.lat))
                binding.etLongitude.setText(String.format(java.util.Locale.ENGLISH, "%.6f", state.lon))
                binding.etDigipin.setText(state.digipin)
                binding.etTimestamp.setText(state.timestamp)
            }
            is LocationState.Failed.PermissionDenied -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_permission_denied), "#F44336")
                clearLocationFields()
            }
            is LocationState.Failed.GpsDisabled -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_gps_disabled), "#F44336")
                clearLocationFields()
            }
            is LocationState.Failed.NoSignal -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_failed), "#F44336")
                clearLocationFields()
            }
            is LocationState.Failed.OutsideIndia -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_failed), "#F44336")
                clearLocationFields()
                Toast.makeText(context, getString(R.string.loc_msg_outside_india), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setStatusText(text: String, colorHex: String) {
        binding.tvLocationStatus.text = text
        try {
            binding.tvLocationStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {}
    }

    private fun clearLocationFields() {
        binding.etLatitude.setText("")
        binding.etLongitude.setText("")
        binding.etDigipin.setText("")
        binding.etTimestamp.setText("")
    }

    private fun toggleGpsFields(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        binding.btnRefreshLocation.visibility = v
        binding.tvLocationStatus.visibility = v
        binding.tilLatitude.visibility = v
        binding.tilLongitude.visibility = v
        binding.tilDigipin.visibility = v
        binding.tilTimestamp.visibility = v
    }

    private fun autoCaptureLocation() {
        if (hasLocationPermission()) {
            checkSettingsAndFetch()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun refreshLocation() {
        when {
            !hasLocationPermission() -> {
                requestLocationPermission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            else -> checkSettingsAndFetch()
        }
    }

    private fun checkSettingsAndFetch() {
        viewModel.setFetching()
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        val client = LocationServices.getSettingsClient(requireActivity())
        client.checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                fetchLocationNow()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val request = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        resolveGpsSettings.launch(request)
                    } catch (e: IntentSender.SendIntentException) {
                        Timber.e(e, "Could not launch GPS settings dialog")
                        viewModel.onLocationFailed(LocationState.Failed.GpsDisabled)
                    }
                } else {
                    viewModel.onLocationFailed(LocationState.Failed.GpsDisabled)
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationNow() {
        if (!hasLocationPermission()) {
            viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
            return
        }
        viewModel.setFetching()

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.onLocationResult(location.latitude, location.longitude)
                } else {
                    // Fall back to last known location
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLocation ->
                            if (lastLocation != null) {
                                viewModel.onLocationResult(lastLocation.latitude, lastLocation.longitude)
                            } else {
                                viewModel.onLocationFailed(LocationState.Failed.NoSignal)
                                if (isAdded) {
                                    Toast.makeText(context, getString(R.string.loc_msg_no_signal), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .addOnFailureListener {
                            viewModel.onLocationFailed(LocationState.Failed.NoSignal)
                        }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "GPS fetch failed")
                viewModel.onLocationFailed(LocationState.Failed.NoSignal)
                if (isAdded) {
                    Toast.makeText(context, getString(R.string.loc_timeout_msg), Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    private fun showOpenSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.loc_section_title))
            .setMessage(getString(R.string.loc_msg_enable_gps))
            .setPositiveButton(getString(R.string.loc_dialog_open_settings)) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                )
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }


    private fun submitForm() {
        activity?.currentFocus?.clearFocus()
        if (!validateFormPage()) return
        if (!validateLocationSection()) return
        viewModel.saveForm()
    }

    private fun validateFormPage(): Boolean {
        val result = (binding.form.rvInputForm.adapter as? FormInputAdapter)?.validateInput(resources)
        return if (result == -1) true
        else {
            result?.let { binding.form.rvInputForm.scrollToPosition(it) }
            false
        }
    }

    private fun validateLocationSection(): Boolean {
        if (viewModel.isLocationValid()) return true
        if (viewModel.isGpsUnavailable.value && viewModel.gpsUnavailableReason.value.isNullOrBlank()) {
            binding.tilReason.error = getString(R.string.loc_err_reason_required)
            binding.nestedScroll.post {
                binding.nestedScroll.smoothScrollTo(0, binding.cardLocation.top)
            }
        } else {
            Toast.makeText(context, getString(R.string.loc_err_location_required), Toast.LENGTH_LONG).show()
            binding.nestedScroll.post {
                binding.nestedScroll.smoothScrollTo(0, binding.cardLocation.top)
            }
        }
        return false
    }


    private fun showCancelDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_cancel_title))
            .setMessage(getString(R.string.dialog_cancel_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                dialog.dismiss()
                if (isAdded) {
                    val popped = findNavController().popBackStack(R.id.homeFragment, false)
                    if (!popped) {
                        findNavController().popBackStack(R.id.volunteerHomeFragment, false)
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
                // Do nothing — preserve all form data
            }
            .show()
    }

    private fun showNextScreenAlert() {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.patient_registered_successfully))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ok)) { successDialog, _ ->
                successDialog.dismiss()
                if (isAdded) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.proceed_to_register_hof))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                            dialog.dismiss()
                            if (isAdded) {
                                findNavController().navigate(
                                    NewHouseholdFragmentDirections.actionNewHouseholdFragmentToNewBenRegFragment(
                                        hhId = viewModel.getHHId(),
                                        relToHeadId = 18
                                    )
                                )
                            }
                        }
                        .setNegativeButton(getString(R.string.no)) { dialog, _ ->
                            dialog.dismiss()
                            if (isAdded) findNavController().navigateUp()
                        }
                        .show()
                }
            }
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
