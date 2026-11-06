package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import wojtoteka.ovh.kajet.core.model.RichTextCodec

/*
  Polecenie dla asystenta pisane wprost w notatce, bez okienka.

  Dwa średniki pod rząd otwierają polecenie: znikają oba, a to, co pisze się
  dalej, nie trafia do notatki - zbiera się tutaj. Enter wysyła, Backspace
  kasuje ostatni znak polecenia, a na pustym poleceniu oddaje jeden średnik,
  jakby skasował ten drugi. [close] porzuca polecenie bez śladu, a ruch
  kursora (stuknięcie gdzie indziej, strzałki) też je kończy - żeby dalsze
  pisanie nie znikało komuś, kto o poleceniu zapomniał.

  Sama logika pola: bez Compose, bez sieci i bez asystenta, więc da się jej
  pilnować zwykłym testem. Wysyłkę i Esc robi TextEditor.
*/
internal class InlinePrompt {

    sealed interface Step {
        /** Nie dotyczy polecenia - zmiana idzie zwykłą drogą. */
        data object Pass : Step

        /** Pole ma zostać takie, a to, co przyszło z klawiatury, przepada. */
        data class Keep(val field: TextFieldValue) : Step

        /** Polecenie gotowe do wysłania; pole jak w [Keep]. */
        data class Send(val field: TextFieldValue, val prompt: String) : Step
    }

    var isOpen: Boolean = false
        private set

    private val prompt = StringBuilder()

    fun close() {
        isOpen = false
        prompt.setLength(0)
    }

    fun onTyped(previous: TextFieldValue, typed: TextFieldValue): Step =
        if (isOpen) collect(previous, typed) else open(previous, typed)

    /** Drugi średnik zaraz za pierwszym: oba znikają, polecenie się otwiera. */
    private fun open(previous: TextFieldValue, typed: TextFieldValue): Step {
        val old = previous.text
        val new = typed.text
        val at = previous.selection.start
        val secondMark = previous.selection.collapsed &&
            at >= 1 &&
            old[at - 1] == MARK &&
            new.length == old.length + 1 &&
            new[at] == MARK &&
            new.regionMatches(0, old, 0, at) &&
            new.regionMatches(at + 1, old, at, old.length - at)
        if (!secondMark) return Step.Pass

        isOpen = true
        prompt.setLength(0)
        return Step.Keep(TextFieldValue(old.removeRange(at - 1, at), TextRange(at - 1)))
    }

    private fun collect(previous: TextFieldValue, typed: TextFieldValue): Step {
        val old = previous.text
        val new = typed.text
        if (old == new) {
            // Sama kompozycja klawiatury zostawia kursor w miejscu; jego ruch
            // znaczy, że człowiek pisze już gdzie indziej.
            if (typed.selection != previous.selection) close()
            return Step.Pass
        }

        val span = TextSpan.of(old, new)
        val removed = old.substring(span.start, span.oldEnd)
        val inserted = new.substring(span.start, span.newEnd)

        if (inserted.isEmpty()) {
            if (prompt.isEmpty()) {
                close()
                val at = previous.selection.start
                return Step.Keep(TextFieldValue(StringBuilder(old).insert(at, MARK).toString(), TextRange(at + 1)))
            }
            prompt.setLength((prompt.length - removed.length).coerceAtLeast(0))
            return Step.Keep(previous)
        }

        /*
          Klawiatura ekranowa potrafi trzymać w kompozycji słowo z notatki tuż
          przed kursorem i podmieniać je razem z nową literą („ma" → „mak").
          Wspólny początek i koniec zostają wyżej, więc i wtedy dopisana jest
          sama nowa litera. Podmiana czegoś na coś innego (autokorekta słowa
          z notatki) nie jest pisaniem polecenia - nie wchodzi ani do niego,
          ani do notatki.
        */
        val added = if (removed.isEmpty()) inserted else ""
        prompt.append(added.substringBefore('\n'))
        if ('\n' !in added) return Step.Keep(previous)

        val ready = prompt.toString().trim()
        close()
        return if (ready.isEmpty()) Step.Keep(previous) else Step.Send(previous, ready)
    }

    companion object {
        const val MARK = ';'

        /** Tyle tekstu sprzed kursora jedzie z poleceniem, żeby było wiadomo, czego dotyczy. */
        private const val CONTEXT = 200

        /**
         * Polecenie razem z miejscem, w którym je napisano. Asystent widzi
         * całą notatkę, ale nie kursor - a „rozwiąż to" bez miejsca nie
         * wiadomo, czego dotyczy.
         */
        fun instruction(prompt: String, before: String): String {
            val tail = before.takeLast(CONTEXT).trim()
            return if (tail.isEmpty()) prompt else "$prompt\n\n(Kursor stoi w notatce tuż za: „$tail”)"
        }

        /**
         * Czy kursor w surowym Markdownie stoi w bloku kodu albo wzoru.
         * Tam dwa średniki są zwykłym kodem - `for (;;)`.
         */
        fun insideFence(text: String, cursor: Int): Boolean {
            var fence: String? = null
            for (line in text.substring(0, cursor.coerceIn(0, text.length)).lineSequence()) {
                val trimmed = line.trimStart()
                val open = fence
                fence = if (open == null) {
                    RichTextCodec.opensFence(trimmed)
                } else if (RichTextCodec.closesFence(trimmed, open)) {
                    null
                } else {
                    open
                }
            }
            return fence != null
        }
    }
}
