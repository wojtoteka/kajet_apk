package wojtoteka.ovh.kajet.cloud

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.NoteCodec
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Ostatnia treść notatki, jaką potwierdził serwer - baza scalania.
 *
 * Bez niej konflikt po pracy bez sieci da się rozstrzygnąć tylko kopią obok
 * („wersja z serwera"), bo nie wiadomo, co zmieniła która strona. Z nią -
 * tak jak w edycji na żywo - obie zmiany składają się w jedną notatkę.
 *
 * Leży w pamięci aplikacji, spakowana (notatka odręczna kurczy się kilka
 * razy), po jednym pliku na notatkę: `<wersja>\n` + JSON.
 */
class SyncBase(private val root: File) {

    private fun file(noteId: String): File =
        File(root, noteId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz")

    @Synchronized
    fun read(noteId: String): Pair<Int, JsonObject>? = runCatching {
        val text = GZIPInputStream(file(noteId).inputStream()).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val newline = text.indexOf('\n')
        val version = text.substring(0, newline).toInt()
        version to NoteCodec.json.parseToJsonElement(text.substring(newline + 1)).jsonObject
    }.getOrNull()

    @Synchronized
    fun write(noteId: String, version: Int, content: JsonObject) {
        runCatching {
            root.mkdirs()
            val target = file(noteId)
            val temporary = File(target.path + ".tmp")
            GZIPOutputStream(temporary.outputStream()).bufferedWriter(Charsets.UTF_8).use {
                it.write(version.toString())
                it.write("\n")
                it.write(content.toString())
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        }
    }

    @Synchronized
    fun forget(noteId: String) {
        file(noteId).delete()
    }

    @Synchronized
    fun forgetAll() {
        root.deleteRecursively()
    }

    companion object {
        /** Treść w postaci, w jakiej zapisuje ją aplikacja - bez pól, których nie zna. */
        fun normalize(document: NoteDocument): JsonObject =
            NoteCodec.json.encodeToJsonElement(document).jsonObject

        fun normalize(content: String): JsonObject? =
            runCatching { normalize(NoteCodec.decodeNote(content)) }.getOrNull()

        fun normalize(content: JsonObject): JsonObject =
            runCatching { normalize(NoteCodec.json.decodeFromJsonElement(NoteDocument.serializer(), content)) }
                .getOrDefault(content)
    }
}
