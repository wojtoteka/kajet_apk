package wojtoteka.ovh.kajet.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.ai.AiNoteKind

class AiCopyTest {

    @Test
    fun `plik i mapa nie mowia notatka`() {
        assertThat(PolishStrings.aiHint(AiNoteKind.CODE)).isEqualTo("Co zmienić w tym pliku?")
        assertThat(PolishStrings.aiHint(AiNoteKind.MINDMAP)).isEqualTo("Co zmienić na tej mapie?")
        assertThat(PolishStrings.aiWorking(AiNoteKind.CODE)).contains("plikiem")
        assertThat(PolishStrings.aiWorking(AiNoteKind.MINDMAP)).contains("mapą")
        assertThat(PolishStrings.aiHint(AiNoteKind.TEXT)).isEqualTo(PolishStrings.aiHint)
    }

    @Test
    fun `english file and map copy does not say note`() {
        assertThat(EnglishStrings.aiHint(AiNoteKind.CODE)).contains("file")
        assertThat(EnglishStrings.aiHint(AiNoteKind.MINDMAP)).contains("map")
        assertThat(EnglishStrings.aiWorking(AiNoteKind.CODE)).doesNotContain("note")
        assertThat(EnglishStrings.aiWorking(AiNoteKind.MINDMAP)).doesNotContain("note")
    }
}
