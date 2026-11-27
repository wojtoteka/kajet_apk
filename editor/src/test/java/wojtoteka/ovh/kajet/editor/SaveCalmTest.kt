package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/*
  Napis stanu zapisu przy pisaniu: przez całą serię zmian stoi „Zapisuję…",
  a „Zapisane" wraca dopiero po chwili spokoju.
*/
class SaveCalmTest {

    /** Kolejne prawdziwe stany, każdy na krótko (krótsza przerwa niż próg). */
    private fun quickly(vararg states: SaveState): List<SaveState> {
        var shown = SaveState.SAVED
        return states.map { state ->
            shown = SaveCalm.now(shown, state)
            shown
        }
    }

    @Test
    fun `pisanie z mikroprzerwami caly czas pokazuje zapis`() {
        val shown = quickly(
            SaveState.CHANGED, SaveState.SAVING, SaveState.SAVED,
            SaveState.CHANGED, SaveState.SAVING, SaveState.SAVED,
            SaveState.CHANGED,
        )

        assertThat(shown).containsExactlyElementsIn(List(7) { SaveState.SAVING })
    }

    @Test
    fun `zapisane wraca po dwoch sekundach spokoju`() {
        assertThat(SaveCalm.later(SaveState.SAVED)).isEqualTo(SaveState.SAVED)
        assertThat(SaveCalm.waitFor(SaveState.SAVED)).isEqualTo(2_000L)
    }

    @Test
    fun `blad i wczytywanie widac od razu`() {
        assertThat(SaveCalm.now(SaveState.SAVING, SaveState.ERROR)).isEqualTo(SaveState.ERROR)
        assertThat(SaveCalm.later(SaveState.ERROR)).isNull()
        assertThat(SaveCalm.now(SaveState.LOADING, SaveState.SAVED)).isEqualTo(SaveState.SAVED)
    }

    @Test
    fun `zapis ktory nie ruszyl w koncu pokazuje czekajace zmiany`() {
        assertThat(SaveCalm.now(SaveState.SAVED, SaveState.CHANGED)).isEqualTo(SaveState.SAVING)
        assertThat(SaveCalm.later(SaveState.CHANGED)).isEqualTo(SaveState.CHANGED)
        assertThat(SaveCalm.waitFor(SaveState.CHANGED)).isGreaterThan(SaveCalm.SETTLE_MS)
    }
}
