package wojtoteka.ovh.kajet.ui.note

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Photos {

    fun fileForPhoto(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "aparat").apply { mkdirs() }
        val file = File(dir, "zdjecie-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.pliki", file)
        return file to uri
    }

    fun read(context: Context, uri: Uri): ByteArray? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

    fun extension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
    }
}
