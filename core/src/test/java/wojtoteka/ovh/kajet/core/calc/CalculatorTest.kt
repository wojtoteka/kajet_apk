package wojtoteka.ovh.kajet.core.calc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalculatorTest {

    private fun eval(text: String) = Calculator.evaluate(text)?.let(Calculator::format)

    private fun press(vararg keys: CalcKey): CalculatorState =
        keys.fold(CalculatorState()) { state, key -> state.press(key) }

    @Test
    fun `liczy z pierwszenstwem mnozenia`() {
        assertThat(eval("2+3×4")).isEqualTo("14")
        assertThat(eval("(2+3)×4")).isEqualTo("20")
        assertThat(eval("10÷4")).isEqualTo("2.5")
        assertThat(eval("7−10")).isEqualTo("−3")
    }

    @Test
    fun `ulamki dziesietne bez bledu zaokraglen`() {
        assertThat(eval("0.1+0.2")).isEqualTo("0.3")
        assertThat(eval("1÷3")).isEqualTo("0.333333333333")
    }

    @Test
    fun `procent`() {
        assertThat(eval("50%")).isEqualTo("0.5")
        assertThat(eval("200+10%")).isEqualTo("220")
        assertThat(eval("200−10%")).isEqualTo("180")
        assertThat(eval("200×10%")).isEqualTo("20")
    }

    @Test
    fun `niepelne dzialanie i dzielenie przez zero daja null`() {
        assertThat(Calculator.evaluate("")).isNull()
        assertThat(Calculator.evaluate("5+")).isNull()
        assertThat(Calculator.evaluate("5÷0")).isNull()
        assertThat(Calculator.evaluate("5)")).isNull()
    }

    @Test
    fun `niezamkniety nawias zamyka sie sam`() {
        assertThat(eval("2×(3+4")).isEqualTo("14")
    }

    @Test
    fun `klawisze skladaja dzialanie`() {
        val state = press(CalcKey.D1, CalcKey.D2, CalcKey.PLUS, CalcKey.D3, CalcKey.EQUALS)
        assertThat(state.expression).isEqualTo("15")
        assertThat(state.showsResult).isTrue()
    }

    @Test
    fun `po wyniku cyfra zaczyna od nowa a znak liczy dalej`() {
        val result = press(CalcKey.D2, CalcKey.TIMES, CalcKey.D3, CalcKey.EQUALS)
        assertThat(result.press(CalcKey.D4).expression).isEqualTo("4")
        assertThat(result.press(CalcKey.PLUS).press(CalcKey.D1).preview?.let(Calculator::format))
            .isEqualTo("7")
    }

    @Test
    fun `drugi znak dzialania zamienia pierwszy`() {
        assertThat(press(CalcKey.D5, CalcKey.PLUS, CalcKey.TIMES).expression).isEqualTo("5×")
        // Minus po mnożeniu to znak liczby, a nie zamiana.
        assertThat(press(CalcKey.D5, CalcKey.TIMES, CalcKey.MINUS, CalcKey.D2).preview?.let(Calculator::format))
            .isEqualTo("−10")
    }

    @Test
    fun `kropka i zera`() {
        assertThat(press(CalcKey.DOT, CalcKey.D5).expression).isEqualTo("0.5")
        assertThat(press(CalcKey.D1, CalcKey.DOT, CalcKey.DOT, CalcKey.D2).expression).isEqualTo("1.2")
        assertThat(press(CalcKey.D0, CalcKey.D0, CalcKey.D7).expression).isEqualTo("7")
    }

    @Test
    fun `nawiasy jednym klawiszem`() {
        val state = press(
            CalcKey.D2, CalcKey.PARENS, CalcKey.D3, CalcKey.PLUS, CalcKey.D4, CalcKey.PARENS,
        )
        assertThat(state.expression).isEqualTo("2×(3+4)")
        assertThat(state.press(CalcKey.EQUALS).expression).isEqualTo("14")
    }

    @Test
    fun `blad po dzieleniu przez zero kasuje sie klawiszem`() {
        val state = press(CalcKey.D1, CalcKey.DIVIDE, CalcKey.D0, CalcKey.EQUALS)
        assertThat(state.error).isTrue()
        assertThat(state.press(CalcKey.BACKSPACE)).isEqualTo(CalculatorState())
    }

    @Test
    fun `ujemny wynik liczy sie dalej`() {
        val result = press(CalcKey.D2, CalcKey.MINUS, CalcKey.D5, CalcKey.EQUALS)
        assertThat(result.expression).isEqualTo("−3")
        assertThat(result.press(CalcKey.TIMES).press(CalcKey.D2).preview?.let(Calculator::format))
            .isEqualTo("−6")
    }

    @Test
    fun `pierwiastek`() {
        assertThat(eval("√9")).isEqualTo("3")
        assertThat(eval("√2")).isEqualTo("1.41421356237")
        assertThat(eval("√9×4")).isEqualTo("12")
        assertThat(eval("√(16+9)")).isEqualTo("5")
        assertThat(eval("√0.25")).isEqualTo("0.5")
        assertThat(eval("√0")).isEqualTo("0")
        assertThat(Calculator.evaluate("√−4")).isNull()
        assertThat(Calculator.evaluate("√")).isNull()
    }

    @Test
    fun `klawisz pierwiastka`() {
        assertThat(press(CalcKey.D2, CalcKey.ROOT, CalcKey.D9).expression).isEqualTo("2×√9")
        assertThat(press(CalcKey.ROOT, CalcKey.PARENS, CalcKey.D1, CalcKey.D6).expression)
            .isEqualTo("√(16")
        // Minus ani znak działania nie stają zaraz po pierwiastku.
        assertThat(press(CalcKey.ROOT, CalcKey.MINUS, CalcKey.PLUS).expression).isEqualTo("√")
        val result = press(CalcKey.D1, CalcKey.D6, CalcKey.EQUALS).press(CalcKey.ROOT)
        assertThat(result.expression).isEqualTo("√16")
        assertThat(result.press(CalcKey.EQUALS).expression).isEqualTo("4")
    }
}
