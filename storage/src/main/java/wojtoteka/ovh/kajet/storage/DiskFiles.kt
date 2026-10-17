package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import wojtoteka.ovh.kajet.core.text.cannotCreateFile
import wojtoteka.ovh.kajet.core.text.cannotCreateFolder
import wojtoteka.ovh.kajet.core.text.cannotOpenFile
import wojtoteka.ovh.kajet.core.text.cannotReadFile
import wojtoteka.ovh.kajet.core.text.cannotSaveFile
import wojtoteka.ovh.kajet.core.text.diskRenamedFile
import wojtoteka.ovh.kajet.core.text.fileVanishedWhileCopying
import wojtoteka.ovh.kajet.core.text.words

/**
 * Wpis katalogu ze wszystkim, co o nim wiadomo z jednego zapytania.
 *
 * DocumentFile pyta magazyn o każdą cechę osobno: nazwa, rodzaj, data i
 * rozmiar to cztery wywołania innego procesu NA KAŻDY plik, a `findFile`
 * pyta o nazwę każdego pliku w folderze po kolei. Przy kilkuset notatkach
 * otwarcie jednej albo wejście do folderu robiło tysiące takich wywołań i
 * interfejs stawał. Tutaj cały folder przychodzi w jednym kursorze.
 */
internal class DiskEntry(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val lastModified: Long,
    val size: Long,
) {
    val isDirectory: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR

    // Tak samo jak w DocumentFile: plik to wszystko, co nie jest katalogiem
    // i ma jakikolwiek typ.
    val isFile: Boolean get() = !isDirectory && !mime.isNullOrEmpty()
}

/** Wpis znaleziony po nazwie: obiekt do zmian na dysku i jego opis. */
internal class Located(
    val file: DocumentFile,
    val entry: DiskEntry,
)

internal object DiskFiles {

    const val MIME_JSON = "application/json"
    const val MIME_TEXT = "text/plain"
    const val MIME_PNG = "image/png"
    const val MIME_JPEG = "image/jpeg"

    private const val MIME_BINARY = "application/octet-stream"

    private const val MAX_SIZE_HINT = 64L * 1024 * 1024

    private val COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    // Spis katalogu

    /**
     * Zawartość katalogu jednym zapytaniem.
     *
     * null znaczy „magazyn nie odpowiedział" - i to nie jest to samo co pusty
     * katalog. DocumentFile.listFiles() połyka błąd i oddaje pustą tablicę,
     * przez co nie da się odróżnić folderu bez notatek od chwilowej awarii.
     * Kto na tej podstawie coś kasuje albo przenosi, musi znać różnicę.
     */
    fun children(resolver: ContentResolver, folder: Uri): List<DiskEntry>? {
        if (folder.scheme == ContentResolver.SCHEME_FILE) return plainChildren(folder)
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getDocumentId(folder))
        } catch (e: IllegalArgumentException) {
            Log.w("Kajet", "Adres katalogu nie wskazuje drzewa: $folder", e)
            return null
        }
        return try {
            resolver.query(childrenUri, COLUMNS, null, null, null)?.use { cursor ->
                // Magazyn w chmurze potrafi oddać pierwszą porcję i doczytywać
                // resztę w tle. Taki spis jest niepełny, więc go nie bierzemy.
                if (cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true) {
                    return null
                }
                val found = ArrayList<DiskEntry>(cursor.count.coerceAtLeast(0))
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    found += DiskEntry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(folder, id),
                        name = name,
                        mime = cursor.getString(2),
                        lastModified = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        size = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                    )
                }
                found
            }
        } catch (e: Exception) {
            Log.w("Kajet", "Magazyn nie oddał spisu katalogu", e)
            null
        }
    }

    /** Spis katalogu, który jest zwykłym plikiem na dysku (testy, kopie w pamięci aplikacji). */
    private fun plainChildren(folder: Uri): List<DiskEntry>? {
        val directory = folder.path?.let(::File) ?: return null
        val files = directory.listFiles() ?: return null
        return files.map(::plainEntry)
    }

    private fun plainEntry(file: File): DiskEntry = DiskEntry(
        uri = Uri.fromFile(file),
        name = file.name,
        mime = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else plainMime(file.name),
        lastModified = file.lastModified(),
        size = if (file.isDirectory) 0L else file.length(),
    )

    // Ten sam typ, który podaje DocumentFile.fromFile - inaczej plik
    // skopiowany z katalogu testów dostawałby inny typ niż w aplikacji.
    private fun plainMime(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot >= 0) {
            val extension = name.substring(dot + 1).lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return MIME_BINARY
    }

    /** Opis jednego wpisu - jedno zapytanie zamiast osobnego na każdą cechę. */
    fun describe(resolver: ContentResolver, file: DocumentFile): DiskEntry? {
        if (file.uri.scheme == ContentResolver.SCHEME_FILE) {
            return file.uri.path?.let(::File)?.takeIf { it.exists() }?.let(::plainEntry)
        }
        return try {
            resolver.query(file.uri, COLUMNS, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                DiskEntry(
                    uri = file.uri,
                    name = cursor.getString(1) ?: return null,
                    mime = cursor.getString(2),
                    lastModified = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    size = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Wpis o tej nazwie w [folder].
     *
     * Zamiast `findFile`, które pyta o nazwę każdego pliku w folderze osobno:
     * jedno zapytanie o spis, a gdy wpis jest - drugie o obiekty DocumentFile,
     * bo tylko z nich da się potem coś na dysku założyć albo zmienić. Kiedy
     * szybki spis nie przyjdzie, zostaje stara droga.
     */
    fun locate(resolver: ContentResolver, folder: DocumentFile, name: String): Located? {
        if (folder.uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = folder.findFile(name) ?: return null
            val entry = file.uri.path?.let(::File)?.let(::plainEntry) ?: return null
            return Located(file, entry)
        }
        val listed = children(resolver, folder.uri)
            ?: return folder.findFile(name)?.let { slow -> describe(resolver, slow)?.let { Located(slow, it) } }
        val wanted = listed.firstOrNull { it.name == name } ?: return null
        val file = folder.listFiles().firstOrNull { it.uri == wanted.uri } ?: return null
        return Located(file, wanted)
    }

    /**
     * Cała zawartość [folder] razem z obiektami do zmian - dwa zapytania na
     * folder, niezależnie od tego, ile w nim leży. null jak w [children].
     */
    fun locatedChildren(resolver: ContentResolver, folder: DocumentFile): List<Located>? {
        val listed = children(resolver, folder.uri) ?: return null
        if (listed.isEmpty()) return emptyList()
        val files = folder.listFiles().associateBy { it.uri }
        return listed.mapNotNull { entry -> files[entry.uri]?.let { Located(it, entry) } }
    }

    // Odczyt

    fun readText(resolver: ContentResolver, file: DocumentFile): String =
        readBytes(resolver, file).toString(Charsets.UTF_8)

    fun readText(resolver: ContentResolver, entry: DiskEntry): String =
        readBytes(resolver, entry).toString(Charsets.UTF_8)

    /**
     * Czyta najwyżej [maxBytes]. null oznacza, że strumień miał choć jeden
     * bajt więcej. Nie polegamy wyłącznie na DocumentFile.length(), bo część
     * dostawców SAF zwraca tam zero nawet dla dużych plików.
     */
    fun readBytesUpTo(
        resolver: ContentResolver,
        file: DocumentFile,
        maxBytes: Int,
    ): ByteArray? = readBytesUpTo(resolver, file.uri, file.length(), maxBytes, file.name)

    fun readBytesUpTo(
        resolver: ContentResolver,
        uri: Uri,
        declaredSize: Long,
        maxBytes: Int,
        name: String?,
    ): ByteArray? {
        require(maxBytes >= 0)
        val stream = plainFile(uri)?.inputStream() ?: resolver.openInputStream(uri)
            ?: throw IOException(words.cannotOpenFile(name))
        stream.use { input ->
            val declared = declaredSize.coerceAtLeast(0L)
            val initial = minOf(maxOf(declared.toInt().coerceAtLeast(0), 1024), maxBytes)
            val output = ByteArrayOutputStream(initial)
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) return output.toByteArray()
                if (total + read > maxBytes) return null
                output.write(chunk, 0, read)
                total += read
            }
        }
    }

    fun readBytes(resolver: ContentResolver, file: DocumentFile): ByteArray {
        plainFile(file)?.let { return it.readBytes() }
        val stream = resolver.openInputStream(file.uri)
            ?: throw IOException(words.cannotOpenFile(file.name))
        return stream.use { readAll(it, file.length()) }
    }

    /** Odczyt po adresie ze spisu - rozmiar jest już znany, więc bez pytania o niego. */
    fun readBytes(resolver: ContentResolver, entry: DiskEntry): ByteArray {
        plainFile(entry.uri)?.let { return it.readBytes() }
        val stream = resolver.openInputStream(entry.uri)
            ?: throw IOException(words.cannotOpenFile(entry.name))
        return stream.use { readAll(it, entry.size) }
    }

    private fun readAll(input: InputStream, sizeHint: Long): ByteArray {
        // Zapowiedziany rozmiar to tylko podpowiedź - bufor i tak rośnie sam,
        // a zaufanie do zbyt dużej liczby kończyłoby się brakiem pamięci.
        val buffer = ByteArrayOutputStream(sizeHint.coerceIn(1024L, MAX_SIZE_HINT).toInt())
        input.copyTo(buffer)
        return buffer.toByteArray()
    }

    private fun plainFile(file: DocumentFile): File? = plainFile(file.uri)

    private fun plainFile(uri: Uri): File? =
        if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let(::File) else null

    // Zapis

    fun writeBytes(resolver: ContentResolver, file: DocumentFile, data: ByteArray) {
        writeBytes(resolver, file.uri, data, file.name)
    }

    fun writeBytes(resolver: ContentResolver, uri: Uri, data: ByteArray, name: String?) {
        plainFile(uri)?.let { fileOnDisk ->
            fileOnDisk.writeBytes(data)
            return
        }
        val stream = resolver.openOutputStream(uri, "rwt")
            ?: throw IOException(words.cannotSaveFile(name))
        stream.use { output ->
            output.write(data)
            output.flush()
        }
    }

    fun writeText(resolver: ContentResolver, file: DocumentFile, content: String) {
        writeBytes(resolver, file, content.toByteArray(Charsets.UTF_8))
    }

    fun writeText(resolver: ContentResolver, uri: Uri, content: String, name: String?) {
        writeBytes(resolver, uri, content.toByteArray(Charsets.UTF_8), name)
    }

    fun copyUri(resolver: ContentResolver, source: Uri, target: DocumentFile) {
        try {
            val stream = resolver.openInputStream(source)
                ?: throw IOException(words.cannotReadFile(target.name))
            stream.use { input ->
                val plain = plainFile(target)
                if (plain != null) {
                    plain.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        output.flush()
                    }
                    return
                }
                val output = resolver.openOutputStream(target.uri, "rwt")
                    ?: throw IOException(words.cannotSaveFile(target.name))
                output.use {
                    input.copyTo(it, DEFAULT_BUFFER_SIZE)
                    it.flush()
                }
            }
        } catch (e: FileNotFoundException) {
            throw IOException(words.cannotReadFile(target.name), e)
        }
    }

    fun fileForWrite(resolver: ContentResolver, folder: DocumentFile, name: String, mime: String): DocumentFile {
        locate(resolver, folder, name)?.let { if (it.entry.isFile) return it.file }
        return createFile(folder, name, mime)
    }

    /**
     * Adres pliku do nadpisania: z gotowego spisu [inside], a gdy pliku
     * jeszcze nie ma - świeżo założony. Zapis notatki nadpisuje dwa pliki
     * w tym samym katalogu, więc spis wystarczy przeczytać raz.
     */
    fun uriForWrite(
        inside: List<DiskEntry>,
        folder: DocumentFile,
        name: String,
        mime: String,
    ): Uri {
        inside.firstOrNull { it.name == name }?.let { if (it.isFile) return it.uri }
        return createFile(folder, name, mime).uri
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

    fun folderForWrite(resolver: ContentResolver, parent: DocumentFile, name: String): DocumentFile {
        locate(resolver, parent, name)?.let { if (it.entry.isDirectory) return it.file }
        return parent.createDirectory(name)
            ?: throw IOException(words.cannotCreateFolder(name))
    }

    // Kopiowanie i kasowanie całych gałęzi

    /**
     * Kopia pliku albo całego katalogu pod nową nazwą.
     *
     * Na tym stoi przeniesienie, wyrzucenie do kosza i przywrócenie: kopia,
     * a po niej skasowanie źródła. Dlatego spis, który nie przyszedł, jest
     * tu błędem, a nie pustym katalogiem. DocumentFile.listFiles() oddawał
     * wtedy pustą tablicę - kopia wychodziła pusta, a skasowanie źródła
     * zabierało notatki bezpowrotnie.
     */
    fun copyRecursively(
        resolver: ContentResolver,
        source: DocumentFile,
        targetFolder: DocumentFile,
        newName: String,
    ): DocumentFile {
        val described = describe(resolver, source)
            ?: throw IOException(words.fileVanishedWhileCopying(source.name))
        return copyRecursively(resolver, described, targetFolder, newName)
    }

    fun copyRecursively(
        resolver: ContentResolver,
        source: DiskEntry,
        targetFolder: DocumentFile,
        newName: String,
    ): DocumentFile {
        if (source.isDirectory) {
            val inside = children(resolver, source.uri)
                ?: throw IOException(words.cannotReadFile(source.name))
            val target = folderForWrite(resolver, targetFolder, newName)
            for (child in inside) {
                copyRecursively(resolver, child, target, child.name)
            }
            return target
        }
        val target = createFile(targetFolder, newName, source.mime ?: MIME_BINARY)
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

    fun occupiedNames(resolver: ContentResolver, folder: DocumentFile): Set<String> {
        children(resolver, folder.uri)?.let { listed -> return listed.mapTo(HashSet()) { it.name } }
        return folder.listFiles().mapNotNull { it.name }.toSet()
    }

    fun deleteRecursively(resolver: ContentResolver, file: DocumentFile): Boolean {
        val described = describe(resolver, file) ?: return deleteSlowly(file)
        return deleteRecursively(resolver, described)
    }

    fun deleteRecursively(resolver: ContentResolver, entry: DiskEntry): Boolean {
        if (entry.isDirectory) {
            // Bez spisu kasujemy sam katalog: magazyn Androida i tak zabiera go
            // razem z zawartością, a nieudana próba wraca jako false i trafia
            // do kolejki ponowień.
            for (child in children(resolver, entry.uri).orEmpty()) {
                deleteRecursively(resolver, child)
            }
        }
        return deleteDocument(resolver, entry.uri)
    }

    /** Kasuje jeden wpis po adresie. Katalog z plików aplikacji musi być już pusty. */
    fun deleteDocument(resolver: ContentResolver, uri: Uri): Boolean {
        plainFile(uri)?.let { return it.delete() }
        return try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            false
        }
    }

    // Dawna droga, gdy magazyn nie chce opisać wpisu jednym zapytaniem.
    private fun deleteSlowly(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            for (child in file.listFiles()) {
                deleteSlowly(child)
            }
        }
        return file.delete()
    }
}
