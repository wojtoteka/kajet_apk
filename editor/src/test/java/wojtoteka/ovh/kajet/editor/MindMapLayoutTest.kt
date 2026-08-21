package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.editor.mindmap.MindMapLayout
import wojtoteka.ovh.kajet.editor.mindmap.MindMapSizes
import kotlin.math.abs
import kotlin.math.hypot

class MindMapLayoutTest {

    private fun map(): MindMapContent = MindMapContent(
        nodes = listOf(
            MindNode(id = "korzen", x = 500f, y = 500f, text = "Ruch"),
            MindNode(id = "a", x = 0f, y = 0f, text = "Prędkość"),
            MindNode(id = "b", x = 0f, y = 0f, text = "Przyspieszenie"),
            MindNode(id = "a1", x = 0f, y = 0f, text = "Średnia"),
        ),
        edges = listOf(
            MindEdge("e1", "korzen", "a"),
            MindEdge("e2", "korzen", "b"),
            MindEdge("e3", "a", "a1"),
        ),
    )

    private fun middle(node: MindNode): Pair<Float, Float> =
        (node.x + node.width / 2f) to (node.y + node.height / 2f)

    /** Pary wezlow, ktore na siebie zachodza. Pusta lista znaczy „mapa czytelna". */
    private fun overlaps(nodes: List<MindNode>): List<String> {
        val bad = mutableListOf<String>()
        for (a in nodes.indices) {
            for (b in a + 1 until nodes.size) {
                val one = nodes[a]
                val two = nodes[b]
                val apart = one.x + one.width <= two.x ||
                    two.x + two.width <= one.x ||
                    one.y + one.height <= two.y ||
                    two.y + two.height <= one.y
                if (!apart) bad += "${one.id} x ${two.id}"
            }
        }
        return bad
    }

    @Test
    fun `temat glowny stoi w srodku, galezie dookola niego`() {
        val arranged = MindMapLayout.arrange(map())
        val byId = arranged.nodes.associateBy { it.id }
        val center = middle(byId.getValue("korzen"))
        val a = middle(byId.getValue("a"))
        val b = middle(byId.getValue("b"))

        // Dwie galezie rozchodza sie na boki, a nie jedna pod druga.
        assertThat(a.first).isGreaterThan(center.first)
        assertThat(b.first).isLessThan(center.first)
    }

    @Test
    fun `poziom to pierscien - rodzenstwo jest tak samo daleko od srodka`() {
        val arranged = MindMapLayout.arrange(map())
        val byId = arranged.nodes.associateBy { it.id }
        val center = middle(byId.getValue("korzen"))
        fun distance(id: String): Float {
            val at = middle(byId.getValue(id))
            return hypot(at.first - center.first, at.second - center.second)
        }

        assertThat(abs(distance("a") - distance("b"))).isAtMost(1f)
        // Wnuk siedzi dalej niz jego rodzic - inaczej lezalby na galezi.
        assertThat(distance("a1")).isGreaterThan(distance("a"))
    }

    @Test
    fun `zadne dwa wezly na siebie nie zachodza`() {
        assertThat(overlaps(MindMapLayout.arrange(map()).nodes)).isEmpty()
    }

    @Test
    fun `zwiniety wezel chowa swoje dzieci`() {
        val withCollapsed = map().let { old ->
            old.copy(nodes = old.nodes.map { if (it.id == "a") it.copy(collapsed = true) else it })
        }
        val visible = MindMapLayout.visible(withCollapsed)
        assertThat(visible).contains("a")
        assertThat(visible).doesNotContain("a1")
        assertThat(visible).contains("b")
    }

    @Test
    fun `bez zwijania widac wszystko`() {
        assertThat(MindMapLayout.visible(map())).hasSize(4)
    }

    @Test
    fun `pusta mapa nie wywala ukladania`() {
        assertThat(MindMapLayout.arrange(MindMapContent()).nodes).isEmpty()
    }

    @Test
    fun `wezel bez dzieci nie dostaje strzalki zwijania`() {
        assertThat(MindMapLayout.hasChildren(map(), "b")).isFalse()
        assertThat(MindMapLayout.hasChildren(map(), "korzen")).isTrue()
    }

    @Test
    fun `wysokosc wiersza maleje razem z przyblizeniem`() {
        val font = MindMapSizes.DEFAULT_FONT_SIZE
        fun linePx(zoom: Float) = font * zoom * MindMapSizes.LINE_RATIO

        assertThat(MindMapSizes.LINE_RATIO).isEqualTo(1.3f)
        assertThat(linePx(1f)).isEqualTo(font * 1.3f)
        assertThat(linePx(0.77f)).isWithin(0.001f).of(linePx(1f) * 0.77f)
        // Stale 24.sp z kroju body nie maleje z zoomem - przy 77% byloby
        // wyzsze niz sam glif i spychalo haslo na dol wezla.
        assertThat(linePx(0.77f)).isLessThan(24f)
        assertThat(linePx(0.25f)).isLessThan(linePx(0.77f))
    }

    @Test
    fun `dluzsze haslo dostaje wiekszy wezel, zeby nie zostalo uciete`() {
        val (width, height) = MindMapSizes.fit("Koalicja polsko-litewska")
        assertThat(width).isGreaterThan(160f)
        assertThat(width).isAtMost(280f)
        assertThat(height).isAtLeast(64f)
    }

    @Test
    fun `dlugi napis wersalikami dostaje wiecej wysokosci niz malymi literami`() {
        val small = MindMapSizes.fit("a".repeat(120))
        val caps = MindMapSizes.fit("A".repeat(120))
        assertThat(caps.second).isGreaterThan(small.second)
    }

    @Test
    fun `wezel tylko rosnie - recznie rozciagniety zostaje taki, jaki jest`() {
        val wide = MindNode(id = "x", x = 0f, y = 0f, width = 400f, height = 300f, text = "Ruch")
        assertThat(MindMapSizes.grown(wide)).isEqualTo(400f to 300f)
    }
}
