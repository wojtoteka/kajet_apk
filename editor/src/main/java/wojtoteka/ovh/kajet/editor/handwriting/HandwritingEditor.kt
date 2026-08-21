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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
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
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.storage.FingerBehavior
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.eraserSizeOf
import wojtoteka.ovh.kajet.core.text.pagesCount
import wojtoteka.ovh.kajet.core.text.pagesShort
import wojtoteka.ovh.kajet.core.text.percentOf
import wojtoteka.ovh.kajet.core.text.pointsOf
import wojtoteka.ovh.kajet.core.text.selectedStrokes
import wojtoteka.ovh.kajet.core.text.strokeWidthOf
import wojtoteka.ovh.kajet.editor.SaveState
import wojtoteka.ovh.kajet.editor.SaveIndicator
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.Brushes
import wojtoteka.ovh.kajet.ink.StrokeCanvas
import wojtoteka.ovh.kajet.ink.CanvasListener
import wojtoteka.ovh.kajet.ink.OnScreenPage
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.ShapeSettings
import kotlin.math.roundToInt

@Composable
fun HandwritingEditor(
    model: HandwritingViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPhotoFromGallery: () -> Unit,
    onPhotoFromCamera: () -> Unit,
) {
    val words = LocalStrings.current
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
    val editedImage by model.editedImage.collectAsStateWithLifecycle()
    val imageBitmaps by model.imageBitmaps.collectAsStateWithLifecycle()
    val toolbarOnRight by model.toolbarOnRight.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
    val inCloud by model.inCloud.collectAsStateWithLifecycle()
    val recentColors by model.recentColors.collectAsStateWithLifecycle()
    val shapes by model.shapeSettings.collectAsStateWithLifecycle()
    val selectedShape by model.selectedShape.collectAsStateWithLifecycle()
    val squareShapes by model.squareShapes.collectAsStateWithLifecycle()

    val colors = Kajet.colors

    /*
      Drganie przy pisaniu robi silniczek w rysiku, nie tablet. Systemowa usługa
      Lenovo musi tylko wiedzieć, który przybór naśladować i jak mocno -
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

    /*
      Zapamiętane na wypadek śmierci procesu.

      System ubija Kajet zwinięty do tła, kiedy pamięć jest potrzebna czemu
      innemu. Po powrocie kartka skakała na samą górę i do dopasowania do
      szerokości - a przy dłuższej notatce znaczyło to szukanie od nowa
      miejsca, w którym się pisało. To samo z otwartym panelem pisaka.
    */
    var penPanel by rememberSaveable { mutableStateOf(false) }
    var settingsPanel by rememberSaveable { mutableStateOf(false) }

    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }

    // Czy powyższe trójka to naprawdę zapamiętane położenie, czy jeszcze
    // wartości startowe. Bez tego nie da się odróżnić kartki wracającej z
    // zapisanego stanu od świeżo otwartej, której należy się dopasowanie do
    // szerokości ekranu.
    var viewRemembered by rememberSaveable { mutableStateOf(false) }

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
            selectedShape != null -> model.selectShape(null)
            editedBox != null -> model.editTextBox(null)
            editedImage != null -> model.editImage(null)
            else -> goBack()
        }
    }

    val handwriting = document?.handwriting

    // Telefon trzymany w pionie dostaje inny układ niż tablet: pasek narzędzi
    // wędruje na dół pod kciuk, powrót do góry, a panele wjeżdżają od dołu
    // na całą szerokość. Wnętrze kartki jest wspólne dla obu układów.
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    /*
     * Miejsce na nową rzecz: kartka i punkt w JEJ współrzędnych, tam gdzie
     * człowiek właśnie patrzy. Wcześniej wszystko szło na stronę 0 ze
     * współrzędną całego dokumentu - na ekranie wyglądało dobrze, ale w
     * pliku (i w PDF) pole lądowało kilometr pod pierwszą kartką.
     */
    val visibleSpot: () -> Triple<Int, Float, Float> = spot@{
        val pages = handwriting?.pages ?: return@spot Triple(0, 60f, 60f)
        val docY = offsetY + 100f
        var top = 0f
        pages.forEachIndexed { index, sheet ->
            val bottom = top + sheet.height
            if (docY < bottom + StrokeCanvas.PAGE_GAP / 2f) {
                return@spot Triple(
                    index,
                    (offsetX + 80f).coerceIn(0f, (sheet.width - 240f).coerceAtLeast(0f)),
                    (docY - top).coerceIn(0f, (sheet.height - 80f).coerceAtLeast(0f)),
                )
            }
            top += sheet.height + StrokeCanvas.PAGE_GAP
        }
        val last = pages.last()
        val lastTop = top - (last.height + StrokeCanvas.PAGE_GAP)
        Triple(
            pages.lastIndex,
            60f,
            (docY - lastTop).coerceIn(0f, (last.height - 80f).coerceAtLeast(0f)),
        )
    }

    val onTextBox = {
        val (page, x, y) = visibleSpot()
        model.addTextBox(page = page, x = x, y = y, argb = colors.text.toArgb())
    }
    val onCodeBox = {
        val (page, x, y) = visibleSpot()
        model.addCodeBox(
            page = page,
            x = x,
            y = y,
            argb = colors.text.toArgb(),
            background = colors.desk.toArgb(),
        )
    }
    val onPhotoGallery = {
        val (page, x, y) = visibleSpot()
        model.rememberPhotoTarget(page, x, y)
        onPhotoFromGallery()
    }
    val onPhotoCamera = {
        val (page, x, y) = visibleSpot()
        model.rememberPhotoTarget(page, x, y)
        onPhotoFromCamera()
    }
    val onColorPanel = { penPanel = !penPanel; settingsPanel = false }
    val onSettingsPanel = { settingsPanel = !settingsPanel; penPanel = false }
    val penColor = Color(
        when (tool) {
            EditorTool.HIGHLIGHTER -> pens.highlighterColor
            EditorTool.SHAPES -> shapes.color
            else -> pens.penColor
        },
    )

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
                                    viewRemembered = true
                                }

                                override fun imageTapped(page: Int, id: String) {
                                    model.editImage(id)
                                }

                                override fun textTapped(page: Int, id: String) {
                                    model.editTextBox(id)
                                }

                                override fun emptyAreaTapped() {
                                    model.editTextBox(null)
                                    model.editImage(null)
                                    model.selectShape(null)
                                }

                                override fun shapeDrawn(page: Int, shape: ShapeElement) {
                                    model.addShape(page, shape)
                                }

                                override fun shapeTapped(page: Int, id: String?) {
                                    model.selectShape(id)
                                }
                            }

                            // Kartka wraca tam, gdzie była. Musi to być tutaj,
                            // a nie w `update`: dopasowanie do szerokości robi
                            // się przy pierwszym nadaniu rozmiaru i bez tego
                            // zdążyłoby nadpisać zapamiętane położenie.
                            if (viewRemembered) view.restoreView(offsetX, offsetY, zoom)
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
                                images = sheet.images,
                                shapes = sheet.shapes,
                                texts = sheet.texts,
                            )
                        }
                        view.imageBitmaps = imageBitmaps
                        view.tool = tool
                        view.settings = pens
                        view.shapeSettings = shapes
                        view.shapeSquareLocked = squareShapes
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
                    onCommit = { page, before, after -> model.commitTextBox(page, before, after) },
                    onDelete = { page, id -> model.removeTextBox(page, id) },
                )

                ImageFrameOnPage(
                    pages = handwriting.pages,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    zoom = zoom,
                    edited = editedImage,
                    onChange = { page, image, toHistory -> model.updateImage(page, image, toHistory) },
                    onCommit = { page, before, after -> model.commitImage(page, before, after) },
                    onDelete = { page, id -> model.removeImage(page, id) },
                )

                ShapeFrameOnPage(
                    pages = handwriting.pages,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    zoom = zoom,
                    selected = selectedShape,
                    square = squareShapes,
                    onChange = { page, shape, toHistory -> model.updateShape(page, shape, toHistory) },
                    onCommit = { page, before, after -> model.commitShape(page, before, after) },
                    onDelete = { page, id -> model.removeShape(page, id) },
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
                    BarTextAction(words.understood, model::dismissError)
                }
            }

            if (selected.isNotEmpty()) {
                SelectionPanel(
                    count = selected.size,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    onDelete = model::deleteSelection,
                    onDeselect = model::deselect,
                )
            }

            if (penPanel && tool == EditorTool.SHAPES) {
                ShapePanel(
                    shapes = shapes,
                    square = squareShapes,
                    recentColors = recentColors,
                    onKind = model::setShapeKind,
                    onColor = model::setShapeColor,
                    onWidth = model::setShapeWidth,
                    onFill = model::setShapeFill,
                    onOpacity = model::setShapeOpacity,
                    onSquare = model::toggleSquareShapes,
                    onRememberColor = model::rememberColor,
                    onClose = { penPanel = false },
                    modifier = panelModifier,
                )
            } else if (penPanel) {
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
                    onRememberColor = model::rememberColor,
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
                        .padding(16.dp)
                        .fillMaxWidth(),
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
                inCloud = inCloud,
                pageCount = handwriting?.pages?.size ?: 0,
                tool = tool,
                pens = pens,
                shapes = shapes,
                onTitle = model::setTitle,
                onPenColor = model::setPenColor,
                onPenWidth = model::setPenWidth,
                onHighlighterColor = model::setHighlighterColor,
                onHighlighterWidth = model::setHighlighterWidth,
                onShapeColor = model::setShapeColor,
                onShapeWidth = model::setShapeWidth,
                onEraserRadius = model::setEraserRadius,
                onMore = onColorPanel,
                moreOpen = penPanel,
                narrow = true,
                onBack = goBack,
            )
            HorizontalRule()
            // Przycięcie: pola TEXT/CODE to composable pozycjonowane offsetem
            // i przy przewijaniu wyjeżdżały na pasek narzędzi.
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), content = canvasArea)
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
                onCodeBox = onCodeBox,
                onPhotoGallery = onPhotoGallery,
                onPhotoCamera = onPhotoCamera,
                onFavorite = model::toggleFavorite,
                onExport = onExport,
            )
        }
    } else {
        Row(Modifier.fillMaxSize().background(colors.desk)) {
            // Szyna narzędzi; leworęczni przestawiają ją w ustawieniach na prawo.
            val rail: @Composable () -> Unit = {
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
                    onCodeBox = onCodeBox,
                    onPhotoGallery = onPhotoGallery,
                    onPhotoCamera = onPhotoCamera,
                    onFavorite = model::toggleFavorite,
                    onExport = onExport,
                    onBack = goBack,
                    onRight = toolbarOnRight,
                )
            }
            if (!toolbarOnRight) rail()

            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Pasek na górze. Kolor i grubość leżą na wierzchu, bo to jest
                // to, co zmienia się w czasie pisania najczęściej.
                TopBar(
                    title = document?.title.orEmpty(),
                    state = saveState,
                    lastSave = lastSave,
                    inCloud = inCloud,
                    pageCount = handwriting?.pages?.size ?: 0,
                    tool = tool,
                    pens = pens,
                    shapes = shapes,
                    onTitle = model::setTitle,
                    onPenColor = model::setPenColor,
                    onPenWidth = model::setPenWidth,
                    onHighlighterColor = model::setHighlighterColor,
                    onHighlighterWidth = model::setHighlighterWidth,
                    onShapeColor = model::setShapeColor,
                    onShapeWidth = model::setShapeWidth,
                    onEraserRadius = model::setEraserRadius,
                    onMore = onColorPanel,
                    moreOpen = penPanel,
                )
                HorizontalRule()
                Box(Modifier.fillMaxSize().clipToBounds(), content = canvasArea)
            }

            if (toolbarOnRight) rail()
        }
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
    onCodeBox: () -> Unit,
    onPhotoGallery: () -> Unit,
    onPhotoCamera: () -> Unit,
    onFavorite: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    /** Szyna stoi po prawej - linia brzegowa idzie wtedy przy lewej krawędzi. */
    onRight: Boolean = false,
) {
    val words = LocalStrings.current
    Column(
        Modifier
            .width(Kajet.dimens.railWidth)
            .fillMaxSize()
            .background(Kajet.colors.desk)
            .marginRule(Kajet.colors.line, atEnd = !onRight)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconAction(KajetIcons.BackArrow, words.backToLibrary, onBack)
        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Pen, EditorTool.PEN.label(words), { onTool(EditorTool.PEN) }, selected = tool == EditorTool.PEN)
        IconAction(KajetIcons.Highlighter, EditorTool.HIGHLIGHTER.label(words), { onTool(EditorTool.HIGHLIGHTER) }, selected = tool == EditorTool.HIGHLIGHTER)
        IconAction(KajetIcons.Eraser, EditorTool.ERASER_PARTIAL.label(words), { onTool(EditorTool.ERASER_PARTIAL) }, selected = tool == EditorTool.ERASER_PARTIAL)
        IconAction(KajetIcons.EraserStroke, EditorTool.ERASER_STROKE.label(words), { onTool(EditorTool.ERASER_STROKE) }, selected = tool == EditorTool.ERASER_STROKE)
        IconAction(KajetIcons.Lasso, EditorTool.LASSO.label(words), { onTool(EditorTool.LASSO) }, selected = tool == EditorTool.LASSO)
        IconAction(KajetIcons.Ruler, EditorTool.RULER.label(words), { onTool(EditorTool.RULER) }, selected = tool == EditorTool.RULER)
        IconAction(KajetIcons.Shapes, EditorTool.SHAPES.label(words), { onTool(EditorTool.SHAPES) }, selected = tool == EditorTool.SHAPES)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = words.penSettings, onClick = onColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(penColor, CircleShape)
                    .border(1.dp, Kajet.colors.line, CircleShape),
            )
        }
        IconAction(KajetIcons.TextBox, words.insertTextBox, onTextBox)
        IconAction(KajetIcons.CodeFile, words.codeBlock, onCodeBox)
        IconAction(KajetIcons.PhotoFrame, words.insertPhotoFromGallery, onPhotoGallery)
        IconAction(KajetIcons.CameraBody, words.takePhoto, onPhotoCamera)

        // Przełącznik palca stoi w pasku, a nie tylko w ustawieniach, bo rysik
        // znika z biurka częściej, niż komukolwiek chce się chodzić po menu.
        IconAction(
            icon = if (fingerDraws) KajetIcons.FingerDraws else KajetIcons.FingerScrolls,
            description = if (fingerDraws) {
                words.fingerDrawsSwitch
            } else {
                words.fingerScrollsSwitch
            },
            onClick = onFinger,
            selected = fingerDraws,
        )
        IconAction(KajetIcons.SettingsCog, words.noteSettingsIcon, onSettings)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Undo, words.undo, onUndo, enabled = canUndo)
        IconAction(KajetIcons.Redo, words.redo, onRedo, enabled = canRedo)

        HorizontalRule(Modifier.padding(horizontal = 12.dp))

        IconAction(KajetIcons.Favourites, if (favorite) words.removeFromFavorites else words.addToFavorites, onFavorite, selected = favorite)
        IconAction(KajetIcons.Export, words.exportNote, onExport)
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
    onCodeBox: () -> Unit,
    onPhotoGallery: () -> Unit,
    onPhotoCamera: () -> Unit,
    onFavorite: () -> Unit,
    onExport: () -> Unit,
) {
    val words = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconAction(KajetIcons.Pen, EditorTool.PEN.label(words), { onTool(EditorTool.PEN) }, selected = tool == EditorTool.PEN)
        IconAction(KajetIcons.Highlighter, EditorTool.HIGHLIGHTER.label(words), { onTool(EditorTool.HIGHLIGHTER) }, selected = tool == EditorTool.HIGHLIGHTER)
        IconAction(KajetIcons.Eraser, EditorTool.ERASER_PARTIAL.label(words), { onTool(EditorTool.ERASER_PARTIAL) }, selected = tool == EditorTool.ERASER_PARTIAL)
        IconAction(KajetIcons.EraserStroke, EditorTool.ERASER_STROKE.label(words), { onTool(EditorTool.ERASER_STROKE) }, selected = tool == EditorTool.ERASER_STROKE)
        IconAction(KajetIcons.Lasso, EditorTool.LASSO.label(words), { onTool(EditorTool.LASSO) }, selected = tool == EditorTool.LASSO)
        IconAction(KajetIcons.Ruler, EditorTool.RULER.label(words), { onTool(EditorTool.RULER) }, selected = tool == EditorTool.RULER)
        IconAction(KajetIcons.Shapes, EditorTool.SHAPES.label(words), { onTool(EditorTool.SHAPES) }, selected = tool == EditorTool.SHAPES)

        VerticalDivider()

        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = words.penSettings, onClick = onColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(penColor, CircleShape)
                    .border(1.dp, Kajet.colors.line, CircleShape),
            )
        }
        IconAction(KajetIcons.TextBox, words.insertTextBox, onTextBox)
        IconAction(KajetIcons.CodeFile, words.codeBlock, onCodeBox)
        IconAction(KajetIcons.PhotoFrame, words.insertPhotoFromGallery, onPhotoGallery)
        IconAction(KajetIcons.CameraBody, words.takePhoto, onPhotoCamera)
        IconAction(
            icon = if (fingerDraws) KajetIcons.FingerDraws else KajetIcons.FingerScrolls,
            description = if (fingerDraws) {
                words.fingerDrawsSwitch
            } else {
                words.fingerScrollsSwitch
            },
            onClick = onFinger,
            selected = fingerDraws,
        )
        IconAction(KajetIcons.SettingsCog, words.noteSettingsIcon, onSettings)

        VerticalDivider()

        IconAction(KajetIcons.Undo, words.undo, onUndo, enabled = canUndo)
        IconAction(KajetIcons.Redo, words.redo, onRedo, enabled = canRedo)

        VerticalDivider()

        IconAction(KajetIcons.Favourites, if (favorite) words.removeFromFavorites else words.addToFavorites, onFavorite, selected = favorite)
        IconAction(KajetIcons.Export, words.exportNote, onExport)
    }
}

/**
 * Pasek u góry kartki: tytuł, stan zapisu i to, czym się właśnie pisze.
 *
 * Na telefonie stoi w dwóch rzędach, a nie w jednym. W jednym rzędzie na
 * kolory i grubości zostawało po odliczeniu strzałki powrotu, tytułu i napisu
 * o zapisie kilkadziesiąt punktów przy prawej krawędzi - kropki ledwo tam było
 * widać, a trafienie w tę właściwą wymagało przewijania w bok po omacku. Teraz
 * pierwszy rząd trzyma tytuł ze stanem zapisu, a kolory i grubości dostają
 * osobny rząd na całą szerokość ekranu.
 *
 * Kropka pełnych ustawień pisaka stoi na telefonie w rzędzie tytułu, nie na
 * końcu przewijanych kropek: to jedyne wyjście do reszty barw, więc ma być
 * pod palcem bez przewijania.
 *
 * Na tablecie zostaje jeden rząd - tam miejsca starcza.
 */
@Composable
private fun TopBar(
    title: String,
    state: SaveState,
    lastSave: Long?,
    inCloud: Boolean?,
    pageCount: Int,
    tool: EditorTool,
    pens: PenSettings,
    shapes: ShapeSettings,
    onTitle: (String) -> Unit,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
    onShapeColor: (Int) -> Unit,
    onShapeWidth: (Float) -> Unit,
    onEraserRadius: (Float) -> Unit,
    onMore: () -> Unit,
    moreOpen: Boolean,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val words = LocalStrings.current
    val strip: @Composable () -> Unit = {
        PenStrip(
            tool = tool,
            pens = pens,
            shapes = shapes,
            onPenColor = onPenColor,
            onPenWidth = onPenWidth,
            onHighlighterColor = onHighlighterColor,
            onHighlighterWidth = onHighlighterWidth,
            onShapeColor = onShapeColor,
            onShapeWidth = onShapeWidth,
            onEraserRadius = onEraserRadius,
        )
    }
    val swatch: @Composable () -> Unit = {
        IconAction(
            icon = KajetIcons.ColorSwatch,
            description = words.morePenSettings,
            onClick = onMore,
            selected = moreOpen,
            touchTarget = 44.dp,
        )
    }

    if (narrow) {
        Column(
            modifier
                .fillMaxWidth()
                .background(Kajet.colors.sheet)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onBack != null) {
                    IconAction(KajetIcons.BackArrow, words.backToLibrary, onBack, touchTarget = 40.dp)
                }
                TitleField(
                    title = title,
                    onTitle = onTitle,
                    modifier = Modifier.weight(1f),
                )
                SaveIndicator(state = state, lastSave = lastSave, inCloud = inCloud)
                swatch()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                strip()
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .background(Kajet.colors.sheet)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                IconAction(KajetIcons.BackArrow, words.backToLibrary, onBack)
            }
            TitleField(
                title = title,
                onTitle = onTitle,
                modifier = Modifier.widthIn(min = 72.dp, max = 220.dp),
            )
            SaveIndicator(state = state, lastSave = lastSave, inCloud = inCloud)
            if (pageCount > 1) {
                Text(words.pagesShort(pageCount), style = Kajet.type.meta, color = Kajet.colors.muted)
            }

            VerticalDivider()

            // Reszta paska przewija się w bok, żeby przy wąskim oknie nic nie
            // wypadło poza krawędź i żeby tytuł zawsze został na swoim miejscu.
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                strip()
                swatch()
            }
        }
    }
}

/** Tytuł notatki wprost w pasku - kliknięcie w napis od razu go poprawia. */
@Composable
private fun TitleField(
    title: String,
    onTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = LocalStrings.current
    BasicTextField(
        value = title,
        onValueChange = onTitle,
        singleLine = true,
        textStyle = Kajet.type.titleSmall.copy(color = Kajet.colors.text),
        cursorBrush = SolidColor(Kajet.colors.accent),
        modifier = modifier,
        decorationBox = { field ->
            if (title.isEmpty()) {
                Text(
                    text = words.unnamed,
                    style = Kajet.type.titleSmall,
                    color = Kajet.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            field()
        },
    )
}

/**
 * Barwy i grubości bieżącego narzędzia. Gumka ma zamiast barw same wielkości.
 *
 * Stoi wprost w rzędzie, który ją wywołuje - raz w pasku tabletu, raz
 * w osobnym rzędzie na telefonie.
 */
@Composable
private fun PenStrip(
    tool: EditorTool,
    pens: PenSettings,
    shapes: ShapeSettings,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onHighlighterColor: (Int) -> Unit,
    onHighlighterWidth: (Float) -> Unit,
    onShapeColor: (Int) -> Unit,
    onShapeWidth: (Float) -> Unit,
    onEraserRadius: (Float) -> Unit,
) {
    val words = LocalStrings.current
    if (tool.isEraser) {
        Text(words.eraser, style = Kajet.type.label, color = Kajet.colors.muted)
        Spacer(Modifier.width(6.dp))
        listOf(6f to 8.dp, 12f to 12.dp, 24f to 16.dp, 48f to 20.dp).forEach { (size, dot) ->
            SizeDot(
                dot = dot,
                color = Kajet.colors.muted.toArgb(),
                picked = pens.eraserRadius == size,
                description = words.eraserSizeOf(size.roundToInt()),
                onClick = { onEraserRadius(size) },
            )
        }
    } else {
        val highlighting = tool == EditorTool.HIGHLIGHTER
        val shaping = tool == EditorTool.SHAPES
        // Pierwszy pisak to atrament tej kartki: na ciemnej jasny, na jasnej ciemny.
        val palette =
            if (highlighting) InkPalette.highlighters(words)
            else InkPalette.pens(Kajet.colors.isDark, words)
        val picked = when {
            shaping -> shapes.color
            highlighting -> pens.highlighterColor
            else -> pens.penColor
        }

        palette.forEach { (name, color) ->
            ColourDot(
                color = color.toArgb(),
                description = name,
                onClick = {
                    when {
                        shaping -> onShapeColor(color.toArgb())
                        highlighting -> onHighlighterColor(color.toArgb())
                        else -> onPenColor(color.toArgb())
                    }
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
        val currentWidth = when {
            shaping -> shapes.strokeWidth
            highlighting -> pens.highlighterWidth
            else -> pens.penWidth
        }
        widths.forEach { (size, dot) ->
            SizeDot(
                dot = dot,
                color = picked,
                picked = currentWidth == size,
                description = words.strokeWidthOf("%.1f".format(size)),
                onClick = {
                    when {
                        shaping -> onShapeWidth(size)
                        highlighting -> onHighlighterWidth(size)
                        else -> onPenWidth(size)
                    }
                },
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
    onDeselect: () -> Unit,
) {
    val words = LocalStrings.current
    val shape = RoundedCornerShape(Kajet.dimens.corner)
    Row(
        modifier
            .clip(shape)
            .background(Kajet.colors.sheet, shape)
            .border(1.dp, Kajet.colors.line, shape)
            // Na wąskim ekranie napis i akcja nie mieszczą się obok siebie.
            .height(44.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(words.selectedStrokes(count), style = Kajet.type.label, color = Kajet.colors.text)
        /*
          Akcja paska, nie SecondaryButton. Ten ma 48 dp i obwódkę -
          w ciasnej karcie 44 dp odcinał się jak niedopasowany przycisk.
          Ten sam krój co licznik zaznaczenia, bez ramki.
        */
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(
                    onClick = onDelete,
                    onClickLabel = words.delete,
                    role = Role.Button,
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = words.delete,
                style = Kajet.type.label,
                color = Kajet.colors.danger,
            )
        }
        IconAction(KajetIcons.Close, words.deselect, onDeselect, touchTarget = 44.dp)
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
    /** Dokłada barwę do spisu „twoich kolorów" - po zamknięciu okna z tęczą. */
    onRememberColor: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val words = LocalStrings.current
    var fullPicker by remember { mutableStateOf(false) }

    ToolPanel(
        title = when {
            tool.isEraser -> words.eraser
            tool == EditorTool.HIGHLIGHTER -> words.highlighterTool
            else -> words.penTool
        },
        onClose = onClose,
        // Szerokość i wysokość nadaje wywołujący: na telefonie panel zajmuje
        // całą szerokość przy dolnej krawędzi, na tablecie wąski pasek z boku.
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        when {
            tool.isEraser -> {
                SettingSlider(
                    name = words.eraserSize,
                    value = pens.eraserRadius,
                    range = 2f..80f,
                    onChange = onEraserRadius,
                    readout = { words.pointsOf(it.roundToInt().toString()) },
                )
                PenStrokePreview(
                    argb = Kajet.colors.text.toArgb(),
                    width = pens.eraserRadius,
                    opacity = 0.2f,
                )
                Text(tool.description(words), style = Kajet.type.meta, color = Kajet.colors.muted)
            }

            tool == EditorTool.HIGHLIGHTER -> {
                ColourRow(
                    palette = InkPalette.highlighters(words),
                    picked = pens.highlighterColor,
                    recent = recentColors,
                    onPick = onHighlighterColor,
                    onCustom = { fullPicker = true },
                )
                SettingSlider(
                    name = words.widthLabel,
                    value = pens.highlighterWidth,
                    range = 4f..40f,
                    onChange = onHighlighterWidth,
                    readout = { words.pointsOf("%.0f".format(it)) },
                )
                SettingSlider(
                    name = words.opacity,
                    value = pens.highlighterOpacity,
                    range = 0.1f..1f,
                    onChange = onHighlighterOpacity,
                    readout = { words.percentOf((it * 100).roundToInt()) },
                )
                PenStrokePreview(
                    argb = pens.highlighterColor,
                    width = pens.highlighterWidth,
                    opacity = pens.highlighterOpacity,
                )
                if (fullPicker) {
                    ColourPickerDialog(
                        title = words.highlighterColourTitle,
                        color = pens.highlighterColor,
                        onChange = onHighlighterColor,
                        onClose = { fullPicker = false; onRememberColor(pens.highlighterColor) },
                        withAlpha = false,
                        presetColors = InkPalette.highlighters(words),
                        recentColors = recentColors,
                    )
                }
            }

            else -> {
                SectionLabel(words.whatYouWriteWith)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Brushes.penKinds.forEach { kind ->
                        IconAction(
                            icon = penIcon(kind),
                            description = kind.label(words),
                            onClick = { onPenKind(kind) },
                            selected = pens.penKind == kind,
                        )
                    }
                }

                ColourRow(
                    palette = InkPalette.pens(Kajet.colors.isDark, words),
                    picked = pens.penColor,
                    recent = recentColors,
                    onPick = onPenColor,
                    onCustom = { fullPicker = true },
                )

                SettingSlider(
                    name = words.thickness,
                    value = pens.penWidth,
                    range = 0.4f..20f,
                    onChange = onPenWidth,
                    readout = { words.pointsOf("%.1f".format(it)) },
                )
                SettingSlider(
                    name = words.opacity,
                    value = pens.penOpacity,
                    range = 0.1f..1f,
                    onChange = onPenOpacity,
                    readout = { words.percentOf((it * 100).roundToInt()) },
                )

                PenStrokePreview(
                    argb = pens.penColor,
                    width = pens.penWidth,
                    opacity = pens.penOpacity,
                )

                Text(
                    text = when (pens.penKind) {
                        InkTool.PEN -> words.penAboutPen
                        InkTool.FINELINER -> words.penAboutFineliner
                        InkTool.PENCIL -> words.penAboutPencil
                        InkTool.DASHED -> words.penAboutDashed
                        InkTool.HIGHLIGHTER -> ""
                    },
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                )

                if (fullPicker) {
                    ColourPickerDialog(
                        title = words.inkColourTitle,
                        color = pens.penColor,
                        onChange = onPenColor,
                        // Do „twoich kolorów" wpada dopiero barwa, na której
                        // człowiek się zatrzymał - jedna, nie cała droga po tęczy.
                        onClose = { fullPicker = false; onRememberColor(pens.penColor) },
                        withAlpha = false,
                        presetColors = InkPalette.pens(Kajet.colors.isDark, words),
                        recentColors = recentColors,
                    )
                }
            }
        }
    }
}

/**
 * Panel kształtu: co się rysuje i czym.
 *
 * Zmiany idą i do ustawień narzędzia, i do kształtu wziętego właśnie do
 * poprawek - poprawienie koloru nie może znaczyć „skasuj figurę i narysuj ją
 * od nowa".
 */
@Composable
private fun ShapePanel(
    shapes: ShapeSettings,
    square: Boolean,
    recentColors: List<Int>,
    onKind: (ShapeKind) -> Unit,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onFill: (Int) -> Unit,
    onOpacity: (Float) -> Unit,
    onSquare: () -> Unit,
    onRememberColor: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val words = LocalStrings.current
    var outlinePicker by remember { mutableStateOf(false) }
    var fillPicker by remember { mutableStateOf(false) }

    ToolPanel(
        title = EditorTool.SHAPES.label(words),
        onClose = onClose,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SectionLabel(words.shapeKindLabel)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ShapeKind.entries.forEach { kind ->
                IconAction(
                    icon = shapeIcon(kind),
                    description = kind.label(words),
                    onClick = { onKind(kind) },
                    selected = shapes.kind == kind,
                )
            }
        }

        ColourRow(
            palette = InkPalette.pens(Kajet.colors.isDark, words),
            picked = shapes.color,
            recent = recentColors,
            onPick = onColor,
            onCustom = { outlinePicker = true },
        )

        SettingSlider(
            name = words.thickness,
            value = shapes.strokeWidth,
            range = 0.4f..20f,
            onChange = onWidth,
            readout = { words.pointsOf("%.1f".format(it)) },
        )
        SettingSlider(
            name = words.opacity,
            value = shapes.opacity,
            range = 0.1f..1f,
            onChange = onOpacity,
            readout = { words.percentOf((it * 100).roundToInt()) },
        )

        // Wypełnienie tylko dla figur zamkniętych - linii i strzałki nie ma czym wypełnić.
        if (!shapes.kind.open) {
            SectionLabel(words.shapeFillLabel)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = KajetIcons.Close,
                    description = words.shapeNoFill,
                    onClick = { onFill(0) },
                    selected = shapes.fill == 0,
                )
                InkPalette.highlighters(words).forEach { (name, color) ->
                    ColourDot(
                        color = color.toArgb(),
                        description = name,
                        onClick = { onFill(color.toArgb()) },
                        selected = shapes.fill == color.toArgb(),
                        diameter = 22.dp,
                    )
                }
                IconAction(
                    icon = KajetIcons.ShapeFill,
                    description = words.shapeFillColourTitle,
                    onClick = { fillPicker = true },
                    touchTarget = 44.dp,
                )
            }
        }

        HorizontalRule()

        ChoiceRow(label = words.shapeSquareLock, picked = square, onClick = onSquare)
        Text(words.shapeSquareAbout, style = Kajet.type.meta, color = Kajet.colors.muted)
        Text(
            text = EditorTool.SHAPES.description(words),
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
    }

    if (outlinePicker) {
        ColourPickerDialog(
            title = words.shapeOutlineColourTitle,
            color = shapes.color,
            onChange = onColor,
            onClose = { outlinePicker = false; onRememberColor(shapes.color) },
            withAlpha = false,
            presetColors = InkPalette.pens(Kajet.colors.isDark, words),
            recentColors = recentColors,
        )
    }

    if (fillPicker) {
        ColourPickerDialog(
            title = words.shapeFillColourTitle,
            color = if (shapes.fill == 0) 0x33FFFFFF else shapes.fill,
            onChange = onFill,
            onClose = { fillPicker = false; onRememberColor(shapes.fill) },
            presetColors = InkPalette.highlighters(words),
            recentColors = recentColors,
        )
    }
}

private fun shapeIcon(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> KajetIcons.ShapeLine
    ShapeKind.ARROW -> KajetIcons.ShapeArrow
    ShapeKind.RECTANGLE -> KajetIcons.ShapeRectangle
    ShapeKind.ROUNDED_RECTANGLE -> KajetIcons.ShapeRoundedRectangle
    ShapeKind.ELLIPSE -> KajetIcons.ShapeEllipse
    ShapeKind.TRIANGLE -> KajetIcons.ShapeTriangle
    ShapeKind.DIAMOND -> KajetIcons.ShapeDiamond
    ShapeKind.STAR -> KajetIcons.ShapeStar
}

@Composable
private fun TextBoxFormatBar(
    box: TextBoxElement,
    recentColors: List<Int>,
    onChange: ((TextBoxElement) -> TextBoxElement) -> Unit,
    onColor: (Int) -> Unit,
    modifier: Modifier,
) {
    val words = LocalStrings.current
    var textColourPicker by remember { mutableStateOf(false) }
    var backgroundPicker by remember { mutableStateOf(false) }
    var fontPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Kajet.dimens.corner)
    // 44 dp jak IconToggle i ColourDot. Domyślne 48 dp (IconAction /
    // SecondaryButton) wystawało z karty na telefonie.
    val compact = 44.dp

    Column(
        modifier
            .clip(shape)
            .background(Kajet.colors.sheet, shape)
            .border(1.dp, Kajet.colors.line, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        /*
          Na ~360 dp suma ikon przekracza szerokość karty i Compose przycina
          rząd - ostatnie wyrównania i obwódka karty znikały za krawędzią.
          Przewijanie w bok jak w NarrowToolRow i szynie mapy myśli.
        */
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconAction(
                icon = KajetIcons.Letters,
                description = "${words.fontFamily}: ${box.font.label(words)}",
                onClick = { fontPicker = !fontPicker },
                selected = fontPicker,
                touchTarget = compact,
            )

            IconAction(KajetIcons.Minus, words.smallerText, {
                onChange { it.copy(fontSize = (it.fontSize - 2f).coerceAtLeast(6f)) }
            }, touchTarget = compact)
            Text(
                text = "${box.fontSize.roundToInt()}",
                style = Kajet.type.label,
                color = Kajet.colors.text,
                modifier = Modifier.width(28.dp),
            )
            IconAction(KajetIcons.Plus, words.largerText, {
                onChange { it.copy(fontSize = (it.fontSize + 2f).coerceAtMost(96f)) }
            }, touchTarget = compact)

            VerticalDivider()

            IconToggle(
                icon = KajetIcons.Bold,
                description = words.bold,
                checked = box.bold,
                onCheckedChange = { on -> onChange { it.copy(bold = on) } },
            )
            IconToggle(
                icon = KajetIcons.Italic,
                description = words.italic,
                checked = box.italic,
                onCheckedChange = { on -> onChange { it.copy(italic = on) } },
            )
            IconToggle(
                icon = KajetIcons.Underline,
                description = words.underline,
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
                    description = align.label(words),
                    onClick = { onChange { it.copy(align = align) } },
                    selected = box.align == align,
                    touchTarget = compact,
                )
            }

            VerticalDivider()

            ColourDot(
                color = box.color,
                description = words.textColourTitle,
                onClick = { textColourPicker = true },
            )
            IconAction(
                icon = KajetIcons.PageRuling,
                description = if (box.background == 0) words.addBoxBackground else words.changeBoxBackground,
                onClick = { backgroundPicker = true },
                selected = box.background != 0,
                touchTarget = compact,
            )
        }

        if (fontPicker) {
            SegmentedChoice(
                options = NoteFont.entries,
                selected = box.font,
                name = { it.label(words) },
                // Menu zostaje otwarte: krój porównuje się na żywo,
                // zamyka się je samemu tym samym przyciskiem „abc".
                onSelect = { font -> onChange { it.copy(font = font) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (textColourPicker) {
        ColourPickerDialog(
            title = words.textColourTitle,
            color = box.color,
            onChange = { argb -> onChange { it.copy(color = argb) } },
            // Zapamiętujemy na wyjściu, nie po drodze - inaczej jedno dobranie
            // barwy zapycha spis „twoich kolorów" odcieniami mijanymi po tęczy.
            onClose = { textColourPicker = false; onColor(box.color) },
            presetColors = InkPalette.pens(words),
            recentColors = recentColors,
        )
    }

    if (backgroundPicker) {
        ColourPickerDialog(
            title = words.boxBackgroundTitle,
            color = if (box.background == 0) 0x33FFFFFF else box.background,
            onChange = { argb -> onChange { it.copy(background = argb) } },
            onClose = { backgroundPicker = false; onColor(box.background) },
            presetColors = InkPalette.highlighters(words),
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
    val words = LocalStrings.current
    SectionLabel(words.colour)
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
                    description = words.yourColour,
                    onClick = { onPick(own) },
                    selected = picked == own,
                    diameter = 22.dp,
                )
            }
        IconAction(KajetIcons.ColorSwatch, words.pickOwnColour, onCustom, touchTarget = 44.dp)
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
    val words = LocalStrings.current
    ToolPanel(
        title = words.noteSettings,
        onClose = onClose,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        SectionLabel(words.pageBackgroundLabel)
        PageBackground.entries.forEach { variant ->
            ChoiceRow(
                label = variant.label(words),
                picked = variant == background,
                onClick = { onBackground(variant) },
            )
        }

        HorizontalRule()

        SectionLabel(words.pageKind)
        PageMode.entries.forEach { variant ->
            ChoiceRow(
                label = variant.label(words),
                picked = variant == mode,
                onClick = { onMode(variant) },
            )
        }
        Text(
            text = if (mode == PageMode.A4) {
                words.pageA4Long
            } else {
                words.pageScrollLong
            },
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )

        HorizontalRule()

        SectionLabel(words.pagesLabel)
        Text(
            text = if (pageCount == 1) words.onePage else words.pagesCount(pageCount),
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(words.addPage, onAddPage, icon = KajetIcons.AddPage)
            if (pageCount > 1) {
                SecondaryButton(
                    text = words.removeLastPage,
                    onClick = onRemovePage,
                    icon = KajetIcons.Bin,
                    color = Kajet.colors.danger,
                )
            }
        }

        HorizontalRule()

        SectionLabel(words.fingerLabel)
        ChoiceRow(
            label = FingerBehavior.DRAW.label(words),
            picked = fingerDraws,
            onClick = { if (!fingerDraws) onFinger() },
        )
        ChoiceRow(
            label = FingerBehavior.SCROLL.label(words),
            picked = !fingerDraws,
            onClick = { if (fingerDraws) onFinger() },
        )
        Text(
            text = words.palmRejectionAbout,
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
        )

        HorizontalRule()
        SecondaryButton(words.fitWidth, onFitWidth, icon = KajetIcons.FitToView)
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

/*
  Akcja paska, nie SecondaryButton. Ten ma 48 dp i obwódkę - w ciasnym
  rzędzie komunikatu odcinał się od tła i na telefonie łamał etykietę
  („Rozumiem"). Tu ten sam krój co treść paska, bez ramki.
*/
@Composable
private fun BarTextAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(48.dp)
            .focusProperties { canFocus = false }
            .clickable(
                onClick = onClick,
                onClickLabel = text,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Kajet.type.label,
            color = Kajet.colors.muted,
            maxLines = 1,
        )
    }
}
