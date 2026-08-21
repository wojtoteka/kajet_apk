package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OtherFileTest {

    @Test
    fun `zdjecia otwieraja sie w podgladzie a nie jako tekst`() {
        assertThat(OtherFileKind.of("zdjecie.jpg")).isEqualTo(OtherFileKind.IMAGE)
        assertThat(OtherFileKind.of("ZDJECIE.JPEG")).isEqualTo(OtherFileKind.IMAGE)
        assertThat(OtherFileKind.of("rysunek.png")).isEqualTo(OtherFileKind.IMAGE)
        assertThat(OtherFileKind.of("animacja.gif")).isEqualTo(OtherFileKind.IMAGE)
        assertThat(OtherFileKind.of("okladka.webp")).isEqualTo(OtherFileKind.IMAGE)
    }

    @Test
    fun `pdf nie idzie do edytora kodu`() {
        assertThat(OtherFileKind.of("skrypt.pdf")).isEqualTo(OtherFileKind.PDF)
        assertThat(OtherFileKind.of("SKRYPT.PDF")).isEqualTo(OtherFileKind.PDF)
    }

    @Test
    fun `znany tekst bez wlasnego jezyka zostaje tekstem`() {
        assertThat(OtherFileKind.of("notatki.md")).isEqualTo(OtherFileKind.TEXT)
        assertThat(OtherFileKind.of("dane.json")).isEqualTo(OtherFileKind.TEXT)
        assertThat(OtherFileKind.of("ustawienia.yaml")).isEqualTo(OtherFileKind.TEXT)
        // .txt jest PLAIN_TEXT w spisie języków - tu też tekst, nie binarka.
        assertThat(OtherFileKind.of("dziennik.txt")).isEqualTo(OtherFileKind.TEXT)
        assertThat(OtherFileKind.of("program.py")).isEqualTo(OtherFileKind.TEXT)
    }

    @Test
    fun `nieznana binarka nie udaje tekstu`() {
        assertThat(OtherFileKind.of("archiwum.zip")).isEqualTo(OtherFileKind.BINARY)
        assertThat(OtherFileKind.of("film.mp4")).isEqualTo(OtherFileKind.BINARY)
        assertThat(OtherFileKind.of("praca.docx")).isEqualTo(OtherFileKind.BINARY)
        assertThat(OtherFileKind.of("bez-rozszerzenia")).isEqualTo(OtherFileKind.BINARY)
        assertThat(OtherFileKind.of("")).isEqualTo(OtherFileKind.BINARY)
    }
}
