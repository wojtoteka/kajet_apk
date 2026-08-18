package wojtoteka.ovh.kajet.ink

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.KajetDarkColors
import wojtoteka.ovh.kajet.core.design.KajetLightColors
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool

class PaperStrokesTest {

    @Test
    fun `kremowy tusz z ciemnej kartki staje sie grafitem`() {
        assertThat(PaperStrokes.ink(InkPalette.DEFAULT_INK_DARK_ARGB))
            .isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
        assertThat(PaperStrokes.ink(0xFFE8E4DA.toInt()))
            .isEqualTo(0xFF23211D.toInt())
    }

    @Test
    fun `grafitowy tusz z jasnej kartki zostaje`() {
        assertThat(PaperStrokes.ink(InkPalette.DEFAULT_INK_LIGHT_ARGB))
            .isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
    }

    @Test
    fun `niebieski pisak zostaje, bo jest wystarczajaco ciemny`() {
        val blue = 0xFF1B4F8C.toInt()
        assertThat(PaperStrokes.ink(blue)).isEqualTo(blue)
    }

    @Test
    fun `zakreslacz zostaje jasny, zeby nadal wygladal jak marker`() {
        val yellow = InkPalette.HighlighterYellow.toArgb()
        assertThat(PaperStrokes.ink(yellow)).isEqualTo(yellow)
        val custom = 0xFFF7E8A0.toInt()
        assertThat(PaperStrokes.ink(custom, highlighter = true)).isEqualTo(custom)
    }

    @Test
    fun `jasny kolor spoza palety nie znika na bieli`() {
        val cream = 0xFFF5F0E8.toInt()
        assertThat(PaperStrokes.ink(cream)).isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
    }

    @Test
    fun `kreska z ciemnego motywu dostaje papierowy tusz`() {
        val stroke = PaperStrokes.of(
            InkStroke(id = "s", color = InkPalette.DEFAULT_INK_DARK_ARGB, size = 2f),
        )
        assertThat(stroke.color).isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB)
    }

    @Test
    fun `alfa tuszu zostaje, zmienia sie tylko barwa`() {
        val creamWash = 0x73E8E4DA
        val mapped = PaperStrokes.ink(creamWash)
        assertThat(mapped ushr 24).isEqualTo(0x73)
        assertThat(mapped and 0x00FFFFFF).isEqualTo(0x0023211D)
    }

    @Test
    fun `kolor folderu z ciemnego motywu wraca do papierowej pary`() {
        val from = FolderColor.Mustard.color(true).toArgb()
        val to = FolderColor.Mustard.color(false).toArgb()
        assertThat(PaperStrokes.ink(from)).isEqualTo(to)
    }

    @Test
    fun `kartka PNG jest nieprzezroczysta biel`() {
        assertThat(PaperStrokes.PAGE).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(PaperStrokes.luminance(0xE8E4DA)).isGreaterThan(0.7f)
        assertThat(PaperStrokes.luminance(0x23211D)).isLessThan(0.05f)
        assertThat(KajetDarkColors.text.toArgb() and 0x00FFFFFF)
            .isEqualTo(InkPalette.DEFAULT_INK_DARK_ARGB and 0x00FFFFFF)
        assertThat(KajetLightColors.text.toArgb() and 0x00FFFFFF)
            .isEqualTo(InkPalette.DEFAULT_INK_LIGHT_ARGB and 0x00FFFFFF)
    }
}
