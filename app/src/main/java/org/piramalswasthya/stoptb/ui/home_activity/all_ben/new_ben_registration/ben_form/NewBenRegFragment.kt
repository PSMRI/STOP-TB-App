package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.databinding.AlertConsentBinding
import org.piramalswasthya.stoptb.databinding.FragmentNewBenRegBinding
import org.piramalswasthya.stoptb.databinding.LayoutViewMediaBinding
import org.piramalswasthya.stoptb.helpers.GpsDiagnostics
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.model.LocationState
import org.piramalswasthya.stoptb.model.Permission
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel.State
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class NewBenRegFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    @Inject
    lateinit var roleManager: RoleManager

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
    // Acquired directly from the OS
    private lateinit var locationManager: LocationManager
    private var gpsWatchdogJob: Job? = null
    private var activeLocationListener: LocationListener? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var activeCancellationTokenSource: CancellationTokenSource? = null

    private var isEditingExisting = false
    private var updateLocationRequested = false

    private var originalLocationState: LocationState? = null

    private var originalCampSite = false
    private var suppressRadioCallbacks = false
    private var fetchTimeoutJob: Job? = null

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                checkSettingsAndFetch()
            } else {
                viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
                val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!showRationale) showOpenSettingsDialog()
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

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager



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
        setupLocationSection()
        if (viewModel.isStandalone && viewModel.recordExists.value == false
            && !isEditingExisting && viewModel.getIsConsentAgreed()) {
            captureLocationSilently()
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
        viewModel.isDeath.observe(viewLifecycleOwner) { isDeath ->
            val recordExists = viewModel.recordExists.value ?: false
            // Can fire after the recordExists observer below — needs the same permission gate,
            // or it silently re-shows the Edit FAB once isDeath loads.
            binding.fabEdit.visibility =
                if (isDeath || !recordExists || !canEditRecord()) View.GONE else View.VISIBLE
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
                        if (formId == 1060) onUpdateLocationToggled(index)

                    }
                }
            },
            sendOtpClickListener = FormInputAdapter.SendOtpClickListener { _, _, _, _, _, _, _ -> },
            selectImageClickListener = FormInputAdapter.SelectUploadImageClickListener { },
            viewDocumentListner = FormInputAdapter.ViewDocumentOnClick { },
            pencilEditClickListener = FormInputAdapter.PencilEditClickListener { formId ->
                val idx = viewModel.enableFieldEditAndGetIndex(formId)
                if (idx >= 0) binding.form.rvInputForm.adapter?.notifyItemChanged(idx)
            },
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
            // Edit FAB is the only way back into edit mode — hide it below full permission.
            binding.fabEdit.visibility = if (recordExists && canEditRecord()) View.VISIBLE else View.GONE
            binding.btnSubmit.visibility = if (recordExists) View.GONE else View.VISIBLE
            binding.btnLinkHousehold.visibility = if (!recordExists && viewModel.showLinkHouseholdButton) View.VISIBLE else View.GONE
            adapter.isEnabled = !recordExists
            updateLocationControls()
            updateCampSiteBox()
            updateCampSiteBox()
            updateSubmitButtonState()
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
                    Toast.makeText(context, resources.getString(R.string.registration_successful), Toast.LENGTH_LONG).show()
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
            isEditingExisting = true
            originalLocationState = viewModel.locationState.value
            originalCampSite = viewModel.isCampSite.value
            updateLocationRequested = false

            viewModel.setLocationOverridden(false)
            suppressRadioCallbacks = true
            binding.rbUpdateNo.isChecked = true
            binding.rgCampSite.check(if (originalCampSite) R.id.rb_camp_yes else R.id.rb_camp_no)
            suppressRadioCallbacks = false
            viewModel.setRecordExist(false)
            viewModel.enableEditMode()
            binding.fabEdit.visibility = View.GONE
            binding.btnSubmit.visibility = View.VISIBLE
            adapter.isEnabled = true
            adapter.notifyDataSetChanged()
        }
    }

    // Both fabEdit observers must call this rather than recompute their own check — isDeath
    // and recordExists are independent LiveData that can fire in either order.
    private fun canEditRecord(): Boolean {
        val union = roleManager.privilegesUnion()
        return if (viewModel.isNonHHArg) {
            union.nonHouseholdPermission == Permission.FULL
        } else {
            union.beneficiaryPermission == Permission.FULL
        }
    }

    // ─── Location section setup (standalone only) ─────────────────────────
    private fun setupLocationSection() {
        binding.btnRefreshLocation.setOnClickListener { refreshLocation() }

        binding.rgUpdateLocation.setOnCheckedChangeListener { _, checkedId ->
            if (!suppressRadioCallbacks) onUpdateLocationChanged(checkedId == R.id.rb_update_yes)
        }
        binding.rgCampSite.setOnCheckedChangeListener { _, checkedId ->
            if (!suppressRadioCallbacks) onCampSiteChoiceChanged(checkedId == R.id.rb_camp_yes)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locationState.collect { state -> updateLocationUI(state) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locationDetail.collect { detail -> updateLocationDetailText(detail) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isCampSite.collect {
                updateCampSiteBox()
                updateLocationControls()
                updateSubmitButtonState()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.gpsUnavailableReason.collect { updateCampSiteBox() }
        }
    }


    // Shows the "registration done at camp site" box instead of the DIGIPIN section when the
    // record has no GPS (camp site / unavailable) and the user is not actively editing it.
    private fun updateCampSiteBox() {
        if (_binding == null) return
        val showBox = viewModel.isCampSite.value
        binding.llGpsSection.visibility = if (showBox) View.GONE else View.VISIBLE
        binding.llCampSite.visibility = if (showBox) View.VISIBLE else View.GONE
        if (showBox) {
            binding.tvCampSiteMsg.text = viewModel.gpsUnavailableReason.value
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.loc_camp_site_msg)
        }
    }

    private fun isLocationEditable(): Boolean {
        if (viewModel.recordExists.value != false) return false
        return if (isEditingExisting) updateLocationRequested && !viewModel.isCampSite.value
        else viewModel.isStandalone
    }

    private fun updateLocationControls() {
        if (_binding == null) return
        val editing = viewModel.recordExists.value == false
        binding.llUpdateLocation.visibility =
            if (editing && isEditingExisting) View.VISIBLE else View.GONE
        binding.llCampSiteChoice.visibility =
            if (editing && isEditingExisting && updateLocationRequested) View.VISIBLE else View.GONE
        // A new member of a household inherits the household location, so no refresh there
        binding.btnRefreshLocation.visibility =
            if (viewModel.isStandalone || isEditingExisting) View.VISIBLE else View.GONE
        binding.btnRefreshLocation.isEnabled =
            isLocationEditable() && viewModel.locationState.value !is LocationState.Fetching
    }

    private fun updateSubmitButtonState() {
        if (_binding == null) return
        val locationOk = when {
            isEditingExisting && !updateLocationRequested -> true
            viewModel.isStandalone || isEditingExisting -> viewModel.isLocationValid()
            else -> true
        }
        binding.btnSubmit.isEnabled = locationOk
    }

    private fun onUpdateLocationToggled(index: Int) {
        onUpdateLocationChanged(index == 0)
    }

    private fun cancelLocationFetch() {
        gpsWatchdogJob?.cancel()
        fetchTimeoutJob?.cancel()
        stopListeningForLocation()
    }

    private fun onUpdateLocationChanged(yes: Boolean) {
        if (yes == updateLocationRequested) return
        updateLocationRequested = yes
        viewModel.setLocationOverridden(yes)
        if (yes) {
            suppressRadioCallbacks = true
            binding.rgCampSite.check(
                if (viewModel.isCampSite.value) R.id.rb_camp_yes else R.id.rb_camp_no
            )
            suppressRadioCallbacks = false
            if (!viewModel.isCampSite.value) captureLocationSilently()
        } else {
            cancelLocationFetch()
            viewModel.onCampSiteChanged(originalCampSite)
            originalLocationState?.let { viewModel.restoreLocationState(it) }
        }
        updateCampSiteBox()
        updateLocationControls()
        updateSubmitButtonState()
    }

    private fun onCampSiteChoiceChanged(camp: Boolean) {
        if (camp == viewModel.isCampSite.value) return
        viewModel.onCampSiteChanged(camp)
        if (camp) {
            cancelLocationFetch()
        } else {
            val s = viewModel.locationState.value
            if (s !is LocationState.Captured && s !is LocationState.Fetching) captureLocationSilently()
        }
        updateCampSiteBox()
        updateLocationControls()
        updateSubmitButtonState()
    }



    // ─── Location UI updates ─────────────────────────────────────────────
    private fun updateLocationUI(state: LocationState) {
        binding.pbLocation.visibility = if (state is LocationState.Fetching) View.VISIBLE else View.GONE
        updateLocationControls()

        when (state) {
            is LocationState.Idle -> {
                setStatusText("", "#FF9800")
                binding.etDigipin.setText("")
            }
            is LocationState.Fetching -> {
                setStatusText(getString(R.string.loc_status_fetching), "#FF9800")
                binding.etDigipin.setText("")
            }
            is LocationState.Captured -> {
                setStatusText(getString(R.string.loc_status_captured), "#4CAF50")
                binding.etDigipin.setText(formatDigiPin(state.digipin))
            }
            is LocationState.Failed.PermissionDenied -> failedUi(getString(R.string.loc_status_permission_denied))
            is LocationState.Failed.GpsDisabled -> failedUi(getString(R.string.loc_status_gps_disabled))
            is LocationState.Failed.NoSignal -> failedUi(getString(R.string.loc_status_failed))
            is LocationState.Failed.OutsideIndia -> {
                failedUi(getString(R.string.loc_status_failed))
                Toast.makeText(context, getString(R.string.loc_msg_outside_india), Toast.LENGTH_LONG).show()
            }
            is LocationState.Failed.NoGpsProvider -> failedUi(getString(R.string.loc_status_no_gps_provider))
            is LocationState.Failed.Timeout -> failedUi(getString(R.string.loc_status_timeout))
        }
        updateSubmitButtonState()
    }

    private fun failedUi(text: String) {
        setStatusText(text, "#F44336")
        binding.etDigipin.setText("")
    }

    private fun updateLocationDetailText(detail: String?) {
        if (detail.isNullOrBlank()) {
            binding.tvLocationDetail.visibility = View.GONE
        } else {
            binding.tvLocationDetail.text = detail
            binding.tvLocationDetail.visibility = View.VISIBLE
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
        // A new capture replaces any legacy "GPS unavailable" state so the captured location is saved.
        viewModel.onGpsUnavailableToggled(false)
        viewModel.setFetching()
        GpsDiagnostics.logPreflight(requireContext(), "NewBenRegFragment")

        if (!GpsDiagnostics.isGpsHardwareAvailable(requireContext())) {
            Timber.tag(GpsDiagnostics.TAG).w("No GPS hardware on this device — failing fast")
            viewModel.onLocationFailed(LocationState.Failed.NoGpsProvider, getString(R.string.loc_detail_no_gps_hardware))
            return
        }
        if (!hasFineLocationPermission()) {
            Timber.tag(GpsDiagnostics.TAG).w("Only approximate location permission granted — precise location required for offline GPS")
            viewModel.onLocationFailed(LocationState.Failed.PermissionDenied, getString(R.string.loc_detail_precise_permission_required))
            showOpenSettingsDialog()
            return
        }
        if (!GpsDiagnostics.isGpsProviderEnabled(requireContext())) {
            Timber.tag(GpsDiagnostics.TAG).w("GPS provider disabled — sending user to system Location Settings")
            viewModel.onLocationFailed(LocationState.Failed.GpsDisabled, getString(R.string.loc_detail_gps_provider_off))
            showEnableLocationDialog()
            return
        }

        fetchLocationNow()
    }

    private fun showEnableLocationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.loc_section_title))
            .setMessage(getString(R.string.loc_msg_enable_location_settings))
            .setPositiveButton(getString(R.string.loc_dialog_open_settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    private fun startGpsWatchdog() {
        gpsWatchdogJob?.cancel()
        gpsWatchdogJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(GpsDiagnostics.MASTER_TIMEOUT_MS)
            // Defensive: only act if we're still Fetching. A terminal state (Captured/Failed)
            // should always cancel this job itself, but this guard means a missed cancellation
            // elsewhere can never clobber an already-resolved result — it's a last resort, not
            // an unconditional override.
            if (viewModel.locationState.value !is LocationState.Fetching) return@launch
            Timber.tag(GpsDiagnostics.TAG).e(
                "GPS watchdog fired — no terminal state after ${GpsDiagnostics.MASTER_TIMEOUT_MS}ms. " +
                        "Forcing UI out of Fetching (likely an OEM battery/background-process throttle, " +
                        "or a hung Play Services task on the fused fast path)."
            )
            viewModel.onLocationFailed(LocationState.Failed.Timeout, getString(R.string.loc_detail_watchdog_timeout))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationNow() {
        if (!hasLocationPermission()) {
            gpsWatchdogJob?.cancel()
            viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
            return
        }
        viewModel.setFetching()
        startGpsWatchdog()

        val fetchStartElapsed = SystemClock.elapsedRealtime()

        if (GpsDiagnostics.isPlayServicesAvailable(requireContext())) {
            Timber.tag(GpsDiagnostics.TAG).i("Play Services available — using FusedLocationProviderClient fast path")
            fetchLocationViaFusedProvider(fetchStartElapsed)
        } else {
            Timber.tag(GpsDiagnostics.TAG).i("Play Services unavailable — using android.location.LocationManager")
            fetchLocationViaLocationManager(fetchStartElapsed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationViaFusedProvider(fetchStartElapsed: Long) {
        val client = fusedLocationClient
            ?: LocationServices.getFusedLocationProviderClient(requireActivity()).also { fusedLocationClient = it }
        val cts = CancellationTokenSource()
        activeCancellationTokenSource = cts

        val fetchCancelJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(GpsDiagnostics.FETCH_TIMEOUT_MS)
            Timber.tag(GpsDiagnostics.TAG).w(
                "Fused fetch exceeded ${GpsDiagnostics.FETCH_TIMEOUT_MS}ms with no fix — cancelling."
            )
            stopListeningForLocation()
            resolveWithCachedLocation(fetchStartElapsed)
        }
        fetchTimeoutJob = fetchCancelJob


        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    fetchCancelJob.cancel()
                    gpsWatchdogJob?.cancel()
                    stopListeningForLocation()
                    if (location != null) {
                        val elapsed = SystemClock.elapsedRealtime() - fetchStartElapsed
                        val detail = GpsDiagnostics.locationUiSummary(location, elapsed, fromCache = false)
                        Timber.tag(GpsDiagnostics.TAG).i("Fused fresh fix in ${elapsed}ms: ${GpsDiagnostics.locationLogLine(location, elapsed)}")
                        viewModel.onLocationResult(location.latitude, location.longitude, detail)
                    } else {
                        Timber.tag(GpsDiagnostics.TAG).w("Fused getCurrentLocation returned null — trying lastLocation")
                        try {
                            client.lastLocation
                                .addOnSuccessListener { last ->
                                    val elapsed = SystemClock.elapsedRealtime() - fetchStartElapsed
                                    if (last != null) {
                                        val detail = GpsDiagnostics.locationUiSummary(last, elapsed, fromCache = true)
                                        Timber.tag(GpsDiagnostics.TAG).i("Fused lastLocation: ${GpsDiagnostics.locationLogLine(last, elapsed)}")
                                        viewModel.onLocationResult(last.latitude, last.longitude, detail)
                                    } else {
                                        viewModel.onLocationFailed(LocationState.Failed.NoSignal, getString(R.string.loc_detail_no_fix_no_cache))
                                        if (isAdded) Toast.makeText(context, getString(R.string.loc_msg_no_signal), Toast.LENGTH_LONG).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Timber.tag(GpsDiagnostics.TAG).e(e, "Fused lastLocation failed: ${GpsDiagnostics.describeFailure(e)}")
                                    viewModel.onLocationFailed(LocationState.Failed.NoSignal, GpsDiagnostics.describeFailure(e))
                                }
                        } catch (e: SecurityException) {
                            Timber.tag(GpsDiagnostics.TAG).e(e, "lastLocation SecurityException: ${GpsDiagnostics.describeFailure(e)}")
                            viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    fetchCancelJob.cancel()
                    gpsWatchdogJob?.cancel()
                    stopListeningForLocation()
                    val elapsed = SystemClock.elapsedRealtime() - fetchStartElapsed
                    Timber.tag(GpsDiagnostics.TAG).e(e, "Fused fetch failed after ${elapsed}ms: ${GpsDiagnostics.describeFailure(e)}")
                    if (elapsed >= GpsDiagnostics.FETCH_TIMEOUT_MS) {
                        viewModel.onLocationFailed(LocationState.Failed.Timeout, getString(R.string.loc_detail_timeout))
                    } else {
                        viewModel.onLocationFailed(LocationState.Failed.NoSignal, GpsDiagnostics.describeFailure(e))
                    }
                    if (isAdded) Toast.makeText(context, getString(R.string.loc_timeout_msg), Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            // Same race as above, but on the initial getCurrentLocation() call itself.
            fetchCancelJob.cancel()
            gpsWatchdogJob?.cancel()
            stopListeningForLocation()
            Timber.tag(GpsDiagnostics.TAG).e(e, "getCurrentLocation SecurityException: ${GpsDiagnostics.describeFailure(e)}")
            viewModel.onLocationFailed(LocationState.Failed.PermissionDenied)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationViaLocationManager(fetchStartElapsed: Long) {
        val fetchCancelJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(GpsDiagnostics.FETCH_TIMEOUT_MS)
            Timber.tag(GpsDiagnostics.TAG).w(
                "GPS fetch exceeded ${GpsDiagnostics.FETCH_TIMEOUT_MS}ms with no fix — removing listener. " +
                        "Offline cold fixes can legitimately take this long without A-GPS assistance data."
            )
            stopListeningForLocation()
            resolveWithCachedLocation(fetchStartElapsed)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                fetchCancelJob.cancel()
                gpsWatchdogJob?.cancel()
                stopListeningForLocation()
                val elapsed = SystemClock.elapsedRealtime() - fetchStartElapsed
                val detail = GpsDiagnostics.locationUiSummary(location, elapsed, fromCache = false)
                Timber.tag(GpsDiagnostics.TAG).i("Fresh fix in ${elapsed}ms: ${GpsDiagnostics.locationLogLine(location, elapsed)}")
                viewModel.onLocationResult(location.latitude, location.longitude, detail)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Timber.tag(GpsDiagnostics.TAG).w("GPS provider was disabled while waiting for a fix")
            }
        }
        activeLocationListener = listener

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            fetchCancelJob.cancel()
            gpsWatchdogJob?.cancel()
            stopListeningForLocation()
            Timber.tag(GpsDiagnostics.TAG).e(e, "requestLocationUpdates failed: ${GpsDiagnostics.describeFailure(e)}")
            viewModel.onLocationFailed(LocationState.Failed.NoSignal, GpsDiagnostics.describeFailure(e))
        }
    }

    private fun stopListeningForLocation() {
        activeLocationListener?.let { runCatching { locationManager.removeUpdates(it) } }
        activeLocationListener = null
        activeCancellationTokenSource?.let { runCatching { it.cancel() } }
        activeCancellationTokenSource = null
    }

    /** No fresh fix within the fetch timeout — try a cached last-known location (GPS, then network) before giving up. */
    @SuppressLint("MissingPermission")
    private fun resolveWithCachedLocation(fetchStartElapsed: Long) {
        // This is a terminal outcome (Captured or Failed either way) — the 75s master watchdog
        // must not be allowed to fire later and clobber whatever we're about to set.
        gpsWatchdogJob?.cancel()
        val elapsed = SystemClock.elapsedRealtime() - fetchStartElapsed
        val cached = runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()

        if (cached != null) {
            val detail = GpsDiagnostics.locationUiSummary(cached, elapsed, fromCache = true)
            Timber.tag(GpsDiagnostics.TAG).i("Using cached location: ${GpsDiagnostics.locationLogLine(cached, elapsed)}")
            viewModel.onLocationResult(cached.latitude, cached.longitude, detail)
        } else {
            Timber.tag(GpsDiagnostics.TAG).w("No fresh fix and no cached location available after ${elapsed}ms")
            if (elapsed >= GpsDiagnostics.FETCH_TIMEOUT_MS) {
                viewModel.onLocationFailed(LocationState.Failed.Timeout, getString(R.string.loc_detail_timeout))
            } else {
                viewModel.onLocationFailed(LocationState.Failed.NoSignal, getString(R.string.loc_detail_no_fix_no_cache))
            }
            if (isAdded) Toast.makeText(context, getString(R.string.loc_msg_no_signal), Toast.LENGTH_LONG).show()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Deliberately stricter than hasLocationPermission(): COARSE-only is enough to reach this
    // screen without a system permission prompt, but not enough to guarantee an offline-capable
    // fix on either acquisition path — see the check in checkSettingsAndFetch().
    private fun hasFineLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
        Toast.makeText(context, getString(R.string.loc_err_location_required), Toast.LENGTH_LONG).show()
        binding.nestedScroll.post { binding.nestedScroll.smoothScrollTo(0, binding.cardLocation.top) }
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
                if (viewModel.isStandalone && viewModel.recordExists.value == false && !isEditingExisting) {
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        val finalUri = withContext(Dispatchers.Default) { compressPhoto(uri) } ?: uri
                        viewModel.setImageUriToFormElement(finalUri)
                        binding.form.rvInputForm.adapter?.notifyItemChanged(0)
                        if (continuePreviewAfterPhoto) {
                            continuePreviewAfterPhoto = false
                            showPreview()
                        }
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


    /** Resizes to max 1024px on the long side and re-encodes as JPEG (~100-250 KB). Returns null on failure. */
    private fun compressPhoto(uri: Uri, maxDim: Int = 1024, quality: Int = 70): Uri? {
        return try {
            val resolver = requireContext().contentResolver

            // 1. Read size only
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2. Decode a downsampled bitmap
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            // 3. Keep the correct orientation
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            // 4. Final scale to maxDim (and rotate if needed)
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()

            // 5. Write the compressed JPEG
            val file = File.createTempFile(Konstants.tempBenImagePrefix, ".jpg", requireActivity().cacheDir)
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            out.recycle()
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
        } catch (e: Exception) {
            Timber.e(e, "Photo compression failed")
            null
        }
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
        gpsWatchdogJob?.cancel()
        stopListeningForLocation()
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