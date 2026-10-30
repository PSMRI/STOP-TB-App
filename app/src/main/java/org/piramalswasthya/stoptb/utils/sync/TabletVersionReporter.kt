package org.piramalswasthya.stoptb.utils.sync

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Tells the camp laptop which app version this tablet runs, so AMRIT Fleet can flag camps whose
 * tablets need an update. Only at a real camp: the camp hub URL must point at a private IP address
 * (a hostname means a server such as UAT, which has no laptop console). The report goes to the
 * laptop console's tablet port on that IP, not to the AMRIT APIs.
 *
 * Blocking network call: use it off the main thread (TabletVersionReportWorker).
 */
object TabletVersionReporter {

    const val TABLET_PORT = 9091
    private const val TIMEOUT_MS = 3000
    private const val MIN_INTERVAL_MS = 60 * 60 * 1000L
    private const val PREFS = "tablet_version_report"
    private const val LAST_AT = "last_at"
    private const val LAST_KEY = "last_key"
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** http://<ip>:9091/tablet/report for a camp hub URL whose host is a private IPv4 address, else null. */
    fun reportUrl(campHubUrl: String?): String? {
        val raw = campHubUrl?.trim().orEmpty()
        val withScheme = if ("://" in raw) raw else "http://$raw"     // QR codes may hold a bare host:port
        val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
        val m = IPV4.matchEntire(host) ?: return null
        val o = m.groupValues.drop(1).map { it.toInt() }
        if (o.any { it > 255 } || !isPrivate(o)) return null
        return "http://$host:$TABLET_PORT/tablet/report"
    }

    /** Camp laptops are on a private network; never send the tablet id and user name anywhere else. */
    private fun isPrivate(o: List<Int>): Boolean =
        o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)

    /**
     * Sends the report when camp mode is on and the hub is an IP. Without [force], at most once an
     * hour unless the version, user or camp changed since the last report that got through.
     */
    fun report(context: Context, pref: PreferenceDao, force: Boolean = false): Boolean {
        if (!pref.isCampModeEnabled()) return false
        val url = reportUrl(pref.getCampHubUrl()) ?: return false
        val userName = pref.getLoggedInUser()?.userName ?: pref.getRememberedUserName()
        val key = listOf(url, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, userName).joinToString("|")
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && store.getString(LAST_KEY, null) == key && now - store.getLong(LAST_AT, 0L) < MIN_INTERVAL_MS) return false

        val body = JSONObject()
            .put("tabletId", tabletId(context))
            .put("app", context.packageName)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
        if (!userName.isNullOrBlank()) body.put("userName", userName.trim())

        var connection: HttpURLConnection? = null
        val ok = runCatching {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json")
            }
            connection!!.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            connection!!.responseCode in 200..299
        }.onFailure {
            Timber.d("Tablet version report not sent: ${it.javaClass.simpleName}: ${it.message}")
        }.also { connection?.disconnect() }.getOrDefault(false)

        if (ok) store.edit().putString(LAST_KEY, key).putLong(LAST_AT, now).apply()
        return ok
    }

    @SuppressLint("HardwareIds")
    private fun tabletId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
}
