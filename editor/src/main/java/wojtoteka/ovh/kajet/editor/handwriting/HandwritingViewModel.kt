package wojtoteka.ovh.kajet.editor.handwriting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.awaria.failureHandler
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.image.Bitmaps
import wojtoteka.ovh.kajet.core.text.words
import android.graphics.Bitmap
import wojtoteka.ovh.kajet.core.model.ImageElement
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.ShapeElement
import wojtoteka.ovh.kajet.core.model.ShapeKind
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.editor.StrokeChange
import wojtoteka.ovh.kajet.editor.FieldChange
import wojtoteka.ovh.kajet.editor.ImageChange
import wojtoteka.ovh.kajet.editor.PageChange
import wojtoteka.ovh.kajet.editor.ShapeChange
import wojtoteka.ovh.kajet.editor.page
import wojtoteka.ovh.kajet.editor.withPage
import wojtoteka.ovh.kajet.ink.Strokes
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.Brushes
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.ink.ShapeGeometry
import wojtoteka.ovh.kajet.ink.ShapeSettings
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.FingerBehavior
import wojtoteka.ovh.kajet.storage.RememberedPen
import wojtoteka.ovh.kajet.storage.RememberedShape
import java.util.UUID

class HandwritingViewModel(
    private val repo: LibraryRepository,
    settings: SettingsStore,
    path: String,
    inkColor: Int,
    highlighterColor: Int,
) : NoteViewModel(repo, settings, path) {

    private val _tool = MutableStateFlow(EditorTool.PEN)
    val tool: StateFlow<EditorTool> = _tool.asStateFlow()

    private val _pens = MutableStateFlow(
        PenSettings(penColor = inkColor, highlighterColor = highlighterColor),
    )
    val pens: StateFlow<PenSettings> = _pens.asStateFlow()

    // Zanim ustawienia wczytają się z dysku, palec przewija. Kartka daje się
    // wtedy przesunąć od pierwszego dotknięcia, a nie zostaje na niej kreska.
    private val _fingerDraws = MutableStateFlow(false)
    val fingerDraws: StateFlow<Boolean> = _fingerDraws.asStateFlow()

    fun toggleFinger() {
        val next = !_fingerDraws.value
        _fingerDraws.value = next
        viewModelScope.launch {
            runCatching {
                settings.setFingerBehavior(
                    if (next) FingerBehavior.DRAW else FingerBehavior.SCROLL,
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            runCatching { settings.settings.first().fingerBehavior }
                .getOrNull()
                ?.let { _fingerDraws.value = it == FingerBehavior.DRAW }
        }

        // Pisak dobrany w poprzedniej notatce wraca tu, zanim człowiek zacznie pisać.
        viewModelScope.launch {
            val saved = runCatching { settings.settings.first().pens }.getOrNull() ?: return@launch
            _pens.value = _pens.value.copy(
                penKind = saved.tool
                    ?.let { name -> runCatching { InkTool.valueOf(name) }.getOrNull() }
                    ?: _pens.value.penKind,
                /*
                  Zapamiętany dawny „Biały" zlewał się z kartką; wraca kolor
                  domyślny. Tak samo zwykły atrament: zapamiętany na jasnej
                  kartce jest czarny i na ciemnej byłby niewidoczny, więc
                  zamiast go przywracać, zostaje atrament TEJ kartki (przyszedł
                  z ekranu notatki jako `inkColor` i już siedzi w _pens).
                  Kolory dobrane świadomie - czerwony, zielony - wracają.
                */
                penColor = saved.color
                    ?.takeUnless { it == InkPalette.LEGACY_WHITE_ARGB }
                    ?.takeUnless { InkPalette.isDefaultInk(it) }
                    ?: _pens.value.penColor,
                penWidth = saved.width ?: _pens.value.penWidth,
                penOpacity = saved.opacity ?: _pens.value.penOpacity,
                highlighterColor = saved.highlighterColor ?: _pens.value.highlighterColor,
                highlighterWidth = saved.highlighterWidth ?: _pens.value.highlighterWidth,
                highlighterOpacity = saved.highlighterOpacity
                    ?: _pens.value.highlighterOpacity,
                eraserRadius = saved.eraserRadius ?: _pens.value.eraserRadius,
            )
        }
    }

    private fun updatePen(transform: (PenSettings) -> PenSettings) {
        val next = transform(_pens.value)
        _pens.value = next
        viewModelScope.launch {
            runCatching {
                settings.setPen(
                    RememberedPen(tool = next.penKind.name, color = next.penColor, width = next.penWidth, opacity = next.penOpacity,
                        highlighterColor = next.highlighterColor,
                        highlighterWidth = next.highlighterWidth,
                        highlighterOpacity = next.highlighterOpacity,
                        eraserRadius = next.eraserRadius,
                    ),
                )
            }
        }
    }

    private val _selected = MutableStateFlow<List<InkStroke>>(emptyList())
    val selected: StateFlow<List<InkStroke>> = _selected.asStateFlow()

    private val _selectionPage = MutableStateFlow(-1)
    val selectionPage: StateFlow<Int> = _selectionPage.asStateFlow()

    private val _editedBox = MutableStateFlow<String?>(null)
    val editedBox: StateFlow<String?> = _editedBox.asStateFlow()

    // Zbieranie jednego pociągnięcia gumki albo jednego przesunięcia zaznaczenia
    private var pendingRemoved = mutableListOf<Pair<Int, InkStroke>>()
    private var pendingAdded = mutableListOf<InkStroke>()
    private var pendingPage = -1
    private var dragTotalX = 0f
    private var dragTotalY = 0f

    fun selectTool(next: EditorTool) {
        _tool.value = next
        if (next != EditorTool.LASSO) deselect()
        // Kształt zostaje wzięty tylko przy narzędziach, które umieją go ruszyć.
        if (next != EditorTool.SHAPES && next != EditorTool.LASSO) selectShape(null)
    }

    fun setPenKind(kind: InkTool) = updatePen { it.copy(penKind = kind) }

    /**
     * Sama barwa pisma. Do spisu „twoich kolorów" nie trafia od razu:
     * tęcza w oknie koloru woła to przy każdym drgnięciu palca, więc jedno
     * dobranie barwy zapychało cały spis odcieniami mijanymi po drodze.
     * Zapamiętuje dopiero [rememberColor], wołane po zamknięciu okna.
     */
    fun setPenColor(argb: Int) {
        updatePen { it.copy(penColor = argb) }
    }

    fun setPenWidth(width: Float) = updatePen {
        it.copy(penWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH))
    }

    fun setPenOpacity(opacity: Float) = updatePen {
        it.copy(penOpacity = opacity.coerceIn(0.05f, 1f))
    }

    /** Jak [setPenColor] - zapamiętanie barwy zostawiamy na zamknięcie okna. */
    fun setHighlighterColor(argb: Int) {
        updatePen { it.copy(highlighterColor = argb) }
    }

    fun setHighlighterWidth(width: Float) = updatePen {
        it.copy(highlighterWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH))
    }

    fun setHighlighterOpacity(opacity: Float) = updatePen {
        it.copy(highlighterOpacity = opacity.coerceIn(0.05f, 1f))
    }

    fun setEraserRadius(radius: Float) = updatePen {
        it.copy(eraserRadius = radius.coerceIn(2f, 80f))
    }

    // Kreski

    fun addStroke(page: Int, stroke: InkStroke) {
        perform(StrokeChange(page = page, added = listOf(stroke)))
        growIfNeeded(page)
    }

    private fun growIfNeeded(page: Int) {
        val document = document.value ?: return
        val handwriting = document.handwriting ?: return
        if (page != handwriting.pages.lastIndex) return
        val sheet = handwriting.pages[page]
        val lowestInk = sheet.strokes.maxOfOrNull { it.bounds().bottom }
        val lowestShape = sheet.shapes.maxOfOrNull { ShapeGeometry.bounds(it).bottom }
        val lowest = when {
            lowestInk == null -> lowestShape ?: return
            lowestShape == null -> lowestInk
            else -> maxOf(lowestInk, lowestShape)
        }
        if (lowest < sheet.height - GROW_THRESHOLD) return

        val pages = handwriting.pages.toMutableList()
        if (handwriting.pageMode == PageMode.SCROLL) {
            pages[page] = sheet.copy(height = sheet.height + NotePage.SCROLL_STEP)
        } else {
            pages += NotePage(
                id = UUID.randomUUID().toString(),
                width = sheet.width,
                height = sheet.height,
            )
        }
        perform(PageChange(before = handwriting.pages, after = pages))
    }

    fun addPage() {
        val handwriting = document.value?.handwriting ?: return
        val last = handwriting.pages.lastOrNull()
        val pages = handwriting.pages + NotePage(
            id = UUID.randomUUID().toString(),
            width = last?.width ?: NotePage.A4_WIDTH,
            height = last?.height ?: NotePage.A4_HEIGHT,
        )
        perform(PageChange(before = handwriting.pages, after = pages))
    }

    fun removePage(index: Int) {
        val handwriting = document.value?.handwriting ?: return
        if (handwriting.pages.size <= 1) return
        if (index !in handwriting.pages.indices) return
        val pages = handwriting.pages.toMutableList().also { it.removeAt(index) }
        // Zaznaczenie pamięta numer strony. Po skasowaniu strony ten numer
        // wskazywałby w pustkę, a obrys zaznaczenia leci przy rysowaniu kartki.
        deselect()
        perform(PageChange(before = handwriting.pages, after = pages))
    }

    fun removeLastPage() {
        val handwriting = document.value?.handwriting ?: return
        removePage(handwriting.pages.lastIndex)
    }

    fun setBackground(background: PageBackground) {
        val document = document.value ?: return
        val handwriting = document.handwriting ?: return
        editWithoutHistory { it.copy(handwriting = handwriting.copy(background = background)) }
    }

    fun setPageMode(mode: PageMode) {
        val handwriting = document.value?.handwriting ?: return
        if (handwriting.pageMode == mode) return

        // Tryb przewijania scala wszystkie strony w jedną, więc numer strony
        // zapamiętany przez zaznaczenie przestaje istnieć. Bez tego obrys
        // zaznaczenia sięgał poza spis stron przy rysowaniu.
        deselect()

        if (mode == PageMode.A4 || handwriting.pages.size <= 1) {
            editWithoutHistory { it.copy(handwriting = handwriting.copy(pageMode = mode)) }
            return
        }

        var top = 0f
        val strokes = mutableListOf<InkStroke>()
        val boxes = mutableListOf<TextBoxElement>()
        val images = mutableListOf<ImageElement>()
        val shapes = mutableListOf<ShapeElement>()
        for (sheet in handwriting.pages) {
            strokes += sheet.strokes.map { it.translated(0f, top) }
            boxes += sheet.texts.map { it.copy(y = it.y + top) }
            images += sheet.images.map { it.copy(y = it.y + top) }
            shapes += sheet.shapes.map { it.movedBy(0f, top) }
            top += sheet.height
        }

        val merged = handwriting.pages.first().copy(
            height = top,
            strokes = strokes,
            texts = boxes,
            images = images,
            shapes = shapes,
            recognized = emptyList(),
        )
        editWithoutHistory {
            it.copy(handwriting = handwriting.copy(pageMode = mode, pages = listOf(merged)))
        }
    }

    // Gumka

    fun erase(page: Int, x: Float, y: Float, radius: Float, wholeStroke: Boolean) {
        // Kształt jest obiektem: gumka do całej kreski kasuje go w całości,
        // zwykła gumka go nie tyka, bo obiektu nie da się przeciąć w połowie.
        if (wholeStroke) eraseShapes(page, x, y, radius)

        val sheet = document.value?.page(page) ?: return
        if (pendingPage != page) {
            flushPending()
            pendingPage = page
        }

        val removed = mutableListOf<Pair<Int, InkStroke>>()
        val added = mutableListOf<InkStroke>()

        sheet.strokes.forEachIndexed { index, stroke ->
            if (!Strokes.hitsCircle(stroke, x, y, radius)) return@forEachIndexed
            removed += index to stroke
            if (!wholeStroke) {
                added += Strokes.cutFragment(stroke, x, y, radius)
            }
        }
        if (removed.isEmpty()) return

        // Zmieniamy dokument od razu, żeby gumka reagowała pod palcem,
        // ale do historii wpisujemy dopiero całe pociągnięcie.
        editWithoutHistory { StrokeChange(page, removed, added).applyTo(it) }
        pendingRemoved += removed
        pendingAdded.removeAll { stroke -> removed.any { it.second.id == stroke.id } }
        pendingAdded += added
    }

    private fun eraseShapes(page: Int, x: Float, y: Float, radius: Float) {
        val sheet = document.value?.page(page) ?: return
        if (sheet.shapes.isEmpty()) return
        val left = sheet.shapes.filterNot { ShapeGeometry.hits(it, x, y, radius) }
        if (left.size == sheet.shapes.size) return
        val gone = sheet.shapes.filterNot { kept -> left.any { it.id == kept.id } }
        perform(ShapeChange(page, sheet.shapes, left))
        if (gone.any { it.id == _selectedShape.value }) _selectedShape.value = null
    }

    fun eraseFinished() = flushPending()

    private fun flushPending() {
        if (pendingPage >= 0 && pendingRemoved.isNotEmpty()) {
            history.record(
                StrokeChange(pendingPage, pendingRemoved.toList(), pendingAdded.toList()),
            )
            refreshButtons()
        }
        pendingRemoved = mutableListOf()
        pendingAdded = mutableListOf()
        pendingPage = -1
    }

    // Lasso

    fun selectWithLasso(page: Int, polygon: List<Float>) {
        val sheet = document.value?.page(page) ?: return
        val hit = sheet.strokes.filter { Strokes.inLasso(it, polygon) }
        _selected.value = hit
        _selectionPage.value = if (hit.isEmpty()) -1 else page
    }

    fun deselect() {
        _selected.value = emptyList()
        _selectionPage.value = -1
        _selectedShape.value = null
    }

    fun moveSelection(dx: Float, dy: Float, finished: Boolean) {
        val page = _selectionPage.value
        if (page < 0) return

        if (!finished) {
            dragTotalX += dx
            dragTotalY += dy
            val moved = _selected.value.map { it.translated(dx, dy) }
            val oldIds = _selected.value.map { it.id }.toSet()
            editWithoutHistory { document ->
                val sheet = document.page(page) ?: return@editWithoutHistory document
                val strokes = sheet.strokes.map { stroke ->
                    if (stroke.id in oldIds) stroke.translated(dx, dy) else stroke
                }
                document.withPage(page) { it.copy(strokes = strokes) }
            }
            _selected.value = moved
            return
        }

        if (dragTotalX != 0f || dragTotalY != 0f) {
            val sheet = document.value?.page(page)
            if (sheet != null) {
                val selectedIds = _selected.value.map { it.id }.toSet()
                val after = sheet.strokes.filter { it.id in selectedIds }
                val before = after.map { it.translated(-dragTotalX, -dragTotalY) }
                val positions = before.map { stroke ->
                    sheet.strokes.indexOfFirst { it.id == stroke.id } to stroke
                }
                history.record(StrokeChange(page, positions, after))
                refreshButtons()
            }
        }
        dragTotalX = 0f
        dragTotalY = 0f
    }

    fun deleteSelection() {
        val page = _selectionPage.value
        if (page < 0) return
        val sheet = document.value?.page(page) ?: return
        val selectedIds = _selected.value.map { it.id }.toSet()
        val removed = sheet.strokes.withIndex()
            .filter { it.value.id in selectedIds }
            .map { it.index to it.value }
        if (removed.isEmpty()) return
        perform(StrokeChange(page, removed = removed))
        deselect()
    }

    // Kształty

    private val _shapes = MutableStateFlow(ShapeSettings(color = inkColor))
    val shapeSettings: StateFlow<ShapeSettings> = _shapes.asStateFlow()

    /** Blokada proporcji 1:1 z panelu — dla tych, którzy nie mają klawiatury. */
    private val _squareShapes = MutableStateFlow(false)
    val squareShapes: StateFlow<Boolean> = _squareShapes.asStateFlow()

    private val _selectedShape = MutableStateFlow<String?>(null)
    val selectedShape: StateFlow<String?> = _selectedShape.asStateFlow()

    /*
      Osobny init, niżej niż pola powyżej: blok wpisany na górze klasy sięgałby
      po `_shapes`, zanim to pole w ogóle powstanie.
    */
    init {
        viewModelScope.launch {
            val saved = runCatching { settings.settings.first().shapes }.getOrNull() ?: return@launch
            _shapes.value = _shapes.value.copy(
                kind = saved.kind
                    ?.let { name -> runCatching { ShapeKind.valueOf(name) }.getOrNull() }
                    ?: _shapes.value.kind,
                // Kolor jak przy pisaku: zapamiętany atrament domyślny zostaje
                // atramentem TEJ kartki, żeby na ciemnej nie wyszedł niewidoczny.
                color = saved.color
                    ?.takeUnless { it == InkPalette.LEGACY_WHITE_ARGB }
                    ?.takeUnless { InkPalette.isDefaultInk(it) }
                    ?: _shapes.value.color,
                strokeWidth = saved.strokeWidth ?: _shapes.value.strokeWidth,
                fill = saved.fill ?: _shapes.value.fill,
                opacity = saved.opacity ?: _shapes.value.opacity,
            )
            _squareShapes.value = saved.square ?: false
        }
    }

    fun addShape(page: Int, shape: ShapeElement) {
        val sheet = document.value?.page(page) ?: return
        val placed = shape.copy(id = UUID.randomUUID().toString())
        perform(ShapeChange(page, sheet.shapes, sheet.shapes + placed))
        _selectedShape.value = placed.id
        growIfNeeded(page)
    }

    fun selectShape(id: String?) {
        _selectedShape.value = id
    }

    /** Kształt wzięty do poprawek razem z numerem strony, na której leży. */
    fun currentShape(): Pair<Int, ShapeElement>? {
        val id = _selectedShape.value ?: return null
        val handwriting = document.value?.handwriting ?: return null
        handwriting.pages.forEachIndexed { index, sheet ->
            sheet.shapes.firstOrNull { it.id == id }?.let { return index to it }
        }
        return null
    }

    fun updateShape(page: Int, shape: ShapeElement, toHistory: Boolean) {
        val sheet = document.value?.page(page) ?: return
        val shapes = sheet.shapes.map { if (it.id == shape.id) shape else it }
        if (toHistory) {
            perform(ShapeChange(page, sheet.shapes, shapes))
        } else {
            editWithoutHistory { document -> document.withPage(page) { it.copy(shapes = shapes) } }
        }
    }

    /** Jak [commitTextBox]: domyka przeciąganie, obrót albo rozciąganie kształtu. */
    fun commitShape(page: Int, before: ShapeElement, after: ShapeElement) {
        val sheet = document.value?.page(page) ?: return
        val then = sheet.shapes.map { if (it.id == before.id) before else it }
        val now = sheet.shapes.map { if (it.id == after.id) after else it }
        perform(ShapeChange(page, then, now))
    }

    fun removeShape(page: Int, id: String) {
        val sheet = document.value?.page(page) ?: return
        if (sheet.shapes.none { it.id == id }) return
        perform(ShapeChange(page, sheet.shapes, sheet.shapes.filterNot { it.id == id }))
        if (_selectedShape.value == id) _selectedShape.value = null
    }

    fun removeSelectedShape() {
        val (page, shape) = currentShape() ?: return
        removeShape(page, shape.id)
    }

    fun setShapeKind(kind: ShapeKind) = updateShapeSettings { it.copy(kind = kind) }

    fun setShapeColor(argb: Int) = updateShapeSettings { it.copy(color = argb) }

    fun setShapeWidth(width: Float) = updateShapeSettings {
        it.copy(strokeWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH))
    }

    fun setShapeFill(argb: Int) = updateShapeSettings { it.copy(fill = argb) }

    fun setShapeOpacity(opacity: Float) = updateShapeSettings {
        it.copy(opacity = opacity.coerceIn(0.05f, 1f))
    }

    fun toggleSquareShapes() {
        val next = !_squareShapes.value
        _squareShapes.value = next
        saveShapeSettings(square = next)
    }

    /**
     * Ustawienia kształtu i, jeżeli jakiś jest wzięty, ten kształt razem z nimi.
     * Inaczej poprawienie koloru znaczyłoby skasować figurę i narysować ją od nowa.
     */
    private fun updateShapeSettings(transform: (ShapeSettings) -> ShapeSettings) {
        val next = transform(_shapes.value)
        _shapes.value = next
        currentShape()?.let { (page, shape) ->
            updateShape(page, shape.withSettings(next), toHistory = true)
        }
        saveShapeSettings()
    }

    private fun saveShapeSettings(square: Boolean = _squareShapes.value) {
        val current = _shapes.value
        viewModelScope.launch {
            runCatching {
                settings.setShape(
                    RememberedShape(
                        kind = current.kind.name,
                        color = current.color,
                        strokeWidth = current.strokeWidth,
                        fill = current.fill,
                        opacity = current.opacity,
                        square = square,
                    ),
                )
            }
        }
    }

    /**
     * Kształt przebrany w podane ustawienia. Zmiana rodzaju z linii na figurę
     * zamkniętą prostuje przy okazji boki: linia w lewo ma ujemną szerokość,
     * a prostokąt musi mieć dodatnią.
     */
    private fun ShapeElement.withSettings(settings: ShapeSettings): ShapeElement {
        val dressed = copy(
            color = settings.color,
            strokeWidth = settings.strokeWidth,
            fill = settings.fill,
            opacity = settings.opacity,
        )
        if (settings.kind == kind) return dressed
        if (settings.kind.open) return dressed.copy(kind = settings.kind)
        val box = box()
        return dressed.copy(
            kind = settings.kind,
            x = box.left,
            y = box.top,
            width = box.width,
            height = box.height,
        )
    }

    // Pola tekstowe

    fun addTextBox(page: Int, x: Float, y: Float, argb: Int) {
        val sheet = document.value?.page(page) ?: return
        val box = TextBoxElement(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            width = 220f,
            height = 44f,
            text = "",
            color = argb,
        )
        perform(FieldChange(page, sheet.texts, sheet.texts + box))
        _editedBox.value = box.id
    }

    fun updateTextBox(page: Int, box: TextBoxElement, toHistory: Boolean) {
        val sheet = document.value?.page(page) ?: return
        val boxes = sheet.texts.map { if (it.id == box.id) box else it }
        if (toHistory) {
            perform(FieldChange(page, sheet.texts, boxes))
        } else {
            editWithoutHistory { document -> document.withPage(page) { it.copy(texts = boxes) } }
        }
    }

    /**
     * Domyka przeciąganie albo rozciąganie pola: [before] to pole sprzed
     * gestu. Zwykłe [updateTextBox] z historią nie umie tego zapisać, bo w
     * trakcie gestu dokument ma już położenia pośrednie i „przed" wyszłoby
     * równe „po" — cofnięcie nie miałoby czego cofać.
     */
    fun commitTextBox(page: Int, before: TextBoxElement, after: TextBoxElement) {
        val sheet = document.value?.page(page) ?: return
        val then = sheet.texts.map { if (it.id == before.id) before else it }
        val now = sheet.texts.map { if (it.id == after.id) after else it }
        perform(FieldChange(page, then, now))
    }

    /**
     * Blok kodu na kartce to pole tekstowe w maszynowym kroju na ciemnej
     * płytce — od razu szersze, bo kod rzadko mieści się w wizytówce.
     */
    fun addCodeBox(page: Int, x: Float, y: Float, argb: Int, background: Int) {
        val sheet = document.value?.page(page) ?: return
        val box = TextBoxElement(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            width = 340f,
            height = 140f,
            text = "",
            color = argb,
            font = NoteFont.MONO,
            background = background,
        )
        perform(FieldChange(page, sheet.texts, sheet.texts + box))
        _editedBox.value = box.id
    }

    // Zdjęcia na kartce

    private val _editedImage = MutableStateFlow<String?>(null)
    val editedImage: StateFlow<String?> = _editedImage.asStateFlow()

    /** Wczytane bitmapy załączników — kartka rysuje z nich, nie z dysku. */
    private val _imageBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val imageBitmaps: StateFlow<Map<String, Bitmap>> = _imageBitmaps.asStateFlow()

    init {
        /*
          Wczytywanie zdjęć chodzi na wątku roboczym, nie na głównym.

          `viewModelScope` domyślnie wpuszcza na wątek główny, a ten kolektor
          rusza przy KAŻDEJ zmianie dokumentu, czyli przy każdym pociągnięciu
          rysika. Dekodowanie zdjęcia stało wtedy w poprzek rysowania i ekran
          zamierał na sekundy — w ciemnym motywie nie do odróżnienia od
          zawieszenia, bo tło jest niemal czarne.

          Bitmapy nie dostają `recycle()`: kartka rysuje z tej samej mapy i
          zwolniona w tej chwili bitmapa mogłaby jeszcze siedzieć w widoku.
          Zwolnienie odwołania wystarcza, a przed brakiem pamięci broni
          zmniejszanie w [Bitmaps.decode].
        */
        viewModelScope.launch(failureHandler("wczytywanie zdjęć kartki")) {
            document.collect { doc ->
                val wanted = doc?.handwriting?.pages.orEmpty()
                    .flatMap { it.images }
                    .map { it.asset }
                    .toSet()
                val loaded = _imageBitmaps.value
                if (wanted == loaded.keys) return@collect
                val next = loaded.filterKeys { it in wanted }.toMutableMap()
                for (name in wanted - loaded.keys) {
                    val bitmap = withContext(Dispatchers.IO) {
                        val data = runCatching { repo.readAttachment(path, name) }.getOrNull()
                        data?.let { Bitmaps.decode(it) }
                    } ?: continue
                    next[name] = bitmap
                }
                _imageBitmaps.value = next
            }
        }
    }

    // Wybór zdjęcia trwa dłuższą chwilę i dzieje się poza edytorem, więc
    // miejsce wstawienia zapamiętuje się przy naciśnięciu przycisku.
    private var photoTarget: Triple<Int, Float, Float>? = null

    fun rememberPhotoTarget(page: Int, x: Float, y: Float) {
        photoTarget = Triple(page, x, y)
    }

    fun insertPhoto(data: ByteArray, extension: String) {
        val (page, x, y) = photoTarget ?: Triple(0, 60f, 60f)
        photoTarget = null
        val sheet = document.value?.page(page) ?: return
        viewModelScope.launch {
            try {
                // Nazwa z zegara, bo przy kolizji magazyn dokleja " (2)" ze
                // spacją — a jedna nazwa ma wskazywać jeden plik.
                val name = repo.writeAttachment(
                    notePath = path,
                    name = "zdjecie-${System.currentTimeMillis()}.$extension",
                    data = data,
                    mime = if (extension == "png") "image/png" else "image/jpeg",
                )
                // Na dysk idzie pełna rozdzielczość, do pamięci zmniejszona.
                val bitmap = withContext(Dispatchers.IO) { Bitmaps.decode(data) }

                // Zdjęcie wchodzi na pół szerokości kartki, w swoich proporcjach.
                val width = (sheet.width * 0.5f).coerceAtLeast(120f)
                val height = if (bitmap != null && bitmap.width > 0) {
                    width * bitmap.height / bitmap.width
                } else {
                    width * 0.75f
                }
                val image = ImageElement(
                    id = UUID.randomUUID().toString(),
                    asset = name,
                    x = x.coerceIn(0f, (sheet.width - width).coerceAtLeast(0f)),
                    y = y.coerceIn(0f, (sheet.height - height).coerceAtLeast(0f)),
                    width = width,
                    height = height,
                )
                if (bitmap != null) {
                    _imageBitmaps.value = _imageBitmaps.value + (name to bitmap)
                }
                perform(ImageChange(page, sheet.images, sheet.images + image))
                _editedImage.value = image.id
            } catch (e: Exception) {
                setError(e.message ?: words.photoSaveFailed)
            }
        }
    }

    fun editImage(id: String?) {
        _editedImage.value = id
    }

    fun updateImage(page: Int, image: ImageElement, toHistory: Boolean) {
        val sheet = document.value?.page(page) ?: return
        val images = sheet.images.map { if (it.id == image.id) image else it }
        if (toHistory) {
            perform(ImageChange(page, sheet.images, images))
        } else {
            editWithoutHistory { document -> document.withPage(page) { it.copy(images = images) } }
        }
    }

    /** Jak [commitTextBox], tylko dla zdjęcia. */
    fun commitImage(page: Int, before: ImageElement, after: ImageElement) {
        val sheet = document.value?.page(page) ?: return
        val then = sheet.images.map { if (it.id == before.id) before else it }
        val now = sheet.images.map { if (it.id == after.id) after else it }
        perform(ImageChange(page, then, now))
    }

    fun removeImage(page: Int, id: String) {
        val sheet = document.value?.page(page) ?: return
        perform(ImageChange(page, sheet.images, sheet.images.filterNot { it.id == id }))
        if (_editedImage.value == id) _editedImage.value = null
    }

    fun removeTextBox(page: Int, id: String) {
        val sheet = document.value?.page(page) ?: return
        perform(FieldChange(page, sheet.texts, sheet.texts.filterNot { it.id == id }))
        if (_editedBox.value == id) _editedBox.value = null
    }

    fun editTextBox(id: String?) {
        _editedBox.value = id
    }

    fun currentTextBox(): Pair<Int, TextBoxElement>? {
        val id = _editedBox.value ?: return null
        val handwriting = document.value?.handwriting ?: return null
        handwriting.pages.forEachIndexed { index, sheet ->
            sheet.texts.firstOrNull { it.id == id }?.let { return index to it }
        }
        return null
    }

    fun styleCurrentTextBox(transform: (TextBoxElement) -> TextBoxElement) {
        val (page, box) = currentTextBox() ?: return
        updateTextBox(page, transform(box), toHistory = true)
    }

    companion object {
        const val GROW_THRESHOLD = 90f
    }

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val path: String,
        private val inkColor: Int,
        private val highlighterColor: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HandwritingViewModel(repo, settings, path, inkColor, highlighterColor) as T
    }
}
