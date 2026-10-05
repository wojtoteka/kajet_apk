package wojtoteka.ovh.kajet.editor.text

import androidx.compose.animation.core.animateDpAsState
import wojtoteka.ovh.kajet.ink.ShapeSettings
import wojtoteka.ovh.kajet.ink.Brushes
import wojtoteka.ovh.kajet.editor.handwriting.SelectionPanel
import wojtoteka.ovh.kajet.editor.handwriting.PenStrip
import wojtoteka.ovh.kajet.editor.handwriting.PenPanel
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.BoxWithConstraints
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
internal const val DRAWING_HEIGHT = 300f

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
  Kartka przy wstawianiu.

  Do notatki idzie CAŁA kartka, którą widać w oknie - na pełną szerokość
  i co najmniej na wysokość, od której okno zaczyna. Mały rysunek w lewym
  rogu zostaje małym rysunkiem w lewym rogu kartki, a nie wyciętym paskiem
  wokół kreski.

  Przycinane jest tylko miejsce dołożone w trakcie rysowania, które zostało
  puste - gdy kreska, dla której kartka urosła, poszła pod gumkę. Wtedy
  kartka kończy się tuż pod ostatnią kreską.

  Kreska wychodząca poza kartkę - na margines z boku albo nad górną
  krawędzią - nie jest ucinana: ramka rysunku rozszerza się, żeby objąć
  wszystko, co narysowano.
*/
private const val DRAWING_TRIM_MARGIN = 40f
private const val DRAWING_EDGE_MARGIN = 8f

/** Rysunek gotowy do wstawienia: kreski w mierze ramki i sama ramka. */
internal class DrawingFrame(val strokes: List<InkStroke>, val width: Float, val height: Float) {
    companion object {
        /**
         * Ramka dla kresek [strokes] narysowanych na kartce [pageWidth] x
         * [pageHeight]. Wysokość nie schodzi poniżej [startHeight] - tyle
         * kartki widać na starcie okna.
         */
        fun of(
            strokes: List<InkStroke>,
            pageWidth: Float,
            pageHeight: Float,
            startHeight: Float = DRAWING_HEIGHT,
        ): DrawingFrame {
            val bounds = Strokes.bounds(strokes)
                ?: return DrawingFrame(strokes, pageWidth, pageHeight)
            // Kreska ma grubość - sam środek kreski przy krawędzi to pół
            // kreski za krawędzią.
            val pad = (strokes.maxOfOrNull { it.size } ?: 0f) / 2f + DRAWING_EDGE_MARGIN

            val left = minOf(0f, bounds.left - pad)
            val top = minOf(0f, bounds.top - pad)
            val right = maxOf(pageWidth, bounds.right + pad)
            val trimmed = (bounds.bottom + DRAWING_TRIM_MARGIN)
                .coerceIn(minOf(startHeight, pageHeight), pageHeight)
            val bottom = maxOf(trimmed, bounds.bottom + pad)

            val moved = if (left == 0f && top == 0f) {
                strokes
            } else {
                strokes.map { it.translated(-left, -top) }
            }
            return DrawingFrame(moved, right - left, bottom - top)
        }
    }
}

/*
  Czym się rysowało ostatnio.

  Okno rysunku zamyka się po każdym rysunku, a drugi rysunek w tej samej
  notatce zwykle robi się tym samym pisakiem. Bez tej pamięci każde otwarcie
  zaczynało od czarnego długopisu 2,4 pt i trzeba było klikać od nowa.
  Pamięć trwa, póki działa aplikacja - na dłużej od tego są ustawienia pisaka
  w notatce odręcznej.
*/
private object LastDrawingTools {
    var tool: EditorTool = EditorTool.PEN
    var pens: PenSettings? = null
    var fingerDraws: Boolean = true
}

/** Najwięcej kroków cofania w oknie rysunku. */
private const val DRAWING_UNDO_LIMIT = 100

/**
 * Okno rysunku. Bez [initial] zaczyna się od pustej kartki; z [initial] otwiera
 * rysunek, który już stoi w notatce, razem z jego kreskami – wtedy przycisk
 * zapisuje poprawki zamiast wstawiać kolejny rysunek.
 *
 * Narzędzia są te same co w notatce odręcznej - pisak (długopis, cienkopis,
 * ołówek, przerywana), zakreślacz, obie gumki, zaznaczanie, linijka
 * z kształtami, cofanie - i ten sam panel pisaka z własnym kolorem,
 * grubością i kryciem. Kształtów-obiektów nie ma: rysunek w notatce to same
 * kreski, a linijka i tak prostuje kreskę w linię, koło czy prostokąt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingDialog(
    onClose: () -> Unit,
    onDone: (strokes: List<InkStroke>, width: Float, height: Float) -> Unit,
    initial: DrawingSource? = null,
    /** „Twoje kolory" z ustawień - te same co w notatce odręcznej. */
    recentColors: List<Int> = emptyList(),
    /** Dokłada barwę do „twoich kolorów" - po zamknięciu okna z tęczą. */
    onRememberColor: (Int) -> Unit = {},
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var tool by remember { mutableStateOf(LastDrawingTools.tool) }
    var pens by remember {
        mutableStateOf(
            LastDrawingTools.pens ?: PenSettings(
                penColor = colors.defaultInk.toArgb(),
                penWidth = 2f,
                highlighterColor = InkPalette.HighlighterYellow.toArgb(),
            ),
        )
    }
    var fingerDraws by remember { mutableStateOf(LastDrawingTools.fingerDraws) }
    var penPanel by remember { mutableStateOf(false) }

    fun pickTool(next: EditorTool) {
        // Drugie stuknięcie w wybrane już narzędzie otwiera jego ustawienia -
        // tak samo jak w notatce odręcznej.
        if (next == tool && next != EditorTool.LASSO) penPanel = !penPanel
        tool = next
        LastDrawingTools.tool = next
    }

    fun updatePens(transform: (PenSettings) -> PenSettings) {
        pens = transform(pens)
        LastDrawingTools.pens = pens
    }

    // Cofanie. Każdy krok to cały stan kartki sprzed zmiany - kresek w jednym
    // rysunku jest tyle, że kopiowanie listy nic nie kosztuje, a cofnięcie
    // gumki, przesunięcia czy „Wyczyść" działa tak samo jak cofnięcie kreski.
    var undoSteps by remember { mutableStateOf(emptyList<List<InkStroke>>()) }
    var redoSteps by remember { mutableStateOf(emptyList<List<InkStroke>>()) }

    fun record(before: List<InkStroke>) {
        if (before == strokes) return
        undoSteps = (undoSteps + listOf(before)).takeLast(DRAWING_UNDO_LIMIT)
        redoSteps = emptyList()
    }

    // Zaznaczenie lassem i początek trwającego ruchu (gumki albo przesuwania),
    // żeby całe pociągnięcie cofało się jednym krokiem.
    var selected by remember { mutableStateOf(emptyList<InkStroke>()) }
    var gestureStart by remember { mutableStateOf<List<InkStroke>?>(null) }

    fun deselect() {
        selected = emptyList()
    }

    fun undo() {
        val previous = undoSteps.lastOrNull() ?: return
        redoSteps = redoSteps + listOf(strokes)
        undoSteps = undoSteps.dropLast(1)
        strokes = previous
        deselect()
    }

    fun redo() {
        val next = redoSteps.lastOrNull() ?: return
        undoSteps = undoSteps + listOf(strokes)
        redoSteps = redoSteps.dropLast(1)
        strokes = next
        deselect()
    }

    fun deleteSelection() {
        val ids = selected.map { it.id }.toSet()
        val before = strokes
        strokes = strokes.filterNot { it.id in ids }
        record(before)
        deselect()
    }

    // Wysokość kartki. Rośnie razem z rysunkiem i tyle samo trafia do notatki,
    // więc wstawiony rysunek ma dokładnie te proporcje, co pod rysikiem.
    var pageHeight by remember { mutableFloatStateOf(startHeight(initial)) }

    // Szerokość kartki poprawianego rysunku zostaje ta, na której go narysowano
    // - inaczej kreski wjechałyby poza kartkę albo skurczyły się w jej rogu.
    val pageWidth = initial?.width?.takeIf { it > 0f } ?: DRAWING_WIDTH
    var canvas by remember { mutableStateOf<StrokeCanvas?>(null) }

    val narrow = LocalConfiguration.current.screenWidthDp < 600

    // Rysunek w notatce tekstowej to też pisanie rysikiem, więc i tu rysik ma
    // drgać tym, czym się rysuje. Zagnieżdżenia liczy PenHaptics, więc
    // zamknięcie tego okna nie zabiera profilu ekranowi pod spodem.
    val context = LocalContext.current
    DisposableEffect(context) {
        PenHaptics.enter(context)
        onDispose { PenHaptics.leave(context) }
    }
    LaunchedEffect(tool, pens.penKind) {
        PenHaptics.use(context, PenHaptics.profileFor(tool, pens.penKind))
    }
    LaunchedEffect(tool) {
        if (tool != EditorTool.LASSO) deselect()
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
                    // Na tablecie okno jest szersze: kartka rośnie razem z nim,
                    // a panel pisaka mieści się obok kreski, nie na niej.
                    .widthIn(max = if (narrow) 640.dp else 860.dp)
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

                // Narzędzia, cofanie i pisak. Na telefonie pasek się nie
                // mieści, więc przewija się w bok.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        .penQuietSurface(context)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(
                        EditorTool.PEN to KajetIcons.Pen,
                        EditorTool.HIGHLIGHTER to KajetIcons.Highlighter,
                        EditorTool.ERASER_PARTIAL to KajetIcons.Eraser,
                        EditorTool.ERASER_STROKE to KajetIcons.EraserStroke,
                        EditorTool.LASSO to KajetIcons.Lasso,
                        EditorTool.RULER to KajetIcons.Ruler,
                    ).forEach { (option, icon) ->
                        IconAction(icon, option.label(words), { pickTool(option) }, selected = tool == option)
                    }
                    ToolDivider()
                    IconAction(KajetIcons.Undo, words.undo, ::undo, enabled = undoSteps.isNotEmpty())
                    IconAction(KajetIcons.Redo, words.redo, ::redo, enabled = redoSteps.isNotEmpty())
                    ToolDivider()
                    // Kropka barwy otwiera pełne ustawienia narzędzia: rodzaj
                    // pisaka, własny kolor, grubość i krycie.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(onClickLabel = words.penSettings) { penPanel = !penPanel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(
                                    Color(if (tool == EditorTool.HIGHLIGHTER) pens.highlighterColor else pens.penColor),
                                    CircleShape,
                                )
                                .border(1.dp, colors.line, CircleShape),
                        )
                    }
                    IconAction(
                        icon = if (fingerDraws) KajetIcons.FingerDraws else KajetIcons.FingerScrolls,
                        description = if (fingerDraws) words.fingerDrawsSwitch else words.fingerScrollsSwitch,
                        onClick = {
                            fingerDraws = !fingerDraws
                            LastDrawingTools.fingerDraws = fingerDraws
                        },
                        selected = fingerDraws,
                    )
                }
                HorizontalRule()

                // Szybki wybór barwy i grubości bieżącego narzędzia - ten sam
                // pasek co w notatce odręcznej.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .penQuietSurface(context)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tool == EditorTool.LASSO) {
                        Text(tool.description(words), style = Kajet.type.meta, color = colors.muted)
                    } else {
                        PenStrip(
                            tool = tool,
                            pens = pens,
                            shapes = ShapeSettings(color = pens.penColor),
                            onPenColor = { argb -> updatePens { it.copy(penColor = argb) } },
                            onPenWidth = { width -> updatePens { it.copy(penWidth = width) } },
                            onHighlighterColor = { argb -> updatePens { it.copy(highlighterColor = argb) } },
                            onHighlighterWidth = { width -> updatePens { it.copy(highlighterWidth = width) } },
                            onShapeColor = {},
                            onShapeWidth = {},
                            onEraserRadius = { radius -> updatePens { it.copy(eraserRadius = radius) } },
                        )
                    }
                }
                HorizontalRule()

                /*
                  Pudło kartki. Wysokość idzie za proporcją kartki przy
                  szerokości okna, więc dołożone miejsce naprawdę widać,
                  a skala kreski się nie zmienia. Ruch jest płynny: skok
                  o sto dwadzieścia punktów w jednej klatce wygląda jak usterka.

                  `weight(1f, fill = false)` przycina to do tego, co zostało
                  w oknie. Gdy kartka urośnie ponad tę granicę, pudło stoi,
                  a widok zjeżdża do świeżego miejsca.
                */
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    val boxHeight by animateDpAsState(
                        targetValue = maxWidth * (pageHeight / pageWidth),
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
                                            val before = strokes
                                            strokes = strokes + stroke
                                            record(before)
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
                                            if (gestureStart == null) gestureStart = strokes
                                            // Zwykła gumka wyciera tylko to, po czym przejechała:
                                            // z kreski zostają kawałki po obu stronach.
                                            strokes = strokes.flatMap { stroke ->
                                                when {
                                                    !Strokes.hitsCircle(stroke, x, y, radius) -> listOf(stroke)
                                                    wholeStroke -> emptyList()
                                                    else -> Strokes.cutFragment(stroke, x, y, radius)
                                                }
                                            }
                                        }

                                        override fun eraserFinished() {
                                            gestureStart?.let { record(it) }
                                            gestureStart = null
                                        }

                                        override fun lassoFinished(page: Int, polygon: List<Float>) {
                                            selected = strokes.filter { Strokes.inLasso(it, polygon) }
                                        }

                                        override fun selectionMoved(dx: Float, dy: Float, finished: Boolean) {
                                            if (finished) {
                                                gestureStart?.let { record(it) }
                                                gestureStart = null
                                                return
                                            }
                                            if (gestureStart == null) gestureStart = strokes
                                            val ids = selected.map { it.id }.toSet()
                                            strokes = strokes.map { if (it.id in ids) it.translated(dx, dy) else it }
                                            selected = selected.map { it.translated(dx, dy) }
                                        }

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
                                view.settings = pens
                                view.fingerDraws = fingerDraws
                                view.selected = selected
                                view.selectionPage = if (selected.isEmpty()) -1 else 0
                                view.paperColor = colors.sheet.toArgb()
                                view.ruleColor = colors.pageRule.toArgb()
                                view.deskColor = colors.desk.toArgb()
                                view.selectionColor = colors.accent.toArgb()
                            },
                        )

                        if (selected.isNotEmpty()) {
                            SelectionPanel(
                                count = selected.size,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .penQuietSurface(context)
                                    .padding(12.dp),
                                onDelete = ::deleteSelection,
                                onDeselect = ::deselect,
                            )
                        }

                        if (penPanel && tool != EditorTool.LASSO) {
                            PenPanel(
                                tool = tool,
                                pens = pens,
                                recentColors = recentColors,
                                onPenKind = { kind -> updatePens { it.copy(penKind = kind) } },
                                onPenColor = { argb -> updatePens { it.copy(penColor = argb) } },
                                onPenWidth = { width ->
                                    updatePens { it.copy(penWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH)) }
                                },
                                onPenOpacity = { opacity -> updatePens { it.copy(penOpacity = opacity.coerceIn(0.05f, 1f)) } },
                                onHighlighterColor = { argb -> updatePens { it.copy(highlighterColor = argb) } },
                                onHighlighterWidth = { width ->
                                    updatePens {
                                        it.copy(highlighterWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH))
                                    }
                                },
                                onHighlighterOpacity = { opacity ->
                                    updatePens { it.copy(highlighterOpacity = opacity.coerceIn(0.05f, 1f)) }
                                },
                                onEraserRadius = { radius -> updatePens { it.copy(eraserRadius = radius.coerceIn(2f, 80f)) } },
                                onRememberColor = onRememberColor,
                                onClose = { penPanel = false },
                                modifier = if (narrow) {
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp)
                                        .penQuietSurface(context)
                                } else {
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .width(280.dp)
                                        .heightIn(max = (boxHeight - 16.dp).coerceAtLeast(120.dp))
                                        .penQuietSurface(context)
                                },
                            )
                        }
                    }
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
                            onClick = {
                                val frame = DrawingFrame.of(strokes, pageWidth, pageHeight)
                                onDone(frame.strokes, frame.width, frame.height)
                            },
                            icon = KajetIcons.Confirm,
                            enabled = strokes.isNotEmpty(),
                        )
                        // „Wyczyść" da się cofnąć - jest zwykłym krokiem historii.
                        DrawingFooterAction(words.clearDrawing) {
                            val before = strokes
                            strokes = emptyList()
                            record(before)
                            deselect()
                        }
                        DrawingFooterAction(words.close, onClose)
                    }
                    SectionLabel(words.drawWithFingerOrStylus)
                }
            }
        }
    }
}

/** Pionowa kreska między grupami przycisków w pasku narzędzi. */
@Composable
private fun ToolDivider() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Kajet.colors.line),
    )
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
