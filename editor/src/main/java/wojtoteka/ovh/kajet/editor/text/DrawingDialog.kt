package wojtoteka.ovh.kajet.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import wojtoteka.ovh.kajet.ink.CanvasListener
import wojtoteka.ovh.kajet.ink.OnScreenPage
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.Strokes

private const val DRAWING_WIDTH = 560f
private const val DRAWING_HEIGHT = 300f

@Composable
fun DrawingDialog(
    onClose: () -> Unit,
    onDone: (strokes: List<InkStroke>, width: Float, height: Float) -> Unit,
) {
    val colors = Kajet.colors
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var color by remember { mutableStateOf(colors.defaultInk.toArgb()) }
    var width by remember { mutableStateOf(2.4f) }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .width(640.dp)
                .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Rysunek w notatce", style = Kajet.type.title, color = colors.text, modifier = Modifier.weight(1f))
                IconAction(KajetIcons.Close, "Zamknij bez zapisywania", onClose)
            }
            HorizontalRule()

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.desk)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconAction(
                    KajetIcons.Pen,
                    "Pióro",
                    { tool = EditorTool.PEN },
                    selected = tool == EditorTool.PEN,
                )
                IconAction(
                    KajetIcons.EraserStroke,
                    "Gumka do całej kreski",
                    { tool = EditorTool.ERASER_STROKE },
                    selected = tool == EditorTool.ERASER_STROKE,
                )
                Box(Modifier.width(12.dp))
                InkPalette.pens.take(4).forEach { (name, variant) ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickable(onClickLabel = "Kolor $name") { color = variant.toArgb() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (color == variant.toArgb()) 24.dp else 18.dp)
                                .background(variant, CircleShape)
                                .border(1.dp, colors.line, CircleShape),
                        )
                    }
                }
                Box(Modifier.width(12.dp))
                listOf(1.6f, 2.4f, 4f, 7f).forEach { variant ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                if (width == variant) colors.accentWash else Color.Transparent,
                                RoundedCornerShape(Kajet.dimens.corner),
                            )
                            .clickable(onClickLabel = "Grubość kreski") { width = variant },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((variant * 2.6f).dp)
                                .background(Color(color), CircleShape),
                        )
                    }
                }
            }
            HorizontalRule()

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(colors.sheet),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    factory = { context ->
                        StrokeCanvas(context).also { view ->
                            view.listener = object : CanvasListener {
                                override fun strokeFinished(page: Int, stroke: InkStroke) {
                                    strokes = strokes + stroke
                                }

                                override fun eraserPassed(
                                    page: Int,
                                    x: Float,
                                    y: Float,
                                    radius: Float,
                                    wholeStroke: Boolean,
                                ) {
                                    strokes = strokes.filterNot {
                                        Strokes.hitsCircle(it, x, y, radius)
                                    }
                                }

                                override fun eraserFinished() = Unit
                                override fun lassoFinished(page: Int, polygon: List<Float>) = Unit
                                override fun selectionMoved(dx: Float, dy: Float, finished: Boolean) = Unit
                                override fun viewChanged(x: Float, y: Float, scale: Float) = Unit
                                override fun emptyAreaTapped() = Unit
                            }
                        }
                    },
                    update = { view ->
                        view.pages = listOf(
                            OnScreenPage(
                                index = 0,
                                width = DRAWING_WIDTH,
                                height = DRAWING_HEIGHT,
                                background = PageBackground.PLAIN,
                                strokes = strokes,
                            ),
                        )
                        view.tool = tool
                        view.settings = PenSettings(
                            penColor = color,
                            penWidth = width,
                            highlighterColor = InkPalette.HighlighterYellow.toArgb(),
                        )
                        view.fingerDraws = true
                        view.paperColor = colors.sheet.toArgb()
                        view.ruleColor = colors.pageRule.toArgb()
                        view.deskColor = colors.sheet.toArgb()
                        view.selectionColor = colors.accent.toArgb()
                    },
                )
            }
            HorizontalRule()

            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = "Wstaw rysunek",
                    onClick = { onDone(strokes, DRAWING_WIDTH, DRAWING_HEIGHT) },
                    icon = KajetIcons.Confirm,
                    enabled = strokes.isNotEmpty(),
                )
                SecondaryButton("Wyczyść", { strokes = emptyList() })
                // Zamknięcie stoi obok pozostałych przycisków, a nie tylko
                // krzyżykiem w rogu, bo tam się go nie szuka.
                SecondaryButton("Zamknij", onClose, icon = KajetIcons.Close)
                Box(Modifier.weight(1f))
                SectionLabel("Rysuj palcem albo rysikiem")
            }
        }
    }
}
