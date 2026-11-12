package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.ncd_referred.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.NcdReferListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchRvButtonBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.ncd_referred.filter.NCDReferTypeFilter
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class NcdRefferedList : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao
    // One binding per view, released in onDestroyView: the fragment stays on the back stack and
    // a lazily created binding kept the old view alive.
    private var _binding: FragmentDisplaySearchRvButtonBinding? = null
    private val binding: FragmentDisplaySearchRvButtonBinding
        get() = _binding!!

    private val filterBottomSheet: NCDReferTypeFilter by lazy { NCDReferTypeFilter() }


    private val viewModel: NcdRefferedListViewModel by viewModels()

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        // A result can arrive with no view (fragment on the back stack): ignore the text then.
        _binding?.searchView?.let { searchView ->
            searchView.setText(lowerValue)
            searchView.setSelection(lowerValue.length)
        }
        viewModel.filterText(lowerValue)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchRvButtonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNextPage.visibility = View.GONE
        binding.filterText.visibility = View.VISIBLE

        val benAdapter =
            NcdReferListAdapter(viewModel.userName, NcdReferListAdapter.NcdReferallickListener { benId ,hhId->
                findNavController().navigate(
                    NcdRefferedListDirections
                        .actionNcdRefferedListToNCDReferalFormFragment(
                            benId = benId,
                            hhId = hhId
                        )
                )
            },true)
        binding.rvAny.adapter = benAdapter



        binding.ivFilter.setOnClickListener {
            if (!filterBottomSheet.isVisible)
                filterBottomSheet.show(childFragmentManager, "ImM")
        }

        binding.tvSelectedFilter.setOnClickListener {
            if (!filterBottomSheet.isVisible)
                filterBottomSheet.show(childFragmentManager, "ImM")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedFilter.collect { value ->
                    binding.tvSelectedFilter.text = value
                }
            }
        }


        // Loader until the first emission; "No records found" only once the list is known empty.
        binding.flLoading.visibility = View.VISIBLE
        binding.flEmpty.visibility = View.GONE
        // View-scoped: stops with the view instead of piling up a collector per view.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.benList.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collect {
                binding.flLoading.visibility = View.GONE
                if (it.isEmpty())
                    binding.flEmpty.visibility = View.VISIBLE
                else
                    binding.flEmpty.visibility = View.GONE
                benAdapter.submitList(it)
            }
        }

        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                viewModel.filterText(p0?.toString()?.trim() ?: "")
            }

        }
        binding.searchView.setOnFocusChangeListener { searchView, b ->
            if (b)
                (searchView as EditText).addTextChangedListener(searchTextWatcher)
            else
                (searchView as EditText).removeTextChangedListener(searchTextWatcher)

        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd_priority,
                    getString(R.string.ncd_refer_list)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd_priority,
                    getString(R.string.ncd_refer_list)
                )
            }
        }
    }

}