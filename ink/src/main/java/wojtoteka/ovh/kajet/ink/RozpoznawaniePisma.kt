package wojtoteka.ovh.kajet.ink

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.InkStroke

/** Co się dzieje z rozpoznawaniem pisma. Pokazujemy to użytkownikowi wprost. */
sealed interface StanRozpoznawania {
    data object BrakModelu : StanRozpoznawania
    data class Pobieranie(val opis: String) : StanRozpoznawania
    data object Gotowy : StanRozpoznawania
    data class Blad(val komunikat: String) : StanRozpoznawania
}

/**
 * Zamiana pisma odręcznego na tekst po polsku.
 *
 * Model językowy pobiera się raz, z internetu, i zostaje na tablecie.
 * Potem rozpoznawanie działa bez sieci, także w szkole i w pociągu.
 *
 * Do rozpoznawania wysyłamy dokładnie te punkty, które zapisał rysik,
 * przesunięte do zera, bo modelowi łatwiej, gdy pismo zaczyna się w rogu
 * przekazanego obszaru.
 */
class RozpoznawaniePisma(private val jezyk: String = "pl") {

    private val menedzerModeli = RemoteModelManager.getInstance()

    private val model: DigitalInkRecognitionModel? by lazy {
        val identyfikator = runCatching {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(jezyk)
        }.getOrNull()
        identyfikator?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    /** Czy model polski leży już na tablecie. */
    suspend fun modelPobrany(): Boolean = withContext(Dispatchers.IO) {
        val gotowy = model ?: return@withContext false
        runCatching { menedzerModeli.isModelDownloaded(gotowy).await() }.getOrDefault(false)
    }

    /**
     * Pobiera model. Wymaga internetu i robi się to raz.
     * Rzuca wyjątek z komunikatem, który da się pokazać wprost użytkownikowi.
     */
    suspend fun pobierzModel() = withContext(Dispatchers.IO) {
        val gotowy = model ?: throw IllegalStateException(
            "Na tym urządzeniu nie ma rozpoznawania pisma po polsku.",
        )
        runCatching {
            menedzerModeli.download(gotowy, DownloadConditions.Builder().build()).await()
        }.getOrElse {
            throw IllegalStateException(
                "Nie udało się pobrać modelu pisma. Sprawdź internet i spróbuj jeszcze raz.",
                it,
            )
        }
    }

    /**
     * Zamienia kreski na tekst. Zwraca najlepszą propozycję oraz kilka kolejnych,
     * bo przy niewyraźnym piśmie warto mieć z czego wybierać.
     */
    suspend fun rozpoznaj(
        kreski: List<InkStroke>,
        szerokoscObszaru: Float,
        wysokoscObszaru: Float,
    ): List<String> = withContext(Dispatchers.Default) {
        if (kreski.isEmpty()) return@withContext emptyList()
        val gotowy = model ?: throw IllegalStateException(
            "Na tym urządzeniu nie ma rozpoznawania pisma po polsku.",
        )

        val obszar = Kresy.obszar(kreski) ?: return@withContext emptyList()
        val budowniczy = Ink.builder()

        for (kreska in kreski) {
            if (kreska.pointCount < 2) continue
            val kreskaMl = Ink.Stroke.builder()
            for (i in 0 until kreska.pointCount) {
                kreskaMl.addPoint(
                    Ink.Point.create(
                        kreska.x(i) - obszar.left,
                        kreska.y(i) - obszar.top,
                        kreska.timeMs(i).toLong(),
                    ),
                )
            }
            budowniczy.addStroke(kreskaMl.build())
        }

        val kontekst = RecognitionContext.builder()
            .setWritingArea(
                WritingArea(
                    maxOf(szerokoscObszaru, obszar.width + 1f),
                    maxOf(wysokoscObszaru, obszar.height + 1f),
                ),
            )
            .build()

        val rozpoznawacz = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(gotowy).build(),
        )

        try {
            val wynik = rozpoznawacz.recognize(budowniczy.build(), kontekst).await()
            wynik.candidates.map { it.text }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Nie udało się odczytać pisma. Zaznacz mniejszy fragment i spróbuj jeszcze raz.",
                e,
            )
        } finally {
            rozpoznawacz.close()
        }
    }
}
