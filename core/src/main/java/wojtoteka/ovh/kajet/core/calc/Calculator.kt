package wojtoteka.ovh.kajet.core.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/*
  Kalkulator na wierzchu notatki - jak ten w telefonie.

  Działanie trzymamy jako napis, tak jak widać je na wyświetlaczu, i liczymy
  od nowa po każdym klawiszu - dzięki temu wynik widać już w trakcie pisania,
  a „=" tylko go zatwierdza. Liczymy na BigDecimal, bo na double
  0,1 + 0,2 daje 0,30000000000000004, a tego w zeszycie nikt nie chce czytać.
  Funkcje naukowe (sinus, logarytm, potęga o ułamkowym wykładniku) idą przez
  double - dwanaście cyfr na wyświetlaczu i tak mieści się w jego dokładności.

  W napisie działania kropka dziesiętna jest zawsze kropką, a znaki działań
  są te, które stoją na klawiszach (×, ÷, −, ^). Przecinek zamiast kropki
  i odstępy między tysiącami to sprawa wyświetlacza ([CalculatorDisplay]),
  nie liczenia.

  Zapis działania:
    sin( cos( tan( sin⁻¹( cos⁻¹( tan⁻¹( ln( log(  - funkcja z nawiasem,
    √ ∛                                          - pierwiastek najbliższej liczby albo nawiasu,
    ^                                            - potęga (2^10, e^(…), 10^(…)),
    ² ³ ⁻¹ ! %                                   - za liczbą: kwadrat, sześcian, odwrotność, silnia, procent,
    π e                                          - stałe,
    1.5E+20                                      - wynik za duży na zwykły zapis.
*/

enum class CalcKey(val symbol: String) {
    D0("0"), D1("1"), D2("2"), D3("3"), D4("4"),
    D5("5"), D6("6"), D7("7"), D8("8"), D9("9"),
    DOT("."),
    PLUS("+"), MINUS("−"), TIMES("×"), DIVIDE("÷"),
    PERCENT("%"),
    ROOT("√"),
    PARENS("()"),
    BACKSPACE("⌫"),
    CLEAR("C"),
    EQUALS("="),

    /** Zmiana znaku liczby, na której stoi kursor: 5 → (−5) → 5. */
    SIGN("±"),
    OPEN("("),
    CLOSE(")"),
    POWER("xʸ"),
    SQUARE("x²"),
    CUBE("x³"),
    INVERSE("1/x"),
    FACTORIAL("x!"),
    CUBE_ROOT("∛"),
    PI("π"),
    E("e"),
    SIN("sin"),
    COS("cos"),
    TAN("tan"),
    ASIN("sin⁻¹"),
    ACOS("cos⁻¹"),
    ATAN("tan⁻¹"),
    LN("ln"),
    LOG("log"),
    EXP("eˣ"),
    TEN_POWER("10ˣ"),
}

/**
 * Stan kalkulatora: działanie na wyświetlaczu i to, czy stoi w nim wynik po
 * „=". Po wyniku cyfra zaczyna nowe działanie, a znak działania liczy dalej
 * od wyniku - tak jak w każdym kalkulatorze w telefonie.
 */
data class CalculatorState(
    val expression: String = "",
    val showsResult: Boolean = false,
    val error: Boolean = false,
    /** Kąty w stopniach (DEG) albo w radianach (RAD). */
    val degrees: Boolean = true,
    /** Działanie, z którego wyszedł wynik - telefon pokazuje je nad wynikiem. */
    val previous: String = "",
) {
    /** Wynik bieżącego działania albo null, gdy działanie jest niepełne. */
    val preview: BigDecimal? get() = Calculator.evaluate(expression, degrees)

    fun press(key: CalcKey): CalculatorState = when (key) {
        CalcKey.CLEAR -> CalculatorState(degrees = degrees)
        CalcKey.BACKSPACE -> if (showsResult || error) {
            CalculatorState(degrees = degrees)
        } else {
            copy(expression = backspace(expression), previous = "")
        }
        CalcKey.EQUALS -> when {
            expression.isEmpty() || showsResult -> this
            else -> {
                val closed = closeParens(expression)
                val value = Calculator.evaluate(closed, degrees)
                if (value == null) {
                    copy(error = true)
                } else {
                    CalculatorState(
                        expression = Calculator.format(value),
                        showsResult = true,
                        degrees = degrees,
                        previous = closed,
                    )
                }
            }
        }
        else -> typed(key)
    }

    /** Przełącza stopnie i radiany; działanie zostaje, wynik liczy się od nowa. */
    fun toggleDegrees(): CalculatorState = copy(degrees = !degrees, error = false)

    /**
     * Wstawia liczbę - na przykład wynik z historii. Na pustym wyświetlaczu
     * i po wyniku staje się wynikiem, w trakcie działania - jego kolejną liczbą.
     */
    fun withNumber(value: String): CalculatorState {
        if (expression.isEmpty() || showsResult || error) {
            return CalculatorState(value, showsResult = true, degrees = degrees)
        }
        val last = expression.last()
        val number = if (value.startsWith('−')) "($value)" else value
        val next = when {
            last in OPERAND_END -> "$expression×$number"
            // Liczba ujemna zaraz po minusie dostaje nawias: 5−(−3), a nie 5−−3.
            else -> expression + number
        }
        if (next.length > MAX_LENGTH) return this
        return CalculatorState(next, degrees = degrees)
    }

    /** Całe działanie od nowa - na przykład stuknięte w historii. */
    fun withExpression(value: String): CalculatorState = CalculatorState(value, degrees = degrees)

    private fun typed(key: CalcKey): CalculatorState {
        if (showsResult) {
            when (key) {
                // Znak liczby zmienia sam wynik i ten dalej jest wynikiem.
                CalcKey.SIGN -> return copy(expression = sign(expression), previous = "")
                // Pierwiastek i funkcja po wyniku biorą sam wynik: 16 = √ daje √16.
                in WRAPS_RESULT -> return CalculatorState(
                    prefixOf(key) + expression,
                    degrees = degrees,
                )
                else -> Unit
            }
        }
        // Po wyniku cyfra zaczyna od nowa, znak działania liczy dalej.
        val base = if (showsResult && key !in CONTINUES_RESULT) "" else expression
        val next = when (key) {
            CalcKey.DOT -> dot(base)
            CalcKey.PLUS, CalcKey.TIMES, CalcKey.DIVIDE -> operator(base, key.symbol)
            CalcKey.POWER -> operator(base, "^")
            CalcKey.MINUS -> minus(base)
            CalcKey.PERCENT -> postfix(base, "%")
            CalcKey.SQUARE -> postfix(base, "²")
            CalcKey.CUBE -> postfix(base, "³")
            CalcKey.INVERSE -> postfix(base, "⁻¹")
            CalcKey.FACTORIAL -> postfix(base, "!")
            CalcKey.PARENS -> parens(base)
            CalcKey.OPEN -> opening(base, "(")
            CalcKey.CLOSE -> closing(base)
            CalcKey.SIGN -> sign(base)
            CalcKey.PI, CalcKey.E -> opening(base, key.symbol)
            in WRAPS_RESULT -> opening(base, prefixOf(key))
            else -> digit(base, key.symbol)
        }
        if (next.length > MAX_LENGTH) return this
        return CalculatorState(next, degrees = degrees)
    }

    internal companion object {
        const val MAX_LENGTH = 120
        const val OPERATORS = "+−×÷^"

        /** Znaki, na których kończy się liczba albo nawias - za nimi może stać działanie. */
        const val OPERAND_END = "0123456789.)%!πe²³¹"

        /** Znaki, za którymi liczba nie może zacząć się od działania. */
        const val PREFIXES = "√∛"

        /** Klawisze, które po wyniku liczą dalej od niego. */
        val CONTINUES_RESULT = setOf(
            CalcKey.PLUS, CalcKey.MINUS, CalcKey.TIMES, CalcKey.DIVIDE, CalcKey.POWER,
            CalcKey.PERCENT, CalcKey.SQUARE, CalcKey.CUBE, CalcKey.INVERSE, CalcKey.FACTORIAL,
        )

        /** Klawisze, które po wyniku obejmują wynik: √16, sin(30. */
        val WRAPS_RESULT = setOf(
            CalcKey.ROOT, CalcKey.CUBE_ROOT,
            CalcKey.SIN, CalcKey.COS, CalcKey.TAN,
            CalcKey.ASIN, CalcKey.ACOS, CalcKey.ATAN,
            CalcKey.LN, CalcKey.LOG, CalcKey.EXP, CalcKey.TEN_POWER,
        )

        /**
         * Kawałki zapisu, które klawisz wstawia naraz i które ⌫ zabiera naraz.
         * Dłuższe przed krótszymi, bo „sin⁻¹(" kończy się tak jak „⁻¹(".
         */
        private val UNITS = listOf(
            "sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "ln(", "log(", "⁻¹",
        )

        fun prefixOf(key: CalcKey): String = when (key) {
            CalcKey.ROOT -> "√"
            CalcKey.CUBE_ROOT -> "∛"
            CalcKey.SIN -> "sin("
            CalcKey.COS -> "cos("
            CalcKey.TAN -> "tan("
            CalcKey.ASIN -> "sin⁻¹("
            CalcKey.ACOS -> "cos⁻¹("
            CalcKey.ATAN -> "tan⁻¹("
            CalcKey.LN -> "ln("
            CalcKey.LOG -> "log("
            CalcKey.EXP -> "e^("
            CalcKey.TEN_POWER -> "10^("
            else -> ""
        }

        fun lastNumber(text: String): String = text.takeLastWhile { it.isDigit() || it == '.' }

        fun endsOperand(text: String): Boolean = text.lastOrNull()?.let { it in OPERAND_END } == true

        fun backspace(text: String): String {
            val unit = UNITS.firstOrNull { text.endsWith(it) }
            return if (unit != null) text.dropLast(unit.length) else text.dropLast(1)
        }

        fun digit(text: String, d: String): String {
            // Po procencie, nawiasie, stałej albo silni cyfra znaczy mnożenie.
            val last = text.lastOrNull()
            if (last != null && last in OPERAND_END && !last.isDigit() && last != '.') return "$text×$d"
            // „007" to 7: zero na początku liczby ustępuje następnej cyfrze.
            if (lastNumber(text) == "0") return text.dropLast(1) + d
            return text + d
        }

        fun dot(text: String): String {
            val number = lastNumber(text)
            if (number.contains('.')) return text
            val last = text.lastOrNull()
            if (last != null && last in OPERAND_END && !last.isDigit()) return "$text×0."
            return if (number.isEmpty()) text + "0." else "$text."
        }

        fun operator(text: String, op: String): String {
            if (text.isEmpty() || text.last() == '(' || text.last() in PREFIXES) return text
            // Drugi znak z rzędu zamienia poprzedni, zamiast psuć działanie.
            val trimmed = text.dropLastWhile { it in OPERATORS || it == '.' }
            if (trimmed.isEmpty() || trimmed.last() == '(' || trimmed.last() in PREFIXES) return trimmed
            return trimmed + op
        }

        fun minus(text: String): String {
            // Minus na początku albo po nawiasie to znak liczby.
            if (text.isEmpty() || text.last() == '(') return "$text−"
            // Pod pierwiastkiem nie ma liczb ujemnych.
            if (text.last() in PREFIXES) return text
            if (text.last() in "×÷^") return "$text−"
            return operator(text, "−")
        }

        /** Kwadrat, odwrotność, silnia, procent - tylko za liczbą albo nawiasem. */
        fun postfix(text: String, mark: String): String = if (endsOperand(text)) text + mark else text

        /** Nawias, funkcja, pierwiastek albo stała; liczba przed nimi to mnożenie: 2π = 2×π. */
        fun opening(text: String, token: String): String =
            if (endsOperand(text)) "$text×$token" else text + token

        fun closing(text: String): String {
            val open = text.count { it == '(' } - text.count { it == ')' }
            return if (open > 0 && endsOperand(text)) "$text)" else text
        }

        fun parens(text: String): String {
            val open = text.count { it == '(' } - text.count { it == ')' }
            return when {
                open > 0 && endsOperand(text) -> "$text)"
                // Liczba przed nawiasem to mnożenie: 2(3+4) = 2×(3+4).
                else -> opening(text, "(")
            }
        }

        /**
         * Zmiana znaku liczby na końcu działania, jak klawisz +/− w telefonie:
         * 12+5 → 12+(−5) → 12+5. Na pustym wyświetlaczu i za znakiem działania
         * zaczyna liczbę ujemną.
         */
        fun sign(text: String): String {
            var start = text.length
            while (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '.')) start--
            // Wynik w zapisie naukowym: 1.5E−7 to jedna liczba.
            if (start >= 2 && start < text.length && text[start - 1] in "+−-" && text[start - 2] == 'E') {
                start -= 2
                while (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '.')) start--
            }
            if (start == text.length && text.lastOrNull().let { it == 'π' || it == 'e' }) start--
            if (start == text.length) {
                val last = text.lastOrNull()
                return if (last == null || last in OPERATORS || last == '(') "$text(−" else text
            }
            val head = text.substring(0, start)
            val number = text.substring(start)
            return when {
                head.isEmpty() -> "−$number"
                head == "−" -> number
                head.endsWith("(−") -> head.dropLast(2) + number
                head.endsWith("−") && head[head.length - 2] in "×÷^" -> head.dropLast(1) + number
                else -> "$head(−$number"
            }
        }

        fun closeParens(text: String): String {
            val open = text.count { it == '(' } - text.count { it == ')' }
            return if (open > 0) text + ")".repeat(open) else text
        }
    }
}

object Calculator {
    private val context = MathContext(20, RoundingMode.HALF_EVEN)

    private val PI_VALUE = BigDecimal("3.14159265358979323846264")
    private val E_VALUE = BigDecimal("2.71828182845904523536029")
    private val HUNDRED = BigDecimal(100)
    private val THREE_SIXTY = BigDecimal(360)

    /** Największa silnia - 3000! ma ponad dziewięć tysięcy cyfr, dalej to już tylko czekanie. */
    private const val MAX_FACTORIAL = 3000

    /** Największy wykładnik dziesiątki w wyniku potęgi liczonej przez double. */
    private const val MAX_POWER_EXPONENT = 9999

    /**
     * Liczy działanie z klawiatury kalkulatora. Zwraca null, gdy działanie
     * jest niepełne albo nie da się go policzyć (na przykład dzielenie przez
     * zero, logarytm z zera). Nawiasy niezamknięte na końcu zamyka sam.
     * Kąty funkcji trygonometrycznych są w stopniach, gdy [degrees].
     */
    fun evaluate(expression: String, degrees: Boolean = true): BigDecimal? {
        if (expression.isBlank()) return null
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        if (open < 0) return null
        val closed = expression + ")".repeat(open)
        return runCatching {
            val parser = Parser(closed, degrees)
            val value = parser.expression()
            if (!parser.done()) null else value
        }.getOrNull()
    }

    /**
     * Wynik do pokazania: dwanaście cyfr znaczących, bez zer na końcu,
     * z kropką dziesiętną - przecinek dokłada wyświetlacz. Liczba bardzo
     * duża albo bardzo mała idzie w zapisie naukowym (1.5E+20), żeby nie
     * zajęła całego wyświetlacza zerami. Taki napis da się od razu liczyć dalej.
     */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val exponent = rounded.precision() - rounded.scale() - 1
        val text = if (exponent >= 15 || exponent < -9) {
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
            mantissa + "E" + (if (exponent < 0) "−" else "+") + abs(exponent)
        } else {
            rounded.toPlainString()
        }
        return text.replace('-', '−')
    }

    /* --- Funkcje na BigDecimal ---------------------------------------- */

    /** Wykładnik dziesiątki liczby dodatniej: 1234 → 3, 0.05 → −2. */
    private fun decimalExponent(value: BigDecimal): Int = value.precision() - value.scale() - 1

    /** log10 dowolnie dużej albo małej liczby dodatniej - double sam by się przepełnił. */
    private fun log10Of(value: BigDecimal): Double {
        if (value.signum() <= 0) throw ArithmeticException("logarytm z liczby niedodatniej")
        val exponent = decimalExponent(value)
        val mantissa = value.movePointLeft(exponent).toDouble()
        return exponent + log10(mantissa)
    }

    private fun fromDouble(value: Double): BigDecimal {
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("poza zakresem")
        return BigDecimal(value, context)
    }

    /*
      BigDecimal.sqrt jest w Androidzie dopiero od API 33, a Kajet chodzi od 26.
      Dlatego pierwiastek liczymy sami: przybliżenie z double i kilka kroków
      metody Newtona, każdy podwaja liczbę dobrych cyfr. Przybliżenie bierzemy
      z samej mantysy, żeby √(1E+400) nie utknął na nieskończoności double.
    */
    private fun squareRoot(value: BigDecimal): BigDecimal {
        if (value.signum() < 0) throw ArithmeticException("pierwiastek z liczby ujemnej")
        if (value.signum() == 0) return BigDecimal.ZERO
        val exponent = decimalExponent(value)
        val even = if (exponent % 2 == 0) exponent else exponent - 1
        var guess = BigDecimal(Math.sqrt(value.movePointLeft(even).toDouble()), context)
            .movePointRight(even / 2)
        val two = BigDecimal(2)
        repeat(6) {
            guess = guess.add(value.divide(guess, context), context).divide(two, context)
        }
        return guess
    }

    /** Pierwiastek sześcienny - także z liczby ujemnej: ∛(−8) = −2. */
    private fun cubeRoot(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        if (value.signum() < 0) return cubeRoot(value.negate()).negate()
        val exponent = decimalExponent(value)
        val whole = Math.floorDiv(exponent, 3) * 3
        var guess = BigDecimal(Math.cbrt(value.movePointLeft(whole).toDouble()), context)
            .movePointRight(whole / 3)
        val two = BigDecimal(2)
        val three = BigDecimal(3)
        repeat(6) {
            val square = guess.multiply(guess, context)
            guess = guess.multiply(two, context).add(value.divide(square, context), context).divide(three, context)
        }
        return guess
    }

    private fun raise(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        val whole = exponent.signum() == 0 || exponent.stripTrailingZeros().scale() <= 0
        if (whole && exponent.abs() <= BigDecimal(MAX_POWER_EXPONENT)) {
            val n = exponent.intValueExact()
            if (base.signum() == 0 && n < 0) throw ArithmeticException("dzielenie przez zero")
            return base.pow(n, context)
        }
        if (base.signum() == 0) {
            if (exponent.signum() > 0) return BigDecimal.ZERO
            throw ArithmeticException("dzielenie przez zero")
        }
        // Ułamkowy wykładnik liczby ujemnej nie ma wyniku wśród liczb rzeczywistych.
        if (base.signum() < 0) throw ArithmeticException("potęga liczby ujemnej")
        val log = log10Of(base) * exponent.toDouble()
        if (log.isNaN() || abs(log) > MAX_POWER_EXPONENT) throw ArithmeticException("poza zakresem")
        val tens = floor(log)
        return fromDouble(10.0.pow(log - tens)).scaleByPowerOfTen(tens.toInt()).round(context)
    }

    private fun factorial(value: BigDecimal): BigDecimal {
        val whole = value.signum() == 0 || value.stripTrailingZeros().scale() <= 0
        if (!whole || value.signum() < 0 || value > BigDecimal(MAX_FACTORIAL)) {
            throw ArithmeticException("silnia tylko z liczby naturalnej")
        }
        var result = BigDecimal.ONE
        for (i in 2..value.intValueExact()) result = result.multiply(BigDecimal(i), context)
        return result
    }

    /**
     * Sinus, cosinus i tangens. W stopniach kąty z osi (0°, 90°, 180°, 270°)
     * liczymy dokładnie - double dawałby cos 90° = 6E−17 i tg 90° jako liczbę
     * zamiast błędu. W radianach to samo robi przycięcie śmieci po double:
     * sin π to 0, a nie 1,2E−16.
     */
    private fun trig(name: String, angle: BigDecimal, degrees: Boolean): BigDecimal {
        // Kąt w stopniach sprowadzamy do jednego obrotu jeszcze na BigDecimal -
        // sin 36000030° ma dać dokładnie 0,5, a nie to, co zostanie z double.
        val turn = if (degrees) {
            angle.remainder(THREE_SIXTY).let { if (it.signum() < 0) it.add(THREE_SIXTY) else it }
        } else {
            angle
        }
        if (degrees) {
            val quarter = if (turn.remainder(BigDecimal(90)).signum() == 0) turn.toInt() / 90 else -1
            if (quarter >= 0) {
                val sin = intArrayOf(0, 1, 0, -1)[quarter]
                val cos = intArrayOf(1, 0, -1, 0)[quarter]
                return when (name) {
                    "sin" -> BigDecimal(sin)
                    "cos" -> BigDecimal(cos)
                    else -> if (cos == 0) throw ArithmeticException("tangens 90°") else BigDecimal(sin * cos)
                }
            }
        }
        val radians = if (degrees) Math.toRadians(turn.toDouble()) else turn.toDouble()
        if (radians.isInfinite() || radians.isNaN()) throw ArithmeticException("poza zakresem")
        val tiny = 1e-15 * max(1.0, abs(radians))
        fun clean(x: Double) = if (abs(x) < tiny) 0.0 else x
        val sin = clean(Math.sin(radians))
        val cos = clean(Math.cos(radians))
        return when (name) {
            "sin" -> fromDouble(sin)
            "cos" -> fromDouble(cos)
            else -> {
                if (cos == 0.0) throw ArithmeticException("tangens nieokreślony")
                fromDouble(sin / cos)
            }
        }
    }

    private fun inverseTrig(name: String, value: BigDecimal, degrees: Boolean): BigDecimal {
        val x = value.toDouble()
        val radians = when (name) {
            "sin" -> if (abs(x) > 1.0) throw ArithmeticException("poza dziedziną") else Math.asin(x)
            "cos" -> if (abs(x) > 1.0) throw ArithmeticException("poza dziedziną") else Math.acos(x)
            else -> Math.atan(x)
        }
        return fromDouble(if (degrees) Math.toDegrees(radians) else radians)
    }

    /* --- Czytanie działania ------------------------------------------- */

    private val BARE_PERCENT = Regex("""[0-9.]+%""")

    /** Funkcje z nawiasem - dłuższe nazwy przed krótszymi. */
    private val FUNCTIONS = listOf(
        "sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "ln(", "log(",
    )

    private class Parser(private val text: String, private val degrees: Boolean) {
        private var at = 0

        fun done() = at == text.length

        fun expression(): BigDecimal {
            var value = term()
            while (at < text.length) {
                val op = text[at]
                if (op != '+' && op != '−' && op != '-') return value
                at++
                val start = at
                var right = term()
                // „200 + 10%" to 220, jak w kalkulatorze w telefonie:
                // procent przy dodawaniu i odejmowaniu liczy się od tego,
                // co stoi po lewej, a nie od stu.
                if (BARE_PERCENT.matches(text.substring(start, at))) {
                    right = value.multiply(right, context)
                }
                value = if (op == '+') value.add(right, context) else value.subtract(right, context)
            }
            return value
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (at < text.length) {
                value = when (text[at]) {
                    '×', '*' -> { at++; value.multiply(factor(), context) }
                    '÷', '/' -> {
                        at++
                        val divisor = factor()
                        if (divisor.signum() == 0) throw ArithmeticException("dzielenie przez zero")
                        value.divide(divisor, context)
                    }
                    else -> return value
                }
            }
            return value
        }

        /** Znak liczby i pierwiastki. Potęga wiąże mocniej: −2^2 = −4. */
        private fun factor(): BigDecimal {
            if (at < text.length && (text[at] == '−' || text[at] == '-')) {
                at++
                return factor().negate()
            }
            // Pierwiastek obejmuje najbliższą liczbę albo nawias: √9×4 = 12.
            if (at < text.length && text[at] == '√') {
                at++
                return squareRoot(factor())
            }
            if (at < text.length && text[at] == '∛') {
                at++
                return cubeRoot(factor())
            }
            return power()
        }

        /** Potęga, od prawej: 2^3^2 = 2^9. Wykładnik może mieć znak: 2^−3. */
        private fun power(): BigDecimal {
            val base = postfix()
            if (at < text.length && text[at] == '^') {
                at++
                return raise(base, factor())
            }
            return base
        }

        private fun postfix(): BigDecimal {
            var value = primary()
            while (at < text.length) {
                value = when {
                    text[at] == '%' -> { at++; value.divide(HUNDRED, context) }
                    text[at] == '!' -> { at++; factorial(value) }
                    text[at] == '²' -> { at++; value.multiply(value, context) }
                    text[at] == '³' -> { at++; value.pow(3, context) }
                    text.startsWith("⁻¹", at) -> {
                        at += 2
                        if (value.signum() == 0) throw ArithmeticException("dzielenie przez zero")
                        BigDecimal.ONE.divide(value, context)
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun primary(): BigDecimal {
            if (at >= text.length) throw IllegalStateException("brak liczby")
            when (text[at]) {
                '(' -> {
                    at++
                    val value = expression()
                    closeParen()
                    return value
                }
                'π' -> { at++; return PI_VALUE }
                'e' -> { at++; return E_VALUE }
            }
            val function = FUNCTIONS.firstOrNull { text.startsWith(it, at) }
            if (function != null) {
                at += function.length
                val argument = expression()
                closeParen()
                val name = function.substringBefore('(')
                return when (name) {
                    "sin", "cos", "tan" -> trig(name, argument, degrees)
                    "sin⁻¹", "cos⁻¹", "tan⁻¹" -> inverseTrig(name.substringBefore('⁻'), argument, degrees)
                    "ln" -> fromDouble(log10Of(argument) * ln(10.0))
                    else -> fromDouble(log10Of(argument))
                }
            }
            return number()
        }

        private fun closeParen() {
            if (at >= text.length || text[at] != ')') throw IllegalStateException("brak nawiasu")
            at++
        }

        private fun number(): BigDecimal {
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
            val digits = text.substring(start, at)
            if (digits.isEmpty() || digits == ".") throw IllegalStateException("brak liczby")
            var exponent = ""
            // Zapis naukowy wyniku: 1.5E+20, 2E−7.
            if (at < text.length && text[at] == 'E') {
                at++
                val sign = if (at < text.length && text[at] in "+−-") text[at++] else '+'
                val from = at
                while (at < text.length && text[at].isDigit()) at++
                if (from == at) throw IllegalStateException("brak wykładnika")
                exponent = "E" + (if (sign == '+') "" else "-") + text.substring(from, at)
            }
            return BigDecimal(digits + exponent)
        }
    }
}

/**
 * Działanie tak, jak stoi na wyświetlaczu: przecinek dziesiętny po polsku
 * i odstępy co trzy cyfry w dużych liczbach (1 234 567), jak w telefonie.
 * Liczenie zawsze idzie na napisie z kropką - to tylko wygląd.
 */
object CalculatorDisplay {
    private val NUMBER = Regex("""(\d+)(\.\d*)?(E[+−-]?\d+)?""")

    fun shown(expression: String, decimal: Char, group: Char): String =
        NUMBER.replace(expression) { match ->
            val whole = match.groupValues[1]
            val fraction = match.groupValues[2].replace('.', decimal)
            grouped(whole, group) + fraction + match.groupValues[3]
        }

    private fun grouped(digits: String, group: Char): String {
        if (digits.length <= 3) return digits
        val out = StringBuilder()
        digits.forEachIndexed { index, ch ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(group)
            out.append(ch)
        }
        return out.toString()
    }
}
