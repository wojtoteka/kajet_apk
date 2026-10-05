package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.editor.mindmap.MindMapLayout
import wojtoteka.ovh.kajet.editor.mindmap.AiFit
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
        assertThat(width).isAtMost(MindMapSizes.MAX_WIDTH)
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

    /*
      Te same liczby co w testach serwera (mindmap-layout.test.ts) - oba
      rachunki muszą dawać to samo, inaczej mapa przeskakuje po synchronizacji.
    */
    @Test
    fun `zwykle zdanie od KajetAI miesci sie w jednym, najwyzej dwoch wierszach`() {
        assertThat(MindMapSizes.fit("Bitwa pod Grunwaldem w 1410 roku")).isEqualTo(300f to 64f)
        assertThat(MindMapSizes.fit("Unia w Krewie połączyła Polskę i Litwę osobą jednego władcy"))
            .isEqualTo(260f to 64f)
    }

    @Test
    fun `lamane haslo dostaje wyrownana szerokosc, nie pelna granice`() {
        val (width, _) = MindMapSizes.fit("Wzrost znaczenia Polski w Europie Środkowej po zwycięstwie")
        assertThat(width).isLessThan(MindMapSizes.MAX_WIDTH)
    }

    /** Udawane pismo o stałej szerokości znaku - do sprawdzenia samego rachunku. */
    private fun mono(text: String, sign: Float = 8f, line: Float = 19.5f): Pair<Float, Float> {
        val paragraphs = text.split("\n")
        val natural = paragraphs.maxOf { it.length } * sign
        return MindMapSizes.measured(natural, paragraphs.size) { usable ->
            var lines = 0
            for (paragraph in paragraphs) {
                var taken = 0f
                var count = 1
                for (word in paragraph.split(" ")) {
                    val w = word.length * sign
                    val next = if (taken > 0f) taken + sign + w else w
                    if (next > usable && taken > 0f) {
                        count += 1
                        taken = w
                    } else {
                        taken = next
                    }
                }
                lines += count
            }
            lines to lines * line
        }
    }

    @Test
    fun `zmierzone haslo w jednym wierszu dostaje szerokosc pod siebie`() {
        val (width, height) = mono("Krótkie zdanie na jeden wiersz")
        // 30 znaków po 8 + wyściółka 20 + zapas 4 = 264, w górę do dwudziestki.
        assertThat(width).isEqualTo(280f)
        assertThat(height).isEqualTo(MindMapSizes.DEFAULT_HEIGHT)
    }

    @Test
    fun `zmierzone dlugie haslo nie przekracza granicy i nie zostaje uciete`() {
        val text = "słowo ".repeat(40).trim()
        val (width, height) = mono(text)
        assertThat(width).isAtMost(MindMapSizes.MAX_WIDTH)
        // 240 znaków po 8 to co najmniej pięć wierszy po 19,5 - plus wyściółka.
        assertThat(height).isAtLeast(5 * 19.5f + MindMapSizes.PAD_Y)
    }

    @Test
    fun `zmierzone haslo lamane i tak dostaje wezsza, wyrownana szerokosc`() {
        // 60 znaków: przy granicy dwa wiersze, z czego drugi prawie pusty.
        val (width, _) = mono("a".repeat(29) + " " + "b".repeat(30))
        assertThat(width).isLessThan(MindMapSizes.MAX_WIDTH)
    }

    @Test
    fun `KajetAI - nowe i poprawione wezly ida do pomiaru, uklad tylko po zmianie budowy`() {
        val before = map()
        val retexted = before.copy(
            nodes = before.nodes.map { if (it.id == "a") it.copy(text = "Nowe, dłuższe hasło") else it },
        )
        val onlyText = AiFit.between(before, retexted)!!
        assertThat(onlyText.retexted).containsExactly("a")
        assertThat(onlyText.added).isEmpty()
        assertThat(onlyText.rearrange).isFalse()

        val grown = retexted.copy(
            nodes = retexted.nodes + MindNode(id = "nowy", x = 0f, y = 0f, text = "Nowy"),
            edges = retexted.edges + MindEdge("e-nowy", "a", "nowy"),
        )
        val withNode = AiFit.between(before, grown)!!
        assertThat(withNode.added).containsExactly("nowy")
        assertThat(withNode.rearrange).isTrue()

        assertThat(AiFit.between(before, before)).isNull()
    }
}
