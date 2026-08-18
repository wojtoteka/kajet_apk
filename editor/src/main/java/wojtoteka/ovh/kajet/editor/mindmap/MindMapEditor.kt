package wojtoteka.ovh.kajet.editor.mindmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.FolderColor
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.ColourDot
import wojtoteka.ovh.kajet.core.design.component.ColourPickerDialog
import wojtoteka.ovh.kajet.core.design.component.IconToggle
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.SettingSlider
import wojtoteka.ovh.kajet.core.design.component.SegmentedChoice
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.component.drawStrokes
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.design.fontFamilyFor
import wojtoteka.ovh.kajet.core.model.MindEdge
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.MindNode
import wojtoteka.ovh.kajet.core.model.NodeShape
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.disconnectFrom
import wojtoteka.ovh.kajet.core.text.mapTally
import wojtoteka.ovh.kajet.core.text.percentOf
import wojtoteka.ovh.kajet.core.text.pointsOf
import wojtoteka.ovh.kajet.core.text.thisNode
import wojtoteka.ovh.kajet.editor.SaveIndicator
import wojtoteka.ovh.kajet.editor.text.DrawingDialog
import kotlin.math.roundToInt

@Composable
fun MindMapEditor(
    model: MindMapViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    /* Puste, gdy konto nie ma asystenta - wtedy nie ma po nim ani śladu. */
    onAi: (() -> Unit)? = null,
) {
    val words = LocalStrings.current
    val document by model.document.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val edited by model.edited.collectAsStateWithLifecycle()
    val connecting by model.connecting.collectAsStateWithLifecycle()
    val inkLabel by model.inkLabel.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
    val inCloud by model.inCloud.collectAsStateWithLifecycle()
    val recentColors by model.recentColors.collectAsStateWithLifecycle()
    val dragged by model.draggedLine.collectAsStateWithLifecycle()
    val selectedEdge by model.selectedEdge.collectAsStateWithLifecycle()
    val canUndo by model.canUndo.collectAsStateWithLifecycle()
    val canRedo by model.canRedo.collectAsStateWithLifecycle()

    val colors = Kajet.colors
    val map = document?.mindMap

    var offsetX by remember { mutableFloatStateOf(map?.viewX ?: 0f) }
    var offsetY by remember { mutableFloatStateOf(map?.viewY ?: 0f) }
    var zoom by remember { mutableFloatStateOf(map?.zoom ?: 1f) }

    /*
      Zapamiętane przesunięcie i przybliżenie wracają dopiero TUTAJ.

      Notatka wczytuje się z dysku po pierwszym złożeniu ekranu, więc przy
      `remember` wyżej `map` jest jeszcze puste i zostawały zera. Mapa otwierała
      się przez to zawsze w lewym górnym rogu i na 100%, choć `rememberView`
      zapisuje jedno i drugie przy każdym zejściu w tło. Raz na otwarcie —
      dalsze zmiany treści nie mają szarpać widokiem spod ręki.
    */
    var viewRestored by remember { mutableStateOf(false) }
    LaunchedEffect(map != null) {
        val loaded = map ?: return@LaunchedEffect
        if (viewRestored) return@LaunchedEffect
        viewRestored = true
        offsetX = loaded.viewX
        offsetY = loaded.viewY
        zoom = loaded.zoom.coerceIn(0.25f, 4f)
    }

    // Rozmiar planszy w pikselach: potrzebny, żeby zmieścić całą mapę w oknie
    // i żeby przyciski przybliżenia trzymały środek ekranu w miejscu.
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val narrow = LocalConfiguration.current.screenWidthDp < 600

    fun zoomBy(factor: Float) {
        val next = (zoom * factor).coerceIn(0.25f, 4f)
        if (next == zoom) return
        if (boardSize.width > 0 && boardSize.height > 0) {
            val centreX = offsetX + boardSize.width / (2f * zoom)
            val centreY = offsetY + boardSize.height / (2f * zoom)
            offsetX = centreX - boardSize.width / (2f * next)
            offsetY = centreY - boardSize.height / (2f * next)
        }
        zoom = next
    }

    /*
      Wysokość panelu węzła. Liczona TUTAJ, a nie w samym panelu, bo plansza
      musi wiedzieć, ile miejsca u dołu jest zajęte.
    */
    val panelHeight = if (narrow) {
        (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    } else {
        460.dp
    }

    /*
      Zaznaczony węzeł ma zostać widoczny.

      Na telefonie panel edycji wisi na dole całą szerokością i zasłania dolną
      połowę planszy — czyli często ten węzeł, którego właśnie dotknięto.
      Po zaznaczeniu plansza zjeżdża tak, żeby węzeł wypadł na środku tego, co
      z niej zostało. Gdy widać go w całości, nic się nie dzieje: przesuwanie
      mapy przy każdym dotknięciu byłoby gorsze od zasłoniętego węzła.
    */
    val density = LocalDensity.current
    LaunchedEffect(selected, narrow, boardSize) {
        if (!narrow) return@LaunchedEffect
        val id = selected ?: return@LaunchedEffect
        val node = map?.nodes?.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (boardSize.height == 0) return@LaunchedEffect

        val free = boardSize.height - with(density) { panelHeight.toPx() }
        if (free <= 0f) return@LaunchedEffect

        val top = (node.y - offsetY) * zoom
        val bottom = top + node.height * zoom
        if (top >= 0f && bottom <= free) return@LaunchedEffect

        val target = node.y - ((free - node.height * zoom) / 2f).coerceAtLeast(0f) / zoom
        animate(initialValue = offsetY, targetValue = target) { value, _ -> offsetY = value }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                model.rememberView(offsetX, offsetY, zoom)
                model.saveNow()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val visible = remember(map) { map?.let { MindMapLayout.visible(it) } ?: emptySet() }

    val toolbarOnRight by model.toolbarOnRight.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().background(colors.desk)) {

        // Pasek narzędzi; leworęczni przestawiają go w ustawieniach na prawo.
        val rail: @Composable () -> Unit = {
        Column(
            Modifier
                // Węższy na telefonie, jak w bibliotece i edytorze tekstu:
                // 56 dp to tam ponad 15% szerokości ekranu.
                .width(if (narrow) 48.dp else Kajet.dimens.railWidth)
                .fillMaxHeight()
                .background(colors.desk)
                .marginRule(colors.line, atEnd = !toolbarOnRight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconAction(KajetIcons.BackArrow, words.backToLibrary, { model.saveNow(); onBack() })
            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.NodeDot,
                description = words.newNode,
                onClick = { model.addNode(offsetX + 120f, offsetY + 120f) },
            )
            IconAction(
                icon = KajetIcons.Plus,
                description = words.addBranch,
                onClick = { selected?.let { model.addChild(it) } },
                enabled = selected != null,
            )
            IconAction(
                icon = KajetIcons.AddPage,
                description = words.addSibling,
                onClick = { selected?.let { model.addSibling(it) } },
                enabled = selected != null,
            )
            IconAction(
                icon = KajetIcons.ShareArrow,
                description = if (connecting) {
                    words.finishConnecting
                } else {
                    words.connectToOthers
                },
                onClick = model::toggleConnecting,
                selected = connecting,
                enabled = selected != null || connecting,
            )
            IconAction(
                icon = KajetIcons.Pen,
                description = words.inkLabel,
                onClick = { selected?.let { model.openInkLabel(it) } },
                enabled = selected != null,
            )

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.MindMapIcon, words.arrangeBranches, model::arrangeBranches)
            IconAction(
                icon = KajetIcons.FitToView,
                description = words.fitWholeMap,
                onClick = {
                    // Obwiednia widocznych węzłów; zwinięte gałęzie zostają poza rachunkiem.
                    val shown = map?.nodes?.filter { it.id in visible }.orEmpty()
                    if (shown.isEmpty() || boardSize.width == 0 || boardSize.height == 0) {
                        offsetX = 0f
                        offsetY = 0f
                        zoom = 1f
                    } else {
                        val left = shown.minOf { it.x } - FIT_PAD
                        val top = shown.minOf { it.y } - FIT_PAD
                        val width = shown.maxOf { it.x + it.width } + FIT_PAD - left
                        val height = shown.maxOf { it.y + it.height } + FIT_PAD - top
                        zoom = minOf(boardSize.width / width, boardSize.height / height)
                            .coerceIn(0.25f, 4f)
                        offsetX = left - (boardSize.width / zoom - width) / 2f
                        offsetY = top - (boardSize.height / zoom - height) / 2f
                    }
                },
            )

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.Undo, words.undo, model::undo, enabled = canUndo)
            IconAction(KajetIcons.Redo, words.redo, model::redo, enabled = canRedo)

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.Favourites,
                description = if (document?.favorite == true) words.removeFromFavorites else words.addToFavorites,
                onClick = model::toggleFavorite,
                selected = document?.favorite == true,
            )
            IconAction(KajetIcons.Export, words.exportMap, onExport)
            if (onAi != null) IconAction(KajetIcons.Bulb, words.aiOpen, onAi)
            Spacer(Modifier.height(12.dp))
        }
        }

        if (!toolbarOnRight) rail()

        /*
          Przycięcie do granic planszy.

          Compose nie ucina niczego z siebie, a węzły stoją na przesunięciach
          liczonych od położenia mapy — po przesunięciu w prawo wychodzą one na
          minus i węzeł maluje się na pasku narzędzi. Plansza jest w rzędzie ZA
          paskiem, więc rysuje się na wierzchu i przykrywała mu ikony.

          Ta sama pułapka co przy podglądzie strony w edytorze kodu
          (CodeEditor.kt) i przy rysowaniu kresek (StrokeCanvas.onDraw).
        */
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .onSizeChanged { boardSize = it },
        ) {
            if (map != null) {
                // Plansza. Dwa palce przesuwają i skalują, jeden palec w pustym
                // miejscu odznacza węzeł.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.desk)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, scaleChange, _ ->
                                zoom = (zoom * scaleChange).coerceIn(0.25f, 4f)
                                offsetX -= pan.x / zoom
                                offsetY -= pan.y / zoom
                            }
                        }
                        .pointerInput(zoom, offsetX, offsetY) {
                            detectTapGestures(
                                onTap = { touch ->
                                    // Dotknięcie linii łapie linię, dotknięcie pustego
                                    // miejsca odznacza wszystko.
                                    val edge = model.edgeAt(
                                        x = touch.x / zoom + offsetX,
                                        y = touch.y / zoom + offsetY,
                                        reach = EDGE_TOUCH_REACH / zoom,
                                    )
                                    if (edge != null) model.selectEdge(edge) else model.select(null)
                                },
                                // Dwa dotknięcia pustego miejsca stawiają tam węzeł,
                                // punkt dotyku wypada mniej więcej w jego środku.
                                onDoubleTap = { touch ->
                                    model.addNode(
                                        x = touch.x / zoom + offsetX - 80f,
                                        y = touch.y / zoom + offsetY - 32f,
                                    )
                                },
                            )
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // Linie między węzłami
                        val byId = map.nodes.associateBy { it.id }
                        for (edge in map.edges) {
                            val from = byId[edge.fromId] ?: continue
                            val to = byId[edge.toId] ?: continue
                            if (from.id !in visible || to.id !in visible) continue
                            val picked = edge.id == selectedEdge
                            drawEdge(
                                from = from,
                                to = to,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                zoom = zoom,
                                // Linia w barwie gałęzi, do której prowadzi — jak w edytorze WWW.
                                color = if (picked) {
                                    colors.accent
                                } else {
                                    nodeColor(to, colors.isDark).copy(alpha = 0.55f)
                                },
                                thick = picked,
                            )
                        }
                        // Podpisy pisane rysikiem
                        for (node in map.nodes) {
                            if (node.id !in visible || node.ink.isEmpty()) continue
                            drawStrokes(
                                strokes = node.ink,
                                color = nodeColor(node, colors.isDark),
                                offset = Offset(
                                    (node.x - offsetX) * zoom,
                                    (node.y - offsetY) * zoom,
                                ),
                                scale = zoom,
                                width = 2f * zoom,
                            )
                        }

                        // Linia ciągnięta palcem. Przerywana, bo jeszcze jej nie ma
                        // w mapie: dopiero powstanie, gdy palec puści.
                        dragged?.let { line ->
                            val from = map.nodes.firstOrNull { it.id == line.fromId }
                            if (from != null) {
                                drawLine(
                                    color = colors.accent,
                                    start = Offset(
                                        (from.x + from.width - offsetX) * zoom,
                                        (from.y + from.height / 2f - offsetY) * zoom,
                                    ),
                                    end = Offset(
                                        (line.x - offsetX) * zoom,
                                        (line.y - offsetY) * zoom,
                                    ),
                                    strokeWidth = 2.5f * zoom,
                                    cap = StrokeCap.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(12f * zoom, 9f * zoom),
                                    ),
                                )
                            }
                        }
                    }

                    map.nodes.filter { it.id in visible }.forEach { node ->
                        NodeOnBoard(
                            node = node,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            zoom = zoom,
                            selected = selected == node.id,
                            edited = edited == node.id,
                            connectTarget = dragged?.targetId == node.id,
                            hasChildren = MindMapLayout.hasChildren(map, node.id),
                            hiddenCount = if (node.collapsed) {
                                MindMapLayout.hiddenDescendants(map, node.id)
                            } else {
                                0
                            },
                            narrow = narrow,
                            onSelect = { model.select(node.id) },
                            onEdit = { model.edit(node.id) },
                            onText = { model.setText(node.id, it) },
                            onDragStart = model::startDragging,
                            onDrag = { dx, dy -> model.moveNode(node.id, dx / zoom, dy / zoom) },
                            onDragEnd = model::finishDragging,
                            onCollapse = { model.toggleCollapsed(node.id) },
                            onResizeStart = model::startDragging,
                            // Rozmiar bierzemy z modelu, a nie ze zrzutu w tym lambda:
                            // w trakcie ciągnięcia węzeł rośnie z każdą klatką.
                            onResize = { dx, dy ->
                                model.map.nodes.firstOrNull { it.id == node.id }?.let { current ->
                                    model.resizeNode(
                                        id = node.id,
                                        width = current.width + dx / zoom,
                                        height = current.height + dy / zoom,
                                        finished = false,
                                    )
                                }
                            },
                            onResizeEnd = {
                                model.resizeNode(node.id, node.width, node.height, finished = true)
                            },
                            // Handle oddaje położenie palca w pikselach ekranu,
                            // a model liczy w układzie mapy, więc przeliczamy tu.
                            onConnectStart = {
                                model.startConnecting(
                                    fromId = node.id,
                                    x = node.x + node.width,
                                    y = node.y + node.height / 2f,
                                )
                            },
                            onConnectDrag = { screenX, screenY ->
                                model.dragConnection(
                                    x = screenX / zoom + offsetX,
                                    y = screenY / zoom + offsetY,
                                )
                            },
                            onConnectEnd = model::finishConnecting,
                            onConnectCancel = model::cancelConnecting,
                        )
                    }
                }

                if (connecting || dragged != null) {
                    Text(
                        text = if (dragged?.targetId != null) {
                            words.releaseToConnect
                        } else if (dragged != null) {
                            words.releaseOnEmpty
                        } else {
                            words.tapNodesToConnect
                        },
                        style = Kajet.type.label,
                        color = colors.onAccent,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(colors.accent, RoundedCornerShape(Kajet.dimens.corner))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                // Dotknięta linia. Rozłączenie leży od razu pod ręką, bez chodzenia
                // po ustawieniach węzła.
                selectedEdge?.let { edgeId ->
                    val edge = map.edges.firstOrNull { it.id == edgeId }
                    if (edge != null) {
                        val byId = map.nodes.associateBy { it.id }
                        val names = listOfNotNull(byId[edge.fromId], byId[edge.toId])
                            .joinToString(" — ") { it.text.ifBlank { words.nodeWithoutName } }
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                                .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(names, style = Kajet.type.label, color = colors.text)
                            BarTextAction(words.disconnect) { model.disconnect(edgeId) }
                            IconAction(KajetIcons.Close, words.keepConnection, { model.selectEdge(null) })
                        }
                    }
                }

                /*
                  Dyskretny rachunek mapy w rogu planszy.

                  Na telefonie go nie ma: plansza ma tam nieco ponad 300 dp
                  szerokości, a sam pasek z tytułem sięga prawie do końca, więc
                  rachunek lądował na nim.
                */
                if (!narrow) {
                    Text(
                        text = words.mapTally(map.nodes.size, map.edges.size),
                        style = Kajet.type.meta,
                        color = colors.muted,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }

                // Przybliżenie przyciskami, z podglądem procentu. Środek ekranu
                // stoi w miejscu, więc mapa nie ucieka spod palca.
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        // Otwarty panel węzła zabiera dół planszy razem
                        // z przyciskami przybliżenia — wtedy wchodzą nad niego.
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = if (narrow && selected != null) panelHeight + 12.dp else 12.dp,
                        )
                        .background(colors.sheet.copy(alpha = 0.94f), RoundedCornerShape(Kajet.dimens.corner))
                        .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(
                        icon = KajetIcons.DividerLine,
                        description = words.zoomOut,
                        onClick = { zoomBy(0.9f) },
                        iconSize = 18.dp,
                        touchTarget = 40.dp,
                    )
                    Text(
                        text = words.percentOf((zoom * 100).roundToInt()),
                        style = Kajet.type.label,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 48.dp),
                    )
                    IconAction(
                        icon = KajetIcons.Plus,
                        description = words.zoomIn,
                        onClick = { zoomBy(1.1f) },
                        iconSize = 18.dp,
                        touchTarget = 40.dp,
                    )
                }

                selected?.let { id ->
                    val node = map.nodes.firstOrNull { it.id == id }
                    if (node != null) {
                        NodePanel(
                            node = node,
                            connections = model.nodeConnections(id),
                            recentColors = recentColors,
                            narrow = narrow,
                            panelHeight = panelHeight,
                            // Na wąskim ekranie panel wisi na dole całą szerokością,
                            // żeby nie zasłaniał planszy stojąc na jej środku.
                            modifier = if (narrow) {
                                Modifier.align(Alignment.BottomCenter)
                            } else {
                                Modifier.align(Alignment.BottomStart).padding(16.dp)
                            },
                            onText = { model.setText(id, it) },
                            onShape = { model.setShape(id, it) },
                            onColor = { model.setColor(id, it) },
                            onCustomColor = { model.setCustomColor(id, it) },
                            onTextColor = { model.setTextColor(id, it) },
                            onRememberColor = { model.rememberColor(it) },
                            onFont = { model.setFont(id, it) },
                            onFontSize = { model.setFontSize(id, it) },
                            onBold = { model.setBold(id, it) },
                            onItalic = { model.setItalic(id, it) },
                            onAlign = { model.setAlign(id, it) },
                            onDisconnect = model::disconnect,
                            onInkLabel = { model.openInkLabel(id) },
                            onDelete = { model.removeNode(id) },
                        )
                    }
                }

                if (map.nodes.isEmpty()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(words.emptyMap, style = Kajet.type.title, color = colors.text)
                        Text(
                            text = words.emptyMapAbout,
                            style = Kajet.type.body,
                            color = colors.muted,
                            modifier = Modifier.width(360.dp),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(colors.sheet.copy(alpha = 0.94f), RoundedCornerShape(Kajet.dimens.corner))
                    .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = document?.title.orEmpty(),
                    onValueChange = model::setTitle,
                    singleLine = true,
                    textStyle = Kajet.type.titleSmall.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    /*
                      Waga bez wypełniania. Tytuł ma się mierzyć PO wskaźniku
                      zapisu, nie przed nim: bez tego zjadał całą szerokość
                      paska, a na wskaźnik zostawało zero i „Zapisane 12:01"
                      łamało się po jednej literze. Przy krótkim tytule pasek
                      dalej zwija się do jego długości.
                    */
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(min = 80.dp, max = if (narrow) 160.dp else 280.dp),
                    decorationBox = { field ->
                        if (document?.title.isNullOrEmpty()) {
                            Text(words.unnamed, style = Kajet.type.titleSmall, color = colors.muted)
                        }
                        field()
                    },
                )
                SaveIndicator(state = saveState, lastSave = lastSave, inCloud = inCloud)
            }
        }

        if (toolbarOnRight) rail()
    }

    inkLabel?.let { id ->
        val node = map?.nodes?.firstOrNull { it.id == id }
        if (node != null) {
            DrawingDialog(
                onClose = model::closeInkLabel,
                onDone = { strokes, _, _ -> model.setInkLabel(id, strokes) },
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdge(
    from: MindNode,
    to: MindNode,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    color: Color,
    thick: Boolean = false,
) {
    val fromX = (from.x + from.width / 2f - offsetX) * zoom
    val fromY = (from.y + from.height / 2f - offsetY) * zoom
    val toX = (to.x + to.width / 2f - offsetX) * zoom
    val toY = (to.y + to.height / 2f - offsetY) * zoom

    val path = Path().apply {
        moveTo(fromX, fromY)
        val middleX = (fromX + toX) / 2f
        cubicTo(middleX, fromY, middleX, toY, toX, toY)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = (if (thick) 4f else 2f) * zoom, cap = StrokeCap.Round),
    )
}

/** Ile pikseli od linii jeszcze liczy się jako dotknięcie linii. */
private const val EDGE_TOUCH_REACH = 22f

/** Oddech wokół mapy przy zmieszczeniu jej całej w oknie. */
private const val FIT_PAD = 40f

@Composable
private fun NodeOnBoard(
    node: MindNode,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    selected: Boolean,
    edited: Boolean,
    connectTarget: Boolean,
    hasChildren: Boolean,
    hiddenCount: Int,
    narrow: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onText: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onCollapse: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    onConnectStart: () -> Unit,
    onConnectDrag: (Float, Float) -> Unit,
    onConnectEnd: () -> Unit,
    onConnectCancel: () -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val density = LocalDensity.current
    val color = nodeColor(node, colors.isDark)

    val left = (node.x - offsetX) * zoom
    val top = (node.y - offsetY) * zoom
    val width = node.width * zoom
    val height = node.height * zoom
    val shape = if (node.shape == NodeShape.OVAL) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(Kajet.dimens.corner)
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { width.toDp() },
                height = with(density) { height.toDp() },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(color = colors.sheet, shape = shape)
                .border(
                    // Węzeł pod ciągniętą linią dostaje grubszą obwódkę,
                    // żeby było widać, gdzie linia trafi po puszczeniu palca.
                    // Obwódka trzyma barwę węzła także przy zaznaczeniu —
                    // inaczej dobieranej barwy nie było widać na żywo.
                    width = if (connectTarget) 3.dp else if (selected) 2.dp else 1.5.dp,
                    color = if (connectTarget) colors.accent else color,
                    shape = shape,
                )
                .pointerInput(node.id) {
                    detectTapGestures(
                        onTap = { onSelect() },
                        onDoubleTap = { onEdit() },
                    )
                }
                .pointerInput(node.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                    ) { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                }
                .then(
                    // Zaznaczenie: akcentowy pierścień tuż pod obwódką barwy.
                    if (selected && !connectTarget) {
                        Modifier
                            .padding(3.dp)
                            .border(
                                width = 2.dp,
                                color = colors.accent,
                                shape = shape,
                            )
                    } else {
                        Modifier
                    },
                )
                /*
                  Wyściółka w jednostkach mapy, nie w dp.

                  Rozmiar węzła liczy MindMapSizes.fit, zakładając PAD_X = 20
                  jednostek mapy — a jednostka mapy to piksel urządzenia. Sztywne
                  10.dp na telefonie o gęstości 2,6 to 52 piksele zamiast 20,
                  czyli o 32 piksele mniej miejsca na hasło, niż przewidział
                  rachunek. Wiersz, który miał się zmieścić, schodził wtedy do
                  następnego i znikał pod dolną krawędzią węzła.

                  Na szerokim ekranie zostaje 10.dp przy 100%: tablet wygląda
                  jak dotąd. Przy oddalaniu wyściółka musi maleć razem z węzłem,
                  inaczej zjada hasło.
                */
                .padding(
                    horizontal = if (narrow) {
                        with(density) { (MindMapSizes.PAD_X / 2f * zoom).toDp() }
                    } else {
                        10.dp * zoom
                    },
                    vertical = if (narrow) {
                        with(density) { (MindMapSizes.PAD_Y / 2f * zoom).toDp() }
                    } else {
                        6.dp * zoom
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val fontPx = node.fontSize * zoom
            val style = Kajet.type.body.copy(
                fontFamily = fontFamilyFor(node.font),
                fontSize = with(density) { fontPx.toSp() },
                /*
                  Wysokość wiersza w em, nie w stałych sp z kroju body.

                  `Kajet.type.body` ma lineHeight = 24.sp. copy() zostawia tę
                  wartość, a fontSize maleje z zoomem. Compose układa glify
                  w ramce 24.sp od góry, a węzeł przycina resztę — przy 77%
                  hasło siedzi już przy dolnej krawędzi, przy dalszym
                  oddalaniu znika. em trzyma 1.3× fontSize na każdym zoomie.
                */
                lineHeight = MindMapSizes.LINE_RATIO.em,
                fontWeight = if (node.bold) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
                // Zero text colour means "pick one", so the theme colour keeps the
                // node readable in both light and dark.
                color = if (node.textColor != 0) Color(node.textColor) else colors.text,
                textAlign = when (node.align) {
                    NoteAlign.LEFT -> TextAlign.Start
                    NoteAlign.CENTER -> TextAlign.Center
                    NoteAlign.RIGHT -> TextAlign.End
                },
            )
            if (edited) {
                BasicTextField(
                    value = node.text,
                    onValueChange = onText,
                    textStyle = style,
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = node.text.ifEmpty { if (node.ink.isEmpty()) words.tapTwiceToType else "" },
                    style = if (node.text.isEmpty()) style.copy(color = colors.muted) else style,
                    // Hasło dłuższe niż węzeł kończy się wielokropkiem, a nie
                    // urwaniem w pół litery — widać wtedy, że dalej coś jest.
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        /*
         * Handle do ciągnięcia linii.
         *
         * Pojawia się dopiero przy zaznaczonym węźle, żeby plansza nie była
         * usiana kropkami. Leży przy prawej krawędzi, poza obrysem węzła,
         * więc nie zabiera miejsca tekstowi i nie myli się z przesuwaniem.
         */
        if (selected) {
            val side = with(density) { (26f * zoom).coerceIn(24f, 40f).toDp() }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset((width / 2f).roundToInt() + 2, 0) }
                    .size(side)
                    .background(colors.accent, RoundedCornerShape(percent = 50))
                    .border(2.dp, colors.sheet, RoundedCornerShape(percent = 50))
                    .semantics { contentDescription = words.dragToConnect }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { onConnectStart() },
                            onDragEnd = { onConnectEnd() },
                            onDragCancel = { onConnectCancel() },
                        ) { change, _ ->
                            change.consume()
                            // Położenie palca liczymy względem lewego górnego rogu
                            // planszy, bo model przelicza je potem na układ mapy.
                            onConnectDrag(
                                left + width + change.position.x,
                                top + height / 2f - side.toPx() / 2f + change.position.y,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KajetIcons.Connect,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(with(density) { (13f * zoom).coerceIn(12f, 18f).toDp() }),
                )
            }

            // Uchwyt zmiany rozmiaru w prawym dolnym rogu. Puszczenie palca
            // dopisuje zmianę do historii (resizeNode z finished = true).
            val grip = with(density) { (22f * zoom).coerceIn(20f, 34f).toDp() }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        val half = (grip.toPx() / 2f).roundToInt()
                        IntOffset(half, half)
                    }
                    .size(grip)
                    .background(colors.sheet, RoundedCornerShape(6.dp))
                    .border(2.dp, colors.accent, RoundedCornerShape(6.dp))
                    .semantics { contentDescription = words.dragToResize }
                    // Klucz z zoomem: po zmianie przybliżenia delta palca musi
                    // być dzielona świeżą wartością, nie tą sprzed gestu.
                    .pointerInput(node.id, zoom) {
                        detectDragGestures(
                            onDragStart = { onResizeStart() },
                            onDragEnd = { onResizeEnd() },
                            onDragCancel = { onResizeEnd() },
                        ) { change, drag ->
                            change.consume()
                            onResize(drag.x, drag.y)
                        }
                    },
            )
        }

        if (hasChildren) {
            val chevron = (22f * zoom).coerceIn(18f, 34f)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (height / 2f).roundToInt() + 2) }
                    .size(with(density) { chevron.toDp() })
                    .background(colors.sheet, RoundedCornerShape(percent = 50))
                    .border(1.dp, color, RoundedCornerShape(percent = 50))
                    .clickable(onClickLabel = if (node.collapsed) words.expandBranch else words.collapseBranch, onClick = onCollapse),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (node.collapsed) KajetIcons.ArrowRight else KajetIcons.ArrowDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
            }
            // Zwinięta gałąź zdradza obok chevrona, ile węzłów siedzi w środku.
            if (node.collapsed && hiddenCount > 0) {
                Text(
                    text = "$hiddenCount",
                    style = Kajet.type.meta,
                    color = colors.muted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(chevron.roundToInt() + 6, (height / 2f).roundToInt() + 2) }
                        .background(colors.sheet, RoundedCornerShape(percent = 50))
                        .border(1.dp, color, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

private fun nodeColor(node: MindNode, dark: Boolean): Color =
    if (node.customColor != 0) Color(node.customColor) else FolderColor.fromId(node.colorId).color(dark)

@Composable
private fun NodePanel(
    node: MindNode,
    connections: List<Pair<MindEdge, MindNode>>,
    recentColors: List<Int>,
    narrow: Boolean,
    /** Liczona przez planszę, bo ona odsuwa spod panelu zaznaczony węzeł. */
    panelHeight: Dp,
    modifier: Modifier,
    onText: (String) -> Unit,
    onShape: (NodeShape) -> Unit,
    onColor: (String) -> Unit,
    onCustomColor: (Int) -> Unit,
    onTextColor: (Int) -> Unit,
    /** Dokłada barwę do spisu „twoich kolorów" - po zamknięciu okna z tęczą. */
    onRememberColor: (Int) -> Unit,
    onFont: (NoteFont) -> Unit,
    onFontSize: (Float) -> Unit,
    onBold: (Boolean) -> Unit,
    onItalic: (Boolean) -> Unit,
    onAlign: (NoteAlign) -> Unit,
    onDisconnect: (String) -> Unit,
    onInkLabel: () -> Unit,
    onDelete: () -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    var nodeColourPicker by remember { mutableStateOf(false) }
    var textColourPicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Na telefonie panel wisi na dole całą szerokością i nie przerasta
    // połowy ekranu, na tablecie stoi jak dotąd w rogu planszy.
    Column(
        modifier
            .then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(320.dp))
            .heightIn(max = panelHeight)
            .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(words.selectedNode, modifier = Modifier.weight(1f))
            IconAction(
                icon = if (expanded) KajetIcons.ArrowDown else KajetIcons.ArrowRight,
                description = if (expanded) words.hideSettings else words.showMoreSettings,
                onClick = { expanded = !expanded },
                iconSize = 16.dp,
                touchTarget = 32.dp,
            )
        }

        // Treść węzła da się poprawić tutaj, bez celowania w mały napis na planszy.
        SectionLabel(words.nodeText)
        BasicTextField(
            value = node.text,
            onValueChange = { onText(it.take(500)) },
            textStyle = Kajet.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 120.dp)
                .padding(vertical = 4.dp),
            decorationBox = { field ->
                if (node.text.isEmpty()) {
                    Text(words.typeNodeText, style = Kajet.type.body, color = colors.muted)
                }
                field()
            },
        )
        HorizontalRule(color = colors.muted.copy(alpha = 0.5f))

        SectionLabel(words.textLabel)
        SegmentedChoice(
            options = NoteFont.entries,
            selected = node.font,
            name = { it.label(words) },
            onSelect = onFont,
        )

        SettingSlider(
            name = words.fontSize,
            value = node.fontSize,
            range = 8f..48f,
            onChange = onFontSize,
            readout = { words.pointsOf(it.roundToInt().toString()) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconToggle(
                icon = KajetIcons.Bold,
                description = words.bold,
                checked = node.bold,
                onCheckedChange = onBold,
            )
            IconToggle(
                icon = KajetIcons.Italic,
                description = words.italic,
                checked = node.italic,
                onCheckedChange = onItalic,
            )
            Box(Modifier.width(8.dp))
            NoteAlign.entries.forEach { align ->
                IconAction(
                    icon = when (align) {
                        NoteAlign.LEFT -> KajetIcons.AlignLeft
                        NoteAlign.CENTER -> KajetIcons.AlignCentre
                        NoteAlign.RIGHT -> KajetIcons.AlignRight
                    },
                    description = align.label(words),
                    onClick = { onAlign(align) },
                    selected = node.align == align,
                    touchTarget = 40.dp,
                )
            }
        }

        HorizontalRule()

        SectionLabel(words.nodeColour)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderColor.entries.forEach { variant ->
                ColourDot(
                    color = variant.color(colors.isDark).toArgb(),
                    description = "${words.colourNamed} ${variant.label(words)}",
                    onClick = { onColor(variant.id) },
                    selected = node.customColor == 0 && node.colorId == variant.id,
                    diameter = 20.dp,
                )
            }
            IconAction(KajetIcons.ColorSwatch, words.pickOwnColour, { nodeColourPicker = true })
        }

        // Połączenia stoją poza zwijaną częścią. Rozłączanie to zwykła czynność,
        // a nie ustawienie, którego szuka się raz na rok.
        if (connections.isNotEmpty()) {
            HorizontalRule()
            SectionLabel(words.connections)
            Text(
                text = words.connectionsAbout,
                style = Kajet.type.meta,
                color = colors.muted,
            )
            connections.forEach { (edge, neighbour) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = neighbour.text.ifBlank { words.nodeWithoutName },
                        style = Kajet.type.body,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        icon = KajetIcons.Close,
                        description = words.disconnectFrom(neighbour.text.ifBlank { words.thisNode() }),
                        onClick = { onDisconnect(edge.id) },
                        iconSize = 14.dp,
                        touchTarget = 36.dp,
                    )
                }
            }
        }

        if (expanded) {
            SectionLabel(words.nodeTextColour)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColourDot(
                    color = if (node.textColor != 0) node.textColor else colors.text.toArgb(),
                    description = words.nodeTextColour,
                    onClick = { textColourPicker = true },
                )
                Text(
                    text = if (node.textColor == 0) words.colourFromTheme else words.colourOwn,
                    style = Kajet.type.meta,
                    color = colors.muted,
                )
            }

            SectionLabel(words.shapeLabel)
            SegmentedChoice(
                options = NodeShape.entries,
                selected = node.shape,
                name = { it.label(words) },
                onSelect = onShape,
            )

        }

        HorizontalRule()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(words.inkLabel, onInkLabel, icon = KajetIcons.Pen)
            SecondaryButton(words.delete, onDelete, icon = KajetIcons.Bin, color = colors.danger)
        }
    }

    if (nodeColourPicker) {
        ColourPickerDialog(
            title = words.nodeColourTitle,
            color = if (node.customColor != 0) {
                node.customColor
            } else {
                FolderColor.fromId(node.colorId).color(colors.isDark).toArgb()
            },
            onChange = onCustomColor,
            onClose = { nodeColourPicker = false; onRememberColor(node.customColor) },
            withAlpha = false,
            recentColors = recentColors,
        )
    }

    if (textColourPicker) {
        ColourPickerDialog(
            title = words.nodeTextColourTitle,
            color = if (node.textColor != 0) node.textColor else colors.text.toArgb(),
            onChange = onTextColor,
            onClose = { textColourPicker = false; onRememberColor(node.textColor) },
            withAlpha = false,
            recentColors = recentColors,
        )
    }
}

/*
  Akcja paska, nie SecondaryButton. Ten ma 48 dp i obwódkę — w ciasnej
  karcie krawędzi odcinał się od tła i na telefonie łamał etykietę
  („Rozłącz"). Tu ten sam krój co nazwy węzłów, bez ramki.
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
