package wojtoteka.ovh.kajet.share

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIncomingTest {

    @After
    fun reset() {
        ShareIncoming.clear()
    }

    @Test
    fun `SEND z tekstem daje tresc bez pliku`() {
        val parsed = parse(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Witaj w Kajecie")
                putExtra(Intent.EXTRA_SUBJECT, "Temat")
            },
        )
        assertThat(parsed).isEqualTo(
            IncomingShare("Witaj w Kajecie", "Temat", emptyList(), "text/plain"),
        )
    }

    @Test
    fun `SEND ze strumieniem daje jeden URI`() {
        val uri = Uri.parse("content://com.example.files/photo.jpg")
        val parsed = parse(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
            },
        )
        assertThat(parsed?.uris).containsExactly(uri)
        assertThat(parsed?.mime).isEqualTo("image/jpeg")
    }

    @Test
    fun `SEND_MULTIPLE zbiera wszystkie URI`() {
        val first = Uri.parse("content://com.example.files/a.pdf")
        val second = Uri.parse("content://com.example.files/b.pdf")
        val parsed = parse(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(first, second),
                )
            },
        )
        assertThat(parsed?.uris).containsExactly(first, second).inOrder()
    }

    @Test
    fun `VIEW z content otwiera ten plik`() {
        val uri = Uri.parse("content://com.example.files/notes.txt")
        val parsed = parse(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
            },
        )
        assertThat(parsed?.uris).containsExactly(uri)
        assertThat(parsed?.text).isNull()
    }

    @Test
    fun `VIEW kajet auth nie jest udostepnieniem`() {
        val parsed = parse(
            Intent(Intent.ACTION_VIEW, Uri.parse("kajet://auth?code=abc")),
        )
        assertThat(parsed).isNull()
    }

    @Test
    fun `puste SEND i zwykly MAIN nic nie daja`() {
        assertThat(parse(Intent(Intent.ACTION_SEND).apply { type = "text/plain" })).isNull()
        assertThat(parse(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(parse(null)).isNull()
    }

    @Test
    fun `porazka zostawia oczekujace udostepnienie do kolejnej proby`() {
        ShareIncoming.offer(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "jeszcze raz")
            },
        )
        val first = ShareIncoming.tryBegin()
        assertThat(first?.text).isEqualTo("jeszcze raz")
        assertThat(ShareIncoming.tryBegin()).isNull()

        ShareIncoming.finish(success = false)

        assertThat(ShareIncoming.pending.value).isEqualTo(first)
        assertThat(ShareIncoming.tryBegin()).isEqualTo(first)
        ShareIncoming.finish(success = true)
        assertThat(ShareIncoming.pending.value).isNull()
    }
}
