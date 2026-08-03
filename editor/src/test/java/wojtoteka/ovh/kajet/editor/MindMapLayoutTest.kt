package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.editor.mindmap.MindMapLayout

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

    @Test
    fun `korzen staje po lewej, dzieci po prawej`() {
        val arranged = MindMapLayout.arrange(map())
        val byId = arranged.nodes.associateBy { it.id }
        assertThat(byId.getValue("korzen").x).isEqualTo(0f)
        assertThat(byId.getValue("a").x).isGreaterThan(byId.getValue("korzen").x)
        assertThat(byId.getValue("a1").x).isGreaterThan(byId.getValue("a").x)
    }

    @Test
    fun `rodzenstwo nie nachodzi na siebie`() {
        val arranged = MindMapLayout.arrange(map())
        val byId = arranged.nodes.associateBy { it.id }
        val a = byId.getValue("a")
        val b = byId.getValue("b")
        assertThat(b.y).isAtLeast(a.y + a.height)
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
}
