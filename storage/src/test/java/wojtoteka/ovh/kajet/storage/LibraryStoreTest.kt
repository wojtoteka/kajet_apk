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
    fun `bounded text read returns content at the limit`() {
        File(root, "small.log").writeText("12345")

        val result = store.readTextUpTo("small.log", 5)

        assertThat(result).isEqualTo(BoundedTextRead.Content("12345"))
    }

    @Test
    fun `bounded text read refuses a file above the limit`() {
        File(root, "large.log").writeText("123456")

        val result = store.readTextUpTo("large.log", 5)

        assertThat(result).isInstanceOf(BoundedTextRead.TooLarge::class.java)
        assertThat((result as BoundedTextRead.TooLarge).sizeBytes).isEqualTo(6)
        assertThat(result.documentUri).contains("large.log")
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

    /*
      Spis pokazuje tytuł z wnętrza pliku, a nie nazwę katalogu. Kopia z tytułem
      pierwowzoru stała więc na liście obok niego wyglądając identycznie i nie
      dało się poznać, którą z nich się otwiera.
    */
    @Test
    fun `kopia notatki ma inny tytul niz pierwowzor`() {
        val note = store.createNote("", "Wzory", NoteKind.TEXT)
        val copy = store.copy(note.path, "")

        assertThat(store.readNote(copy).title).isEqualTo("Wzory (kopia)")
        assertThat(store.readNote(note.path).title).isEqualTo("Wzory")
    }

    @Test
    fun `kopia folderu tez jest podpisana`() {
        store.createFolder("", "Matematyka")
        val copy = store.copy("Matematyka", "")

        assertThat(store.list("").first { it.path == copy }.name).isEqualTo("Matematyka (kopia)")
    }

    /*
      Magazyn Androida nie wie, że plik zmienia nazwę sam na siebie: widzi zajętą
      nazwę i dokleja „ (1)". „Zmień nazwę" i „Zapisz" bez poprawki zmieniały
      więc nazwę pliku, choć nikt o to nie prosił.
    */
    @Test
    fun `zapis tej samej nazwy nie rusza pliku`() {
        val note = store.createNote("", "Wzory", NoteKind.TEXT)
        val after = store.rename(note.path, "Wzory")

        assertThat(after).isEqualTo("Wzory.note")
        assertThat(File(root, "Wzory.note").isDirectory).isTrue()
        assertThat(root.list()!!.toList()).containsExactly("Wzory.note")
    }

    @Test
    fun `zapis tej samej nazwy folderu tez niczego nie dokleja`() {
        store.createFolder("", "Matematyka")
        val after = store.rename("Matematyka", "Matematyka")

        assertThat(after).isEqualTo("Matematyka")
        assertThat(root.list()!!.toList()).containsExactly("Matematyka")
    }

    /*
      Plik z barwą i ikoną folderu to nie wpis - przez niego pusty folder
      meldował na liście „1 wpis".
    */
    @Test
    fun `pusty folder nie liczy pliku ze swoim wygladem`() {
        store.createFolder("", "Pusty")

        assertThat(store.list("").first().childCount).isEqualTo(0)
    }

    @Test
    fun `folder liczy to, co w nim widac`() {
        store.createFolder("", "Szkoła")
        store.createNote("Szkoła", "Notatka", NoteKind.TEXT)

        assertThat(store.list("").first().childCount).isEqualTo(1)
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

    // Kasowanie notatki, którą serwer skasował na zawsze
    //
    // To jest sedno naprawy: notatka wyrzucona do kosza znika ze spisu, a kosz
    // jest ukryty, więc uzgadnianie z serwerem nie miało jak jej dosięgnąć.
    // Poniższe drogi po nią sięgają.

    @Test
    fun `a note in the bin can be found by its identifier`() {
        val note = store.createNote("", "Skasowana na stronie", NoteKind.TEXT)
        val noteId = store.readNote(note.path).id
        store.moveToTrash(note.path)

        val slot = store.trashSlotForNote(noteId)

        assertThat(slot).isNotNull()
        assertThat(store.trashedNoteSlots().map { it.second }).containsExactly(noteId)
    }

    @Test
    fun `deleting a note from the bin takes the whole slot with the description`() {
        val note = store.createNote("", "Do wymazania", NoteKind.TEXT)
        val noteId = store.readNote(note.path).id
        store.moveToTrash(note.path)
        val slot = store.trashSlotForNote(noteId)!!

        assertThat(store.deleteNoteInsideTrash(slot, noteId)).isTrue()

        assertThat(store.listTrash()).isEmpty()
        assertThat(File(root, ".trash/$slot").exists()).isFalse()
    }

    @Test
    fun `deleting one note from a binned folder leaves the rest restorable`() {
        store.createFolder("", "Fizyka")
        val goes = store.createNote("Fizyka", "Skasowana", NoteKind.TEXT)
        val stays = store.createNote("Fizyka", "Zostaje", NoteKind.MINDMAP)
        val goesId = store.readNote(goes.path).id
        val staysId = store.readNote(stays.path).id
        store.moveToTrash("Fizyka")

        val slot = store.trashSlotForNote(goesId)!!
        assertThat(store.deleteNoteInsideTrash(slot, goesId)).isTrue()

        // Wpis kosza stoi dalej, bo wyrzucono cały folder, a nie tę notatkę.
        assertThat(store.listTrash()).hasSize(1)
        assertThat(store.trashedNoteSlots().map { it.second }).containsExactly(staysId)

        val restored = store.restore(slot)
        assertThat(File(root, "$restored/Zostaje.note/content.json").isFile).isTrue()
        assertThat(File(root, "$restored/Skasowana.note").exists()).isFalse()
    }

    @Test
    fun `a handwritten note in the bin goes away with its attachments`() {
        val note = store.createNote("", "Odręczna", NoteKind.HANDWRITTEN)
        store.writeAttachment(note.path, "zdjecie.png", byteArrayOf(1, 2, 3), "image/png")
        val noteId = store.readNote(note.path).id
        store.moveToTrash(note.path)

        val slot = store.trashSlotForNote(noteId)!!
        store.deleteNoteInsideTrash(slot, noteId)

        assertThat(File(root, ".trash").listFiles()?.toList().orEmpty()).isEmpty()
    }

    @Test
    fun `a bin entry knows whether the server put it there`() {
        val mine = store.createNote("", "Moja", NoteKind.TEXT)
        val theirs = store.createNote("", "Skasowana na serwerze", NoteKind.TEXT)
        store.moveToTrash(mine.path)
        store.moveToTrash(theirs.path, fromServer = true)

        val entries = store.listTrash().associateBy { it.displayName }

        assertThat(entries.getValue("Moja").fromServer).isFalse()
        assertThat(entries.getValue("Skasowana na serwerze").fromServer).isTrue()
    }

    @Test
    fun `the countdown starts when the entry lands in this bin`() {
        val before = System.currentTimeMillis()
        store.moveToTrash(store.createNote("", "Za serwerem", NoteKind.TEXT).path, fromServer = true)
        val after = System.currentTimeMillis()

        // Data z TEGO urządzenia, nie z serwera - od niej liczy się czas na
        // przywrócenie.
        assertThat(store.listTrash().single().deletedAt).isAtLeast(before)
        assertThat(store.listTrash().single().deletedAt).isAtMost(after)
    }

    @Test
    fun `restoring takes the whole entry away, countdown included`() {
        val note = store.createNote("", "Wraca", NoteKind.TEXT)
        store.moveToTrash(note.path, fromServer = true)

        store.restore(store.listTrash().single().id)

        assertThat(store.listTrash()).isEmpty()
        assertThat(File(root, "Wraca.note/content.json").isFile).isTrue()
    }

    @Test
    fun `a bin slot without a description counts as broken`() {
        store.moveToTrash(store.createNote("", "Zdrowa", NoteKind.TEXT).path)
        File(root, ".trash/bez-opisu").mkdirs()

        assertThat(store.brokenTrashSlots()).containsExactly("bez-opisu")

        store.deletePermanently("bez-opisu")
        assertThat(store.brokenTrashSlots()).isEmpty()
        assertThat(store.listTrash()).hasSize(1)
    }

    @Test
    fun `a bin slot whose file vanished counts as broken`() {
        val note = store.createNote("", "Bez pliku", NoteKind.TEXT)
        store.moveToTrash(note.path)
        val slot = store.listTrash().first().id
        File(root, ".trash/$slot/Bez pliku.note").deleteRecursively()

        assertThat(store.brokenTrashSlots()).containsExactly(slot)
    }

    @Test
    fun `emptying the bin reports what it could not delete`() {
        store.moveToTrash(store.createNote("", "Raz", NoteKind.TEXT).path)

        assertThat(store.emptyTrash()).isEmpty()
        assertThat(store.listTrash()).isEmpty()
    }

    @Test
    fun `a code file in the bin is found by the path it had before`() {
        store.createFolder("", "Kod")
        val file = store.createCodeFile("Kod", "zadanie", wojtoteka.ovh.kajet.core.model.CodeLanguage.PYTHON)
        store.moveToTrash(file.path)

        val slots = store.trashedCodeSlots()
        assertThat(slots.map { it.second }).containsExactly("Kod/zadanie.py")

        val (slot, path) = slots.first()
        assertThat(store.deleteCodeInsideTrash(slot, path)).isTrue()
        assertThat(store.trashedCodeSlots()).isEmpty()
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
    fun `importing a jpeg keeps the bytes and is an other file`() {
        val source = temporaryFolder.newFile("zrodlo.jpg")
        source.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02))
        val item = store.importFile(
            "",
            "wakacje",
            "image/jpeg",
            android.net.Uri.fromFile(source),
        )

        assertThat(item.name).isEqualTo("wakacje.jpg")
        assertThat(item.type).isEqualTo(ItemType.OTHER_FILE)
        assertThat(File(root, "wakacje.jpg").readBytes())
            .isEqualTo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02))
    }

    @Test
    fun `importing a text file keeps a recognised language`() {
        val source = temporaryFolder.newFile("zrodlo.txt")
        source.writeText("linia")
        val item = store.importFile(
            "",
            "lista.txt",
            "text/plain",
            android.net.Uri.fromFile(source),
        )

        assertThat(item.type).isEqualTo(ItemType.CODE_FILE)
        assertThat(store.readText(item.path)).isEqualTo("linia")
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
