package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import wojtoteka.ovh.kajet.core.model.RichText
import wojtoteka.ovh.kajet.core.model.FormatSpan
import wojtoteka.ovh.kajet.core.model.SpanType
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.ParagraphAlign

/**
 * Formaty zapamiętane na przyszłość.
 *
 * Kto naciśnie pogrubienie bez zaznaczenia, nie chce pogrubić niczego wstecz -
 * chce, żeby pogrubione było to, co zaraz napisze. Do czasu napisania format
 * czeka tutaj, a pasek narzędzi pokazuje go jako zapalony.
 */
data class PendingFormat(
    /** Formaty do nadania, razem z wartością (barwa, liczba pikseli). */
    val on: Map<SpanType, String> = emptyMap(),
    /** Formaty do zdjęcia - kursor stoi w pogrubieniu, a dalej ma być zwykłe. */
    val off: Set<SpanType> = emptySet(),
) {
    val isEmpty: Boolean get() = on.isEmpty() && off.isEmpty()

    fun with(type: SpanType, value: String = ""): PendingFormat =
        copy(on = on + (type to value), off = off - type)

    fun without(type: SpanType): PendingFormat =
        copy(on = on - type, off = off + type)

    /** Czy ten format będzie miał tekst pisany od kursora, przy [active] pod kursorem. */
    fun willHave(type: SpanType, active: Boolean): Boolean = when {
        on.containsKey(type) -> true
        type in off -> false
        else -> active
    }
}

/** Formaty znaku po nałożeniu tych, które czekają na pisanie. */
internal fun PendingFormat.appliedTo(attrs: RichTextCodec.Attrs): RichTextCodec.Attrs {
    var result = attrs
    for ((type, value) in on) result = result.with(type, value)
    for (type in off) result = result.without(type)
    return result
}

/**
 * Zapis notatki w widoku surowego markdownu i ułożenie akapitów.
 *
 * Formaty znaku i budowa akapitu z paska narzędzi idą przez [TextCommands],
 * pisanie w widoku notatki - przez [TextEdit]. Tutaj zostaje to, co działa
 * wprost na zapisie: format czekający na pisanie w surowym markdownie,
 * ułożenie akapitów i wstawianie gotowych kawałków.
 */
object TextFormat {

    /** Zakres wielkości pisma fragmentu - szerszy niż całej notatki, bo
     *  pojedyncze słowo może być i drobnym przypisem, i wielkim tytułem. */
    const val SMALLEST_FRAGMENT = 8f
    const val LARGEST_FRAGMENT = 72f

    /**
     * Nadaje zapamiętane formaty tekstowi dopiero co wpisanemu.
     *
     * Wywoływane po zmianie treści: [previous] to zapis sprzed naciśnięcia
     * klawisza. Jeśli człowiek dopisał znaki, dostają one formaty z [pending] -
     * tak samo, jak w każdym porządnym edytorze. Zwraca null, gdy nie było
     * czego formatować (kasowanie, sam ruch kursora).
     */
    fun applyPending(
        field: TextFieldValue,
        previous: String,
        pending: PendingFormat,
    ): TextFieldValue? {
        if (pending.isEmpty) return null
        val change = change(previous, field.text) ?: return null
        if (change.added.isEmpty()) return null

        val parsed = RichTextCodec.read(field.text)
        val from = parsed.plainOffset(change.from)
        val to = parsed.plainOffset(change.from + change.added.length)
        if (from >= to) return null

        val plain = parsed.rich.text
        var attrs = parsed.attrs
        for ((type, value) in pending.on) {
            attrs = RichTextCodec.apply(plain, attrs, from, to, type, value).second
        }
        for (type in pending.off) {
            attrs = RichTextCodec.remove(plain, attrs, from, to, type).second
        }

        val rendered = RichTextCodec.write(plain, attrs)
        return TextFieldValue(rendered.markdown, TextRange(rendered.sourceEndOf(to)))
    }

    /** Wynik pisania: nowe pole i formaty, które mają iść dalej. */
    class Typed(val field: TextFieldValue, val carry: PendingFormat)

    /**
     * Klawisz nowej linii.
     *
     * Kursor po nadaniu formatu stoi MIĘDZY znacznikami, żeby dalsze pisanie
     * szło dalej pogrubione. Znak nowej linii wstawiony w to miejsce rozcinał
     * jednak znaczniki na pół („**tekst\n**"), a formaty nie przechodzą przez
     * koniec wiersza - obie połówki stawały się zwykłym tekstem i gwiazdki
     * wychodziły na wierzch.
     *
     * Dlatego nowa linia idzie przez model, a nie przez ciąg znaków: znak
     * końca wiersza nie nosi formatów, więc zapis składa się z powrotem jako
     * „**tekst**\n". Formaty spod kursora wracają w [Typed.carry] i obejmują
     * to, co człowiek napisze w nowym wierszu.
     *
     * Przy okazji ciągnie się lista: po „1. mleko" nowy wiersz zaczyna się od
     * „2. ", a nowa linia w pustej pozycji listę kończy.
     *
     * Null, gdy zmiana nie jest wstawieniem nowej linii.
     */
    fun typedNewline(
        previous: String,
        typed: TextFieldValue,
        pending: PendingFormat,
    ): Typed? {
        val change = change(previous, typed.text) ?: return null
        val inserted = change.added
        if ('\n' !in inserted) return null

        val parsed = RichTextCodec.read(previous)
        val pFrom = parsed.plainOffset(change.from)
        val pTo = parsed.plainOffset(change.removedTo)

        val plain = parsed.rich.text

        /*
          Czy kursor stoi MIĘDZY znacznikami, czy już za nimi. Po nadaniu
          formatu stoi w środku - i tylko wtedy nowa linia rozcina znacznik,
          i tylko wtedy format ma iść dalej. Kursor za domknięciem znaczy, że
          format się skończył: nowy wiersz zaczyna się zwykłym pismem.
        */
        val insideMarkers = change.from == parsed.sourceEnd(pFrom)
        val inside = if (insideMarkers && pFrom > 0) {
            parsed.attrs[pFrom - 1]
        } else {
            RichTextCodec.NONE
        }
        val carried = pending.appliedTo(inside)

        // Sam Enter (a nie wklejony kawałek tekstu) ciągnie listę dalej.
        val line = plain.substring(plain.lastIndexOf('\n', pFrom - 1) + 1, pFrom)
        val item = if (inserted == "\n") listItem.find(line) else null

        /*
          Kursor nie stoi w żadnym znaczniku, nie ma formatu do przeniesienia
          i nie ma listy do ciągnięcia: nowa linia nie ma po co przechodzić
          przez model. Zapis zostaje wtedy taki, jaki przyszedł z klawiatury.

          Sam warunek na [carried] nie wystarczał: kto zdejmował format przed
          naciśnięciem Enter, trafiał tu z pustym [carried] mimo kursora
          w środku znaczników - i nowa linia znów je rozcinała.
        */
        if (inside == RichTextCodec.NONE && carried == RichTextCodec.NONE && item == null) {
            return null
        }

        if (item != null && item.groupValues[5].isBlank()) {
            // Nowa linia w pustej pozycji: koniec listy. Znacznik znika,
            // a wiersz zostaje pusty - tak kończy się każda lista zakupów.
            val lineStart = pFrom - line.length
            return rebuild(
                plain = plain.substring(0, lineStart) + plain.substring(pTo),
                attrs = parsed.attrs.subList(0, lineStart) + parsed.attrs.subList(pTo, plain.length),
                cursor = lineStart,
                carry = carryOf(carried),
            )
        }

        val marker = item?.let { nextMarker(it) }.orEmpty()
        val added0 = inserted + marker

        val attrs = ArrayList<RichTextCodec.Attrs>(plain.length + added0.length)
        attrs += parsed.attrs.subList(0, pFrom)
        for (ch in added0) {
            // Koniec wiersza i znacznik listy to budowa notatki, nie format.
            attrs += if (ch == '\n' || marker.isNotEmpty()) RichTextCodec.NONE else carried
        }
        attrs += parsed.attrs.subList(pTo, plain.length)

        return rebuild(
            plain = plain.substring(0, pFrom) + added0 + plain.substring(pTo),
            attrs = attrs,
            cursor = pFrom + added0.length,
            carry = carryOf(carried),
        )
    }

    private fun rebuild(
        plain: String,
        attrs: List<RichTextCodec.Attrs>,
        cursor: Int,
        carry: PendingFormat,
    ): Typed {
        val rendered = RichTextCodec.write(plain, attrs)
        return Typed(
            field = TextFieldValue(rendered.markdown, TextRange(rendered.sourceEndOf(cursor))),
            carry = carry,
        )
    }

    /**
     * Kasowanie w polu, w którym znaczników nie widać.
     *
     * Znaczniki są schowane, ale nadal siedzą w treści - a Compose kasuje
     * znaki ZAPISU, nie te, które widać. Kursor stojący za „</span>" i jedno
     * naciśnięcie Backspace zabierało z niego „>", rozbity znacznik przestawał
     * być znacznikiem i pokazywał się w notatce jako goły tekst. Tak samo
     * ginęła jedna gwiazdka z pary.
     *
     * Dlatego kasowanie idzie przez model: liczy się to, co widać. Zniknięcie
     * samych znaczników znaczy „człowiek celował w znak przed nimi", a zapis
     * składa się z powrotem - bez pustych par i bez połówek znaczników.
     *
     * Obsługuje też podmianę: zaznaczenie zastąpione pisaniem bierze formaty
     * tego, co zastępuje. Null, gdy nic nie ubyło (samo dopisywanie idzie
     * zwykłą drogą, żeby nie gubić różnicy między „w znaczniku" i „za nim").
     */
    fun typedDeletion(previous: String, typed: TextFieldValue): TextFieldValue? {
        val change = change(previous, typed.text) ?: return null
        if (change.removedTo <= change.from) return null

        val parsed = RichTextCodec.read(previous)
        val plain = parsed.rich.text
        var pFrom = parsed.plainOffset(change.from)
        val pTo = parsed.plainOffset(change.removedTo)

        if (pFrom == pTo) {
            /*
              Zniknęły same znaczniki, żaden widoczny znak. Jeśli jest co
              zabrać przed nimi - zabieramy to. Jeśli nie ma, kasowanie nie
              miało czego dotknąć i zapis wraca w całości.
            */
            if (pFrom == 0 || plain.isEmpty()) {
                val whole = RichTextCodec.write(plain, parsed.attrs)
                return TextFieldValue(whole.markdown, TextRange(whole.sourceEndOf(0)))
            }
            pFrom--
        }

        // Wpisane w miejsce zaznaczenia bierze formaty tego, co zastąpiło.
        val carried = parsed.attrs.getOrNull(pFrom) ?: RichTextCodec.NONE

        val attrs = ArrayList<RichTextCodec.Attrs>(plain.length)
        attrs += parsed.attrs.subList(0, pFrom)
        for (ch in change.added) {
            attrs += if (ch == '\n') RichTextCodec.NONE else carried
        }
        attrs += parsed.attrs.subList(pTo, plain.length)

        val rest = plain.substring(0, pFrom) + change.added + plain.substring(pTo)
        val rendered = RichTextCodec.write(rest, attrs)
        return TextFieldValue(
            rendered.markdown,
            TextRange(rendered.sourceEndOf(pFrom + change.added.length)),
        )
    }

    /** Wiersz listy: wcięcie, znacznik, ewentualny numer i treść. */
    private val listItem =
        Regex("""^(\s*)([-*+] \[[ xX]] |[-*+] |(\d+)([.)]) )(.*)$""")

    /** Znacznik następnej pozycji listy. Numer rośnie, zadanie wraca puste. */
    private fun nextMarker(item: MatchResult): String {
        val indent = item.groupValues[1]
        val number = item.groupValues[3]
        if (number.isNotEmpty()) {
            return indent + ((number.toIntOrNull() ?: 1) + 1) + item.groupValues[4] + " "
        }
        return indent + item.groupValues[2].replace(checkedBox, "[ ]")
    }

    private val checkedBox = Regex("""\[[xX]]""")

    internal fun carryOf(attrs: RichTextCodec.Attrs): PendingFormat {
        var carry = PendingFormat()
        for (type in SpanType.entries) {
            if (attrs.has(type)) carry = carry.with(type, attrs.valueOf(type))
        }
        return carry
    }


    /**
     * Co się zmieniło między dwoma zapisami: od [from] zniknęło wszystko do
     * [removedTo], a w to miejsce weszło [added]. Liczone od wspólnego
     * początku i wspólnego końca, więc obejmuje i dopisanie, i skasowanie,
     * i podmianę zaznaczenia. Null, gdy zapisy są takie same.
     */
    internal class Change(val from: Int, val removedTo: Int, val added: String)

    internal fun change(before: String, after: String): Change? {
        if (before == after) return null

        val shorter = minOf(before.length, after.length)
        var start = 0
        while (start < shorter && before[start] == after[start]) start++

        var tail = 0
        while (
            tail < shorter - start &&
            before[before.length - 1 - tail] == after[after.length - 1 - tail]
        ) {
            tail++
        }

        return Change(
            from = start,
            removedTo = before.length - tail,
            added = after.substring(start, after.length - tail),
        )
    }

    // --- Ułożenie akapitu ---

    /** Ułożenie zapisane w wierszu pod kursorem; null, gdy wiersz nie ma własnego. */
    fun alignAt(field: TextFieldValue): NoteAlign? {
        val content = field.text
        val at = field.selection.start.coerceIn(0, content.length)
        val start = content.lastIndexOf('\n', (at - 1).coerceAtLeast(0))
            .let { if (it < 0 || at == 0) 0 else it + 1 }
        val end = content.indexOf('\n', start).let { if (it < 0) content.length else it }
        return ParagraphAlign.alignOf(content.substring(start, end))
    }

    /**
     * Ułożenie akapitów pod kursorem albo w zaznaczeniu - i tylko ich, jak
     * w Wordzie. Ułożenie równe [noteDefault] (ułożeniu całej notatki) nie
     * potrzebuje znacznika, więc znika z wiersza.
     *
     * Kursor i zaznaczenie zostają przy tych samych słowach.
     */
    fun alignLines(field: TextFieldValue, align: NoteAlign, noteDefault: NoteAlign): TextFieldValue {
        val content = field.text
        val from = field.selection.min.coerceIn(0, content.length)
        val to = field.selection.max.coerceIn(from, content.length)
        val firstLine = content.lastIndexOf('\n', (from - 1).coerceAtLeast(0))
            .let { if (it < 0 || from == 0) 0 else it + 1 }
        val lastLine = content.indexOf('\n', to).let { if (it < 0) content.length else it }
        val many = content.substring(firstLine, lastLine).contains('\n')
        val wanted = if (align == noteDefault) null else align

        return realign(field, firstLine, lastLine) { line, inCode ->
            val inner = ParagraphAlign.unwrap(line)
            when {
                inCode || !alignable(inner) -> line
                // W zaznaczeniu przez kilka akapitów puste wiersze to tylko
                // odstęp między nimi - nie dostają znacznika.
                many && inner.isBlank() -> line
                else -> ParagraphAlign.wrap(inner, wanted)
            }
        }
    }

    /**
     * Jedno ułożenie przy każdym akapicie notatki - przejście ze starego
     * ułożenia całej notatki na ułożenie akapitów.
     */
    fun alignEveryLine(markdown: String, align: NoteAlign): String {
        val wanted = if (align == NoteAlign.LEFT) null else align
        return realign(TextFieldValue(markdown), 0, markdown.length) { line, inCode ->
            val inner = ParagraphAlign.unwrap(line)
            if (inCode || inner.isBlank() || !alignable(inner) || ParagraphAlign.openingLength(line) > 0) {
                line
            } else {
                ParagraphAlign.wrap(inner, wanted)
            }
        }.text
    }

    /** Czy wiersz może mieć własne ułożenie - zdjęcia, tabele i zadania mają swoje. */
    private fun alignable(inner: String): Boolean {
        val trimmed = inner.trim()
        return when {
            RichTextCodec.opensFence(trimmed) != null -> false
            trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1 -> false
            ImageLines.read(inner) != null -> false
            Blocks.isTaskLine(inner) -> false
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> false
            else -> true
        }
    }

    /**
     * Przepisuje wiersze od [from] do [to] (granice wierszy) funkcją [change]
     * i przestawia zaznaczenie tak, żeby zostało przy tej samej treści.
     * [change] dostaje też informację, czy wiersz leży w bloku kodu.
     */
    private fun realign(
        field: TextFieldValue,
        from: Int,
        to: Int,
        change: (line: String, inCode: Boolean) -> String,
    ): TextFieldValue {
        val content = field.text

        class Row(val oldStart: Int, val old: String, val newStart: Int, val new: String)

        val rows = ArrayList<Row>()
        val out = StringBuilder()
        // Blok kodu liczy się od początku treści, nie od zaznaczenia.
        var fence: String? = null
        var lineStart = 0
        while (true) {
            val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
            val line = content.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val inCode = when {
                fence != null -> {
                    if (RichTextCodec.closesFence(trimmed, fence)) fence = null
                    true
                }

                RichTextCodec.opensFence(trimmed) != null -> {
                    fence = RichTextCodec.opensFence(trimmed)
                    true
                }

                else -> false
            }
            val rewritten = if (lineStart in from..to) change(line, inCode) else line
            rows += Row(lineStart, line, out.length, rewritten)
            out.append(rewritten)
            if (lineEnd >= content.length) break
            out.append('\n')
            lineStart = lineEnd + 1
        }

        // Pozycja w starej treści -> ta sama treść w nowej. W przepisanym
        // wierszu liczy się od początku treści, za znacznikiem ułożenia.
        fun moved(position: Int): Int {
            val row = rows.lastOrNull { it.oldStart <= position } ?: return position
            val inLine = (position - row.oldStart).coerceIn(0, row.old.length)
            if (row.old == row.new) return row.newStart + inLine
            val oldOpen = ParagraphAlign.openingLength(row.old)
            val oldClose = ParagraphAlign.closingLength(row.old, oldOpen)
            val newOpen = ParagraphAlign.openingLength(row.new)
            val newClose = ParagraphAlign.closingLength(row.new, newOpen)
            val inContent = (inLine - oldOpen).coerceIn(0, row.old.length - oldOpen - oldClose)
            return row.newStart + newOpen + inContent.coerceAtMost(row.new.length - newOpen - newClose)
        }

        return TextFieldValue(
            out.toString(),
            TextRange(moved(field.selection.start), moved(field.selection.end)),
        )
    }

    fun insert(field: TextFieldValue, fragment: String, stepBack: Int = 0): TextFieldValue {
        val content = field.text
        var from = field.selection.min.coerceIn(0, content.length)
        var to = field.selection.max.coerceIn(from, content.length)

        /*
          Fragment od nowego wiersza (linia, blok) to nowy akapit - staje za
          całym wierszem z kursorem, a nie w jego środku. W środku rozciąłby
          wiersz razem z jego znacznikiem ułożenia i domknięcie „</p>"
          wyszłoby na wierzch w następnym wierszu.
        */
        if (fragment.startsWith("\n") && from == to) {
            from = content.indexOf('\n', from).let { if (it < 0) content.length else it }
            to = from
        }

        val next = content.substring(0, from) + fragment + content.substring(to)
        val cursor = (from + fragment.length - stepBack).coerceIn(0, next.length)
        return TextFieldValue(next, TextRange(cursor))
    }
}
