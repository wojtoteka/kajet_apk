package wojtoteka.ovh.kajet.code

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.CodeLanguage

class CodeFileLimitsTest {
    @Test
    fun `plain text has the stricter editor limit`() {
        assertThat(CodeFileLimits.editorBytes(CodeLanguage.PLAIN_TEXT))
            .isEqualTo(250 * 1024)
        assertThat(CodeFileLimits.editorBytes(CodeLanguage.PYTHON))
            .isEqualTo(400 * 1024)
    }

    @Test
    fun `syntax highlighting stops above its limit`() {
        assertThat(CodeFileLimits.shouldHighlight(CodeFileLimits.SYNTAX_HIGHLIGHT_CHARS)).isTrue()
        assertThat(CodeFileLimits.shouldHighlight(CodeFileLimits.SYNTAX_HIGHLIGHT_CHARS + 1)).isFalse()
    }

    @Test
    fun `line numbers stop above the display limit`() {
        assertThat(CodeFileLimits.shouldShowLineNumbers(CodeFileLimits.LINE_NUMBER_LIMIT)).isTrue()
        assertThat(CodeFileLimits.shouldShowLineNumbers(CodeFileLimits.LINE_NUMBER_LIMIT + 1)).isFalse()
    }
}
