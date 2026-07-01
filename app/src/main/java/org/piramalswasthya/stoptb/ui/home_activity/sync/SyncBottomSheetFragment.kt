package org.piramalswasthya.stoptb.ui.home_activity.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.adapters.SyncStatusAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.BottomSheetSyncBinding
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.model.asDomainModel

@AndroidEntryPoint
class SyncBottomSheetFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: BottomSheetSyncBinding? = null
    private val binding: BottomSheetSyncBinding
        get() = _binding!!

    private val viewModel: SyncViewModel by viewModels({ requireActivity() })

    // Rows visible to Registrar only (others hidden)
    private val registrarRows = setOf("Beneficiary", "Anthropometric", "TB Screening")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSyncBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = SyncStatusAdapter()
        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.rvSync.adapter = adapter
        binding.rvSync.addItemDecoration(divider)

        val localNames = viewModel.getLocalNames(requireContext())
        val englishNames = viewModel.getEnglishNames(requireContext())
        val isRegistrar = prefDao.getLoggedInUser()?.role.isRegistrationOfficerRole()
        val isCounsellingOfficer = prefDao.getLoggedInUser()?.role.isCounsellingOfficerRole()

        lifecycleScope.launch {
            viewModel.syncStatus.collect {
                var list = it.asDomainModel(localNames, englishNames)
                if (isRegistrar) {
                    // Registrar: show only Beneficiary, Anthropometric (Counselling excluded by this filter)
                    list = list.filter { item ->
                        val idx = localNames.indexOf(item.name)
                        val english = if (idx >= 0) englishNames.getOrNull(idx) ?: item.name else item.name
                        english in registrarRows
                    }
                } else if (!isCounsellingOfficer) {
                    // Nurse and other roles: hide Counselling
                    list = list.filter { item -> item.name != "Counselling" }
                }
                binding.nsv.layoutParams.height = if (list.size * 150 < 800) list.size * 150 else 800
                adapter.submitList(list)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
