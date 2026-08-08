package wojtoteka.ovh.kajet.editor.handwriting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.NoticeBar
import wojtoteka.ovh.kajet.core.design.component.ColourDot
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.ColourPickerDialog
import wojtoteka.ovh.kajet.core.design.component.ToolPanel
import wojtoteka.ovh.kajet.core.design.component.IconToggle
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SettingSlider
import wojtoteka.ovh.kajet.core.design.component.SegmentedChoice
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.editor.SaveState
import wojtoteka.ovh.kajet.editor.SaveIndicator
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.Brushes
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import wojtoteka.ovh.kajet.ink.CanvasListener
import wojtoteka.ovh.kajet.ink.OnScreenPage
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.RecognitionState
import kotlin.math.roundToInt

@Composable
fun HandwritingEditor(
    model: HandwritingViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val document by model.document.collectAsStateWithLifecycle()
    val fingerDraws by model.fingerDraws.collectAsStateWithLifecycle()
    val tool by model.tool.collectAsStateWithLifecycle()
    val pens by model.pens.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val selectionPage by model.selectionPage.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val canUndo by model.canUndo.collectAsStateWithLifecycle()
    val canRedo by model.canRedo.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val editedBox by model.editedBox.collectAsStateWithLifecycle()
    val recognitionState by model.recognitionState.collectAsStateWithLifecycle()
    val suggestions by model.textSuggestions.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
    val recentColors by model.recentColors.collectAsStateWithLifecycle()

    val colors = Kajet.colors

    /*
      Drganie przy pisaniu robi silniczek w rysiku, nie tablet. Systemowa usługa
      Lenovo musi tylko wiedzieć, który przybór naśladować i jak mocno —
      a mówimy jej to przy każdej zmianie narzędzia. Poza notatnikiem
      obowiązuje profil „długopis", więc przy wyjściu nie gasimy niczego,
      tylko wracamy do niego.
    */
    val context = LocalContext.current
    DisposableEffect(context) {
        PenHaptics.enter(context)
        onDispose { PenHaptics.leave(context) }
    }
    LaunchedEffect(tool, pens.penKind) {
        PenHaptics.use(context, PenHaptics.profileFor(tool, pens.penKind))
    }

    var penPanel by remember { mutableStateOf(false) }
    var settingsPanel by remember { mutableStateOf(false) }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }

    val canvas = remember { mutableStateOf<StrokeCanvas?>(null) }

    val goBack = {
        model.saveNow()
        onBack()
    }

    // Zapis przy wyjściu z aplikacji, zanim system uśpi tablet.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.saveNow()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Przycisk wstecz systemu ma wracać do biblioteki, a nie zamykać aplikacji.
    // Najpierw zamyka otwarty panel, bo tego się człowiek spodziewa.
    BackHandler {
        when {
            penPanel || settingsPanel -> {
                penPanel = false
                settingsPanel = false
            }

            selected.isNotEmpty() -> model.deselect()
            editedBox != null -> model.editTextBox(null)
            else -> goBack()
        }
    }

    val handwriting = document?.handwriting

    // Telefon trzymany w pionie dostaje inny układ niż tablet: pasek narzędzi
    // wędruje na dół pod kciuk, powrót do góry, a panele wjeżdżają od dołu
    // na całą szerokość. Wnętrze kartki jest wspólne dla obu układów.
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    val onTextBox = {
        model.addTextBox(
            page = 0,
            x = offsetX + 80f,
            y = offsetY + 80f,
            argb = colors.text.toArgb(),
        )
    }
    val onColorPanel = { penPanel = !penPanel; settingsPanel = false }
    val onSettingsPanel = { settingsPanel = !settingsPanel; penPanel = false }
    val penColor = Color(if (tool == EditorTool.HIGHLIGHTER) pens.highlighterColor else pens.penColor)

    val canvasArea: @Composable BoxScope.() -> Unit = {
        val panelModifier = if (narrow) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 440.dp)
        } else {
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .width(300.dp)
                .heightIn(max = 560.dp)
        }
            if (handwriting != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        StrokeCanvas(context).also { view ->
                            canvas.value = view
                            view.listener = object : CanvasListener {
                                override fun strokeFinished(page: Int, stroke: InkStroke) {
                                    model.addStroke(page, stroke)
                                }

                                override fun eraserPassed(
                                    page: Int,
                                    x: Float,
                                    y: Float,
                                    radius: Float,
                                    wholeStroke: Boolean,
                                ) {
                                    model.erase(page, x, y, radius, wholeStroke)
                                }

                                override fun eraserFinished() {
                                    model.eraseFinished()
                                }

                                override fun lassoFinished(page: Int, polygon: List<Float>) {
                                    model.selectWithLasso(page, polygon)
                                }

                                override fun selectionMoved(dx: Float, dy: Float, finished: Boolean) {
                                    model.moveSelection(dx, dy, finished)
                                }

                                override fun viewChanged(x: Float, y: Float, scale: Float) {
                                    offsetX = x
                                    offsetY = y
                                    zoom = scale
                                }

                                override fun emptyAreaTapped() {
                                    model.editTextBox(null)
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.pages = handwriting.pages.mapIndexed { index, sheet ->
                            OnScreenPage(
                                index = index,
                                width = sheet.width,
                                height = sheet.height,
                                background = sheet.background ?: handwriting.background,
                                strokes = sheet.strokes,
                            )
                        }
                        view.tool = tool
                        view.settings = pens
                        view.fingerDraws = fingerDraws
                        view.selected = selected
                        view.selectionPage = selectionPage
                        view.paperColor = colors.sheet.toArgb()
                        view.ruleColor = colors.pageRule.toArgb()
                        view.deskColor = colors.desk.toArgb()
                        view.selectionColor = colors.accent.toArgb()
                    },
                )

                TextBoxesOnPage(
                    pages = handwriting.pages,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    zoom = zoom,
                    edited = editedBox,
                    onEdit = model::editTextBox,
                    onChange = { page, box, toHistory -> model.updateTextBox(page, box, toHistory) },
                    onDelete = { page, id -> model.removeTextBox(page, id) },
                )
            }

            if (error != null) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                        .border(1.dp, colors.danger, RoundedCornerShape(Kajet.dimens.corner))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.ErrorMark, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Text(error.orEmpty(), style = Kajet.type.body, color = colors.text)
                    SecondaryButton("Rozumiem", model::dismissError)
                }
            }

            if (selected.isNotEmpty()) {
                SelectionPanel(
                    count = selected.size,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    onDelete = model::deleteSelection,
                    onRecognise = model::recognizeSelection,
                    onDeselect = model::deselect,
                )
            }

            when (val state = recognitionState) {
                is RecognitionState.Downloading -> NoticeBar(
                    icon = KajetIcons.RecogniseText,
                    text = state.message,
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                is RecognitionState.Failed -> NoticeBar(
                    icon = KajetIcons.ErrorMark,
                    text = state.message,
                    color = colors.danger,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SecondaryButton("Rozumiem", model::dismissRecognitionState)
                }

                else -> Unit
            }

            if (penPanel) {
                PenPanel(
                    tool = tool,
                    pens = pens,
                    recentColors = recentColors,
                    onPenKind = model::setPenKind,
                    onPenColor = model::setPenColor,
                    onPenWidth = model::setPenWidth,
                    onPenOpacity = model::setPenOpacity,
                    onHighlighterColor = model::setHighlighterColor,
                    onHighlighterWidth = model::setHighlighterWidth,
                    onHighlighterOpacity = model::setHighlighterOpacity,
                    onEraserRadius = model::setEraserRadius,
                    onClose = { penPanel = false },
                    modifier = panelModifier,
                )
            }

            // Pasek formatowania pola tekstowego. Pojawia się dopiero wtedy,
            // kiedy jakieś pole jest zaznaczone, żeby nie zabierał miejsca na pisanie.
            val boxToFormat = editedBox?.let { model.currentTextBox()?.second }
            if (boxToFormat != null) {
                TextBoxFormatBar(
                    box = boxToFormat,
                    recentColors = recentColors,
                    onChange = model::styleCurrentTextBox,
                    onColor = { model.rememberColor(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }

            if (settingsPanel) {
                NoteSettingsPanel(
                    background = handwriting?.background ?: PageBackground.LINED,
                    mode = handwriting?.pageMode ?: PageMode.A4,
                    pageCount = handwriting?.pages?.size ?: 1,
                    fingerDraws = fingerDraws,
                    onBackground = model::setBackground,
                    onMode = model::setPageMode,
                    onFinger = model::toggleFinger,
                    onAddPage = model::addPage,
                    onRemovePage = model::removeLastPage,
                    onFitWidth = { canvas.value?.fitWidth() },
                    onClose = { settingsPanel = false },
                    modifier = panelModifier,
                )
            }
    }

    if (narrow) {
        Column(Modifier.fillMaxSize().background(colors.desk)) {
            TopBar(
                title = document?.title.orEmpty(),
                state = saveState,
                lastSave = lastSave,
                pageCount = handwriting?.pages?.size ?: 0,
                tool = tool,
                pens = pens,
                onTitle = model::setTitle,
                onPenColor = model::setPenColor,
                onPenWidth = model::setPenWidth,
                onHighlighterColor = model::setHighlighterColor,
                onHighlighterWidth = model::setHighlighterWidth,
                onEraserRadius = model::setEraserRadius,
                onMore = onColorPanel,
                moreOpen = penPanel,
                narrow = true,
                onBack = goBack,
            )
            HorizontalRule()
            Box(Modifier.fillMaxWidth().weight(1f), content = canvasArea)
            HorizontalRule()
            NarrowToolRow(
                tool = tool,
                canUndo = canUndo,
                canRedo = canRedo,
                favorite = document?.favorite == true,
                fingerDraws = fingerDraws,
                penColor = penColor,
                onTool = model::selectTool,
                onUndo = model::undo,
                onRedo = model::redo,
                onColor = onColorPanel,
                onSettings = onSettingsPanel,
                onFinger = model::toggleFinger,
                onTextBox = onTextBox,
                onFavorite = model::toggleFavorite,
                onExport = onExport,
            )
        }
    } else {
        Row(Modifier.fillMaxSize().background(colors.desk)) {
            DrawingRail(
                tool = tool,
                canUndo = canUndo,
                canRedo = canRedo,
                favorite = document?.favorite == true,
                fingerDraws = fingerDraws,
                penColor = penColor,
                onTool = model::selectTool,
                onUndo = model::undo,
                onRedo = model::redo,
                onColor = onColorPanel,
                onSettings = onSettingsPanel,
                onFinger = model::toggleFinger,
                onTextBox = onTextBox,
                onFavorite = model::toggleFavorite,
                onExport = onExport,
                onBack = goBack,
            )

            Column(Modifier.fillMaxSize()) {
                // Pasek na górze. Kolor i grubość leżą na wierzchu, bo to jest
                // to, co zmienia się w czasie pisania najczęściej.
                TopBar(
                    title = document?.title.orEmpty(),
                    state = saveState,
                    lastSave = lastSave,
                    pageCount = handwriting?.pages?.size ?: 0,
                    tool = tool,
                    pens = pens,
                    onTitle = model::setTitle,
                    onPenColor = model::setPenColor,
                    onPenWidth = model::setPenWidth,
                    onHighlighterColor = model::setHighlighterColor,
                    onHighlighterWidth = model::setHighlighterWidth,
                    onEraserRadius = model::setEraserRadius,
                    onMore = onColorPanel,
                    moreOpen = penPanel,
                )
                HorizontalRule()
                Box(Modifier.fillMaxSize(), content = canvasArea)
            }
        }
    }

    if (suggestions.isNotEmpty()) {
        SuggestionsDialog(
            suggestions = suggestions,
            onPick = model::acceptRecognition,
            onClose = model::dismissSuggestions,
        )
    }
}

@Composable
private fun DrawingRail(
    tool: EditorTool,
    canUndo: Boolean,
    canRedo: Boolean,
    favorite: Boolean,
    fingerDraws: Boolean,
    penColor: Color,
    onTool: (EditorTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColor: () -> Unit,
    onSettings: () -> Unit,
    onFinger: () -> Unit,
    onTextBox: () -> Unit,
    onFavorite: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .width(Kajet.dimens.railWidth)
            .fillMaxSize()
            .background(Kajet.colors.desk)
            .marginRule(Kajet.colors.line)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconAction(KajetIcons.BackArrow, "Wróć do biblioteki", onBack)
        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Pen, EditorTool.PEN.labelPl, { onTool(EditorTool.PEN) }, selected = tool == EditorTool.PEN)
        IconAction(KajetIcons.Highlighter, EditorTool.HIGHLIGHTER.labelPl, { onTool(EditorTool.HIGHLIGHTER) }, selected = tool == EditorTool.HIGHLIGHTER)
        IconAction(KajetIcons.Eraser, EditorTool.ERASER_PARTIAL.labelPl, { onTool(EditorTool.ERASER_PARTIAL) }, selected = tool == EditorTool.ERASER_PARTIAL)
        IconAction(KajetIcons.EraserStroke, EditorTool.ERASER_STROKE.labelPl, { onTool(EditorTool.ERASER_STROKE) }, selected = tool == EditorTool.ERASER_STROKE)
        IconAction(KajetIcons.Lasso, EditorTool.LASSO.labelPl, { onTool(EditorTool.LASSO) }, selected = tool == EditorTool.LASSO)
        IconAction(KajetIcons.Ruler, EditorTool.RULER.labelPl, { onTool(EditorTool.RULER) }, selected = tool == EditorTool.RULER)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = "Ustawienia pisaka", onClick = onColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(penColor, CircleShape)
                    .border(1.dp, Kajet.colors.line, CircleShape),
            )
        }
        IconAction(KajetIcons.TextBox, "Wstaw pole tekstowe", onTextBox)

        // Przełącznik palca stoi w pasku, a nie tylko w ustawieniach, bo rysik
        // znika z biurka częściej, niż komukolwiek chce się chodzić po menu.
        IconAction(
            icon = if (fingerDraws) KajetIcons.FingerDraws else KajetIcons.FingerScrolls,
            description = if (fingerDraws) {
                "Palec rysuje. Dotknij, żeby palcem przewijać stronę"
            } else {
                "Palec przewija stronę. Dotknij, żeby palcem rysować"
            },
            onClick = onFinger,
            selected = fingerDraws,
        )
        IconAction(KajetIcons.SettingsCog, "Ustawienia notatki: tło, strony, palec", onSettings)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Undo, "Cofnij", onUndo, enabled = canUndo)
        IconAction(KajetIcons.Redo, "Ponów", onRedo, enabled = canRedo)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Favourites, if (favorite) "Usuń z ulubionych" else "Dodaj do ulubionych", onFavorite, selected = favorite)
        IconAction(KajetIcons.Export, "Eksportuj notatkę", onExport)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Dolny pasek narzędzi na telefon. Te same działania co w [DrawingRail],
 * ale w poziomie, pod kciukiem, z przewijaniem w bok.
 */
@Composable
private fun NarrowToolRow(
    tool: EditorTool,
    canUndo: Boolean,
    canRedo: Boolean,
    favorite: Boolean,
    fingerDraws: Boolean,
    penColor: Color,
    onTool: (EditorTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onColor: () -> Unit,
    onSettings: () -> Unit,
    onFinger: () -> Unit,
    onTextBox: () -> Unit,
    onFavorite: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconAction(KajetIcons.Pen, EditorTool.PEN.labelPl, { onTool(EditorTool.PEN) }, selected = tool == EditorTool.PEN)
        IconAction(KajetIcons.Highlighter, EditorTool.HIGHLIGHTER.labelPl, { onTool(EditorTool.HIGHLIGHTER) }, selected = tool == EditorTool.HIGHLIGHTER)
        IconAction(KajetIcons.Eraser, EditorTool.ERASER_PARTIAL.labelPl, { onTool(EditorTool.ERASER_PARTIAL) }, selected = tool == EditorTool.ERASER_PARTIAL)
        IconAction(KajetIcons.EraserStroke, EditorTool.ERASER_STROKE.labelPl, { onTool(EditorTool.ERASER_STROKE) }, selected = tool == EditorTool.ERASER_STROKE)
        IconAction(KajetIcons.Lasso, EditorTool.LASSO.labelPl, { onTool(EditorTool.LASSO) }, selected = tool == EditorTool.LASSO)
        IconAction(KajetIcons.Ruler, EditorTool.RULER.labelPl, { onTool(EditorTool.RULER) }, selected = tool == EditorTool.RULER)

        VerticalDivider()

        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = "Ustawienia pisaka", onClick = onColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(penColor, CircleShape)
                    .border(1.dp, Kajet.colors.line, CircleShape),
            )
        }
        IconAction(KajetIcons.TextBox, "Wstaw pole tekstowe", onTextBox)
        IconAction(
            icon = if (fingerDraws) KajetIcons.FingerDraws else KajetIcons.FingerScrolls,
            description = if (fingerDraws) {
                "Palec rysuje. Dotknij, żeby palcem przewijać stronę"
            } else {
                "Palec przewija stronę. Dotknij, żeby palcem rysować"
            },
            onClick = onFinger,
            selected = fingerDraws,
        )
        IconAction(KajetIcons.SettingsCog, "Ustawienia notatki: tło, strony, palec", onSettings)

        VerticalDivider()

        IconAction(KajetIcons.Undo, "Cofnij", onUndo, enabled = canUndo)
        IconAction(KajetIcons.Redo, "Ponów", onRedo, enabled = canRedo)

        VerticalDivider()

        IconAction(KajetIcons.Favourites, if (favorite) "Usuń z ulubionych" else "Dodaj do ulubionych", onFavorite, selected = favorite)
        IconAction(KajetIcons.Export, "Eksportuj notatkę", onExport)
    }
}

@Composable
private fun TopBar(
    title: String,
    state: SaveState,
    lastSave: Long?,
    pageCount: Int,
    tool: EditorTool,
    pens: PenSettings,
    onTitle: (String) -> Unit,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
    onEraserRadius: (Float) -> Unit,
    onMore: () -> Unit,
    moreOpen: Boolean,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(horizontal = if (narrow) 4.dp else 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 10.dp),
    ) {
        if (onBack != null) {
            IconAction(KajetIcons.BackArrow, "Wróć do biblioteki", onBack)
        }
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            singleLine = true,
            textStyle = Kajet.type.titleSmall.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            // Na wąskim ekranie tytuł nie może zjeść miejsca na kolory.
            modifier = Modifier.widthIn(min = 72.dp, max = if (narrow) 120.dp else 220.dp),
            decorationBox = { field ->
                if (title.isEmpty()) {
                    Text("Bez nazwy", style = Kajet.type.titleSmall, color = Kajet.colors.muted)
                }
                field()
            },
        )
        SaveIndicator(state = state, lastSave = lastSave)
        if (pageCount > 1 && !narrow) {
            Text("$pageCount stron", style = Kajet.type.meta, color = Kajet.colors.muted)
        }

        VerticalDivider()

        // Reszta paska przewija się w bok, żeby na wąskim ekranie nic nie
        // wypadło poza krawędź i żeby tytuł zawsze został na swoim miejscu.
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (tool.isEraser) {
                Text("Gumka", style = Kajet.type.label, color = Kajet.colors.muted)
                Spacer(Modifier.width(6.dp))
                listOf(6f to 8.dp, 12f to 12.dp, 24f to 16.dp, 48f to 20.dp).forEach { (size, dot) ->
                    SizeDot(
                        dot = dot,
                        color = Kajet.colors.muted.toArgb(),
                        picked = pens.eraserRadius == size,
                        description = "Wielkość gumki ${size.roundToInt()}",
                        onClick = { onEraserRadius(size) },
                    )
                }
            } else {
                val highlighting = tool == EditorTool.HIGHLIGHTER
                val palette = if (highlighting) InkPalette.highlighters else InkPalette.pens
                val picked = if (highlighting) pens.highlighterColor else pens.penColor

                palette.forEach { (name, color) ->
                    ColourDot(
                        color = color.toArgb(),
                        description = name,
                        onClick = {
                            if (highlighting) onHighlighterColor(color.toArgb()) else onPenColor(color.toArgb())
                        },
                        selected = picked == color.toArgb(),
                        diameter = 22.dp,
                    )
                }

                Spacer(Modifier.width(6.dp))

                val widths = if (highlighting) {
                    listOf(8f to 8.dp, 16f to 12.dp, 26f to 16.dp, 36f to 20.dp)
                } else {
                    listOf(1f to 6.dp, 2f to 9.dp, 4f to 13.dp, 8f to 18.dp)
                }
                val currentWidth = if (highlighting) pens.highlighterWidth else pens.penWidth
                widths.forEach { (size, dot) ->
                    SizeDot(
                        dot = dot,
                        color = picked,
                        picked = currentWidth == size,
                        description = "Grubość ${"%.1f".format(size)}",
                        onClick = { if (highlighting) onHighlighterWidth(size) else onPenWidth(size) },
                    )
                }
            }

            IconAction(
                icon = KajetIcons.ColorSwatch,
                description = "Więcej ustawień pisaka",
                onClick = onMore,
                selected = moreOpen,
                touchTarget = 44.dp,
            )
        }
    }
}

/** Kropka pokazująca grubość kreski. Wielkość kropki to ta grubość. */
@Composable
private fun SizeDot(
    dot: androidx.compose.ui.unit.Dp,
    color: Int,
    picked: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .background(
                if (picked) Kajet.colors.accentWash else Color.Transparent,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dot)
                .background(Color(color), CircleShape),
        )
    }
}

@Composable
private fun SelectionPanel(
    count: Int,
    modifier: Modifier,
    onDelete: () -> Unit,
    onRecognise: () -> Unit,
    onDeselect: () -> Unit,
) {
    Row(
        modifier
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            // Na wąskim ekranie przyciski nie mieszczą się obok tekstu.
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Zaznaczono $count kresek", style = Kajet.type.label, color = Kajet.colors.text)
        SecondaryButton("Zamień na tekst", onRecognise, icon = KajetIcons.RecogniseText)
        SecondaryButton("Skasuj", onDelete, icon = KajetIcons.Bin, color = Kajet.colors.danger)
        IconAction(KajetIcons.Close, "Odznacz", onDeselect)
    }
}

@Composable
private fun PenPanel(
    tool: EditorTool,
    pens: PenSettings,
    recentColors: List<Int>,
    onPenKind: (InkTool) -> Unit,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onPenOpacity: (Float) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
    onHighlighterOpacity: (Float) -> Unit,
    onEraserRadius: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var fullPicker by remember { mutableStateOf(false) }

    ToolPanel(
        title = when {
            tool.isEraser -> "Gumka"
            tool == EditorTool.HIGHLIGHTER -> "Zakreślacz"
            else -> "Pisak"
        },
        onClose = onClose,
        // Szerokość i wysokość nadaje wywołujący: na telefonie panel zajmuje
        // całą szerokość przy dolnej krawędzi, na tablecie wąski pasek z boku.
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        when {
            tool.isEraser -> {
                SettingSlider(
                    name = "Wielkość gumki",
                    value = pens.eraserRadius,
                    range = 2f..80f,
                    onChange = onEraserRadius,
                    readout = { "${it.roundToInt()} pkt" },
                )
                PenStrokePreview(
                    argb = Kajet.colors.text.toArgb(),
                    width = pens.eraserRadius,
                    opacity = 0.2f,
                )
                Text(tool.descriptionPl, style = Kajet.type.meta, color = Kajet.colors.muted)
            }

            tool == EditorTool.HIGHLIGHTER -> {
                ColourRow(
                    palette = InkPalette.highlighters,
                    picked = pens.highlighterColor,
                    recent = recentColors,
                    onPick = onHighlighterColor,
                    onCustom = { fullPicker = true },
                )
                SettingSlider(
                    name = "Szerokość",
                    value = pens.highlighterWidth,
                    range = 4f..40f,
                    onChange = onHighlighterWidth,
                    readout = { "%.0f pkt".format(it) },
                )
                SettingSlider(
                    name = "Krycie",
                    value = pens.highlighterOpacity,
                    range = 0.1f..1f,
                    onChange = onHighlighterOpacity,
                    readout = { "${(it * 100).roundToInt()} %" },
                )
                PenStrokePreview(
                    argb = pens.highlighterColor,
                    width = pens.highlighterWidth,
                    opacity = pens.highlighterOpacity,
                )
                if (fullPicker) {
                    ColourPickerDialog(
                        title = "Kolor zakreślacza",
                        color = pens.highlighterColor,
                        onChange = onHighlighterColor,
                        onClose = { fullPicker = false },
                        withAlpha = false,
                        presetColors = InkPalette.highlighters,
                        recentColors = recentColors,
                    )
                }
            }

            else -> {
                SectionLabel("Czym piszesz")
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Brushes.penKinds.forEach { kind ->
                        IconAction(
                            icon = penIcon(kind),
                            description = kind.labelPl,
                            onClick = { onPenKind(kind) },
                            selected = pens.penKind == kind,
                        )
                    }
                }

                ColourRow(
                    palette = InkPalette.pens,
                    picked = pens.penColor,
                    recent = recentColors,
                    onPick = onPenColor,
                    onCustom = { fullPicker = true },
                )

                SettingSlider(
                    name = "Grubość",
                    value = pens.penWidth,
                    range = 0.4f..20f,
                    onChange = onPenWidth,
                    readout = { "%.1f pkt".format(it) },
                )
                SettingSlider(
                    name = "Krycie",
                    value = pens.penOpacity,
                    range = 0.1f..1f,
                    onChange = onPenOpacity,
                    readout = { "${(it * 100).roundToInt()} %" },
                )

                PenStrokePreview(
                    argb = pens.penColor,
                    width = pens.penWidth,
                    opacity = pens.penOpacity,
                )

                Text(
                    text = when (pens.penKind) {
                        InkTool.PEN -> "Kreska grubieje tam, gdzie mocniej naciskasz rysikiem."
                        InkTool.FINELINER -> "Równa kreska o stałej szerokości."
                        InkTool.PENCIL -> "Kreska z ziarnem, jak ołówek na papierze."
                        InkTool.DASHED -> "Linia przerywana, do podziałów i szkiców."
                        InkTool.HIGHLIGHTER -> ""
                    },
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )

                if (fullPicker) {
                    ColourPickerDialog(
                        title = "Kolor atramentu",
                        color = pens.penColor,
                        onChange = onPenColor,
                        onClose = { fullPicker = false },
                        withAlpha = false,
                        presetColors = InkPalette.pens,
                        recentColors = recentColors,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextBoxFormatBar(
    box: TextBoxElement,
    recentColors: List<Int>,
    onChange: ((TextBoxElement) -> TextBoxElement) -> Unit,
    onColor: (Int) -> Unit,
    modifier: Modifier,
) {
    var textColourPicker by remember { mutableStateOf(false) }
    var backgroundPicker by remember { mutableStateOf(false) }
    var fontPicker by remember { mutableStateOf(false) }

    Column(
        modifier
            .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconAction(
                icon = KajetIcons.Letters,
                description = "Krój pisma: ${box.font.labelPl}",
                onClick = { fontPicker = !fontPicker },
                selected = fontPicker,
            )

            IconAction(KajetIcons.MoreDots, "Mniejsze pismo", {
                onChange { it.copy(fontSize = (it.fontSize - 2f).coerceAtLeast(6f)) }
            })
            Text(
                text = "${box.fontSize.roundToInt()}",
                style = Kajet.type.label,
                color = Kajet.colors.text,
                modifier = Modifier.width(28.dp),
            )
            IconAction(KajetIcons.Plus, "Większe pismo", {
                onChange { it.copy(fontSize = (it.fontSize + 2f).coerceAtMost(96f)) }
            })

            VerticalDivider()

            IconToggle(
                icon = KajetIcons.Bold,
                description = "Pogrubienie",
                checked = box.bold,
                onCheckedChange = { on -> onChange { it.copy(bold = on) } },
            )
            IconToggle(
                icon = KajetIcons.Italic,
                description = "Kursywa",
                checked = box.italic,
                onCheckedChange = { on -> onChange { it.copy(italic = on) } },
            )
            IconToggle(
                icon = KajetIcons.Underline,
                description = "Podkreślenie",
                checked = box.underline,
                onCheckedChange = { on -> onChange { it.copy(underline = on) } },
            )

            VerticalDivider()

            NoteAlign.entries.forEach { align ->
                IconAction(
                    icon = when (align) {
                        NoteAlign.LEFT -> KajetIcons.AlignLeft
                        NoteAlign.CENTER -> KajetIcons.AlignCentre
                        NoteAlign.RIGHT -> KajetIcons.AlignRight
                    },
                    description = align.labelPl,
                    onClick = { onChange { it.copy(align = align) } },
                    selected = box.align == align,
                )
            }

            VerticalDivider()

            ColourDot(
                color = box.color,
                description = "Kolor pisma",
                onClick = { textColourPicker = true },
            )
            IconAction(
                icon = KajetIcons.PageRuling,
                description = if (box.background == 0) "Dodaj tło pola" else "Zmień tło pola",
                onClick = { backgroundPicker = true },
                selected = box.background != 0,
            )
        }

        if (fontPicker) {
            SegmentedChoice(
                options = NoteFont.entries,
                selected = box.font,
                name = { it.labelPl },
                onSelect = { font ->
                    onChange { it.copy(font = font) }
                    fontPicker = false
                },
            )
        }
    }

    if (textColourPicker) {
        ColourPickerDialog(
            title = "Kolor pisma",
            color = box.color,
            onChange = { argb ->
                onChange { it.copy(color = argb) }
                onColor(argb)
            },
            onClose = { textColourPicker = false },
            presetColors = InkPalette.pens,
            recentColors = recentColors,
        )
    }

    if (backgroundPicker) {
        ColourPickerDialog(
            title = "Tło pola tekstowego",
            color = if (box.background == 0) 0x33FFFFFF else box.background,
            onChange = { argb ->
                onChange { it.copy(background = argb) }
                onColor(argb)
            },
            onClose = { backgroundPicker = false },
            presetColors = InkPalette.highlighters,
            recentColors = recentColors,
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Kajet.colors.line),
    )
}

private fun penIcon(kind: InkTool) = when (kind) {
    InkTool.PEN -> KajetIcons.Pen
    InkTool.FINELINER -> KajetIcons.Fineliner
    InkTool.PENCIL -> KajetIcons.Pencil
    InkTool.DASHED -> KajetIcons.DashedLine
    InkTool.HIGHLIGHTER -> KajetIcons.Highlighter
}

@Composable
private fun ColourRow(
    palette: List<Pair<String, Color>>,
    picked: Int,
    recent: List<Int>,
    onPick: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    SectionLabel("Kolor")
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palette.forEach { (name, color) ->
            ColourDot(
                color = color.toArgb(),
                description = name,
                onClick = { onPick(color.toArgb()) },
                selected = picked == color.toArgb(),
                diameter = 22.dp,
            )
        }
        recent.filterNot { own -> palette.any { it.second.toArgb() == own } }
            .take(6)
            .forEach { own ->
                ColourDot(
                    color = own,
                    description = "Twój kolor",
                    onClick = { onPick(own) },
                    selected = picked == own,
                    diameter = 22.dp,
                )
            }
        IconAction(KajetIcons.ColorSwatch, "Dobierz własny kolor", onCustom, touchTarget = 44.dp)
    }
}

@Composable
private fun PenStrokePreview(argb: Int, width: Float, opacity: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner)),
    ) {
        val path = Path()
        val margin = 16f
        val middle = size.height / 2f
        path.moveTo(margin, middle + 10f)
        path.cubicTo(
            size.width * 0.3f, middle - 22f,
            size.width * 0.6f, middle + 22f,
            size.width - margin, middle - 8f,
        )
        drawPath(
            path = path,
            color = Color(argb).copy(alpha = opacity),
            style = DrawStroke(width = width.coerceIn(0.5f, 40f), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun NoteSettingsPanel(
    background: PageBackground,
    mode: PageMode,
    pageCount: Int,
    fingerDraws: Boolean,
    onBackground: (PageBackground) -> Unit,
    onMode: (PageMode) -> Unit,
    onFinger: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onFitWidth: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    ToolPanel(
        title = "Ustawienia notatki",
        onClose = onClose,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SectionLabel("Tło strony")
        PageBackground.entries.forEach { variant ->
            ChoiceRow(
                label = variant.labelPl,
                picked = variant == background,
                onClick = { onBackground(variant) },
            )
        }

        HorizontalRule()

        SectionLabel("Rodzaj strony")
        PageMode.entries.forEach { variant ->
            ChoiceRow(
                label = variant.labelPl,
                picked = variant == mode,
                onClick = { onMode(variant) },
            )
        }
        Text(
            text = if (mode == PageMode.A4) {
                "Osobne kartki A4. Tak samo wyjdzie na drukarce."
            } else {
                "Jedna strona, która rośnie w dół, kiedy dopiszesz przy dolnej krawędzi."
            },
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )

        HorizontalRule()

        SectionLabel("Strony")
        Text(
            text = if (pageCount == 1) "Notatka ma jedną stronę." else "Notatka ma $pageCount stron.",
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Dołóż", onAddPage, icon = KajetIcons.AddPage)
            if (pageCount > 1) {
                SecondaryButton(
                    text = "Skasuj ostatnią",
                    onClick = onRemovePage,
                    icon = KajetIcons.Bin,
                    color = Kajet.colors.danger,
                )
            }
        }

        HorizontalRule()

        SectionLabel("Palec")
        ChoiceRow(
            label = "Palec też rysuje",
            picked = fingerDraws,
            onClick = { if (!fingerDraws) onFinger() },
        )
        ChoiceRow(
            label = "Palec przewija stronę",
            picked = !fingerDraws,
            onClick = { if (fingerDraws) onFinger() },
        )
        Text(
            text = "Kiedy rysik dotyka ekranu, dłoń nie rysuje nigdy. To działa zawsze.",
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )

        HorizontalRule()
        SecondaryButton("Dopasuj szerokość", onFitWidth, icon = KajetIcons.FitToView)
    }
}

@Composable
private fun ChoiceRow(label: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(
                if (picked) Kajet.colors.accentWash else Color.Transparent,
                RoundedCornerShape(Kajet.dimens.corner),
            )
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Kajet.type.body,
            color = if (picked) Kajet.colors.accent else Kajet.colors.text,
        )
    }
}

@Composable
private fun SuggestionsDialog(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .width(460.dp)
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Odczytane pismo", style = Kajet.type.title, color = Kajet.colors.text)
            Text(
                text = "Wybierz zapis, który pasuje. Pismo zostanie na kartce, a tekst posłuży do wyszukiwania.",
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion,
                    style = Kajet.type.bodyLarge,
                    color = Kajet.colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Kajet.colors.desk, RoundedCornerShape(Kajet.dimens.corner))
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            SecondaryButton("Anuluj", onClose)
        }
    }
}
