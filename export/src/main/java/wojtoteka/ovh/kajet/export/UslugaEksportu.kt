package wojtoteka.ovh.kajet.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.storage.NazwyPlikow
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Do jakiego pliku zapisujemy notatkę. */
enum class FormatEksportu(
    val nazwaPl: String,
    val opisPl: String,
    val rozszerzenie: String,
    val mime: String,
) {
    PDF(
        nazwaPl = "PDF",
        opisPl = "Wygląda dokładnie tak jak na ekranie. Nadaje się do druku i do wysłania.",
        rozszerzenie = "pdf",
        mime = "application/pdf",
    ),
    DOCX(
        nazwaPl = "Dokument Word",
        opisPl = "Tekst do dalszej pracy. Bez zdjęć i bez pisma odręcznego.",
        rozszerzenie = "docx",
        mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ),
    MARKDOWN(
        nazwaPl = "Markdown",
        opisPl = "Czysty tekst ze znacznikami. Otworzysz go w każdym edytorze.",
        rozszerzenie = "md",
        mime = "text/markdown",
    ),
    PNG(
        nazwaPl = "Obrazek PNG",
        opisPl = "Pierwsza strona jako obrazek. Dobre do wklejenia w wiadomość.",
        rozszerzenie = "png",
        mime = "image/png",
    ),
}

/**
 * Zapis notatki do pliku, udostępnianie i drukowanie.
 *
 * Gotowy plik trafia najpierw do pamięci podręcznej aplikacji, a stamtąd
 * użytkownik decyduje, co z nim zrobić. Do biblioteki nie wkładamy go sami,
 * żeby eksport nie zaśmiecał katalogu z notatkami.
 */
class UslugaEksportu(
    private val context: Context,
    private val repo: RepozytoriumBiblioteki,
) {

    private fun katalogEksportu(): File = File(context.cacheDir, "eksport").apply { mkdirs() }

    suspend fun eksportuj(
        sciezkaNotatki: String,
        dokument: NoteDocument,
        format: FormatEksportu,
    ): File = withContext(Dispatchers.IO) {
        val nazwa = NazwyPlikow.bezpieczna(dokument.title) + "." + format.rozszerzenie
        val plik = File(katalogEksportu(), nazwa)

        FileOutputStream(plik).use { wyjscie ->
            when (format) {
                FormatEksportu.PDF -> EksportPdf.zapisz(
                    dokument = dokument,
                    wyjscie = wyjscie,
                    zalacznik = { nazwaPliku -> czytajZalacznikSynchronicznie(sciezkaNotatki, nazwaPliku) },
                )

                FormatEksportu.DOCX -> EksportDocx.zapisz(dokument, wyjscie)

                FormatEksportu.MARKDOWN -> wyjscie.write(
                    EksportMarkdown.zamien(dokument).toByteArray(Charsets.UTF_8),
                )

                FormatEksportu.PNG -> {
                    val kartka = dokument.handwriting?.pages?.firstOrNull()
                    val bitmapa = if (kartka != null) {
                        EksportPdf.stronaJakoPng(kartka)
                    } else {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }
                    bitmapa.compress(Bitmap.CompressFormat.PNG, 100, wyjscie)
                    bitmapa.recycle()
                }
            }
        }
        plik
    }

    /**
     * Eksport całego folderu do jednego pliku ZIP.
     * W środku zachowujemy układ podfolderów, więc archiwum wygląda
     * tak samo jak folder na tablecie.
     */
    suspend fun eksportujFolder(
        sciezkaFolderu: String,
        format: FormatEksportu,
        postep: ((zrobione: Int, wszystkich: Int) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val magazyn = repo.magazyn()
            ?: throw java.io.IOException("Nie wybrano katalogu na notatki.")

        val notatki = mutableListOf<String>()
        fun zejdz(sciezka: String) {
            for (wpis in magazyn.wypisz(sciezka)) {
                when (wpis.type) {
                    ItemType.FOLDER -> zejdz(wpis.path)
                    ItemType.NOTATKA -> notatki += wpis.path
                    else -> Unit
                }
            }
        }
        zejdz(sciezkaFolderu)

        val nazwaFolderu = sciezkaFolderu.substringAfterLast('/').ifEmpty { "Biblioteka" }
        val plik = File(katalogEksportu(), NazwyPlikow.bezpieczna(nazwaFolderu) + ".zip")

        ZipOutputStream(plik.outputStream()).use { zip ->
            notatki.forEachIndexed { numer, sciezka ->
                val dokument = runCatching { magazyn.czytajNotatke(sciezka) }.getOrNull()
                if (dokument != null) {
                    val wzgledna = sciezka.removePrefix(sciezkaFolderu).trimStart('/')
                    val wpis = wzgledna.removeSuffix(".note") + "." + format.rozszerzenie
                    zip.putNextEntry(ZipEntry(wpis))
                    when (format) {
                        FormatEksportu.PDF -> EksportPdf.zapisz(
                            dokument = dokument,
                            wyjscie = zip,
                            zalacznik = { nazwa -> czytajZalacznikSynchronicznie(sciezka, nazwa) },
                        )
                        FormatEksportu.DOCX -> EksportDocx.zapisz(dokument, zip)
                        FormatEksportu.MARKDOWN -> zip.write(
                            EksportMarkdown.zamien(dokument).toByteArray(Charsets.UTF_8),
                        )
                        FormatEksportu.PNG -> {
                            val kartka = dokument.handwriting?.pages?.firstOrNull()
                            if (kartka != null) {
                                val bitmapa = EksportPdf.stronaJakoPng(kartka)
                                bitmapa.compress(Bitmap.CompressFormat.PNG, 100, zip)
                                bitmapa.recycle()
                            }
                        }
                    }
                    zip.closeEntry()
                }
                postep?.invoke(numer + 1, notatki.size)
            }
        }
        plik
    }

    private fun czytajZalacznikSynchronicznie(sciezkaNotatki: String, nazwa: String): ByteArray? =
        kotlinx.coroutines.runBlocking { repo.czytajZalacznik(sciezkaNotatki, nazwa) }

    fun adresPliku(plik: File) = FileProvider.getUriForFile(context, "${context.packageName}.pliki", plik)

    /** Systemowe okno udostępniania. */
    fun udostepnij(plik: File, mime: String, tytul: String) {
        val zamiar = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, adresPliku(plik))
            putExtra(Intent.EXTRA_SUBJECT, tytul)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val wybor = Intent.createChooser(zamiar, "Wyślij: $tytul").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(wybor)
    }

    fun otworz(plik: File, mime: String) {
        val zamiar = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(adresPliku(plik), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(zamiar) }
    }

    /**
     * Drukowanie przez systemowy menedżer wydruku.
     * Podsuwamy mu ten sam plik PDF, który powstaje przy eksporcie,
     * więc wydruk wygląda dokładnie tak jak zapisany plik.
     */
    fun drukuj(dokument: NoteDocument, sciezkaNotatki: String) {
        val menedzer = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val nazwa = NazwyPlikow.bezpieczna(dokument.title)
        menedzer.print(
            nazwa,
            AdapterWydruku(nazwa) { wyjscie ->
                EksportPdf.zapisz(
                    dokument = dokument,
                    wyjscie = wyjscie,
                    zalacznik = { plik -> czytajZalacznikSynchronicznie(sciezkaNotatki, plik) },
                )
            },
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build(),
        )
    }
}

/** Most między naszym plikiem PDF a systemowym oknem wydruku. */
private class AdapterWydruku(
    private val nazwa: String,
    private val zapisz: (java.io.OutputStream) -> Unit,
) : PrintDocumentAdapter() {

    override fun onLayout(
        stare: PrintAttributes?,
        nowe: PrintAttributes?,
        anulowanie: CancellationSignal?,
        odpowiedz: LayoutResultCallback,
        dodatkowe: Bundle?,
    ) {
        if (anulowanie?.isCanceled == true) {
            odpowiedz.onLayoutCancelled()
            return
        }
        val opis = PrintDocumentInfo.Builder("$nazwa.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        odpowiedz.onLayoutFinished(opis, true)
    }

    override fun onWrite(
        strony: Array<out PageRange>?,
        cel: ParcelFileDescriptor?,
        anulowanie: CancellationSignal?,
        odpowiedz: WriteResultCallback,
    ) {
        if (cel == null) {
            odpowiedz.onWriteFailed("Nie udało się otworzyć pliku do wydruku.")
            return
        }
        try {
            java.io.FileOutputStream(cel.fileDescriptor).use { wyjscie -> zapisz(wyjscie) }
            odpowiedz.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            odpowiedz.onWriteFailed(e.message ?: "Wydruk się nie udał.")
        }
    }
}
