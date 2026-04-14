package org.piramalswasthya.stoptb.ui.home_activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity


class AboutUsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about_us, container, false)
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(R.drawable.ic_info, getString(R.string.about))
                is VolunteerActivity -> it.updateActionBar(R.drawable.ic_info, getString(R.string.about))
            }
        }
    }
}