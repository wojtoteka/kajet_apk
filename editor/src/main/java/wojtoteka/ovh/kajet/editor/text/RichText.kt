package wojtoteka.ovh.kajet.editor.text

import kotlin.math.roundToInt
import wojtoteka.ovh.kajet.core.model.ParagraphAlign
import wojtoteka.ovh.kajet.core.model.TextMarkers

/**
 * Jeden format źródłowy treści z formatowaniem.
 *
 * Treść notatki leży na dysku i idzie na serwer jako markdown ze znacznikami
 * HTML tam, gdzie markdown nie ma własnego zapisu:
 *
 *   **grube**  *pochyłe*  ~~skreślone~~  ==wyróżnione==
 *   <u>podkreślone</u>
 *   <span style="color:#RRGGBB">barwne</span>
 *   <span style="font-size:21px">większe</span>
 *
 * Dokładnie ten zapis czyta i pisze serwer (`src/lib/rich-text.ts`), więc
 * format pliku zostaje bez zmian. Zmienia się to, co dzieje się w pamięci:
 * edytor nie dokleja już znaczników do ciągu znaków, tylko czyta treść na
 * [RichText] - czysty tekst plus lista zakresów - przestawia zakresy i składa
 * zapis z powrotem. Dzięki temu znaczniki nie mają jak się zagnieździć,
 * osierocić ani wyjść poza zaznaczenie.
 *
 * Czego ten model NIE obejmuje: nagłówków, list, zadań, cytatów, tabel,
 * odnośników, zdjęć, wzorów i bloków kodu. To budowa notatki, nie format
 * fragmentu - zostaje w treści znak w znak i zajmują się nią [Blocks]
 * oraz [InlineStyle].
 */
enum class SpanType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    COLOR,
    SIZE,
    HIGHLIGHT,

    /**
     * Kod w zdaniu - `tak`. W środku znaczniki nic nie znaczą, więc treść
     * takiego kawałka zostaje znak w znak.
     */
    CODE,
    ;

    /** Czy rodzaj niesie wartość (barwa, liczba pikseli), czy sam siebie. */
    val carriesValue: Boolean get() = this == COLOR || this == SIZE
}

/**
 * Zakres jednego formatu: od [start] włącznie do [end] bez.
 *
 * [value] ma znaczenie wyłącznie dla dwóch rodzajów:
 *   [SpanType.COLOR] - barwa jako „#RRGGBB",
 *   [SpanType.SIZE]  - wielkość pisma w pikselach, na przykład „21".
 * Dla pozostałych zostaje pusty.
 */
data class FormatSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String = "",
)

/** Czysta treść i formaty nałożone na jej zakresy. */
data class RichText(
    val text: String,
    val spans: List<FormatSpan> = emptyList(),
) {

    /** Zapis do pliku notatki i na serwer. */
    fun toMarkdown(): String = RichTextCodec.write(this).markdown

    /** Formaty obejmujące CAŁY zakres [from] do [to]; przy pustym - te pod kursorem. */
    fun formatsIn(from: Int, to: Int): Set<FormatSpan> {
        val start = from.coerceIn(0, text.length)
        val end = to.coerceIn(start, text.length)
        if (start == end) return formatsAt(start)

        return spans
            .filter { it.start <= start && it.end >= end }
            .distinctBy { it.type to it.value }
            .toSet()
    }

    /**
     * Formaty pod kursorem. Liczy się znak PRZED kursorem - tak samo jak
     * w każdym innym notatniku: kto stanie za pogrubionym słowem i zacznie
     * pisać, pisze dalej pogrubione.
     */
    fun formatsAt(cursor: Int): Set<FormatSpan> {
        val at = cursor.coerceIn(0, text.length)
        val character = if (at > 0) at - 1 else at
        if (text.isEmpty()) return emptySet()
        return spans.filter { character >= it.start && character < it.end }.toSet()
    }

    companion object {
        /** Czyta zapis notatki na czysty tekst i zakresy. */
        fun parse(markdown: String): RichText = RichTextCodec.read(markdown).rich
    }
}

/**
 * Przekład zapis <-> model, w obie strony i bez straty.
 *
 * W środku model żyje jako „każdy znak ma swój zestaw formatów" ([Attrs]).
 * Taki zapis nie umie być niepoprawny: nie ma w nim zagnieżdżeń, osieroconych
 * domknięć ani zakresów zachodzących na siebie krzywo. Lista [FormatSpan] to
 * ten sam stan podany na zewnątrz - powstaje z ciągów znaków o tych samych
 * formatach i wraca do nich tak samo.
 */
object RichTextCodec {

    /** Formaty jednego znaku. */
    data class Attrs(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val highlight: Boolean = false,
        val code: Boolean = false,
        val color: Int? = null,
        val sizePx: Float? = null,
    ) {
        fun with(type: SpanType, value: String): Attrs = when (type) {
            SpanType.BOLD -> copy(bold = true)
            SpanType.ITALIC -> copy(italic = true)
            SpanType.UNDERLINE -> copy(underline = true)
            SpanType.STRIKETHROUGH -> copy(strikethrough = true)
            SpanType.HIGHLIGHT -> copy(highlight = true)
            SpanType.CODE -> copy(code = true)
            SpanType.COLOR -> copy(color = TextMarkers.colorFromHex(value) ?: color)
            SpanType.SIZE -> copy(sizePx = value.toFloatOrNull() ?: sizePx)
        }

        fun without(type: SpanType): Attrs = when (type) {
            SpanType.BOLD -> copy(bold = false)
            SpanType.ITALIC -> copy(italic = false)
            SpanType.UNDERLINE -> copy(underline = false)
            SpanType.STRIKETHROUGH -> copy(strikethrough = false)
            SpanType.HIGHLIGHT -> copy(highlight = false)
            SpanType.CODE -> copy(code = false)
            SpanType.COLOR -> copy(color = null)
            SpanType.SIZE -> copy(sizePx = null)
        }

        fun has(type: SpanType): Boolean = when (type) {
            SpanType.BOLD -> bold
            SpanType.ITALIC -> italic
            SpanType.UNDERLINE -> underline
            SpanType.STRIKETHROUGH -> strikethrough
            SpanType.HIGHLIGHT -> highlight
            SpanType.CODE -> code
            SpanType.COLOR -> color != null
            SpanType.SIZE -> sizePx != null
        }

        fun valueOf(type: SpanType): String = when (type) {
            SpanType.COLOR -> color?.let { colorHex(it) }.orEmpty()
            SpanType.SIZE -> sizePx?.let { sizeText(it) }.orEmpty()
            else -> ""
        }
    }

    val NONE = Attrs()

    /** Model plus mapy w obie strony, do przestawienia kursora i zaznaczenia. */
    class Parsed(
        val rich: RichText,
        val attrs: List<Attrs>,
        private val toPlain: IntArray,
        private val toSource: IntArray,
    ) {
        fun plainOffset(sourceOffset: Int): Int =
            toPlain[sourceOffset.coerceIn(0, toPlain.size - 1)]

        /** Gdzie w zapisie stoi znak treści o tej pozycji. */
        fun sourceOffset(plainOffset: Int): Int =
            toSource[plainOffset.coerceIn(0, toSource.size - 1)]

        /** Koniec zakresu w zapisie: tuż za treścią, przed znacznikiem domykającym. */
        fun sourceEnd(plainEnd: Int): Int =
            if (plainEnd <= 0) sourceOffset(0) else sourceOffset(plainEnd - 1) + 1
    }

    /** Zapis plus mapa czysty tekst -> zapis. */
    class Rendered(val markdown: String, private val fromPlain: IntArray) {
        fun sourceOffsetOf(plainOffset: Int): Int =
            fromPlain[plainOffset.coerceIn(0, fromPlain.size - 1)]

        /** Koniec zakresu: tuż za ostatnim znakiem, przed znacznikiem domykającym. */
        fun sourceEndOf(plainEnd: Int): Int =
            if (plainEnd <= 0) sourceOffsetOf(0) else sourceOffsetOf(plainEnd - 1) + 1
    }

    // --- Czytanie ---

    private const val CLOSING = "</span>"
    private const val UNDERLINE_OPENING = "<u>"
    private const val UNDERLINE_CLOSING = "</u>"
    private const val UNKNOWN_OPENING = "<span "

    /** Najdłuższy możliwy znacznik - tyle wystarczy obejrzeć przy czytaniu. */
    private const val LONGEST_TAG = 48

    private val colorOpening = Regex("""^<span style="color:(#[0-9a-fA-F]{6,8})">""")
    private val sizeOpening = Regex("""^<span style="font-size:(\d+(?:\.\d+)?)px">""")

    /**
     * Początek wiersza, który jest budową notatki, a nie treścią: kratki
     * nagłówka, znak listy, kwadracik zadania, znak cytatu. Zostaje w treści
     * znak w znak - inaczej gwiazdka listy „* mleko" udawałaby kursywę.
     *
     * Kratki biorą się w pętli (`(?:#{1,6} )+`), bo przełączanie H1/H2/H3
     * potrafiło zostawić „## # Tytuł". Jedna kratka zostawiałaby wewnętrzne
     * krzyżyki w treści i wychodziłyby na wierzch zamiast nagłówka.
     */
    private val blockPrefix = Regex("""^\s*(?:(?:#{1,6} )+|> |[-*+] \[[ xX]] |[-*+] |\d+[.)] )""")

    /**
     * Czy [marker] to kratki nagłówka z paska (H1-H6, ze spacją).
     * Inne znaczniki wiersza - lista, cytat - idą inną drogą.
     */
    fun isHeadingMarker(marker: String): Boolean {
        if (!marker.endsWith(' ')) return false
        val hashes = marker.length - 1
        return hashes in 1..6 && marker.take(hashes).all { it == '#' }
    }

    /**
     * Długość kratek na początku [line] - także poskładanych z kilku
     * naciśnięć H1/H2/H3 („## # Tytuł"). Zero, gdy wiersz nie jest nagłówkiem.
     * Wcięcie trzeba zdjąć wcześniej: tu liczy się od pierwszego znaku.
     */
    fun headingPrefixLength(line: String): Int {
        var i = 0
        var consumed = 0
        while (i < line.length && line[i] == '#') {
            var hashes = 0
            while (i < line.length && line[i] == '#') {
                hashes++
                i++
            }
            if (hashes !in 1..6) break
            if (i < line.length && line[i] == ' ') {
                i++
                consumed = i
                continue
            }
            // Ogonek bez spacji po już zdjętej kratce („## #") - to nadal
            // znacznik, nie treść. Samo „###" bez spacji zostaje tekstem.
            if (consumed > 0 && i == line.length) consumed = i
            break
        }
        return consumed
    }

    /** Znacznik otwierający blok kodu albo wzoru; null, gdy wiersz nim nie jest. */
    fun opensFence(trimmed: String): String? = when {
        trimmed.startsWith("```") -> "```"
        // Sam „$$" w wierszu. Wzór wpisany w jednym wierszu („$$x$$") to nie
        // blok, tylko treść - zostaje tam, gdzie stoi.
        trimmed == "$$" -> "$$"
        else -> null
    }

    /** Czy wiersz domyka otwarty blok [fence]. */
    fun closesFence(trimmed: String, fence: String): Boolean =
        if (fence == "```") trimmed.startsWith("```") else trimmed == "$$"

    private class Sink(size: Int) {
        val plain = StringBuilder(size)
        val attrs = ArrayList<Attrs>(size)
        val toPlain = IntArray(size + 1)
        val toSource = ArrayList<Int>(size + 1)

        fun mark(from: Int, to: Int) {
            for (i in from until to) if (i in toPlain.indices) toPlain[i] = plain.length
        }

        fun add(sourceAt: Int, ch: Char, attributes: Attrs) {
            plain.append(ch)
            attrs.add(attributes)
            toSource += sourceAt
        }

        fun verbatim(text: String, from: Int, to: Int, attributes: Attrs) {
            for (i in from until to) {
                mark(i, i + 1)
                add(i, text[i], attributes)
            }
        }
    }

    /** Czyta zapis notatki. Bloki kodu zostają nietknięte co do znaku. */
    fun read(markdown: String): Parsed {
        val sink = Sink(markdown.length)
        // Blok kodu albo wzoru: „```" lub „$$". W środku znaczniki nic nie
        // znaczą - gwiazdka we wzorze to mnożenie, a nie kursywa.
        var fence: String? = null
        var at = 0

        while (at <= markdown.length) {
            val lineEnd = markdown.indexOf('\n', at).let { if (it < 0) markdown.length else it }
            val line = markdown.substring(at, lineEnd)
            val trimmed = line.trimStart()

            if (fence != null) {
                if (closesFence(trimmed, fence)) fence = null
                sink.verbatim(markdown, at, lineEnd, NONE)
            } else if (opensFence(trimmed) != null) {
                fence = opensFence(trimmed)
                sink.verbatim(markdown, at, lineEnd, NONE)
            } else {
                // Budowa wiersza zostaje; formaty czytamy tylko z jego treści.
                // Znacznik ułożenia akapitu obejmuje cały wiersz - jego
                // otwarcie i domknięcie to też budowa, nie treść.
                val open = ParagraphAlign.openingLength(line)
                val prefix = open + (blockPrefix.find(line.substring(open))?.value?.length ?: 0)
                val close = ParagraphAlign.closingLength(line, open)
                    .takeIf { line.length - it >= prefix } ?: 0
                sink.verbatim(markdown, at, at + prefix, NONE)
                scan(markdown, at + prefix, lineEnd - close, NONE, sink, IntArray(1))
                sink.verbatim(markdown, lineEnd - close, lineEnd, NONE)
            }

            if (lineEnd >= markdown.length) {
                sink.mark(lineEnd, lineEnd + 1)
                break
            }
            sink.mark(lineEnd, lineEnd + 1)
            sink.add(lineEnd, '\n', NONE)
            at = lineEnd + 1
        }

        val plain = sink.plain.toString()
        sink.toSource += markdown.length
        return Parsed(
            rich = RichText(plain, spansOf(plain, sink.attrs)),
            attrs = sink.attrs,
            toPlain = sink.toPlain,
            toSource = sink.toSource.toIntArray(),
        )
    }

    /**
     * Czyta treść jednego wiersza od [from] do [to], dokładając [base] do
     * każdego znaku. Znaczniki obejmujące dalszą treść wchodzą w rekurencję,
     * więc zagnieżdżenie składa się samo i nie ma jak się rozjechać.
     */
    private fun scan(
        text: String,
        from: Int,
        to: Int,
        base: Attrs,
        sink: Sink,
        /*
          Ile nieznanych znaczników <span ...> stoi otwartych w tym wierszu.
          Ich domknięcia mają zostać w treści razem z nimi, a nie zniknąć jako
          osierocone - inaczej z notatki wyparowałby kawałek cudzego zapisu.
        */
        unknown: IntArray,
    ) {
        var at = from
        while (at < to) {
            val ch = text[at]

            // Kod w tekście stoi najwyżej: w środku nic już nie znaczy, więc
            // jego treść idzie do modelu znak w znak, z samym znacznikiem kodu.
            if (ch == '`' && !base.code) {
                val close = text.indexOf('`', at + 1).takeIf { it in (at + 1) until to }
                if (close != null) {
                    sink.mark(at, at + 1)
                    sink.verbatim(text, at + 1, close, base.copy(code = true))
                    sink.mark(close, close + 1)
                    at = close + 1
                    continue
                }
            }

            // Adres odnośnika i zdjęcia zostaje znak w znak; opis czytamy zwykle.
            if (ch == '(' && at > from && text[at - 1] == ']') {
                val close = text.indexOf(')', at + 1).takeIf { it in (at + 1) until to }
                if (close != null) {
                    sink.verbatim(text, at, close + 1, base)
                    at = close + 1
                    continue
                }
            }

            if (ch == '<') {
                val consumed = readTag(text, at, to, base, sink, unknown)
                if (consumed > 0) {
                    at += consumed
                    continue
                }
            }

            val pair = readPair(text, at, to, from, base, sink, unknown)
            if (pair > 0) {
                at += pair
                continue
            }

            sink.mark(at, at + 1)
            sink.add(at, ch, base)
            at++
        }
    }

    /** Znacznik HTML pod [at]. Zwraca, ile znaków zapisu zabrał; 0 = to nie znacznik. */
    private fun readTag(
        text: String,
        at: Int,
        to: Int,
        base: Attrs,
        sink: Sink,
        unknown: IntArray,
    ): Int {
        if (text.startsWith(CLOSING, at) && at + CLOSING.length <= to) {
            if (unknown[0] > 0) {
                // Domknięcie nieznanego znacznika: zostaje w treści.
                unknown[0]--
                return 0
            }
            // Osierocone domknięcie - ślad po zepsutym zapisie. Znika.
            sink.mark(at, at + CLOSING.length)
            return CLOSING.length
        }

        if (text.startsWith(UNDERLINE_CLOSING, at) && at + UNDERLINE_CLOSING.length <= to) {
            sink.mark(at, at + UNDERLINE_CLOSING.length)
            return UNDERLINE_CLOSING.length
        }

        if (text.startsWith(UNDERLINE_OPENING, at) && at + UNDERLINE_OPENING.length <= to) {
            val innerFrom = at + UNDERLINE_OPENING.length
            val close = text.indexOf(UNDERLINE_CLOSING, innerFrom)
                .takeIf { it in innerFrom until to } ?: to
            sink.mark(at, innerFrom)
            scan(text, innerFrom, close, base.copy(underline = true), sink, unknown)
            val after = if (close < to) close + UNDERLINE_CLOSING.length else to
            sink.mark(close, after)
            return after - at
        }

        val rest = text.substring(at, minOf(to, at + LONGEST_TAG))
        val colour = colorOpening.find(rest)
        val size = if (colour == null) sizeOpening.find(rest) else null
        if (colour == null && size == null) {
            // Nieznany <span ...>: zostaje w treści razem ze swoim domknięciem.
            if (text.startsWith(UNKNOWN_OPENING, at)) unknown[0]++
            return 0
        }

        val opening = (colour ?: size)!!.value.length
        val innerFrom = at + opening
        val close = closingSpanAt(text, innerFrom, to)
        val next = if (colour != null) {
            base.copy(color = TextMarkers.colorFromHex(colour.groupValues[1]) ?: base.color)
        } else {
            base.copy(sizePx = size!!.groupValues[1].toFloatOrNull() ?: base.sizePx)
        }

        sink.mark(at, innerFrom)
        // Znacznik bez domknięcia działa do końca wiersza - tak czytały go
        // stare notatki i tak czyta je strona.
        val innerTo = close ?: to
        scan(text, innerFrom, innerTo, next, sink, unknown)
        val after = if (close != null) close + CLOSING.length else to
        sink.mark(innerTo, after)
        return after - at
    }

    /** Domknięcie znacznika span otwartego tuż przed [from], z liczeniem zagnieżdżeń. */
    private fun closingSpanAt(text: String, from: Int, to: Int): Int? {
        var depth = 1
        var at = from
        while (at < to) {
            when {
                text.startsWith(CLOSING, at) -> {
                    depth--
                    if (depth == 0) return at
                    at += CLOSING.length
                }

                text.startsWith(UNKNOWN_OPENING, at) -> {
                    depth++
                    at += UNKNOWN_OPENING.length
                }

                else -> at++
            }
        }
        return null
    }

    private val pairs = listOf(
        "**" to SpanType.BOLD,
        "~~" to SpanType.STRIKETHROUGH,
        "==" to SpanType.HIGHLIGHT,
        "*" to SpanType.ITALIC,
    )

    /** Znacznik parzysty pod [at]. Bez pary zostaje w treści zwykłym tekstem. */
    private fun readPair(
        text: String,
        at: Int,
        to: Int,
        /** Początek czytanego kawałka - patrz [opensItalic]. */
        regionFrom: Int,
        base: Attrs,
        sink: Sink,
        unknown: IntArray,
    ): Int {
        for ((marker, type) in pairs) {
            if (!text.startsWith(marker, at) || at + marker.length > to) continue
            if (base.has(type)) continue

            if (marker == "*" && !opensItalic(text, at, to, regionFrom)) continue

            val innerFrom = at + marker.length
            val close = closingPairAt(text, innerFrom, to, marker) ?: continue

            sink.mark(at, innerFrom)
            scan(text, innerFrom, close, base.with(type, ""), sink, unknown)
            sink.mark(close, close + marker.length)
            return close + marker.length - at
        }
        return 0
    }

    /**
     * Pojedyncza gwiazdka zaczyna kursywę tylko poza słowem - inaczej
     * „2 * 3 * 4" wracałoby z notatki pochyłe.
     *
     * Gwiazdka tuż za inną gwiazdką normalnie nie otwiera niczego, ale na
     * samym początku czytanego kawałka ([regionFrom]) otwiera: poprzednia
     * gwiazdka poszła wtedy na pogrubienie, a to jest „***grube i pochyłe***".
     */
    private fun opensItalic(text: String, at: Int, to: Int, regionFrom: Int): Boolean {
        if (text.startsWith("**", at)) return false
        val before = if (at > 0) text[at - 1] else ' '
        // Gwiazdka tuż za inną gwiazdką to nie kursywa, tylko reszta pary.
        // Gwiazdka w środku słowa kursywę otwiera - tak liczy to i serwer,
        // i CommonMark, a bez tego „Al*a ma k*ota" pokazywało gołe gwiazdki.
        if (at > regionFrom && before == '*') return false
        val after = if (at + 1 < to) text[at + 1] else return false
        return after != ' ' && after != '*'
    }

    private fun closingPairAt(text: String, from: Int, to: Int, marker: String): Int? {
        var at = from
        while (at + marker.length <= to) {
            if (!text.startsWith(marker, at)) {
                at++
                continue
            }
            /*
              Zbite znaczniki, na przykład „***grube i pochyłe***": para
              dwuznakowa bierze DWIE OSTATNIE gwiazdki ciągu, żeby pojedyncza
              miała czym się domknąć. Tak samo liczy to serwer.
            */
            if (marker.length == 2) {
                var run = 0
                while (at + run < to && text[at + run] == marker[0]) run++
                if (run > marker.length) at += run - marker.length
            }
            if (at == from) {
                // Pusta zawartość: „**" to dwie gwiazdki, nie pogrubienie.
                at += marker.length
                continue
            }
            // Pojedyncza gwiazdka nie domyka się o podwójną - ale tylko wtedy,
            // gdy ta druga naprawdę należy jeszcze do czytanego kawałka.
            val glued = marker == "*" &&
                ((at > from && text[at - 1] == '*') || (at + 1 < to && text[at + 1] == '*'))
            if (glued) {
                at++
                continue
            }
            return at
        }
        return null
    }

    // --- Składanie zapisu ---

    /**
     * Warstwy od najbardziej zewnętrznej. Porządek jest stały, więc ten sam
     * stan zawsze daje ten sam zapis - na tym stoi bezstratność. Rozmiar
     * stoi na zewnątrz barwy, dokładnie tak jak pisze serwer.
     */
    private val layers = listOf(
        SpanType.BOLD,
        SpanType.ITALIC,
        SpanType.STRIKETHROUGH,
        SpanType.HIGHLIGHT,
        SpanType.UNDERLINE,
        SpanType.SIZE,
        SpanType.COLOR,
        // Kod przy samej treści: między grawisami nic już nie ma prawa stać.
        SpanType.CODE,
    )

    fun write(rich: RichText): Rendered = write(rich.text, attrsOf(rich))

    /** Składa formaty znaków z powrotem w zapis notatki. */
    fun write(plain: String, attrs: List<Attrs>): Rendered {
        val out = StringBuilder(plain.length + 16)
        val fromPlain = IntArray(plain.length + 1)
        // Co jest teraz otwarte, od zewnątrz: rodzaj i jego wartość.
        val open = ArrayList<Pair<SpanType, String>>(layers.size)

        fun close(downTo: Int) {
            while (open.size > downTo) {
                out.append(closingOf(open.removeAt(open.size - 1).first))
            }
        }

        for (i in plain.indices) {
            val ch = plain[i]
            if (ch == '\n') {
                // Nowy wiersz zamyka wszystko: format nie przechodzi na
                // kolejny akapit, tak samo czyta go strona.
                close(0)
            } else {
                val wanted = attrs.getOrElse(i) { NONE }
                val target = layers
                    .filter { wanted.has(it) }
                    .map { it to wanted.valueOf(it) }

                var same = 0
                while (same < open.size && same < target.size && open[same] == target[same]) {
                    same++
                }
                close(same)
                for (layer in target.drop(same)) {
                    out.append(openingOf(layer.first, layer.second))
                    open += layer
                }
            }
            fromPlain[i] = out.length
            out.append(ch)
        }
        close(0)
        fromPlain[plain.length] = out.length

        return Rendered(out.toString(), fromPlain)
    }

    private fun openingOf(type: SpanType, value: String): String = when (type) {
        SpanType.BOLD -> "**"
        SpanType.ITALIC -> "*"
        SpanType.STRIKETHROUGH -> "~~"
        SpanType.HIGHLIGHT -> "=="
        SpanType.UNDERLINE -> UNDERLINE_OPENING
        SpanType.CODE -> "`"
        SpanType.SIZE -> "<span style=\"font-size:${value}px\">"
        SpanType.COLOR -> "<span style=\"color:$value\">"
    }

    private fun closingOf(type: SpanType): String = when (type) {
        SpanType.BOLD -> "**"
        SpanType.ITALIC -> "*"
        SpanType.STRIKETHROUGH -> "~~"
        SpanType.HIGHLIGHT -> "=="
        SpanType.UNDERLINE -> UNDERLINE_CLOSING
        SpanType.CODE -> "`"
        SpanType.SIZE, SpanType.COLOR -> CLOSING
    }

    // --- Model listy zakresów <-> formaty znaków ---

    /** Ciągi znaków o tym samym formacie, po jednym zakresie na ciąg. */
    fun spansOf(plain: String, attrs: List<Attrs>): List<FormatSpan> {
        val spans = mutableListOf<FormatSpan>()
        for (type in layers) {
            var start = -1
            var value = ""
            for (i in 0..plain.length) {
                val here = attrs.getOrNull(i)?.takeIf { i < plain.length && plain[i] != '\n' }
                val active = here?.has(type) == true
                val current = if (active) here.valueOf(type) else ""

                if (start >= 0 && (!active || current != value)) {
                    spans += FormatSpan(start, i, type, value)
                    start = -1
                }
                if (active && start < 0) {
                    start = i
                    value = current
                }
            }
        }
        return spans.sortedWith(compareBy({ it.start }, { it.type.ordinal }))
    }

    /** Zakresy z powrotem na formaty znaków. */
    fun attrsOf(rich: RichText): List<Attrs> {
        val attrs = MutableList(rich.text.length) { NONE }
        for (span in rich.spans) {
            val from = span.start.coerceIn(0, rich.text.length)
            val to = span.end.coerceIn(from, rich.text.length)
            for (i in from until to) {
                if (rich.text[i] == '\n') continue
                attrs[i] = attrs[i].with(span.type, span.value)
            }
        }
        return attrs
    }

    // --- Przestawianie formatów ---

    /**
     * Nadaje albo zdejmuje format w zakresie. [value] pustej barwy nie ma
     * sensu, więc zdejmowanie idzie przez [remove].
     */
    fun apply(
        plain: String,
        attrs: List<Attrs>,
        from: Int,
        to: Int,
        type: SpanType,
        value: String,
    ): Pair<RichText, List<Attrs>> = change(plain, attrs, from, to) { it.with(type, value) }

    fun remove(
        plain: String,
        attrs: List<Attrs>,
        from: Int,
        to: Int,
        type: SpanType,
    ): Pair<RichText, List<Attrs>> = change(plain, attrs, from, to) { it.without(type) }

    private fun change(
        plain: String,
        source: List<Attrs>,
        from: Int,
        to: Int,
        transform: (Attrs) -> Attrs,
    ): Pair<RichText, List<Attrs>> {
        val attrs = source.toMutableList()
        val start = from.coerceIn(0, attrs.size)
        val end = to.coerceIn(start, attrs.size)
        for (i in start until end) {
            // Znak nowej linii nie nosi formatów - znaczniki żyją w wierszu.
            if (plain.getOrNull(i) == '\n') continue
            attrs[i] = transform(attrs[i])
        }
        return RichText(plain, spansOf(plain, attrs)) to attrs
    }

    // --- Naprawa starych notatek ---

    /**
     * Jednorazowe sprowadzenie zapisu do postaci kanonicznej: zagnieżdżone,
     * osierocone i podwójne znaczniki schodzą do jednej formy. Naprawiony
     * zapis wraca bez zmiany, więc dzieje się to raz na notatkę.
     */
    fun flatten(text: String): String {
        if (!needsRepair(text)) return text
        val parsed = read(text)
        return write(parsed.rich.text, parsed.attrs).markdown
    }

    private fun needsRepair(text: String): Boolean =
        "<span " in text || CLOSING in text || UNDERLINE_OPENING in text

    /** Liczba pikseli bez zbędnego „,0" na końcu. */
    fun sizeText(px: Float): String =
        if (px == px.roundToInt().toFloat()) px.roundToInt().toString() else px.toString()

    /**
     * Barwa jako „#rrggbb", małymi literami. Serwer sprowadza barwę do tej
     * samej postaci (`normaliseColour` w rich-text.ts), więc zapis notatki
     * wychodzi po obu stronach znak w znak taki sam i synchronizacja nie ma
     * czego przestawiać po samym otwarciu notatki na stronie.
     */
    fun colorHex(argb: Int): String = "#%06x".format(argb and 0x00FFFFFF)
}
