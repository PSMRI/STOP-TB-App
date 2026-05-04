package org.piramalswasthya.stoptb.ui.volunteer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.VolunteerPagerAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentHomeBinding
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject

@AndroidEntryPoint
class VolunteerHomeFragment : Fragment() {
    @Inject
    lateinit var pref: PreferenceDao

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViewPager()
    }

    private fun setUpViewPager() {
        binding.vp2Home.adapter = VolunteerPagerAdapter(this)
        TabLayoutMediator(binding.tlHomeViewpager, binding.vp2Home) { tab, position ->
            tab.text = when (position) {
                0 -> requireActivity().getString(R.string.menu_home_scheduler)
                1 -> requireActivity().getString(R.string.menu_home_home)
                else -> "NA"
            }
        }.attach()
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            (it as VolunteerActivity).updateActionBar(
                R.drawable.ic_home,
                getHomeToolbarTitle()
            )
            it.addClickListenerToHomepageActionBarTitle()
        }
        binding.vp2Home.setCurrentItem(1, false)
    }

    private fun getHomeToolbarTitle(): String {
        val village = pref.getLocationRecord()?.village ?: return getString(R.string.home)
        return when (pref.getCurrentLanguage()) {
            Languages.ENGLISH -> village.name
            Languages.HINDI -> village.nameHindi ?: village.name
            Languages.ASSAMESE -> village.nameAssamese ?: village.name
        }
    }

    override fun onStop() {
        activity?.let {
            (it as VolunteerActivity).removeClickListenerToHomepageActionBarTitle()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
