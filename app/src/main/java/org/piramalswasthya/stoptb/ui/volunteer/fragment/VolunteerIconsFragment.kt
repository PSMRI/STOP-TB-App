package org.piramalswasthya.stoptb.ui.volunteer.fragment

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
import org.piramalswasthya.stoptb.databinding.FragmentVolunteerHomeBinding
import javax.inject.Inject

@AndroidEntryPoint
class VolunteerIconsFragment : Fragment() {

    @Inject
    lateinit var iconDataset: IconDataset

    private var _binding: FragmentVolunteerHomeBinding? = null
    private val binding: FragmentVolunteerHomeBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVolunteerHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpIconRvAdapter()

    }

    private fun setUpIconRvAdapter() {
        val rvLayoutManager = GridLayoutManager(
            context,
            1
        )
        binding.rvIconGrid.layoutManager = rvLayoutManager
        val iconAdapter = IconGridAdapter(
            IconGridAdapter.GridIconClickListener {
                findNavController().navigate(it)
            },
            viewLifecycleOwner.lifecycleScope
        )
        binding.rvIconGrid.adapter = iconAdapter
        iconAdapter.submitList(iconDataset.getVolunteerIconDataset(resources))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
