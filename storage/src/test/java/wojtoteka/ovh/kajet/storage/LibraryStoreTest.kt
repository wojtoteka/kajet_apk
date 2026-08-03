package wojtoteka.ovh.kajet.storage

import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var store: LibraryStore

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("library")
        val context = RuntimeEnvironment.getApplication()
        store = LibraryStore(context.contentResolver, DocumentFile.fromFile(root))
    }

    @Test
    fun `a new folder becomes a directory on disk`() {
        val folder = store.createFolder("", "Matematyka", colorId = "morski", iconId = "dzialania")

        assertThat(File(root, "Matematyka").isDirectory).isTrue()
        assertThat(File(root, "Matematyka/folder.json").isFile).isTrue()
        assertThat(folder.path).isEqualTo("Matematyka")
        assertThat(folder.colorId).isEqualTo("morski")
    }

    @Test
    fun `a folder with a colon gets a safe name on disk but keeps the real one`() {
        val folder = store.createFolder("", "Fizyka: mechanika")

        assertThat(File(root, "Fizyka_ mechanika").isDirectory).isTrue()
        assertThat(folder.name).isEqualTo("Fizyka: mechanika")

        val fromListing = store.list("").first()
        assertThat(fromListing.name).isEqualTo("Fizyka: mechanika")
        assertThat(fromListing.path).isEqualTo("Fizyka_ mechanika")
    }

    @Test
    fun `folder nesting has no limit`() {
        store.createFolder("", "Szkoła")
        store.createFolder("Szkoła", "Matematyka")
        store.createFolder("Szkoła/Matematyka", "Całki")
        store.createFolder("Szkoła/Matematyka/Całki", "Podstawienia")

        assertThat(File(root, "Szkoła/Matematyka/Całki/Podstawienia").isDirectory).isTrue()
        assertThat(store.list("Szkoła/Matematyka/Całki")).hasSize(1)
    }

    @Test
    fun `a note is a directory ending in note with a content json file`() {
        val note = store.createNote(
            "", "Pochodne", NoteKind.HANDWRITTEN, PageMode.A4, PageBackground.GRID,
        )

        val folder = File(root, "Pochodne.note")
        assertThat(folder.isDirectory).isTrue()
        assertThat(File(folder, "content.json").isFile).isTrue()

        val document = store.readNote(note.path)
        assertThat(document.title).isEqualTo("Pochodne")
        assertThat(document.kind).isEqualTo(NoteKind.HANDWRITTEN)
        val handwriting = document.handwriting!!
        assertThat(handwriting.background).isEqualTo(PageBackground.GRID)
        assertThat(handwriting.pages).hasSize(1)
    }

    @Test
    fun `saving a note leaves a copy in case the write is cut short`() {
        val note = store.createNote("", "Zadania", NoteKind.TEXT)
        val document = store.readNote(note.path)
        store.writeNote(note.path, document.copy(text = TextContent(markdown = "# Zadanie 1")))

        val folder = File(root, "Zadania.note")
        assertThat(File(folder, LibraryStore.BACKUP_FILE).isFile).isTrue()
        assertThat(File(folder, "content.json").readText()).contains("Zadanie 1")
        assertThat(File(folder, LibraryStore.BACKUP_FILE).readText()).contains("Zadanie 1")
    }

    @Test
    fun `a damaged main file does not lose the note, because the copy loads`() {
        val note = store.createNote("", "Ważne", NoteKind.TEXT)
        val document = store.readNote(note.path)
        store.writeNote(note.path, document.copy(text = TextContent(markdown = "treść nie do stracenia")))

        // This is what the file looks like after a write cut in half.
        File(root, "Ważne.note/content.json").writeText("{\"format\":1,\"id\":\"n")

        val recovered = store.readNote(note.path)
        assertThat(recovered.text!!.markdown).isEqualTo("treść nie do stracenia")
    }

    @Test
    fun `two notes with the same name do not overwrite each other`() {
        store.createNote("", "Notatka", NoteKind.TEXT)
        val second = store.createNote("", "Notatka", NoteKind.TEXT)

        assertThat(second.path).isEqualTo("Notatka (2).note")
        assertThat(File(root, "Notatka.note").isDirectory).isTrue()
        assertThat(File(root, "Notatka (2).note").isDirectory).isTrue()
    }

    @Test
    fun `renaming a note changes the directory and the title inside`() {
        val note = store.createNote("", "Stara nazwa", NoteKind.TEXT)
        val newPath = store.rename(note.path, "Nowa nazwa")

        assertThat(newPath).isEqualTo("Nowa nazwa.note")
        assertThat(File(root, "Stara nazwa.note").exists()).isFalse()
        assertThat(store.readNote(newPath).title).isEqualTo("Nowa nazwa")
    }

    @Test
    fun `moving a note into a folder moves the whole directory with attachments`() {
        store.createFolder("", "Fizyka")
        val note = store.createNote("", "Drgania", NoteKind.HANDWRITTEN)
        store.writeAttachment(note.path, "wykres.png", byteArrayOf(1, 2, 3), "image/png")

        val newPath = store.move(note.path, "Fizyka")

        assertThat(newPath).isEqualTo("Fizyka/Drgania.note")
        assertThat(File(root, "Drgania.note").exists()).isFalse()
        assertThat(File(root, "Fizyka/Drgania.note/content.json").isFile).isTrue()
        assertThat(store.readAttachment(newPath, "wykres.png")).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a folder cannot be moved into itself`() {
        store.createFolder("", "Szkoła")
        store.createFolder("Szkoła", "Matematyka")

        val error = assertThrows(java.io.IOException::class.java) {
            store.move("Szkoła", "Szkoła/Matematyka")
        }
        assertThat(error.message).contains("własnego wnętrza")
    }

    @Test
    fun `copying a note creates a separate directory with the same content`() {
        val note = store.createNote("", "Wzory", NoteKind.TEXT)
        val document = store.readNote(note.path)
        store.writeNote(note.path, document.copy(text = TextContent(markdown = "E = mc^2")))

        val copy = store.copy(note.path, "")

        assertThat(copy).isEqualTo("Wzory (kopia).note")
        assertThat(store.readNote(copy).text!!.markdown).isEqualTo("E = mc^2")
        assertThat(store.readNote(note.path).text!!.markdown).isEqualTo("E = mc^2")
    }

    @Test
    fun `the bin does not delete the file, it moves it into the trash directory`() {
        val note = store.createNote("", "Do wyrzucenia", NoteKind.TEXT)
        store.moveToTrash(note.path)

        assertThat(File(root, "Do wyrzucenia.note").exists()).isFalse()
        assertThat(File(root, ".trash").isDirectory).isTrue()

        val trash = store.listTrash()
        assertThat(trash).hasSize(1)
        assertThat(trash.first().displayName).isEqualTo("Do wyrzucenia")
        assertThat(trash.first().originalPath).isEqualTo("Do wyrzucenia.note")
    }

    @Test
    fun `restoring from the bin puts the note back where it disappeared from`() {
        store.createFolder("", "Polski")
        val note = store.createNote("Polski", "Lektury", NoteKind.TEXT)
        val document = store.readNote(note.path)
        store.writeNote(note.path, document.copy(text = TextContent(markdown = "Pan Tadeusz")))

        store.moveToTrash(note.path)
        val restored = store.restore(store.listTrash().first().id)

        assertThat(restored).isEqualTo("Polski/Lektury.note")
        assertThat(store.readNote(restored).text!!.markdown).isEqualTo("Pan Tadeusz")
        assertThat(store.listTrash()).isEmpty()
    }

    @Test
    fun `restoring recreates the missing parent folder`() {
        store.createFolder("", "Chemia")
        val note = store.createNote("Chemia", "Kwasy", NoteKind.TEXT)
        store.moveToTrash(note.path)
        store.moveToTrash("Chemia")

        val trashed = store.listTrash().first { it.originalPath.endsWith("Kwasy.note") }
        val restored = store.restore(trashed.id)

        assertThat(restored).isEqualTo("Chemia/Kwasy.note")
        assertThat(File(root, "Chemia/Kwasy.note/content.json").isFile).isTrue()
    }

    @Test
    fun `emptying the bin deletes everything for good`() {
        store.moveToTrash(store.createNote("", "Raz", NoteKind.TEXT).path)
        store.moveToTrash(store.createNote("", "Dwa", NoteKind.TEXT).path)
        assertThat(store.listTrash()).hasSize(2)

        store.emptyTrash()

        assertThat(store.listTrash()).isEmpty()
    }

    @Test
    fun `the bin does not show up in the folder listing`() {
        store.moveToTrash(store.createNote("", "Coś", NoteKind.TEXT).path)
        store.createFolder("", "Widoczny")

        val items = store.list("")

        assertThat(items.map { it.name }).containsExactly("Widoczny")
    }

    @Test
    fun `the folder json file does not show up as a note`() {
        store.createFolder("", "Historia")
        val items = store.list("Historia")
        assertThat(items).isEmpty()
    }

    @Test
    fun `a code file gets an extension and a recognised language`() {
        val file = store.createCodeFile("", "zadanie", wojtoteka.ovh.kajet.core.model.CodeLanguage.PYTHON)

        assertThat(file.name).isEqualTo("zadanie.py")
        assertThat(file.type).isEqualTo(ItemType.CODE_FILE)
        assertThat(file.language).isEqualTo(wojtoteka.ovh.kajet.core.model.CodeLanguage.PYTHON)
        assertThat(store.readText(file.path)).contains("print")
    }

    @Test
    fun `walking the tree finds everything outside the bin`() {
        store.createFolder("", "Matematyka")
        store.createNote("Matematyka", "Całki", NoteKind.HANDWRITTEN)
        store.createNote("", "Luźne myśli", NoteKind.TEXT)
        store.moveToTrash(store.createNote("", "Wyrzucona", NoteKind.TEXT).path)

        val found = mutableListOf<String>()
        store.walkTree { found += it.path }

        assertThat(found).containsExactly(
            "Matematyka",
            "Matematyka/Całki.note",
            "Luźne myśli.note",
        )
    }

    @Test
    fun `folders come before notes in the listing`() {
        store.createNote("", "Aaa notatka", NoteKind.TEXT)
        store.createFolder("", "Zzz folder")

        val items = store.list("")

        assertThat(items.map { it.type }).containsExactly(ItemType.FOLDER, ItemType.NOTE).inOrder()
    }

    @Test
    fun `an attachment lands in the assets directory inside the note`() {
        val note = store.createNote("", "Ze zdjęciem", NoteKind.TEXT)
        val name = store.writeAttachment(note.path, "tablica.jpg", byteArrayOf(9, 8, 7), "image/jpeg")

        assertThat(File(root, "Ze zdjęciem.note/assets/$name").isFile).isTrue()
        assertThat(store.readAttachment(note.path, name)).isEqualTo(byteArrayOf(9, 8, 7))
    }

    @Test
    fun `a folder colour change is saved in folder json`() {
        store.createFolder("", "Biologia")
        store.updateFolderLook("Biologia", "oliwka", "kolba")

        val item = store.list("").first()
        assertThat(item.colorId).isEqualTo("oliwka")
        assertThat(item.iconId).isEqualTo("kolba")
        assertThat(item.name).isEqualTo("Biologia")
    }
}
