package org.piramalswasthya.stoptb.utils

import androidx.navigation.NavController
import androidx.navigation.NavDirections

fun NavController.navigateSafe(directions: NavDirections) {
    currentDestination?.getAction(directions.actionId)?.let {
        navigate(directions)
    }
}
