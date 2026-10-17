package wojtoteka.ovh.kajet.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/** Stan odbudowy spisu dla ekranu: postęp, koniec albo powód niepowodzenia. */
data class RebuildState(
    /** Które zlecenie - ekran odróżnia po tym swoją odbudowę od wcześniejszej. */
    val id: UUID,
    val running: Boolean,
    val done: Int,
    val total: Int,
    val finished: Boolean,
    val failure: String?,
)

/**
 * Odbudowa spisu notatek jako zadanie systemu, a nie ekranu.
 *
 * Dawniej szła w zakresie ekranu biblioteki: wyjście z aplikacji przerywało
 * ją w pół, a przy dużej bibliotece trwała minuty i mało kto chciał na nią
 * patrzeć. Zostawał spis z połową notatek. Teraz system prowadzi ją dalej,
 * także po zamknięciu aplikacji, a ekran tylko pokazuje postęp.
 */
class IndexRebuildWork(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = repositoryProvider?.invoke(applicationContext) ?: return Result.failure()
        return try {
            coroutineScope {
                // Postęp idzie do systemu co chwilę, a nie przy każdej notatce.
                val latest = MutableStateFlow(0 to 0)
                val reporter = launch {
                    latest.collect { (done, total) ->
                        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                        delay(PROGRESS_EVERY_MS)
                    }
                }
                try {
                    repository.rebuildIndex { done, total -> latest.value = done to total }
                } finally {
                    reporter.cancel()
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_FAILURE to e.message.orEmpty()))
        }
    }

    companion object {
        private const val NAME = "kajet-odbudowa-spisu"
        private const val KEY_DONE = "zrobione"
        private const val KEY_TOTAL = "wszystkie"
        private const val KEY_FAILURE = "blad"
        private const val PROGRESS_EVERY_MS = 300L

        /** Biblioteka, którą zadanie odbudowuje - podpina ją aplikacja przy starcie. */
        @Volatile
        var repositoryProvider: ((Context) -> LibraryRepository?)? = null

        /**
         * Zleca odbudowę. [restart] przerywa trwającą i zaczyna od nowa (nowy
         * folder, przycisk w ustawieniach); bez niego trwająca wystarcza.
         */
        fun start(context: Context, restart: Boolean): UUID {
            val request = OneTimeWorkRequestBuilder<IndexRebuildWork>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
            return request.id
        }

        fun state(context: Context): Flow<RebuildState?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NAME).map { infos ->
                val info = infos.lastOrNull() ?: return@map null
                RebuildState(
                    id = info.id,
                    running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                    done = info.progress.getInt(KEY_DONE, 0),
                    total = info.progress.getInt(KEY_TOTAL, 0),
                    finished = info.state.isFinished,
                    failure = if (info.state == WorkInfo.State.FAILED) {
                        info.outputData.getString(KEY_FAILURE).orEmpty()
                    } else {
                        null
                    },
                )
            }
    }
}
