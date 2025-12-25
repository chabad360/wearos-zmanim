package me.chabad360.zmanim.wearos

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.chabad360.zmanim.wearos.workers.ZmanimUpdateWorker
import java.util.concurrent.TimeUnit

/**
 * Application class to initialize background workers
 */
class ZmanimApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ZmanimApplication onCreate")

        // Schedule periodic zmanim updates
        scheduleZmanimUpdates()
    }

    private fun scheduleZmanimUpdates() {
        val constraints = Constraints.Builder()
            // Only run when device is not in battery saver mode
//            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ZmanimUpdateWorker>(
            1, TimeUnit.HOURS, // Run every hour
            15, TimeUnit.MINUTES // With 15 minute flex period
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ZmanimUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            workRequest
        )

        Log.d(TAG, "Scheduled periodic zmanim updates (every 1 hour)")
    }

    companion object {
        private const val TAG = "ZmanimApplication"
    }
}

