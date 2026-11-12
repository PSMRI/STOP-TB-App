package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SyncDashboardPagerAdapter(
    fragment: Fragment
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SyncStatusTabFragment()
            1 -> SyncLogsTabFragment()
            2 -> SyncSummaryTabFragment()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }
}
