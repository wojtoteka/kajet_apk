package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SendQueue(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun add(path: String, noteId: String) {
        val current = read().toMutableMap()
        val previous = current[path]
        current[path] = QueueEntry(
            path = path,
            noteId = noteId,
            // We keep the number of failed attempts so as not to loop forever
            // on a note the server will not accept anyway.
            failedAttempts = previous?.failedAttempts ?: 0,
            addedAt = previous?.addedAt ?: System.currentTimeMillis(),
        )
        write(current)
    }

    @Synchronized
    fun remove(path: String) {
        val current = read().toMutableMap()
        current.remove(path)
        write(current)
    }

    @Synchronized
    fun recordFailure(path: String): Boolean {
        val current = read().toMutableMap()
        val entry = current[path] ?: return false
        val after = entry.copy(failedAttempts = entry.failedAttempts + 1)

        return if (after.failedAttempts >= MAX_ATTEMPTS) {
            // The note stays on the tablet, we just stop pounding at the server
            // with it. The entry leaves the queue so it does not block the rest.
            current.remove(path)
            write(current)
            false
        } else {
            current[path] = after
            write(current)
            true
        }
    }

    @Synchronized
    fun all(): List<QueueEntry> = read().values.sortedBy { it.addedAt }

    @Synchronized
    fun size(): Int = read().size

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private fun read(): Map<String, QueueEntry> {
        val text = preferences.getString(KEY, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<List<QueueEntry>>(text) }
            .getOrDefault(emptyList())
            .associateBy { it.path }
    }

    private fun write(entries: Map<String, QueueEntry>) {
        preferences.edit().putString(KEY, json.encodeToString(entries.values.toList())).apply()
    }

    private companion object {
        const val FILE_NAME = "kajet-queue"
        const val KEY = "to_send"

        const val MAX_ATTEMPTS = 5

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class QueueEntry(
    val path: String,
    val noteId: String,
    val failedAttempts: Int = 0,
    val addedAt: Long = 0,
)
