package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextContentTest {

    @Test
    fun `zero zostaje zerem, bo to wielkosc z motywu`() {
        assertThat(TextContent.storedFontSize(0f)).isEqualTo(0f)
        assertThat(TextContent.storedFontSize(-1f)).isEqualTo(0f)
    }

    @Test
    fun `wybrana wielkosc zostaje w 10-48`() {
        assertThat(TextContent.storedFontSize(10f)).isEqualTo(10f)
        assertThat(TextContent.storedFontSize(17f)).isEqualTo(17f)
        assertThat(TextContent.storedFontSize(48f)).isEqualTo(48f)
        assertThat(TextContent.storedFontSize(9f)).isEqualTo(10f)
        assertThat(TextContent.storedFontSize(49f)).isEqualTo(48f)
    }
}
