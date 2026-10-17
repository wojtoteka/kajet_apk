package wojtoteka.ovh.kajet.storage

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.storage.index.IndexDao
import wojtoteka.ovh.kajet.storage.index.IndexDatabase
import wojtoteka.ovh.kajet.storage.index.IndexEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndexDaoTest {

    private lateinit var database: IndexDatabase
    private lateinit var dao: IndexDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), IndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.index()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun note(path: String, id: String = path) = dao.upsert(
        IndexEntry(documentUri = "uri:$path", documentId = id, path = path, name = path, type = "NOTE"),
    )

    private suspend fun folder(path: String) = dao.upsert(
        IndexEntry(documentUri = "uri:$path", path = path, name = path, type = "FOLDER"),
    )

    @Test
    fun `an underscore in a folder name is a plain character, not any character`() = runTest {
        folder("a_b")
        note("a_b/Moja.note", id = "moja")
        folder("axb")
        note("axb/Cudza.note", id = "cudza")

        assertThat(dao.notesUnder("a_b").map { it.documentId }).containsExactly("moja")
    }

    @Test
    fun `a percent sign does not swallow other folders`() = runTest {
        folder("50%")
        note("50%/W środku.note", id = "w-srodku")
        note("500 zadań/Obok.note", id = "obok")

        assertThat(dao.notesUnder("50%").map { it.documentId }).containsExactly("w-srodku")
    }

    @Test
    fun `a branch keeps the letter case of its name`() = runTest {
        folder("Fizyka")
        note("Fizyka/Ruch.note", id = "ruch")
        folder("fizyka")
        note("fizyka/Inna.note", id = "inna")

        dao.deleteBranch("Fizyka")

        assertThat(dao.allEntries().map { it.path }).containsExactly("fizyka", "fizyka/Inna.note")
    }

    @Test
    fun `a branch is the entry and what lies under it, not its namesakes`() = runTest {
        folder("Szkoła")
        folder("Szkoła/Fizyka")
        note("Szkoła/Fizyka/Ruch.note", id = "ruch")
        folder("Szkoła 2")
        note("Szkoła 2/Obok.note", id = "obok")

        assertThat(dao.folderPathsUnder("Szkoła")).containsExactly("Szkoła", "Szkoła/Fizyka")
        assertThat(dao.urisUnder("Szkoła")).hasSize(3)

        dao.deleteBranchWithContent("Szkoła")

        assertThat(dao.allEntries().map { it.path }).containsExactly("Szkoła 2", "Szkoła 2/Obok.note")
    }

    @Test
    fun `a folder from the index holds only what lies directly in it`() = runTest {
        folder("Szkoła")
        folder("Szkoła/Fizyka")
        note("Szkoła/Fizyka/Ruch.note")
        note("Szkoła/Lektury.note")
        note("Luzem.note")
        note("Szkoła 2.note")

        assertThat(dao.childrenOf("").map { it.path }).containsExactly("Szkoła", "Luzem.note", "Szkoła 2.note")
        assertThat(dao.childrenOf("Szkoła").map { it.path }).containsExactly("Szkoła/Fizyka", "Szkoła/Lektury.note")
        assertThat(dao.childrenOf("Szkoła/Fizyka").map { it.path }).containsExactly("Szkoła/Fizyka/Ruch.note")
    }

    @Test
    fun `parent paths work for names with dots, spaces and Polish letters`() = runTest {
        folder("Zażółć gęślą")
        note("Zażółć gęślą/a.b.c (2).note")
        folder("Zażółć gęślą/ąę")
        note("Zażółć gęślą/ąę/ź.note")

        assertThat(dao.childrenOf("Zażółć gęślą").map { it.path })
            .containsExactly("Zażółć gęślą/a.b.c (2).note", "Zażółć gęślą/ąę")
        assertThat(dao.childrenOf("Zażółć gęślą/ąę").map { it.path }).containsExactly("Zażółć gęślą/ąę/ź.note")
    }

    @Test
    fun `child counts match what lies directly in each folder`() = runTest {
        folder("A")
        folder("A/B")
        note("A/B/1.note")
        note("A/B/2.note")
        note("A/3.note")
        folder("Pusty")

        val counts = dao.childCounts(listOf("A", "A/B", "Pusty")).associate { it.parent to it.count }

        assertThat(counts).containsExactly("A", 2, "A/B", 2)
    }
}
