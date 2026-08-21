package wojtoteka.ovh.kajet.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Wczytywanie zdjęć do pamięci.
 *
 * Zdjęcie z aparatu tabletu to dziś kilkanaście milionów punktów. Wczytane
 * w pełnej rozdzielczości zajmuje w pamięci cztery bajty na punkt - przy 12 Mpix
 * to około 48 MB na JEDNO zdjęcie, przy kilku na kartce kończy się brakiem
 * pamięci i zniknięciem aplikacji. Na ekranie i tak nie widać więcej niż
 * kilku milionów punktów, więc zdjęcie idzie do pamięci zmniejszone.
 *
 * Plik na dysku zostaje nietknięty, w pełnej rozdzielczości - eksport do PDF
 * czyta go osobno i ma z czego drukować.
 */
object Bitmaps {

    /**
     * Najdłuższy bok bitmapy trzymanej w pamięci, w punktach.
     *
     * 2048 z zapasem starcza na kartkę A4 pokazaną na całym ekranie tabletu
     * i na rozsądne przybliżenie. Koszt to około 16 MB w najgorszym przypadku.
     */
    const val LONGEST_SIDE = 2048

    /**
     * Wczytuje zdjęcie zmniejszone tak, żeby jego dłuższy bok nie przekraczał
     * [longestSide]. Zwraca null, gdy bajty nie są obrazem.
     *
     * Wołać poza wątkiem głównym - samo dekodowanie potrafi trwać ułamki
     * sekundy, a przy większych plikach dłużej.
     */
    fun decode(data: ByteArray, longestSide: Int = LONGEST_SIDE): Bitmap? {
        if (data.isEmpty()) return null

        // Pierwsze przejście czyta sam rozmiar, bez wkładania punktów do pamięci.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, longestSide)
        }
        return runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, options) }
            .getOrNull()
    }

    /**
     * Krok pomniejszania. BitmapFactory przyjmuje tylko potęgi dwójki, więc
     * podwajamy, dopóki dłuższy bok nie zejdzie do [longestSide].
     */
    fun sampleSize(width: Int, height: Int, longestSide: Int): Int {
        if (width <= 0 || height <= 0 || longestSide <= 0) return 1
        var step = 1
        while (maxOf(width, height) / step > longestSide) {
            step *= 2
        }
        return step
    }
}
