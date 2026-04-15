package org.piramalswasthya.stoptb.ui.home_activity.all_ben

import android.app.AlertDialog
import android.content.Intent
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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.adapters.BenPagingAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.AlertFilterBinding
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchAndToggleRvButtonBinding
import org.piramalswasthya.stoptb.ui.abha_id_activity.AbhaIdActivity
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AllBenFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentDisplaySearchAndToggleRvButtonBinding? = null

    private val binding: FragmentDisplaySearchAndToggleRvButtonBinding
        get() = _binding!!

    val args: AllBenFragmentArgs by lazy {
        AllBenFragmentArgs.fromBundle(requireArguments())
    }

    private lateinit var benAdapter: BenPagingAdapter

    private var selectedAbha = Abha.ALL

    private val viewModel: AllBenViewModel by viewModels()

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        binding.searchView.setText(lowerValue)
        binding.searchView.setSelection(lowerValue.length)
        viewModel.filterText(lowerValue)
    }

    private val abhaDisclaimer by lazy {
        AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.beneficiary_abha_number))
            .setMessage("it")
            .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
            .create()
    }

    enum class Abha {
        ALL, WITH, WITHOUT, AGE_ABOVE_30,
    }

    private val filterAlert by lazy {
        val filterAlertBinding = AlertFilterBinding.inflate(layoutInflater, binding.root, false)
        filterAlertBinding.rgAbha.setOnCheckedChangeListener { radioGroup, i ->
            Timber.d("RG Gender selected id : $i")
            selectedAbha = when (i) {
                filterAlertBinding.rbAll.id -> Abha.ALL
                filterAlertBinding.rbWith.id -> Abha.WITH
                filterAlertBinding.rbWithout.id -> Abha.WITHOUT
                filterAlertBinding.rbAgeAboveThirty.id -> Abha.AGE_ABOVE_30
                else -> Abha.ALL
            }
        }

        filterAlertBinding.tvRch.visibility = View.GONE
        filterAlertBinding.cbRch.visibility = View.GONE

        val alert = MaterialAlertDialogBuilder(requireContext())
            .setView(filterAlertBinding.root)
            .setOnCancelListener {}
            .create()

        filterAlertBinding.btnOk.setOnClickListener {
            val filter = when (selectedAbha) {
                Abha.WITH -> 1
                Abha.WITHOUT -> 2
                Abha.AGE_ABOVE_30 -> 3
                else -> 0
            }
            viewModel.filterType(filter)
            alert.cancel()
        }
        filterAlertBinding.btnCancel.setOnClickListener {
            alert.cancel()
        }
        alert
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchAndToggleRvButtonBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val isVolunteer = prefDao.getLoggedInUser()?.role
//            .equals(RoleConstants.ROLE_VOLUNTEER, true)
        val isVolunteer = true
        if (isVolunteer) {
            binding.btnNextPage.visibility = View.VISIBLE
            binding.btnNextPage.text = getString(R.string.add_beneficiary)
            binding.btnNextPage.setOnClickListener {
                findNavController().navigate(
                    AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                        hhId = 0L,
                        relToHeadId = 18,
                        gender = 0
                    )
                )
            }
        } else {
            binding.btnNextPage.visibility = View.GONE
        }

        binding.ibFilter.setOnClickListener {
            filterAlert.show()
        }

        binding.ibDownload.setOnClickListener {
            viewModel.downloadCsv(requireContext())
        }

        if (args.source == 1 || args.source == 2 || args.source == 3 || args.source == 4) {
            binding.ibFilter.visibility = View.GONE
            binding.ibDownload.visibility = View.VISIBLE
        }

        var lastClickTime = 0L
        benAdapter = BenPagingAdapter(
            clickListener = BenListAdapter.BenClickListener(
                { item, hhId, benId, relToHeadId ->
                    val now = System.currentTimeMillis()
                    timber.log.Timber.d("BEN_CLICK: clicked benId=$benId, hhId=$hhId, time=$now")
                    if (now - lastClickTime > 1000) {
                        lastClickTime = now
                        timber.log.Timber.d("BEN_CLICK: navigating to form for benId=$benId")
                        val navOptions = NavOptions.Builder()
                            .setEnterAnim(0)
                            .setExitAnim(0)
                            .setPopEnterAnim(0)
                            .setPopExitAnim(0)
                            .setLaunchSingleTop(true)
                            .build()
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                                hhId = hhId,
                                benId = benId,
                                relToHeadId = relToHeadId,
                                isAddSpouse = 0,
                                gender = 0
                            ),
                            navOptions
                        )
                    }
                },
                clickedWifeBen = { item, hhId, benId, relToHeadId -> },
                clickedHusbandBen = { item, hhId, benId, relToHeadId -> },
                clickedChildben = { item, hhId, benId, relToHeadId -> },
                { item, hhid -> },
                { item, benId, hhId ->
                    checkAndGenerateABHA(benId)
                },
                { item, benId, hhId, isViewMode, isIFA -> },
                { },
                { }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            showAbha = true,
            showCall = true,
            pref = prefDao,
            context = requireActivity()
        )

        binding.rvAny.adapter = benAdapter
        binding.rvAny.setHasFixedSize(true)
        binding.rvAny.setItemViewCacheSize(20)
        (binding.rvAny.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.apply {
            initialPrefetchItemCount = 10
        }

        lifecycleScope.launch {
            viewModel.benList.collectLatest {
                benAdapter.submitData(it)
            }
        }

        lifecycleScope.launch {
            benAdapter.loadStateFlow.collectLatest { loadStates ->
                val isEmpty = loadStates.refresh is LoadState.NotLoading
                        && benAdapter.itemCount == 0
                binding.flEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                // Volunteer ke liye button hamesha visible rahega
                if (isVolunteer) {
                    binding.btnNextPage.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.childCounts.collectLatest { countMap ->
                benAdapter.submitChildCounts(countMap)
            }
        }

        binding.ibSearch.visibility = View.VISIBLE
        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                viewModel.filterText(p0?.toString() ?: "")
            }
        }

        binding.searchView.setOnFocusChangeListener { searchView, b ->
            if (b) (searchView as EditText).addTextChangedListener(searchTextWatcher)
            else (searchView as EditText).removeTextChangedListener(searchTextWatcher)
        }

        viewModel.abha.observe(viewLifecycleOwner) {
            it.let {
                if (it != null) {
                    abhaDisclaimer.setMessage(it)
                    abhaDisclaimer.show()
                }
            }
        }

        viewModel.benRegId.observe(viewLifecycleOwner) {
            if (it != null) {
                val intent = Intent(requireActivity(), AbhaIdActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("benId", viewModel.benId.value)
                intent.putExtra("benRegId", it)
                requireActivity().startActivity(intent)
                viewModel.resetBenRegId()
            }
        }
    }

    private fun checkAndGenerateABHA(benId: Long) {
        lifecycleScope.launch {
            if (viewModel.getBenFromId(benId) == 0L) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Alert!")
                    .setMessage("Please wait for the record to sync and try again.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                viewModel.fetchAbha(benId)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            val title = if (args.source == 1) {
                getString(R.string.icon_title_abhas)
            } else if (args.source == 2) {
                getString(R.string.icon_title_rchs)
            } else {
                getString(R.string.icon_title_ben)
            }

            when (it) {
                is HomeActivity -> it.updateActionBar(R.drawable.ic__ben, title)
                is VolunteerActivity -> it.updateActionBar(R.drawable.ic__ben, title)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}