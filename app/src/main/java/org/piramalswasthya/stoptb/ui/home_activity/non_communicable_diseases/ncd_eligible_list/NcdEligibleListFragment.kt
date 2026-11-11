package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.ncd_eligible_list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.NCDCategoryAdapter
import org.piramalswasthya.stoptb.adapters.NcdCbacBenListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.databinding.FragmentNcdEligibleListBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.utils.HelperUtil.getDateStrFromLong
import timber.log.Timber

@AndroidEntryPoint
class NcdEligibleListFragment : Fragment() , NCDCategoryAdapter.ClickListener {



    // One binding per view, released in onDestroyView: the fragment stays on the back stack and
    // a lazily created binding kept the old view alive.
    private var _binding: FragmentNcdEligibleListBinding? = null
    private val binding: FragmentNcdEligibleListBinding
        get() = _binding!!

    private val viewModel: NcdEligibleListViewModel by viewModels()

    private val bottomSheet: NcdBottomSheetFragment by lazy { NcdBottomSheetFragment() }

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
        _binding = FragmentNcdEligibleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNextPage.visibility = View.GONE
        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, viewModel.yearsList())
        binding.tilRvDropdown.setAdapter(adapter)

        binding.rvCat.adapter = NCDCategoryAdapter(viewModel.categoryData(),this,viewModel)

        val benAdapter =
            NcdCbacBenListAdapter(
                clickListener = NcdCbacBenListAdapter.CbacFormClickListener(
                    clickedView = {
                        Timber.d("ClickListener Triggered!")
                        viewModel.setSelectedBenId(it)
                        if (!bottomSheet.isVisible)
                            bottomSheet.show(
                                childFragmentManager,
                                resources.getString(R.string.cbac)
                            )
                    },
                    clickedNew = { benId, nextFillDateMillis ->
                        if (nextFillDateMillis != null) {
                            Toast.makeText(
                                context,
                                "Available on ${getDateStrFromLong(nextFillDateMillis)}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            findNavController().navigate(
                                NcdEligibleListFragmentDirections.actionNcdEligibleListFragmentToCbacFragment(
                                    benId = benId,
                                    ashaId = viewModel.getAshaId()
                                )
                            )
                        }

                    })
            )
        binding.rvAny.adapter = benAdapter

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
        // View-scoped: stops with the view instead of piling up a collector per view.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ncdDetails.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collect {
                Timber.d("Collecting Ncd Details : $it")
            }
        }
        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                viewModel.filterText(p0?.toString()?.trim() ?: "")
            }

        }
        binding.tilRvDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                if (selectedItem != "Select Years") {
                    viewModel.filterText(selectedItem)
                }

            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                Timber.d("Called here!")
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
                    R.drawable.ic__ncd_list,
                    getString(R.string.ncd_eligible_list)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd_list,
                    getString(R.string.ncd_eligible_list)
                )
            }
        }
    }

    override fun onClicked(catDataList: String) {
        viewModel.setSelectedCategory(catDataList)
    }

}