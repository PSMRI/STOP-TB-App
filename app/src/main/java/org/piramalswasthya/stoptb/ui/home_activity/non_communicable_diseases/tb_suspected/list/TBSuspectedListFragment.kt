package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.TbSuspectedListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchRvButtonBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject

@AndroidEntryPoint
class TBSuspectedListFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentDisplaySearchRvButtonBinding? = null
    private val binding: FragmentDisplaySearchRvButtonBinding
        get() = _binding!!

    private val viewModel: TBSuspectedListViewModel by viewModels()

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        binding.searchView.setText(lowerValue)
        binding.searchView.setSelection(lowerValue.length)
        viewModel.filterText(lowerValue)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchRvButtonBinding.inflate(layoutInflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNextPage.visibility = View.GONE
        val benAdapter = TbSuspectedListAdapter(
            TbSuspectedListAdapter.ClickListener { hhId, benId, _ ->
                findNavController().navigate(
                    TBSuspectedListFragmentDirections.actionTBSuspectedListFragmentToTBSuspectedFragment(
                        benId = benId,
                        viewOnly = true
                    )
                )
            }, pref = prefDao
        )
        binding.rvAny.adapter = benAdapter
        binding.flLoading.visibility = View.VISIBLE
        binding.rvAny.visibility = View.GONE
        // fl_empty declares no visibility and therefore starts VISIBLE, which is what drew
        // "No records found" on top of the list (and, briefly, on top of this loader).
        binding.flEmpty.visibility = View.GONE

        // viewLifecycleOwner + repeatOnLifecycle: the fragment outlives its view on the back
        // stack, so a fragment-scoped collector survives onDestroyView and a second one is
        // started the next time onViewCreated runs, each doing the same work on every emission.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.benList.collect {
                renderListState(it)
                benAdapter.submitList(it)
                }
            }
        }

        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }
        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                val query = p0?.toString()?.trim().orEmpty()
                onQueryChanged(query)
                viewModel.filterText(query)
            }

        }
        binding.searchView.setOnFocusChangeListener { searchView, b ->
            if (b)
                (searchView as EditText).addTextChangedListener(searchTextWatcher)
            else
                (searchView as EditText).removeTextChangedListener(searchTextWatcher)

        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_suspected_list)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_suspected_list)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    /**
     * Loading, empty and list are mutually exclusive. Previously only fl_empty was toggled, so
     * before the first emission the screen was blank, and an empty first emission drew
     * "No records found" on top of rv_any rather than instead of it.
     */
    private fun renderListState(items: List<*>) {
        _binding?.let { b ->
            b.rvAny.removeCallbacks(showSearchLoader)
            b.flLoading.visibility = View.GONE
            b.flEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            b.rvAny.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** Posted on each query change; the next emission cancels it, so a fast filter never flashes. */
    private val showSearchLoader = Runnable {
        _binding?.let { b ->
            b.flEmpty.visibility = View.GONE
            b.rvAny.visibility = View.GONE
            b.flLoading.visibility = View.VISIBLE
        }
    }

    private var lastQuery: String? = null

    private fun onQueryChanged(query: String) {
        // The ViewModel normalizes to lowercase and the filter is a StateFlow, which drops an
        // equal value - so a keystroke that doesn't change the normalized query (a trailing
        // space, or re-casing a letter) produces no emission, and nothing would ever clear a
        // loader scheduled here. Compare against the same normalized form.
        val normalized = query.lowercase()
        if (normalized == lastQuery) return
        lastQuery = normalized
        _binding?.rvAny?.let { rv ->
            rv.removeCallbacks(showSearchLoader)
            rv.postDelayed(showSearchLoader, SEARCH_LOADER_DELAY_MS)
        }
    }

    companion object {
        private const val SEARCH_LOADER_DELAY_MS = 300L
    }
}
