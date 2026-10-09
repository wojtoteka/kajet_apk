package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.text.words
import java.io.File

/**
 * Cudze notatki otwarte w aplikacji - z odnośnika albo z „Udostępnione mi".
 *
 * Nie leżą w katalogu notatek użytkownika (to jego biblioteka i jego kopia
 * zapasowa), tylko w pamięci aplikacji, każda we własnym katalogu:
 *
 *     shared/<id notatki>/content.json   treść, jak w bibliotece
 *     shared/<id notatki>/meta.json      odnośnik, prawa, czy przyjęta
 *     shared/<id notatki>/assets/        zdjęcia i rysunki
 *
 * Edytory otwierają je zwykłą ścieżką z przedrostkiem [PREFIX] - repozytorium
 * kieruje odczyt i zapis tutaj (patrz [LibraryRepository.readNote]), więc
 * edytor nie musi wiedzieć, czyja to notatka.
 *
 * Notatka z samego odnośnika jest tu tylko na czas oglądania - po wyjściu
 * z niej znika ([forgetTemporary]). Przyjęta (udostępniona imiennie) zostaje,
 * więc da się ją otworzyć i pisać w niej także bez sieci; zmiany dojadą, gdy
 * sieć wróci.
 */
class SharedNotes(private val root: File) {

    @Serializable
    data class Meta(
        val noteId: String,
        /** Odnośnik, którym ta notatka się otwiera - nim przedstawia się serwerowi. */
        val token: String,
        val canEdit: Boolean,
        /** Przyjęte udostępnienie imienne - zostaje na liście po wyjściu. */
        val accepted: Boolean,
        val title: String = "",
        val kind: String = "",
        val owner: String = "",
        /** Wersja serwera, z której pochodzi [base] - od niej idzie edycja na żywo. */
        val version: Int = 0,
        /** Treść z serwera w tej wersji - baza scalania po pracy bez sieci. */
        val base: String = "",
    )

    fun isShared(path: String): Boolean = path.startsWith(PREFIX)

    fun pathFor(noteId: String): String = PREFIX + noteId

    fun noteIdOf(path: String): String = path.removePrefix(PREFIX)

    private fun directory(noteId: String): File {
        // Identyfikator przychodzi z serwera - nie pozwalamy mu wyjść z katalogu.
        val safe = noteId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(root, safe)
    }

    @Synchronized
    fun store(meta: Meta, document: NoteDocument) {
        val folder = directory(meta.noteId)
        folder.mkdirs()
        File(folder, CONTENT).writeText(NoteCodec.encodeNote(document))
        writeMeta(meta)
    }

    @Synchronized
    fun meta(noteId: String): Meta? = runCatching {
        NoteCodec.json.decodeFromString<Meta>(File(directory(noteId), META).readText())
    }.getOrNull()

    @Synchronized
    fun writeMeta(meta: Meta) {
        val folder = directory(meta.noteId)
        folder.mkdirs()
        File(folder, META).writeText(NoteCodec.json.encodeToString(meta))
    }

    /** Przesuwa bazę scalania po zapisie przyjętym przez serwer. */
    @Synchronized
    fun rememberBase(noteId: String, version: Int, base: String) {
        val meta = meta(noteId) ?: return
        writeMeta(meta.copy(version = version, base = base))
    }

    fun readNote(noteId: String): NoteDocument =
        NoteCodec.decodeNote(File(directory(noteId), CONTENT).readText())

    @Synchronized
    fun writeNote(noteId: String, document: NoteDocument) {
        val folder = directory(noteId)
        if (!folder.isDirectory) throw FormatException(words.sharedNotHere)
        File(folder, CONTENT).writeText(NoteCodec.encodeNote(document))
    }

    fun exists(noteId: String): Boolean = File(directory(noteId), CONTENT).isFile

    private fun assets(noteId: String): File = File(directory(noteId), ASSETS)

    private fun asset(noteId: String, name: String): File? {
        val clean = File(name).name
        if (clean.isBlank() || clean != name) return null
        return File(assets(noteId), clean)
    }

    fun attachmentNames(noteId: String): List<String> =
        assets(noteId).list()?.sorted().orEmpty()

    fun readAttachment(noteId: String, name: String): ByteArray? =
        asset(noteId, name)?.takeIf { it.isFile }?.readBytes()

    fun writeAttachment(noteId: String, name: String, data: ByteArray) {
        val file = asset(noteId, name) ?: return
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    /** Notatki przyjęte na tym urządzeniu - do listy w bibliotece bez sieci. */
    fun accepted(): List<Meta> =
        root.listFiles()?.mapNotNull { folder -> meta(folder.name) }?.filter { it.accepted }.orEmpty()

    @Synchronized
    fun forget(noteId: String) {
        directory(noteId).deleteRecursively()
    }

    /** Sprząta notatki otwarte samym odnośnikiem - nie miały zostać na urządzeniu. */
    @Synchronized
    fun forgetTemporary(except: String? = null) {
        root.listFiles()?.forEach { folder ->
            val meta = meta(folder.name)
            if ((meta == null || !meta.accepted) && folder.name != except) folder.deleteRecursively()
        }
    }

    companion object {
        /** Pusta notatka danego rodzaju - do założenia w cudzym folderze. */
        fun emptyDocument(id: String, kind: NoteKind, title: String): NoteDocument {
            val now = System.currentTimeMillis()
            return NoteDocument(
                id = id,
                kind = kind,
                title = title,
                createdAt = now,
                updatedAt = now,
                handwriting = if (kind == NoteKind.HANDWRITTEN) {
                    HandwritingContent(
                        pageMode = PageMode.A4,
                        background = PageBackground.LINED,
                        pages = listOf(
                            NotePage(
                                id = java.util.UUID.randomUUID().toString(),
                                width = NotePage.A4_WIDTH,
                                height = NotePage.A4_HEIGHT,
                            ),
                        ),
                    )
                } else {
                    null
                },
                text = if (kind == NoteKind.TEXT) TextContent() else null,
                mindMap = if (kind == NoteKind.MINDMAP) MindMapContent() else null,
            )
        }

        /**
         * Przedrostek ścieżek cudzych notatek. Dwukropek nie może wystąpić
         * w nazwie pliku na pamięci telefonu, więc ścieżka z biblioteki nigdy
         * tak nie wygląda.
         */
        const val PREFIX = "udostepnione:"
        private const val CONTENT = "content.json"
        private const val META = "meta.json"
        private const val ASSETS = "assets"
    }
}
