package wojtoteka.ovh.kajet.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.TextMarkers
import wojtoteka.ovh.kajet.ink.Strokes
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object PdfExport {

    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    fun write(
        document: NoteDocument,
        output: OutputStream,
        attachment: (String) -> ByteArray?,
        withBackground: Boolean = true,
    ) {
        val pdf = PdfDocument()
        try {
            when {
                document.handwriting != null -> handwrittenPages(pdf, document, attachment, withBackground)
                document.mindMap != null -> mindMapPage(pdf, document.mindMap!!, document.title)
                document.text != null -> textPages(pdf, document)
            }
            if (pdf.pages.isEmpty()) emptyPage(pdf, document.title)
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun handwrittenPages(
        pdf: PdfDocument,
        document: NoteDocument,
        attachment: (String) -> ByteArray?,
        withBackground: Boolean,
    ) {
        val handwriting = document.handwriting ?: return
        val renderer = CanvasStrokeRenderer.create()

        handwriting.pages.forEachIndexed { index, page ->
            // A scroll can be taller than A4, so we slice it into consecutive pages.
            val sliceCount = max(1, kotlin.math.ceil(page.height / A4_HEIGHT.toFloat()).toInt())
            for (slice in 0 until sliceCount) {
                val offset = slice * A4_HEIGHT.toFloat()
                val info = PdfDocument.PageInfo.Builder(
                    A4_WIDTH,
                    A4_HEIGHT,
                    pdf.pages.size + 1,
                ).create()
                val pdfPage = pdf.startPage(info)
                val canvas = pdfPage.canvas

                canvas.drawColor(Color.WHITE)
                if (withBackground) drawBackground(canvas, page.background ?: handwriting.background, offset)

                canvas.save()
                canvas.translate(0f, -offset)

                for (stroke in page.strokes) {
                    val bounds = stroke.bounds()
                    if (bounds.bottom < offset || bounds.top > offset + A4_HEIGHT) continue
                    val engineStroke = runCatching { Strokes.toEngine(stroke) }.getOrNull() ?: continue
                    renderer.draw(canvas, engineStroke, Matrix())
                }

                for (image in page.images) {
                    val bytes = attachment(image.asset) ?: continue
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(
                            image.x.toInt(),
                            image.y.toInt(),
                            (image.x + image.width).toInt(),
                            (image.y + image.height).toInt(),
                        ),
                        null,
                    )
                    bitmap.recycle()
                }

                for (field in page.texts) {
                    if (field.text.isBlank()) continue
                    drawText(canvas, field.text, field.x, field.y, field.width, field.fontSize, field.color, field.bold)
                }

                canvas.restore()
                pageNumber(canvas, pdf.pages.size + 1)
                pdf.finishPage(pdfPage)
            }
            if (index >= 0) Unit
        }
    }

    private fun textPages(pdf: PdfDocument, document: NoteDocument) {
        val content = document.text?.markdown.orEmpty()
        val leftMargin = 64f
        val topMargin = 72f
        val width = A4_WIDTH - 2 * leftMargin

        var y = topMargin
        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create(),
        )
        page.canvas.drawColor(Color.WHITE)

        fun nextPage() {
            pageNumber(page.canvas, pdf.pages.size + 1)
            pdf.finishPage(page)
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pdf.pages.size + 1).create(),
            )
            page.canvas.drawColor(Color.WHITE)
            y = topMargin
        }

        drawText(page.canvas, document.title, leftMargin, y, width, 22f, Color.BLACK, true)
        y += 40f

        for (line in content.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                y += 8f
                continue
            }
            val level = trimmed.takeWhile { it == '#' }.length
            val (raw, size, bold) = when {
                level in 1..6 -> Triple(trimmed.drop(level + 1), 20f - level * 1.5f, true)
                Regex("^[-*+] \\[[ xX]] ").containsMatchIn(trimmed) ->
                    Triple(
                        trimmed
                            .replace(Regex("^[-*+] \\[ ] "), "☐  ")
                            .replace(Regex("^[-*+] \\[[xX]] "), "☑  "),
                        11f,
                        false,
                    )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    Triple("•  " + trimmed.drop(2), 11f, false)
                trimmed.startsWith("> ") -> Triple("    " + trimmed.drop(2), 11f, false)
                else -> Triple(trimmed, 11f, false)
            }
            // Formatting markers are stripped: on paper, asterisks around a word
            // are two asterisks, not bold text.
            val text = TextMarkers.plain(raw)
            val height = textHeight(text, width, size, bold)
            if (y + height > A4_HEIGHT - topMargin) nextPage()
            drawText(page.canvas, text, leftMargin, y, width, size, Color.BLACK, bold)
            y += height + 4f
        }

        pageNumber(page.canvas, pdf.pages.size + 1)
        pdf.finishPage(page)
    }

    private fun mindMapPage(pdf: PdfDocument, map: MindMapContent, title: String) {
        val info = PdfDocument.PageInfo.Builder(A4_HEIGHT, A4_WIDTH, 1).create()
        val page = pdf.startPage(info)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        drawText(canvas, title, 40f, 36f, A4_HEIGHT - 80f, 18f, Color.BLACK, true)

        if (map.nodes.isEmpty()) {
            pdf.finishPage(page)
            return
        }

        val minX = map.nodes.minOf { it.x }
        val minY = map.nodes.minOf { it.y }
        val maxX = map.nodes.maxOf { it.x + it.width }
        val maxY = map.nodes.maxOf { it.y + it.height }
        val scale = min(
            (A4_HEIGHT - 80f) / max(1f, maxX - minX),
            (A4_WIDTH - 140f) / max(1f, maxY - minY),
        ).coerceAtMost(1.4f)

        canvas.save()
        canvas.translate(40f, 90f)
        canvas.scale(scale, scale)
        canvas.translate(-minX, -minY)

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.rgb(0x8C, 0x87, 0x7C)
        }
        val byId = map.nodes.associateBy { it.id }
        for (edge in map.edges) {
            val from = byId[edge.fromId] ?: continue
            val to = byId[edge.toId] ?: continue
            canvas.drawLine(
                from.x + from.width / 2f,
                from.y + from.height / 2f,
                to.x + to.width / 2f,
                to.y + to.height / 2f,
                edgePaint,
            )
        }

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.rgb(0x23, 0x21, 0x1D)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        for (node in map.nodes) {
            val radius = if (node.shape.name == "OWAL") node.height / 2f else 3f
            canvas.drawRoundRect(
                node.x, node.y, node.x + node.width, node.y + node.height,
                radius, radius, fillPaint,
            )
            canvas.drawRoundRect(
                node.x, node.y, node.x + node.width, node.y + node.height,
                radius, radius, framePaint,
            )
            if (node.text.isNotBlank()) {
                drawText(
                    canvas, node.text, node.x + 8f, node.y + 10f,
                    node.width - 16f, 10f, Color.BLACK, false,
                )
            }
        }
        canvas.restore()
        pdf.finishPage(page)
    }

    private fun emptyPage(pdf: PdfDocument, title: String) {
        val page = pdf.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create(),
        )
        page.canvas.drawColor(Color.WHITE)
        drawText(page.canvas, title, 64f, 72f, A4_WIDTH - 128f, 20f, Color.BLACK, true)
        drawText(
            page.canvas,
            "Ta notatka jest jeszcze pusta.",
            64f, 116f, A4_WIDTH - 128f, 11f, Color.DKGRAY, false,
        )
        pdf.finishPage(page)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layout(text: String, width: Float, size: Float, bold: Boolean): StaticLayout {
        val paint = textPaint(size, Color.BLACK, bold)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setLineSpacing(size * 0.55f, 1f)
            .build()
    }

    private fun textHeight(text: String, width: Float, size: Float, bold: Boolean): Float =
        layout(text, width, size, bold).height.toFloat()

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        size: Float,
        color: Int,
        bold: Boolean,
    ) {
        val paint = textPaint(size, color, bold)
        val textLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setLineSpacing(size * 0.55f, 1f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        textLayout.draw(canvas)
        canvas.restore()
    }

    private fun pageNumber(canvas: Canvas, number: Int) {
        val paint = textPaint(9f, Color.rgb(0x67, 0x63, 0x5A), false)
        canvas.drawText(number.toString(), A4_WIDTH / 2f, A4_HEIGHT - 32f, paint)
    }

    private fun drawBackground(canvas: Canvas, background: PageBackground, offset: Float) {
        if (background == PageBackground.PLAIN) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.rgb(0xD3, 0xCC, 0xBC)
        }
        when (background) {
            PageBackground.LINED -> {
                var y = 28f
                while (y < A4_HEIGHT) {
                    canvas.drawLine(0f, y, A4_WIDTH.toFloat(), y, paint)
                    y += 28f
                }
            }
            PageBackground.GRID -> {
                var y = 20f
                while (y < A4_HEIGHT) {
                    canvas.drawLine(0f, y, A4_WIDTH.toFloat(), y, paint)
                    y += 20f
                }
                var x = 20f
                while (x < A4_WIDTH) {
                    canvas.drawLine(x, 0f, x, A4_HEIGHT.toFloat(), paint)
                    x += 20f
                }
            }
            PageBackground.DOTS -> {
                paint.style = Paint.Style.FILL
                var y = 20f
                while (y < A4_HEIGHT) {
                    var x = 20f
                    while (x < A4_WIDTH) {
                        canvas.drawCircle(x, y, 0.8f, paint)
                        x += 20f
                    }
                    y += 20f
                }
            }
            PageBackground.STAVE -> {
                var y = 60f
                while (y + 36f < A4_HEIGHT) {
                    for (i in 0 until 5) {
                        canvas.drawLine(30f, y + i * 9f, A4_WIDTH - 30f, y + i * 9f, paint)
                    }
                    y += 5 * 9f + 46f
                }
            }
            PageBackground.PLAIN -> Unit
        }
        paint.strokeWidth = 0.9f
        canvas.drawLine(60f, 0f, 60f, A4_HEIGHT.toFloat(), paint)
        if (offset > 0f) Unit
    }

    fun pageAsPng(page: NotePage, density: Float = 2f): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (page.width * density).toInt().coerceAtLeast(1),
            (page.height * density).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(density, density)
        val renderer = CanvasStrokeRenderer.create()
        for (stroke in page.strokes) {
            val engineStroke = runCatching { Strokes.toEngine(stroke) }.getOrNull() ?: continue
            renderer.draw(canvas, engineStroke, Matrix())
        }
        for (field in page.texts) {
            if (field.text.isBlank()) continue
            drawText(canvas, field.text, field.x, field.y, field.width, field.fontSize, field.color, field.bold)
        }
        return bitmap
    }
}
