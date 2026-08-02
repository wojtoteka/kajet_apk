package wojtoteka.ovh.kajet.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NazwyPlikowTest {

    @Test
    fun `polskie znaki i spacje zostaja bez zmian`() {
        assertThat(NazwyPlikow.bezpieczna("Całki oznaczone")).isEqualTo("Całki oznaczone")
        assertThat(NazwyPlikow.bezpieczna("Żółw i gęś")).isEqualTo("Żółw i gęś")
    }

    @Test
    fun `znaki zabronione zamieniaja sie na podkreslenie`() {
        assertThat(NazwyPlikow.bezpieczna("Fizyka: praca i moc")).isEqualTo("Fizyka_ praca i moc")
        assertThat(NazwyPlikow.bezpieczna("Co to jest?")).isEqualTo("Co to jest_")
        assertThat(NazwyPlikow.bezpieczna("a/b\\c*d|e")).isEqualTo("a_b_c_d_e")
    }

    @Test
    fun `kropka i spacja na koncu znikaja`() {
        assertThat(NazwyPlikow.bezpieczna("Notatka.")).isEqualTo("Notatka")
        assertThat(NazwyPlikow.bezpieczna("Notatka   ")).isEqualTo("Notatka")
        assertThat(NazwyPlikow.bezpieczna("Notatka. . ")).isEqualTo("Notatka")
    }

    @Test
    fun `kropka na poczatku nie robi z folderu ukrytego katalogu`() {
        assertThat(NazwyPlikow.bezpieczna(".trash")).isEqualTo("_trash")
        assertThat(NazwyPlikow.bezpieczna(".ukryty folder")).isEqualTo("_ukryty folder")
    }

    @Test
    fun `nazwy zastrzezone w systemie Windows dostaja przedrostek`() {
        assertThat(NazwyPlikow.bezpieczna("CON")).isEqualTo("_CON")
        assertThat(NazwyPlikow.bezpieczna("com1.txt")).isEqualTo("_com1.txt")
        assertThat(NazwyPlikow.bezpieczna("Concert")).isEqualTo("Concert")
    }

    @Test
    fun `pusta nazwa dostaje nazwe zastepcza`() {
        assertThat(NazwyPlikow.bezpieczna("")).isEqualTo(NazwyPlikow.NAZWA_ZASTEPCZA)
        assertThat(NazwyPlikow.bezpieczna("   ")).isEqualTo(NazwyPlikow.NAZWA_ZASTEPCZA)
        assertThat(NazwyPlikow.bezpieczna("...")).isEqualTo(NazwyPlikow.NAZWA_ZASTEPCZA)
    }

    @Test
    fun `bardzo dluga nazwa jest przycinana`() {
        val dluga = "a".repeat(300)
        val wynik = NazwyPlikow.bezpieczna(dluga)
        assertThat(wynik).hasLength(NazwyPlikow.MAKS_DLUGOSC)
    }

    @Test
    fun `zajeta nazwa dostaje numer przed rozszerzeniem`() {
        val zajete = setOf("Całki.note", "Całki (2).note")
        val wynik = NazwyPlikow.unikalna("Całki", zajete, ".note")
        assertThat(wynik).isEqualTo("Całki (3).note")
    }

    @Test
    fun `wolna nazwa zostaje bez numeru`() {
        assertThat(NazwyPlikow.unikalna("Pochodne", setOf("Całki.note"), ".note"))
            .isEqualTo("Pochodne.note")
    }

    @Test
    fun `wielkosc liter nie tworzy dwoch osobnych plikow`() {
        // Na karcie sformatowanej jako FAT nazwy nie rozrozniaja wielkosci liter.
        val wynik = NazwyPlikow.unikalna("całki", setOf("CAŁKI.note"), ".note")
        assertThat(wynik).isEqualTo("całki (2).note")
    }

    @Test
    fun `rozpoznawanie katalogu notatki`() {
        assertThat(NazwyPlikow.czyNotatka("Całki.note")).isTrue()
        assertThat(NazwyPlikow.czyNotatka("Całki")).isFalse()
        assertThat(NazwyPlikow.bezRozszerzeniaNote("Całki.note")).isEqualTo("Całki")
    }
}
