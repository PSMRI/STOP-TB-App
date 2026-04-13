package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.ncd_priority_list

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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchRvButtonBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.home_activity.home.HomeViewModel
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject


@AndroidEntryPoint
class NcdPriorityListFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao


    private val binding: FragmentDisplaySearchRvButtonBinding by lazy {
        FragmentDisplaySearchRvButtonBinding.inflate(layoutInflater)
    }

    private val viewModel: NcdPriorityListViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNextPage.visibility = View.GONE

        val benAdapter = BenListAdapter(
            clickListener = BenListAdapter.BenClickListener(
                { _, _, _, _ -> },
                clickedWifeBen = { _, _, _, _ -> },
                clickedHusbandBen = { _, _, _, _ -> },
                clickedChildben = { _, _, _, _ -> },
                { _, _ -> },
                { _, _, _ -> },
                { _, _, _, _, _ -> },
                { },
                { },
            ),
            showBeneficiaries = true,
            pref = prefDao,
            context = requireActivity(),
            showActionButtons = false
        )
        binding.rvAny.adapter = benAdapter

        lifecycleScope.launch {
            viewModel.benList.collect {
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
                viewModel.filterText(p0?.toString() ?: "")
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
            if (it is VolunteerActivity) {
                it.updateActionBar(
                    R.drawable.ic__ncd_priority,
                    getString(R.string.ncd_priority_list)
                )
            } else {
                (it as HomeActivity).updateActionBar(
                    R.drawable.ic__ncd_priority,
                    getString(R.string.ncd_priority_list)
                )
            }
        }
    }

}