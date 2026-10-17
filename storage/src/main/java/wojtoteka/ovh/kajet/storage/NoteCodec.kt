package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.FolderMeta
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.text.words

class FormatException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

object NoteCodec {

    val json = Json {
        // A file written by a newer version must not crash an older app.
        ignoreUnknownKeys = true
        // Defaults are written out so the file can be read without knowing the code.
        encodeDefaults = true
        // Empty fields are skipped, because in a handwritten note most branches are empty.
        explicitNulls = false
        prettyPrint = false
        allowSpecialFloatingPointValues = false
    }

    fun encodeNote(document: NoteDocument): String = json.encodeToString(document)

    fun decodeNote(content: String): NoteDocument {
        if (content.isBlank()) {
            throw FormatException(words.emptyNoteFile)
        }
        val document = try {
            json.decodeFromString<NoteDocument>(content)
        } catch (e: SerializationException) {
            throw FormatException(
                words.brokenContentFile,
                e,
            )
        }
        if (document.format > NoteDocument.FORMAT_CURRENT) {
            throw FormatException(
                words.newerKajet,
            )
        }
        return document
    }

    /*
      Sam początek notatki: wersja formatu i identyfikator. Reszta pliku jest
      tylko przeskakiwana, bez budowania kresek, stron i akapitów - kosz pyta
      o identyfikatory przy każdej synchronizacji i nic więcej z treści nie
      potrzebuje.
    */
    @Serializable
    private class NoteHeader(
        val format: Int = NoteDocument.FORMAT_CURRENT,
        val id: String,
    )

    /** Identyfikator notatki z content.json - te same błędy co [decodeNote]. */
    fun decodeNoteId(content: String): String {
        if (content.isBlank()) {
            throw FormatException(words.emptyNoteFile)
        }
        val header = try {
            json.decodeFromString<NoteHeader>(content)
        } catch (e: SerializationException) {
            throw FormatException(words.brokenContentFile, e)
        }
        if (header.format > NoteDocument.FORMAT_CURRENT) {
            throw FormatException(words.newerKajet)
        }
        return header.id
    }

    fun encodeDrawing(drawing: DrawingSource): String = json.encodeToString(drawing)

    fun decodeDrawing(content: String): DrawingSource = try {
        json.decodeFromString<DrawingSource>(content)
    } catch (e: SerializationException) {
        throw FormatException(words.brokenInlineDrawing, e)
    }

    fun encodeFolder(meta: FolderMeta): String = json.encodeToString(meta)

    fun decodeFolder(content: String): FolderMeta = try {
        json.decodeFromString<FolderMeta>(content)
    } catch (e: SerializationException) {
        throw FormatException(words.brokenFolderDescription, e)
    }
}
