package wojtoteka.ovh.kajet.storage

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.storage.index.IndexDatabase
import java.io.File

/**
 * Biblioteka pokazuje folder najpierw ze spisu, a dysk dogrywa się w tle
 * i poprawia spis. Tu widać obie strony: co trafia na ekran i co zostaje
 * w spisie, z którego korzysta potem synchronizacja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFolderContentTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var database: IndexDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("library")
        val context = RuntimeEnvironment.getApplication()
        val store = LibraryStore(context.contentResolver, FakeDocumentsProvider.install(root))
        database = Room.inMemoryDatabaseBuilder(context, IndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(
            context = context,
            settings = SettingsStore(context),
            dao = database.index(),
            retries = DeleteRetryQueue(context),
            io = Dispatchers.IO,
            fixedStore = store,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Notatka położona na dysk z pominięciem aplikacji - jak skopiowana z komputera. */
    private fun noteFromOutside(name: String, title: String) {
        val folder = File(root, "$name.note").apply { mkdirs() }
        val document = NoteDocument(
            id = "z-zewnatrz-$name",
            kind = NoteKind.TEXT,
            title = title,
            createdAt = 1L,
            updatedAt = 2L,
            text = TextContent(markdown = "treść spoza aplikacji"),
        )
        File(folder, NoteDocument.CONTENT_FILE).writeText(NoteCodec.encodeNote(document))
    }

    private fun contents(count: Int): List<FolderContent> = runBlocking {
        withTimeout(10_000) { repository.folderContent("").take(count).toList() }
    }

    @Test
    fun `the remembered folder comes first and the disk confirms it`() {
        runBlocking { repository.createNote("", "Pierwsza", NoteKind.TEXT) }

        val (remembered, confirmed) = contents(2)

        assertThat(remembered.fromDisk).isFalse()
        assertThat(remembered.items.map { it.name }).containsExactly("Pierwsza")
        assertThat(confirmed.fromDisk).isTrue()
        assertThat(confirmed.items.map { it.name }).containsExactly("Pierwsza")
    }

    @Test
    fun `a note copied in from outside appears and lands in the index`() {
        runBlocking { repository.createNote("", "Znana", NoteKind.TEXT) }
        noteFromOutside("Skopiowana", "Tytuł ze środka")

        val shown = contents(3)

        assertThat(shown[0].items.map { it.name }).containsExactly("Znana")
        // Z dysku od razu, choć jeszcze pod nazwą katalogu...
        assertThat(shown[1].items.map { it.name }).containsExactly("Znana", "Skopiowana")
        // ...a po przeczytaniu - pod tytułem z treści.
        assertThat(shown[2].items.map { it.name }).containsExactly("Znana", "Tytuł ze środka")
        val ids = runBlocking { repository.allNoteIds() }.map { it.second }
        assertThat(ids).contains("z-zewnatrz-Skopiowana")
    }

    @Test
    fun `a note deleted outside the app leaves the index, and only the index`() {
        val gone = runBlocking { repository.createNote("", "Zniknie", NoteKind.TEXT) }
        runBlocking { repository.createNote("", "Zostaje", NoteKind.TEXT) }
        File(root, gone.path).deleteRecursively()

        val shown = contents(2)

        assertThat(shown[1].items.map { it.name }).containsExactly("Zostaje")
        val indexed = runBlocking { repository.allNoteIds() }.map { it.first }
        assertThat(indexed).containsExactly("Zostaje.note")
        assertThat(File(root, "Zostaje.note/content.json").isFile).isTrue()
    }

    @Test
    fun `a storage that does not answer keeps what the index remembers`() {
        runBlocking {
            repository.createNote("", "Raz", NoteKind.TEXT)
            repository.createNote("", "Dwa", NoteKind.TEXT)
        }
        FakeDocumentsProvider.failListing = { it == root }

        val shown = contents(2)

        FakeDocumentsProvider.failListing = null
        assertThat(shown[1].fromDisk).isTrue()
        assertThat(shown[1].items.map { it.name }).containsExactly("Raz", "Dwa")
        assertThat(runBlocking { repository.allNoteIds() }).hasSize(2)
    }

    @Test
    fun `an empty folder is reported as empty only after the disk says so`() {
        val first = runBlocking { withTimeout(10_000) { repository.folderContent("").first() } }

        assertThat(first.fromDisk).isTrue()
        assertThat(first.items).isEmpty()
    }

    @Test
    fun `folders from the index carry their colour and how much is inside`() {
        runBlocking {
            repository.createFolder("", "Szkoła", colorId = "morski", iconId = "ksiazki")
            repository.createNote("Szkoła", "W środku", NoteKind.TEXT)
        }

        val remembered = runBlocking { repository.indexedFolder("") }

        val folder = remembered.single()
        assertThat(folder.name).isEqualTo("Szkoła")
        assertThat(folder.colorId).isEqualTo("morski")
        assertThat(folder.childCount).isEqualTo(1)
    }

    @Test
    fun `a saved note shows the time it was saved, not the time it was opened`() {
        val item = runBlocking { repository.createNote("", "Poprawiana", NoteKind.TEXT) }
        val opened = runBlocking { repository.readNote(item.path) }
        Thread.sleep(5)

        runBlocking { repository.writeNote(item.path, opened.copy(text = TextContent(markdown = "poprawka"))) }

        val listed = runBlocking { repository.indexedFolder("") }.single()
        assertThat(listed.updatedAt).isGreaterThan(opened.updatedAt)
    }

    @Test
    fun `a star set from the list does not move the note to the top`() {
        val item = runBlocking { repository.createNote("", "Z gwiazdką", NoteKind.TEXT) }
        val before = runBlocking { repository.indexedFolder("") }.single().updatedAt
        Thread.sleep(5)

        runBlocking { repository.toggleFavorite(item.path) }

        val listed = runBlocking { repository.indexedFolder("") }.single()
        assertThat(listed.favorite).isTrue()
        assertThat(listed.updatedAt).isEqualTo(before)
    }

    @Test
    fun `the thumbnail of a saved handwritten note does not read the note again`() {
        val item = runBlocking { repository.createNote("", "Odręczna", NoteKind.HANDWRITTEN) }
        val document = runBlocking { repository.readNote(item.path) }
        val page = document.handwriting!!.pages.first()
        val stroke = wojtoteka.ovh.kajet.core.model.InkStroke(
            id = "k",
            color = 0,
            size = 2f,
            points = listOf(10f, 10f, 0f, 1f, 0f, 0f, 80f, 40f, 5f, 1f, 0f, 0f),
        )
        val drawn = document.copy(
            handwriting = document.handwriting!!.copy(pages = listOf(page.copy(strokes = listOf(stroke)))),
        )
        runBlocking { repository.writeNote(item.path, drawn) }
        val listed = runBlocking { repository.indexedFolder("") }.single()
        // Bez pliku na dysku - miniatura musi przyjść z pamięci, a nie z odczytu.
        File(root, "${item.path}/content.json").delete()
        File(root, "${item.path}/${LibraryStore.BACKUP_FILE}").delete()

        val thumbnail = runBlocking { repository.thumbnail(listed) }

        assertThat(thumbnail?.lines?.single()?.toList()).containsExactly(10, 10, 80, 40).inOrder()
    }

    @Test
    fun `the disk list shows up before unknown notes are read`() {
        repeat(3) { number -> noteFromOutside("Z zewnątrz $number", "Tytuł $number") }
        FakeDocumentsProvider.openDelayMs = 1_000
        val started = System.nanoTime()
        val arrivals = mutableListOf<Long>()

        val shown = runBlocking {
            withTimeout(20_000) {
                repository.folderContent("")
                    .onEach { arrivals += (System.nanoTime() - started) / 1_000_000 }
                    .take(2)
                    .toList()
            }
        }

        FakeDocumentsProvider.openDelayMs = 0
        // Lista z dysku nie czeka na odczyt żadnej notatki...
        assertThat(shown[0].items.map { it.name }).containsExactly("Z zewnątrz 0", "Z zewnątrz 1", "Z zewnątrz 2")
        assertThat(arrivals[0]).isLessThan(1_000)
        // ...a tytuły ze środka przychodzą, gdy notatki zostaną przeczytane.
        assertThat(shown[1].items.map { it.name }).containsExactly("Tytuł 0", "Tytuł 1", "Tytuł 2")
        assertThat(arrivals[1]).isAtLeast(3_000)
    }

    @Test
    fun `a note that cannot be read is shown from the disk and not written into the index empty`() {
        runBlocking { repository.createNote("", "Dobra", NoteKind.TEXT) }
        File(root, "Zepsuta.note").mkdirs()
        File(root, "Zepsuta.note/content.json").writeText("{ to nie jest notatka")

        val shown = contents(2)

        assertThat(shown[1].items.map { it.name }).containsExactly("Dobra", "Zepsuta")
        assertThat(runBlocking { repository.allNoteIds() }.map { it.first }).containsExactly("Dobra.note")
    }

    @Test
    fun `an interrupted rebuild leaves the index as it was, not half empty`() {
        runBlocking {
            repeat(5) { number -> repository.createNote("", "Notatka $number", NoteKind.TEXT) }
        }

        val interrupted = runCatching {
            runBlocking {
                repository.rebuildIndex { done, _ ->
                    if (done == 2) throw kotlinx.coroutines.CancellationException("wyjście z aplikacji")
                }
            }
        }

        assertThat(interrupted.isFailure).isTrue()
        assertThat(runBlocking { repository.allNoteIds() }).hasSize(5)
    }

    @Test
    fun `a full rebuild drops rows of files that are gone and keeps what was opened`() {
        val opened = runBlocking { repository.createNote("", "Otwierana", NoteKind.TEXT) }
        runBlocking {
            repository.rememberOpened(opened)
            database.index().upsert(
                wojtoteka.ovh.kajet.storage.index.IndexEntry(
                    documentUri = "uri:nieistniejaca",
                    documentId = "nieistniejaca",
                    path = "Nie ma.note",
                    name = "Nie ma",
                    type = "NOTE",
                ),
            )
        }

        runBlocking { repository.rebuildIndex() }

        assertThat(runBlocking { repository.allNoteIds() }.map { it.first }).containsExactly("Otwierana.note")
        assertThat(runBlocking { repository.recent().first() }.map { it.name }).containsExactly("Otwierana")
    }
}
