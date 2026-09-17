package org.piramalswasthya.stoptb.model

sealed class LocationState {
    object Idle : LocationState()
    object Fetching : LocationState()
    data class Captured(
        val lat: Double,
        val lon: Double,
        val digipin: String,
        val timestamp: String
    ) : LocationState()
    sealed class Failed : LocationState() {
        object PermissionDenied : Failed()
        // Location is off system-wide. Acquired via raw android.location.LocationManager
        object GpsDisabled : Failed()
        object NoSignal : Failed()
        object OutsideIndia : Failed()
        // Device has no GPS chip, or the GPS provider is specifically off
        object NoGpsProvider : Failed()
        // We hit our own explicit fetch cutoff
        object Timeout : Failed()
    }
}
