package wojtoteka.ovh.kajet.storage

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
}
