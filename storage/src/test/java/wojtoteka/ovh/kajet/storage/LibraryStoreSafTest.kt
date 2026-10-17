package wojtoteka.ovh.kajet.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.File

/**
 * Te same sprawdziany co w [LibraryStoreTest], tylko przez magazyn dokumentów
 * (SAF) - tak jak na telefonie i tablecie, a nie po zwykłych plikach.
 *
 * Do tego kilka pomiarów: ile razy biblioteka pyta magazyn. Każde takie
 * pytanie to wywołanie innego procesu, a dawniej szło ich kilka na KAŻDY plik
 * w folderze - przy kilkuset notatkach interfejs stawał na całe sekundy.
 */
class LibraryStoreSafTest : LibraryStoreTest() {

    override fun storeFor(root: File): LibraryStore {
        val tree = FakeDocumentsProvider.install(root)
        return LibraryStore(RuntimeEnvironment.getApplication().contentResolver, tree)
    }

    private fun queriesDuring(block: () -> Unit): Int {
        val before = FakeDocumentsProvider.queries
        block()
        return FakeDocumentsProvider.queries - before
    }

    @Test
    fun `listing a folder asks once for the folder and once per subfolder, not per note`() {
        store.createFolder("", "Szkoła")
        store.createFolder("Szkoła", "Fizyka")
        store.createFolder("Szkoła", "Chemia")
        repeat(60) { number -> store.createNote("Szkoła", "Notatka $number", NoteKind.TEXT) }

        var items = emptyList<wojtoteka.ovh.kajet.core.model.LibraryItem>()
        val asked = queriesDuring { items = store.list("Szkoła") }

        assertThat(items).hasSize(62)
        assertThat(items.take(2).map { it.name }).containsExactly("Chemia", "Fizyka").inOrder()
        // Korzeń, sam folder i po jednym pytaniu o wnętrze każdego podfolderu.
        assertThat(asked).isAtMost(5)
    }

    @Test
    fun `folder descriptions are read once and remembered until they change`() {
        repeat(5) { number -> store.createFolder("", "Folder $number", colorId = "morski") }
        store.list("")

        assertThat(store.list("").map { it.colorId }.toSet()).containsExactly("morski")

        store.updateFolderLook("Folder 3", colorId = "rdza", iconId = "atom")
        val after = store.list("").first { it.path == "Folder 3" }
        assertThat(after.colorId).isEqualTo("rdza")
        assertThat(after.iconId).isEqualTo("atom")
    }

    @Test
    fun `opening a note among hundreds does not ask about every neighbour`() {
        repeat(200) { number -> store.createNote("", "Notatka $number", NoteKind.TEXT) }
        val note = store.createNote("", "Szukana", NoteKind.TEXT)

        val asked = queriesDuring { store.readNote(note.path) }

        assertThat(asked).isAtMost(3)
    }

    @Test
    fun `saving a note does not walk the whole folder file by file`() {
        repeat(200) { number -> store.createNote("", "Notatka $number", NoteKind.TEXT) }
        val note = store.createNote("", "Pisana", NoteKind.TEXT)
        val document = store.readNote(note.path)

        val asked = queriesDuring {
            store.writeNote(note.path, document.copy(text = TextContent(markdown = "nowa treść")))
        }

        assertThat(store.readNote(note.path).text!!.markdown).isEqualTo("nowa treść")
        assertThat(asked).isAtMost(6)
    }

    @Test
    fun `a move whose source cannot be listed keeps the source`() {
        store.createFolder("", "Cel")
        val note = store.createNote("", "Cenna", NoteKind.TEXT)
        store.writeAttachment(note.path, "zdjecie.png", byteArrayOf(9, 9, 9), "image/png")
        // Spis zdjęć nie przychodzi. Dawniej wyglądało to jak pusty katalog:
        // kopia wychodziła bez zdjęć, a oryginał i tak był kasowany.
        FakeDocumentsProvider.failListing = { it.name == "assets" }

        val moved = runCatching { store.move(note.path, "Cel") }

        FakeDocumentsProvider.failListing = null
        assertThat(moved.isFailure).isTrue()
        assertThat(File(root, "Cenna.note/content.json").isFile).isTrue()
        assertThat(File(root, "Cenna.note/assets/zdjecie.png").readBytes()).isEqualTo(byteArrayOf(9, 9, 9))
    }

    @Test
    fun `trashing a note whose files cannot be listed keeps the note in place`() {
        val note = store.createNote("", "Też cenna", NoteKind.TEXT)
        FakeDocumentsProvider.failListing = { it.name == "Też cenna.note" }

        val trashed = runCatching { store.moveToTrash(note.path) }

        FakeDocumentsProvider.failListing = null
        assertThat(trashed.isFailure).isTrue()
        assertThat(File(root, "Też cenna.note/content.json").isFile).isTrue()
    }

    @Test
    fun `a bin entry that cannot be looked into is not taken for broken`() {
        val note = store.createNote("", "W koszu", NoteKind.TEXT)
        store.moveToTrash(note.path)
        val slot = store.listTrash().single().id
        FakeDocumentsProvider.failListing = { it.name == slot }

        val broken = store.brokenTrashSlots()

        FakeDocumentsProvider.failListing = null
        assertThat(broken).isEmpty()
        assertThat(store.listTrash()).hasSize(1)
    }

    @Test
    fun `a path that cannot be looked up is unknown, so nothing is deleted`() {
        store.createFolder("", "Szkoła")
        store.createNote("Szkoła", "Notatka", NoteKind.TEXT)
        FakeDocumentsProvider.failListing = { it.name == "Szkoła" }

        val lookup = store.lookup("Szkoła/Notatka.note")
        val deleted = store.deleteEntry("Szkoła/Notatka.note")

        FakeDocumentsProvider.failListing = null
        assertThat(lookup).isEqualTo(PathLookup.Unknown)
        assertThat(deleted).isFalse()
        assertThat(File(root, "Szkoła/Notatka.note/content.json").isFile).isTrue()
    }

    @Test
    fun `a walk with a folder that does not answer says it is incomplete`() {
        store.createFolder("", "A")
        store.createFolder("", "B")
        FakeDocumentsProvider.failListing = { it.name == "B" }

        val complete = store.walk { }

        FakeDocumentsProvider.failListing = null
        assertThat(complete).isFalse()
    }

    @Test
    fun `the whole tree is walked with one question per folder`() {
        store.createFolder("", "A")
        store.createFolder("A", "B")
        store.createFolder("A/B", "C")
        repeat(30) { number -> store.createNote("A/B", "N$number", NoteKind.TEXT) }

        val paths = mutableListOf<String>()
        var complete = false
        val asked = queriesDuring { complete = store.walk { paths += it.path } }

        assertThat(complete).isTrue()
        assertThat(paths).contains("A/B/C")
        assertThat(paths.count { it.startsWith("A/B/N") }).isEqualTo(30)
        assertThat(asked).isAtMost(5)
    }

    @Test
    fun `a branch lists the entry itself and everything under it`() {
        store.createFolder("", "Szkoła")
        store.createFolder("Szkoła", "Fizyka")
        store.createNote("Szkoła/Fizyka", "Ruch", NoteKind.TEXT)
        store.createNote("", "Obok", NoteKind.TEXT)

        val branch = store.branch("Szkoła")!!

        assertThat(branch.map { it.path })
            .containsExactly("Szkoła", "Szkoła/Fizyka", "Szkoła/Fizyka/Ruch.note")
        assertThat(branch.first().type).isEqualTo(ItemType.FOLDER)
    }

    @Test
    fun `a path that is not there is missing, not unknown`() {
        store.createFolder("", "Jest")

        assertThat(store.lookup("Nie ma")).isEqualTo(PathLookup.Missing)
        assertThat(store.lookup("Jest/Nie ma/Głębiej")).isEqualTo(PathLookup.Missing)
        assertThat(store.lookup("Jest")).isInstanceOf(PathLookup.Found::class.java)
    }

    @Test
    fun `deleting the library root by an empty path is refused`() {
        store.createNote("", "Zostaje", NoteKind.TEXT)

        assertThat(store.deleteEntry("")).isFalse()
        assertThat(File(root, "Zostaje.note/content.json").isFile).isTrue()
    }
}
