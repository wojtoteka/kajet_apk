package wojtoteka.ovh.kajet.editor.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.TextContent

/*
  Edytor notatki tekstowej na prawdziwym polu Compose: klawiatura, kursor,
  mapowanie ukrytych znaczników i bloki. To, co widać w polu, czytamy z
  semantyki - dokładnie ten tekst widzi człowiek na ekranie.

  Pasek narzędzi jest tu odtworzony tak, jak robi to TextEditor w widoku
  blokowym (format(), insertCode(), alignParagraphs()).
*/
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val centre = """<p style="text-align:center">"""

    /** Stan edytora - ten sam, który trzyma TextEditor. */
    private inner class Editor(markdown: String) {
        var blocks by mutableStateOf(Blocks.split(markdown))
        var keyToFocus by mutableStateOf<String?>(null)
        var focusedKey: String? = null
        var focusedField = TextFieldValue()
        var setField: ((TextFieldValue) -> Unit)? = null
        var pending = PendingFormat()

        val markdown: String get() = Blocks.join(blocks)

        fun format(transform: (TextFieldValue) -> TextFieldValue?) {
            val key = focusedKey ?: return
            val set = setField ?: return
            val block = blocks.firstOrNull { it.key == key }
            if (block !is Block.Text && block !is Block.Task) return
            val next = transform(focusedField) ?: return
            focusedField = next
            set(next)
            blocks = Blocks.setText(blocks, key, next.text)
        }

        fun insertCode(fence: String) {
            val key = focusedKey
            val cursor = if (blocks.firstOrNull { it.key == key } is Block.Text) focusedField.selection.start else 0
            val split = Blocks.insertCode(blocks, key, cursor, fence)
            blocks = split.blocks
            keyToFocus = split.focusKey
        }

        fun align(align: NoteAlign) = format { TextFormat.alignLines(it, align, NoteAlign.LEFT) }

        fun heading(marker: String) = format { TextFormat.beforeLine(it, marker) }

        @Composable
        fun Content() {
            KajetTheme(darkTheme = false) {
                BlockEditor(
                    blocks = blocks,
                    attachment = { null },
                    attachmentRevision = 0,
                    isDrawing = { false },
                    onEditDrawing = {},
                    onBlocksChange = { blocks = it },
                    onTapBelow = {},
                    selectedPhoto = null,
                    onSelectPhoto = {},
                    keyToFocus = keyToFocus,
                    onFocusTaken = { keyToFocus = null },
                    onBlockFocused = { key, set ->
                        focusedKey = key
                        setField = set
                    },
                    onFocusBlock = { keyToFocus = it },
                    onSelection = { focusedField = it },
                    onTyped = { previous, typed ->
                        if (previous.text == typed.text) {
                            if (!pending.isEmpty) pending = PendingFormat()
                            null
                        } else {
                            val outcome = TextEdit.typed(previous, typed, pending)
                            pending = outcome.pending
                            outcome.field
                        }
                    },
                    appearance = TextContent(),
                )
            }
        }
    }

    private fun open(markdown: String): Editor {
        val editor = Editor(markdown)
        rule.setContent { editor.Content() }
        rule.waitForIdle()
        return editor
    }

    private fun field(index: Int = 0): SemanticsNodeInteraction =
        rule.onAllNodes(hasSetTextAction())[index]

    private fun shown(index: Int = 0): String =
        field(index).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun fieldCount(): Int = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    /** Kursor na widocznej pozycji - tak jak po stuknięciu palcem. */
    private fun cursorAt(index: Int, visible: Int) {
        field(index).performClick()
        // Pozycja w tym, co WIDAĆ - Compose przelicza ją na zapis tym samym
        // mapowaniem, co przy stuknięciu palcem.
        field(index).performSemanticsAction(SemanticsActions.SetSelection) { it(visible, visible, false) }
        rule.waitForIdle()
    }

    private fun backspace(index: Int = 0) {
        field(index).performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
    }

    private fun enter(index: Int = 0) {
        field(index).performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
    }

    private fun type(text: String, index: Int = 0) {
        field(index).performTextInput(text)
        rule.waitForIdle()
    }

    // --- Blok kodu ---

    @Test
    fun `wstawiony blok kodu to osobne pole bez plotow`() {
        val editor = open("Ala ma kota")
        cursorAt(0, 11)
        rule.runOnIdle { editor.insertCode(Block.CODE_FENCE) }
        rule.waitForIdle()

        // Akapit, pole kodu i miejsce na dalsze pisanie pod nim.
        assertThat(fieldCount()).isEqualTo(3)
        type("print(1)", index = 1)
        enter(index = 1)
        type("print(2)", index = 1)

        assertThat(editor.markdown).isEqualTo("Ala ma kota\n\n```\nprint(1)\nprint(2)\n```")
        assertThat(shown(0)).isEqualTo("Ala ma kota")
        assertThat(shown(1)).isEqualTo("print(1)\nprint(2)")
        for (i in 0 until fieldCount()) assertThat(shown(i)).doesNotContain("```")
    }

    @Test
    fun `backspace na poczatku kodu nie rozbija bloku`() {
        val editor = open("Ala\n\n```\nprint(1)\n```")
        cursorAt(1, 0)
        backspace(index = 1)
        backspace(index = 1)

        assertThat(editor.markdown).isEqualTo("Ala\n\n```\nprint(1)\n```")
        assertThat(shown(1)).isEqualTo("print(1)")
    }

    @Test
    fun `wzor tez jest osobnym blokiem`() {
        val editor = open("")
        cursorAt(0, 0)
        rule.runOnIdle { editor.insertCode(Block.FORMULA_FENCE) }
        rule.waitForIdle()
        type("x^2", index = 0)

        assertThat(editor.markdown).isEqualTo("$$\nx^2\n$$")
    }

    @Test
    fun `stara notatka z kodem w akapicie otwiera sie z blokiem kodu`() {
        val editor = open("przed\n```python\nprint(1)\n```\npo")

        assertThat(editor.blocks.map { it::class.simpleName })
            .containsExactly("Text", "Code", "Text").inOrder()
        assertThat(shown(1)).isEqualTo("print(1)")
        assertThat(shown(0)).isEqualTo("przed")
        assertThat(shown(2)).isEqualTo("po")
    }

    // --- Nagłówki ---

    @Test
    fun `naglowek i backspace na jego poczatku nie pokazuje kratek`() {
        val editor = open("Ala\nTytul")
        cursorAt(0, 6)
        rule.runOnIdle { editor.heading("# ") }
        rule.waitForIdle()
        assertThat(editor.markdown).isEqualTo("Ala\n# Tytul")
        assertThat(shown()).isEqualTo("Ala\nTytul")

        cursorAt(0, 4)
        backspace()

        assertThat(shown()).isEqualTo("AlaTytul")
        assertThat(editor.markdown).isEqualTo("AlaTytul")
    }

    @Test
    fun `pisanie w naglowku i enter dziala jak w edytorze tekstu`() {
        val editor = open("# Tytul")
        cursorAt(0, 5)
        type(" notatki")
        enter()
        type("Pierwszy akapit")

        assertThat(editor.markdown).isEqualTo("# Tytul notatki\nPierwszy akapit")
        assertThat(shown()).isEqualTo("Tytul notatki\nPierwszy akapit")
    }

    // --- Ułożenie akapitu ---

    @Test
    fun `srodek dotyczy tylko jednego akapitu`() {
        val editor = open("Pierwszy\nDrugi\nTrzeci")
        cursorAt(0, 12)
        rule.runOnIdle { editor.align(NoteAlign.CENTER) }
        rule.waitForIdle()

        assertThat(editor.markdown).isEqualTo("Pierwszy\n${centre}Drugi</p>\nTrzeci")
        assertThat(shown()).isEqualTo("Pierwszy\nDrugi\nTrzeci")
    }

    @Test
    fun `pisanie i enter w wysrodkowanym akapicie`() {
        val editor = open("${centre}Ala</p>")
        cursorAt(0, 3)
        type(" ma kota")
        enter()
        type("i psa")

        assertThat(editor.markdown).isEqualTo("${centre}Ala ma kota</p>\n${centre}i psa</p>")
        assertThat(shown()).isEqualTo("Ala ma kota\ni psa")
        assertThat(shown()).doesNotContain("<")
    }

    @Test
    fun `kasowanie wysrodkowanego tekstu do zera nie zostawia znacznika`() {
        val editor = open("Ala\n${centre}kot</p>")
        cursorAt(0, 7)
        // „kot", pusty wysrodkowany akapit, koniec wiersza i „a".
        repeat(3) { backspace() }
        assertThat(shown()).isEqualTo("Ala\n")
        assertThat(editor.markdown).isEqualTo("Ala\n$centre</p>")
        repeat(2) { backspace() }

        assertThat(shown()).isEqualTo("Al")
        assertThat(editor.markdown).isEqualTo("Al")
    }

    // --- Formaty ---

    @Test
    fun `pogrubienie z paska, pisanie i backspace nie pokazuja gwiazdek`() {
        val editor = open("Ala ")
        cursorAt(0, 4)
        rule.runOnIdle {
            editor.pending = editor.pending.with(SpanType.BOLD)
        }
        type("kot")
        backspace()
        backspace()
        type("ot")

        assertThat(editor.markdown).isEqualTo("Ala **kot**")
        assertThat(shown()).isEqualTo("Ala kot")
    }

    // --- Listy ---

    @Test
    fun `lista pokazuje kropke, enter ciagnie liste, pusty enter ja konczy`() {
        val editor = open("- mleko")
        assertThat(shown()).isEqualTo("\u2022 mleko")

        cursorAt(0, 7)
        enter()
        type("chleb")
        enter()
        enter()
        type("koniec")

        assertThat(editor.markdown).isEqualTo("- mleko\n- chleb\nkoniec")
        assertThat(shown()).isEqualTo("\u2022 mleko\n\u2022 chleb\nkoniec")
    }

    // --- Blok kodu w środku tekstu ---

    @Test
    fun `kod wstawiony w srodek tekstu staje pod wierszem z kursorem`() {
        val editor = open("pierwszy\ndrugi\ntrzeci")
        cursorAt(0, 12)
        rule.runOnIdle { editor.insertCode(Block.CODE_FENCE) }
        rule.waitForIdle()
        type("x = 1", index = 1)

        assertThat(editor.markdown).isEqualTo("pierwszy\ndrugi\n\n```\nx = 1\n```\n\ntrzeci")
        assertThat(shown(0)).isEqualTo("pierwszy\ndrugi")
        assertThat(shown(2)).isEqualTo("trzeci")
    }

    @Test
    fun `kosz przy bloku kodu usuwa caly blok`() {
        val editor = open("Ala\n\n```\nprint(1)\n```\n\nkot")
        rule.onNodeWithContentDescription("Usuń blok kodu").performClick()
        rule.waitForIdle()

        assertThat(editor.markdown).isEqualTo("Ala\n\nkot")
        assertThat(editor.blocks.none { it is Block.Code }).isTrue()
    }

    // --- Kolor i pisanie dalej ---

    @Test
    fun `backspace za kolorowym slowem nie pokazuje znacznika`() {
        val editor = open("""Ala <span style="color:#c81e1e">ma</span> kota""")
        cursorAt(0, 6)
        backspace()
        backspace()

        assertThat(shown()).isEqualTo("Ala  kota")
        assertThat(editor.markdown).isEqualTo("Ala  kota")
    }
}
