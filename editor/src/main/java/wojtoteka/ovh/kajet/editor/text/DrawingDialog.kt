package wojtoteka.ovh.kajet.editor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.ColourDot
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.ink.CanvasListener
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.OnScreenPage
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import wojtoteka.ovh.kajet.ink.Strokes

private const val DRAWING_WIDTH = 560f
private const val DRAWING_HEIGHT = 300f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingDialog(
    onClose: () -> Unit,
    onDone: (strokes: List<InkStroke>, width: Float, height: Float) -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var color by remember { mutableStateOf(colors.defaultInk.toArgb()) }
    var width by remember { mutableStateOf(2.4f) }

    // Rysunek w notatce tekstowej to też pisanie rysikiem, więc i tu rysik ma
    // drgać tym, czym się rysuje. Zagnieżdżenia liczy PenHaptics, więc
    // zamknięcie tego okna nie zabiera profilu ekranowi pod spodem.
    val context = LocalContext.current
    DisposableEffect(context) {
        PenHaptics.enter(context)
        onDispose { PenHaptics.leave(context) }
    }
    LaunchedEffect(tool) {
        PenHaptics.use(context, PenHaptics.profileFor(tool, InkTool.PEN))
    }

    /*
      To samo okno co KajetDialog: bez usePlatformDefaultWidth=false zawartość
      liczy odstępy od całego ekranu, a kreska i palec rozjeżdżają się o pasek
      stanu. Dialog rysunku nie może wołać KajetDialog z :app, więc właściwości
      i wcięcia są tu lokalnie.
    */
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .clip(RoundedCornerShape(Kajet.dimens.corner))
                    .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        words.drawingInNote,
                        style = Kajet.type.title,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(KajetIcons.Close, words.closeWithoutSaving, onClose)
                }
                HorizontalRule()

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        // Na telefonie pasek się nie mieści, więc przewija się w bok
                        // zamiast ściskać kropki do zerowej szerokości.
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconAction(
                        KajetIcons.Pen,
                        EditorTool.PEN.label(words),
                        { tool = EditorTool.PEN },
                        selected = tool == EditorTool.PEN,
                    )
                    IconAction(
                        KajetIcons.EraserStroke,
                        EditorTool.ERASER_STROKE.label(words),
                        { tool = EditorTool.ERASER_STROKE },
                        selected = tool == EditorTool.ERASER_STROKE,
                    )
                    Box(Modifier.width(12.dp))
                    InkPalette.pens(colors.isDark, words).forEach { (name, variant) ->
                        ColourDot(
                            color = variant.toArgb(),
                            description = "${words.colourNamed} $name",
                            onClick = { color = variant.toArgb() },
                            selected = color == variant.toArgb(),
                        )
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
                                .clickable(onClickLabel = words.strokeWidth) { width = variant },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size((variant * 2.6f).dp)
                                    .background(Color(color), CircleShape)
                                    // Ciemny tusz na ciemnym biurku znika bez jaśniejszej obwódki.
                                    .border(
                                        1.dp,
                                        if (Color(color).luminance() < 0.25f) colors.muted else colors.line,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
                HorizontalRule()

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp)
                        .height(320.dp)
                        .background(colors.desk),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            StrokeCanvas(context).also { view ->
                                view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                                    val newW = right - left
                                    val newH = bottom - top
                                    val oldW = oldRight - oldLeft
                                    val oldH = oldBottom - oldTop
                                    if (newW <= 0) return@addOnLayoutChangeListener
                                    if (newW == oldW && newH == oldH) return@addOnLayoutChangeListener
                                    if (view.pages.isEmpty()) return@addOnLayoutChangeListener
                                    // StrokeCanvas dopasowuje kartkę tylko raz. Po obrocie
                                    // zostaje zoom z poprzedniej szerokości, więc tu
                                    // dopasowujemy przy każdej zmianie rozmiaru pudła.
                                    view.fitWidth()
                                }
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
                                    override fun imageTapped(page: Int, id: String) = Unit
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
                            view.deskColor = colors.desk.toArgb()
                            view.selectionColor = colors.accent.toArgb()
                        },
                    )
                }
                HorizontalRule()

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Trzy SecondaryButton 48 dp plus etykieta nie mieszczą się
                    // w karcie: Wyczyść i Zamknij są akcjami tekstowymi jak
                    // „Wyczyść" w konsoli, etykieta spada pod przyciski.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrimaryButton(
                            text = words.insertDrawing,
                            onClick = { onDone(strokes, DRAWING_WIDTH, DRAWING_HEIGHT) },
                            icon = KajetIcons.Confirm,
                            enabled = strokes.isNotEmpty(),
                        )
                        DrawingFooterAction(words.clearDrawing) { strokes = emptyList() }
                        DrawingFooterAction(words.close, onClose)
                    }
                    SectionLabel(words.drawWithFingerOrStylus)
                }
            }
        }
    }
}

@Composable
private fun DrawingFooterAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(44.dp)
            .clickable(onClick = onClick, onClickLabel = label, role = Role.Button)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Kajet.type.label, color = Kajet.colors.muted)
    }
}
