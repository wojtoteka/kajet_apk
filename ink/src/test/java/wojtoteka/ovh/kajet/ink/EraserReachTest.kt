package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EraserReachTest {

    // Gęstość ekranu tabletu w zaokrągleniu: 2 piksele na dp.
    private val density = 2f

    /** Prowadzi gumkę w poziomie ze stałą szybkością (dp/ms), punkt co [stepMs]. */
    private fun EraserReach.drive(
        fromX: Float,
        fromTime: Long,
        speedDp: Float,
        durationMs: Long,
        stepMs: Long = 4L,
    ): Pair<Float, Long> {
        var x = fromX
        var time = fromTime
        while (time < fromTime + durationMs) {
            time += stepMs
            x += speedDp * density * stepMs
            move(x, 500f, time)
        }
        return x to time
    }

    @Test
    fun `spokojne wycieranie zostawia wybrana wielkosc`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (_, end) = reach.drive(0f, 0L, speedDp = 0.2f, durationMs = 400L)
        assertThat(reach.scaleAt(end)).isEqualTo(1f)
    }

    @Test
    fun `szybkie mazanie powieksza gumke`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (_, end) = reach.drive(0f, 0L, speedDp = 2f, durationMs = 300L)
        assertThat(reach.scaleAt(end)).isGreaterThan(2.5f)
    }

    @Test
    fun `im szybciej tym wieksza`() {
        val slower = EraserReach(density).apply { start(0f, 500f, 0L) }
        val faster = EraserReach(density).apply { start(0f, 500f, 0L) }
        val (_, end) = slower.drive(0f, 0L, speedDp = 1f, durationMs = 300L)
        faster.drive(0f, 0L, speedDp = 1.8f, durationMs = 300L)
        assertThat(faster.scaleAt(end)).isGreaterThan(slower.scaleAt(end))
    }

    @Test
    fun `po zwolnieniu gumka wraca do swojej wielkosci`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (x, fast) = reach.drive(0f, 0L, speedDp = 2.5f, durationMs = 300L)
        val big = reach.scaleAt(fast)
        val (_, slow) = reach.drive(x, fast, speedDp = 0.1f, durationMs = 800L)
        assertThat(big).isGreaterThan(2f)
        assertThat(reach.scaleAt(slow)).isEqualTo(1f)
    }

    @Test
    fun `rysik ktory stanal tez odzyskuje zwykla gumke`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (_, end) = reach.drive(0f, 0L, speedDp = 2.5f, durationMs = 300L)
        assertThat(reach.scaleAt(end)).isGreaterThan(2f)
        assertThat(reach.scaleAt(end + 1_000L)).isEqualTo(1f)
    }

    @Test
    fun `nie rosnie bez konca`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (_, end) = reach.drive(0f, 0L, speedDp = 50f, durationMs = 300L)
        assertThat(reach.scaleAt(end)).isEqualTo(EraserReach.MAX_SCALE)
    }

    @Test
    fun `punkty z ta sama chwila nie wysadzaja pomiaru`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 10L)
        reach.move(400f, 500f, 10L)
        reach.move(800f, 500f, 10L)
        assertThat(reach.scaleAt(10L)).isEqualTo(1f)
        // Droga zebrana w tej samej chwili liczy się dopiero z następnym czasem.
        reach.move(810f, 500f, 30L)
        assertThat(reach.scaleAt(30L)).isGreaterThan(1f)
    }

    @Test
    fun `nowe pociagniecie zaczyna od zwyklej gumki`() {
        val reach = EraserReach(density)
        reach.start(0f, 500f, 0L)
        val (_, end) = reach.drive(0f, 0L, speedDp = 2.5f, durationMs = 300L)
        reach.start(0f, 900f, end + 5L)
        assertThat(reach.scaleAt(end + 5L)).isEqualTo(1f)
    }

    @Test
    fun `promien nigdy nie spada ponizej wybranego ani nie rosnie ponad granice`() {
        assertThat(EraserReach.radius(12f, 1f)).isEqualTo(12f)
        assertThat(EraserReach.radius(12f, 3f)).isEqualTo(36f)
        // Mała gumka rośnie najwyżej do granicy...
        assertThat(EraserReach.radius(40f, 3.5f)).isEqualTo(EraserReach.MAX_RADIUS)
        // ...a wybrana większa od granicy zostaje taka, jaką ją ustawiono.
        assertThat(EraserReach.radius(120f, 3.5f)).isEqualTo(120f)
    }
}
