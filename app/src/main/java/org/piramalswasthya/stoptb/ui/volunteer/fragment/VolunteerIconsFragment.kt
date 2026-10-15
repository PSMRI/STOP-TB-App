package org.piramalswasthya.stoptb.ui.volunteer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.IconGridAdapter
import org.piramalswasthya.stoptb.configuration.IconDataset
import org.piramalswasthya.stoptb.databinding.FragmentVolunteerHomeBinding
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.utils.navigateSafe
import javax.inject.Inject

@AndroidEntryPoint
class VolunteerIconsFragment : Fragment() {

    @Inject
    lateinit var iconDataset: IconDataset

    @Inject
    lateinit var roleManager: RoleManager

    private var _binding: FragmentVolunteerHomeBinding? = null
    private val binding: FragmentVolunteerHomeBinding
        get() = _binding!!

    private var iconAdapter: IconGridAdapter? = null

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

        // Refresh the card grid whenever the active role tab changes (bottom nav lives in
        // the parent VolunteerActivity, so this fragment must observe rather than be re-created).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                roleManager.activeRole.collect {
                    iconAdapter?.submitList(iconDataset.getVolunteerIconDataset(resources))
                }
            }
        }
    }

    private fun setUpIconRvAdapter() {
        val rvLayoutManager = GridLayoutManager(
            context,
            1
        )
        binding.rvIconGrid.layoutManager = rvLayoutManager
        val adapter = IconGridAdapter(
            IconGridAdapter.GridIconClickListener {
                findNavController().navigateSafe(it)
            },
            viewLifecycleOwner.lifecycleScope
        )
        iconAdapter = adapter
        binding.rvIconGrid.adapter = adapter
        adapter.submitList(iconDataset.getVolunteerIconDataset(resources))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        iconAdapter = null
        _binding = null
    }
}
