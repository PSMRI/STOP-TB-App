package org.piramalswasthya.stoptb.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity

fun Fragment.callPhoneNumber(phoneNumber: String?) {
    val number = phoneNumber?.trim()
    if (number.isNullOrEmpty()) {
        Toast.makeText(requireContext(), "Mobile number not available", Toast.LENGTH_SHORT).show()
        return
    }

    val packageManager = requireContext().packageManager
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
        Toast.makeText(requireContext(), "Calling is not supported on this device", Toast.LENGTH_SHORT).show()
        return
    }

    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
        != PackageManager.PERMISSION_GRANTED
    ) {
        (activity as? HomeActivity)?.askForPermissions()
        Toast.makeText(requireContext(), "Please allow permissions first", Toast.LENGTH_SHORT).show()
        return
    }

    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
    if (callIntent.resolveActivity(packageManager) == null) {
        Toast.makeText(requireContext(), "No calling app found", Toast.LENGTH_SHORT).show()
        return
    }
    startActivity(callIntent)
}
