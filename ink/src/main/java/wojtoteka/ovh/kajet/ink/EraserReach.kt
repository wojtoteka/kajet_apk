package wojtoteka.ovh.kajet.ink

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Gumka zależna od szybkości: im szybciej ręka maże, tym szerzej wyciera,
 * a kiedy zwalnia, gumka wraca do wybranej wielkości.
 *
 * Szybkość liczy się na ekranie (dp na milisekundę), nie na kartce - przy
 * przybliżeniu ręka rusza się tak samo, więc i gumka ma rosnąć tak samo.
 * Wygładzenie nie pozwala jej drgać od pojedynczych szarpnięć: rośnie
 * szybko, maleje wolniej, żeby przy zawracaniu nie kurczyła się w pół ruchu.
 */
class EraserReach(private val density: Float) {

    // Wygładzona szybkość w dp na milisekundę.
    private var speed = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = NO_TIME

    /** Początek pociągnięcia: gumka zaczyna od swojej wielkości. */
    fun start(x: Float, y: Float, timeMs: Long) {
        speed = 0f
        lastX = x
        lastY = y
        lastTime = timeMs
    }

    /** Kolejny punkt ruchu w pikselach widoku. */
    fun move(x: Float, y: Float, timeMs: Long) {
        if (lastTime == NO_TIME) {
            start(x, y, timeMs)
            return
        }
        // Punkty z tą samą chwilą nic nie mówią o szybkości. Droga zbiera
        // się do następnego punktu, który przyjdzie później.
        val elapsed = timeMs - lastTime
        if (elapsed <= 0L) return

        val instant = hypot(x - lastX, y - lastY) / density / elapsed
        val settle = if (instant > speed) GROW_MS else SHRINK_MS
        speed += (instant - speed) * (1f - exp(-elapsed / settle))
        lastX = x
        lastY = y
        lastTime = timeMs
    }

    /**
     * Mnożnik wielkości gumki w chwili [timeMs]. Rysik, który stanął i nie
     * przysyła ruchu, też ma odzyskać zwykłą gumkę, więc szybkość gaśnie
     * z czasem od ostatniego punktu.
     */
    fun scaleAt(timeMs: Long): Float {
        if (lastTime == NO_TIME) return 1f
        val idle = (timeMs - lastTime).coerceAtLeast(0L)
        return scaleFor(speed * exp(-idle / SHRINK_MS))
    }

    /** Wygasza pomiar - następne pociągnięcie zacznie od zera. */
    fun reset() {
        speed = 0f
        lastTime = NO_TIME
    }

    companion object {
        /** Do tej szybkości gumka ma swoją wielkość - spokojne, dokładne wycieranie. */
        const val CALM_SPEED = 0.3f

        /** O ile rośnie mnożnik na każdy dp/ms ponad spokojną szybkość. */
        const val GROWTH = 1.2f

        const val MAX_SCALE = 3.5f

        /** Największy promień, do jakiego gumka urośnie sama (wybrana większa zostaje). */
        const val MAX_RADIUS = 96f

        private const val GROW_MS = 40f
        private const val SHRINK_MS = 140f
        private const val NO_TIME = Long.MIN_VALUE

        internal fun scaleFor(speed: Float): Float =
            (1f + GROWTH * (speed - CALM_SPEED)).coerceIn(1f, MAX_SCALE)

        /** Promień po powiększeniu - nigdy mniejszy od wybranego. */
        fun radius(chosen: Float, scale: Float): Float =
            min(chosen * scale, max(chosen, MAX_RADIUS))
    }
}
