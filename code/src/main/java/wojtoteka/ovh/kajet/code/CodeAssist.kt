package wojtoteka.ovh.kajet.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import wojtoteka.ovh.kajet.core.model.CodeLanguage

/**
 * Drobna pomoc przy pisaniu kodu - taka jak w VS Code, ale bez pisania za
 * człowieka.
 *
 * Robi dokładnie cztery rzeczy i ani jednej więcej:
 *
 *  1. domyka nawias albo cudzysłów, zostawiając kursor w środku,
 *  2. przepuszcza kursor przez domknięcie, które już stoi (żeby nie wychodziło
 *     „))" po dopisaniu własnego),
 *  3. kasuje pustą parę jednym cofnięciem,
 *  4. w HTML domyka znacznik: po wpisaniu `<p>` dostawia `</p>` za kursorem.
 *
 * Czego NIE robi: nie wstawia szkieletów, pętli ani gotowych bloków. Kto pisze
 * `if`, dostaje `if`, a nie trzy linijki, które trzeba potem czytać i kasować.
 *
 * Wszystko tutaj to czyste działania na tekście i zaznaczeniu, bez Compose'a
 * i bez stanu - dzięki temu da się to sprawdzić testami (CodeAssistTest).
 */
object CodeAssist {

    private val PAIRS = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '"' to '"',
        '\'' to '\'',
        '`' to '`',
    )

    private val CLOSERS = PAIRS.values.toSet()

    /** Cudzysłów jest sam sobie domknięciem - trzeba go liczyć osobno. */
    private val SYMMETRIC = setOf('"', '\'', '`')

    /**
     * Znaczniki, które nie mają domknięcia. Dopisanie `</br>` byłoby błędem,
     * a nie pomocą.
     */
    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
        "!doctype",
    )

    /** Otwierający znacznik tuż przed kursorem: `<p>`, `<div class="x">`. */
    private val OPENING_TAG = Regex("""<([A-Za-z][A-Za-z0-9:-]*)(\s[^<>]*)?>$""")

    /**
     * Bierze stan pola sprzed zmiany i po zmianie, oddaje stan, który ma
     * naprawdę wejść do pola. Gdy nie ma czym pomóc, oddaje [after] bez zmian.
     */
    fun assist(
        before: TextFieldValue,
        after: TextFieldValue,
        language: CodeLanguage,
    ): TextFieldValue {
        // Pomagamy tylko przy pisaniu pojedynczym znakiem i kasowaniu jednego.
        // Wklejanie, zaznaczanie i podmiana większego kawałka zostają nietknięte.
        if (!after.selection.collapsed) return after

        return when (after.text.length) {
            before.text.length + 1 -> afterTyping(before, after, language)
            before.text.length - 1 -> afterBackspace(before, after)
            else -> after
        }
    }

    private fun afterTyping(
        before: TextFieldValue,
        after: TextFieldValue,
        language: CodeLanguage,
    ): TextFieldValue {
        val at = after.selection.start
        if (at <= 0 || at > after.text.length) return after

        // Znak, który właśnie wszedł - i pewność, że reszta tekstu się zgadza,
        // czyli że to naprawdę było zwykłe dopisanie w tym miejscu.
        val typed = after.text[at - 1]
        val rebuilt = after.text.removeRange(at - 1, at)
        if (rebuilt != before.text) return after

        val next = after.text.getOrNull(at)

        // 2. Domknięcie już stoi tuż za kursorem - przechodzimy przez nie
        //    zamiast dokładać drugie. Bez tego dopisanie własnego „)" po tym,
        //    które sami dostawiliśmy, dawało „))".
        if (typed in CLOSERS && next == typed) {
            return TextFieldValue(before.text, TextRange(at))
        }

        // 4. Znacznik HTML.
        if (typed == '>' && language == CodeLanguage.HTML) {
            val closing = closingTagFor(after.text.substring(0, at))
            if (closing != null) {
                return TextFieldValue(
                    after.text.substring(0, at) + closing + after.text.substring(at),
                    TextRange(at),
                )
            }
        }

        // 1. Domykamy nawias albo cudzysłów.
        val partner = PAIRS[typed] ?: return after
        if (!roomToClose(typed, next, after.text, at)) return after

        return TextFieldValue(
            after.text.substring(0, at) + partner + after.text.substring(at),
            TextRange(at),
        )
    }

    private fun afterBackspace(before: TextFieldValue, after: TextFieldValue): TextFieldValue {
        val at = after.selection.start
        if (at < 0 || at >= before.text.length) return after

        // 3. Skasowany otwierający, a tuż za kursorem stoi jego para - schodzi
        //    razem z nim. Pusta „()" znika jednym cofnięciem, tak jak weszła.
        val removed = before.text[at]
        val partner = PAIRS[removed] ?: return after
        if (after.text.getOrNull(at) != partner) return after

        return TextFieldValue(after.text.removeRange(at, at + 1), TextRange(at))
    }

    /**
     * Czy jest gdzie domknąć. Za kursorem musi być koniec wiersza, odstęp albo
     * inne domknięcie - inaczej wpisanie nawiasu przed istniejącym słowem
     * dokładałoby domknięcie w środku wyrażenia.
     */
    private fun roomToClose(typed: Char, next: Char?, text: String, at: Int): Boolean {
        if (next != null && !(next.isWhitespace() || next in CLOSERS)) return false

        // Apostrof w słowie to najczęściej apostrof, nie początek napisu:
        // „don't" nie ma się zamienić w „don''t".
        if (typed in SYMMETRIC) {
            val previous = text.getOrNull(at - 2)
            if (previous != null && (previous.isLetterOrDigit() || previous == typed)) return false
        }
        return true
    }

    /** Domknięcie dla znacznika stojącego tuż przed kursorem, albo null. */
    private fun closingTagFor(head: String): String? {
        val match = OPENING_TAG.find(head) ?: return null
        // Znacznik domykający (`</p>`) i sam domknięty (`<br />`) nic nie potrzebują.
        if (match.value.startsWith("</")) return null
        if (match.groupValues[2].trimEnd().endsWith("/")) return null

        val name = match.groupValues[1]
        if (name.lowercase() in VOID_TAGS) return null
        return "</$name>"
    }

    /**
     * Wcięcie z poprzedniego wiersza po naciśnięciu Entera. Osobno od [assist],
     * bo Enter przychodzi jako zwykły znak nowego wiersza i nie ma tu nic do
     * domykania - jest za to co przepisać.
     */
    fun keepIndent(before: TextFieldValue, after: TextFieldValue): TextFieldValue {
        if (!after.selection.collapsed) return after
        if (after.text.length != before.text.length + 1) return after

        val at = after.selection.start
        if (at <= 0 || after.text[at - 1] != '\n') return after

        val lineStart = before.text.lastIndexOf('\n', (at - 2).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val indent = before.text.drop(lineStart).takeWhile { it == ' ' || it == '\t' }
        if (indent.isEmpty()) return after

        return TextFieldValue(
            after.text.substring(0, at) + indent + after.text.substring(at),
            TextRange(at + indent.length),
        )
    }
}
