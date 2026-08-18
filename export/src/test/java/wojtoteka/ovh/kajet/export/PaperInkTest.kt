package wojtoteka.ovh.kajet.export

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.KajetDarkColors
import wojtoteka.ovh.kajet.core.design.KajetLightColors
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.ink.StrokeCanvas

class PaperInkTest {

    @Test
    fun `kremowy tusz z ciemnej kartki staje sie grafitem`() {
        assertThat(PaperInk.ink(InkPalette.DEFAULT_INK_DARK_ARGB))
            .isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
        assertThat(PaperInk.ink(0xFFE8E4DA.toInt()))
            .isEqualTo(0xFF23211D.toInt())
    }

    @Test
    fun `grafitowy tusz z jasnej kartki zostaje`() {
        assertThat(PaperInk.ink(InkPalette.DEFAULT_INK_LIGHT_ARGB))
            .isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
    }

    @Test
    fun `tekst motywu ciemnego tez idzie na grafit`() {
        assertThat(PaperInk.ink(KajetDarkColors.text.toArgb()))
            .isEqualTo(KajetLightColors.text.toArgb())
    }

    @Test
    fun `niebieski pisak zostaje, bo jest wystarczajaco ciemny`() {
        val blue = 0xFF1B4F8C.toInt()
        assertThat(PaperInk.ink(blue)).isEqualTo(blue)
    }

    @Test
    fun `zakreslacz zostaje jasny, zeby nadal wygladal jak marker`() {
        val yellow = InkPalette.HighlighterYellow.toArgb()
        assertThat(PaperInk.ink(yellow)).isEqualTo(yellow)
        val custom = 0xFFF7E8A0.toInt()
        assertThat(PaperInk.ink(custom, highlighter = true)).isEqualTo(custom)
    }

    @Test
    fun `jasny kolor spoza palety nie znika na bieli`() {
        val cream = 0xFFF5F0E8.toInt()
        assertThat(PaperInk.ink(cream)).isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
    }

    @Test
    fun `plytka bloku kodu z ciemnego biurka robi sie jasna`() {
        assertThat(PaperInk.plate(KajetDarkColors.desk.toArgb()))
            .isEqualTo(KajetLightColors.desk.toArgb())
        assertThat(PaperInk.plate(0)).isEqualTo(0)
    }

    @Test
    fun `kolor folderu z ciemnego motywu wraca do papierowej pary`() {
        val from = FolderColor.Mustard.color(true).toArgb()
        val to = FolderColor.Mustard.color(false).toArgb()
        assertThat(PaperInk.ink(from)).isEqualTo(to)
        assertThat(PaperInk.plate(from)).isEqualTo(to)
    }

    @Test
    fun `alfa tuszu zostaje, zmienia sie tylko barwa`() {
        val creamWash = 0x73E8E4DA
        val mapped = PaperInk.ink(creamWash)
        assertThat(mapped ushr 24).isEqualTo(0x73)
        assertThat(mapped and 0x00FFFFFF).isEqualTo(0x0023211D)
    }

    @Test
    fun `kreska, ksztalt i pole dostaja papierowe barwy`() {
        val stroke = PaperInk.forStroke(
            InkStroke(id = "s", color = InkPalette.DEFAULT_INK_DARK_ARGB, size = 2f),
        )
        assertThat(stroke.color).isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)

        val marker = PaperInk.forStroke(
            InkStroke(
                id = "h",
                tool = InkTool.HIGHLIGHTER,
                color = InkPalette.HighlighterPink.toArgb(),
                size = 16f,
            ),
        )
        assertThat(marker.color).isEqualTo(InkPalette.HighlighterPink.toArgb())

        val shape = PaperInk.forShape(
            ShapeElement(
                id = "k",
                kind = ShapeKind.RECTANGLE,
                x = 0f,
                y = 0f,
                width = 10f,
                height = 10f,
                color = InkPalette.DEFAULT_INK_DARK_ARGB,
                fill = KajetDarkColors.sheet.toArgb(),
            ),
        )
        assertThat(shape.color).isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
        assertThat(shape.fill).isEqualTo(KajetLightColors.sheet.toArgb())

        val field = PaperInk.forField(
            TextBoxElement(
                id = "t",
                x = 0f,
                y = 0f,
                width = 100f,
                height = 40f,
                text = "kod",
                color = KajetDarkColors.text.toArgb(),
                italic = true,
                underline = true,
                font = NoteFont.MONO,
                align = NoteAlign.CENTER,
                background = KajetDarkColors.desk.toArgb(),
            ),
        )
        assertThat(field.color).isEqualTo(KajetLightColors.text.toArgb())
        assertThat(field.background).isEqualTo(KajetLightColors.desk.toArgb())
        assertThat(field.italic).isTrue()
        assertThat(field.underline).isTrue()
        assertThat(field.font).isEqualTo(NoteFont.MONO)
        assertThat(field.align).isEqualTo(NoteAlign.CENTER)
    }

    @Test
    fun `pieciolinia na papierze zaczyna sie jak na ekranie`() {
        assertThat(StrokeCanvas.STAVE_SPACING).isEqualTo(9f)
        assertThat(StrokeCanvas.PAGE_MARGIN).isEqualTo(60f)
        assertThat(PaperInk.luminance(0xE8E4DA)).isGreaterThan(0.7f)
        assertThat(PaperInk.luminance(0x23211D)).isLessThan(0.05f)
    }
}
