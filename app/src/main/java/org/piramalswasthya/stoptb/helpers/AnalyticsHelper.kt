package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

class AnalyticsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val firebaseAnalytics = Firebase.analytics
    private val crashlytics = FirebaseCrashlytics.getInstance()

    fun setUserId(userId: String) {
        firebaseAnalytics.setUserId(userId)
        crashlytics.setUserId(userId)
    }

    fun setUserProperty(key: String, value: String) {
        firebaseAnalytics.setUserProperty(key, value)
        crashlytics.setCustomKey(key, value)
    }

    fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics.logEvent(name, params)
    }

    fun logEvent(eventName: String, json: JSONObject?) {
        val bundle = Bundle()
        json?.keys()?.forEach { key ->
            when (val value = json.get(key)) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Double -> bundle.putDouble(key, value)
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        firebaseAnalytics.logEvent(eventName, bundle)

      /*
      //Usage
      fun sendEvent() {
            val json = JSONObject().apply {
                put("screen", "HomeScreen")
                put("button_clicked", "Start")
                put("timestamp", System.currentTimeMillis())
                put("user_type", "premium")
            }
            analyticsHelper.logEvent("custom_button_click", json)
        }*/
    }

    fun logCustomTimestampEvent(eventName: String, timestamp: Long) {
        firebaseAnalytics.logEvent(eventName, Bundle().apply {
            putLong("${eventName}_time", timestamp)
        })
    }

    fun logApiCall(
        endpoint: String,
        durationMs: Long,
        isSuccess: Boolean,
        responseCode: Int? = null,
        errorMessage: String? = null
    ) {
        val bundle = Bundle().apply {
            putString("api_name", endpoint)
            putLong("response_time_ms", durationMs)
            putString("status", if (isSuccess) "success" else "failure")
            responseCode?.let { putInt("response_code", it) }
            errorMessage?.let { putString("error_message", it.take(100)) } // limit to 100 chars
        }
        firebaseAnalytics.logEvent("api_call_event", bundle)
    }
}
