package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.SpanType

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
            // Formaty pisma idą do nowego akapitu, wygląd nagłówka nie: za
            // nagłówkiem zaczyna się zwykły tekst, jak w każdym edytorze.
            // Reszta przeciętego nagłówka zostaje nagłówkiem - jej znaki
            // niosą go same.
            nextPending = TextFormat.carryOf(carried.without(SpanType.HEADING))
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
            first.wholeHeading() != null -> first.clearHeading()
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
}
