package org.piramalswasthya.stoptb.helpers

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity

interface AutoFlowBackNavigationHost {
    fun setAutoFlowBackNavigationBlocked(blocked: Boolean)
}

fun Fragment.setAutoFlowBackNavigationBlocked(blocked: Boolean) {
    when (val host = activity) {
        is AutoFlowBackNavigationHost -> host.setAutoFlowBackNavigationBlocked(blocked)
    }
}

/**
 * In the nurse auto-flow (General Examination → TB Screening → General OPD → Diagnostics),
 * back navigation must be disabled on intermediate steps so users cannot return to a prior form.
 */
fun Fragment.blockBackNavigationInAutoFlow(isAutoFlow: Boolean) {
    if (!isAutoFlow) return
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        }
    )
}

/**
 * @param allowBack When true, toolbar and system back work normally (e.g. General Examination entry).
 */
fun Fragment.applyAutoFlowBackPolicyOnResume(isAutoFlow: Boolean, allowBack: Boolean) {
    if (!isAutoFlow) {
        setAutoFlowBackNavigationBlocked(false)
        return
    }
    setAutoFlowBackNavigationBlocked(!allowBack)
}
