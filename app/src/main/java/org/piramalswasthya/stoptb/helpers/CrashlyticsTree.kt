package org.piramalswasthya.stoptb.helpers

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class CrashlyticsTree(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return

        val crashlyticsMessage = buildString {
            append(tag ?: "App")
            append(": ")
            append(message)
        }
        crashlytics.log(crashlyticsMessage.take(MAX_LOG_LENGTH))

        if (priority >= Log.ERROR && t != null) {
            crashlytics.recordException(t)
        }
    }

    private companion object {
        const val MAX_LOG_LENGTH = 4000
    }
}
