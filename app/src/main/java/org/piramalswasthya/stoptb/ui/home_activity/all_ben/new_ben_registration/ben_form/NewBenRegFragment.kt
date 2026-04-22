package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.databinding.AlertConsentBinding
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.databinding.LayoutViewMediaBinding
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel.State
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class NewBenRegFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
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
    private val takePicture = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            latestTmpUri?.let { uri ->
                viewModel.setImageUriToFormElement(uri)
                binding.form.rvInputForm.adapter?.notifyItemChanged(0)
            }
        }
    }

    // ─── Inflate ─────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        timber.log.Timber.d("BEN_FORM: onCreateView called, benId=${viewModel.benIdFromArgs}")
        _binding = FragmentNewFormBinding.inflate(layoutInflater, container, false)
        return binding.root
    }


    override fun onResume() {
        super.onResume()
        timber.log.Timber.d("BEN_FORM: onResume called")
    }

    // ─── onViewCreated ───────────────────────────────────────────────────
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timber.log.Timber.d("BEN_FORM: onViewCreated called")

        // Show loading, hide content until form is ready
        binding.llLoading.visibility = View.VISIBLE
        binding.llContent.visibility = View.GONE

        // Back press — show discard dialog in edit mode
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val isEditMode = viewModel.recordExists.value == false
                    if (isEditMode) {
                        showDiscardDialog()
                    } else {
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
                if (isEditMode) {
                    showDiscardDialog()
                } else {
                    try {
                        findNavController().popBackStack()
                    } catch (e: Exception) {
                        Timber.e(e)
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }

        // Set action bar title once
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ben,
                    getString(R.string.frag_new_ben_reg_type_title)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ben,
                    getString(R.string.frag_new_ben_reg_type_title)
                )
            }
        }

        // Capture geolocation silently
        captureGeolocation()

        binding.cvPatientInformation.visibility = View.GONE

        // Submit button
        binding.btnSubmit.setOnClickListener {
            if (validateCurrentPage()) showPreview()
        }

        // Cancel button hidden — discard handled by back press

        // Death badge visibility — only show fab if record exists AND not dead
        viewModel.isDeath.observe(viewLifecycleOwner) { isDeath ->
            val recordExists = viewModel.recordExists.value ?: false
            binding.fabEdit.visibility = if (isDeath || !recordExists) View.GONE else View.VISIBLE
        }

        // Set up adapter once
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
            // OTP not used in StopTB — empty listener kept for adapter compat
            sendOtpClickListener = FormInputAdapter.SendOtpClickListener { _, _, _, _, _, _, _ -> },
            selectImageClickListener = FormInputAdapter.SelectUploadImageClickListener { },
            viewDocumentListner = FormInputAdapter.ViewDocumentOnClick { },
            isEnabled = true,
        )
        binding.form.rvInputForm.adapter = adapter
        binding.form.rvInputForm.itemAnimator = null

        // Collect form list — show loading until ready
        var formShown = false
        lifecycleScope.launch {
            viewModel.formList.collect {
                timber.log.Timber.d("BEN_FORM: formList collected, size=${it.size}")
                if (it.isNotEmpty()) {
                    adapter.submitList(it) {
                        if (!formShown) {
                            formShown = true
                            // Wait for RecyclerView to fully layout before showing
                            binding.form.rvInputForm.post {
                                binding.form.rvInputForm.post {
                                    binding.llLoading.visibility = View.GONE
                                    binding.llContent.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }
        }

        // Record exists observer — drives view/edit mode + consent
        viewModel.recordExists.observe(viewLifecycleOwner) { recordExists ->
            timber.log.Timber.d("BEN_FORM: recordExists=$recordExists")
            binding.fabEdit.visibility  = if (recordExists) View.VISIBLE else View.GONE
            binding.btnSubmit.visibility = if (recordExists) View.GONE else View.VISIBLE
            // btnCancel hidden — discard via back press
            adapter.isEnabled = !recordExists
            // Show consent popup for new registrations
            if (!recordExists && !viewModel.getIsConsentAgreed()) consentAlert.show()
        }

        // State observer
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state!!) {
                State.IDLE -> { /* nothing */ }

                State.SAVING -> {
                    binding.llContent.visibility = View.GONE
                    binding.pbForm.visibility    = View.VISIBLE
                }

                State.SAVE_SUCCESS -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility    = View.GONE
                    Toast.makeText(context, resources.getString(R.string.save_successful), Toast.LENGTH_LONG).show()
                    WorkerUtils.triggerAmritPushWorker(requireContext())
                    try {
                        if (viewModel.benIdFromArgs == 0L) {
                            findNavController().navigate(
                                R.id.TBScreeningFormFragment,
                                bundleOf(
                                    "benId" to viewModel.getCurrentBenId(),
                                    "autoFlow" to true
                                )
                            )
                        } else {
                            findNavController().navigateUp()
                        }
                    } catch (e: Exception) { Timber.e(e) }
                }

                State.SAVE_FAILED -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility    = View.GONE
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
            // btnCancel hidden
            adapter.isEnabled = true
            adapter.notifyDataSetChanged()
        }
    }

    // ─── onStart ───────────────────────────────────────────────────────────
    override fun onStart() {
        super.onStart()
    }

    // ─── Consent popup ───────────────────────────────────────────────────
    private val consentAlert by lazy {
        val alertBinding = AlertConsentBinding.inflate(layoutInflater, binding.root, false)
        alertBinding.textView4.text    = resources.getString(R.string.consent_alert_title)
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
                try { findNavController().navigateUp() } catch (e: Exception) { Timber.e(e) }
            }
            .setNegativeButton(getString(R.string.stay)) { dialog, _ ->
                dialog.dismiss()
            }
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

    // ─── Validation ──────────────────────────────────────────────────────
    private fun validateCurrentPage(): Boolean {
        val result = (binding.form.rvInputForm.adapter as? FormInputAdapter)?.validateInput(resources)
        return if (result == -1) true
        else {
            result?.let { binding.form.rvInputForm.scrollToPosition(it) }
            false
        }
    }

    // ─── Hard-coded adapter refresh for specific formIds ─────────────────
    private fun hardCodedListUpdate(formId: Int) {
        binding.form.rvInputForm.adapter?.apply {
            when (formId) {
                1008 -> notifyDataSetChanged()          // marital status
                1012 -> {                               // age at marriage
                    if ((viewModel.dataset.ageAtMarriage.value?.length ?: 0) >= 2)
                        notifyDataSetChanged()
                }
                9    -> notifyDataSetChanged()          // gender
                115  -> notifyDataSetChanged()          // age/dob
                12   -> notifyDataSetChanged()          // mobile relation
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

    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile(
            Konstants.tempBenImagePrefix, null, requireActivity().cacheDir
        ).apply { createNewFile() }
        return FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.provider", tmpFile
        )
    }

    // ─── View image ──────────────────────────────────────────────────────
    private fun viewImage(imageUri: Uri) {
        val viewImageBinding = LayoutViewMediaBinding.inflate(layoutInflater, binding.root, false)
        val alertDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(viewImageBinding.root)
            .setCancelable(true)
            .create()
        Glide.with(this)
            .load(imageUri)
            .placeholder(R.drawable.ic_person)
            .into(viewImageBinding.viewImage)
        viewImageBinding.btnClose.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    // ─── Geolocation ────────────────────────────────────────────────────
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchLocation()
        }

    private fun captureGeolocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        location?.let {
            viewModel.capturedLatitude = it.latitude
            viewModel.capturedLongitude = it.longitude
            Timber.d("Geolocation captured: lat=${it.latitude}, lng=${it.longitude}")
        }
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
}
