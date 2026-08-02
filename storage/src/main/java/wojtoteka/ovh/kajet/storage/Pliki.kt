package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Odczyt i zapis pojedynczych plików przez Storage Access Framework.
 *
 * Wszystko idzie przez [DocumentFile], więc ten sam kod działa na katalogu
 * wskazanym przez użytkownika i na zwykłym katalogu na dysku w testach.
 */
internal object Pliki {

    const val MIME_JSON = "application/json"
    const val MIME_TEKST = "text/plain"
    const val MIME_PNG = "image/png"
    const val MIME_JPEG = "image/jpeg"

    fun czytajTekst(resolver: ContentResolver, plik: DocumentFile): String =
        czytajBajty(resolver, plik).toString(Charsets.UTF_8)

    fun czytajBajty(resolver: ContentResolver, plik: DocumentFile): ByteArray {
        zwyklyPlik(plik)?.let { return it.readBytes() }
        val strumien = resolver.openInputStream(plik.uri)
            ?: throw IOException("Nie można otworzyć pliku ${plik.name}.")
        strumien.use { wejscie ->
            val bufor = ByteArrayOutputStream(maxOf(plik.length().toInt(), 1024))
            wejscie.copyTo(bufor)
            return bufor.toByteArray()
        }
    }

    /**
     * Kiedy adres wskazuje zwykły plik na dysku, czytamy go wprost.
     * Tak jest przy testach i wtedy, gdy użytkownik wskaże katalog aplikacji.
     */
    private fun zwyklyPlik(plik: DocumentFile): File? =
        if (plik.uri.scheme == "file") plik.uri.path?.let(::File) else null

    /**
     * Zapisuje całą zawartość pliku od nowa.
     *
     * Tryb "rwt" ucina poprzednią treść. Bez niego dłuższy plik zostawiłby
     * ogon po starszej wersji i zepsuł plik JSON.
     */
    fun zapiszBajty(resolver: ContentResolver, plik: DocumentFile, dane: ByteArray) {
        zwyklyPlik(plik)?.let { plikNaDysku ->
            plikNaDysku.writeBytes(dane)
            return
        }
        val strumien = resolver.openOutputStream(plik.uri, "rwt")
            ?: throw IOException("Nie można zapisać pliku ${plik.name}.")
        strumien.use { wyjscie ->
            wyjscie.write(dane)
            wyjscie.flush()
        }
    }

    fun zapiszTekst(resolver: ContentResolver, plik: DocumentFile, tresc: String) {
        zapiszBajty(resolver, plik, tresc.toByteArray(Charsets.UTF_8))
    }

    /** Znajduje plik o danej nazwie albo tworzy nowy. */
    fun plikDoZapisu(katalog: DocumentFile, nazwa: String, mime: String): DocumentFile {
        katalog.findFile(nazwa)?.let { if (it.isFile) return it }
        return utworzPlik(katalog, nazwa, mime)
    }

    /**
     * Tworzy plik o dokładnie takiej nazwie, o jaką prosimy.
     *
     * Dostawcy plików lubią doklejać rozszerzenie wynikające z typu MIME,
     * na przykład z content.json robi się content.json.json. Sprawdzamy nazwę
     * po utworzeniu i poprawiamy ją, żeby format notatki był zawsze taki sam.
     */
    fun utworzPlik(katalog: DocumentFile, nazwa: String, mime: String): DocumentFile {
        val plik = katalog.createFile(mime, nazwa)
            ?: throw IOException("Nie można utworzyć pliku $nazwa w katalogu ${katalog.name}.")
        if (plik.name != nazwa) {
            if (!plik.renameTo(nazwa)) {
                throw IOException(
                    "Dysk zapisał plik pod nazwą ${plik.name} zamiast $nazwa. Wybierz inny katalog na notatki.",
                )
            }
        }
        return plik
    }

    fun katalogDoZapisu(rodzic: DocumentFile, nazwa: String): DocumentFile {
        rodzic.findFile(nazwa)?.let { if (it.isDirectory) return it }
        return rodzic.createDirectory(nazwa)
            ?: throw IOException("Nie można utworzyć katalogu $nazwa.")
    }

    /**
     * Kopiuje plik albo cały katalog razem z zawartością.
     * Storage Access Framework nie ma gotowego kopiowania drzewa.
     */
    fun kopiujRekurencyjnie(
        resolver: ContentResolver,
        zrodlo: DocumentFile,
        katalogDocelowy: DocumentFile,
        nowaNazwa: String,
    ): DocumentFile {
        if (zrodlo.isDirectory) {
            val cel = katalogDoZapisu(katalogDocelowy, nowaNazwa)
            for (dziecko in zrodlo.listFiles()) {
                val nazwa = dziecko.name ?: continue
                kopiujRekurencyjnie(resolver, dziecko, cel, nazwa)
            }
            return cel
        }
        val mime = zrodlo.type ?: "application/octet-stream"
        val cel = utworzPlik(katalogDocelowy, nowaNazwa, mime)
        try {
            resolver.openInputStream(zrodlo.uri).use { wejscie ->
                if (wejscie == null) throw IOException("Nie można odczytać pliku ${zrodlo.name}.")
                resolver.openOutputStream(cel.uri, "rwt").use { wyjscie ->
                    if (wyjscie == null) throw IOException("Nie można zapisać pliku $nowaNazwa.")
                    wejscie.copyTo(wyjscie, DEFAULT_BUFFER_SIZE)
                    wyjscie.flush()
                }
            }
        } catch (e: FileNotFoundException) {
            throw IOException("Plik ${zrodlo.name} zniknął w trakcie kopiowania.", e)
        }
        return cel
    }

    /** Nazwy wszystkich wpisów w katalogu. Potrzebne, żeby nie nadpisać istniejącego. */
    fun zajeteNazwy(katalog: DocumentFile): Set<String> =
        katalog.listFiles().mapNotNull { it.name }.toSet()

    /** Usuwa katalog razem z zawartością. DocumentFile.delete robi to samo, ale nie na każdym dostawcy. */
    fun usunRekurencyjnie(plik: DocumentFile): Boolean {
        if (plik.isDirectory) {
            for (dziecko in plik.listFiles()) {
                usunRekurencyjnie(dziecko)
            }
        }
        return plik.delete()
    }
}
