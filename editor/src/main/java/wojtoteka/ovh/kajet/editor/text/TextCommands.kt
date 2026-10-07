package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.SpanType
import kotlin.math.roundToInt

/**
 * Polecenia paska narzędzi notatki tekstowej - jedna droga dla każdego
 * przycisku.
 *
 * Przycisk nie dokleja znaczników do zapisu. Wysyła polecenie, a polecenie
 * czyta notatkę na model ([EditLine]: akapity z budową i znaki z formatami),
 * zmienia model i składa zapis od nowa. Miejsce, na którym działa, liczy się
 * w tym, CO WIDAĆ - znaczniki są ukryte, więc ani format, ani zaznaczenie nie
 * trafią w ich środek. Wcześniej pogrubienie zaznaczone przez dwa punkty listy
 * obejmowało też znak listy („**- chl**eb") i wiersz przestawał być listą,
 * a kursywa przez dwa wyśrodkowane akapity zamykała się w środku „</p>".
 *
 * Formaty znaku (pogrubienie, barwa, wielkość, nagłówek H1-H3...) działają
 * tak jak w Wordzie:
 *  - z zaznaczeniem - na zaznaczony tekst i tylko na niego,
 *  - z kursorem w środku słowa - na to słowo,
 *  - z kursorem między słowami albo na końcu - czekają na pisanie
 *    ([PendingFormat]): to, co człowiek zaraz napisze, dostaje format.
 *
 * Budowa akapitu (punkt, numer, zadanie, cytat) dotyczy całego akapitu, jak
 * w każdym edytorze - połowy wiersza nie da się wypunktować. Działa na każdy
 * akapit, którego dotyka zaznaczenie, a nie tylko na pierwszy.
 */
object TextCommands {

    /** Wynik polecenia: nowe pole (null - treść bez zmian) i formaty czekające na pisanie. */
    class Result(val field: TextFieldValue?, val pending: PendingFormat)

    /** Co obowiązuje w zaznaczeniu albo pod kursorem - po tym świecą przyciski paska. */
    data class Formats(
        /** Formaty, które ma CAŁE zaznaczenie (albo tekst pisany od kursora). */
        val active: Set<SpanType>,
        /** Wspólny poziom nagłówka; null - zwykły tekst albo różne poziomy. */
        val heading: Int?,
        /** Wielkość pisma pierwszego znaku, razem z powiększeniem nagłówka. */
        val sizePx: Float,
        val color: Int?,
    ) {
        fun has(type: SpanType): Boolean = type in active
    }

    /** Formaty zapisywane parą znaczników - nie znoszą spacji na brzegach („** ma**"). */
    private val pairs = setOf(
        SpanType.BOLD,
        SpanType.ITALIC,
        SpanType.STRIKETHROUGH,
        SpanType.HIGHLIGHT,
        SpanType.CODE,
    )

    // --- Formaty znaku ---

    /**
     * Nadaje albo zdejmuje format. Kawałek, który ma go w całości, traci go -
     * drugie naciśnięcie zdejmuje. Nagłówek: [value] to poziom („1"-„3"),
     * a inny poziom na tym samym kawałku zostaje podmieniony.
     *
     * [lineHeading]: czy pusty akapit może stać się nagłówkiem od razu. W
     * zadaniu nie może - zadanie to zawsze „- [ ] ", więc tam nagłówek czeka
     * na pisanie jak każdy inny format.
     */
    fun toggle(
        field: TextFieldValue,
        type: SpanType,
        value: String = "",
        pending: PendingFormat = PendingFormat(),
        lineHeading: Boolean = true,
    ): Result {
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        val range = doc.range(from, to)

        if (range == null) {
            if (type == SpanType.HEADING && lineHeading) {
                doc.headingOnEmptyLine(from, value.toIntOrNull() ?: 1)?.let { text ->
                    return Result(doc.fieldAfter(text, from, to), pending.forget(SpanType.HEADING))
                }
            }
            val now = doc.formatsAt(from, pending, base = 0f)
            val active = if (type == SpanType.HEADING) now.heading == value.toIntOrNull() else now.has(type)
            return Result(null, if (active) pending.without(type) else pending.with(type, value))
        }

        val pieces = doc.pieces(range.first, range.last + 1, trim = type in pairs)
        if (pieces.isEmpty()) return Result(null, pending)
        val level = value.toIntOrNull()
        val whole = pieces.all { piece ->
            piece.indices.all { i ->
                val attrs = piece.line.attrs[i]
                if (type == SpanType.HEADING) attrs.heading == level else attrs.has(type)
            }
        }
        doc.change(pieces) { if (whole) it.without(type) else it.with(type, value) }
        return Result(doc.fieldAfter(doc.write(), from, to), pending)
    }

    /** Barwa pisma; null zdejmuje barwę. Barwa już obecna jest podmieniana. */
    fun color(field: TextFieldValue, argb: Int?, pending: PendingFormat = PendingFormat()): Result {
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        val range = doc.range(from, to)
            ?: return Result(
                null,
                if (argb == null) {
                    pending.without(SpanType.COLOR)
                } else {
                    pending.with(SpanType.COLOR, RichTextCodec.colorHex(argb))
                },
            )
        val pieces = doc.pieces(range.first, range.last + 1, trim = false)
        if (pieces.isEmpty()) return Result(null, pending)
        doc.change(pieces) {
            if (argb == null) it.without(SpanType.COLOR) else it.with(SpanType.COLOR, RichTextCodec.colorHex(argb))
        }
        return Result(doc.fieldAfter(doc.write(), from, to), pending)
    }

    /**
     * Wielkość pisma o [delta] punktów. Każdy znak rośnie od SWOJEJ wielkości
     * - nagłówek w zaznaczeniu zostaje większy od zwykłego tekstu, jak przy
     * „Powiększ czcionkę" w Wordzie. Powrót do wielkości wynikającej z notatki
     * i nagłówka ([base]) zdejmuje znacznik.
     */
    fun resize(
        field: TextFieldValue,
        delta: Float,
        base: Float,
        pending: PendingFormat = PendingFormat(),
    ): Result {
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        val range = doc.range(from, to)

        if (range == null) {
            val at = pending.appliedTo(doc.attrsAt(from))
            val natural = natural(at, base).roundToInt().toFloat()
            val next = grown(at.sizePx ?: natural, delta)
            return Result(
                null,
                if (next == natural) {
                    pending.without(SpanType.SIZE)
                } else {
                    pending.with(SpanType.SIZE, RichTextCodec.sizeText(next))
                },
            )
        }

        val pieces = doc.pieces(range.first, range.last + 1, trim = false)
        if (pieces.isEmpty()) return Result(null, pending)
        doc.change(pieces) { attrs ->
            val natural = natural(attrs, base).roundToInt().toFloat()
            val next = grown(attrs.sizePx ?: natural, delta)
            if (next == natural) {
                attrs.without(SpanType.SIZE)
            } else {
                attrs.with(SpanType.SIZE, RichTextCodec.sizeText(next))
            }
        }
        return Result(doc.fieldAfter(doc.write(), from, to), pending)
    }

    /** Formaty zaznaczenia; bez zaznaczenia - tego, co zostanie napisane od kursora. */
    fun formats(field: TextFieldValue, pending: PendingFormat, base: Float): Formats {
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        if (to > from) {
            val chars = doc.pieces(from, to, trim = false).flatMap { piece ->
                piece.indices.map { piece.line.attrs[it] }
            }
            if (chars.isNotEmpty()) {
                val first = chars.first()
                return Formats(
                    active = SpanType.entries.filterTo(HashSet()) { type -> chars.all { it.has(type) } },
                    heading = chars.map { it.heading }.distinct().singleOrNull(),
                    sizePx = first.sizePx ?: natural(first, base),
                    color = first.color,
                )
            }
        }
        return doc.formatsAt(from, pending, base)
    }

    /**
     * Wielkość o [delta] większa, w całych pikselach. Nagłówek ma wielkość
     * ułamkową (1,7 wielkości notatki), a „28,9px" w zapisie nikomu nie służy.
     */
    private fun grown(size: Float, delta: Float): Float =
        (size + delta).roundToInt().toFloat().coerceIn(SMALLEST, LARGEST)

    /** Wielkość pisma bez własnej wielkości fragmentu: notatki, a w nagłówku - nagłówka. */
    private fun natural(attrs: RichTextCodec.Attrs, base: Float): Float =
        base * RichTextCodec.headingScale(attrs.heading)

    const val SMALLEST = TextFormat.SMALLEST_FRAGMENT
    const val LARGEST = TextFormat.LARGEST_FRAGMENT

    // --- Indeks górny i dolny ---

    /**
     * Cyfry w indeksie górnym (x²) albo dolnym (H₂O).
     *
     * Indeks to zwykłe znaki Unicode (¹ ² ₁ ₂...), a nie znacznik w zapisie -
     * dzięki temu wygląda tak samo na stronie, w PDF-ie, w wyszukiwarce i
     * w każdej innej aplikacji, do której notatka trafi przez schowek.
     * Unicode ma w obu indeksach komplet cyfr i znaki + − = ( ), a liter
     * tylko garść, więc indeks dotyczy cyfr i tych znaków.
     *
     * Działa na zaznaczenie, a bez zaznaczenia na liczbę tuż przed kursorem:
     * napisane „H2" i jedno stuknięcie daje „H₂". Kiedy wszystko już jest
     * w tym indeksie, to samo stuknięcie wraca do zwykłych cyfr; cyfry
     * z drugiego indeksu przechodzą do wybranego. Null, gdy nie ma czego
     * zmienić.
     */
    fun script(field: TextFieldValue, superscript: Boolean): TextFieldValue? {
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        val start = if (to > from) from else ScriptDigits.numberStart(doc.layout.visible, from)
        val end = if (to > from) to else from
        if (end <= start) return null

        // Ukryty zapis odnośnika („(adres)") idzie z opisem w całości - jego
        // cyfr ruszać nie wolno, bo adres przestałby prowadzić tam, gdzie
        // prowadził.
        val chosen = doc.pieces(start, end, trim = false).flatMap { piece ->
            piece.indices
                .filter { !piece.line.hidden[it] && ScriptDigits.isScriptable(piece.line.text[it]) }
                .map { piece.line to it }
        }
        if (chosen.isEmpty()) return null

        val already = chosen.all { (line, i) -> ScriptDigits.isIn(line.text[i], superscript) }
        for ((line, i) in chosen) {
            val ch = line.text[i]
            line.text.setCharAt(i, if (already) ScriptDigits.plain(ch) else ScriptDigits.to(ch, superscript))
        }
        return doc.fieldAfter(doc.write(), from, to)
    }

    // --- Budowa akapitu ---

    /**
     * Punkt, numer, zadanie albo cytat dla akapitów w zaznaczeniu (albo tego
     * z kursorem). Akapit, który już ma inną budowę, dostaje nową w jej
     * miejsce - z punktu robi się numer, a nie „1. - mleko". Kiedy wszystkie
     * wybrane akapity już ją mają, to samo naciśnięcie ją zdejmuje.
     *
     * W zaznaczeniu przez kilka akapitów puste wiersze są tylko odstępem
     * i zostają, jakie były. Null, gdy nie ma czego zmienić.
     */
    fun paragraphs(field: TextFieldValue, kind: LineKind): TextFieldValue? {
        val marker = when (kind) {
            LineKind.BULLET -> "- "
            LineKind.NUMBER -> "1. "
            LineKind.TASK -> Blocks.TASK_MARKER
            LineKind.QUOTE -> "> "
            else -> return null
        }
        val doc = Doc(field.text)
        val (from, to) = doc.visibleSelection(field)
        val first = doc.layout.lineAtVisible(from)
        val last = doc.layout.lineAtVisible(to)
        val start = doc.anchor(from)
        val end = doc.anchor(to)

        val many = last > first
        val chosen = (first..last).filter { i ->
            val line = doc.lines[i]
            line.kind in listable && !(many && line.text.isBlank()) && !isImageOrTable(line)
        }
        if (chosen.isEmpty()) return null

        val remove = chosen.all { doc.lines[it].kind == kind }
        for (i in chosen) {
            val line = doc.lines[i]
            if (remove) {
                line.dropPrefix()
                continue
            }
            // Podlista zostaje podlistą - wcięcie przechodzi na nowy znak.
            val indent = if (line.kind in lists) line.prefix.takeWhile { it == ' ' || it == '\t' } else ""
            line.prefix = indent + marker
            line.prefixHidden = kind == LineKind.QUOTE
            line.kind = kind
            // Zadanie stoi zawsze przy lewej krawędzi, obok kwadracika - i tylko
            // bez znacznika ułożenia zostaje zadaniem po ponownym otwarciu.
            if (kind == LineKind.TASK) {
                line.open = ""
                line.close = ""
            }
        }

        val text = doc.write()
        val after = Doc(text)
        return TextFieldValue(
            text,
            after.layout.sourceSelection(after.visibleOf(start), after.visibleOf(end)),
        )
    }

    /** Budowa akapitu, w którym stoi kursor (początek zaznaczenia). */
    fun paragraphKind(field: TextFieldValue): LineKind {
        val layout = TextLayout.of(field.text)
        return layout.lines[layout.lineAtVisible(layout.visibleOfSource(field.selection.min))].kind
    }

    private val lists = setOf(LineKind.BULLET, LineKind.NUMBER, LineKind.TASK)
    private val listable = lists + setOf(LineKind.PARAGRAPH, LineKind.HEADING, LineKind.QUOTE)

    private fun isImageOrTable(line: EditLine): Boolean {
        val text = line.text.toString().trim()
        return ImageLines.read(text) != null || (text.length > 1 && text.startsWith("|") && text.endsWith("|"))
    }

    // --- Słowo pod kursorem ---

    /** Słowo, w którego ŚRODKU stoi kursor; null na brzegu słowa i między słowami. */
    fun wordAround(visible: String, at: Int): IntRange? {
        if (at <= 0 || at >= visible.length) return null
        if (!isWordChar(visible[at - 1]) || !isWordChar(visible[at])) return null
        var start = at
        while (start > 0 && isWordChar(visible[start - 1])) start--
        var end = at
        while (end < visible.length && isWordChar(visible[end])) end++
        return start until end
    }

    private fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit()

    // --- Model ---

    /** Miejsce w akapicie: numer wiersza i indeks w jego treści. */
    private class Anchor(val line: Int, val index: Int)

    /** Kawałek treści jednego akapitu: indeksy znaków, na których działa polecenie. */
    private class Piece(val line: EditLine, val indices: List<Int>)

    /** Notatka rozebrana na akapity razem z ich miejscem w tym, co widać. */
    private class Doc(source: String) {
        val layout = TextLayout.of(source)
        val lines = EditLine.from(layout)

        fun start(line: Int): Int = layout.lineVisibleStart(line)

        fun write(): String = EditLine.write(lines)

        fun visibleSelection(field: TextFieldValue): Pair<Int, Int> {
            val a = layout.visibleOfSource(field.selection.min)
            val b = layout.visibleOfSource(field.selection.max)
            return a to maxOf(a, b)
        }

        /** Zakres, na którym działa format: zaznaczenie albo słowo pod kursorem. */
        fun range(from: Int, to: Int): IntRange? = when {
            to > from -> from until to
            else -> wordAround(layout.visible, from)
        }

        fun anchor(visible: Int): Anchor {
            val index = layout.lineAtVisible(visible)
            val line = lines[index]
            return Anchor(index, line.insertionIndex(visible - start(index)))
        }

        fun visibleOf(anchor: Anchor): Int {
            val index = anchor.line.coerceIn(0, lines.lastIndex)
            return start(index) + lines[index].visibleOffsetOf(anchor.index)
        }

        /** Formaty znaku, który poprowadzi pisanie od widocznej pozycji [at]. */
        fun attrsAt(at: Int): RichTextCodec.Attrs {
            val index = layout.lineAtVisible(at)
            val line = lines[index]
            val attrs = line.neighbourAttrs(line.insertionIndex(at - start(index)))
            // Pusty nagłówek („# " bez treści) nie ma znaku, który by go niósł.
            if (line.text.isEmpty() && line.kind == LineKind.HEADING) {
                return attrs.copy(heading = RichTextCodec.headingLevelOf(line.prefix).takeIf { it > 0 })
            }
            return attrs
        }

        fun formatsAt(at: Int, pending: PendingFormat, base: Float): Formats {
            val attrs = pending.appliedTo(attrsAt(at))
            return Formats(
                active = SpanType.entries.filterTo(HashSet()) { attrs.has(it) },
                heading = attrs.heading,
                sizePx = attrs.sizePx ?: natural(attrs, base),
                color = attrs.color,
            )
        }

        /**
         * Znaki treści objęte widocznym zakresem [from, to). Znak listy na
         * początku wiersza i ukryta budowa nie należą do treści, więc format
         * ich nie dotyka.
         *
         * Odnośnik idzie w całości albo wcale: zapis „[opis](adres)" dostaje
         * format tylko razem z całym opisem. Pogrubiona połowa opisu zostaje
         * w nawiasach - „[**str**onę](adres)" - zamiast rozcinać zapis
         * odnośnika, którego strona by wtedy nie rozpoznała.
         */
        fun pieces(from: Int, to: Int, trim: Boolean): List<Piece> {
            val result = ArrayList<Piece>()
            for (index in layout.lineAtVisible(from)..layout.lineAtVisible(to)) {
                val line = lines[index]
                val contentFrom = start(index) + line.visiblePrefix
                val contentTo = start(index) + line.visibleLength()
                val a = maxOf(from, contentFrom) - contentFrom
                val b = minOf(to, contentTo) - contentFrom
                if (b <= a) continue

                val text = line.text
                val mask = BooleanArray(text.length)
                for (i in line.indexOfVisible(a) until line.indexOfVisible(b - 1) + 1) mask[i] = true
                for (link in linkPattern.findAll(text)) {
                    val label = link.groups[1] ?: continue
                    val labelFrom = label.range.first
                    val labelTo = labelFrom + label.value.length
                    val whole = labelTo > labelFrom && (labelFrom until labelTo).all { mask[it] }
                    for (i in link.range.first until labelFrom) mask[i] = whole
                    for (i in labelTo..link.range.last) mask[i] = whole
                }

                val chosen = ArrayList<Int>()
                var i = 0
                while (i < text.length) {
                    if (!mask[i]) {
                        i++
                        continue
                    }
                    var runEnd = i
                    while (runEnd < text.length && mask[runEnd]) runEnd++
                    var lo = i
                    var hi = runEnd
                    if (trim) {
                        while (lo < hi && text[lo].isWhitespace()) lo++
                        while (hi > lo && text[hi - 1].isWhitespace()) hi--
                    }
                    for (k in lo until hi) chosen += k
                    i = runEnd
                }
                if (chosen.isNotEmpty()) result += Piece(line, chosen)
            }
            return result
        }

        fun change(pieces: List<Piece>, transform: (RichTextCodec.Attrs) -> RichTextCodec.Attrs) {
            for (piece in pieces) {
                for (i in piece.indices) piece.line.attrs[i] = transform(piece.line.attrs[i])
            }
        }

        /**
         * Nagłówek w pustym akapicie: akapit od razu staje się nagłówkiem, jak
         * w Wordzie, i to, co się w nim napisze, jest nagłówkiem - także po
         * przejściu kursorem gdzie indziej i z powrotem. Ten sam poziom drugi
         * raz go zdejmuje. Null, gdy akapit nie jest pusty.
         */
        fun headingOnEmptyLine(at: Int, level: Int): String? {
            val line = lines[layout.lineAtVisible(at)]
            if (line.text.isNotEmpty()) return null
            if (line.kind != LineKind.PARAGRAPH && line.kind != LineKind.HEADING) return null
            val current = if (line.kind == LineKind.HEADING) RichTextCodec.headingLevelOf(line.prefix) else 0
            if (current == level) {
                line.dropPrefix()
            } else {
                line.prefix = "#".repeat(level) + " "
                line.prefixHidden = true
                line.kind = LineKind.HEADING
            }
            return write()
        }

        /** Nowe pole z tym samym zaznaczeniem (albo kursorem) w tym, co widać. */
        fun fieldAfter(text: String, from: Int, to: Int): TextFieldValue {
            val shaped = TextLayout.of(text)
            val selection = if (to > from) shaped.sourceSelection(from, to) else TextRange(shaped.sourceCursor(from))
            return TextFieldValue(text, selection)
        }
    }

    private val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")

    /** Format, który przestaje czekać na pisanie - został już nadany wprost. */
    private fun PendingFormat.forget(type: SpanType): PendingFormat =
        copy(on = on - type, off = off - type)
}
