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
import wojtoteka.ovh.kajet.ink.Kresy
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Eksport notatki do pliku PDF.
 *
 * Strona A4 w pliku PDF ma 595 na 842 punkty i dokładnie w takich jednostkach
 * trzymamy współrzędne w notatce, więc nic nie trzeba przeliczać ani skalować.
 *
 * Pola tekstowe idą do pliku jako prawdziwy tekst, a nie jako obrazek,
 * więc da się je zaznaczyć i wyszukać w czytniku PDF. Pismo odręczne
 * z natury zostaje rysunkiem, bo takie jest.
 */
object EksportPdf {

    const val SZEROKOSC_A4 = 595
    const val WYSOKOSC_A4 = 842

    fun zapisz(
        dokument: NoteDocument,
        wyjscie: OutputStream,
        zalacznik: (String) -> ByteArray?,
        zTlem: Boolean = true,
    ) {
        val pdf = PdfDocument()
        try {
            when {
                dokument.handwriting != null -> stronyOdreczne(pdf, dokument, zalacznik, zTlem)
                dokument.mindMap != null -> stronaMapy(pdf, dokument.mindMap!!, dokument.title)
                dokument.text != null -> stronyTekstowe(pdf, dokument)
            }
            if (pdf.pages.isEmpty()) pustaStrona(pdf, dokument.title)
            pdf.writeTo(wyjscie)
        } finally {
            pdf.close()
        }
    }

    private fun stronyOdreczne(
        pdf: PdfDocument,
        dokument: NoteDocument,
        zalacznik: (String) -> ByteArray?,
        zTlem: Boolean,
    ) {
        val pismo = dokument.handwriting ?: return
        val rysownik = CanvasStrokeRenderer.create()

        pismo.pages.forEachIndexed { numer, kartka ->
            // Wstęga bywa dłuższa niż kartka A4, więc tniemy ją na kolejne strony.
            val ileStron = max(1, kotlin.math.ceil(kartka.height / WYSOKOSC_A4.toFloat()).toInt())
            for (czesc in 0 until ileStron) {
                val przesuniecie = czesc * WYSOKOSC_A4.toFloat()
                val opis = PdfDocument.PageInfo.Builder(
                    SZEROKOSC_A4,
                    WYSOKOSC_A4,
                    pdf.pages.size + 1,
                ).create()
                val strona = pdf.startPage(opis)
                val plotno = strona.canvas

                plotno.drawColor(Color.WHITE)
                if (zTlem) rysujTlo(plotno, kartka.background ?: pismo.background, przesuniecie)

                plotno.save()
                plotno.translate(0f, -przesuniecie)

                for (kreska in kartka.strokes) {
                    val granice = kreska.bounds()
                    if (granice.bottom < przesuniecie || granice.top > przesuniecie + WYSOKOSC_A4) continue
                    val gotowa = runCatching { Kresy.doSilnika(kreska) }.getOrNull() ?: continue
                    rysownik.draw(plotno, gotowa, Matrix())
                }

                for (obrazek in kartka.images) {
                    val dane = zalacznik(obrazek.asset) ?: continue
                    val bitmapa = BitmapFactory.decodeByteArray(dane, 0, dane.size) ?: continue
                    plotno.drawBitmap(
                        bitmapa,
                        null,
                        Rect(
                            obrazek.x.toInt(),
                            obrazek.y.toInt(),
                            (obrazek.x + obrazek.width).toInt(),
                            (obrazek.y + obrazek.height).toInt(),
                        ),
                        null,
                    )
                    bitmapa.recycle()
                }

                for (pole in kartka.texts) {
                    if (pole.text.isBlank()) continue
                    rysujTekst(plotno, pole.text, pole.x, pole.y, pole.width, pole.fontSize, pole.color, pole.bold)
                }

                plotno.restore()
                numerStrony(plotno, pdf.pages.size + 1)
                pdf.finishPage(strona)
            }
            if (numer >= 0) Unit
        }
    }

    private fun stronyTekstowe(pdf: PdfDocument, dokument: NoteDocument) {
        val tresc = dokument.text?.markdown.orEmpty()
        val marginesLewy = 64f
        val marginesGorny = 72f
        val szerokosc = SZEROKOSC_A4 - 2 * marginesLewy

        var y = marginesGorny
        var strona = pdf.startPage(
            PdfDocument.PageInfo.Builder(SZEROKOSC_A4, WYSOKOSC_A4, 1).create(),
        )
        strona.canvas.drawColor(Color.WHITE)

        fun nowaStrona() {
            numerStrony(strona.canvas, pdf.pages.size + 1)
            pdf.finishPage(strona)
            strona = pdf.startPage(
                PdfDocument.PageInfo.Builder(SZEROKOSC_A4, WYSOKOSC_A4, pdf.pages.size + 1).create(),
            )
            strona.canvas.drawColor(Color.WHITE)
            y = marginesGorny
        }

        rysujTekst(strona.canvas, dokument.title, marginesLewy, y, szerokosc, 22f, Color.BLACK, true)
        y += 40f

        for (wiersz in tresc.split('\n')) {
            val przyciety = wiersz.trim()
            if (przyciety.isEmpty()) {
                y += 8f
                continue
            }
            val poziom = przyciety.takeWhile { it == '#' }.length
            val (tekst, rozmiar, pogrubienie) = when {
                poziom in 1..6 -> Triple(przyciety.drop(poziom + 1), 20f - poziom * 1.5f, true)
                przyciety.startsWith("- ") || przyciety.startsWith("* ") ->
                    Triple("•  " + przyciety.drop(2), 11f, false)
                Regex("^[-*+] \\[[ xX]] ").containsMatchIn(przyciety) ->
                    Triple(przyciety.replace(Regex("^[-*+] \\[ ] "), "[ ]  ").replace(Regex("^[-*+] \\[[xX]] "), "[x]  "), 11f, false)
                przyciety.startsWith("> ") -> Triple("    " + przyciety.drop(2), 11f, false)
                else -> Triple(przyciety, 11f, false)
            }
            val wysokosc = wysokoscTekstu(tekst, szerokosc, rozmiar, pogrubienie)
            if (y + wysokosc > WYSOKOSC_A4 - marginesGorny) nowaStrona()
            rysujTekst(strona.canvas, tekst, marginesLewy, y, szerokosc, rozmiar, Color.BLACK, pogrubienie)
            y += wysokosc + 4f
        }

        numerStrony(strona.canvas, pdf.pages.size + 1)
        pdf.finishPage(strona)
    }

    private fun stronaMapy(pdf: PdfDocument, mapa: MindMapContent, tytul: String) {
        val opis = PdfDocument.PageInfo.Builder(WYSOKOSC_A4, SZEROKOSC_A4, 1).create()
        val strona = pdf.startPage(opis)
        val plotno = strona.canvas
        plotno.drawColor(Color.WHITE)

        rysujTekst(plotno, tytul, 40f, 36f, WYSOKOSC_A4 - 80f, 18f, Color.BLACK, true)

        if (mapa.nodes.isEmpty()) {
            pdf.finishPage(strona)
            return
        }

        val minX = mapa.nodes.minOf { it.x }
        val minY = mapa.nodes.minOf { it.y }
        val maxX = mapa.nodes.maxOf { it.x + it.width }
        val maxY = mapa.nodes.maxOf { it.y + it.height }
        val skala = min(
            (WYSOKOSC_A4 - 80f) / max(1f, maxX - minX),
            (SZEROKOSC_A4 - 140f) / max(1f, maxY - minY),
        ).coerceAtMost(1.4f)

        plotno.save()
        plotno.translate(40f, 90f)
        plotno.scale(skala, skala)
        plotno.translate(-minX, -minY)

        val pedzelLinii = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.rgb(0x8C, 0x87, 0x7C)
        }
        val poId = mapa.nodes.associateBy { it.id }
        for (linia in mapa.edges) {
            val od = poId[linia.fromId] ?: continue
            val doWezla = poId[linia.toId] ?: continue
            plotno.drawLine(
                od.x + od.width / 2f,
                od.y + od.height / 2f,
                doWezla.x + doWezla.width / 2f,
                doWezla.y + doWezla.height / 2f,
                pedzelLinii,
            )
        }

        val pedzelRamki = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = Color.rgb(0x23, 0x21, 0x1D)
        }
        val pedzelTla = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        for (wezel in mapa.nodes) {
            val promien = if (wezel.shape.name == "OWAL") wezel.height / 2f else 3f
            plotno.drawRoundRect(
                wezel.x, wezel.y, wezel.x + wezel.width, wezel.y + wezel.height,
                promien, promien, pedzelTla,
            )
            plotno.drawRoundRect(
                wezel.x, wezel.y, wezel.x + wezel.width, wezel.y + wezel.height,
                promien, promien, pedzelRamki,
            )
            if (wezel.text.isNotBlank()) {
                rysujTekst(
                    plotno, wezel.text, wezel.x + 8f, wezel.y + 10f,
                    wezel.width - 16f, 10f, Color.BLACK, false,
                )
            }
        }
        plotno.restore()
        pdf.finishPage(strona)
    }

    private fun pustaStrona(pdf: PdfDocument, tytul: String) {
        val strona = pdf.startPage(
            PdfDocument.PageInfo.Builder(SZEROKOSC_A4, WYSOKOSC_A4, 1).create(),
        )
        strona.canvas.drawColor(Color.WHITE)
        rysujTekst(strona.canvas, tytul, 64f, 72f, SZEROKOSC_A4 - 128f, 20f, Color.BLACK, true)
        rysujTekst(
            strona.canvas,
            "Ta notatka jest jeszcze pusta.",
            64f, 116f, SZEROKOSC_A4 - 128f, 11f, Color.DKGRAY, false,
        )
        pdf.finishPage(strona)
    }

    // Rysowanie wspólnych elementów

    private fun tekstowyPedzel(rozmiar: Float, kolor: Int, pogrubienie: Boolean) = TextPaint().apply {
        isAntiAlias = true
        textSize = rozmiar
        color = kolor
        typeface = Typeface.create(Typeface.SANS_SERIF, if (pogrubienie) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun uklad(tekst: String, szerokosc: Float, rozmiar: Float, pogrubienie: Boolean): StaticLayout {
        val pedzel = tekstowyPedzel(rozmiar, Color.BLACK, pogrubienie)
        return StaticLayout.Builder
            .obtain(tekst, 0, tekst.length, pedzel, szerokosc.toInt().coerceAtLeast(1))
            .setLineSpacing(rozmiar * 0.55f, 1f)
            .build()
    }

    private fun wysokoscTekstu(tekst: String, szerokosc: Float, rozmiar: Float, pogrubienie: Boolean): Float =
        uklad(tekst, szerokosc, rozmiar, pogrubienie).height.toFloat()

    /** Tekst zapisany w pliku PDF jako tekst, nie jako obrazek. */
    private fun rysujTekst(
        plotno: Canvas,
        tekst: String,
        x: Float,
        y: Float,
        szerokosc: Float,
        rozmiar: Float,
        kolor: Int,
        pogrubienie: Boolean,
    ) {
        val pedzel = tekstowyPedzel(rozmiar, kolor, pogrubienie)
        val uklad = StaticLayout.Builder
            .obtain(tekst, 0, tekst.length, pedzel, szerokosc.toInt().coerceAtLeast(1))
            .setLineSpacing(rozmiar * 0.55f, 1f)
            .build()
        plotno.save()
        plotno.translate(x, y)
        uklad.draw(plotno)
        plotno.restore()
    }

    private fun numerStrony(plotno: Canvas, numer: Int) {
        val pedzel = tekstowyPedzel(9f, Color.rgb(0x67, 0x63, 0x5A), false)
        plotno.drawText(numer.toString(), SZEROKOSC_A4 / 2f, WYSOKOSC_A4 - 32f, pedzel)
    }

    private fun rysujTlo(plotno: Canvas, tlo: PageBackground, przesuniecie: Float) {
        if (tlo == PageBackground.GLADKIE) return
        val pedzel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.rgb(0xD3, 0xCC, 0xBC)
        }
        when (tlo) {
            PageBackground.LINIE -> {
                var y = 28f
                while (y < WYSOKOSC_A4) {
                    plotno.drawLine(0f, y, SZEROKOSC_A4.toFloat(), y, pedzel)
                    y += 28f
                }
            }
            PageBackground.KRATKA -> {
                var y = 20f
                while (y < WYSOKOSC_A4) {
                    plotno.drawLine(0f, y, SZEROKOSC_A4.toFloat(), y, pedzel)
                    y += 20f
                }
                var x = 20f
                while (x < SZEROKOSC_A4) {
                    plotno.drawLine(x, 0f, x, WYSOKOSC_A4.toFloat(), pedzel)
                    x += 20f
                }
            }
            PageBackground.KROPKI -> {
                pedzel.style = Paint.Style.FILL
                var y = 20f
                while (y < WYSOKOSC_A4) {
                    var x = 20f
                    while (x < SZEROKOSC_A4) {
                        plotno.drawCircle(x, y, 0.8f, pedzel)
                        x += 20f
                    }
                    y += 20f
                }
            }
            PageBackground.PIECIOLINIA -> {
                var y = 60f
                while (y + 36f < WYSOKOSC_A4) {
                    for (i in 0 until 5) {
                        plotno.drawLine(30f, y + i * 9f, SZEROKOSC_A4 - 30f, y + i * 9f, pedzel)
                    }
                    y += 5 * 9f + 46f
                }
            }
            PageBackground.GLADKIE -> Unit
        }
        pedzel.strokeWidth = 0.9f
        plotno.drawLine(60f, 0f, 60f, WYSOKOSC_A4.toFloat(), pedzel)
        if (przesuniecie > 0f) Unit
    }

    /** Jedna strona notatki odręcznej jako obrazek PNG. */
    fun stronaJakoPng(kartka: NotePage, gestosc: Float = 2f): Bitmap {
        val bitmapa = Bitmap.createBitmap(
            (kartka.width * gestosc).toInt().coerceAtLeast(1),
            (kartka.height * gestosc).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val plotno = Canvas(bitmapa)
        plotno.drawColor(Color.WHITE)
        plotno.scale(gestosc, gestosc)
        val rysownik = CanvasStrokeRenderer.create()
        for (kreska in kartka.strokes) {
            val gotowa = runCatching { Kresy.doSilnika(kreska) }.getOrNull() ?: continue
            rysownik.draw(plotno, gotowa, Matrix())
        }
        for (pole in kartka.texts) {
            if (pole.text.isBlank()) continue
            rysujTekst(plotno, pole.text, pole.x, pole.y, pole.width, pole.fontSize, pole.color, pole.bold)
        }
        return bitmapa
    }
}
