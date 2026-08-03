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

sealed interface RecognitionState {
    data object NoModel : RecognitionState
    data class Downloading(val message: String) : RecognitionState
    data object Ready : RecognitionState
    data class Failed(val message: String) : RecognitionState
}

class HandwritingRecognition(private val language: String = "pl") {

    private val modelManager = RemoteModelManager.getInstance()

    private val model: DigitalInkRecognitionModel? by lazy {
        val identifier = runCatching {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(language)
        }.getOrNull()
        identifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    suspend fun isModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        val ready = model ?: return@withContext false
        runCatching { modelManager.isModelDownloaded(ready).await() }.getOrDefault(false)
    }

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        val ready = model ?: throw IllegalStateException(
            "Na tym urządzeniu nie ma rozpoznawania pisma po polsku.",
        )
        runCatching {
            modelManager.download(ready, DownloadConditions.Builder().build()).await()
        }.getOrElse {
            throw IllegalStateException(
                "Nie udało się pobrać modelu pisma. Sprawdź internet i spróbuj jeszcze raz.",
                it,
            )
        }
    }

    suspend fun recognize(
        strokes: List<InkStroke>,
        areaWidth: Float,
        areaHeight: Float,
    ): List<String> = withContext(Dispatchers.Default) {
        if (strokes.isEmpty()) return@withContext emptyList()
        val ready = model ?: throw IllegalStateException(
            "Na tym urządzeniu nie ma rozpoznawania pisma po polsku.",
        )

        val bounds = Strokes.bounds(strokes) ?: return@withContext emptyList()
        val builder = Ink.builder()

        for (stroke in strokes) {
            if (stroke.pointCount < 2) continue
            val mlStroke = Ink.Stroke.builder()
            for (i in 0 until stroke.pointCount) {
                mlStroke.addPoint(
                    Ink.Point.create(
                        stroke.x(i) - bounds.left,
                        stroke.y(i) - bounds.top,
                        stroke.timeMs(i).toLong(),
                    ),
                )
            }
            builder.addStroke(mlStroke.build())
        }

        val context = RecognitionContext.builder()
            .setWritingArea(
                WritingArea(
                    maxOf(areaWidth, bounds.width + 1f),
                    maxOf(areaHeight, bounds.height + 1f),
                ),
            )
            .build()

        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(ready).build(),
        )

        try {
            val result = recognizer.recognize(builder.build(), context).await()
            result.candidates.map { it.text }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Nie udało się odczytać pisma. Zaznacz mniejszy fragment i spróbuj jeszcze raz.",
                e,
            )
        } finally {
            recognizer.close()
        }
    }
}
