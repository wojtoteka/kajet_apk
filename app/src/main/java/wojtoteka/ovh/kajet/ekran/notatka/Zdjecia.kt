package wojtoteka.ovh.kajet.ekran.notatka

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Pomoc przy zdjęciach. Aparat zapisuje plik do pamięci podręcznej aplikacji,
 * a my przepisujemy go do katalogu notatki i kasujemy oryginał.
 */
object Zdjecia {

    fun plikNaZdjecie(context: Context): Pair<File, Uri> {
        val katalog = File(context.cacheDir, "aparat").apply { mkdirs() }
        val plik = File(katalog, "zdjecie-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.pliki", plik)
        return plik to uri
    }

    fun wczytaj(context: Context, uri: Uri): ByteArray? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

    fun rozszerzenie(context: Context, uri: Uri): String {
        val typ = context.contentResolver.getType(uri).orEmpty()
        return when {
            typ.contains("png") -> "png"
            typ.contains("webp") -> "webp"
            else -> "jpg"
        }
    }
}
