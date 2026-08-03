package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.FolderMeta
import wojtoteka.ovh.kajet.core.model.NoteDocument

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
            throw FormatException("Plik notatki jest pusty. Otwórz kopię z kosza albo utwórz notatkę na nowo.")
        }
        val document = try {
            json.decodeFromString<NoteDocument>(content)
        } catch (e: SerializationException) {
            throw FormatException(
                "Nie da się odczytać pliku content.json. Plik jest uszkodzony albo nie należy do Kajetu.",
                e,
            )
        }
        if (document.format > NoteDocument.FORMAT_CURRENT) {
            throw FormatException(
                "Ta notatka pochodzi z nowszej wersji Kajetu. Zaktualizuj aplikację, żeby ją otworzyć.",
            )
        }
        return document
    }

    fun encodeDrawing(drawing: DrawingSource): String = json.encodeToString(drawing)

    fun decodeDrawing(content: String): DrawingSource = try {
        json.decodeFromString<DrawingSource>(content)
    } catch (e: SerializationException) {
        throw FormatException("Nie da się odczytać rysunku wstawionego w tekst.", e)
    }

    fun encodeFolder(meta: FolderMeta): String = json.encodeToString(meta)

    fun decodeFolder(content: String): FolderMeta = try {
        json.decodeFromString<FolderMeta>(content)
    } catch (e: SerializationException) {
        throw FormatException("Nie da się odczytać opisu folderu.", e)
    }
}
