package wojtoteka.ovh.kajet.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pasek wgrywania mieści się na ekranie telefonu.
 *
 * Serwer odmawia całym zdaniem („Kajet nie obsługuje tego rozszerzenia.
 * Wybierz plik tekstowy albo z kodem obsługiwany przez aplikację."), a na
 * telefonie taki pasek zajmował pół ekranu. Znane kody odmowy mają więc
 * własne, krótkie zakończenie zdania.
 */
class UploadNoticeTest {

    private val serverRefusal =
        "Kajet nie obsługuje tego rozszerzenia. Wybierz plik tekstowy albo z kodem " +
            "obsługiwany przez aplikację."

    @Test
    fun `odmowa po rozszerzeniu miesci sie w jednym wierszu`() {
        val notice = PolishStrings.uploadStatusText(
            name = "zdjęcie.jpg",
            status = "FAILED_PERMANENT",
            progress = 0,
            error = serverRefusal,
            errorCode = "unsupported-extension",
        )

        assertThat(notice).isEqualTo("zdjęcie.jpg — to nie plik tekstowy ani z kodem")
        assertThat(notice.length).isLessThan(serverRefusal.length)
    }

    @Test
    fun `nieznany powod skraca sie do pierwszego zdania`() {
        val notice = PolishStrings.uploadStatusText(
            name = "notatki.txt",
            status = "FAILED_PERMANENT",
            progress = 0,
            error = serverRefusal,
            errorCode = "cos-nowego-na-serwerze",
        )

        assertThat(notice).isEqualTo("notatki.txt — Kajet nie obsługuje tego rozszerzenia.")
    }

    @Test
    fun `jedno bardzo dlugie zdanie ma koniec z wielokropkiem`() {
        val long = "Coś poszło nie tak, " + "i tak dalej ".repeat(30)
        val notice = PolishStrings.uploadStatusText(
            name = "plik.py",
            status = "FAILED_PERMANENT",
            progress = 0,
            error = long,
            errorCode = null,
        )

        assertThat(notice).endsWith("…")
        assertThat(notice.length).isAtMost("plik.py — ".length + 120)
    }

    @Test
    fun `brak powodu nie zostawia samego mysnika`() {
        val notice = PolishStrings.uploadStatusText(
            name = "plik.py",
            status = "FAILED_RETRYABLE",
            progress = 0,
            error = "   ",
            errorCode = null,
        )

        assertThat(notice).isEqualTo("plik.py — wysyłanie się nie udało, Kajet spróbuje jeszcze raz")
    }

    @Test
    fun `english notice keeps the same short shape`() {
        val notice = EnglishStrings.uploadStatusText(
            name = "photo.jpg",
            status = "FAILED_PERMANENT",
            progress = 0,
            error = "Kajet does not support that file extension. Choose a text file.",
            errorCode = "unsupported-extension",
        )

        assertThat(notice).isEqualTo("photo.jpg — not a text or source-code file")
    }

    @Test
    fun `odmowa przy wybieraniu pliku to jedno krotkie zdanie`() {
        val about = PolishStrings.uploadNotTextAbout("wakacje.jpg")

        assertThat(about).contains("wakacje.jpg")
        assertThat(about).isEqualTo("„wakacje.jpg” to nie plik tekstowy ani z kodem.")
    }

    @Test
    fun `wgrany plik nadal mowi wprost, ze sie udalo`() {
        assertThat(
            PolishStrings.uploadStatusText("plik.py", "SYNCED", 100, null, null),
        ).isEqualTo("plik.py — wgrany pomyślnie")
        assertThat(
            PolishStrings.uploadStatusText("plik.py", "UPLOADING", 40, null, null),
        ).isEqualTo("plik.py — wysyłanie (40%)")
    }
}
