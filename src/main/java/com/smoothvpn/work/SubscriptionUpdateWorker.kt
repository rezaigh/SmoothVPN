package com.smoothvpn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Scheduled subscription auto-update + de-dup, on top of your net/SubscriptionUpdater.
 *
 * Implement SubscriptionRefresher somewhere (e.g. in your repository layer) and
 * expose it to the worker via a small provider. Then schedule it once at app start:
 *
 *   SubscriptionUpdateScheduler.schedule(context, everyHours = 12)
 */
interface SubscriptionRefresher {
    /** Refresh every subscription group and persist results. Return true on success. */
    suspend fun refreshAll(): Boolean
}

object SubscriptionRefresherProvider {
    @Volatile var instance: SubscriptionRefresher? = null
}

class SubscriptionUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refresher = SubscriptionRefresherProvider.instance ?: return Result.success()
        return try {
            if (refresher.refreshAll()) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object SubscriptionUpdateScheduler {
    private const val WORK_NAME = "smoothvpn-subscription-update"

    fun schedule(context: Context, everyHours: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
            everyHours.coerceAtLeast(1), TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
