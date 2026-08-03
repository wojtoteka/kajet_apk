package wojtoteka.ovh.kajet.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileNamesTest {

    @Test
    fun `polish characters and spaces stay untouched`() {
        assertThat(FileNames.safe("Całki oznaczone")).isEqualTo("Całki oznaczone")
        assertThat(FileNames.safe("Żółw i gęś")).isEqualTo("Żółw i gęś")
    }

    @Test
    fun `forbidden characters turn into an underscore`() {
        assertThat(FileNames.safe("Fizyka: praca i moc")).isEqualTo("Fizyka_ praca i moc")
        assertThat(FileNames.safe("Co to jest?")).isEqualTo("Co to jest_")
        assertThat(FileNames.safe("a/b\\c*d|e")).isEqualTo("a_b_c_d_e")
    }

    @Test
    fun `a trailing dot and space disappear`() {
        assertThat(FileNames.safe("Notatka.")).isEqualTo("Notatka")
        assertThat(FileNames.safe("Notatka   ")).isEqualTo("Notatka")
        assertThat(FileNames.safe("Notatka. . ")).isEqualTo("Notatka")
    }

    @Test
    fun `a leading dot does not turn a folder into a hidden directory`() {
        assertThat(FileNames.safe(".trash")).isEqualTo("_trash")
        assertThat(FileNames.safe(".ukryty folder")).isEqualTo("_ukryty folder")
    }

    @Test
    fun `names reserved on Windows get a prefix`() {
        assertThat(FileNames.safe("CON")).isEqualTo("_CON")
        assertThat(FileNames.safe("com1.txt")).isEqualTo("_com1.txt")
        assertThat(FileNames.safe("Concert")).isEqualTo("Concert")
    }

    @Test
    fun `an empty name gets the fallback name`() {
        assertThat(FileNames.safe("")).isEqualTo(FileNames.FALLBACK_NAME)
        assertThat(FileNames.safe("   ")).isEqualTo(FileNames.FALLBACK_NAME)
        assertThat(FileNames.safe("...")).isEqualTo(FileNames.FALLBACK_NAME)
    }

    @Test
    fun `a very long name is trimmed`() {
        val long = "a".repeat(300)
        val result = FileNames.safe(long)
        assertThat(result).hasLength(FileNames.MAX_LENGTH)
    }

    @Test
    fun `a taken name gets a number before the extension`() {
        val occupied = setOf("Całki.note", "Całki (2).note")
        val result = FileNames.unique("Całki", occupied, ".note")
        assertThat(result).isEqualTo("Całki (3).note")
    }

    @Test
    fun `a free name stays without a number`() {
        assertThat(FileNames.unique("Pochodne", setOf("Całki.note"), ".note"))
            .isEqualTo("Pochodne.note")
    }

    @Test
    fun `letter case does not create two separate files`() {
        // On a card formatted as FAT names do not distinguish letter case.
        val result = FileNames.unique("całki", setOf("CAŁKI.note"), ".note")
        assertThat(result).isEqualTo("całki (2).note")
    }

    @Test
    fun `recognising a note directory`() {
        assertThat(FileNames.isNote("Całki.note")).isTrue()
        assertThat(FileNames.isNote("Całki")).isFalse()
        assertThat(FileNames.withoutNoteExtension("Całki.note")).isEqualTo("Całki")
    }
}
