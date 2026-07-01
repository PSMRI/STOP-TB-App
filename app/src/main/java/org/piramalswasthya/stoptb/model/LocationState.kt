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
        object GpsDisabled : Failed()
        object NoSignal : Failed()
        object OutsideIndia : Failed()
    }
}
