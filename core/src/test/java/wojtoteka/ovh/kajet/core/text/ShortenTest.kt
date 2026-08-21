package wojtoteka.ovh.kajet.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShortenTest {

    @Test
    fun `krotki tekst zostaje w calosci`() {
        assertThat(shortenMiddle("ala@poczta.pl", 40)).isEqualTo("ala@poczta.pl")
    }

    @Test
    fun `dlugi adres traci srodek, nie koncowke`() {
        val address = "bardzo.dlugi.adres.pocztowy@bardzodlugadomena.przyklad.pl"
        val short = shortenMiddle(address, 30)

        assertThat(short.length).isAtMost(30)
        assertThat(short).contains("…")
        // Początek i domena zostają - po nich poznaje się, czyj to adres.
        assertThat(short).startsWith("bardzo.dlugi.adre")
        assertThat(short).endsWith("przyklad.pl")
    }

    @Test
    fun `granica rowna dlugosci niczego nie ucina`() {
        val text = "dokladnie-dwadziescia"
        assertThat(shortenMiddle(text, text.length)).isEqualTo(text)
    }

    @Test
    fun `smiesznie mala granica nie wywraca skracania`() {
        // Poniżej pięciu znaków nie ma czego dzielić na głowę i ogon -
        // oddajemy tekst, jaki jest, zamiast liczyć ujemne długości.
        assertThat(shortenMiddle("abcdefgh", 3)).isEqualTo("abcdefgh")
    }
}
