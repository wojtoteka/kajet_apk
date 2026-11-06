package wojtoteka.ovh.kajet.editor.handwriting

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.design.KajetTheme

/*
  Pasek zaznaczenia w lewym dolnym rogu kartki. Z zaznaczeniem ma Wytnij,
  Kopiuj, Wklej i Usuń; po wycięciu, bez ramki - samo Wklej.
*/
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val clicked = mutableListOf<String>()

    private fun show(count: Int, clipboardCount: Int, canEdit: Boolean = true) {
        rule.setContent {
            KajetTheme(darkTheme = false) {
                SelectionPanel(
                    count = count,
                    clipboardCount = clipboardCount,
                    modifier = Modifier,
                    onCut = { clicked += "wytnij" },
                    onCopy = { clicked += "kopiuj" },
                    onPaste = { clicked += "wklej" },
                    onDelete = { clicked += "usun" },
                    onDeselect = { clicked += "odznacz" },
                    onClearClipboard = { clicked += "oproznij" },
                    canEdit = canEdit,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1200dp")
    fun `na tablecie akcje maja podpisy i kazda robi swoje`() {
        show(count = 11, clipboardCount = 3)

        rule.onNodeWithText("Zaznaczono 11 kresek").assertExists()
        listOf("Wytnij", "Kopiuj", "Wklej", "Usuń").forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithContentDescription("Odznacz").performClick()

        assertThat(clicked).containsExactly("wytnij", "kopiuj", "wklej", "usun", "odznacz").inOrder()
    }

    @Test
    fun `na telefonie same ikony, ale z opisem dla czytnika`() {
        show(count = 2, clipboardCount = 0)

        rule.onNodeWithText("Zaznaczono 2 kreski").assertExists()
        rule.onNodeWithText("Wytnij").assertDoesNotExist()
        rule.onNodeWithContentDescription("Wytnij").performClick()
        rule.onNodeWithContentDescription("Kopiuj").performClick()
        rule.onNodeWithContentDescription("Usuń").performClick()
        // Pusty schowek nie ma czego wkleić.
        rule.onNodeWithContentDescription("Wklej").assertDoesNotExist()

        assertThat(clicked).containsExactly("wytnij", "kopiuj", "usun").inOrder()
    }

    @Test
    @Config(qualifiers = "w800dp-h1200dp")
    fun `po wycieciu zostaje samo wklej, a krzyzyk oproznia schowek`() {
        show(count = 0, clipboardCount = 5)

        rule.onNodeWithText("W schowku: 5 kresek").assertExists()
        rule.onNodeWithText("Wytnij").assertDoesNotExist()
        rule.onNodeWithText("Kopiuj").assertDoesNotExist()
        rule.onNodeWithText("Usuń").assertDoesNotExist()
        rule.onNodeWithText("Wklej").performClick()
        rule.onNodeWithContentDescription("Opróżnij schowek").performClick()

        assertThat(clicked).containsExactly("wklej", "oproznij").inOrder()
    }

    @Test
    @Config(qualifiers = "w800dp-h1200dp")
    fun `notatka tylko do odczytu pozwala tylko kopiowac`() {
        show(count = 4, clipboardCount = 2, canEdit = false)

        rule.onNodeWithText("Kopiuj").assertExists()
        listOf("Wytnij", "Wklej", "Usuń").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
}
