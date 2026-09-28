package wojtoteka.ovh.kajet.editor.text

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableFloatStateOf
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
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.editor.penQuietSurface
import wojtoteka.ovh.kajet.ink.CanvasListener
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.OnScreenPage
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import wojtoteka.ovh.kajet.ink.Strokes

private const val DRAWING_WIDTH = 560f
private const val DRAWING_HEIGHT = 300f

/** Wysokość pudła kartki przy [DRAWING_HEIGHT]. Z tej pary bierze się skala. */
private val DRAWING_BOX_HEIGHT = 320.dp

/*
  Rosnąca kartka.

  Kreska postawiona blisko dolnej krawędzi znaczy „miejsce się kończy", więc
  kartka dostaje kolejny kawałek. Rośnie DOPIERO po oderwaniu rysika - w
  trakcie kreski podłoga uciekałaby spod ręki. Raz dołożone miejsce zostaje:
  gumka ani cofnięcie ostatniej kreski nie zabierają go z powrotem, bo pole,
  które kurczy się samo, jest nie do trafienia rysikiem.

  Górna granica to trzy kartki. Wyżej okno przestaje być oknem rysunku
  w notatce, a robi się notatką odręczną - od tego jest osobny rodzaj notatki.
*/
private const val DRAWING_GROW_MARGIN = 56f
private const val DRAWING_GROW_STEP = 120f
private const val DRAWING_MAX_HEIGHT = DRAWING_HEIGHT * 3

/*
  Kartka przycięta przy wstawianiu.

  Miejsce dołożone w trakcie rysowania zostaje puste, gdy kreska, dla której
  urosło, pójdzie pod gumkę. Zamiast kurczyć kartkę pod rysikiem - czego nie
  da się trafić - liczymy to raz, na „Wstaw rysunek": kartka kończy się tuż
  pod ostatnią kreską, więc w notatce nie ma pustego pasa. Odstęp jest ciut
  mniejszy od tego, przy którym kartka rośnie, żeby przycięcie nie wyglądało
  jak ucięta kreska.

  Dół i tylko dół: przycięcie od góry przesuwałoby rysunek względem tego, co
  było widać pod rysikiem. Najniższa kartka to jeden krok wzrostu - z samej
  kropki nie robimy paska o wysokości kropki.
*/
private const val DRAWING_TRIM_MARGIN = 40f
private const val DRAWING_MIN_HEIGHT = DRAWING_GROW_STEP

/**
 * Okno rysunku. Bez [initial] zaczyna się od pustej kartki; z [initial] otwiera
 * rysunek, który już stoi w notatce, razem z jego kreskami – wtedy przycisk
 * zapisuje poprawki zamiast wstawiać kolejny rysunek.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingDialog(
    onClose: () -> Unit,
    onDone: (strokes: List<InkStroke>, width: Float, height: Float) -> Unit,
    initial: DrawingSource? = null,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var color by remember { mutableStateOf(colors.defaultInk.toArgb()) }
    var width by remember { mutableStateOf(2.4f) }

    // Wysokość kartki. Rośnie razem z rysunkiem i tyle samo trafia do notatki,
    // więc wstawiony rysunek ma dokładnie te proporcje, co pod rysikiem.
    var pageHeight by remember { mutableFloatStateOf(startHeight(initial)) }

    // Szerokość kartki poprawianego rysunku zostaje ta, na której go narysowano
    // - inaczej kreski wjechałyby poza kartkę albo skurczyły się w jej rogu.
    val pageWidth = initial?.width?.takeIf { it > 0f } ?: DRAWING_WIDTH
    var canvas by remember { mutableStateOf<StrokeCanvas?>(null) }

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
    /*
      Rysunek zamyka się tylko przyciskiem: „Wstaw rysunek", „Zamknij" albo
      krzyżykiem w nagłówku. Dotknięcie obok kartki czy gest wstecz nie robią
      nic - dłoń oparta o ekran przy rysowaniu zamykała okno razem z rysunkiem.
    */
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tło łapie stuknięcia, żeby nie przechodziły pod okno.
                .pointerInput(Unit) { detectTapGestures { } }
                // Rysik ma drgać nad kartką, a nie nad całym oknem. Wjazd nad
                // okno wycisza go z góry; kartka zgłosi się zaraz potem sama.
                .penQuietSurface(context)
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
                        .penQuietSurface(context)
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (initial == null) words.drawingInNote else words.editDrawing,
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
                        .penQuietSurface(context)
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
                                // Bez podświetlenia dotyku: po rysiku zostawał
                                // szary kwadrat, nie do odróżnienia od wybranej
                                // grubości.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = words.strokeWidth,
                                ) { width = variant },
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

                /*
                  Pudło kartki. Wysokość idzie za wysokością kartki, więc
                  dołożone miejsce naprawdę widać, a skala kreski się nie
                  zmienia. Ruch jest płynny: skok o sto dwadzieścia punktów
                  w jednej klatce wygląda jak usterka.

                  `weight(1f, fill = false)` przycina to do tego, co zostało
                  w oknie. Gdy kartka urośnie ponad tę granicę, pudło stoi,
                  a widok zjeżdża do świeżego miejsca.
                */
                val boxHeight by animateDpAsState(
                    targetValue = DRAWING_BOX_HEIGHT * (pageHeight / DRAWING_HEIGHT),
                    // Dopiero gdy pudło stanie na swoim, wiadomo, czy kartka
                    // się w nim zmieściła. Jeśli nie - widok zjeżdża do
                    // świeżego miejsca. `post` czeka na nowy rozmiar widoku,
                    // bo bez tego liczylibyśmy z poprzedniego.
                    finishedListener = { canvas?.post { canvas?.showPageBottom(pageHeight) } },
                    label = "wysokosc kartki",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp)
                        .height(boxHeight)
                        .background(colors.desk),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            StrokeCanvas(context).also { view ->
                                canvas = view
                                view.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                                    val newW = right - left
                                    val oldW = oldRight - oldLeft
                                    if (newW <= 0) return@addOnLayoutChangeListener
                                    if (newW == oldW) return@addOnLayoutChangeListener
                                    if (view.pages.isEmpty()) return@addOnLayoutChangeListener
                                    // StrokeCanvas dopasowuje kartkę tylko raz. Po obrocie
                                    // zostaje zoom z poprzedniej szerokości, więc tu
                                    // dopasowujemy przy każdej zmianie SZEROKOŚCI pudła.
                                    // Sama zmiana wysokości niczego nie dopasowuje:
                                    // fitWidth() cofa widok na sam początek kartki,
                                    // a przy rosnącej kartce właśnie tego nie chcemy.
                                    view.fitWidth()
                                }
                                view.listener = object : CanvasListener {
                                    override fun strokeFinished(page: Int, stroke: InkStroke) {
                                        strokes = strokes + stroke
                                        // Kreska sięgnęła dołu kartki: miejsce się
                                        // kończy, więc dokładamy następny kawałek.
                                        if (stroke.bounds().bottom > pageHeight - DRAWING_GROW_MARGIN) {
                                            pageHeight = (pageHeight + DRAWING_GROW_STEP)
                                                .coerceAtMost(DRAWING_MAX_HEIGHT)
                                        }
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
                        onRelease = { canvas = null },
                        update = { view ->
                            view.pages = listOf(
                                OnScreenPage(
                                    index = 0,
                                    width = pageWidth,
                                    height = pageHeight,
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
                        .penQuietSurface(context)
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
                            text = if (initial == null) words.insertDrawing else words.saveDrawingChanges,
                            onClick = { onDone(strokes, pageWidth, trimmedHeight(strokes, pageHeight)) },
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

/**
 * Wysokość kartki na starcie. Pusta kartka zaczyna od [DRAWING_HEIGHT];
 * poprawiany rysunek dostaje co najmniej tyle samo, nawet gdy przy wstawianiu
 * przycięło go do niższego paska - inaczej nie byłoby gdzie dorysować, a sama
 * kartka rośnie dopiero pod kreską przy dolnej krawędzi.
 */
private fun startHeight(initial: DrawingSource?): Float {
    val stored = initial?.height ?: return DRAWING_HEIGHT
    return stored.coerceIn(DRAWING_HEIGHT, DRAWING_MAX_HEIGHT)
}

/**
 * Wysokość kartki obcięta do tego, co na niej stoi: dolna krawędź kresek plus
 * [DRAWING_TRIM_MARGIN]. Nigdy nie dokłada miejsca - najwyżej oddaje to, które
 * zostało puste.
 */
private fun trimmedHeight(strokes: List<InkStroke>, pageHeight: Float): Float {
    val bottom = Strokes.bounds(strokes)?.bottom ?: return pageHeight
    return (bottom + DRAWING_TRIM_MARGIN)
        .coerceIn(DRAWING_MIN_HEIGHT, pageHeight)
}

/**
 * Zjeżdża do dolnej krawędzi kartki. Dopóki cała kartka mieści się w pudle,
 * nie robi nic - widok zostaje tam, gdzie go zostawiono.
 */
private fun StrokeCanvas.showPageBottom(pageHeight: Float) {
    if (height <= 0 || zoom <= 0f) return
    val target = (pageHeight - height / zoom).coerceAtLeast(0f)
    if (target != offsetY) setView(offsetX, target, zoom)
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
