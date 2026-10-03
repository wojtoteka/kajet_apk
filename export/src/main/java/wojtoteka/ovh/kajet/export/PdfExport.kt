package wojtoteka.ovh.kajet.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.toArgb
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import wojtoteka.ovh.kajet.core.design.KajetLightColors
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.ParagraphAlign
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.ink.ShapeGeometry
import wojtoteka.ovh.kajet.ink.ShapePainter
import wojtoteka.ovh.kajet.ink.StrokeCanvas
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
                document.text != null -> textPages(pdf, document, attachment)
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
        val shapePainter = ShapePainter()
        val identity = Matrix()

        handwriting.pages.forEach { page ->
            // Długa kartka nie mieści się na A4 - kroimy ją na kolejne arkusze.
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

                canvas.drawColor(PaperInk.PAPER)
                canvas.save()
                canvas.clipRect(0f, 0f, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat())
                canvas.translate(0f, -offset)
                paintHandwritten(
                    canvas = canvas,
                    page = page,
                    attachment = attachment,
                    background = page.background ?: handwriting.background,
                    renderer = renderer,
                    shapePainter = shapePainter,
                    strokeMatrix = identity,
                    offset = offset,
                    viewHeight = A4_HEIGHT.toFloat(),
                    withBackground = withBackground,
                )
                canvas.restore()
                pageNumber(canvas, pdf.pages.size + 1)
                pdf.finishPage(pdfPage)
            }
        }
    }

    /**
     * Ta sama kolejność co na ekranie: tło, zdjęcia, kształty, atrament, pola.
     * Kolory idą przez [PaperInk], bo kartka eksportu jest biała.
     */
    private fun paintHandwritten(
        canvas: Canvas,
        page: NotePage,
        attachment: (String) -> ByteArray?,
        background: PageBackground,
        renderer: CanvasStrokeRenderer,
        shapePainter: ShapePainter,
        strokeMatrix: Matrix,
        offset: Float,
        viewHeight: Float,
        withBackground: Boolean,
    ) {
        if (withBackground) drawBackground(canvas, background, page.width, page.height)

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (image in page.images) {
            if (!visible(image.y, image.y + image.height, offset, viewHeight)) continue
            val bytes = attachment(image.asset) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    image.x,
                    image.y,
                    image.x + image.width,
                    image.y + image.height,
                ),
                imagePaint,
            )
            bitmap.recycle()
        }

        for (shape in page.shapes) {
            val bounds = ShapeGeometry.bounds(shape)
            if (!visible(bounds.top, bounds.bottom, offset, viewHeight)) continue
            shapePainter.draw(canvas, PaperInk.forShape(shape))
        }

        val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fallbackPath = Path()
        for (stroke in page.strokes) {
            val bounds = stroke.bounds()
            if (!visible(bounds.top, bounds.bottom, offset, viewHeight)) continue
            val paper = PaperInk.forStroke(stroke)
            val engineStroke = runCatching { Strokes.toEngine(paper) }.getOrNull()
            if (engineStroke != null) {
                renderer.draw(canvas, engineStroke, strokeMatrix)
            } else {
                drawFallback(canvas, paper, fallbackPaint, fallbackPath)
            }
        }

        for (field in page.texts) {
            if (!visible(field.y, field.y + field.height, offset, viewHeight)) continue
            drawField(canvas, PaperInk.forField(field))
        }
    }

    private fun visible(top: Float, bottom: Float, offset: Float, viewHeight: Float): Boolean =
        bottom >= offset && top <= offset + viewHeight

    private fun drawFallback(
        canvas: Canvas,
        stroke: InkStroke,
        paint: Paint,
        path: Path,
    ) {
        val count = stroke.pointCount
        if (count == 0) return
        paint.color = stroke.color
        paint.strokeWidth = stroke.size.coerceAtLeast(0.4f)
        if (count == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.x(0), stroke.y(0), stroke.size / 2f, paint)
            paint.style = Paint.Style.STROKE
            return
        }
        path.reset()
        path.moveTo(stroke.x(0), stroke.y(0))
        for (i in 1 until count) path.lineTo(stroke.x(i), stroke.y(i))
        canvas.drawPath(path, paint)
    }

    /**
     * Wiersz ze zdjęciami: jedno albo kilka obok siebie, tak jak w notatce.
     *
     * Szerokości ściąga [ImageLines.sideBySide] - ta sama rachuba co w
     * edytorze, więc wydruk pokazuje to, co było widać na tablecie. Oddaje
     * wysokość, o którą przesuwa się kartka; zero, gdy żadnego zdjęcia nie
     * udało się wczytać.
     */
    private fun drawPhotoRow(
        pictures: List<PdfMarkdown.ImageRef>,
        attachment: (String) -> ByteArray?,
        canvas: () -> Canvas,
        paint: Paint,
        leftMargin: Float,
        width: Float,
        topMargin: Float,
        top: () -> Float,
        place: (Float) -> Unit,
    ): Float {
        val gap = 8f
        val loaded = pictures.mapNotNull { picture ->
            val bytes = PdfMarkdown.attachmentBytes(attachment, picture.asset)
            val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bitmap == null) null else bitmap to picture
        }
        if (loaded.isEmpty()) return 0f

        val gapShare = if (loaded.size > 1) gap * (loaded.size - 1) / width else 0f
        val parts = ImageLines.sideBySide(loaded.map { it.second.width }, gapShare)
        var sizes = loaded.mapIndexed { index, (bitmap, _) ->
            val drawW = width * parts[index]
            drawW to bitmap.height * (drawW / bitmap.width.toFloat())
        }

        // Najwyższe zdjęcie wiersza wyznacza skalę - kartka ma swoją wysokość,
        // a wiersz musi zmieścić się w niej cały.
        val tallest = sizes.maxOf { it.second }
        val room = A4_HEIGHT - 2 * topMargin
        if (tallest > room) {
            val shrink = room / tallest
            sizes = sizes.map { (drawW, drawH) -> drawW * shrink to drawH * shrink }
        }

        val rowHeight = sizes.maxOf { it.second }
        val used = sizes.fold(0f) { total, size -> total + size.first } + gap * (sizes.size - 1)
        val free = (width - used).coerceAtLeast(0f)
        val start = leftMargin + when (loaded.first().second.align) {
            NoteAlign.LEFT -> 0f
            NoteAlign.CENTER -> free / 2f
            NoteAlign.RIGHT -> free
        }

        place(rowHeight)
        val y = top()
        var x = start
        loaded.forEachIndexed { index, (bitmap, _) ->
            val (drawW, drawH) = sizes[index]
            canvas().drawBitmap(bitmap, null, RectF(x, y, x + drawW, y + drawH), paint)
            bitmap.recycle()
            x += drawW + gap
        }
        return rowHeight + 8f
    }

    private fun textPages(
        pdf: PdfDocument,
        document: NoteDocument,
        attachment: (String) -> ByteArray?,
    ) {
        val content = document.text?.markdown.orEmpty()
        val leftMargin = 64f
        val topMargin = 72f
        val width = A4_WIDTH - 2 * leftMargin
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        var y = topMargin
        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create(),
        )
        page.canvas.drawColor(PaperInk.PAPER)
        var fence: String? = null

        fun nextPage() {
            pageNumber(page.canvas, pdf.pages.size + 1)
            pdf.finishPage(page)
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pdf.pages.size + 1).create(),
            )
            page.canvas.drawColor(PaperInk.PAPER)
            y = topMargin
        }

        fun place(height: Float) {
            if (y + height > A4_HEIGHT - topMargin) nextPage()
        }

        drawText(page.canvas, document.title, leftMargin, y, width, 22f, PaperInk.INK, true)
        y += 40f

        val noteAlign = document.text?.align ?: NoteAlign.LEFT
        // Wielkość pisma notatki - od niej liczy się powiększenie fragmentu.
        val noteSize = document.text?.fontSize?.takeIf { it > 0f } ?: TextContent.DEFAULT_SIZE
        for (line in content.split('\n')) {
            if (fence != null) {
                val trimmed = line.trim()
                if (PdfMarkdown.closesFence(trimmed, fence)) {
                    fence = null
                    continue
                }
                val height = styledHeight(line, width, 10f)
                place(height)
                drawStyled(page.canvas, line, leftMargin, y, width, 10f, mono = true)
                y += height + 2f
                continue
            }
            // Ułożenie akapitu siedzi w znaczniku obejmującym wiersz.
            val paragraphAlign = ParagraphAlign.alignOf(line.trim()) ?: noteAlign
            val trimmed = ParagraphAlign.unwrap(line.trim()).trim()
            val opens = PdfMarkdown.opensFence(trimmed)
            if (opens != null) {
                fence = opens
                continue
            }
            if (trimmed.isEmpty()) {
                y += 8f
                continue
            }

            val pictures = PdfMarkdown.images(trimmed)
            if (pictures.isNotEmpty()) {
                y += drawPhotoRow(
                    pictures = pictures,
                    attachment = attachment,
                    canvas = { page.canvas },
                    paint = imagePaint,
                    leftMargin = leftMargin,
                    width = width,
                    topMargin = topMargin,
                    top = { y },
                    place = { place(it) },
                )
                continue
            }

            // Kratki nagłówka liczy ten sam czytnik co notatka: „#hashtag"
            // bez spacji to zwykły tekst, a nie nagłówek „ashtag".
            val level = RichTextCodec.headingLevelOfLine(trimmed)
            val look = when {
                level > 0 -> LineLook(
                    trimmed.substring(RichTextCodec.headingPrefixLength(trimmed)),
                    20f - level * 1.5f,
                    extraBold = true,
                )
                Regex("^[-*+] \\[[xX]] ").containsMatchIn(trimmed) -> LineLook(
                    trimmed.replace(Regex("^[-*+] \\[[xX]] "), "☑  "),
                    11f,
                    extraStrike = true,
                )
                Regex("^[-*+] \\[ ] ").containsMatchIn(trimmed) -> LineLook(
                    trimmed.replace(Regex("^[-*+] \\[ ] "), "☐  "),
                    11f,
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    LineLook("•  " + trimmed.drop(2), 11f)
                trimmed.startsWith("> ") -> LineLook("    " + trimmed.drop(2), 11f, extraItalic = true)
                else -> LineLook(trimmed, 11f)
            }
            val block = styled(
                markdown = look.raw,
                noteSize = noteSize,
                extraBold = look.extraBold,
                extraItalic = look.extraItalic,
                extraStrike = look.extraStrike,
            )
            val height = styledHeight(block, width, look.size)
            place(height)
            drawStyled(page.canvas, block, leftMargin, y, width, look.size, align = paragraphAlign)
            y += height + 4f
        }

        pageNumber(page.canvas, pdf.pages.size + 1)
        pdf.finishPage(page)
    }

    private data class LineLook(
        val raw: String,
        val size: Float,
        val extraBold: Boolean = false,
        val extraItalic: Boolean = false,
        val extraStrike: Boolean = false,
    )

    private fun mindMapPage(pdf: PdfDocument, map: MindMapContent, title: String) {
        val info = PdfDocument.PageInfo.Builder(A4_HEIGHT, A4_WIDTH, 1).create()
        val page = pdf.startPage(info)
        val canvas = page.canvas
        canvas.drawColor(PaperInk.PAPER)

        drawText(canvas, title, 40f, 36f, A4_HEIGHT - 80f, 18f, PaperInk.INK, true)

        val nodes = PdfMarkdown.visibleNodes(map)
        if (nodes.isEmpty()) {
            pdf.finishPage(page)
            return
        }

        val visibleIds = PdfMarkdown.visibleIds(map)
        val minX = nodes.minOf { it.x }
        val minY = nodes.minOf { it.y }
        val maxX = nodes.maxOf { it.x + it.width }
        val maxY = nodes.maxOf { it.y + it.height }
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
        val byId = nodes.associateBy { it.id }
        for (edge in map.edges) {
            if (edge.fromId !in visibleIds || edge.toId !in visibleIds) continue
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

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = KajetLightColors.sheet.toArgb()
        }
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        for (node in nodes) {
            val radius = PdfMarkdown.cornerRadius(node)
            framePaint.color = PdfMarkdown.nodeInk(node)
            canvas.drawRoundRect(
                node.x, node.y, node.x + node.width, node.y + node.height,
                radius, radius, fillPaint,
            )
            canvas.drawRoundRect(
                node.x, node.y, node.x + node.width, node.y + node.height,
                radius, radius, framePaint,
            )
            drawNodeText(canvas, node)
        }
        canvas.restore()
        pdf.finishPage(page)
    }

    private fun emptyPage(pdf: PdfDocument, title: String) {
        val page = pdf.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create(),
        )
        page.canvas.drawColor(PaperInk.PAPER)
        drawText(page.canvas, title, 64f, 72f, A4_WIDTH - 128f, 20f, PaperInk.INK, true)
        drawText(
            page.canvas,
            words.emptyNoteInExport,
            64f, 116f, A4_WIDTH - 128f, 11f, Color.DKGRAY, false,
        )
        pdf.finishPage(page)
    }

    private fun textPaint(
        size: Float,
        color: Int,
        bold: Boolean,
        italic: Boolean = false,
        mono: Boolean = false,
    ) = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = typefaceFor(
            font = if (mono) NoteFont.MONO else NoteFont.BODY,
            bold = bold,
            italic = italic,
        )
    }

    private fun styled(
        markdown: String,
        /** Wielkość pisma notatki w aplikacji - fragment „21px" rośnie względem niej. */
        noteSize: Float,
        extraBold: Boolean = false,
        extraItalic: Boolean = false,
        extraStrike: Boolean = false,
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val runs = PdfMarkdown.runs(markdown).ifEmpty {
            if (markdown.isEmpty()) emptyList() else listOf(PdfMarkdown.Run(markdown))
        }
        val flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        for (run in runs) {
            val from = builder.length
            builder.append(run.text)
            val to = builder.length
            if (from == to) continue
            val bold = extraBold || run.bold || run.heading != null
            val italic = extraItalic || run.italic
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                builder.setSpan(StyleSpan(style), from, to, flags)
            }
            if (run.underline) builder.setSpan(UnderlineSpan(), from, to, flags)
            if (extraStrike || run.strike) builder.setSpan(StrikethroughSpan(), from, to, flags)
            if (run.highlight) {
                builder.setSpan(BackgroundColorSpan(PdfMarkdown.HIGHLIGHT), from, to, flags)
            }
            if (run.code) {
                builder.setSpan(TypefaceSpan("monospace"), from, to, flags)
            }
            val color = run.color?.let { PaperInk.ink(it) } ?: PaperInk.INK
            builder.setSpan(ForegroundColorSpan(color), from, to, flags)
            /*
              Wielkość fragmentu i nagłówek nadany kawałkowi zdania - względem
              pisma wiersza, tak samo jak w notatce. Dawniej „21px" szło na
              kartkę jako 21 punktów przy 11-punktowym tekście, czyli prawie
              dwa razy za duże.
            */
            val scale = run.sizePx?.let { it / noteSize } ?: RichTextCodec.headingScale(run.heading)
            if (scale != 1f) builder.setSpan(RelativeSizeSpan(scale), from, to, flags)
        }
        return builder
    }

    private fun styledLayout(
        text: CharSequence,
        width: Float,
        size: Float,
        mono: Boolean = false,
        align: NoteAlign = NoteAlign.LEFT,
    ): StaticLayout {
        val paint = textPaint(size, PaperInk.INK, bold = false, mono = mono)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setLineSpacing(size * 0.55f, 1f)
            .setAlignment(
                when (align) {
                    NoteAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    NoteAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    NoteAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
            .build()
    }

    private fun styledHeight(text: CharSequence, width: Float, size: Float): Float =
        styledLayout(text, width, size).height.toFloat()

    private fun drawStyled(
        canvas: Canvas,
        text: CharSequence,
        x: Float,
        y: Float,
        width: Float,
        size: Float,
        mono: Boolean = false,
        align: NoteAlign = NoteAlign.LEFT,
    ) {
        canvas.save()
        canvas.translate(x, y)
        styledLayout(text, width, size, mono, align).draw(canvas)
        canvas.restore()
    }

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

    private fun drawNodeText(canvas: Canvas, node: MindNode) {
        if (node.text.isBlank()) return
        val padX = PdfMarkdown.NODE_PAD_X / 2f
        val padY = PdfMarkdown.NODE_PAD_Y / 2f
        val size = if (node.fontSize > 0f) node.fontSize else PdfMarkdown.NODE_FONT
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = PdfMarkdown.nodeTextInk(node)
            typeface = typefaceFor(node.font, node.bold, node.italic)
        }
        val width = (node.width - padX * 2f).toInt().coerceAtLeast(1)
        val alignment = when (node.align) {
            NoteAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            NoteAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            NoteAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val textLayout = StaticLayout.Builder
            .obtain(node.text, 0, node.text.length, paint, width)
            .setAlignment(alignment)
            .build()
        val rect = RectF(node.x, node.y, node.x + node.width, node.y + node.height)
        val radius = PdfMarkdown.cornerRadius(node)
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(node.x + padX, node.y + padY)
        textLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * Pole TEXT/CODE jak na ekranie: płytka tła, krój, kursywa, podkreślenie, justowanie.
     * Na ekranie wcięcie to 4.dp; tu 4 jednostki strony (przy gęstości 1 to to samo).
     */
    private fun drawField(canvas: Canvas, field: TextBoxElement) {
        if (field.background == 0 && field.text.isBlank()) return

        if (field.background != 0) {
            val fill = Paint().apply {
                style = Paint.Style.FILL
                color = field.background
            }
            canvas.drawRect(
                field.x,
                field.y,
                field.x + field.width,
                field.y + field.height,
                fill,
            )
        }
        if (field.text.isBlank()) return

        val inset = 4f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = field.fontSize
            color = field.color
            typeface = typefaceFor(field.font, field.bold, field.italic)
            isUnderlineText = field.underline
        }
        val width = (field.width - inset * 2f).toInt().coerceAtLeast(1)
        val alignment = when (field.align) {
            NoteAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            NoteAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            NoteAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val textLayout = StaticLayout.Builder
            .obtain(field.text, 0, field.text.length, paint, width)
            .setAlignment(alignment)
            .build()
        canvas.save()
        canvas.clipRect(field.x, field.y, field.x + field.width, field.y + field.height)
        canvas.translate(field.x + inset, field.y + inset)
        textLayout.draw(canvas)
        canvas.restore()
    }

    private fun typefaceFor(font: NoteFont, bold: Boolean, italic: Boolean): Typeface {
        val family = when (font) {
            NoteFont.MONO -> Typeface.MONOSPACE
            NoteFont.HEADING, NoteFont.BODY -> Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(family, style)
    }

    private fun pageNumber(canvas: Canvas, number: Int) {
        val paint = textPaint(9f, Color.rgb(0x67, 0x63, 0x5A), false)
        canvas.drawText(number.toString(), A4_WIDTH / 2f, A4_HEIGHT - 32f, paint)
    }

    private fun drawBackground(
        canvas: Canvas,
        background: PageBackground,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        if (background == PageBackground.PLAIN) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = PaperInk.RULE
        }
        when (background) {
            PageBackground.LINED -> {
                var y = StrokeCanvas.LINE_SPACING
                while (y < pageHeight) {
                    canvas.drawLine(0f, y, pageWidth, y, paint)
                    y += StrokeCanvas.LINE_SPACING
                }
            }
            PageBackground.GRID -> {
                var y = StrokeCanvas.GRID_SPACING
                while (y < pageHeight) {
                    canvas.drawLine(0f, y, pageWidth, y, paint)
                    y += StrokeCanvas.GRID_SPACING
                }
                var x = StrokeCanvas.GRID_SPACING
                while (x < pageWidth) {
                    canvas.drawLine(x, 0f, x, pageHeight, paint)
                    x += StrokeCanvas.GRID_SPACING
                }
            }
            PageBackground.DOTS -> {
                paint.style = Paint.Style.FILL
                var y = StrokeCanvas.GRID_SPACING
                while (y < pageHeight) {
                    var x = StrokeCanvas.GRID_SPACING
                    while (x < pageWidth) {
                        canvas.drawCircle(x, y, 0.8f, paint)
                        x += StrokeCanvas.GRID_SPACING
                    }
                    y += StrokeCanvas.GRID_SPACING
                }
                paint.style = Paint.Style.STROKE
            }
            PageBackground.STAVE -> {
                // Na ekranie pierwsza linia to STAVE_SPACING (9), nie margines 60.
                var y = StrokeCanvas.STAVE_SPACING
                while (y + 4 * StrokeCanvas.STAVE_SPACING < pageHeight) {
                    for (i in 0 until 5) {
                        canvas.drawLine(
                            StrokeCanvas.STAVE_MARGIN,
                            y + i * StrokeCanvas.STAVE_SPACING,
                            pageWidth - StrokeCanvas.STAVE_MARGIN,
                            y + i * StrokeCanvas.STAVE_SPACING,
                            paint,
                        )
                    }
                    y += 5 * StrokeCanvas.STAVE_SPACING + StrokeCanvas.STAVE_GAP
                }
            }
            PageBackground.PLAIN -> Unit
        }
        paint.strokeWidth = 0.9f
        canvas.drawLine(StrokeCanvas.PAGE_MARGIN, 0f, StrokeCanvas.PAGE_MARGIN, pageHeight, paint)
    }

    fun pageAsPng(
        page: NotePage,
        density: Float = 2f,
        attachment: (String) -> ByteArray? = { null },
        background: PageBackground? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (page.width * density).toInt().coerceAtLeast(1),
            (page.height * density).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(PaperInk.PAPER)
        canvas.scale(density, density)
        val renderer = CanvasStrokeRenderer.create()
        // Macierz mówi rendererowi o skali canvasa; bez niej teselacja
        // liczy się dla skali 1 i kreski wychodzą kanciaste.
        val strokeMatrix = Matrix().apply { setScale(density, density) }
        paintHandwritten(
            canvas = canvas,
            page = page,
            attachment = attachment,
            background = page.background ?: background ?: PageBackground.PLAIN,
            renderer = renderer,
            shapePainter = ShapePainter(),
            strokeMatrix = strokeMatrix,
            offset = 0f,
            viewHeight = page.height,
            withBackground = true,
        )
        return bitmap
    }
}
