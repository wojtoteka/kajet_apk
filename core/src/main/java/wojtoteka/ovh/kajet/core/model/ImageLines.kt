package wojtoteka.ovh.kajet.core.model

import kotlin.math.roundToInt

/**
 * Jedno zdjęcie albo rysunek z wiersza notatki.
 *
 * [width] to ułamek szerokości notatki (0.01-1), [align] mówi, gdzie w wierszu
 * stoi całe to, co w nim jest: przy lewej krawędzi, na środku albo przy prawej.
 * Ułożenie dotyczy CAŁEGO wiersza, więc wszystkie zdjęcia stojące obok siebie
 * mają je takie samo - [ImageLines.read] wyrównuje je do pierwszego.
 */
data class NotePhoto(
    val alt: String,
    val url: String,
    val width: Float = ImageLines.FULL_WIDTH,
    val align: NoteAlign = NoteAlign.LEFT,
)

/**
 * Wiersz notatki złożony ze zdjęć - czytanie i zapis, jeden na całą aplikację.
 *
 * Zdjęcia stojące w JEDNYM wierszu pliku stoją obok siebie także w notatce:
 *
 * ```
 * ![mapa|25%](assets/mapa.png) ![szkic|25%](assets/szkic.png)
 * ```
 *
 * Tak samo czyta to markdown na stronie - dwa zdjęcia w jednym akapicie idą
 * jedno przy drugim, więc zapis nie jest niczym naszym własnym. Wcześniej
 * każde zdjęcie musiało stać w swoim wierszu i przy 25% szerokości zostawał
 * po nim pusty pas przez trzy czwarte notatki.
 *
 * Szerokość stoi w opisie (`|25%`), a ułożenie w tytule (`"srodek"`). Tytuł,
 * a nie opis, bo opis czyta czytnik ekranu i trafia do wydruku - słowo
 * „srodek" nie jest opisem zdjęcia. Czytamy też dawny zapis z tabletu,
 * w którym w tytule stała szerokość (`"50%"`).
 */
object ImageLines {

    const val FULL_WIDTH = 1f

    /** Najmniejsza szerokość przy zapisie i suwaku - ten sam próg co serwer. */
    const val SMALLEST_WIDTH = 0.2f

    // URL łapany leniwie, bo magazyn potrafi nadać nazwę ze spacją i nawiasem
    // ("zdjecie (2).jpg"). Wyrażenie obejmuje CAŁY kawałek wiersza, więc
    // domykający nawias jest ten ostatni - nazwa z nawiasem w środku przeżyje.
    private val token = Regex("""^!\[([^\]]*)]\((.+?)(?:\s+"([^"]*)")?\)$""")

    private val altWidth = Regex("""^(.*?)\s*\|\s*(\d{1,3})\s*%$""")
    private val percentTitle = Regex("""^(\d{1,3})%$""")

    private const val CENTRE_MARK = "srodek"
    private const val RIGHT_MARK = "prawo"

    /**
     * Zdjęcia z wiersza albo null, gdy w wierszu stoi cokolwiek poza nimi.
     *
     * Null znaczy „to nie jest wiersz ze zdjęciami" - wtedy wiersz jest
     * zwykłym tekstem, także wtedy, gdy zdjęcie stoi w środku zdania.
     */
    fun read(line: String): List<NotePhoto>? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("![")) return null

        val starts = mutableListOf<Int>()
        var at = 0
        while (true) {
            val found = trimmed.indexOf("![", at)
            if (found < 0) break
            starts += found
            at = found + 2
        }

        val photos = mutableListOf<NotePhoto>()
        for ((index, start) in starts.withIndex()) {
            val stop = starts.getOrNull(index + 1) ?: trimmed.length
            val piece = trimmed.substring(start, stop)
            // Domykający nawias tego zdjęcia to ostatni nawias przed następnym
            // zdjęciem. Między zdjęciami może stać tylko odstęp - inaczej to
            // jest zdanie ze zdjęciem w środku, a nie wiersz ze zdjęciami.
            val close = piece.lastIndexOf(')')
            if (close < 0) return null
            if (piece.substring(close + 1).isNotBlank()) return null

            val match = token.find(piece.substring(0, close + 1)) ?: return null
            val (alt, width) = sizeOf(match.groupValues[1], match.groupValues[3])
            photos += NotePhoto(
                alt = alt,
                url = match.groupValues[2].trim(),
                width = width,
                align = alignOf(match.groupValues[3]),
            )
        }
        if (photos.isEmpty()) return null

        // Ułożenie ma cały wiersz, nie pojedyncze zdjęcie. Gdyby drugie zdjęcie
        // miało własne, wiersz nie miałby dokąd się przesunąć.
        val align = photos.first().align
        return photos.map { if (it.align == align) it else it.copy(align = align) }
    }

    /** Sam opis, bez dopisanej szerokości. */
    fun plainAlt(alt: String): String = sizeOf(alt, "").first

    /** Jedno zdjęcie w kanonicznym zapisie. */
    fun write(photo: NotePhoto): String {
        val alt = plainAlt(photo.alt)
        val percent = (photo.width * 100).roundToInt().coerceIn(
            (SMALLEST_WIDTH * 100).roundToInt(),
            (FULL_WIDTH * 100).roundToInt(),
        )
        val described = if (photo.width >= FULL_WIDTH || percent >= 100) alt else "$alt|$percent%"
        val title = when (photo.align) {
            NoteAlign.LEFT -> ""
            NoteAlign.CENTER -> """ "$CENTRE_MARK""""
            NoteAlign.RIGHT -> """ "$RIGHT_MARK""""
        }
        return "![$described](${photo.url}$title)"
    }

    /** Cały wiersz: zdjęcia obok siebie dzieli odstęp. */
    fun write(photos: List<NotePhoto>): String = photos.joinToString(" ") { write(it) }

    /**
     * Szerokości zdjęć w wierszu, ściągnięte tak, żeby zmieściły się obok
     * siebie. [gapShare] to ułamek szerokości notatki zjadany przez odstępy
     * między zdjęciami.
     *
     * Bez tego dwa zdjęcia po 75% wychodziłyby poza kartkę - a suwak od
     * szerokości nic o sąsiedzie nie wie i wiedzieć nie musi.
     */
    fun sideBySide(widths: List<Float>, gapShare: Float = 0f): List<Float> {
        if (widths.isEmpty()) return widths
        val room = (FULL_WIDTH - gapShare).coerceAtLeast(0.01f)
        val together = widths.sumOf { it.toDouble() }.toFloat()
        val shrink = if (together > room) room / together else 1f
        return widths.map { (it * shrink).coerceIn(0.01f, FULL_WIDTH) }
    }

    /**
     * Opis i szerokość z obu zapisów: `![opis|60%](url)` oraz
     * `![opis](url "60%")`. Z pliku bierzemy, co stoi (także 10%);
     * dolny próg 20% obowiązuje dopiero przy zapisie.
     */
    private fun sizeOf(rawAlt: String, title: String): Pair<String, Float> {
        val fromAlt = altWidth.find(rawAlt)
        if (fromAlt != null) {
            return fromAlt.groupValues[1] to percentFromFile(fromAlt.groupValues[2])
        }
        val fromTitle = percentTitle.find(title.trim())?.groupValues?.get(1)
        if (fromTitle != null) {
            return rawAlt to percentFromFile(fromTitle)
        }
        return rawAlt to FULL_WIDTH
    }

    private fun percentFromFile(raw: String): Float {
        val percent = raw.toIntOrNull() ?: return FULL_WIDTH
        return percent.coerceIn(1, 100) / 100f
    }

    // Po angielsku też, bo ten sam zapis czyta strona i notatka może przyjechać
    // spoza tabletu.
    private fun alignOf(title: String): NoteAlign = when (title.trim().lowercase()) {
        CENTRE_MARK, "środek", "center", "centre" -> NoteAlign.CENTER
        RIGHT_MARK, "right" -> NoteAlign.RIGHT
        else -> NoteAlign.LEFT
    }
}
