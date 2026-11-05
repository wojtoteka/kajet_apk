package wojtoteka.ovh.kajet.editor.text

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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.core.model.TextContent

/*
  Polecenie pisane w notatce na prawdziwym polu Compose: pole odrzuca to,
  co przyszło z klawiatury, i na ekranie nie może zostać ani litera
  polecenia. onTyped złożone tak jak w TextEditor - najpierw polecenie,
  potem zwykłe pisanie (TextEdit).
*/
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InlinePromptUiTest {

    @get:Rule
    val rule = createComposeRule()

    private var blocks by mutableStateOf(emptyList<Block>())
    private val prompt = InlinePrompt()
    private val sent = mutableListOf<String>()

    private val markdown: String get() = Blocks.join(blocks)

    private fun open(text: String) {
        blocks = Blocks.split(text)
        rule.setContent {
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
                    keyToFocus = null,
                    onFocusTaken = {},
                    onBlockFocused = { _, _ -> },
                    onFocusBlock = {},
                    onSelection = {},
                    onTyped = { previous, typed ->
                        when (val step = prompt.onTyped(previous, typed)) {
                            InlinePrompt.Step.Pass -> if (previous.text == typed.text) {
                                null
                            } else {
                                TextEdit.typed(previous, typed, PendingFormat()).field
                            }
                            is InlinePrompt.Step.Keep -> step.field
                            is InlinePrompt.Step.Send -> {
                                sent += step.prompt
                                step.field
                            }
                        }
                    },
                    appearance = TextContent(),
                )
            }
        }
        rule.waitForIdle()
        // Kursor na końcu tekstu, jak po stuknięciu za ostatnim słowem.
        val end = shown().length
        field().performClick()
        field().performSemanticsAction(SemanticsActions.SetSelection) { it(end, end, false) }
        rule.waitForIdle()
    }

    private fun field(): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction())[0]

    private fun shown(): String = field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun type(text: String) {
        field().performTextInput(text)
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        field().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun `polecenie nie pokazuje się w polu i wysyła się Enterem`() {
        open("Zadanie: 17*23 = ")
        type(";")
        assertThat(shown()).isEqualTo("Zadanie: 17*23 = ;")
        type(";")
        assertThat(shown()).isEqualTo("Zadanie: 17*23 = ")

        type("oblicz")
        type(" to")
        assertThat(shown()).isEqualTo("Zadanie: 17*23 = ")
        assertThat(markdown).isEqualTo("Zadanie: 17*23 = ")

        press(Key.Enter)
        assertThat(sent).containsExactly("oblicz to")
        assertThat(shown()).isEqualTo("Zadanie: 17*23 = ")
        assertThat(markdown).isEqualTo("Zadanie: 17*23 = ")

        // Dalej pisze się normalnie, w tym samym miejscu.
        type("391")
        assertThat(shown()).isEqualTo("Zadanie: 17*23 = 391")
    }

    @Test
    fun `Backspace z klawiatury kasuje polecenie, a nie notatkę`() {
        open("Ala ma kota")
        type(";")
        type(";")
        type("xy")
        press(Key.Backspace)
        assertThat(shown()).isEqualTo("Ala ma kota")
        press(Key.Enter)
        assertThat(sent).containsExactly("x")
        assertThat(shown()).isEqualTo("Ala ma kota")
    }
}
