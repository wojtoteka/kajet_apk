package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.text.uploadTooLargeAbout
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.storage.index.FileUploadDao
import wojtoteka.ovh.kajet.storage.index.FileUploadEntry
import java.io.File
import java.io.IOException
import java.util.UUID

enum class FileUploadStatus {
    PENDING,
    UPLOADING,
    SYNCED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
}

/**
 * Trwała kolejka uploadów i prywatne kopie wybranych plików.
 *
 * URI z pickera zostaje tylko informacją diagnostyczną. Worker czyta kopię z
 * [Context.getFilesDir], więc działa po śmierci procesu, restarcie i utracie
 * uprawnienia dostawcy dokumentów.
 */
class FileUploadStore(
    context: Context,
    private val dao: FileUploadDao,
) {
    private val app = context.applicationContext
    private val directory = File(app.filesDir, DIRECTORY)

    fun observeAll(): Flow<List<FileUploadEntry>> = dao.observeAll()

    suspend fun enqueue(
        uri: Uri,
        originalName: String,
        mimeType: String,
        reportedSize: Long?,
        folderPath: String,
        folderId: String?,
    ): FileUploadEntry = withContext(Dispatchers.IO) {
        if (reportedSize != null && reportedSize > MAX_FILE_BYTES) {
            throw FileTooLargeException(reportedSize, MAX_FILE_BYTES)
        }

        val id = UUID.randomUUID().toString()
        directory.mkdirs()
        val target = File(directory, id)
        var copied = 0L
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Nie udało się otworzyć wybranego pliku.")
            input.use { source ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_FILE_BYTES) {
                            throw FileTooLargeException(copied, MAX_FILE_BYTES)
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val now = System.currentTimeMillis()
            val entry = FileUploadEntry(
                id = id,
                sourceUri = uri.toString(),
                localPath = target.absolutePath,
                originalName = originalName,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                sizeBytes = copied,
                folderPath = folderPath,
                folderId = folderId,
                status = FileUploadStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            )
            dao.insert(entry)
            Log.i(TAG, "Upload $id: file selected, copied locally, PENDING")
            entry
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    suspend fun recoverInterrupted(): Int = withContext(Dispatchers.IO) {
        val recovered = dao.recoverInterrupted(System.currentTimeMillis())
        if (recovered > 0) Log.i(TAG, "Upload: recovered $recovered interrupted item(s)")
        recovered
    }

    suspend fun ready(): List<FileUploadEntry> = withContext(Dispatchers.IO) { dao.ready() }

    suspend fun markUploading(id: String) = withContext(Dispatchers.IO) {
        dao.markUploading(id, System.currentTimeMillis())
        Log.i(TAG, "Upload $id: upload started")
    }

    suspend fun progress(id: String, percent: Int) = withContext(Dispatchers.IO) {
        dao.setProgress(id, percent.coerceIn(0, 99), System.currentTimeMillis())
    }

    suspend fun markSynced(entry: FileUploadEntry, remoteId: String) = withContext(Dispatchers.IO) {
        dao.markSynced(entry.id, remoteId, System.currentTimeMillis())
        File(entry.localPath).delete()
        Log.i(TAG, "Upload ${entry.id}: SYNCED, remoteFileId=$remoteId")
    }

    suspend fun markFailed(
        id: String,
        retryable: Boolean,
        message: String,
        code: String?,
    ) = withContext(Dispatchers.IO) {
        val status = if (retryable) {
            FileUploadStatus.FAILED_RETRYABLE
        } else {
            FileUploadStatus.FAILED_PERMANENT
        }
        dao.markFailed(id, status.name, message.take(500), code?.take(80), System.currentTimeMillis())
        Log.i(TAG, "Upload $id: ${status.name}, code=${code.orEmpty()}")
    }

    suspend fun retry(id: String) = withContext(Dispatchers.IO) {
        dao.retry(id, System.currentTimeMillis())
    }

    /**
     * Zamknięcie paska krzyżykiem, na trwałe.
     *
     * Wgrany plik i trwale odrzucony nie mają już nic do zrobienia, więc wpis
     * i prywatna kopia znikają z dysku. Reszta zostaje w kolejce z podniesioną
     * flagą: robota leci dalej w tle, tylko napis o niej się nie pokazuje.
     */
    suspend fun hide(id: String) = withContext(Dispatchers.IO) {
        val entry = dao.find(id) ?: return@withContext
        val finished = entry.status == FileUploadStatus.SYNCED.name ||
            entry.status == FileUploadStatus.FAILED_PERMANENT.name
        if (finished) {
            File(entry.localPath).delete()
            dao.delete(id)
        } else {
            dao.hide(id, System.currentTimeMillis())
        }
        Log.i(TAG, "Upload $id: notice closed, entry ${if (finished) "removed" else "kept"}")
    }

    /**
     * Sprzątanie skończonych wgrywań.
     *
     * Wpis wgranego i trwale odrzuconego pliku czekał dotąd na krzyżyk, więc
     * pasek z komunikatem wracał po każdym uruchomieniu aplikacji, choćby
     * i miesiąc później. Po [before] wpis znika razem z prywatną kopią pliku:
     * nie ma już czego wysłać ani o czym pisać.
     */
    suspend fun purgeFinished(before: Long): Int = withContext(Dispatchers.IO) {
        val finished = dao.finishedBefore(before)
        finished.forEach { entry ->
            File(entry.localPath).delete()
            dao.delete(entry.id)
        }
        if (finished.isNotEmpty()) Log.i(TAG, "Upload: cleared ${finished.size} finished item(s)")
        finished.size
    }

    companion object {
        const val MAX_FILE_BYTES = 26_214_400L
        private const val DIRECTORY = "pending_uploads"
        private const val TAG = "Kajet"
    }
}

class FileTooLargeException(val actualBytes: Long, val maximumBytes: Long) :
    IOException(words.uploadTooLargeAbout(actualBytes, maximumBytes))
