package org.piramalswasthya.stoptb.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.piramalswasthya.stoptb.ui.home_activity.dashboard.DashboardFragment
import org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerIconsFragment

/** Build with (childFragmentManager, viewLifecycleOwner.lifecycle) so the pages don't outlive the host view. */
class VolunteerPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> VolunteerIconsFragment()
            1 -> DashboardFragment()
            else -> throw IllegalStateException("Index >1 called!")
        }
    }
}