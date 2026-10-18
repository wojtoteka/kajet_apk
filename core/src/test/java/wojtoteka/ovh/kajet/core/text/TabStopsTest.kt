package wojtoteka.ovh.kajet.core.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabStopsTest {

    @Test
    fun `tekst bez tabulatora zostaje bez zmian`() {
        val result = TabStops().filter(AnnotatedString("zwykły tekst"))
        assertThat(result.text.text).isEqualTo("zwykły tekst")
        assertThat(result.offsetMapping.originalToTransformed(3)).isEqualTo(3)
    }

    @Test
    fun `tabulator zajmuje cztery spacje`() {
        val result = TabStops().filter(AnnotatedString("ab\tc"))
        assertThat(result.text.text).isEqualTo("ab    c")
        assertThat(result.offsetMapping.originalToTransformed(3)).isEqualTo(6)
        assertThat(result.offsetMapping.transformedToOriginal(6)).isEqualTo(3)
    }

    @Test
    fun `w kodzie tabulator dociaga do kolumny`() {
        val result = TabStops(columns = true).filter(AnnotatedString("ab\tc\n\td"))
        assertThat(result.text.text).isEqualTo("ab  c\n    d")
    }

    @Test
    fun `stukniecie w srodek tabulatora stawia kursor przy blizszej krawedzi`() {
        val mapping = TabStops().filter(AnnotatedString("a\tb")).offsetMapping
        assertThat(mapping.transformedToOriginal(2)).isEqualTo(1)
        assertThat(mapping.transformedToOriginal(4)).isEqualTo(2)
        assertThat(mapping.transformedToOriginal(6)).isEqualTo(3)
    }

    @Test
    fun `style przesuwaja sie razem z tekstem`() {
        val source = AnnotatedString.Builder("\tgrube").apply {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 1, 6)
        }.toAnnotatedString()
        val result = TabStops().filter(source)
        val span = result.text.spanStyles.single()
        assertThat(span.start).isEqualTo(4)
        assertThat(span.end).isEqualTo(9)
    }

    @Test
    fun `mapowanie nie wychodzi poza tekst`() {
        val source = "\t\ta\n\tb\t"
        val result = TabStops(columns = true).filter(AnnotatedString(source))
        for (offset in 0..source.length) {
            assertThat(result.offsetMapping.originalToTransformed(offset)).isIn(0..result.text.length)
        }
        for (offset in 0..result.text.length) {
            assertThat(result.offsetMapping.transformedToOriginal(offset)).isIn(0..source.length)
        }
    }
}
