package org.piramalswasthya.stoptb.ui.contact_tracing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentContactTracingTypeBottomSheetBinding
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingStatus

@AndroidEntryPoint
class ContactTracingTypeBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "contact_tracing_type_bottom_sheet"
        private const val ARG_INDEX_CASE_BEN_ID = "indexCaseBenId"

        fun newInstance(indexCaseBenId: Long) =
            ContactTracingTypeBottomSheetFragment().apply {
                arguments = bundleOf(ARG_INDEX_CASE_BEN_ID to indexCaseBenId)
            }
    }

    private var _binding: FragmentContactTracingTypeBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactTracingTypeViewModel by viewModels()

    private val indexCaseBenId: Long
        get() = arguments?.getLong(ARG_INDEX_CASE_BEN_ID) ?: 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactTracingTypeBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTypeCommunity.setOnClickListener {
            ContactTracingActivity.startForType(requireContext(), indexCaseBenId, "COMMUNITY")
//            dismiss()
        }

        binding.btnTypeOccupational.setOnClickListener {
            ContactTracingActivity.startForType(requireContext(), indexCaseBenId, "OCCUPATIONAL")
//            dismiss()
        }

        viewModel.status.observe(viewLifecycleOwner) { status -> updateStatusIcons(status) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    private fun updateStatusIcons(status: ContactTracingStatus) {
        binding.ivStatusCommunity.setImageResource(
            if (status.isCommunitySubmitted) R.drawable.ic_check_circle_green else R.drawable.ic_close
        )
        binding.ivStatusOccupational.setImageResource(
            if (status.isOccupationalSubmitted) R.drawable.ic_check_circle_green else R.drawable.ic_close
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
