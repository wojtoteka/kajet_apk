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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import wojtoteka.ovh.kajet.editor.SaveIndicator
import wojtoteka.ovh.kajet.editor.text.DrawingDialog
import kotlin.math.roundToInt

@Composable
fun MindMapEditor(
    model: MindMapViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val document by model.document.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val edited by model.edited.collectAsStateWithLifecycle()
    val connecting by model.connecting.collectAsStateWithLifecycle()
    val inkLabel by model.inkLabel.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
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

    Row(Modifier.fillMaxSize().background(colors.desk)) {

        Column(
            Modifier
                .width(Kajet.dimens.railWidth)
                .fillMaxSize()
                .background(colors.desk)
                .marginRule(colors.line)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconAction(KajetIcons.BackArrow, "Wróć do biblioteki", { model.saveNow(); onBack() })
            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.NodeDot,
                description = "Nowy węzeł",
                onClick = { model.addNode(offsetX + 120f, offsetY + 120f) },
            )
            IconAction(
                icon = KajetIcons.Plus,
                description = "Dodaj gałąź do wybranego węzła",
                onClick = { selected?.let { model.addChild(it) } },
                enabled = selected != null,
            )
            IconAction(
                icon = KajetIcons.ShareArrow,
                description = if (connecting) "Przerwij łączenie" else "Połącz dwa węzły",
                onClick = model::toggleConnecting,
                selected = connecting,
                enabled = selected != null || connecting,
            )
            IconAction(
                icon = KajetIcons.Pen,
                description = "Podpis rysikiem",
                onClick = { selected?.let { model.openInkLabel(it) } },
                enabled = selected != null,
            )

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.MindMapIcon, "Rozłóż gałęzie automatycznie", model::arrangeBranches)
            IconAction(
                icon = KajetIcons.FitToView,
                description = "Wróć do środka",
                onClick = {
                    offsetX = 0f
                    offsetY = 0f
                    zoom = 1f
                },
            )

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.Undo, "Cofnij", model::undo, enabled = canUndo)
            IconAction(KajetIcons.Redo, "Ponów", model::redo, enabled = canRedo)

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.Favourites,
                description = if (document?.favorite == true) "Usuń z ulubionych" else "Dodaj do ulubionych",
                onClick = model::toggleFavorite,
                selected = document?.favorite == true,
            )
            IconAction(KajetIcons.Export, "Eksportuj mapę", onExport)
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.fillMaxSize()) {
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
                            detectTapGestures(onTap = { touch ->
                                // Dotknięcie linii łapie linię, dotknięcie pustego
                                // miejsca odznacza wszystko.
                                val edge = model.edgeAt(
                                    x = touch.x / zoom + offsetX,
                                    y = touch.y / zoom + offsetY,
                                    reach = EDGE_TOUCH_REACH / zoom,
                                )
                                if (edge != null) model.selectEdge(edge) else model.select(null)
                            })
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
                                color = if (picked) colors.accent else colors.line,
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
                            onSelect = { model.select(node.id) },
                            onEdit = { model.edit(node.id) },
                            onText = { model.setText(node.id, it) },
                            onDragStart = model::startDragging,
                            onDrag = { dx, dy -> model.moveNode(node.id, dx / zoom, dy / zoom) },
                            onDragEnd = model::finishDragging,
                            onCollapse = { model.toggleCollapsed(node.id) },
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
                            "Puść, żeby połączyć."
                        } else if (dragged != null) {
                            "Puść na pustym miejscu, żeby dołożyć tam nowy węzeł."
                        } else {
                            "Dotknij drugiego węzła, żeby połączyć go linią."
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
                            .joinToString(" — ") { it.text.ifBlank { "węzeł bez nazwy" } }
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
                            SecondaryButton(
                                text = "Rozłącz",
                                onClick = { model.disconnect(edgeId) },
                                icon = KajetIcons.Bin,
                                color = colors.danger,
                            )
                            IconAction(KajetIcons.Close, "Zostaw połączenie", { model.selectEdge(null) })
                        }
                    }
                }

                selected?.let { id ->
                    val node = map.nodes.firstOrNull { it.id == id }
                    if (node != null) {
                        NodePanel(
                            node = node,
                            connections = model.nodeConnections(id),
                            recentColors = recentColors,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            onShape = { model.setShape(id, it) },
                            onColor = { model.setColor(id, it) },
                            onCustomColor = { model.setCustomColor(id, it) },
                            onTextColor = { model.setTextColor(id, it) },
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
                        Text("Pusta mapa", style = Kajet.type.title, color = colors.text)
                        Text(
                            text = "Dodaj pierwszy węzeł przyciskiem po lewej stronie, a potem doczepiaj do niego gałęzie.",
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
                    modifier = Modifier.widthIn(min = 80.dp, max = 280.dp),
                    decorationBox = { field ->
                        if (document?.title.isNullOrEmpty()) {
                            Text("Bez nazwy", style = Kajet.type.titleSmall, color = colors.muted)
                        }
                        field()
                    },
                )
                SaveIndicator(state = saveState, lastSave = lastSave)
            }
        }
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
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onText: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onCollapse: () -> Unit,
    onConnectStart: () -> Unit,
    onConnectDrag: (Float, Float) -> Unit,
    onConnectEnd: () -> Unit,
    onConnectCancel: () -> Unit,
) {
    val colors = Kajet.colors
    val density = LocalDensity.current
    val color = nodeColor(node, colors.isDark)

    val left = (node.x - offsetX) * zoom
    val top = (node.y - offsetY) * zoom
    val width = node.width * zoom
    val height = node.height * zoom

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
                .background(
                    color = colors.sheet,
                    shape = if (node.shape == NodeShape.OVAL) {
                        RoundedCornerShape(percent = 50)
                    } else {
                        RoundedCornerShape(Kajet.dimens.corner)
                    },
                )
                .border(
                    // Węzeł pod ciągniętą linią dostaje grubszą obwódkę,
                    // żeby było widać, gdzie linia trafi po puszczeniu palca.
                    width = if (connectTarget) 3.dp else if (selected) 2.dp else 1.5.dp,
                    color = if (connectTarget || selected) colors.accent else color,
                    shape = if (node.shape == NodeShape.OVAL) {
                        RoundedCornerShape(percent = 50)
                    } else {
                        RoundedCornerShape(Kajet.dimens.corner)
                    },
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
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = Kajet.type.body.copy(
                fontFamily = fontFamilyFor(node.font),
                fontSize = with(density) { (node.fontSize * zoom).toSp() },
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
                    text = node.text.ifEmpty { if (node.ink.isEmpty()) "Dotknij dwa razy, aby wpisać" else "" },
                    style = if (node.text.isEmpty()) style.copy(color = colors.muted) else style,
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
                    .semantics { contentDescription = "Pociągnij, żeby połączyć z innym węzłem" }
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
        }

        if (hasChildren) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (height / 2f).roundToInt() + 2) }
                    .size(with(density) { (22f * zoom).coerceIn(18f, 34f).toDp() })
                    .background(colors.sheet, RoundedCornerShape(percent = 50))
                    .border(1.dp, color, RoundedCornerShape(percent = 50))
                    .clickable(onClickLabel = if (node.collapsed) "Rozwiń gałąź" else "Zwiń gałąź", onClick = onCollapse),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (node.collapsed) KajetIcons.ArrowRight else KajetIcons.ArrowDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
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
    modifier: Modifier,
    onShape: (NodeShape) -> Unit,
    onColor: (String) -> Unit,
    onCustomColor: (Int) -> Unit,
    onTextColor: (Int) -> Unit,
    onFont: (NoteFont) -> Unit,
    onFontSize: (Float) -> Unit,
    onBold: (Boolean) -> Unit,
    onItalic: (Boolean) -> Unit,
    onAlign: (NoteAlign) -> Unit,
    onDisconnect: (String) -> Unit,
    onInkLabel: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Kajet.colors
    var nodeColourPicker by remember { mutableStateOf(false) }
    var textColourPicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier
            .width(320.dp)
            .heightIn(max = 460.dp)
            .background(colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
            .border(1.dp, colors.line, RoundedCornerShape(Kajet.dimens.corner))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Wybrany węzeł", modifier = Modifier.weight(1f))
            IconAction(
                icon = if (expanded) KajetIcons.ArrowDown else KajetIcons.ArrowRight,
                description = if (expanded) "Schowaj ustawienia" else "Pokaż więcej ustawień",
                onClick = { expanded = !expanded },
                iconSize = 16.dp,
                touchTarget = 32.dp,
            )
        }

        SectionLabel("Pismo")
        SegmentedChoice(
            options = NoteFont.entries,
            selected = node.font,
            name = { it.labelPl },
            onSelect = onFont,
        )

        SettingSlider(
            name = "Wielkość pisma",
            value = node.fontSize,
            range = 8f..48f,
            onChange = onFontSize,
            readout = { "${it.roundToInt()} pkt" },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconToggle(
                icon = KajetIcons.Bold,
                description = "Pogrubienie",
                checked = node.bold,
                onCheckedChange = onBold,
            )
            IconToggle(
                icon = KajetIcons.Italic,
                description = "Kursywa",
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
                    description = align.labelPl,
                    onClick = { onAlign(align) },
                    selected = node.align == align,
                    touchTarget = 40.dp,
                )
            }
        }

        HorizontalRule()

        SectionLabel("Kolor węzła")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderColor.entries.forEach { variant ->
                ColourDot(
                    color = variant.color(colors.isDark).toArgb(),
                    description = "Kolor ${variant.labelPl}",
                    onClick = { onColor(variant.id) },
                    selected = node.customColor == 0 && node.colorId == variant.id,
                    diameter = 20.dp,
                )
            }
            IconAction(KajetIcons.ColorSwatch, "Dobierz własny kolor", { nodeColourPicker = true })
        }

        // Połączenia stoją poza zwijaną częścią. Rozłączanie to zwykła czynność,
        // a nie ustawienie, którego szuka się raz na rok.
        if (connections.isNotEmpty()) {
            HorizontalRule()
            SectionLabel("Połączenia")
            Text(
                text = "Możesz też dotknąć linii na planszy i rozłączyć ją tam.",
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
                        text = neighbour.text.ifBlank { "Węzeł bez nazwy" },
                        style = Kajet.type.body,
                        color = colors.text,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        icon = KajetIcons.Close,
                        description = "Rozłącz z ${neighbour.text.ifBlank { "tym węzłem" }}",
                        onClick = { onDisconnect(edge.id) },
                        iconSize = 14.dp,
                        touchTarget = 36.dp,
                    )
                }
            }
        }

        if (expanded) {
            SectionLabel("Kolor pisma")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColourDot(
                    color = if (node.textColor != 0) node.textColor else colors.text.toArgb(),
                    description = "Kolor pisma",
                    onClick = { textColourPicker = true },
                )
                Text(
                    text = if (node.textColor == 0) "dobierany do motywu" else "własny",
                    style = Kajet.type.meta,
                    color = colors.muted,
                )
            }

            SectionLabel("Kształt")
            SegmentedChoice(
                options = NodeShape.entries,
                selected = node.shape,
                name = { it.labelPl },
                onSelect = onShape,
            )

        }

        HorizontalRule()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Podpis rysikiem", onInkLabel, icon = KajetIcons.Pen)
            SecondaryButton("Skasuj", onDelete, icon = KajetIcons.Bin, color = colors.danger)
        }
    }

    if (nodeColourPicker) {
        ColourPickerDialog(
            title = "Kolor węzła",
            color = if (node.customColor != 0) {
                node.customColor
            } else {
                FolderColor.fromId(node.colorId).color(colors.isDark).toArgb()
            },
            onChange = onCustomColor,
            onClose = { nodeColourPicker = false },
            withAlpha = false,
            recentColors = recentColors,
        )
    }

    if (textColourPicker) {
        ColourPickerDialog(
            title = "Kolor pisma w węźle",
            color = if (node.textColor != 0) node.textColor else colors.text.toArgb(),
            onChange = onTextColor,
            onClose = { textColourPicker = false },
            withAlpha = false,
            recentColors = recentColors,
        )
    }
}
