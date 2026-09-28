package wojtoteka.ovh.kajet.core.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/*
  Zwykły kalkulator na wierzchu notatki.

  Działanie trzymamy jako napis, tak jak widać je na wyświetlaczu, i liczymy
  od nowa po każdym klawiszu - dzięki temu wynik widać już w trakcie pisania,
  a Enter („=") tylko go zatwierdza. Liczymy na BigDecimal, bo na double
  0,1 + 0,2 daje 0,30000000000000004, a tego w zeszycie nikt nie chce czytać.

  W napisie działania kropka dziesiętna jest zawsze kropką, a znaki działań
  są te, które stoją na klawiszach (×, ÷, −). Przecinek zamiast kropki to
  sprawa wyświetlacza, nie liczenia.
*/

enum class CalcKey(val symbol: String) {
    D0("0"), D1("1"), D2("2"), D3("3"), D4("4"),
    D5("5"), D6("6"), D7("7"), D8("8"), D9("9"),
    DOT("."),
    PLUS("+"), MINUS("−"), TIMES("×"), DIVIDE("÷"),
    PERCENT("%"),
    PARENS("()"),
    BACKSPACE("⌫"),
    CLEAR("C"),
    EQUALS("="),
}

/**
 * Stan kalkulatora: działanie na wyświetlaczu i to, czy stoi w nim wynik po
 * „=". Po wyniku cyfra zaczyna nowe działanie, a znak działania liczy dalej
 * od wyniku - tak jak w każdym kalkulatorze kieszonkowym.
 */
data class CalculatorState(
    val expression: String = "",
    val showsResult: Boolean = false,
    val error: Boolean = false,
) {
    /** Wynik bieżącego działania albo null, gdy działanie jest niepełne. */
    val preview: BigDecimal? get() = Calculator.evaluate(expression)

    fun press(key: CalcKey): CalculatorState = when (key) {
        CalcKey.CLEAR -> CalculatorState()
        CalcKey.BACKSPACE -> if (showsResult || error) {
            CalculatorState()
        } else {
            copy(expression = expression.dropLast(1))
        }
        CalcKey.EQUALS -> {
            if (expression.isEmpty()) {
                this
            } else {
                val value = Calculator.evaluate(closeParens(expression))
                if (value == null) {
                    copy(error = true)
                } else {
                    CalculatorState(Calculator.format(value), showsResult = true)
                }
            }
        }
        else -> typed(key)
    }

    private fun typed(key: CalcKey): CalculatorState {
        val base = when {
            // Po wyniku cyfra zaczyna od nowa, znak działania liczy dalej.
            showsResult && (key.isDigit() || key == CalcKey.DOT || key == CalcKey.PARENS) -> ""
            else -> expression
        }
        val next = when (key) {
            CalcKey.DOT -> dot(base)
            CalcKey.PLUS, CalcKey.TIMES, CalcKey.DIVIDE -> operator(base, key.symbol)
            CalcKey.MINUS -> minus(base)
            CalcKey.PERCENT -> if (base.lastOrNull()?.let { it.isDigit() || it == ')' } == true) {
                "$base%"
            } else {
                base
            }
            CalcKey.PARENS -> parens(base)
            else -> digit(base, key.symbol)
        }
        if (next.length > MAX_LENGTH) return this
        return CalculatorState(next)
    }

    private fun CalcKey.isDigit() = symbol.length == 1 && symbol[0].isDigit()

    private companion object {
        const val MAX_LENGTH = 64
        const val OPERATORS = "+−×÷"

        fun lastNumber(text: String): String = text.takeLastWhile { it.isDigit() || it == '.' }

        fun digit(text: String, d: String): String {
            // Po procencie albo nawiasie zamykającym cyfra znaczy mnożenie.
            if (text.lastOrNull() == '%' || text.lastOrNull() == ')') return "$text×$d"
            // „007" to 7: zero na początku liczby ustępuje następnej cyfrze.
            if (lastNumber(text) == "0") return text.dropLast(1) + d
            return text + d
        }

        fun dot(text: String): String {
            val number = lastNumber(text)
            if (number.contains('.')) return text
            if (text.lastOrNull() == '%' || text.lastOrNull() == ')') return "$text×0."
            return if (number.isEmpty()) text + "0." else "$text."
        }

        fun operator(text: String, op: String): String {
            if (text.isEmpty() || text.last() == '(') return text
            // Drugi znak z rzędu zamienia poprzedni, zamiast psuć działanie.
            val trimmed = text.dropLastWhile { it in OPERATORS || it == '.' }
            if (trimmed.isEmpty() || trimmed.last() == '(') return trimmed
            return trimmed + op
        }

        fun minus(text: String): String {
            // Minus na początku albo po nawiasie to znak liczby.
            if (text.isEmpty() || text.last() == '(') return "$text−"
            if (text.last() in "×÷") return "$text−"
            return operator(text, "−")
        }

        fun parens(text: String): String {
            val open = text.count { it == '(' } - text.count { it == ')' }
            val last = text.lastOrNull()
            val closes = open > 0 && last != null && (last.isDigit() || last == ')' || last == '%')
            return when {
                closes -> "$text)"
                last == null || last in OPERATORS || last == '(' -> "$text("
                // Liczba przed nawiasem to mnożenie: 2(3+4) = 2×(3+4).
                else -> "$text×("
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

    /**
     * Liczy działanie z klawiatury kalkulatora. Zwraca null, gdy działanie
     * jest niepełne albo nie da się go policzyć (na przykład dzielenie przez
     * zero). Nawiasy niezamknięte na końcu zamyka sam.
     */
    fun evaluate(expression: String): BigDecimal? {
        if (expression.isBlank()) return null
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        if (open < 0) return null
        val closed = expression + ")".repeat(open)
        return runCatching {
            val parser = Parser(closed)
            val value = parser.expression()
            if (!parser.done()) null else value
        }.getOrNull()
    }

    /**
     * Wynik do pokazania: dwanaście cyfr znaczących, bez zer na końcu i bez
     * notacji naukowej, z kropką dziesiętną - przecinek dokłada wyświetlacz.
     * Taki napis da się od razu liczyć dalej.
     */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        return rounded.toPlainString().replace('-', '−')
    }

    private val BARE_PERCENT = Regex("""[0-9.]+%""")

    private class Parser(private val text: String) {
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
                // „200 + 10%" to 220, jak w kalkulatorze kieszonkowym:
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
            var value = unary()
            while (at < text.length) {
                value = when (text[at]) {
                    '×', '*' -> { at++; value.multiply(unary(), context) }
                    '÷', '/' -> {
                        at++
                        val divisor = unary()
                        if (divisor.signum() == 0) throw ArithmeticException("dzielenie przez zero")
                        value.divide(divisor, context)
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun unary(): BigDecimal {
            if (at < text.length && (text[at] == '−' || text[at] == '-')) {
                at++
                return unary().negate()
            }
            return postfix()
        }

        private fun postfix(): BigDecimal {
            var value = primary()
            while (at < text.length && text[at] == '%') {
                at++
                value = value.divide(BigDecimal(100), context)
            }
            return value
        }

        private fun primary(): BigDecimal {
            if (at >= text.length) throw IllegalStateException("brak liczby")
            if (text[at] == '(') {
                at++
                val value = expression()
                if (at >= text.length || text[at] != ')') throw IllegalStateException("brak nawiasu")
                at++
                return value
            }
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
            val number = text.substring(start, at)
            if (number.isEmpty() || number == ".") throw IllegalStateException("brak liczby")
            return BigDecimal(number)
        }
    }
}
