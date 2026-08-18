package wojtoteka.ovh.kajet.share

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem

class ShareImportTest {

    @Test
    fun `tytul bierze temat albo pierwsza niepusta linie`() {
        assertThat(sharedNoteTitle("Temat", "treść", "Bez tytułu")).isEqualTo("Temat")
        assertThat(sharedNoteTitle(null, "  \nPierwsza\nDruga", "Bez tytułu")).isEqualTo("Pierwsza")
        assertThat(sharedNoteTitle("  ", "   \n", "Bez tytułu")).isEqualTo("Bez tytułu")
    }

    @Test
    fun `czesciowa porazka zwraca pierwszy sukces i liczbe nieudanych`() = runBlocking {
        val ok = fakeItem("udany.txt")
        val outcome = importEach(listOf("zły", "udany", "gorszy")) { name ->
            if (name == "udany") ok else error("nie da się wziąć")
        }
        assertThat(outcome.item).isEqualTo(ok)
        assertThat(outcome.failed).isEqualTo(2)
    }

    @Test
    fun `pierwszy sukces przy kolejnej porazce nadal otwiera ten plik`() = runBlocking {
        val first = fakeItem("pierwszy.pdf")
        val outcome = importEach(listOf("pierwszy", "zły")) { name ->
            if (name == "pierwszy") first else error("drugi nie wszedł")
        }
        assertThat(outcome.item).isEqualTo(first)
        assertThat(outcome.failed).isEqualTo(1)
    }

    @Test
    fun `same porazki rzucaja z komunikatem ostatniego bledu`() {
        val thrown = assertThrows(IOException::class.java) {
            runBlocking {
                importEach(listOf("a", "b")) { error("brak dostępu") }
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo("brak dostępu")
    }
}

private fun fakeItem(name: String) = LibraryItem(
    id = name,
    path = name,
    name = name,
    type = ItemType.OTHER_FILE,
    documentUri = "content://kajet/test/$name",
)
