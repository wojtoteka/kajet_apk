package wojtoteka.ovh.kajet.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke

class MeshCacheTest {

    private val stroke = InkStroke(
        id = "a",
        color = 0,
        size = 2f,
        points = listOf(10f, 20f, 0f, 1f, 0f, 0f, 30f, 40f, 5f, 1f, 0f, 0f),
    )

    private var builds = 0
    private val cache = MeshCache<String>()

    private fun mesh(of: InkStroke): String? = cache.meshFor(of) {
        builds++
        "x=${it.x(0)}"
    }

    @Test
    fun `ta sama kreska nie buduje sie drugi raz`() {
        mesh(stroke)
        mesh(stroke)
        assertEquals(1, builds)
    }

    @Test
    fun `kreska z tymi samymi punktami w nowym obiekcie nie buduje sie drugi raz`() {
        mesh(stroke)
        mesh(stroke.copy(points = stroke.points.toList()))
        assertEquals(1, builds)
    }

    @Test
    fun `przesunieta kreska z tym samym id rysuje sie w nowym miejscu`() {
        assertEquals("x=10.0", mesh(stroke))
        assertEquals("x=15.0", mesh(stroke.translated(5f, 0f)))
    }

    @Test
    fun `cofniete przesuniecie wraca na stare miejsce`() {
        mesh(stroke)
        mesh(stroke.translated(5f, 0f))
        assertEquals("x=10.0", mesh(stroke))
    }

    @Test
    fun `zmiana koloru buduje kreske od nowa`() {
        mesh(stroke)
        mesh(stroke.copy(color = 1))
        assertEquals(2, builds)
    }

    @Test
    fun `kreska ktorej nie da sie zbudowac nie zostawia starej wersji`() {
        mesh(stroke)
        assertNull(cache.meshFor(stroke.translated(5f, 0f)) { null })
        mesh(stroke)
        assertEquals(2, builds)
    }

    @Test
    fun `zapomina kreski spoza spisu`() {
        val other = stroke.copy(id = "b")
        mesh(stroke)
        mesh(other)
        cache.retain(setOf("a"))
        mesh(other)
        assertEquals(3, builds)
    }
}
