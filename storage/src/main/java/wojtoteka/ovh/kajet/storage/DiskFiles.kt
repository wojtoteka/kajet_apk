package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import wojtoteka.ovh.kajet.core.text.cannotCreateFile
import wojtoteka.ovh.kajet.core.text.cannotCreateFolder
import wojtoteka.ovh.kajet.core.text.cannotOpenFile
import wojtoteka.ovh.kajet.core.text.cannotReadFile
import wojtoteka.ovh.kajet.core.text.cannotSaveFile
import wojtoteka.ovh.kajet.core.text.diskRenamedFile
import wojtoteka.ovh.kajet.core.text.fileVanishedWhileCopying
import wojtoteka.ovh.kajet.core.text.words

internal object DiskFiles {

    const val MIME_JSON = "application/json"
    const val MIME_TEXT = "text/plain"
    const val MIME_PNG = "image/png"
    const val MIME_JPEG = "image/jpeg"

    fun readText(resolver: ContentResolver, file: DocumentFile): String =
        readBytes(resolver, file).toString(Charsets.UTF_8)

    fun readBytes(resolver: ContentResolver, file: DocumentFile): ByteArray {
        plainFile(file)?.let { return it.readBytes() }
        val stream = resolver.openInputStream(file.uri)
            ?: throw IOException(words.cannotOpenFile(file.name))
        stream.use { input ->
            val buffer = ByteArrayOutputStream(maxOf(file.length().toInt(), 1024))
            input.copyTo(buffer)
            return buffer.toByteArray()
        }
    }

    private fun plainFile(file: DocumentFile): File? =
        if (file.uri.scheme == "file") file.uri.path?.let(::File) else null

    fun writeBytes(resolver: ContentResolver, file: DocumentFile, data: ByteArray) {
        plainFile(file)?.let { fileOnDisk ->
            fileOnDisk.writeBytes(data)
            return
        }
        val stream = resolver.openOutputStream(file.uri, "rwt")
            ?: throw IOException(words.cannotSaveFile(file.name))
        stream.use { output ->
            output.write(data)
            output.flush()
        }
    }

    fun writeText(resolver: ContentResolver, file: DocumentFile, content: String) {
        writeBytes(resolver, file, content.toByteArray(Charsets.UTF_8))
    }

    fun fileForWrite(folder: DocumentFile, name: String, mime: String): DocumentFile {
        folder.findFile(name)?.let { if (it.isFile) return it }
        return createFile(folder, name, mime)
    }

    fun createFile(folder: DocumentFile, name: String, mime: String): DocumentFile {
        val file = folder.createFile(mime, name)
            ?: throw IOException(words.cannotCreateFile(name, folder.name))
        if (file.name != name) {
            if (!file.renameTo(name)) {
                throw IOException(
                    words.diskRenamedFile(file.name, name),
                )
            }
        }
        return file
    }

    fun folderForWrite(parent: DocumentFile, name: String): DocumentFile {
        parent.findFile(name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name)
            ?: throw IOException(words.cannotCreateFolder(name))
    }

    fun copyRecursively(
        resolver: ContentResolver,
        source: DocumentFile,
        targetFolder: DocumentFile,
        newName: String,
    ): DocumentFile {
        if (source.isDirectory) {
            val target = folderForWrite(targetFolder, newName)
            for (child in source.listFiles()) {
                val name = child.name ?: continue
                copyRecursively(resolver, child, target, name)
            }
            return target
        }
        val mime = source.type ?: "application/octet-stream"
        val target = createFile(targetFolder, newName, mime)
        try {
            resolver.openInputStream(source.uri).use { input ->
                if (input == null) throw IOException(words.cannotReadFile(source.name))
                resolver.openOutputStream(target.uri, "rwt").use { output ->
                    if (output == null) throw IOException(words.cannotSaveFile(newName))
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                }
            }
        } catch (e: FileNotFoundException) {
            throw IOException(words.fileVanishedWhileCopying(source.name), e)
        }
        return target
    }

    fun occupiedNames(folder: DocumentFile): Set<String> =
        folder.listFiles().mapNotNull { it.name }.toSet()

    fun deleteRecursively(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            for (child in file.listFiles()) {
                deleteRecursively(child)
            }
        }
        return file.delete()
    }
}
