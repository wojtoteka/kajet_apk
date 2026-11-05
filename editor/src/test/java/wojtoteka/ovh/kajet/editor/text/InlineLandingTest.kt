package wojtoteka.ovh.kajet.editor.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/*
  Wynik polecenia pisanego w notatce i jego cofanie (Esc). Cofnięcie ma zdjąć
  zmianę asystenta zawsze i w całości; to, co człowiek dopisał obok, zostaje,
  a to, co dopisał w środku zmiany asystenta, przepada razem z nią.
*/
class InlineLandingTest {

    private val before = "Zadanie 1: 2+2 = \nZadanie 2: 17*23 = "
    private val fromAi = "Zadanie 1: 2+2 = 4\nZadanie 2: 17*23 = 391"

    // --- Cofanie świeżej zmiany ---

    @Test
    fun `bez pisania cofnięcie wraca dokładnie do notatki sprzed asystenta`() {
        assertThat(InlineLanding.revert(fromAi, before, fromAi)).isEqualTo(before)
    }

    @Test
    fun `dopisane za zmianą asystenta zostaje, zmiana znika`() {
        val current = "$fromAi\nZadanie 3"
        assertThat(InlineLanding.revert(fromAi, before, current))
            .isEqualTo("$before\nZadanie 3")
    }

    @Test
    fun `dopisane przed zmianą asystenta zostaje, zmiana znika`() {
        val current = "Praca domowa\n$fromAi"
        assertThat(InlineLanding.revert(fromAi, before, current))
            .isEqualTo("Praca domowa\n$before")
    }

    @Test
    fun `poprawka w środku wyniku asystenta przepada razem z nim`() {
        val current = "Zadanie 1: 2+2 = 4!\nZadanie 2: 17*23 = 391"
        assertThat(InlineLanding.revert(fromAi, before, current)).isEqualTo(before)
    }

    @Test
    fun `bez zmiany asystenta cofanie niczego nie rusza`() {
        assertThat(InlineLanding.revert("Ala", "Ala", "Ala ma kota")).isEqualTo("Ala ma kota")
    }

    // --- Wynik wraca ---

    @Test
    fun `bez pisania w trakcie staje wynik asystenta`() {
        val landed = InlineLanding.land(before, before, fromAi, merged = false)
        assertThat(landed.shown).isEqualTo(fromAi)
        assertThat(landed.withoutAi).isEqualTo(before)
    }

    @Test
    fun `pisanie w trakcie scala się z wynikiem, a cofnięcie zostawia pisanie`() {
        val screen = "$before\nZadanie 3: 1-1 = 0"
        val landed = InlineLanding.land(before, screen, fromAi, merged = false)
        assertThat(landed.shown).isEqualTo("$fromAi\nZadanie 3: 1-1 = 0")
        assertThat(landed.withoutAi).isEqualTo(screen)
        assertThat(InlineLanding.revert(landed.shown, landed.withoutAi, landed.shown)).isEqualTo(screen)
    }

    @Test
    fun `pisanie tuż za miejscem polecenia staje za wynikiem asystenta`() {
        val screen = "${before}Dalej"
        val landed = InlineLanding.land(before, screen, fromAi, merged = false)
        assertThat(landed.shown).isEqualTo("${fromAi}Dalej")
    }

    @Test
    fun `poprawki asystenta w całej notatce scalają się z pisaniem w środku`() {
        val start = "Ala ma kota\nBasia ma psa\nCela ma rybkę\nDarek ma żółwia"
        val ai = "Ala ma kota.\nBasia ma psa\nCela ma rybkę\nDarek ma żółwia."
        val screen = "Ala ma kota\nBasia ma psa\nI chomika\nCela ma rybkę\nDarek ma żółwia"
        val landed = InlineLanding.land(start, screen, ai, merged = false)
        assertThat(landed.shown)
            .isEqualTo("Ala ma kota.\nBasia ma psa\nI chomika\nCela ma rybkę\nDarek ma żółwia.")
    }

    @Test
    fun `wynik wstawiony już przez edycję na żywo zostaje, a cofnięcie go zdejmuje`() {
        val landed = InlineLanding.land(before, fromAi, fromAi, merged = true)
        assertThat(landed.shown).isEqualTo(fromAi)
        assertThat(landed.withoutAi).isEqualTo(before)
    }

    // --- Esc, zanim wynik wrócił ---

    @Test
    fun `zatrzymanie bez edycji na żywo zostawia ekran - wyniku na nim nie ma`() {
        val screen = "$before\nZadanie 3"
        assertThat(InlineLanding.stop(before, screen, fromAi, merged = false)).isEqualTo(screen)
    }

    @Test
    fun `zatrzymanie zdejmuje wynik, który edycja na żywo zdążyła wstawić`() {
        assertThat(InlineLanding.stop(before, fromAi, fromAi, merged = true)).isEqualTo(before)
        assertThat(InlineLanding.stop(before, "$fromAi\nZadanie 3", fromAi, merged = true))
            .isEqualTo("$before\nZadanie 3")
    }
}
