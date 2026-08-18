package wojtoteka.ovh.kajet.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.storage.index.IndexText

/**
 * Podgląd notatki w spisie ma nieść treść, a nie znaczniki.
 *
 * Zgłoszenie z sierpnia 2026: na liście notatek stało
 * „Prompt do generatora obrazów:** A surreal, high-deta...". Znaczniki
 * zdejmowane były PO obcięciu znaków z początku wiersza, więc gwiazdki
 * otwierające znikały, a domykające zostawały.
 */
class IndexTextTest {

    private fun noteOf(markdown: String) = NoteDocument(
        id = "1",
        kind = NoteKind.TEXT,
        title = "Notatka",
        createdAt = 0L,
        updatedAt = 0L,
        text = TextContent(markdown = markdown),
    )

    private fun previewOf(markdown: String) = IndexText.preview(noteOf(markdown))

    @Test
    fun `pogrubienie na samym poczatku znika razem z gwiazdkami`() {
        val preview = previewOf("**Prompt do generatora obrazów:** A surreal, high-detail scene")

        assertEquals("Prompt do generatora obrazów: A surreal, high-detail scene", preview)
        assertFalse(preview.contains("*"))
    }

    @Test
    fun `kursywa na poczatku wiersza tez schodzi`() {
        assertEquals("ważne zdanie", previewOf("*ważne zdanie*"))
    }

    @Test
    fun `myslnik listy schodzi, a pogrubienie w srodku sie rozwija`() {
        assertEquals("ważne rzeczy", previewOf("- **ważne** rzeczy"))
    }

    @Test
    fun `zadanie do zrobienia zostaje sama trescia`() {
        assertEquals("kupić chleb", previewOf("- [ ] kupić chleb"))
    }

    @Test
    fun `naglowek i cytat schodza`() {
        assertEquals("Tytuł Cytat", previewOf("# Tytuł\n> Cytat"))
    }

    @Test
    fun `linia pozioma i plot bloku kodu nie wchodza do podgladu`() {
        assertEquals("Przed Po", previewOf("Przed\n---\n```python\nPo"))
    }

    @Test
    fun `gwiazdka bez pary zostaje trescia, nie znika w polowie`() {
        // 2 * 3 to mnożenie, a nie początek kursywy — treść ma przeżyć w całości.
        assertEquals("wynik 2 * 3", previewOf("wynik 2 * 3"))
    }
}
