package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import wojtoteka.ovh.kajet.storage.FileUploadStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.index.FileUploadEntry
import java.io.File

data class UploadBatchResult(
    val synced: Int = 0,
    val reason: String? = null,
    val worthRetrying: Boolean = false,
)

/** Extends the existing sync worker with durable file imports. */
class FileUploader(
    context: Context,
    private val store: FileUploadStore,
    private val repository: LibraryRepository,
    private val client: CloudClient,
) {
    private val app = context.applicationContext
    val uploads: Flow<List<FileUploadEntry>> = store.observeAll()

    /**
     * Przed każdą turą wysyłania: skończone wgrywania sprzed [FINISHED_KEPT_MILLIS]
     * znikają z kolejki, a przerwane wracają do wysłania. Bez pierwszego kroku
     * wpis wgranego pliku zostawał w bazie na zawsze i po każdym uruchomieniu
     * aplikacji wracał z nim pasek „wgrany pomyślnie".
     */
    suspend fun recoverInterrupted(): Int {
        store.purgeFinished(System.currentTimeMillis() - FINISHED_KEPT_MILLIS)
        return store.recoverInterrupted()
    }

    suspend fun enqueue(
        uri: Uri,
        originalName: String,
        mimeType: String,
        sizeBytes: Long?,
        folderPath: String,
    ): FileUploadEntry {
        val folderId = if (folderPath.isBlank()) {
            null
        } else {
            repository.allCloudFolders().firstOrNull { it.path == folderPath }?.id
        }
        val entry = store.enqueue(uri, originalName, mimeType, sizeBytes, folderPath, folderId)
        SyncWork.scheduleNow(app)
        return entry
    }

    suspend fun hide(id: String) = store.hide(id)

    suspend fun retry(id: String) {
        store.retry(id)
        SyncWork.scheduleNow(app)
    }

    suspend fun processPending(): UploadBatchResult {
        recoverInterrupted()
        var synced = 0
        var firstError: String? = null
        var retry = false

        for (entry in store.ready()) {
            val file = File(entry.localPath)
            if (!file.isFile) {
                val message = "Brakuje prywatnej kopii pliku oczekującego na upload."
                store.markFailed(entry.id, retryable = false, message, "local-file-missing")
                firstError = firstError ?: message
                continue
            }

            store.markUploading(entry.id)
            val response = client.uploadFile(
                uploadId = entry.id,
                file = file,
                originalName = entry.originalName,
                mimeType = entry.mimeType,
                folderId = entry.folderId,
                onProgress = { percent ->
                    // At most twenty small Room updates per file. The upload
                    // already runs on IO, so this never blocks the UI thread.
                    if (percent % 5 == 0) runBlocking { store.progress(entry.id, percent) }
                },
            )
            when (response) {
                is CloudClient.Result.Ok -> {
                    store.markSynced(entry, response.data.id)
                    synced += 1
                    Log.i("Kajet", "Upload ${entry.id}: HTTP success (${response.data.status})")
                }
                is CloudClient.Result.Error -> {
                    val retryable = uploadFailureIsRetryable(response)
                    store.markFailed(
                        entry.id,
                        retryable = retryable,
                        message = response.message,
                        code = response.code.ifBlank { response.httpStatus?.toString() },
                    )
                    firstError = firstError ?: response.message
                    retry = retry || retryable
                    Log.i("Kajet", "Upload ${entry.id}: HTTP ${response.httpStatus ?: 0}")
                    if (response.mustSignIn) break
                }
            }
        }
        return UploadBatchResult(synced, firstError, retry)
    }

    private companion object {
        /** Pół godziny na przeczytanie paska o skończonym wgrywaniu. */
        const val FINISHED_KEPT_MILLIS = 30 * 60 * 1000L
    }
}

internal fun uploadFailureIsRetryable(failure: CloudClient.Result.Error): Boolean =
    failure.worthRetrying && !failure.mustSignIn
