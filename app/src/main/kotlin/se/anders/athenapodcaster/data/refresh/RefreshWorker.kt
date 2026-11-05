package se.anders.athenapodcaster.data.refresh

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import se.anders.athenapodcaster.AthenaPodcasterApplication

/** The periodic background refresh (FR-030). Feeds are small, so any network will do; the downloads it queues follow the Wi-Fi rule. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Connection problems leave no message on the feeds; the job just asks to be tried again later (back-off).
        val unreachable = (applicationContext as AthenaPodcasterApplication).container.sync.refreshAll(background = true)
        return if (unreachable) Result.retry() else Result.success()
    }
}

object RefreshScheduler {
    private const val NAME = "refresh"

    /** [hours] 0 means manual only: any scheduled refresh is removed. */
    fun apply(context: Context, hours: Int) {
        val work = WorkManager.getInstance(context)
        if (hours <= 0) { work.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
