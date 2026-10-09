package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedLinkBridgeTest {

    private val token = "AbCdEf123_-xyz9"

    @Test
    fun `link do notatki na stronie Kajetu`() {
        val link = SharedLinkBridge.parse("https", "kajet.wojtoteka.ovh", listOf("n", token), null)
        assertThat(link).isEqualTo(SharedLinkBridge.Link(token))
    }

    @Test
    fun `notatka w udostepnionym folderze`() {
        val link = SharedLinkBridge.parse("https", "kajet.wojtoteka.ovh", listOf("n", token, "attachment"), "note-1")
        assertThat(link).isEqualTo(SharedLinkBridge.Link(token, "note-1"))
    }

    @Test
    fun `przycisk otworz w aplikacji`() {
        assertThat(SharedLinkBridge.parse("kajet", "n", listOf(token), "")).isEqualTo(SharedLinkBridge.Link(token))
    }

    @Test
    fun `obce strony i inne sciezki zostaja w przegladarce`() {
        assertThat(SharedLinkBridge.parse("https", "example.com", listOf("n", token), null)).isNull()
        assertThat(SharedLinkBridge.parse("https", "kajet.wojtoteka.ovh", listOf("note", token), null)).isNull()
        assertThat(SharedLinkBridge.parse("https", "kajet.wojtoteka.ovh", listOf("n"), null)).isNull()
        assertThat(SharedLinkBridge.parse("kajet", "auth", listOf(token), null)).isNull()
        assertThat(SharedLinkBridge.parse(null, null, emptyList(), null)).isNull()
    }

    @Test
    fun `token o dziwnej postaci nie przechodzi`() {
        assertThat(SharedLinkBridge.parse("kajet", "n", listOf("krotki"), null)).isNull()
        assertThat(SharedLinkBridge.parse("kajet", "n", listOf("../../etc/passwd"), null)).isNull()
        assertThat(SharedLinkBridge.parse("kajet", "n", listOf("a".repeat(129)), null)).isNull()
    }
}
