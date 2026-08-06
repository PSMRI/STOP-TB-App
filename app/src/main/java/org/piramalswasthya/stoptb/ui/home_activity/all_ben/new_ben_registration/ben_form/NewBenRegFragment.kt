package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form

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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.databinding.AlertConsentBinding
import org.piramalswasthya.stoptb.databinding.FragmentNewBenRegBinding
import org.piramalswasthya.stoptb.databinding.LayoutViewMediaBinding
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.model.LocationState
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel.State
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class NewBenRegFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentNewBenRegBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewBenRegViewModel by viewModels()

    // ─── Speech to text ──────────────────────────────────────────────────
    private var micClickedElementId: Int = -1
    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val formatted = value.uppercase()
        viewModel.updateValueByIdAndReturnListIndex(micClickedElementId, formatted)
            .takeIf { it >= 0 }
            ?.let { binding.form.rvInputForm.adapter?.notifyItemChanged(it) }
    }

    // ─── Camera ──────────────────────────────────────────────────────────
    private var latestTmpUri: Uri? = null
    private var continuePreviewAfterPhoto = false
    private val takePicture = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            latestTmpUri?.let { uri -> validateFaceAndAcceptPhoto(uri) }
        } else {
            continuePreviewAfterPhoto = false
        }
    }

    // ─── Location (standalone only) ──────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                checkSettingsAndFetch()
            } else {
                val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!showRationale) showOpenSettingsDialog()
                else viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
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

    // ─── Inflate ─────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewBenRegBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
    }

    // ─── onViewCreated ───────────────────────────────────────────────────
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())



        // Back press — show discard dialog in edit mode
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val isEditMode = viewModel.recordExists.value == false
                    if (isEditMode) showDiscardDialog()
                    else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        // Toolbar back arrow — same discard logic as back press
        activity?.let { act ->
            val toolbar = when (act) {
                is HomeActivity -> act.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                is VolunteerActivity -> act.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
                else -> null
            }
            toolbar?.setNavigationOnClickListener {
                if (!isAdded) return@setNavigationOnClickListener
                val isEditMode = viewModel.recordExists.value == false
                if (isEditMode) showDiscardDialog()
                else {
                    try { findNavController().popBackStack() }
                    catch (e: Exception) { Timber.e(e); requireActivity().onBackPressedDispatcher.onBackPressed() }
                }
            }
        }

        binding.cvPatientInformation.visibility = View.GONE

        // Location section
        binding.cardLocation.visibility = View.VISIBLE
        if (viewModel.isStandalone) {
            setupLocationSection()
            if (viewModel.recordExists.value == false && viewModel.getIsConsentAgreed()) {
                captureLocationSilently()
            }
        } else {
            setupLinkedLocationSection()
        }

        // Submit button
        binding.btnSubmit.setOnClickListener {
            if (validateCurrentPage()) {
                showPreview()
            }
        }

        binding.btnLinkHousehold.setOnClickListener {
            triggerLinkHouseholdFlow(viewModel.benIdFromArgs)
        }

        binding.btnCancel.setOnClickListener {
            val isEditMode = viewModel.recordExists.value == false
            if (isEditMode) showDiscardDialog()
            else {
                findNavController().popBackStack()
            }
        }

        // Death badge visibility
        val currentRole = prefDao.getLoggedInUser()?.role
        val isNurse = currentRole.isNurseRole()
        val isCounsellingOfficer = currentRole.isCounsellingOfficerRole()
        viewModel.isDeath.observe(viewLifecycleOwner) { isDeath ->
            val recordExists = viewModel.recordExists.value ?: false
            binding.fabEdit.visibility = if (isDeath || !recordExists || isNurse || isCounsellingOfficer) View.GONE else View.VISIBLE
        }

        // Set up adapter
        val adapter = FormInputAdapter(
            imageClickListener = FormInputAdapter.ImageClickListener {
                viewModel.setCurrentImageFormId(it)
                takeImage()
            },
            formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                when (index) {
                    Konstants.micClickIndex -> {
                        micClickedElementId = formId
                        sttContract.launch(Unit)
                    }
                    else -> {
                        viewModel.updateListOnValueChanged(formId, index)
                        hardCodedListUpdate(formId)
                    }
                }
            },
            sendOtpClickListener = FormInputAdapter.SendOtpClickListener { _, _, _, _, _, _, _ -> },
            selectImageClickListener = FormInputAdapter.SelectUploadImageClickListener { },
            viewDocumentListner = FormInputAdapter.ViewDocumentOnClick { },
            isEnabled = true,
        )
        binding.form.rvInputForm.adapter = adapter
        binding.form.rvInputForm.itemAnimator = null

        lifecycleScope.launch {
            viewModel.formList.collect {
                if (it.isNotEmpty()) adapter.submitList(it)
            }
        }

        // Loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                if (loading) {
                    binding.llContent.visibility = View.GONE
                    binding.llLoading.visibility = View.VISIBLE
                } else {
                    binding.llContent.visibility = View.VISIBLE
                    binding.llLoading.visibility = View.GONE
                }
            }
        }

        // Record exists observer
        viewModel.recordExists.observe(viewLifecycleOwner) { recordExists ->
            binding.fabEdit.visibility = if (recordExists && !isNurse) View.VISIBLE else View.GONE
            binding.btnSubmit.visibility = if (recordExists) View.GONE else View.VISIBLE
            binding.btnLinkHousehold.visibility = if (!recordExists && viewModel.showLinkHouseholdButton) View.VISIBLE else View.GONE
            adapter.isEnabled = !recordExists
            if (viewModel.isStandalone) {
                binding.btnRefreshLocation.isEnabled = !recordExists
                binding.cbGpsUnavailable.isEnabled = !recordExists
                binding.acvReason.isEnabled = !recordExists
            }
            if (!recordExists && !viewModel.getIsConsentAgreed()) consentAlert.show()
        }

        // State observer
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state!!) {
                State.IDLE -> { }
                State.SAVING -> {
                    binding.llContent.visibility = View.GONE
                    binding.pbForm.visibility = View.VISIBLE
                }
                State.SAVE_SUCCESS -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                    Toast.makeText(context, resources.getString(R.string.save_successful), Toast.LENGTH_LONG).show()
                    try {
                        WorkerUtils.triggerAmritPushWorker(requireContext())
                        if (viewModel.isNonHHArg) {
                            val popped = findNavController().popBackStack(R.id.nonHHFragment, false)
                            if (!popped) findNavController().navigateUp()
                        } else if (viewModel.relToHeadId == 18) {
                            val popped = findNavController().popBackStack(R.id.allHouseholdFragment, false)
                            if (!popped) findNavController().navigateUp()
                        } else {
                            findNavController().navigateUp()
                        }
                    } catch (e: Exception) { Timber.e(e) }
                }
                State.SAVE_FAILED -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                    Toast.makeText(context, resources.getString(R.string.something_wend_wong_contact_testing), Toast.LENGTH_LONG).show()
                }
            }
        }

        // Edit FAB
        binding.fabEdit.setOnClickListener {
            viewModel.setRecordExist(false)
            viewModel.enableEditMode()
            binding.fabEdit.visibility = View.GONE
            binding.btnSubmit.visibility = View.VISIBLE
            adapter.isEnabled = true
            adapter.notifyDataSetChanged()
        }
    }

    // ─── Location section setup (standalone only) ─────────────────────────
    private fun setupLocationSection() {
        val reasons = resources.getStringArray(R.array.loc_gps_unavailable_reasons)
        val reasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reasons)
        binding.acvReason.setAdapter(reasonAdapter)
        binding.acvReason.setOnItemClickListener { _, _, position, _ ->
            viewModel.onGpsUnavailableReasonSelected(reasons[position])
            binding.tilReason.error = null
        }

        binding.btnRefreshLocation.setOnClickListener { refreshLocation() }

        binding.cbGpsUnavailable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onGpsUnavailableToggled(isChecked)
            binding.llGpsReason.visibility = if (isChecked) View.VISIBLE else View.GONE
            toggleGpsFields(!isChecked)
            if (!isChecked) {
                binding.acvReason.setText("", false)
                captureLocationSilently()
            }
        }

        // Observe location state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locationState.collect { state -> updateLocationUI(state) }
        }

        // Observe GPS unavailable toggle
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isGpsUnavailable.collect { unavailable ->
                binding.cbGpsUnavailable.isChecked = unavailable
                binding.llGpsReason.visibility = if (unavailable) View.VISIBLE else View.GONE
                toggleGpsFields(!unavailable)
            }
        }

        // Observe GPS unavailable reason
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gpsUnavailableReason.collect { reason ->
                if (!reason.isNullOrBlank() && binding.acvReason.text.toString() != reason) {
                    binding.acvReason.setText(reason, false)
                }
            }
        }
    }

    private fun setupLinkedLocationSection() {
        // Disable refresh button, GPS capture, DIGIPIN generation, and GPS unavailable checkbox
        binding.btnRefreshLocation.visibility = View.GONE
        binding.cbGpsUnavailable.isEnabled = false
        binding.acvReason.isEnabled = false

        // Observe location state to populate fields read-only
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locationState.collect { state ->
                updateLocationUI(state)
                binding.btnRefreshLocation.visibility = View.GONE
                binding.cbGpsUnavailable.isEnabled = false
                binding.acvReason.isEnabled = false
            }
        }

        // Observe GPS unavailable toggle to set checkbox & hide/show fields
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isGpsUnavailable.collect { unavailable ->
                binding.cbGpsUnavailable.isChecked = unavailable
                binding.llGpsReason.visibility = if (unavailable) View.VISIBLE else View.GONE
                toggleGpsFields(!unavailable)
                binding.btnRefreshLocation.visibility = View.GONE
                binding.cbGpsUnavailable.isEnabled = false
                binding.acvReason.isEnabled = false
            }
        }

        // Observe GPS unavailable reason
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gpsUnavailableReason.collect { reason ->
                if (!reason.isNullOrBlank() && binding.acvReason.text.toString() != reason) {
                    binding.acvReason.setText(reason, false)
                }
            }
        }
    }

    // ─── Location UI updates ─────────────────────────────────────────────
    private fun updateLocationUI(state: LocationState) {
        val isEditMode = viewModel.recordExists.value == false
        when (state) {
            is LocationState.Idle -> setStatusText("", "#FF9800")
            is LocationState.Fetching -> {
                setStatusText(getString(R.string.loc_status_fetching), "#FF9800")
                binding.btnRefreshLocation.isEnabled = false
            }
            is LocationState.Captured -> {
                binding.btnRefreshLocation.isEnabled = isEditMode
                setStatusText(getString(R.string.loc_status_captured), "#4CAF50")
                binding.etLatitude.setText(String.format(java.util.Locale.ENGLISH, "%.6f", state.lat))
                binding.etLongitude.setText(String.format(java.util.Locale.ENGLISH, "%.6f", state.lon))
                binding.etDigipin.setText(formatDigiPin(state.digipin))
                binding.etTimestamp.setText(formatEpochToDateTime(state.timestamp))
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

    private fun formatDigiPin(pin: String): String {
        val clean = pin.replace("-", "")
        if (clean.length == 10) {
            return "${clean.substring(0, 4)}-${clean.substring(4, 8)}-${clean.substring(8, 10)}"
        }
        return pin
    }

    private fun formatEpochToDateTime(epochStr: String): String {
        return epochStr.toLongOrNull()?.let {
            try {
                java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", java.util.Locale.ENGLISH)
                    .format(java.util.Date(it))
            } catch (e: Exception) {
                epochStr
            }
        } ?: epochStr
    }

    private fun setStatusText(text: String, colorHex: String) {
        binding.tvLocationStatus.text = text
        try { binding.tvLocationStatus.setTextColor(android.graphics.Color.parseColor(colorHex)) }
        catch (_: Exception) {}
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

    // ─── Location capture ─────────────────────────────────────────────────
    private fun captureLocationSilently() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            checkSettingsAndFetch()
        }
    }

    private fun refreshLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            checkSettingsAndFetch()
        }
    }

    private fun checkSettingsAndFetch() {
        viewModel.setFetching()
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdates(1)
            .build()
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        LocationServices.getSettingsClient(requireActivity())
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener { fetchLocationNow() }
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
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) viewModel.onLocationResult(last.latitude, last.longitude)
                            else {
                                viewModel.onLocationFailed(LocationState.Failed.NoSignal)
                                if (isAdded) Toast.makeText(context, getString(R.string.loc_msg_no_signal), Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { viewModel.onLocationFailed(LocationState.Failed.NoSignal) }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "GPS fetch failed")
                viewModel.onLocationFailed(LocationState.Failed.NoSignal)
                if (isAdded) Toast.makeText(context, getString(R.string.loc_timeout_msg), Toast.LENGTH_LONG).show()
            }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

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

    // ─── Location validation (standalone only) ───────────────────────────
    private fun validateLocationSection(): Boolean {
        if (viewModel.isLocationValid()) return true
        if (viewModel.isGpsUnavailable.value && viewModel.gpsUnavailableReason.value.isNullOrBlank()) {
            binding.tilReason.error = getString(R.string.loc_err_reason_required)
            binding.nestedScroll.post { binding.nestedScroll.smoothScrollTo(0, binding.cardLocation.top) }
        } else {
            Toast.makeText(context, getString(R.string.loc_err_location_required), Toast.LENGTH_LONG).show()
            binding.nestedScroll.post { binding.nestedScroll.smoothScrollTo(0, binding.cardLocation.top) }
        }
        return false
    }

    // ─── onStart ──────────────────────────────────────────────────────────
    override fun onStart() {
        super.onStart()
        val isNonHH = viewModel.isNonHHArg || (viewModel.benIdFromArgs != 0L && viewModel.showLinkHouseholdButton)
        val title = when {
            isNonHH                     -> getString(R.string.title_new_non_hh_reg)
            viewModel.relToHeadId == 18 -> getString(R.string.title_new_ben_reg_hof)
            viewModel.relToHeadId > 0   -> getString(R.string.title_new_ben_reg_non_hof)
            else                        -> getString(R.string.frag_new_ben_reg_type_title)
        }
        val icon = if (viewModel.relToHeadId == 18) R.drawable.ic__hh else R.drawable.ic__ben
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(icon, title)
                is VolunteerActivity -> it.updateActionBar(icon, title)
            }
        }
    }

    // ─── Consent popup ───────────────────────────────────────────────────
    private val consentAlert by lazy {
        val alertBinding = AlertConsentBinding.inflate(layoutInflater, binding.root, false)
        alertBinding.textView4.text = resources.getString(R.string.consent_alert_title)
        alertBinding.scrollableText.text = resources.getString(R.string.consent_text)
        alertBinding.scrollableText.movementMethod = android.text.method.ScrollingMovementMethod()

        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(alertBinding.root)
            .setCancelable(false)
            .create()

        alertBinding.scrollableText.setOnClickListener {
            alertBinding.checkBox.isChecked = !alertBinding.checkBox.isChecked
        }
        alertBinding.btnNegative.setOnClickListener {
            alertDialog.dismiss()
            try { findNavController().navigateUp() } catch (e: Exception) { alertDialog.dismiss() }
        }
        alertBinding.btnPositive.setOnClickListener {
            if (alertBinding.checkBox.isChecked) {
                viewModel.setConsentAgreed()
                alertDialog.dismiss()
                if (viewModel.isStandalone && viewModel.recordExists.value == false) {
                    captureLocationSilently()
                }
            } else {
                Toast.makeText(context, resources.getString(R.string.please_tick_the_checkbox), Toast.LENGTH_SHORT).show()
            }
        }
        alertDialog
    }

    // ─── Cancel confirmation ─────────────────────────────────────────────
    private fun showDiscardDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.discard_changes_title))
            .setMessage(getString(R.string.discard_changes_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.discard)) { dialog, _ ->
                dialog.dismiss()
                if (isAdded) {
                    val popped = findNavController().popBackStack(R.id.homeFragment, false)
                    if (!popped) {
                        findNavController().popBackStack(R.id.volunteerHomeFragment, false)
                    }
                }
            }
            .setNegativeButton(getString(R.string.stay)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ─── Preview bottom sheet ────────────────────────────────────────────
    private fun showPreview() {
        lifecycleScope.launch {
            val previewItems = try {
                viewModel.getFormPreviewData()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.something_wend_wong_contact_testing), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sheet = PreviewBottomSheet()
            sheet.setData(previewItems)
            sheet.setCallbacks(
                onEdit   = { },
                onSubmit = { if (validateCurrentPage()) viewModel.saveForm() }
            )
            sheet.show(parentFragmentManager, "ben_preview_sheet")
        }
    }

    private fun handlePhotoReminderBeforePreview() {

        if (viewModel.isDeathSelected()) {
            showPreview()
            return
        }

        if (viewModel.hasBeneficiaryPhoto()) {
            showPreview()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.do_you_like_to_take_photo))
            .setPositiveButton(android.R.string.yes) { dialog, _ ->
                dialog.dismiss()
                continuePreviewAfterPhoto = true
                takeImage()
            }
            .setNegativeButton(android.R.string.no) { dialog, _ ->
                dialog.dismiss()
                showPreview()
            }
            .show()
    }

    // ─── Validation ──────────────────────────────────────────────────────
    private fun validateCurrentPage(): Boolean {
        val result = (binding.form.rvInputForm.adapter as? FormInputAdapter)
            ?.validateInput(resources, binding.form.rvInputForm) ?: -1
        return result == -1
    }

    // ─── Hard-coded adapter refresh for specific formIds ─────────────────
    private fun hardCodedListUpdate(formId: Int) {
        binding.form.rvInputForm.adapter?.apply {
            when (formId) {
                1008 -> notifyDataSetChanged()
                1012 -> { if (viewModel.getAgeAtMarriageLength() >= 2) notifyDataSetChanged() }
                9    -> notifyDataSetChanged()
                115  -> notifyDataSetChanged()
                12   -> notifyDataSetChanged()
                1052 -> notifyDataSetChanged()
            }
        }
    }

    // ─── Camera ──────────────────────────────────────────────────────────
    private fun takeImage() {
        lifecycleScope.launchWhenStarted {
            latestTmpUri = getTmpFileUri()
            takePicture.launch(latestTmpUri)
        }
    }

    private fun validateFaceAndAcceptPhoto(uri: Uri) {
        val image = runCatching { InputImage.fromFilePath(requireContext(), uri) }
            .getOrElse {
                Timber.e(it, "Unable to read captured beneficiary photo")
                Toast.makeText(requireContext(), getString(R.string.unable_to_validate_photo), Toast.LENGTH_SHORT).show()
                continuePreviewAfterPhoto = false
                return
            }

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    viewModel.setImageUriToFormElement(uri)
                    binding.form.rvInputForm.adapter?.notifyItemChanged(0)
                    if (continuePreviewAfterPhoto) {
                        continuePreviewAfterPhoto = false
                        showPreview()
                    }
                } else {
                    val shouldContinueAfterRetake = continuePreviewAfterPhoto
                    continuePreviewAfterPhoto = false
                    showFaceNotDetectedDialog(shouldContinueAfterRetake)
                }
            }
            .addOnFailureListener {
                Timber.e(it, "Beneficiary face detection failed")
                Toast.makeText(requireContext(), getString(R.string.unable_to_validate_photo), Toast.LENGTH_SHORT).show()
                continuePreviewAfterPhoto = false
            }
            .addOnCompleteListener { detector.close() }
    }

    private fun showFaceNotDetectedDialog(continueAfterRetake: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.human_face_not_detected))
            .setPositiveButton(getString(R.string.retake)) { dialog, _ ->
                dialog.dismiss()
                continuePreviewAfterPhoto = continueAfterRetake
                takeImage()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile(Konstants.tempBenImagePrefix, null, requireActivity().cacheDir)
            .apply { createNewFile() }
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", tmpFile)
    }

    // ─── View image ──────────────────────────────────────────────────────
    private fun viewImage(imageUri: Uri) {
        val viewImageBinding = LayoutViewMediaBinding.inflate(layoutInflater, binding.root, false)
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(viewImageBinding.root)
            .setCancelable(true)
            .create()
        Glide.with(this).load(imageUri).placeholder(R.drawable.ic_person).into(viewImageBinding.viewImage)
        viewImageBinding.btnClose.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.let { act ->
            when (act) {
                is HomeActivity -> act.restoreToolbarNavigation()
                is VolunteerActivity -> act.restoreToolbarNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun triggerLinkHouseholdFlow(benId: Long) {
        val options = arrayOf("Link to Existing Household", "Create New Household")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Link Household")
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    showExistingHouseholdSelectionDialog(benId)
                } else {
                    findNavController().navigate(
                        NewBenRegFragmentDirections.actionNewBenRegFragmentToNewHouseholdFragment(
                            hhId = 0L,
                            isAshaFamily = "No",
                            linkBenId = benId
                        )
                    )
                }
            }
            .show()
    }

    private fun showExistingHouseholdSelectionDialog(benId: Long) {
        lifecycleScope.launch {
            val hhList = viewModel.hhList.firstOrNull()
            if (hhList.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No existing households found in this village", Toast.LENGTH_SHORT).show()
                return@launch
            }

            data class HouseholdSearchItem(
                val displayText: String,
                val hhId: Long,
                val headName: String,
                val famName: String
            )

            val searchItems = hhList.map { hh ->
                val headName = hh.household.family?.familyHeadName ?: "No Head Name"
                val famName = hh.household.family?.familyName ?: ""
                val displayText = "Head: $headName $famName (HH ID: ${hh.household.householdId})"
                HouseholdSearchItem(displayText, hh.household.householdId, headName, famName)
            }

            val context = requireContext()
            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val paddingPx = (16 * context.resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, 0)
            }

            val searchInput = android.widget.EditText(context).apply {
                hint = "Search by name or ID..."
                setSingleLine(true)
                maxLines = 1
                val searchIcon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search)
                setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null)
                compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
                val p = (10 * context.resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
            }
            container.addView(searchInput)

            val adapterList = searchItems.map { it.displayText }.toMutableList()
            val arrayAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_list_item_1, adapterList)

            var filteredItems = searchItems.toList()

            val listView = android.widget.ListView(context).apply {
                adapter = arrayAdapter
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (300 * context.resources.displayMetrics.density).toInt()
                )
            }
            container.addView(listView)

            val tvEmptyResult = android.widget.TextView(context).apply {
                text = "No matching result found"
                gravity = android.view.Gravity.CENTER
                visibility = android.view.View.GONE
                val p = (16 * context.resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            container.addView(tvEmptyResult)

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Select Household")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .create()

            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    filteredItems = if (query.isEmpty()) {
                        searchItems
                    } else {
                        searchItems.filter { item ->
                            item.headName.contains(query, ignoreCase = true) ||
                            item.famName.contains(query, ignoreCase = true) ||
                            item.hhId.toString().contains(query, ignoreCase = true)
                        }
                    }
                    arrayAdapter.clear()
                    arrayAdapter.addAll(filteredItems.map { it.displayText })
                    arrayAdapter.notifyDataSetChanged()

                    if (filteredItems.isEmpty()) {
                        listView.visibility = android.view.View.GONE
                        tvEmptyResult.visibility = android.view.View.VISIBLE
                    } else {
                        listView.visibility = android.view.View.VISIBLE
                        tvEmptyResult.visibility = android.view.View.GONE
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            listView.setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                if (position in filteredItems.indices) {
                    val selected = filteredItems[position]
                    showRelationshipSelectionDialog(benId, selected.hhId)
                }
            }

            dialog.show()
        }
    }

    private fun showRelationshipSelectionDialog(benId: Long, hhId: Long) {
        lifecycleScope.launch {
            val ben = viewModel.getBenFromId(benId) ?: return@launch
            val isFemale = ben.genderId == 2 || ben.gender?.name?.equals("FEMALE", ignoreCase = true) == true

            val relations = if (isFemale) {
                arrayOf(
                    "Mother",
                    "Sister",
                    "Wife",
                    "Niece",
                    "Daughter",
                    "Grand Mother",
                    "Mother in Law",
                    "Grand Daughter",
                    "Daughter in Law",
                    "Sister in Law",
                    "Other"
                )
            } else {
                arrayOf(
                    "Father",
                    "Brother",
                    "Husband",
                    "Nephew",
                    "Son",
                    "Grand Father",
                    "Father in Law",
                    "Grand Son",
                    "Son in Law",
                    "Other"
                )
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Relationship to HOF")
                .setItems(relations, android.content.DialogInterface.OnClickListener { dialog, index ->
                    dialog.dismiss()
                    val selectedRelation = relations[index]
                    val (relationPos, relationName) = when (selectedRelation) {
                        "Mother" -> Pair(1, "Mother")
                        "Father" -> Pair(2, "Father")
                        "Brother" -> Pair(3, "Brother")
                        "Sister" -> Pair(4, "Sister")
                        "Wife" -> Pair(5, "Wife")
                        "Husband" -> Pair(6, "Husband")
                        "Nephew" -> Pair(7, "Nephew")
                        "Niece" -> Pair(8, "Niece")
                        "Son" -> Pair(9, "Son")
                        "Daughter" -> Pair(10, "Daughter")
                        "Grand Father" -> Pair(11, "Grand Father")
                        "Grand Mother" -> Pair(12, "Grand Mother")
                        "Father in Law" -> Pair(13, "Father in Law")
                        "Mother in Law" -> Pair(14, "Mother in Law")
                        "Grand Son" -> Pair(15, "Grand Son")
                        "Grand Daughter" -> Pair(16, "Grand Daughter")
                        "Son in Law" -> Pair(17, "Son in Law")
                        "Daughter in Law" -> Pair(18, "Daughter in Law")
                        "Sister in Law" -> Pair(20, "Sister in Law")
                        else -> Pair(21, "Other")
                    }
                    viewModel.linkBenToHousehold(hhId, relationPos, relationName)
                    Toast.makeText(requireContext(), "Linked to household successfully", Toast.LENGTH_SHORT).show()
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerAmritPushWorker(requireContext())
                    findNavController().navigateUp()
                })
                .show()
        }
    }
}
