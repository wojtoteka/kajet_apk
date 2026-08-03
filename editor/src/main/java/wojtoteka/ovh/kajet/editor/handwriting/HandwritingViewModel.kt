package wojtoteka.ovh.kajet.editor.handwriting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InkTool
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.RecognizedText
import wojtoteka.ovh.kajet.core.model.TextBoxElement
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.editor.StrokeChange
import wojtoteka.ovh.kajet.editor.FieldChange
import wojtoteka.ovh.kajet.editor.PageChange
import wojtoteka.ovh.kajet.editor.page
import wojtoteka.ovh.kajet.editor.withPage
import wojtoteka.ovh.kajet.ink.Strokes
import wojtoteka.ovh.kajet.ink.EditorTool
import wojtoteka.ovh.kajet.ink.Brushes
import wojtoteka.ovh.kajet.ink.HandwritingRecognition
import wojtoteka.ovh.kajet.ink.RecognitionState
import wojtoteka.ovh.kajet.ink.PenSettings
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.FingerBehavior
import wojtoteka.ovh.kajet.storage.RememberedPen
import java.util.UUID

class HandwritingViewModel(
    repo: LibraryRepository,
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
                penColor = saved.color ?: _pens.value.penColor,
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

    private val recognition = HandwritingRecognition()

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Ready)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _textSuggestions = MutableStateFlow<List<String>>(emptyList())
    val textSuggestions: StateFlow<List<String>> = _textSuggestions.asStateFlow()

    // Zbieranie jednego pociągnięcia gumki albo jednego przesunięcia zaznaczenia
    private var pendingRemoved = mutableListOf<Pair<Int, InkStroke>>()
    private var pendingAdded = mutableListOf<InkStroke>()
    private var pendingPage = -1
    private var dragTotalX = 0f
    private var dragTotalY = 0f

    fun selectTool(next: EditorTool) {
        _tool.value = next
        if (next != EditorTool.LASSO) deselect()
    }

    fun setPenKind(kind: InkTool) = updatePen { it.copy(penKind = kind) }

    fun setPenColor(argb: Int) {
        updatePen { it.copy(penColor = argb) }
        rememberColor(argb)
    }

    fun setPenWidth(width: Float) = updatePen {
        it.copy(penWidth = width.coerceIn(Brushes.MIN_WIDTH, Brushes.MAX_WIDTH))
    }

    fun setPenOpacity(opacity: Float) = updatePen {
        it.copy(penOpacity = opacity.coerceIn(0.05f, 1f))
    }

    fun setHighlighterColor(argb: Int) {
        updatePen { it.copy(highlighterColor = argb) }
        rememberColor(argb)
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
        val lowest = sheet.strokes.maxOfOrNull { it.bounds().bottom } ?: return
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

        if (mode == PageMode.A4 || handwriting.pages.size <= 1) {
            editWithoutHistory { it.copy(handwriting = handwriting.copy(pageMode = mode)) }
            return
        }

        var top = 0f
        val strokes = mutableListOf<InkStroke>()
        val boxes = mutableListOf<TextBoxElement>()
        for (sheet in handwriting.pages) {
            strokes += sheet.strokes.map { it.translated(0f, top) }
            boxes += sheet.texts.map { it.copy(y = it.y + top) }
            top += sheet.height
        }

        val merged = handwriting.pages.first().copy(
            height = top,
            strokes = strokes,
            texts = boxes,
            recognized = emptyList(),
        )
        editWithoutHistory {
            it.copy(handwriting = handwriting.copy(pageMode = mode, pages = listOf(merged)))
        }
    }

    // Gumka

    fun erase(page: Int, x: Float, y: Float, radius: Float, wholeStroke: Boolean) {
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

    // Handwriting recognition

    fun recognizeSelection() {
        val strokes = _selected.value
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            try {
                if (!recognition.isModelDownloaded()) {
                    _recognitionState.value = RecognitionState.Downloading(
                        "Pobieram model pisma po polsku. Robi się to raz, potem działa bez internetu.",
                    )
                    recognition.downloadModel()
                }
                _recognitionState.value = RecognitionState.Downloading("Odczytuję pismo...")

                val sheet = document.value?.page(_selectionPage.value)
                val suggestions = recognition.recognize(
                    strokes = strokes,
                    areaWidth = sheet?.width ?: 595f,
                    areaHeight = sheet?.height ?: 842f,
                )
                _recognitionState.value = RecognitionState.Ready
                if (suggestions.isEmpty()) {
                    _recognitionState.value = RecognitionState.Failed(
                        "Nie odczytałem tego pisma. Zaznacz mniejszy fragment i spróbuj jeszcze raz.",
                    )
                } else {
                    _textSuggestions.value = suggestions.take(5)
                }
            } catch (e: Exception) {
                _recognitionState.value = RecognitionState.Failed(
                    e.message ?: "Nie udało się odczytać pisma.",
                )
            }
        }
    }

    fun acceptRecognition(text: String) {
        val page = _selectionPage.value
        val strokes = _selected.value
        _textSuggestions.value = emptyList()
        if (page < 0 || strokes.isEmpty()) return

        val area = Strokes.bounds(strokes) ?: return
        editWithoutHistory { document ->
            document.withPage(page) { sheet ->
                sheet.copy(
                    recognized = sheet.recognized + RecognizedText(
                        id = UUID.randomUUID().toString(),
                        text = text,
                        x = area.left,
                        y = area.top,
                        width = area.width,
                        height = area.height,
                        strokeIds = strokes.map { it.id },
                    ),
                )
            }
        }
        deselect()
    }

    fun dismissSuggestions() {
        _textSuggestions.value = emptyList()
    }

    fun dismissRecognitionState() {
        _recognitionState.value = RecognitionState.Ready
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
