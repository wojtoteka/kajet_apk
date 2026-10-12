package wojtoteka.ovh.kajet.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWork(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val sync = syncProvider?.invoke(applicationContext) ?: return Result.success()

        val result = runCatching { sync.synchronise() }.getOrElse {
            // An unexpected error. We try again, because the notes are sitting
            // safely on the tablet anyway and nothing is lost.
            return Result.retry()
        }

        return when {
            result.fullySucceeded -> Result.success()
            result.worthRetrying -> Result.retry()
            // An error that repeating will not help, for example running out of
            // account storage. We finish with success so the system does not
            // pound away forever; the person will see the message in the app.
            else -> Result.success()
        }
    }

    companion object {
        private const val ONE_OFF_NAME = "kajet-sync-now"
        private const val PERIODIC_NAME = "kajet-sync-periodic"
        private const val AFTER_PAUSE_NAME = "kajet-sync-after-pause"

        @Volatile
        var syncProvider: ((Context) -> Sync?)? = null

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun scheduleNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_NAME,
                // Work already scheduled is enough: a second one would send the same.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWork>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWork>(30, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build(),
            )
        }

        /**
         * Przebieg zaraz po końcu wstrzymanej synchronizacji - żeby to, co
         * czekało w kolejce, nie czekało jeszcze na zadanie okresowe.
         */
        fun scheduleAfterPause(context: Context, delayMs: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                AFTER_PAUSE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWork>()
                    .setConstraints(constraints)
                    .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancelAfterPause(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(AFTER_PAUSE_NAME)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(ONE_OFF_NAME)
                cancelUniqueWork(PERIODIC_NAME)
                cancelUniqueWork(AFTER_PAUSE_NAME)
            }
        }
    }
}
