package wojtoteka.ovh.kajet.export

import androidx.compose.ui.graphics.toArgb
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import kotlin.math.min

/**
 * To, czego PDF nie powinien zgadywać sam: przebiegi formatu, zdjęcia
 * z `assets/` i które węzły mapy widać. [PdfExport] tylko maluje wynik.
 *
 * Format fragmentu czyta [RichTextCodec] - ten sam, którym pisze edytor - więc
 * wydruk i plik Worda widzą dokładnie to, co notatka (także formaty jeden
 * w drugim i nagłówek nadany kawałkowi zdania). [DocxExport] bierze stąd te
 * same przebiegi.
 */
internal object PdfMarkdown {

    val HIGHLIGHT: Int = InkPalette.HighlighterYellow.toArgb()

    const val NODE_PAD_X = 20f
    const val NODE_PAD_Y = 12f
    const val NODE_FONT = 15f

    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val highlight: Boolean = false,
        val code: Boolean = false,
        val color: Int? = null,
        val sizePx: Float? = null,
        /** Poziom nagłówka nadanego kawałkowi zdania; null - zwykły tekst. */
        val heading: Int? = null,
    )

    data class ImageRef(
        val asset: String,
        val width: Float = 1f,
        val align: NoteAlign = NoteAlign.LEFT,
    )

    /** Zapis odnośnika i zdjęcia w zdaniu - na kartce zostaje sam opis. */
    private val linkPattern = Regex("""!?\[([^\]\n]*)]\(([^)\s]+)\)""")

    /**
     * Zdjęcia z wiersza - kilka, gdy w notatce stoją obok siebie.
     *
     * Czytanie samego wiersza siedzi w [ImageLines], wspólne z edytorem.
     * Wcześniej wydruk miał własne wyrażenie, które znało tylko szerokość
     * zapisaną w tytule - zdjęcie zmniejszone w notatce wychodziło z drukarki
     * na całą szerokość kartki.
     */
    fun images(line: String): List<ImageRef> {
        val photos = ImageLines.read(line) ?: return emptyList()
        return photos.mapNotNull { photo ->
            if (!photo.url.startsWith("assets/")) {
                null
            } else {
                ImageRef(
                    asset = photo.url.removePrefix("assets/"),
                    width = photo.width.coerceIn(0.1f, 1f),
                    align = photo.align,
                )
            }
        }
    }

    /**
     * Treść wiersza (już bez budowy: kratek, znaku listy) na kawałki o jednym
     * wyglądzie. Wcześniej czytały to wyrażenia regularne, które nie znały
     * formatów jeden w drugim: pogrubione słowo w innym rozmiarze wychodziło
     * na wydruku jako goły `<span style=...>`.
     */
    fun runs(markdown: String): List<Run> {
        if (markdown.isEmpty()) return emptyList()
        val parsed = RichTextCodec.read(markdown)
        val plain = parsed.rich.text
        val attrs = parsed.attrs

        val hidden = BooleanArray(plain.length)
        for (link in linkPattern.findAll(plain)) {
            val label = link.groups[1] ?: continue
            for (i in link.range.first until label.range.first) hidden[i] = true
            for (i in label.range.first + label.value.length..link.range.last) hidden[i] = true
        }

        val result = ArrayList<Run>()
        val text = StringBuilder()
        var current = RichTextCodec.NONE
        fun flush() {
            if (text.isNotEmpty()) result += runOf(text.toString(), current)
            text.clear()
        }
        for (i in plain.indices) {
            if (hidden[i]) continue
            val here = attrs.getOrElse(i) { RichTextCodec.NONE }
            if (here != current) {
                flush()
                current = here
            }
            text.append(plain[i])
        }
        flush()
        return result
    }

    private fun runOf(text: String, attrs: RichTextCodec.Attrs) = Run(
        text = text,
        bold = attrs.bold,
        italic = attrs.italic,
        underline = attrs.underline,
        strike = attrs.strikethrough,
        highlight = attrs.highlight,
        code = attrs.code,
        color = attrs.color,
        sizePx = attrs.sizePx,
        heading = attrs.heading,
    )

    fun opensFence(trimmed: String): String? = when {
        trimmed.startsWith("```") -> "```"
        trimmed == "$$" -> "$$"
        else -> null
    }

    fun closesFence(trimmed: String, fence: String): Boolean =
        if (fence == "```") trimmed.startsWith("```") else trimmed == "$$"

    /**
     * Zwinięta gałąź chowa dzieci - ten sam rachunek co [MindMapLayout.visible].
     */
    fun visibleIds(map: MindMapContent): Set<String> {
        val byId = map.nodes.associateBy { it.id }
        val children = HashMap<String, MutableList<String>>()
        val hasParent = HashSet<String>()
        for (edge in map.edges) {
            if (edge.fromId !in byId || edge.toId !in byId) continue
            if (edge.toId in hasParent) continue
            children.getOrPut(edge.fromId) { mutableListOf() } += edge.toId
            hasParent += edge.toId
        }

        val visible = HashSet<String>()
        fun descend(id: String) {
            if (!visible.add(id)) return
            val node = byId[id] ?: return
            if (node.collapsed) return
            children[id].orEmpty().forEach { descend(it) }
        }
        map.nodes.filter { it.id !in hasParent }.forEach { descend(it.id) }
        map.nodes.forEach { node ->
            if (node.id !in visible && !hiddenUnderCollapsed(node.id, byId, children, hasParent)) {
                visible += node.id
            }
        }
        return visible
    }

    private fun hiddenUnderCollapsed(
        id: String,
        byId: Map<String, MindNode>,
        children: Map<String, List<String>>,
        hasParent: Set<String>,
    ): Boolean {
        if (id !in hasParent) return false
        val parent = children.entries.firstOrNull { id in it.value }?.key ?: return false
        val parentNode = byId[parent] ?: return false
        return parentNode.collapsed || hiddenUnderCollapsed(parent, byId, children, hasParent)
    }

    fun visibleNodes(map: MindMapContent): List<MindNode> {
        val ids = visibleIds(map)
        return map.nodes.filter { it.id in ids }
    }

    fun cornerRadius(node: MindNode): Float =
        if (node.shape == NodeShape.OVAL) min(node.width, node.height) / 2f else 3f

    fun nodeInk(node: MindNode): Int {
        val raw = if (node.customColor != 0) {
            node.customColor
        } else {
            FolderColor.fromId(node.colorId).color(isDark = false).toArgb()
        }
        return PaperInk.ink(raw)
    }

    fun nodeTextInk(node: MindNode): Int =
        if (node.textColor != 0) PaperInk.ink(node.textColor) else PaperInk.INK

    fun attachmentBytes(attachment: (String) -> ByteArray?, asset: String): ByteArray? =
        attachment(asset) ?: attachment("assets/$asset")
}
