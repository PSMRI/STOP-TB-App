package org.piramalswasthya.stoptb.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import timber.log.Timber
import java.util.Locale

object GpsDiagnostics {

    const val TAG = "GPS_DIAG"

    const val FETCH_TIMEOUT_MS = 60_000L
    const val MASTER_TIMEOUT_MS = 75_000L

    fun isGpsHardwareAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    fun isGpsProviderEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return try {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun logPreflight(context: Context, screen: String) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsProviderEnabled = isGpsProviderEnabled(context)
        val networkProviderEnabled = try {
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
        } catch (e: Exception) {
            false
        }
        val locationServicesOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm?.isLocationEnabled ?: false
        } else {
            gpsProviderEnabled || networkProviderEnabled
        }

        val hasGpsHardware = isGpsHardwareAvailable(context)
        val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)

        val airplaneMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            false
        }

        // Logged only for correlation — GPS capture itself never checks this.
        val internetAvailable = try {
            isInternetAvailable(context)
        } catch (e: Exception) {
            false
        }

        Timber.tag(TAG).i(
            "[$screen] preflight: hasGpsHardware=$hasGpsHardware gpsProviderEnabled=$gpsProviderEnabled " +
                "networkProviderEnabled=$networkProviderEnabled locationServicesOn=$locationServicesOn " +
                "fineGranted=$fineGranted coarseGranted=$coarseGranted " +
                "playServicesStatus=$playServicesStatus airplaneMode=$airplaneMode " +
                "internetAvailable=$internetAvailable (not required for GPS)"
        )
    }

    fun locationLogLine(location: Location, fixElapsedMs: Long): String {
        val ageMs = System.currentTimeMillis() - location.time
        val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
        val accuracy = if (location.hasAccuracy()) "${location.accuracy}m" else "unknown"
        return "provider=${location.provider ?: "fused"} accuracy=$accuracy ageMs=$ageMs " +
            "fixTimeMs=$fixElapsedMs mock=$mock"
    }

    fun locationUiSummary(location: Location, fixElapsedMs: Long, fromCache: Boolean): String {
        val accuracy = if (location.hasAccuracy()) "±${location.accuracy.toInt()}m" else "accuracy unknown"
        return if (fromCache) {
            val ageSec = (System.currentTimeMillis() - location.time) / 1000
            "$accuracy, cached fix (${ageSec}s old)"
        } else {
            "$accuracy, fix in %.1fs".format(Locale.ENGLISH, fixElapsedMs / 1000.0)
        }
    }

    fun describeFailure(e: Throwable): String = when (e) {
        is ApiException -> "ApiException statusCode=${e.statusCode} message=${e.message}"
        else -> "${e::class.java.simpleName}: ${e.message}"
    }
}
