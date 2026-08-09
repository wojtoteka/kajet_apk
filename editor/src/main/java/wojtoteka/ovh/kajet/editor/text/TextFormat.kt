package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Formaty zapamiętane na przyszłość.
 *
 * Kto naciśnie pogrubienie bez zaznaczenia, nie chce pogrubić niczego wstecz —
 * chce, żeby pogrubione było to, co zaraz napisze. Do czasu napisania format
 * czeka tutaj, a pasek narzędzi pokazuje go jako zapalony.
 */
data class PendingFormat(
    /** Formaty do nadania, razem z wartością (barwa, liczba pikseli). */
    val on: Map<SpanType, String> = emptyMap(),
    /** Formaty do zdjęcia — kursor stoi w pogrubieniu, a dalej ma być zwykłe. */
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

/**
 * Formatowanie fragmentu notatki.
 *
 * Wszystko idzie przez [RichText]: zapis schodzi do czystego tekstu z listą
 * zakresów, zmienia się zakres, zapis składa się z powrotem. Znaczniki nie są
 * tu nigdy doklejane do ciągu znaków, więc nie mają jak się zagnieździć,
 * osierocić ani wyjść poza zaznaczenie.
 *
 * Każda z tych funkcji rusza WYŁĄCZNIE zaznaczenie. Bez zaznaczenia oddaje
 * null — wtedy format idzie do [PendingFormat] i czeka na pisanie.
 */
object TextFormat {

    /** Zakres wielkości pisma fragmentu — szerszy niż całej notatki, bo
     *  pojedyncze słowo może być i drobnym przypisem, i wielkim tytułem. */
    const val SMALLEST_FRAGMENT = 8f
    const val LARGEST_FRAGMENT = 72f

    /** Formaty obejmujące całe zaznaczenie; bez zaznaczenia — te pod kursorem. */
    fun formatsIn(field: TextFieldValue): Set<FormatSpan> {
        val parsed = RichTextCodec.read(field.text)
        val from = parsed.plainOffset(field.selection.min)
        val to = parsed.plainOffset(field.selection.max)
        return parsed.rich.formatsIn(from, to)
    }

    /** Czy zaznaczenie (albo miejsce pod kursorem) ma już ten format. */
    fun has(field: TextFieldValue, type: SpanType): Boolean =
        formatsIn(field).any { it.type == type }

    /**
     * Nadaje albo zdejmuje format zaznaczenia. Fragment, który format już ma
     * w całości, traci go — drugie naciśnięcie przycisku zdejmuje pogrubienie.
     */
    fun toggle(field: TextFieldValue, type: SpanType, value: String = ""): TextFieldValue? =
        edit(field, trimEdges = true) { parsed, from, to ->
            val whole = (from until to).all { i ->
                parsed.rich.text[i] == '\n' || parsed.attrs[i].has(type)
            }
            if (whole) {
                RichTextCodec.remove(parsed.rich.text, parsed.attrs, from, to, type)
            } else {
                RichTextCodec.apply(parsed.rich.text, parsed.attrs, from, to, type, value)
            }
        }

    /**
     * Nadaje zaznaczeniu barwę pisma ([argb]; null zdejmuje barwę). Barwa już
     * obecna jest PODMIENIANA, nie obudowywana drugim znacznikiem.
     */
    fun applyColor(field: TextFieldValue, argb: Int?): TextFieldValue? = edit(field) { parsed, from, to ->
        if (argb == null) {
            RichTextCodec.remove(parsed.rich.text, parsed.attrs, from, to, SpanType.COLOR)
        } else {
            RichTextCodec.apply(
                parsed.rich.text,
                parsed.attrs,
                from,
                to,
                SpanType.COLOR,
                RichTextCodec.colorHex(argb),
            )
        }
    }

    /**
     * Zmienia wielkość pisma zaznaczonego fragmentu o [delta] punktów. Kolejne
     * naciśnięcia przestawiają liczbę zamiast zagnieżdżać znaczniki, a powrót
     * do wielkości notatki ([base]) zdejmuje znacznik całkiem.
     */
    fun resize(field: TextFieldValue, delta: Float, base: Float): TextFieldValue? =
        edit(field) { parsed, from, to ->
            val current = parsed.attrs.getOrNull(from)?.sizePx ?: base
            val resized = (current + delta).coerceIn(SMALLEST_FRAGMENT, LARGEST_FRAGMENT)
            if (resized == base) {
                RichTextCodec.remove(parsed.rich.text, parsed.attrs, from, to, SpanType.SIZE)
            } else {
                RichTextCodec.apply(
                    parsed.rich.text,
                    parsed.attrs,
                    from,
                    to,
                    SpanType.SIZE,
                    RichTextCodec.sizeText(resized),
                )
            }
        }

    /**
     * Barwa pisma zaznaczenia albo tego, co pod kursorem; null, gdy fragment
     * nie ma własnej barwy. Po tym okno z tęczą otwiera się na barwie, którą
     * fragment już ma, zamiast na czymkolwiek.
     */
    fun colorIn(field: TextFieldValue): Int? {
        val parsed = RichTextCodec.read(field.text)
        val at = parsed.plainOffset(field.selection.min)
        val character = if (field.selection.collapsed && at > 0) at - 1 else at
        return parsed.attrs.getOrNull(character)?.color
    }

    /** Wielkość pisma pod kursorem albo w zaznaczeniu; [base], gdy fragment jej nie ma. */
    fun sizeIn(field: TextFieldValue, base: Float): Float {
        val parsed = RichTextCodec.read(field.text)
        val at = parsed.plainOffset(field.selection.min)
        val character = if (field.selection.collapsed && at > 0) at - 1 else at
        return parsed.attrs.getOrNull(character)?.sizePx ?: base
    }

    /**
     * Nadaje zapamiętane formaty tekstowi dopiero co wpisanemu.
     *
     * Wywoływane po zmianie treści: [previous] to zapis sprzed naciśnięcia
     * klawisza. Jeśli człowiek dopisał znaki, dostają one formaty z [pending] —
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
     * koniec wiersza — obie połówki stawały się zwykłym tekstem i gwiazdki
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
          formatu stoi w środku — i tylko wtedy nowa linia rozcina znacznik,
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
          w środku znaczników — i nowa linia znów je rozcinała.
        */
        if (inside == RichTextCodec.NONE && carried == RichTextCodec.NONE && item == null) {
            return null
        }

        if (item != null && item.groupValues[5].isBlank()) {
            // Nowa linia w pustej pozycji: koniec listy. Znacznik znika,
            // a wiersz zostaje pusty — tak kończy się każda lista zakupów.
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
     * Znaczniki są schowane, ale nadal siedzą w treści — a Compose kasuje
     * znaki ZAPISU, nie te, które widać. Kursor stojący za „</span>" i jedno
     * naciśnięcie Backspace zabierało z niego „>", rozbity znacznik przestawał
     * być znacznikiem i pokazywał się w notatce jako goły tekst. Tak samo
     * ginęła jedna gwiazdka z pary.
     *
     * Dlatego kasowanie idzie przez model: liczy się to, co widać. Zniknięcie
     * samych znaczników znaczy „człowiek celował w znak przed nimi", a zapis
     * składa się z powrotem — bez pustych par i bez połówek znaczników.
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
              zabrać przed nimi — zabieramy to. Jeśli nie ma, kasowanie nie
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

    private fun carryOf(attrs: RichTextCodec.Attrs): PendingFormat {
        var carry = PendingFormat()
        for (type in SpanType.entries) {
            if (attrs.has(type)) carry = carry.with(type, attrs.valueOf(type))
        }
        return carry
    }

    private fun PendingFormat.appliedTo(attrs: RichTextCodec.Attrs): RichTextCodec.Attrs {
        var result = attrs
        for ((type, value) in on) result = result.with(type, value)
        for (type in off) result = result.without(type)
        return result
    }

    /**
     * Co się zmieniło między dwoma zapisami: od [from] zniknęło wszystko do
     * [removedTo], a w to miejsce weszło [added]. Liczone od wspólnego
     * początku i wspólnego końca, więc obejmuje i dopisanie, i skasowanie,
     * i podmianę zaznaczenia. Null, gdy zapisy są takie same.
     */
    private class Change(val from: Int, val removedTo: Int, val added: String)

    private fun change(before: String, after: String): Change? {
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

    /**
     * Przestawia formaty zaznaczenia i składa zapis z powrotem. Zwraca pole
     * z zaznaczonym tym samym fragmentem — można poprawiać do skutku.
     */
    private fun edit(
        field: TextFieldValue,
        /**
         * Czy obciąć z zaznaczenia spacje na brzegach. Znaczniki parzyste
         * ich nie znoszą — „* ma*" przestaje być kursywą i gwiazdki wychodzą
         * na wierzch. Nikt zresztą nie chce pogrubionej spacji.
         */
        trimEdges: Boolean = false,
        change: (
            RichTextCodec.Parsed,
            Int,
            Int,
        ) -> Pair<RichText, List<RichTextCodec.Attrs>>,
    ): TextFieldValue? {
        val parsed = RichTextCodec.read(field.text)
        var from = parsed.plainOffset(field.selection.min)
        var to = parsed.plainOffset(field.selection.max)

        if (trimEdges) {
            val plain = parsed.rich.text
            while (from < to && plain[from].isWhitespace()) from++
            while (to > from && plain[to - 1].isWhitespace()) to--
        }
        if (from >= to) return null

        val (rich, attrs) = change(parsed, from, to)
        val rendered = RichTextCodec.write(rich.text, attrs)
        return TextFieldValue(
            rendered.markdown,
            TextRange(rendered.sourceOffsetOf(from), rendered.sourceEndOf(to)),
        )
    }

    // --- Budowa wiersza: to nie format fragmentu, tylko układ notatki ---

    fun beforeLine(field: TextFieldValue, marker: String): TextFieldValue {
        val content = field.text
        val cursor = field.selection.start.coerceIn(0, content.length)

        val lineStart = content.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
        val line = content.substring(lineStart, lineEnd)

        return if (line.startsWith(marker)) {
            val next = content.removeRange(lineStart, lineStart + marker.length)
            val cursorAfter = (cursor - marker.length).coerceAtLeast(lineStart)
            TextFieldValue(next, TextRange(cursorAfter))
        } else {
            val next = content.substring(0, lineStart) + marker + content.substring(lineStart)
            TextFieldValue(next, TextRange(cursor + marker.length))
        }
    }

    fun insert(field: TextFieldValue, fragment: String, stepBack: Int = 0): TextFieldValue {
        val content = field.text
        val from = field.selection.min.coerceIn(0, content.length)
        val to = field.selection.max.coerceIn(from, content.length)

        val next = content.substring(0, from) + fragment + content.substring(to)
        val cursor = (from + fragment.length - stepBack).coerceIn(0, next.length)
        return TextFieldValue(next, TextRange(cursor))
    }
}
