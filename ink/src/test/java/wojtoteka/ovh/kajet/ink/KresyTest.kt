package wojtoteka.ovh.kajet.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke

/**
 * Gumka, lasso i linijka liczone bez urządzenia.
 * To jest czysta geometria, więc da się ją sprawdzić na komputerze.
 */
class KresyTest {

    /** Kreska pozioma od x = 0 do x = 100 na wysokości y = 50. */
    private fun pozioma(krok: Float = 10f, size: Float = 2f) = InkStroke(
        id = "k",
        color = 0xFF000000.toInt(),
        size = size,
        points = buildList {
            var x = 0f
            var czas = 0f
            while (x <= 100f) {
                add(x); add(50f); add(czas); add(0.5f); add(0.4f); add(1f)
                x += krok
                czas += 8f
            }
        },
    )

    @Test
    fun `gumka trafia w kreske, po ktorej przejedzie`() {
        assertThat(Kresy.dotykaKola(pozioma(), 50f, 50f, 5f)).isTrue()
    }

    @Test
    fun `gumka nie trafia w kreske obok`() {
        assertThat(Kresy.dotykaKola(pozioma(), 50f, 90f, 5f)).isFalse()
    }

    @Test
    fun `gumka trafia miedzy punktami, a nie tylko w punkty`() {
        // Punkty leza co 40, gumka jest dokladnie miedzy nimi.
        val rzadka = pozioma(krok = 40f)
        assertThat(Kresy.dotykaKola(rzadka, 20f, 50f, 3f)).isTrue()
    }

    @Test
    fun `wytarcie srodka dzieli kreske na dwa kawalki`() {
        val kawalki = Kresy.wytnijFragment(pozioma(), 50f, 50f, 12f)
        assertThat(kawalki).hasSize(2)
        assertThat(kawalki[0].x(0)).isEqualTo(0f)
        assertThat(kawalki[0].bounds().right).isLessThan(50f)
        assertThat(kawalki[1].bounds().left).isGreaterThan(50f)
    }

    @Test
    fun `kawalki po wytarciu maja wlasne identyfikatory`() {
        val kawalki = Kresy.wytnijFragment(pozioma(), 50f, 50f, 12f)
        assertThat(kawalki[0].id).isNotEqualTo(kawalki[1].id)
        assertThat(kawalki[0].id).isNotEqualTo("k")
    }

    @Test
    fun `wytarcie poza kreska nie zmienia niczego`() {
        val kawalki = Kresy.wytnijFragment(pozioma(), 300f, 300f, 12f)
        assertThat(kawalki).hasSize(1)
        assertThat(kawalki[0].id).isEqualTo("k")
    }

    @Test
    fun `wytarcie calej kreski zostawia pusto`() {
        val kawalki = Kresy.wytnijFragment(pozioma(), 50f, 50f, 300f)
        assertThat(kawalki).isEmpty()
    }

    @Test
    fun `kawalek krotszy niz dwa punkty jest odrzucany`() {
        // Gumka zjada wszystko poza ostatnim punktem.
        val kawalki = Kresy.wytnijFragment(pozioma(), 40f, 50f, 85f)
        assertThat(kawalki.all { it.pointCount >= 2 }).isTrue()
    }

    @Test
    fun `czas w kawalku liczy sie od jego poczatku`() {
        val kawalki = Kresy.wytnijFragment(pozioma(), 50f, 50f, 12f)
        assertThat(kawalki[1].timeMs(0)).isEqualTo(0f)
    }

    @Test
    fun `lasso lapie kreske w srodku`() {
        val kwadrat = listOf(-10f, 0f, 200f, 0f, 200f, 100f, -10f, 100f)
        assertThat(Kresy.wLassie(pozioma(), kwadrat)).isTrue()
    }

    @Test
    fun `lasso nie lapie kreski obok`() {
        val kwadrat = listOf(200f, 0f, 300f, 0f, 300f, 100f, 200f, 100f)
        assertThat(Kresy.wLassie(pozioma(), kwadrat)).isFalse()
    }

    @Test
    fun `lasso lapie kreske, ktorej wiekszosc wpadla do srodka`() {
        // Kwadrat obejmuje x od -10 do 80, czyli okolo 80 procent kreski.
        val kwadrat = listOf(-10f, 0f, 80f, 0f, 80f, 100f, -10f, 100f)
        assertThat(Kresy.wLassie(pozioma(), kwadrat)).isTrue()
    }

    @Test
    fun `punkt w wielokacie liczony jest poprawnie dla ksztaltu wkleslego`() {
        // Litera C: wielokat wklesly.
        val c = listOf(
            0f, 0f, 100f, 0f, 100f, 20f, 20f, 20f,
            20f, 80f, 100f, 80f, 100f, 100f, 0f, 100f,
        )
        assertThat(Kresy.punktWWielokacie(10f, 50f, c)).isTrue()
        assertThat(Kresy.punktWWielokacie(60f, 50f, c)).isFalse()
    }

    @Test
    fun `linijka prostuje krzywa do odcinka`() {
        val krzywa = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                20f, 30f, 8f, 0.5f, 0.4f, 1f,
                40f, 5f, 16f, 0.5f, 0.4f, 1f,
                60f, 20f, 24f, 0.5f, 0.4f, 1f,
            ),
        )
        val prosta = Kresy.wyprostuj(krzywa)
        // Wszystkie punkty leza na odcinku miedzy pierwszym a ostatnim.
        for (i in 0 until prosta.pointCount) {
            val odleglosc = Kresy.odlegloscOdOdcinka(
                prosta.x(i), prosta.y(i),
                prosta.x(0), prosta.y(0),
                prosta.x(prosta.pointCount - 1), prosta.y(prosta.pointCount - 1),
            )
            assertThat(odleglosc).isLessThan(0.01f)
        }
    }

    @Test
    fun `linijka dociaga prawie pozioma kreske do rownej`() {
        val prawiePozioma = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                100f, 3f, 16f, 0.5f, 0.4f, 1f,
            ),
        )
        val prosta = Kresy.wyprostuj(prawiePozioma)
        val ostatni = prosta.pointCount - 1
        assertThat(prosta.y(ostatni)).isWithin(0.01f).of(prosta.y(0))
    }

    @Test
    fun `linijka nie rusza kreski pod katem trzydziestu stopni`() {
        val ukosna = InkStroke(
            id = "k",
            color = 0xFF000000.toInt(),
            size = 2f,
            points = listOf(
                0f, 0f, 0f, 0.5f, 0.4f, 1f,
                100f, 58f, 16f, 0.5f, 0.4f, 1f,
            ),
        )
        val prosta = Kresy.wyprostuj(ukosna)
        val ostatni = prosta.pointCount - 1
        assertThat(prosta.x(ostatni)).isWithin(0.5f).of(100f)
        assertThat(prosta.y(ostatni)).isWithin(0.5f).of(58f)
    }

    @Test
    fun `obszar kilku kresek obejmuje je wszystkie`() {
        val a = pozioma()
        val b = pozioma().translated(0f, 100f)
        val obszar = Kresy.obszar(listOf(a, b))!!
        assertThat(obszar.top).isEqualTo(50f)
        assertThat(obszar.bottom).isEqualTo(150f)
        assertThat(obszar.right).isEqualTo(100f)
    }
}
