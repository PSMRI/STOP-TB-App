package org.piramalswasthya.stoptb.ui.home_activity.home

import android.os.Bundle
import java.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentSchedulerBinding
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.ui.home_activity.home.SchedulerViewModel.State.LOADED
import org.piramalswasthya.stoptb.ui.home_activity.home.SchedulerViewModel.State.LOADING
import javax.inject.Inject

@AndroidEntryPoint
class SchedulerFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentSchedulerBinding? = null
    private val binding: FragmentSchedulerBinding
        get() = _binding!!

    private val viewModel: SchedulerViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSchedulerBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        applyRoleVisibility()

        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                LOADING -> {
                    binding.llContent.visibility = View.GONE
                    binding.pbLoading.visibility = View.VISIBLE
                }
                LOADED -> {
                    binding.pbLoading.visibility = View.GONE
                    binding.llContent.visibility = View.VISIBLE
                }
            }
        }

        viewModel.date.observe(viewLifecycleOwner) {
            binding.tvSelectedDate.text = viewModel.formattedDate
        }

        lifecycleScope.launch {
            viewModel.abhaNewGeneratedCount.collect {
                binding.tvAbhaNewCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            viewModel.abhaOldGeneratedCount.collect {
                binding.tvAbhaOldCount.text = it.toString()
            }
        }

        // RCH count
        lifecycleScope.launch {
            viewModel.rchIdCount.collect {
                binding.tvRch.text = it.toString()
            }
        }

        lifecycleScope.launch {
            viewModel.allBenCount.collect {
                binding.tvAllBenCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            viewModel.householdCount.collect {
                binding.tvHouseholdCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            viewModel.tbCount.collect {
                binding.tvTbCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            viewModel.ncdCount.collect {
                binding.tvNcdCount.text = it.toString()
            }
        }
        binding.cvAllBen.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToAllBenFragment(0))
        }
        binding.cvHousehold.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToAllHouseholdFragment())
        }
        binding.cvTb.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToCdFragment())
        }
        binding.cvNcd.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToNcdFragment())
        }
        binding.cvReferrals.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToReferralIconsFragment())
        }

        binding.cvAbha.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToAllBenFragment(1))
        }

        binding.cvRch.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToAllBenFragment(2))
        }
    }

    private fun applyRoleVisibility() {
        val role = prefDao.getLoggedInUser()?.role
        binding.cvHousehold.visibility = View.VISIBLE
        when {
            role.isNullOrBlank() || (!role.isNurseRole() && !role.isCounsellingOfficerRole()) -> {
                binding.cvAllBen.visibility = View.VISIBLE
                binding.cvTb.visibility = View.GONE
                binding.cvNcd.visibility = View.GONE
                binding.cvReferrals.visibility = View.GONE
                binding.cvAbha.visibility = View.GONE
                binding.cvRch.visibility = View.GONE
            }
            role.isNurseRole() -> {
                binding.cvAllBen.visibility = View.VISIBLE
                binding.cvTb.visibility = View.VISIBLE
                binding.cvNcd.visibility = View.GONE
                binding.cvReferrals.visibility = View.VISIBLE
                binding.cvAbha.visibility = View.GONE
                binding.cvRch.visibility = View.GONE
            }
            role.isCounsellingOfficerRole() -> {
                binding.cvAllBen.visibility = View.GONE
                binding.cvTb.visibility = View.VISIBLE
                binding.cvNcd.visibility = View.GONE
                binding.cvReferrals.visibility = View.VISIBLE
                binding.cvAbha.visibility = View.GONE
                binding.cvRch.visibility = View.GONE
            }
        }
    }

    private fun setupHeader() {
        val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
        binding.tvHomeGreeting.text = getString(R.string.label_greeting_name, greeting, viewModel.getFirstName())
        binding.tvSelectedDate.text = viewModel.formattedDate
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
