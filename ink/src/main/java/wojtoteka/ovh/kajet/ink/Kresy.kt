package wojtoteka.ovh.kajet.ink

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.InputKind
import wojtoteka.ovh.kajet.core.model.Rect
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Zamiana kresek między postacią zapisaną w pliku a postacią, którą rysuje silnik.
 *
 * W pliku leżą surowe punkty z rysika, bo tylko z nich da się odtworzyć kreskę
 * w pełnej jakości na innym ekranie i w innej wielkości. Silnik potrzebuje
 * z nich zbudować siatkę, więc robimy to przy wczytaniu notatki.
 */
object Kresy {

    /** O tyle miejsc po przecinku skracamy liczby przy zapisie, żeby plik nie puchł. */
    private const val MIEJSCA_WSPOLRZEDNE = 2
    private const val MIEJSCA_NACISK = 3

    fun doSilnika(kreska: InkStroke): Stroke {
        val partia = MutableStrokeInputBatch()
        val typ = when (kreska.input) {
            InputKind.RYSIK -> InputToolType.STYLUS
            InputKind.PALEC -> InputToolType.TOUCH
            InputKind.MYSZ -> InputToolType.MOUSE
        }
        for (i in 0 until kreska.pointCount) {
            partia.add(
                typ,
                kreska.x(i),
                kreska.y(i),
                kreska.timeMs(i).toLong(),
                kreska.pressure(i),
                kreska.tilt(i),
                kreska.orientation(i),
            )
        }
        val pedzel = Pedzle.dla(kreska.tool, kreska.color, kreska.size, kreska.epsilon)
        return Stroke(pedzel, partia.toImmutable())
    }

    fun doModelu(kreska: Stroke, narzedzie: InkTool, id: String = UUID.randomUUID().toString()): InkStroke {
        val wejscia = kreska.inputs
        val punkty = ArrayList<Float>(wejscia.size * InkStroke.WARTOSCI_NA_PUNKT)
        val bufor = StrokeInput()
        var typWejscia = InputToolType.STYLUS
        for (i in 0 until wejscia.size) {
            wejscia.populate(i, bufor)
            if (i == 0) typWejscia = bufor.toolType
            punkty += zaokraglij(bufor.x, MIEJSCA_WSPOLRZEDNE)
            punkty += zaokraglij(bufor.y, MIEJSCA_WSPOLRZEDNE)
            punkty += bufor.elapsedTimeMillis.toFloat()
            punkty += if (bufor.hasPressure) zaokraglij(bufor.pressure, MIEJSCA_NACISK) else InkStroke.BRAK
            punkty += if (bufor.hasTilt) zaokraglij(bufor.tiltRadians, MIEJSCA_NACISK) else InkStroke.BRAK
            punkty += if (bufor.hasOrientation) {
                zaokraglij(bufor.orientationRadians, MIEJSCA_NACISK)
            } else {
                InkStroke.BRAK
            }
        }
        return InkStroke(
            id = id,
            tool = narzedzie,
            color = kreska.brush.colorIntArgb,
            size = kreska.brush.size,
            epsilon = kreska.brush.epsilon,
            input = when (typWejscia) {
                InputToolType.STYLUS -> InputKind.RYSIK
                InputToolType.MOUSE -> InputKind.MYSZ
                else -> InputKind.PALEC
            },
            points = punkty,
        )
    }

    private fun zaokraglij(wartosc: Float, miejsca: Int): Float {
        if (wartosc == InkStroke.BRAK) return wartosc
        var mnoznik = 1f
        repeat(miejsca) { mnoznik *= 10f }
        return kotlin.math.round(wartosc * mnoznik) / mnoznik
    }

    // Gumka i trafianie w kreskę

    /**
     * Czy kreska przechodzi przez kółko gumki.
     * Sprawdzamy odcinki, a nie same punkty, bo przy szybkim piśmie
     * punkty leżą daleko od siebie i gumka trafiałaby w puste miejsca.
     */
    fun dotykaKola(kreska: InkStroke, srodekX: Float, srodekY: Float, promien: Float): Boolean {
        val zasieg = promien + kreska.size / 2f
        val obszar = kreska.bounds().expanded(zasieg)
        if (!obszar.contains(srodekX, srodekY)) return false

        val liczba = kreska.pointCount
        if (liczba == 0) return false
        if (liczba == 1) {
            return hypot(kreska.x(0) - srodekX, kreska.y(0) - srodekY) <= zasieg
        }
        for (i in 0 until liczba - 1) {
            val odleglosc = odlegloscOdOdcinka(
                srodekX, srodekY,
                kreska.x(i), kreska.y(i),
                kreska.x(i + 1), kreska.y(i + 1),
            )
            if (odleglosc <= zasieg) return true
        }
        return false
    }

    /**
     * Wycina z kreski fragment, który wpadł pod gumkę.
     *
     * Zwraca kawałki, które zostały. Pusta lista oznacza, że cała kreska znika.
     * Kawałki krótsze niż dwa punkty odrzucamy, bo nic by z nich nie było widać.
     */
    fun wytnijFragment(
        kreska: InkStroke,
        srodekX: Float,
        srodekY: Float,
        promien: Float,
    ): List<InkStroke> {
        val zasieg = promien + kreska.size / 2f
        val liczba = kreska.pointCount
        if (liczba == 0) return emptyList()

        val zostaje = BooleanArray(liczba) { i ->
            hypot(kreska.x(i) - srodekX, kreska.y(i) - srodekY) > zasieg
        }
        if (zostaje.all { it }) return listOf(kreska)
        if (zostaje.none { it }) return emptyList()

        val kawalki = mutableListOf<InkStroke>()
        var poczatek = -1
        for (i in 0 until liczba) {
            if (zostaje[i]) {
                if (poczatek < 0) poczatek = i
            } else if (poczatek >= 0) {
                dodajKawalek(kreska, poczatek, i - 1, kawalki)
                poczatek = -1
            }
        }
        if (poczatek >= 0) dodajKawalek(kreska, poczatek, liczba - 1, kawalki)
        return kawalki
    }

    private fun dodajKawalek(zrodlo: InkStroke, od: Int, doIndeksu: Int, wynik: MutableList<InkStroke>) {
        if (doIndeksu - od < 1) return
        val punkty = ArrayList<Float>((doIndeksu - od + 1) * InkStroke.WARTOSCI_NA_PUNKT)
        val poczatekCzasu = zrodlo.timeMs(od)
        for (i in od..doIndeksu) {
            val baza = i * InkStroke.WARTOSCI_NA_PUNKT
            punkty += zrodlo.points[baza]
            punkty += zrodlo.points[baza + 1]
            punkty += zrodlo.points[baza + 2] - poczatekCzasu
            punkty += zrodlo.points[baza + 3]
            punkty += zrodlo.points[baza + 4]
            punkty += zrodlo.points[baza + 5]
        }
        wynik += zrodlo.copy(id = UUID.randomUUID().toString(), points = punkty)
    }

    fun odlegloscOdOdcinka(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val dlugoscKwadrat = dx * dx + dy * dy
        if (dlugoscKwadrat < 1e-6f) return hypot(px - ax, py - ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / dlugoscKwadrat
        t = t.coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    // Lasso

    /**
     * Czy kreska mieści się w narysowanym lassie.
     *
     * Bierzemy kreskę w całości, kiedy większość jej punktów wpadła do środka.
     * Dzięki temu zaznaczenie działa tak, jak człowiek się tego spodziewa,
     * i nie tnie liter na pół.
     */
    fun wLassie(kreska: InkStroke, wielokat: List<Float>, prog: Float = 0.6f): Boolean {
        val liczba = kreska.pointCount
        if (liczba == 0 || wielokat.size < 6) return false
        var wewnatrz = 0
        for (i in 0 until liczba) {
            if (punktWWielokacie(kreska.x(i), kreska.y(i), wielokat)) wewnatrz++
        }
        return wewnatrz.toFloat() / liczba >= prog
    }

    /** Sprawdzenie promieniem poziomym. Wielokąt to spłaszczona lista par x, y. */
    fun punktWWielokacie(px: Float, py: Float, wielokat: List<Float>): Boolean {
        var wynik = false
        val liczba = wielokat.size / 2
        var j = liczba - 1
        for (i in 0 until liczba) {
            val xi = wielokat[i * 2]
            val yi = wielokat[i * 2 + 1]
            val xj = wielokat[j * 2]
            val yj = wielokat[j * 2 + 1]
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                wynik = !wynik
            }
            j = i
        }
        return wynik
    }

    fun obszar(kreski: List<InkStroke>): Rect? {
        if (kreski.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var puste = true
        for (kreska in kreski) {
            for (i in 0 until kreska.pointCount) {
                puste = false
                minX = min(minX, kreska.x(i))
                maxX = max(maxX, kreska.x(i))
                minY = min(minY, kreska.y(i))
                maxY = max(maxY, kreska.y(i))
            }
        }
        return if (puste) null else Rect(minX, minY, maxX, maxY)
    }

    // Linijka

    /**
     * Prostuje kreskę do odcinka między pierwszym a ostatnim punktem.
     *
     * Kiedy odcinek jest prawie poziomy, prawie pionowy albo prawie pod kątem 45 stopni,
     * dociągamy go do pełnego kąta. Ludzka ręka nie trafia w idealną poziomą,
     * a przy rysowaniu tabelki to widać.
     */
    fun wyprostuj(kreska: InkStroke, tolerancjaStopni: Float = 4f): InkStroke {
        val liczba = kreska.pointCount
        if (liczba < 2) return kreska

        val x1 = kreska.x(0)
        val y1 = kreska.y(0)
        var x2 = kreska.x(liczba - 1)
        var y2 = kreska.y(liczba - 1)

        val dx = x2 - x1
        val dy = y2 - y1
        val dlugosc = hypot(dx, dy)
        if (dlugosc < 1f) return kreska

        val kat = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val katSiatki = kotlin.math.round(kat / 45f) * 45f
        if (abs(kat - katSiatki) <= tolerancjaStopni) {
            val radiany = Math.toRadians(katSiatki.toDouble())
            x2 = x1 + (dlugosc * kotlin.math.cos(radiany)).toFloat()
            y2 = y1 + (dlugosc * kotlin.math.sin(radiany)).toFloat()
        }

        // Nacisk bierzemy z pierwszego i ostatniego punktu, żeby kreska
        // zaczynała się i kończyła tak samo jak ta narysowana ręką.
        val punkty = ArrayList<Float>(2 * InkStroke.WARTOSCI_NA_PUNKT)
        val kroki = 24
        for (i in 0..kroki) {
            val t = i / kroki.toFloat()
            punkty += x1 + (x2 - x1) * t
            punkty += y1 + (y2 - y1) * t
            punkty += kreska.timeMs(0) + (kreska.timeMs(liczba - 1) - kreska.timeMs(0)) * t
            punkty += mieszaj(kreska.pressure(0), kreska.pressure(liczba - 1), t)
            punkty += mieszaj(kreska.tilt(0), kreska.tilt(liczba - 1), t)
            punkty += mieszaj(kreska.orientation(0), kreska.orientation(liczba - 1), t)
        }
        return kreska.copy(points = punkty)
    }

    private fun mieszaj(a: Float, b: Float, t: Float): Float {
        if (a == InkStroke.BRAK || b == InkStroke.BRAK) return InkStroke.BRAK
        return a + (b - a) * t
    }
}
