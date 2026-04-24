package org.piramalswasthya.stoptb

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.helpers.CrashHandler
import org.piramalswasthya.stoptb.helpers.CrashlyticsTree
import org.piramalswasthya.stoptb.helpers.SyncLogFileWriter
import org.piramalswasthya.stoptb.helpers.SyncLogManager
import org.piramalswasthya.stoptb.helpers.SyncLogTree
import org.piramalswasthya.stoptb.utils.KeyUtils
import timber.log.Timber
import javax.inject.Inject


@HiltAndroidApp
class SakhiApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var database: InAppDb

    @Inject
    lateinit var syncLogManager: SyncLogManager

    @Inject
    lateinit var syncLogFileWriter: SyncLogFileWriter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(SyncLogTree(syncLogManager))
        syncLogFileWriter.runRotation()
        KeyUtils.encryptedPassKey()
        KeyUtils.baseAbhaUrl()
        KeyUtils.baseTMCUrl()
        KeyUtils.abhaAuthUrl()
        KeyUtils.abhaClientID()
        KeyUtils.abhaClientSecret()
        KeyUtils.abhaTokenUrl()
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        Timber.plant(CrashlyticsTree())
        createNotificationChannels()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))

        // Recover any records orphaned in SYNCING state from a previous crash/kill
        CoroutineScope(Dispatchers.IO).launch {
            try {
                recoverOrphanedSyncStates()
            } catch (e: Exception) {
                Timber.e(e, "Failed to recover orphaned SYNCING states")
            }
        }
    }

    /**
     * Create notification channels early so that WorkManager workers always have
     * a valid channel available — even after a device reboot when workers restart
     * before LoginActivity.onCreate() runs.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Sync channel used by all push/pull/dynamic workers
            nm.createNotificationChannel(
                NotificationChannel(
                    getString(R.string.notification_sync_channel_id),
                    getString(R.string.notification_sync_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notification_sync_channel_description)
                }
            )

            // Download channel used by DownloadCardWorker
            nm.createNotificationChannel(
                NotificationChannel(
                    "download abha card",
                    "Download ABHA Card",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for ABHA card downloads"
                }
            )
        }
    }

    private suspend fun recoverOrphanedSyncStates() {
        // Reset all records stuck in SYNCING (ordinal 1) back to UNSYNCED (ordinal 0).
        // No sync can be in progress at app startup, so any SYNCING record is orphaned.
        database.benDao.resetSyncingToUnsynced()
        database.cbacDao.resetSyncingToUnsynced()
        database.aesDao.resetSyncingToUnsynced()
        database.filariaDao.resetSyncingToUnsynced()
        database.kalaAzarDao.resetSyncingToUnsynced()

        // Multi-table DAOs
        database.leprosyDao.resetScreeningSyncingToUnsynced()
        database.leprosyDao.resetFollowUpSyncingToUnsynced()
        database.malariaDao.resetScreeningSyncingToUnsynced()
        database.malariaDao.resetConfirmedSyncingToUnsynced()
        database.tbDao.resetScreeningSyncingToUnsynced()
        database.tbDao.resetSuspectedSyncingToUnsynced()
        database.tbDao.resetConfirmedSyncingToUnsynced()

        Timber.d("Recovered orphaned SYNCING states at startup")
    }
}
