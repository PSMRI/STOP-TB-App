package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentMissingDataBenBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity

@AndroidEntryPoint
class MissingDataBenFragment : Fragment() {

    private var _binding: FragmentMissingDataBenBinding? = null
    private val binding: FragmentMissingDataBenBinding
        get() = _binding!!

    private val viewModel: MissingDataBenViewModel by viewModels()
    private lateinit var adapter: MissingDataBenAdapter

    private val searchTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            viewModel.processIntent(
                MissingDataBenIntent.SearchQueryChanged(
                    s?.toString().orEmpty()
                )
            )
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMissingDataBenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateToolbarTitle()
        setupViews()
        observeDataState()
        observeEffects()
        observeData()
    }

    private fun updateToolbarTitle() {
        activity?.let {
            val title = getString(R.string.icon_title_missing_data_ben)
            val icon = R.drawable.ic__ben
            when (it) {
                is HomeActivity -> it.updateActionBar(icon, title)
                is VolunteerActivity -> it.updateActionBar(icon, title)
            }
        }
    }

    private fun setupViews() {
        adapter = MissingDataBenAdapter { selectedItem ->
            viewModel.processIntent(MissingDataBenIntent.ItemClicked(selectedItem))
        }
        binding.rvMissingDataBen.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMissingDataBen.adapter = adapter

        binding.searchView.addTextChangedListener(searchTextWatcher)
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tbScreeningBenIds.collectLatest { list ->
                    adapter.submitTbScreeningBenIds(list)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTbDiagnostics.collectLatest { list ->
                    adapter.submitTBDiagnostics(list)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTbScreening.collectLatest { list ->
                    adapter.submitTbScreeningList(list)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.householdMemberCounts.collectLatest { map ->
                    adapter.submitHouseholdMemberCounts(map)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.householdHeadNames.collectLatest { map ->
                    adapter.submitHouseholdHeadNames(map)
                }
            }
        }
    }

    private fun observeDataState() {

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.benPagingData.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    with(binding) {
                        loadingOverlay.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = state.isEmpty
                        binding.rvMissingDataBen.isVisible = !state.isEmpty
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.onPagesUpdatedFlow.collectLatest {
                    if (adapter.loadStateFlow.first().refresh is LoadState.Loading) return@collectLatest
                    val isEmpty = adapter.itemCount == 0
                    viewModel.processIntent(MissingDataBenIntent.UpdateListState(isEmpty))

                    // Hide the overlay only after the first load finishes; it never comes back
                    if (viewModel.uiState.value.isLoading) {
                        viewModel.processIntent(MissingDataBenIntent.UpdateLoadingState(false))
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collectLatest { loadStates ->
                    if (loadStates.refresh is LoadState.Error && viewModel.uiState.value.isLoading) {
                        viewModel.processIntent(MissingDataBenIntent.UpdateLoadingState(false))
                    }
                }
            }
        }
    }

    private fun observeEffects() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { effect ->
                    handleEffect(effect)
                }
            }
        }
    }
    var lastClickTime = 0L
    private fun handleEffect(effect: MissingDataBenEffect) {
        when (effect) {
            is MissingDataBenEffect.ShowToast -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }

            is MissingDataBenEffect.ShowDetailsDialog -> {
                MissingDataBenDetailsDialog.show(requireContext(), effect.item) { item ->
                    viewModel.processIntent(MissingDataBenIntent.EditItemClicked(item))
                }
            }

            is MissingDataBenEffect.OpenEditDetails -> {
                val now = System.currentTimeMillis()
                if (now - lastClickTime > 800) {
                    lastClickTime = now
                    val navOptions = NavOptions.Builder()
                        .setEnterAnim(0)
                        .setExitAnim(0)
                        .setPopEnterAnim(0)
                        .setPopExitAnim(0)
                        .setLaunchSingleTop(true)
                        .build()

                    val action = effect.item.benBasicDomain.let { item ->
                        MissingDataBenFragmentDirections.actionMissingDataBenFragmentToNewBenRegFragment(
                            hhId = item.hhId,
                            benId = item.benId,
                            relToHeadId = item.relToHeadId,
                            isAddSpouse = 0,
                            gender = 0,
                            isEditModeUp = true
                        )
                    }

                    findNavController().navigate(
                        action,
                        navOptions
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.searchView.removeTextChangedListener(searchTextWatcher)
        _binding = null
    }
}
