package org.piramalswasthya.stoptb.ui.home_activity.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.IconGridAdapter
import org.piramalswasthya.stoptb.configuration.IconDataset
import org.piramalswasthya.stoptb.databinding.RvIconGridBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject

@AndroidEntryPoint
class ReferralIconsFragment : Fragment() {

    @Inject
    lateinit var iconDataset: IconDataset

    private val binding by lazy { RvIconGridBinding.inflate(layoutInflater) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvIconGrid.layoutManager = GridLayoutManager(
            context,
            requireContext().resources.getInteger(R.integer.icon_grid_span)
        )
        binding.rvIconGrid.adapter = IconGridAdapter(
            IconGridAdapter.GridIconClickListener { findNavController().navigate(it) },
            viewLifecycleOwner.lifecycleScope
        ).also {
            it.submitList(iconDataset.getReferralDataset(resources))
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic_ncd_noneligible,
                    getString(R.string.ncd_refer_list)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic_ncd_noneligible,
                    getString(R.string.ncd_refer_list)
                )
            }
        }
    }
}
