package org.piramalswasthya.stoptb.ui.login_activity.camp_mode

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentCampModeConnectBinding
import org.piramalswasthya.stoptb.ui.login_activity.LoginActivity
import org.piramalswasthya.stoptb.ui.login_activity.camp_mode.CampModeConnectViewModel.CampHubStatus
import org.piramalswasthya.stoptb.work.WorkerUtils
import javax.inject.Inject

@AndroidEntryPoint
class CampModeConnectFragment : Fragment() {

    companion object {
        private const val TAG = "CampHub"
        const val CAMP_HUB_CONNECTION_UPDATED = "campHubConnectionUpdated"
    }

    @Inject
    lateinit var pref: PreferenceDao

    private var _binding: FragmentCampModeConnectBinding? = null
    private val binding: FragmentCampModeConnectBinding
        get() = _binding!!

    private val viewModel: CampModeConnectViewModel by viewModels()
    private val openedFromOfflineChip: Boolean
        get() = requireActivity().intent.getBooleanExtra(
            LoginActivity.EXTRA_OPEN_CAMP_CONNECT,
            false
        )

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        binding.pbConnect.visibility = View.GONE
        val scannedUrl = result.contents
        Log.d(TAG, "QR scanner result. scannedUrl=$scannedUrl")
        if (scannedUrl.isNullOrBlank()) {
            showToast(R.string.camp_hub_scan_empty)
        } else {
            binding.etCampHubUrl.setText(scannedUrl)
            binding.etCampHubUrl.setSelection(scannedUrl.length)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCampModeConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyStatusBarInsetsToHeader()

        binding.etCampHubUrl.setText(viewModel.getCampHubUrl())
        binding.ibBack.setOnClickListener {
            closeConnectScreen(refreshSignIn = false)
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeConnectScreen(refreshSignIn = false)
                }
            }
        )

        binding.btnScan.setOnClickListener {
            startQrScanner()
        }

        binding.btnConnect.setOnClickListener {
            val campHubUrl = binding.etCampHubUrl.text?.toString().orEmpty()
            Log.d(TAG, "Connect button tapped from UI. url=$campHubUrl")
            viewModel.connectToCampHub(campHubUrl)
        }

        viewModel.campHubStatus.observe(viewLifecycleOwner) { status ->
            updateCampHubStatus(status)
        }
    }

    private fun applyStatusBarInsetsToHeader() {
        val initialHeaderPaddingTop = binding.header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.header.updatePadding(top = initialHeaderPaddingTop + statusBarTop)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun startQrScanner() {
        Log.d(TAG, "QR scanner opened.")
        binding.pbConnect.visibility = View.VISIBLE
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(PortraitQrCaptureActivity::class.java)
        qrScannerLauncher.launch(options)
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT).show()
    }

    private fun updateCampHubStatus(status: CampHubStatus) {
        Log.d(TAG, "Camp hub UI status changed. status=$status, openedFromOfflineChip=$openedFromOfflineChip")
        when (status) {
            CampHubStatus.IDLE -> {
                binding.pbConnect.visibility = View.GONE
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = getString(R.string.camp_hub_not_connected)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                )
            }

            CampHubStatus.CHECKING -> {
                binding.pbConnect.visibility = View.VISIBLE
                binding.btnConnect.isEnabled = false
                binding.tvStatus.text = getString(R.string.camp_hub_checking)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_light_onSurfaceVariant)
                )
            }

            CampHubStatus.CONNECTED -> {
                binding.pbConnect.visibility = View.GONE
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = getString(R.string.camp_hub_connected)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
                )
                if (openedFromOfflineChip) {
                    WorkerUtils.triggerCampQuickPullIfConnected(requireContext(), pref, force = true)
                }
                closeConnectScreen(refreshSignIn = true)
            }

            CampHubStatus.NOT_CONNECTED -> {
                binding.pbConnect.visibility = View.GONE
                binding.btnConnect.isEnabled = true
                binding.tvStatus.text = getString(R.string.camp_hub_not_connected)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun closeConnectScreen(refreshSignIn: Boolean) {
        if (openedFromOfflineChip) {
            requireActivity().finish()
        } else if (refreshSignIn) {
            parentFragmentManager.setFragmentResult(CAMP_HUB_CONNECTION_UPDATED, Bundle.EMPTY)
            findNavController().previousBackStackEntry
                ?.savedStateHandle
                ?.set(CAMP_HUB_CONNECTION_UPDATED, true)
            findNavController().navigate(
                R.id.signInFragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(R.id.signInFragment, true)
                    .build()
            )
        } else {
            findNavController().popBackStack()
        }
    }

}
