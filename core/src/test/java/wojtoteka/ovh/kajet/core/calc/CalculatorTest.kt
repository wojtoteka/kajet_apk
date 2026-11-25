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
    fun `klawisz pierwiastka otwiera nawias jak w telefonie`() {
        val root = press(CalcKey.ROOT, CalcKey.D1, CalcKey.D6)
        assertThat(root.expression).isEqualTo("√(16")
        assertThat(root.preview?.let(Calculator::format)).isEqualTo("4")
        // Nawias domyka się sam przy „=".
        assertThat(root.press(CalcKey.EQUALS).expression).isEqualTo("4")
        // Liczba przed pierwiastkiem to mnożenie.
        assertThat(press(CalcKey.D2, CalcKey.ROOT, CalcKey.D9).expression).isEqualTo("2×√(9")
        // Znak działania nie staje zaraz za nawiasem pierwiastka.
        assertThat(press(CalcKey.ROOT, CalcKey.PLUS).expression).isEqualTo("√(")
        val result = press(CalcKey.D1, CalcKey.D6, CalcKey.EQUALS).press(CalcKey.ROOT)
        assertThat(result.expression).isEqualTo("√(16")
        assertThat(result.press(CalcKey.EQUALS).expression).isEqualTo("4")
    }

    @Test
    fun `pod pierwiastkiem jest cale dzialanie w nawiasie`() {
        // Wcześniej „√16+9" dawało 13 - pierwiastek brał tylko 16.
        val sum = press(CalcKey.ROOT, CalcKey.D1, CalcKey.D6, CalcKey.PLUS, CalcKey.D9)
        assertThat(sum.expression).isEqualTo("√(16+9")
        assertThat(sum.preview?.let(Calculator::format)).isEqualTo("5")
        // Domknięty nawias kończy pierwiastek: √(16)+9 = 13.
        val closed = press(
            CalcKey.ROOT, CalcKey.D1, CalcKey.D6, CalcKey.PARENS, CalcKey.PLUS, CalcKey.D9, CalcKey.EQUALS,
        )
        assertThat(closed.previous).isEqualTo("√(16)+9")
        assertThat(closed.expression).isEqualTo("13")
        assertThat(press(CalcKey.CUBE_ROOT, CalcKey.D2, CalcKey.D7, CalcKey.EQUALS).expression).isEqualTo("3")
    }

    @Test
    fun `backspace zabiera pierwiastek razem z nawiasem`() {
        assertThat(press(CalcKey.D4, CalcKey.PLUS, CalcKey.ROOT).press(CalcKey.BACKSPACE).expression)
            .isEqualTo("4+")
        assertThat(press(CalcKey.CUBE_ROOT).press(CalcKey.BACKSPACE).expression).isEqualTo("")
    }

    @Test
    fun `zmiana znaku pod pierwiastkiem nie dokleja drugiego nawiasu`() {
        val negative = press(CalcKey.ROOT, CalcKey.D5, CalcKey.SIGN)
        assertThat(negative.expression).isEqualTo("√(−5")
        assertThat(negative.preview).isNull()
        assertThat(negative.press(CalcKey.SIGN).expression).isEqualTo("√(5")
        // Zwykły nawias zaczęty zmianą znaku dalej schodzi w całości.
        assertThat(press(CalcKey.D1, CalcKey.PLUS, CalcKey.D5, CalcKey.SIGN, CalcKey.SIGN).expression)
            .isEqualTo("1+5")
    }

    @Test
    fun `stary zapis pierwiastka bez nawiasu z historii dalej sie liczy`() {
        assertThat(CalculatorState().withExpression("√16").press(CalcKey.EQUALS).expression).isEqualTo("4")
    }

    // --- Funkcje naukowe ---

    @Test
    fun `trygonometria w stopniach`() {
        assertThat(eval("sin(30)")).isEqualTo("0.5")
        assertThat(eval("cos(60)")).isEqualTo("0.5")
        assertThat(eval("tan(45)")).isEqualTo("1")
        // Kąty z osi liczą się dokładnie, a nie z resztkami po double.
        assertThat(eval("cos(90)")).isEqualTo("0")
        assertThat(eval("sin(180)")).isEqualTo("0")
        assertThat(eval("sin(−90)")).isEqualTo("−1")
        assertThat(eval("sin(36000030)")).isEqualTo("0.5")
        assertThat(Calculator.evaluate("tan(90)")).isNull()
    }

    @Test
    fun `trygonometria w radianach`() {
        fun rad(text: String) = Calculator.evaluate(text, degrees = false)?.let(Calculator::format)
        assertThat(rad("sin(π)")).isEqualTo("0")
        assertThat(rad("cos(π)")).isEqualTo("−1")
        assertThat(rad("sin(π÷6)")).isEqualTo("0.5")
        assertThat(Calculator.evaluate("tan(π÷2)", degrees = false)).isNull()
    }

    @Test
    fun `funkcje odwrotne`() {
        assertThat(eval("sin⁻¹(0.5)")).isEqualTo("30")
        assertThat(eval("cos⁻¹(0)")).isEqualTo("90")
        assertThat(eval("tan⁻¹(1)")).isEqualTo("45")
        assertThat(Calculator.evaluate("sin⁻¹(2)")).isNull()
    }

    @Test
    fun `logarytmy i potegi`() {
        assertThat(eval("log(1000)")).isEqualTo("3")
        assertThat(eval("ln(e)")).isEqualTo("1")
        assertThat(eval("e^(0)")).isEqualTo("1")
        assertThat(eval("10^(3)")).isEqualTo("1000")
        assertThat(eval("2^10")).isEqualTo("1024")
        assertThat(eval("2^−2")).isEqualTo("0.25")
        assertThat(eval("2^3^2")).isEqualTo("512")
        assertThat(eval("−2^2")).isEqualTo("−4")
        assertThat(eval("9^0.5")).isEqualTo("3")
        assertThat(eval("5²")).isEqualTo("25")
        assertThat(eval("2³")).isEqualTo("8")
        assertThat(eval("4⁻¹")).isEqualTo("0.25")
        assertThat(eval("∛27")).isEqualTo("3")
        assertThat(eval("∛−8")).isEqualTo("−2")
        assertThat(Calculator.evaluate("log(0)")).isNull()
        assertThat(Calculator.evaluate("ln(−1)")).isNull()
        assertThat(Calculator.evaluate("0⁻¹")).isNull()
        assertThat(Calculator.evaluate("(−8)^0.5")).isNull()
    }

    @Test
    fun `silnia`() {
        assertThat(eval("5!")).isEqualTo("120")
        assertThat(eval("0!")).isEqualTo("1")
        assertThat(eval("3!!")).isEqualTo("720")
        assertThat(Calculator.evaluate("2.5!")).isNull()
        assertThat(Calculator.evaluate("(−3)!")).isNull()
    }

    @Test
    fun `stale`() {
        assertThat(eval("π")).isEqualTo("3.14159265359")
        assertThat(eval("e")).isEqualTo("2.71828182846")
        assertThat(eval("2×π")).isEqualTo("6.28318530718")
    }

    @Test
    fun `duze i male liczby w zapisie naukowym`() {
        assertThat(eval("10^(20)")).isEqualTo("1E+20")
        assertThat(eval("25!")).isEqualTo("1.55112100433E+25")
        assertThat(eval("10^(−12)")).isEqualTo("1E−12")
        // Wynik w zapisie naukowym liczy się dalej.
        assertThat(eval("1.5E+20×2")).isEqualTo("3E+20")
        assertThat(eval("2E−7×5")).isEqualTo("0.000001")
        val result = press(CalcKey.D2, CalcKey.D5, CalcKey.FACTORIAL, CalcKey.EQUALS)
        assertThat(result.press(CalcKey.DIVIDE).press(CalcKey.D2).press(CalcKey.EQUALS).expression)
            .isEqualTo("7.75560502165E+24")
    }

    // --- Klawisze naukowe ---

    @Test
    fun `funkcja po liczbie to mnozenie, a nawias domyka sie sam`() {
        val state = press(CalcKey.D2, CalcKey.SIN, CalcKey.D3, CalcKey.D0)
        assertThat(state.expression).isEqualTo("2×sin(30")
        assertThat(state.preview?.let(Calculator::format)).isEqualTo("1")
        assertThat(state.press(CalcKey.EQUALS).expression).isEqualTo("1")
    }

    @Test
    fun `stala po liczbie i liczba po stalej to mnozenie`() {
        assertThat(press(CalcKey.D2, CalcKey.PI).expression).isEqualTo("2×π")
        assertThat(press(CalcKey.PI, CalcKey.D2).expression).isEqualTo("π×2")
        assertThat(press(CalcKey.D3, CalcKey.SQUARE, CalcKey.D2).expression).isEqualTo("3²×2")
    }

    @Test
    fun `funkcja po wyniku obejmuje wynik`() {
        val result = press(CalcKey.D1, CalcKey.D0, CalcKey.D0, CalcKey.D0, CalcKey.EQUALS)
        val log = result.press(CalcKey.LOG)
        assertThat(log.expression).isEqualTo("log(1000")
        assertThat(log.press(CalcKey.EQUALS).expression).isEqualTo("3")
    }

    @Test
    fun `kwadrat i potega po wyniku licza dalej`() {
        val result = press(CalcKey.D3, CalcKey.PLUS, CalcKey.D2, CalcKey.EQUALS)
        assertThat(result.press(CalcKey.SQUARE).expression).isEqualTo("5²")
        val power = result.press(CalcKey.POWER).press(CalcKey.D3)
        assertThat(power.expression).isEqualTo("5^3")
        assertThat(power.press(CalcKey.EQUALS).expression).isEqualTo("125")
    }

    @Test
    fun `potega przyjmuje wykladnik ujemny`() {
        val state = press(CalcKey.D2, CalcKey.POWER, CalcKey.MINUS, CalcKey.D1)
        assertThat(state.expression).isEqualTo("2^−1")
        assertThat(state.preview?.let(Calculator::format)).isEqualTo("0.5")
    }

    @Test
    fun `backspace zabiera funkcje w calosci`() {
        assertThat(press(CalcKey.D2, CalcKey.PLUS, CalcKey.ASIN).press(CalcKey.BACKSPACE).expression)
            .isEqualTo("2+")
        assertThat(press(CalcKey.LN).press(CalcKey.BACKSPACE).expression).isEqualTo("")
        assertThat(press(CalcKey.D4, CalcKey.INVERSE).press(CalcKey.BACKSPACE).expression).isEqualTo("4")
    }

    @Test
    fun `osobne nawiasy`() {
        val state = press(
            CalcKey.OPEN, CalcKey.D2, CalcKey.PLUS, CalcKey.D3, CalcKey.CLOSE, CalcKey.CLOSE,
            CalcKey.TIMES, CalcKey.D4,
        )
        // Drugi zamykający nie ma czego zamknąć.
        assertThat(state.expression).isEqualTo("(2+3)×4")
        assertThat(state.press(CalcKey.EQUALS).expression).isEqualTo("20")
    }

    @Test
    fun `zmiana znaku jak w telefonie`() {
        assertThat(press(CalcKey.D5, CalcKey.SIGN).expression).isEqualTo("−5")
        assertThat(press(CalcKey.D5, CalcKey.SIGN, CalcKey.SIGN).expression).isEqualTo("5")
        val inside = press(CalcKey.D1, CalcKey.D2, CalcKey.PLUS, CalcKey.D5, CalcKey.SIGN)
        assertThat(inside.expression).isEqualTo("12+(−5")
        assertThat(inside.preview?.let(Calculator::format)).isEqualTo("7")
        assertThat(inside.press(CalcKey.SIGN).expression).isEqualTo("12+5")
        assertThat(press(CalcKey.D3, CalcKey.TIMES, CalcKey.SIGN, CalcKey.D2).expression).isEqualTo("3×(−2")
        assertThat(press(CalcKey.SIGN, CalcKey.D4).expression).isEqualTo("(−4")
    }

    @Test
    fun `zmiana znaku wyniku zostawia wynik`() {
        val result = press(CalcKey.D9, CalcKey.EQUALS)
        val negated = result.press(CalcKey.SIGN)
        assertThat(negated.expression).isEqualTo("−9")
        assertThat(negated.showsResult).isTrue()
        assertThat(negated.press(CalcKey.D1).expression).isEqualTo("1")
    }

    @Test
    fun `stopnie i radiany`() {
        val state = press(CalcKey.SIN, CalcKey.D9, CalcKey.D0)
        assertThat(state.preview?.let(Calculator::format)).isEqualTo("1")
        val radians = state.toggleDegrees()
        assertThat(radians.degrees).isFalse()
        assertThat(radians.preview?.let(Calculator::format)).isEqualTo("0.893996663601")
        // Czyszczenie nie przestawia jednostki kąta.
        assertThat(radians.press(CalcKey.CLEAR).degrees).isFalse()
    }

    @Test
    fun `po wyniku zostaje dzialanie, z ktorego wyszedl`() {
        val result = press(CalcKey.D2, CalcKey.TIMES, CalcKey.OPEN, CalcKey.D3, CalcKey.PLUS, CalcKey.D4, CalcKey.EQUALS)
        assertThat(result.expression).isEqualTo("14")
        assertThat(result.previous).isEqualTo("2×(3+4)")
        // Drugie „=" na wyniku niczego nie zmienia.
        assertThat(result.press(CalcKey.EQUALS)).isEqualTo(result)
    }

    @Test
    fun `liczba z historii`() {
        assertThat(CalculatorState().withNumber("42")).isEqualTo(CalculatorState("42", showsResult = true))
        val inside = press(CalcKey.D5, CalcKey.MINUS).withNumber("−3")
        assertThat(inside.expression).isEqualTo("5−(−3)")
        assertThat(inside.preview?.let(Calculator::format)).isEqualTo("8")
        assertThat(press(CalcKey.D2).withNumber("3").expression).isEqualTo("2×3")
    }

    // --- Wyświetlacz ---

    @Test
    fun `wyswietlacz grupuje tysiace i stawia przecinek`() {
        assertThat(CalculatorDisplay.shown("1234567.891", ',', ' ')).isEqualTo("1 234 567,891")
        assertThat(CalculatorDisplay.shown("999+1000", ',', ' ')).isEqualTo("999+1 000")
        assertThat(CalculatorDisplay.shown("12345.6789", '.', ',')).isEqualTo("12,345.6789")
        // Wykładnik zapisu naukowego zostaje bez odstępów.
        assertThat(CalculatorDisplay.shown("1.5E+1000", ',', ' ')).isEqualTo("1,5E+1000")
        assertThat(CalculatorDisplay.shown("sin(30)×2", ',', ' ')).isEqualTo("sin(30)×2")
    }
}
