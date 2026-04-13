package org.piramalswasthya.stoptb.ui.home_activity.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.databinding.FragmentSchedulerBinding
import org.piramalswasthya.stoptb.ui.home_activity.home.SchedulerViewModel.State.LOADED
import org.piramalswasthya.stoptb.ui.home_activity.home.SchedulerViewModel.State.LOADING
import java.util.Calendar

@AndroidEntryPoint
class SchedulerFragment : Fragment() {

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
            binding.calendarView.date = it
        }

        // ABHA counts
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

        // ABHA card click -> AllBen with ABHA filter
        binding.cvAbha.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToAllBenFragment(1))
        }

        // RCH card click -> AllBen with RCH filter
        binding.cvRch.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionNavHomeToAllBenFragment(2))
        }

        // Hide cards that depend on deleted features
        binding.cvAnc.visibility = View.GONE
        binding.cvImm.visibility = View.GONE
        binding.cvHrp.visibility = View.GONE
        binding.cvNonHrp.visibility = View.GONE
        binding.cvHrb.visibility = View.GONE
        binding.cvLwb.visibility = View.GONE
        binding.cvNon.visibility = View.GONE
        binding.cvMiss.visibility = View.GONE
        binding.cvRcha.visibility = View.GONE
        binding.cvNona.visibility = View.GONE

        binding.calendarView.setOnDateChangeListener { _, year, month, day ->
            val calLong = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }.timeInMillis
            viewModel.setDate(calLong)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}