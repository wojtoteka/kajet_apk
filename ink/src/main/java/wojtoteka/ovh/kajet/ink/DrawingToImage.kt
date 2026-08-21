package wojtoteka.ovh.kajet.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import wojtoteka.ovh.kajet.core.model.InkStroke
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object DrawingToImage {

    const val DENSITY = 3f

    fun bitmap(
        strokes: List<InkStroke>,
        width: Float,
        height: Float,
        density: Float = DENSITY,
        backgroundColor: Int? = null,
    ): Bitmap {
        val widthPx = max(1, (width * density).roundToInt())
        val heightPx = max(1, (height * density).roundToInt())
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (backgroundColor != null) canvas.drawColor(backgroundColor)

        val renderer = CanvasStrokeRenderer.create()
        val matrix = Matrix().apply { setScale(density, density) }
        // Renderer nie nakłada macierzy na canvas, dostaje ją tylko do jakości
        // teselacji - skalę trzeba nałożyć samemu, inaczej rysunek ląduje
        // pomniejszony w rogu bitmapy.
        canvas.save()
        canvas.concat(matrix)
        for (stroke in strokes) {
            val mesh = runCatching { Strokes.toEngine(stroke) }.getOrNull() ?: continue
            renderer.draw(canvas, mesh, matrix)
        }
        canvas.restore()
        return bitmap
    }

    fun png(
        strokes: List<InkStroke>,
        width: Float,
        height: Float,
        density: Float = DENSITY,
        backgroundColor: Int? = null,
    ): ByteArray {
        val bitmap = bitmap(strokes, width, height, density, backgroundColor)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
