package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.editor.mapa.UkladMapy

class UkladMapyTest {

    private fun mapa(): MindMapContent = MindMapContent(
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
        val ulozona = UkladMapy.rozloz(mapa())
        val poId = ulozona.nodes.associateBy { it.id }
        assertThat(poId.getValue("korzen").x).isEqualTo(0f)
        assertThat(poId.getValue("a").x).isGreaterThan(poId.getValue("korzen").x)
        assertThat(poId.getValue("a1").x).isGreaterThan(poId.getValue("a").x)
    }

    @Test
    fun `rodzenstwo nie nachodzi na siebie`() {
        val ulozona = UkladMapy.rozloz(mapa())
        val poId = ulozona.nodes.associateBy { it.id }
        val a = poId.getValue("a")
        val b = poId.getValue("b")
        assertThat(b.y).isAtLeast(a.y + a.height)
    }

    @Test
    fun `zwiniety wezel chowa swoje dzieci`() {
        val zeZwinieta = mapa().let { stara ->
            stara.copy(nodes = stara.nodes.map { if (it.id == "a") it.copy(collapsed = true) else it })
        }
        val widoczne = UkladMapy.widoczne(zeZwinieta)
        assertThat(widoczne).contains("a")
        assertThat(widoczne).doesNotContain("a1")
        assertThat(widoczne).contains("b")
    }

    @Test
    fun `bez zwijania widac wszystko`() {
        assertThat(UkladMapy.widoczne(mapa())).hasSize(4)
    }

    @Test
    fun `pusta mapa nie wywala ukladania`() {
        assertThat(UkladMapy.rozloz(MindMapContent()).nodes).isEmpty()
    }

    @Test
    fun `wezel bez dzieci nie dostaje strzalki zwijania`() {
        assertThat(UkladMapy.maDzieci(mapa(), "b")).isFalse()
        assertThat(UkladMapy.maDzieci(mapa(), "korzen")).isTrue()
    }
}
