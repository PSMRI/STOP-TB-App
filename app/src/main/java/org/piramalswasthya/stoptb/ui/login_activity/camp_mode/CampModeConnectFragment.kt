package org.piramalswasthya.stoptb.ui.login_activity.camp_mode

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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

    private val qrScannerOptions by lazy {
        GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
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

        binding.etCampHubUrl.setText(viewModel.getCampHubUrl())
        binding.ibBack.setOnClickListener {
            closeConnectScreen()
        }

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

    private fun startQrScanner() {
        Log.d(TAG, "QR scanner opened.")
        binding.pbConnect.visibility = View.VISIBLE
        GmsBarcodeScanning.getClient(requireContext(), qrScannerOptions)
            .startScan()
            .addOnSuccessListener { barcode ->
                binding.pbConnect.visibility = View.GONE
                val scannedUrl = barcode.rawValue
                Log.d(TAG, "QR scanner success. scannedUrl=$scannedUrl")
                if (scannedUrl.isNullOrBlank()) {
                    showToast(R.string.camp_hub_scan_empty)
                } else {
                    binding.etCampHubUrl.setText(scannedUrl)
                    binding.etCampHubUrl.setSelection(scannedUrl.length)
                }
            }
            .addOnCanceledListener {
                Log.d(TAG, "QR scanner cancelled.")
                binding.pbConnect.visibility = View.GONE
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "QR scanner failed. error=${error.javaClass.simpleName}: ${error.message}", error)
                binding.pbConnect.visibility = View.GONE
                showToast(R.string.camp_hub_scan_failed)
            }
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
                closeConnectScreen()
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

    private fun closeConnectScreen() {
        if (openedFromOfflineChip) {
            requireActivity().finish()
        } else {
            findNavController().popBackStack()
        }
    }
}
