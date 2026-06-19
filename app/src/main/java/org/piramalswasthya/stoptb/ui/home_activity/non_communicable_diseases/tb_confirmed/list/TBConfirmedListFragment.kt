package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_confirmed.list

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.TbConfirmedListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchRvButtonBinding
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.CounsellingActivity
import org.piramalswasthya.stoptb.ui.counselling_activity.CounsellingViewModel
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.getValue


@AndroidEntryPoint
class TBConfirmedListFragment : Fragment() {


    @Inject
    lateinit var prefDao: PreferenceDao

    @Inject
    lateinit var counsellingRepository: ICounsellingRepository

    private var _binding: FragmentDisplaySearchRvButtonBinding? = null
    private val binding: FragmentDisplaySearchRvButtonBinding
        get() = _binding!!

    private val viewModel: TBConfirmedListViewModel by viewModels()

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
        val benAdapter = TbConfirmedListAdapter(
            TbConfirmedListAdapter.ClickListener(
                clickedForm = { hhId, benId ->
                    findNavController().navigate(
                        TBConfirmedListFragmentDirections
                            .actionTBConfirmedListFragmentToTBConfirmedFormFragment(benId)
                    )
                },
                clickedCounselling = { item ->
                    val regDateMillis = try {
                        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).parse(item.ben.regDate)?.time
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "Failed to parse registration date: ${item.ben.regDate}")
                        null
                    } ?: 0L
                    val overviewData = CounsellingOverviewData(
                        benId = item.ben.benId,
                        patientName = item.ben.benFullName,
                        nikshayId = item.ben.nikshayId ?: "",
                        counsellingDate = SimpleDateFormat(
                            "dd-MM-yyyy",
                            Locale.ENGLISH
                        ).format(Date()),
                        counsellingOfficer = prefDao.getLoggedInUser()?.name ?: "",
                        regDate = regDateMillis,
                        beneficiaryId = if (item.ben.benId < 0L) "Pending Sync" else item.ben.benId.toString(),
                        ageGender = "${item.ben.age} / ${item.ben.gender}",
                        diagnosis = item.tbSuspected?.typeOfTBCase ?: ""
                    )

                    startActivity(
                        Intent(requireContext(), CounsellingActivity::class.java)
                            .putExtra(CounsellingViewModel.EXTRA_BEN_ID, item.ben.benId)
                            .putExtra(CounsellingViewModel.EXTRA_OVERVIEW_DATA, overviewData)
                    )
                }
            ),
            pref = prefDao
        )
        binding.rvAny.adapter = benAdapter

        viewLifecycleOwner.lifecycleScope.launch {
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
            when (it) {
                is HomeActivity -> it.updateActionBar(R.drawable.ic__ncd, getString(R.string.tb_confirmed_list))
                is VolunteerActivity -> it.updateActionBar(R.drawable.ic__ncd, getString(R.string.tb_confirmed_list))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

    }
    override fun onDestroy() {
        super.onDestroy()
    }
}