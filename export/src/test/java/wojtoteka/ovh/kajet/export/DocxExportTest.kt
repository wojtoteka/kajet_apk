package wojtoteka.ovh.kajet.export

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent

class DocxExportTest {

    private fun documentXml(markdown: String): String {
        val note = NoteDocument(
            id = "n",
            kind = NoteKind.TEXT,
            title = "Notatka",
            createdAt = 0L,
            updatedAt = 0L,
            text = TextContent(markdown = markdown),
        )
        val bytes = ByteArrayOutputStream().also { DocxExport.write(note, it) }.toByteArray()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("brak word/document.xml")
    }

    @Test
    fun `formaty w naglowku, punkcie i cytacie nie zostawiaja gwiazdek`() {
        val xml = documentXml("# Tytul z **grubym**\n\n- punkt *pochyly*\n\n> cytat ==wazny==")

        assertThat(xml).doesNotContain("**")
        assertThat(xml).doesNotContain("==")
        assertThat(xml).contains("<w:pStyle w:val=\"Heading1\"/>")
        assertThat(xml).contains("<w:b/>")
        assertThat(xml).contains("<w:i/>")
    }

    @Test
    fun `naglowek na kawalku zdania jest grubszy i wiekszy`() {
        val xml = documentXml("""Ala <span class="h1">ma</span> kota""")

        assertThat(xml).doesNotContain("span")
        assertThat(xml).contains("""<w:rPr><w:b/><w:sz w:val="34"/></w:rPr><w:t xml:space="preserve">ma</w:t>""")
    }

    @Test
    fun `hashtag bez spacji nie jest naglowkiem`() {
        val xml = documentXml("#zakupy na jutro")

        assertThat(xml).doesNotContain("Heading")
        assertThat(xml).contains("#zakupy na jutro")
    }
}
