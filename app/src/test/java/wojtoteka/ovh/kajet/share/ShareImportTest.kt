package wojtoteka.ovh.kajet.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareImportTest {

    @Test
    fun `tytul bierze temat albo pierwsza niepusta linie`() {
        assertThat(sharedNoteTitle("Temat", "treść", "Bez tytułu")).isEqualTo("Temat")
        assertThat(sharedNoteTitle(null, "  \nPierwsza\nDruga", "Bez tytułu")).isEqualTo("Pierwsza")
        assertThat(sharedNoteTitle("  ", "   \n", "Bez tytułu")).isEqualTo("Bez tytułu")
    }
}
