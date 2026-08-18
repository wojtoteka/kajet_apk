package wojtoteka.ovh.kajet.export

import androidx.compose.ui.graphics.toArgb
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.core.model.TextMarkers
import kotlin.math.min

/**
 * To, czego PDF nie powinien zgadywać sam: przebiegi formatu, zdjęcia
 * z `assets/` i które węzły mapy widać. [PdfExport] tylko maluje wynik.
 *
 * Format fragmentu idzie tą samą drogą co [DocxExport.formattedParagraph]:
 * znaczniki z [RichText] (`**`, `*`, `==`, `` ` ``, `<u>`, barwa, rozmiar).
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
    )

    data class ImageRef(
        val asset: String,
        val width: Float = 1f,
    )

    // URL leniwy jak w edytorze: nazwa pliku potrafi mieć spację albo nawias.
    private val imageLine =
        Regex("""^\s*!\[([^\]]*)]\((.+?)(?:\s+"([^"]*)")?\)\s*$""")
    private val percentTitle = Regex("""^(\d{1,3})%$""")

    /**
     * Ten sam porządek co w [DocxExport.formattedParagraph], plus rozmiar
     * pisma, którego Word jeszcze nie maluje.
     */
    private val decorationPattern = Regex(
        """<span style="color:[^"]*">[^<]*</span>""" +
            """|<span style="font-size:[^"]*px">[^<]*</span>""" +
            """|<u>[^<]*</u>""" +
            """|\*\*[^*]+\*\*""" +
            """|~~[^~]+~~""" +
            """|==[^=]+==""" +
            """|`[^`]+`""" +
            """|\*[^*]+\*""",
    )

    fun image(line: String): ImageRef? {
        val match = imageLine.find(line) ?: return null
        val url = match.groupValues[2].trim()
        if (!url.startsWith("assets/")) return null
        val title = match.groupValues.getOrNull(3).orEmpty()
        val width = percentTitle.find(title.trim())?.groupValues?.get(1)?.toIntOrNull()
            ?.let { (it / 100f).coerceIn(0.1f, 1f) }
            ?: 1f
        return ImageRef(url.removePrefix("assets/"), width)
    }

    fun runs(markdown: String): List<Run> {
        if (markdown.isEmpty()) return emptyList()
        val result = ArrayList<Run>()
        var position = 0
        for (match in decorationPattern.findAll(markdown)) {
            if (match.range.first > position) {
                val plain = TextMarkers.plain(markdown.substring(position, match.range.first))
                if (plain.isNotEmpty()) result += Run(plain)
            }
            result += runOf(match.value)
            position = match.range.last + 1
        }
        if (position < markdown.length) {
            val plain = TextMarkers.plain(markdown.substring(position))
            if (plain.isNotEmpty()) result += Run(plain)
        }
        return result
    }

    private fun runOf(piece: String): Run = when {
        piece.startsWith("**") -> Run(piece.removeSurrounding("**"), bold = true)
        piece.startsWith("~~") -> Run(piece.removeSurrounding("~~"), strike = true)
        piece.startsWith("==") -> Run(piece.removeSurrounding("=="), highlight = true)
        piece.startsWith("`") -> Run(piece.trim('`'), code = true)
        piece.startsWith("<u>") -> Run(piece.removeSurrounding("<u>", "</u>"), underline = true)
        piece.startsWith("<span") && "font-size:" in piece -> {
            val inner = TextMarkers.sizePattern.find(piece)
            Run(
                text = inner?.groupValues?.get(2).orEmpty(),
                sizePx = inner?.groupValues?.get(1)?.toFloatOrNull(),
            )
        }
        piece.startsWith("<span") -> {
            val inner = TextMarkers.colorPattern.find(piece)
            val hex = Regex("""color:\s*(#[0-9a-fA-F]{6,8})""").find(piece)?.groupValues?.get(1)
            Run(inner?.groupValues?.get(1).orEmpty(), color = hex?.let { TextMarkers.colorFromHex(it) })
        }
        else -> Run(piece.trim('*'), italic = true)
    }

    fun opensFence(trimmed: String): String? = when {
        trimmed.startsWith("```") -> "```"
        trimmed == "$$" -> "$$"
        else -> null
    }

    fun closesFence(trimmed: String, fence: String): Boolean =
        if (fence == "```") trimmed.startsWith("```") else trimmed == "$$"

    /**
     * Zwinięta gałąź chowa dzieci — ten sam rachunek co [MindMapLayout.visible].
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
