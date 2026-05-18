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
 * @param isManagedFlow True when the screen is part of a multi-step flow (nurse or General OPD flow).
 * @param allowBack When true, toolbar and system back work normally (e.g. first screen in a flow).
 */
fun Fragment.blockBackNavigationInManagedFlow(isManagedFlow: Boolean, allowBack: Boolean) {
    if (!isManagedFlow || allowBack) return
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        }
    )
}

fun Fragment.applyManagedFlowBackPolicyOnResume(isManagedFlow: Boolean, allowBack: Boolean) {
    if (!isManagedFlow) {
        setAutoFlowBackNavigationBlocked(false)
        return
    }
    setAutoFlowBackNavigationBlocked(!allowBack)
}

/** @see blockBackNavigationInManagedFlow */
fun Fragment.blockBackNavigationInAutoFlow(isAutoFlow: Boolean) {
    blockBackNavigationInManagedFlow(isManagedFlow = isAutoFlow, allowBack = false)
}

/** @see applyManagedFlowBackPolicyOnResume */
fun Fragment.applyAutoFlowBackPolicyOnResume(isAutoFlow: Boolean, allowBack: Boolean) {
    applyManagedFlowBackPolicyOnResume(isManagedFlow = isAutoFlow, allowBack = allowBack)
}
