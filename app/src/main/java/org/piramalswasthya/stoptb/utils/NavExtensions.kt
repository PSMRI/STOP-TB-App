package org.piramalswasthya.stoptb.utils

import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections

fun NavController.navigateSafe(directions: NavDirections) {
    currentDestination?.getAction(directions.actionId)?.let {
        navigate(directions)
    }
}

// Safely navigate only if the action exists in the current destination to prevent duplicate navigation crashes.
fun NavController.navigateSafe(@IdRes actionId: Int) {
    currentDestination?.getAction(actionId)?.let {
        navigate(actionId)
    }
}
