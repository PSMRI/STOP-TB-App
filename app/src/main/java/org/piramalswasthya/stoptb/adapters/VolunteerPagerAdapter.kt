package org.piramalswasthya.stoptb.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.piramalswasthya.stoptb.ui.home_activity.dashboard.DashboardFragment
import org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerIconsFragment

class VolunteerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DashboardFragment()
            1 -> VolunteerIconsFragment()
            else -> throw IllegalStateException("Index >1 called!")
        }
    }
}