package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.FolderMeta
import wojtoteka.ovh.kajet.core.model.NoteDocument

/** Zawartość pliku jest uszkodzona albo pochodzi z nowszej wersji Kajetu. */
class BladFormatuException(
    val komunikatDlaUzytkownika: String,
    przyczyna: Throwable? = null,
) : Exception(komunikatDlaUzytkownika, przyczyna)

/**
 * Zamiana notatki na tekst pliku content.json i z powrotem.
 *
 * Ta klasa nie dotyka dysku. Dzięki temu da się ją przetestować bez urządzenia,
 * a to jest miejsce, w którym błąd oznacza utratę notatki.
 */
object NoteCodec {

    val json = Json {
        // Plik zapisany przez nowszą wersję nie może wywalić starszej aplikacji.
        ignoreUnknownKeys = true
        // Wartości domyślne zapisujemy, żeby plik dało się przeczytać bez znajomości kodu.
        encodeDefaults = true
        // Pola puste pomijamy, bo w notatce odręcznej większość gałęzi jest pusta.
        explicitNulls = false
        prettyPrint = false
        allowSpecialFloatingPointValues = false
    }

    fun zapiszNotatke(dokument: NoteDocument): String = json.encodeToString(dokument)

    fun czytajNotatke(tresc: String): NoteDocument {
        if (tresc.isBlank()) {
            throw BladFormatuException("Plik notatki jest pusty. Otwórz kopię z kosza albo utwórz notatkę na nowo.")
        }
        val dokument = try {
            json.decodeFromString<NoteDocument>(tresc)
        } catch (e: SerializationException) {
            throw BladFormatuException(
                "Nie da się odczytać pliku content.json. Plik jest uszkodzony albo nie należy do Kajetu.",
                e,
            )
        }
        if (dokument.format > NoteDocument.FORMAT_BIEZACY) {
            throw BladFormatuException(
                "Ta notatka pochodzi z nowszej wersji Kajetu. Zaktualizuj aplikację, żeby ją otworzyć.",
            )
        }
        return dokument
    }

    fun zapiszRysunek(rysunek: DrawingSource): String = json.encodeToString(rysunek)

    fun czytajRysunek(tresc: String): DrawingSource = try {
        json.decodeFromString<DrawingSource>(tresc)
    } catch (e: SerializationException) {
        throw BladFormatuException("Nie da się odczytać rysunku wstawionego w tekst.", e)
    }

    fun zapiszFolder(meta: FolderMeta): String = json.encodeToString(meta)

    fun czytajFolder(tresc: String): FolderMeta = try {
        json.decodeFromString<FolderMeta>(tresc)
    } catch (e: SerializationException) {
        throw BladFormatuException("Nie da się odczytać opisu folderu.", e)
    }
}
