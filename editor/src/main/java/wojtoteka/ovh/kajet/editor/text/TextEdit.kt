package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pisanie w polu, w którym znaczników nie widać.
 *
 * Compose pisze i kasuje znaki ZAPISU, a nie te, które widać. Kursor za
 * schowanymi kratkami nagłówka i jeden Backspace zabierał spację z „# " -
 * wiersz przestawał być nagłówkiem i „#Tytuł" wychodził na wierzch. Tak samo
 * rozjeżdżały się ukryte płoty bloku kodu i znaczniki barwy.
 *
 * Dlatego każda zmiana z klawiatury jest tu przeliczana na zmianę TEGO, CO
 * WIDAĆ ([TextLayout]), wykonywana na wierszach (ułożenie, budowa wiersza,
 * treść z formatami) i składana z powrotem w zapis. Tak działa Word: ukryte
 * formatowanie należy do akapitu albo do znaku i nie da się go „przeciąć".
 *
 * Kiedy wynik jest znak w znak taki sam jak to, co przyszło z klawiatury
 * (zwykłe pisanie, czyli prawie zawsze), pole zostaje nietknięte - inaczej
 * klawiatura ekranowa traciłaby słowo, które właśnie składa.
 */
object TextEdit {

    class Outcome(
        /** Nowe pole; null znaczy „zostaw to, co przyszło z klawiatury". */
        val field: TextFieldValue?,
        /** Formaty, które mają czekać na dalsze pisanie. */
        val pending: PendingFormat,
    )

    fun typed(
        previous: TextFieldValue,
        typed: TextFieldValue,
        pending: PendingFormat,
    ): Outcome {
        val before = previous.text
        val change = TextFormat.change(before, typed.text) ?: return Outcome(null, pending)
        val layout = TextLayout.of(before)
        val lines = EditLine.from(layout)

        var vFrom = layout.visibleOfSource(change.from)
        var vTo = maxOf(vFrom, layout.visibleOfSource(change.removedTo))
        val added = change.added

        if (added.isEmpty() && vFrom == vTo) {
            // Zniknęły same ukryte znaki zapisu. Człowiek celował w widoczny
            // znak obok - przed kursorem (Backspace) albo za nim (Delete).
            val forward = previous.selection.collapsed && previous.selection.start <= change.from
            when {
                forward && vTo < layout.visible.length -> vTo++
                !forward && vFrom > 0 -> vFrom--
                !forward -> return atBlockStart(lines, before, previous, pending)
                else -> return Outcome(TextFieldValue(before, previous.selection), pending)
            }
        }

        // --- Kasowanie ---

        val la = layout.lineAtVisible(vFrom)
        val ka = vFrom - layout.lineVisibleStart(la)
        val lb = layout.lineAtVisible(vTo)
        val kb = vTo - layout.lineVisibleStart(lb)

        // Formaty tego, co ewentualnie zastępujemy - wpisane w miejsce
        // zaznaczenia ma wyglądać tak jak ono.
        val replaced = if (vTo > vFrom) lines[la].attrsAtVisible(ka) else null

        val line: EditLine
        var at: Int
        if (la == lb) {
            line = lines[la]
            at = line.delete(ka, kb)
        } else {
            val first = lines[la]
            val last = lines[lb]
            val cutA = first.cutFrom(ka)
            val cutB = last.cutTo(kb)
            /*
              Złączony akapit bierze budowę pierwszego - jak Backspace na
              początku akapitu w Wordzie. Wyjątek: pierwszy był pusty (albo
              został wyczyszczony do zera), wtedy zostaje budowa drugiego.
            */
            line = if (cutA.index == 0 && !cutB.prefixCut) {
                last
            } else {
                if (cutA.prefixCut) first.dropPrefix()
                first
            }
            val head = first.slice(0, cutA.index)
            val tail = last.slice(cutB.index, last.text.length)
            val merged = EditLine.styled(line, head, tail)
            for (i in la..lb) lines.removeAt(la)
            lines.add(la, merged)
            at = head.text.length
        }
        val target = lines[la]
        target.dropEmptyLinks()
        at = at.coerceIn(0, target.text.length)

        // --- Wpisywanie ---

        val typedParse = if (added.isEmpty()) null else RichTextCodec.read(typed.text)
        fun attrsOf(index: Int): RichTextCodec.Attrs {
            val parsed = typedParse
            val base = if (parsed != null) {
                val sourceAt = change.from + index
                val p = parsed.plainOffset(sourceAt)
                if (p < parsed.rich.text.length && parsed.sourceOffset(p) == sourceAt) {
                    parsed.attrs[p]
                } else {
                    null
                }
            } else {
                null
            } ?: replaced ?: target.neighbourAttrs(at)
            return if (pending.isEmpty) base else pending.appliedTo(base)
        }

        var cursorLine = la
        var cursorAt = at
        var nextPending = if (added.isEmpty()) pending else PendingFormat()

        if (added == "\n") {
            val carried = pending.appliedTo(
                if (at > 0) target.attrs[at - 1] else target.neighbourAttrs(at),
            )
            if (target.text.isEmpty() && target.endsListOnEnter()) {
                // Nowa linia w pustej pozycji listy albo cytatu: koniec listy.
                target.dropPrefix()
                cursorAt = 0
            } else {
                val next = target.split(at)
                lines.add(la + 1, next)
                cursorLine = la + 1
                cursorAt = 0
            }
            nextPending = TextFormat.carryOf(carried)
        } else if (added.isNotEmpty()) {
            val segments = added.split('\n')
            var offset = 0
            // Pierwszy kawałek idzie w bieżący wiersz.
            target.insert(at, segments[0]) { attrsOf(offset + it) }
            offset += segments[0].length + 1
            cursorAt = at + segments[0].length
            if (segments.size > 1) {
                val rest = target.split(cursorAt, continueStructure = false)
                var row = la
                for (segment in segments.drop(1).dropLast(1)) {
                    row++
                    val fresh = EditLine.plain()
                    val base = offset
                    fresh.insert(0, segment) { attrsOf(base + it) }
                    lines.add(row, fresh)
                    offset += segment.length + 1
                }
                row++
                val lastSegment = segments.last()
                val base = offset
                rest.insert(0, lastSegment) { attrsOf(base + it) }
                lines.add(row, rest)
                cursorLine = row
                cursorAt = lastSegment.length
            }
        }

        val result = EditLine.write(lines)
        if (result == typed.text) return Outcome(null, nextPending)

        val visibleCursor = (0 until cursorLine).sumOf { lines[it].visibleLength() + 1 } +
            lines[cursorLine].visibleOffsetOf(cursorAt)
        val shaped = TextLayout.of(result)
        val cursor = shaped.sourceCursor(visibleCursor.coerceIn(0, shaped.visible.length))
        return Outcome(TextFieldValue(result, TextRange(cursor)), nextPending)
    }

    /**
     * Backspace na samym początku pola: przed kursorem nie ma już nic do
     * skasowania. Jak w Wordzie zdejmuje się wtedy budowa akapitu - najpierw
     * nagłówek albo cytat, potem ułożenie. Zwykły akapit zostaje, jaki był.
     */
    private fun atBlockStart(
        lines: MutableList<EditLine>,
        before: String,
        previous: TextFieldValue,
        pending: PendingFormat,
    ): Outcome {
        val first = lines.first()
        when {
            first.prefix.isNotEmpty() -> first.dropPrefix()
            first.open.isNotEmpty() -> {
                first.open = ""
                first.close = ""
            }

            else -> return Outcome(TextFieldValue(before, previous.selection), pending)
        }
        val result = EditLine.write(lines)
        val shaped = TextLayout.of(result)
        return Outcome(TextFieldValue(result, TextRange(shaped.sourceCursor(0))), pending)
    }

    /** Miejsce cięcia wiersza i to, czy cięcie zabrało znak listy. */
    private class Cut(val index: Int, val prefixCut: Boolean)

    /**
     * Wiersz w trakcie zmiany: ułożenie i budowa (zawsze w całości) plus treść
     * z formatami znak po znaku.
     */
    private class EditLine(
        var open: String,
        var prefix: String,
        var prefixHidden: Boolean,
        var kind: LineKind,
        val text: StringBuilder,
        val attrs: MutableList<RichTextCodec.Attrs>,
        /** Znak treści ukryty (zapis odnośnika). */
        val hidden: MutableList<Boolean>,
        /** Ukryty ogon odnośnika - kursor staje za nim. */
        val tail: MutableList<Boolean>,
        var close: String,
    ) {
        val visiblePrefix: Int get() = if (prefixHidden) 0 else prefix.length

        fun visibleLength(): Int = visiblePrefix + hidden.count { !it }

        fun visibleOffsetOf(contentIndex: Int): Int =
            visiblePrefix + (0 until contentIndex.coerceAtMost(text.length)).count { !hidden[it] }

        /** Indeks w treści k-tego widocznego znaku treści; długość, gdy go nie ma. */
        private fun indexOfVisible(k: Int): Int {
            var seen = 0
            for (i in 0 until text.length) {
                if (hidden[i]) continue
                if (seen == k) return i
                seen++
            }
            return text.length
        }

        /** Miejsce tuż za k-tym widocznym znakiem (licząc od 1) i za ogonem odnośnika. */
        private fun indexAfterVisible(k: Int): Int {
            if (k <= 0) return 0
            var i = indexOfVisible(k - 1) + 1
            while (i < text.length && tail[i]) i++
            return i.coerceAtMost(text.length)
        }

        fun attrsAtVisible(k: Int): RichTextCodec.Attrs? {
            val content = k - visiblePrefix
            if (content < 0) return null
            val i = indexOfVisible(content)
            return attrs.getOrNull(i)
        }

        fun neighbourAttrs(at: Int): RichTextCodec.Attrs =
            attrs.getOrNull(at - 1) ?: attrs.getOrNull(at) ?: RichTextCodec.NONE

        /** Kasuje widoczne [ka, kb) w tym wierszu; zwraca miejsce kursora w treści. */
        fun delete(ka: Int, kb: Int): Int {
            if (kb <= ka) return insertionIndex(ka)
            val cutsPrefix = ka < visiblePrefix
            val ca = (ka - visiblePrefix).coerceAtLeast(0)
            val cb = (kb - visiblePrefix).coerceAtLeast(0)
            var at = if (cutsPrefix) 0 else insertionIndex(ka)
            if (cb > ca) {
                var start = indexOfVisible(ca)
                var end = indexOfVisible(cb - 1) + 1
                if (damagesLink(start, end)) {
                    unwrapLinks()
                    start = indexOfVisible(ca)
                    end = indexOfVisible(cb - 1) + 1
                }
                remove(start, end)
                at = start
            }
            if (cutsPrefix) dropPrefix()
            return at
        }

        /** Gdzie wpisać tekst stojący na widocznej pozycji [k] wiersza. */
        fun insertionIndex(k: Int): Int =
            if (k <= visiblePrefix) 0 else indexAfterVisible(k - visiblePrefix)

        /** Początek kasowania od widocznej pozycji [k] do końca wiersza. */
        fun cutFrom(k: Int): Cut {
            val prefixCut = k < visiblePrefix
            val content = (k - visiblePrefix).coerceAtLeast(0)
            var index = indexOfVisible(content)
            if (damagesLink(index, text.length)) {
                unwrapLinks()
                index = indexOfVisible(content)
            }
            return Cut(index, prefixCut)
        }

        /** Koniec kasowania od początku wiersza do widocznej pozycji [k]. */
        fun cutTo(k: Int): Cut {
            val prefixCut = k in 1..visiblePrefix && visiblePrefix > 0
            if (k <= visiblePrefix) return Cut(0, prefixCut)
            var index = indexAfterVisible(k - visiblePrefix)
            if (damagesLink(0, index)) {
                unwrapLinks()
                index = indexAfterVisible(k - visiblePrefix)
            }
            return Cut(index, false)
        }

        /**
         * Czy skasowanie [from, to) zabierze kawałek zapisu odnośnika, a nie
         * cały odnośnik. Został by wtedy goły nawias - więc odnośnik trzeba
         * najpierw zamienić na zwykły tekst. Kasowanie w samym opisie
         * odnośnika go nie rusza.
         */
        private fun damagesLink(from: Int, to: Int): Boolean {
            if (to <= from) return false
            return linkPattern.findAll(text).any { match ->
                val label = match.groups[1] ?: return@any false
                val start = match.range.first
                val end = match.range.last + 1
                val labelStart = label.range.first
                val labelEnd = labelStart + label.value.length
                val whole = from <= start && to >= end
                val touchesOpening = from < labelStart && to > start
                val touchesTail = from < end && to > labelEnd
                !whole && (touchesOpening || touchesTail)
            }
        }

        fun slice(from: Int, to: Int): EditLine = EditLine(
            open = open,
            prefix = prefix,
            prefixHidden = prefixHidden,
            kind = kind,
            text = StringBuilder(text.substring(from, to)),
            attrs = attrs.subList(from, to).toMutableList(),
            hidden = hidden.subList(from, to).toMutableList(),
            tail = tail.subList(from, to).toMutableList(),
            close = close,
        )

        fun dropPrefix() {
            prefix = ""
            prefixHidden = false
            kind = LineKind.PARAGRAPH
        }

        /** Pusta pozycja listy albo cytatu - Enter w niej kończy listę. */
        fun endsListOnEnter(): Boolean = prefix.isNotEmpty() && kind in listKinds

        private fun remove(from: Int, to: Int) {
            if (to <= from) return
            text.delete(from, to)
            attrs.subList(from, to).clear()
            hidden.subList(from, to).clear()
            tail.subList(from, to).clear()
        }

        fun insert(at: Int, value: String, attrsFor: (Int) -> RichTextCodec.Attrs) {
            text.insert(at, value)
            attrs.addAll(at, value.indices.map { attrsFor(it) })
            hidden.addAll(at, List(value.length) { false })
            tail.addAll(at, List(value.length) { false })
        }

        /**
         * Tnie wiersz w miejscu [at]. Ten wiersz zostaje z treścią przed
         * cięciem, oddany - z resztą. Nowy wiersz ma to samo ułożenie, a lista
         * idzie dalej następną pozycją. Za nagłówkiem, przeciętym na samym
         * końcu, zaczyna się zwykły akapit - jak w każdym edytorze tekstu.
         */
        fun split(at: Int, continueStructure: Boolean = true): EditLine {
            val rest = slice(at, text.length)
            remove(at, text.length)
            if (!continueStructure) {
                rest.dropPrefix()
                return rest
            }
            when (kind) {
                LineKind.BULLET, LineKind.NUMBER, LineKind.TASK -> rest.prefix = nextMarker(prefix)
                LineKind.QUOTE -> Unit
                LineKind.HEADING -> if (rest.text.isEmpty()) rest.dropPrefix()
                else -> rest.dropPrefix()
            }
            return rest
        }

        /** Odnośnik z pustym opisem jest niewidoczny - schodzi cały. */
        fun dropEmptyLinks() {
            while (true) {
                val match = emptyLink.find(text) ?: return
                remove(match.range.first, match.range.last + 1)
            }
        }

        /** Odnośniki w tym wierszu stają się zwykłym tekstem swojego opisu. */
        private fun unwrapLinks() {
            while (true) {
                val match = linkPattern.find(text) ?: break
                val label = match.groups[1] ?: break
                val labelEnd = label.range.first + label.value.length
                remove(labelEnd, match.range.last + 1)
                remove(match.range.first, label.range.first)
            }
            for (i in hidden.indices) {
                hidden[i] = false
                tail[i] = false
            }
        }

        companion object {
            private val listKinds = setOf(LineKind.BULLET, LineKind.NUMBER, LineKind.TASK, LineKind.QUOTE)
            private val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")
            private val emptyLink = Regex("""!?\[]\([^)\s]+\)""")
            private val numbered = Regex("""^(\s*)(\d+)([.)]) $""")
            private val checkedBox = Regex("""\[[xX]]""")

            fun nextMarker(prefix: String): String {
                val number = numbered.find(prefix)
                if (number != null) {
                    val next = (number.groupValues[2].toIntOrNull() ?: 1) + 1
                    return number.groupValues[1] + next + number.groupValues[3] + " "
                }
                return prefix.replace(checkedBox, "[ ]")
            }

            fun plain(): EditLine = EditLine(
                open = "",
                prefix = "",
                prefixHidden = false,
                kind = LineKind.PARAGRAPH,
                text = StringBuilder(),
                attrs = mutableListOf(),
                hidden = mutableListOf(),
                tail = mutableListOf(),
                close = "",
            )

            /** Wiersz z budową [style] i treścią [head] + [tail]. */
            fun styled(style: EditLine, head: EditLine, tail: EditLine): EditLine = EditLine(
                open = style.open,
                prefix = style.prefix,
                prefixHidden = style.prefixHidden,
                kind = style.kind,
                text = StringBuilder(head.text).append(tail.text),
                attrs = (head.attrs + tail.attrs).toMutableList(),
                hidden = (head.hidden + tail.hidden).toMutableList(),
                tail = (head.tail + tail.tail).toMutableList(),
                close = style.close,
            )

            fun from(layout: TextLayout): MutableList<EditLine> {
                val plain = layout.plain
                val attrs = layout.parsed.attrs
                return layout.lines.mapTo(ArrayList(layout.lines.size)) { line ->
                    val start = line.contentStart
                    val end = line.contentEnd
                    val hidden = (start until end).map { !layout.isVisible(it) }
                    // Ogon odnośnika to ukryte znaki stojące za widocznym opisem.
                    val tail = MutableList(end - start) { false }
                    var afterVisible = false
                    for (i in start until end) {
                        if (layout.isVisible(i)) {
                            afterVisible = true
                        } else if (plain[i] == '[' || (plain[i] == '!' && plain.getOrNull(i + 1) == '[')) {
                            afterVisible = false
                        } else {
                            tail[i - start] = true
                        }
                    }
                    EditLine(
                        open = plain.substring(line.start, line.start + line.open),
                        prefix = plain.substring(line.start + line.open, start),
                        prefixHidden = line.prefixHidden,
                        kind = line.kind,
                        text = StringBuilder(plain.substring(start, end)),
                        attrs = attrs.subList(start, end).toMutableList(),
                        hidden = hidden.toMutableList(),
                        tail = tail,
                        close = plain.substring(end, line.end),
                    )
                }
            }

            /** Wiersze z powrotem w zapis notatki. */
            fun write(lines: List<EditLine>): String {
                val plain = StringBuilder()
                val attrs = ArrayList<RichTextCodec.Attrs>()
                for ((index, line) in lines.withIndex()) {
                    if (index > 0) {
                        plain.append('\n')
                        attrs += RichTextCodec.NONE
                    }
                    val structure = line.open + line.prefix
                    plain.append(structure)
                    repeat(structure.length) { attrs += RichTextCodec.NONE }
                    plain.append(line.text)
                    attrs += line.attrs
                    plain.append(line.close)
                    repeat(line.close.length) { attrs += RichTextCodec.NONE }
                }
                return RichTextCodec.write(plain.toString(), attrs).markdown
            }
        }
    }
}
