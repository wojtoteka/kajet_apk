package wojtoteka.ovh.kajet.editor.text

import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.SpanType

/*
  Model notatki, na którym pracuje edytor: lista akapitów (EditLine), każdy
  z budową - ułożeniem, znakiem listy albo cytatu - i treścią, w której
  każdy znak ma swoje formaty (RichTextCodec.Attrs).

  Tak działa każdy porządny edytor tekstu: to, co widać, jest tylko obrazem
  modelu. Pisanie (TextEdit) i polecenia paska (TextCommands) zmieniają model,
  a zapis markdownu składa się z niego od nowa - znaczniki nigdy nie są
  doklejane do ciągu znaków, więc nie da się ich przeciąć ani zagnieździć
  krzywo.
*/

/** Miejsce cięcia wiersza i to, czy cięcie zabrało znak listy. */
internal class Cut(val index: Int, val prefixCut: Boolean)

/**
 * Wiersz w trakcie zmiany: ułożenie i budowa (zawsze w całości) plus treść
 * z formatami znak po znaku.
 */
internal class EditLine(
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
    /**
     * Poziom nagłówka CAŁEGO akapitu z zapisu („# Tytuł"); 0 - brak. Treść
     * niesie go znak po znaku, a tu zostaje, żeby przy złączaniu akapitów
     * nagłówek akapitu szedł za budową akapitu, jak w Wordzie.
     */
    var headingLine: Int = 0,
) {
    val visiblePrefix: Int get() = if (prefixHidden) 0 else prefix.length

    fun visibleLength(): Int = visiblePrefix + hidden.count { !it }

    fun visibleOffsetOf(contentIndex: Int): Int =
        visiblePrefix + (0 until contentIndex.coerceAtMost(text.length)).count { !hidden[it] }

    /** Indeks w treści k-tego widocznego znaku treści; długość, gdy go nie ma. */
    fun indexOfVisible(k: Int): Int {
        var seen = 0
        for (i in 0 until text.length) {
            if (hidden[i]) continue
            if (seen == k) return i
            seen++
        }
        return text.length
    }

    /** Miejsce tuż za k-tym widocznym znakiem (licząc od 1) i za ogonem odnośnika. */
    fun indexAfterVisible(k: Int): Int {
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
        headingLine = headingLine,
    )

    /**
     * Nagłówek akapitu [from] na znakach treści od [start] do [end] zamienia się
     * na nagłówek tego akapitu. Nagłówek nadany samemu kawałkowi (inny poziom
     * niż cały akapit) zostaje, jaki był.
     */
    fun adoptHeading(from: EditLine, start: Int, end: Int) {
        if (from.headingLine == headingLine) return
        val old = from.headingLine.takeIf { it > 0 }
        val new = headingLine.takeIf { it > 0 }
        for (i in start until end) {
            if (attrs[i].heading == old) attrs[i] = attrs[i].copy(heading = new)
        }
    }

    fun dropPrefix() {
        prefix = ""
        prefixHidden = false
        kind = LineKind.PARAGRAPH
        headingLine = 0
    }

    /** Poziom nagłówka, który ma CAŁA treść wiersza; null, gdy różny albo brak. */
    fun wholeHeading(): Int? {
        val level = attrs.firstOrNull()?.heading ?: return null
        return if (attrs.all { it.heading == level }) level else null
    }

    /** Treść wiersza wraca do zwykłego pisma (bez wyglądu nagłówka). */
    fun clearHeading() {
        for (i in attrs.indices) attrs[i] = attrs[i].without(SpanType.HEADING)
        headingLine = 0
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

        private val numberedPrefix = Regex("""^(\s*)(\d+)([.)]) $""")

        /**
         * Kolejne pozycje listy numerowanej dostają kolejne numery - jak
         * w Wordzie. Numer stał w treści na sztywno, więc po skasowaniu pozycji
         * ze środka lista zostawała z dziurą („1. 3."), a po wypunktowaniu
         * kilku akapitów naraz każdy miał „1.". Pierwsza pozycja ciągu
         * zachowuje swój numer; ciąg przerywa każdy wiersz spoza listy.
         * Podlisty (głębsze wcięcie) liczą się osobno.
         */
        fun renumber(lines: List<EditLine>) {
            val last = HashMap<Int, Int>()
            for (line in lines) {
                when (line.kind) {
                    LineKind.NUMBER -> {
                        val match = numberedPrefix.find(line.prefix) ?: continue
                        val indent = match.groupValues[1].length
                        last.keys.removeAll { it > indent }
                        val number = last[indent]?.plus(1) ?: match.groupValues[2].toIntOrNull() ?: 1
                        last[indent] = number
                        line.prefix = match.groupValues[1] + number + match.groupValues[3] + " "
                    }

                    LineKind.BULLET, LineKind.TASK -> {
                        val indent = line.prefix.length - line.prefix.trimStart().length
                        last.keys.removeAll { it >= indent }
                    }

                    else -> last.clear()
                }
            }
        }

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
        fun styled(style: EditLine, head: EditLine, tail: EditLine): EditLine {
            val merged = EditLine(
                open = style.open,
                prefix = style.prefix,
                prefixHidden = style.prefixHidden,
                kind = style.kind,
                text = StringBuilder(head.text).append(tail.text),
                attrs = (head.attrs + tail.attrs).toMutableList(),
                hidden = (head.hidden + tail.hidden).toMutableList(),
                tail = (head.tail + tail.tail).toMutableList(),
                close = style.close,
                headingLine = style.headingLine,
            )
            // Nagłówek akapitu idzie za budową, jak w Wordzie: zwykły akapit
            // złączony z nagłówkiem pod spodem zostaje zwykły, a nagłówek
            // złączony z akapitem pod spodem obejmuje całość.
            merged.adoptHeading(head, 0, head.text.length)
            merged.adoptHeading(tail, head.text.length, merged.text.length)
            return merged
        }

        /**
         * Wiersze treści [layout].
         *
         * Wiersz-nagłówek z treścią („# Tytuł") przychodzi tu BEZ kratek: jego
         * treść niesie poziom nagłówka znak po znaku (RichTextCodec czyta go
         * z kratek), więc nagłówek da się zdjąć z połowy wiersza, a wiersz
         * złączony Backspace'em z innym zachowuje wygląd każdego kawałka.
         * Kratki wracają przy [write], kiedy cała treść wiersza ma ten sam
         * poziom. Kratek i tak nie widać, więc nic na ekranie się nie przesuwa.
         */
        fun from(layout: TextLayout): MutableList<EditLine> {
            val plain = layout.plain
            val attrs = layout.parsed.attrs
            return layout.lines.mapTo(ArrayList(layout.lines.size)) { line ->
                val inline = line.kind == LineKind.HEADING && line.contentEnd > line.contentStart
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
                    prefix = if (inline) "" else plain.substring(line.start + line.open, start),
                    prefixHidden = if (inline) false else line.prefixHidden,
                    kind = if (inline) LineKind.PARAGRAPH else line.kind,
                    text = StringBuilder(plain.substring(start, end)),
                    attrs = attrs.subList(start, end).toMutableList(),
                    hidden = hidden.toMutableList(),
                    tail = tail,
                    close = plain.substring(end, line.end),
                    headingLine = if (line.kind == LineKind.HEADING) line.level else 0,
                )
            }
        }

        /**
         * Wiersze z powrotem w zapis notatki.
         *
         * Po drodze dwie rzeczy, które Word robi sam:
         *  - zwykły akapit, którego CAŁA treść ma jeden poziom nagłówka,
         *    zapisuje się po markdownowemu - „# Tytuł" - a nie znacznikiem
         *    wokół całego wiersza,
         *  - lista numerowana liczy się od nowa, po kolei (patrz [renumber]).
         */
        fun write(lines: List<EditLine>): String {
            for (line in lines) {
                if (line.prefix.isNotEmpty() || line.kind != LineKind.PARAGRAPH) continue
                val level = line.wholeHeading() ?: continue
                line.prefix = "#".repeat(level) + " "
                line.prefixHidden = true
                line.kind = LineKind.HEADING
            }
            renumber(lines)

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
