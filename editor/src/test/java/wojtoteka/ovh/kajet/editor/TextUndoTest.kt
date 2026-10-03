package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent

/*
  Cofanie w notatce tekstowej. Pisanie bez przerwy cofa się jednym krokiem,
  a każde polecenie paska (pogrubienie, lista) osobno - jak Ctrl+Z w Wordzie.
*/
class TextUndoTest {

    private fun note(markdown: String) = NoteDocument(
        id = "n",
        kind = NoteKind.TEXT,
        title = "",
        createdAt = 0L,
        updatedAt = 0L,
        text = TextContent(markdown = markdown),
    )

    private val NoteDocument.markdown: String get() = text?.markdown.orEmpty()

    @Test
    fun `pisanie bez przerwy cofa sie jednym krokiem`() {
        val history = ChangeHistory()
        history.record(TextChange("", "A"), mergeable = true)
        history.record(TextChange("A", "Al"), mergeable = true)
        history.record(TextChange("Al", "Ala"), mergeable = true)

        assertThat(history.undo(note("Ala"))!!.markdown).isEqualTo("")
        assertThat(history.canUndo).isFalse()
    }

    @Test
    fun `polecenie paska jest osobnym krokiem`() {
        val history = ChangeHistory()
        history.record(TextChange("", "Ala"), mergeable = true)
        history.record(TextChange("Ala", "**Ala**"), mergeable = false)
        history.record(TextChange("**Ala**", "**Ala** ma"), mergeable = true)

        var document = note("**Ala** ma")
        document = history.undo(document)!!
        assertThat(document.markdown).isEqualTo("**Ala**")
        document = history.undo(document)!!
        assertThat(document.markdown).isEqualTo("Ala")
        document = history.undo(document)!!
        assertThat(document.markdown).isEqualTo("")
    }

    @Test
    fun `ponow przywraca cofniete`() {
        val history = ChangeHistory()
        history.record(TextChange("Ala", "**Ala**"))

        val undone = history.undo(note("**Ala**"))!!
        assertThat(undone.markdown).isEqualTo("Ala")
        assertThat(history.redo(undone)!!.markdown).isEqualTo("**Ala**")
    }
}
