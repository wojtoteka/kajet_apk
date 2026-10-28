package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageEdgeTest {

    private val width = 600f
    private val height = 800f

    @Test
    fun `punkt na kartce i na samym brzegu jest na kartce`() {
        assertThat(PageEdge.inside(300f, 400f, width, height)).isTrue()
        assertThat(PageEdge.inside(0f, 0f, width, height)).isTrue()
        assertThat(PageEdge.inside(width, height, width, height)).isTrue()
    }

    @Test
    fun `biurko obok kartki nie jest kartka`() {
        // Po bokach, nad kartką i pod nią - tam, gdzie pisało się niewidocznie.
        assertThat(PageEdge.inside(-1f, 400f, width, height)).isFalse()
        assertThat(PageEdge.inside(width + 1f, 400f, width, height)).isFalse()
        assertThat(PageEdge.inside(300f, -1f, width, height)).isFalse()
        assertThat(PageEdge.inside(300f, height + 1f, width, height)).isFalse()
    }

    @Test
    fun `wyjazd w prawo konczy kreske na prawym brzegu`() {
        val t = PageEdge.exit(500f, 400f, 700f, 400f, width, height)
        assertThat(t).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `wyjazd w lewo konczy kreske na lewym brzegu`() {
        val t = PageEdge.exit(20f, 100f, -60f, 100f, width, height)
        assertThat(t).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `wyjazd w dol i w gore konczy kreske na dolnym i gornym brzegu`() {
        assertThat(PageEdge.exit(300f, 780f, 300f, 820f, width, height)).isWithin(1e-4f).of(0.5f)
        assertThat(PageEdge.exit(300f, 30f, 300f, -10f, width, height)).isWithin(1e-4f).of(0.75f)
    }

    @Test
    fun `wyjazd przez rog liczy brzeg spotkany pierwszy`() {
        // Prawy brzeg w połowie drogi, dolny dopiero w trzech czwartych.
        val t = PageEdge.exit(590f, 770f, 610f, 810f, width, height)
        assertThat(t).isWithin(1e-4f).of(0.5f)
        val x = 590f + (610f - 590f) * t
        val y = 770f + (810f - 770f) * t
        assertThat(PageEdge.inside(x, y, width, height)).isTrue()
        assertThat(x).isWithin(1e-3f).of(width)
    }

    @Test
    fun `punkt na samym brzegu wychodzi od razu`() {
        assertThat(PageEdge.exit(width, 400f, width + 30f, 400f, width, height)).isEqualTo(0f)
        assertThat(PageEdge.exit(300f, 0f, 300f, -30f, width, height)).isEqualTo(0f)
    }

    @Test
    fun `kartka urosla pod rysikiem - brzegu po drodze nie ma`() {
        assertThat(PageEdge.exit(300f, 700f, 310f, 790f, width, height)).isEqualTo(1f)
    }
}
