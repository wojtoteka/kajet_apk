package wojtoteka.ovh.kajet.editor.text

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.InlineDrawing
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.NotePhoto
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.ink.DrawingToImage
import wojtoteka.ovh.kajet.ink.PaperStrokes
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

class TextNoteViewModel(
    private val repo: LibraryRepository,
    settings: SettingsStore,
    path: String,
) : NoteViewModel(repo, settings, path) {

    private val _drawing = MutableStateFlow(false)
    val drawing: StateFlow<Boolean> = _drawing.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    val markdown: String get() = document.value?.text?.markdown.orEmpty()

    val appearance: StateFlow<TextContent> = MutableStateFlow(TextContent()).also { state ->
        viewModelScope.launch {
            document.collect { current -> state.value = current?.text ?: TextContent() }
        }
    }.asStateFlow()

    private fun changeAppearance(transform: (TextContent) -> TextContent) {
        editWithoutHistory { document ->
            document.copy(text = transform(document.text ?: TextContent()))
        }
    }

    fun setFont(font: NoteFont) = changeAppearance { it.copy(font = font) }

    fun setFontSize(points: Float) = changeAppearance {
        it.copy(fontSize = TextContent.storedFontSize(points))
    }

    /**
     * Do spisu „twoich kolorów" barwa trafia dopiero po zamknięciu okna z
     * tęczą (rememberColor woła pasek formatowania). Tęcza zgłasza każdy
     * odcień mijany pod palcem, więc zapisywanie po drodze zapychało spis.
     */
    fun setTextColor(argb: Int) {
        changeAppearance { it.copy(textColor = argb) }
    }

    fun setAlign(align: NoteAlign) = changeAppearance { it.copy(align = align) }

    fun openDrawing() {
        _drawing.value = true
    }

    fun closeDrawing() {
        _drawing.value = false
    }

    fun setContent(markdown: String) {
        editWithoutHistory { document ->
            val text = document.text ?: TextContent()
            document.copy(text = text.copy(markdown = markdown))
        }
    }

    fun insert(fragment: String, start: Int, end: Int): Int {
        val content = markdown
        val from = start.coerceIn(0, content.length)
        val to = end.coerceIn(from, content.length)
        setContent(content.substring(0, from) + fragment + content.substring(to))
        return from + fragment.length
    }

    fun wrap(marker: String, start: Int, end: Int): IntRange {
        val content = markdown
        val from = start.coerceIn(0, content.length)
        val to = end.coerceIn(from, content.length)
        val middle = content.substring(from, to).ifEmpty { words.placeholderWord }
        val next = content.substring(0, from) + marker + middle + marker + content.substring(to)
        setContent(next)
        return (from + marker.length)..(from + marker.length + middle.length)
    }

    fun beforeLine(marker: String, position: Int): Int {
        val after = TextFormat.beforeLine(
            TextFieldValue(markdown, TextRange(position.coerceIn(0, markdown.length))),
            marker,
        )
        setContent(after.text)
        return after.selection.start
    }

    // Zdjęcia i rysunki

    /**
     * Miejsce dla wstawianego zdjęcia: [at] to znak treści, za którym ma
     * stanąć, [beside] mówi, czy ma stanąć OBOK poprzedniego zdjęcia,
     * w tym samym wierszu, a [width] to jego szerokość - zdjęcie wchodzące
     * obok bierze ją od sąsiada, żeby wiersz od razu był równy.
     */
    data class PhotoSpot(
        val at: Int,
        val beside: Boolean = false,
        val width: Float = ImageLines.FULL_WIDTH,
    )

    // Wybór zdjęcia w galerii albo aparacie trwa dłuższą chwilę i dzieje się
    // poza edytorem, więc miejsce wstawienia zapamiętuje się w chwili
    // naciśnięcia przycisku, a nie w chwili powrotu z wynikiem.
    private var photoSpot: PhotoSpot? = null

    fun rememberPhotoSpot(spot: PhotoSpot) {
        photoSpot = spot
    }

    fun takePhotoSpot(): PhotoSpot {
        val spot = photoSpot ?: PhotoSpot(markdown.length)
        photoSpot = null
        return spot.copy(at = spot.at.coerceIn(0, markdown.length))
    }

    /**
     * Zapis zdjęcia gotowy do wstawienia w treść.
     *
     * Zdjęcie stojące OBOK poprzedniego siedzi w tym samym wierszu, rozdzielone
     * samym odstępem - tak czyta wiersz ze zdjęciami [ImageLines] i tak samo
     * markdown na stronie. Zdjęcie od nowego wiersza dostaje własny wiersz.
     */
    private fun photoMarkdown(alt: String, name: String, spot: PhotoSpot): String {
        val photo = ImageLines.write(
            NotePhoto(alt = alt, url = "assets/$name", width = spot.width),
        )
        return if (spot.beside) " $photo" else "\n$photo\n"
    }

    fun insertPhoto(data: ByteArray, extension: String, spot: PhotoSpot) {
        viewModelScope.launch {
            _busy.value = words.savingPhoto
            try {
                // Nazwa z zegara, bo przy kolizji magazyn dokleja " (2)" ze spacją,
                // a takiego adresu nie czyta blok obrazka w podglądzie.
                val name = repo.writeAttachment(
                    notePath = path,
                    name = "zdjecie-${System.currentTimeMillis()}.$extension",
                    data = data,
                    mime = if (extension == "png") "image/png" else "image/jpeg",
                )
                insert(photoMarkdown(words.photoAltText, name, spot), spot.at, spot.at)
            } catch (e: Exception) {
                setError(e.message ?: words.photoSaveFailed)
            } finally {
                _busy.value = null
            }
        }
    }

    fun insertDrawing(
        strokes: List<InkStroke>,
        width: Float,
        height: Float,
        spot: PhotoSpot,
    ) {
        if (strokes.isEmpty()) {
            _drawing.value = false
            return
        }
        viewModelScope.launch {
            _busy.value = words.savingDrawing
            try {
                // PNG siada na jasnym papierze markdowna, więc tło jest białe,
                // a tusz z ciemnej kartki idzie na grafit - inaczej kremowa
                // kreska znika. Źródło kresek zostaje w barwach edytora.
                val png = DrawingToImage.png(
                    strokes = PaperStrokes.of(strokes),
                    width = width,
                    height = height,
                    backgroundColor = PaperStrokes.PAGE,
                )
                val imageName =
                    repo.writeAttachment(path, "rysunek-${System.currentTimeMillis()}.png", png, "image/png")
                val sourceName = imageName.removeSuffix(".png") + ".strokes.json"

                val store = repo.store()
                store?.writeInlineDrawing(
                    notePath = path,
                    name = sourceName,
                    drawing = DrawingSource(width = width, height = height, strokes = strokes),
                )

                editWithoutHistory { document ->
                    val text = document.text ?: TextContent()
                    document.copy(
                        text = text.copy(
                            drawings = text.drawings + InlineDrawing(
                                asset = imageName,
                                source = sourceName,
                                width = width,
                                height = height,
                            ),
                        ),
                    )
                }
                insert(photoMarkdown("rysunek", imageName, spot), spot.at, spot.at)
            } catch (e: Exception) {
                setError(e.message ?: words.drawingSaveFailed)
            } finally {
                _busy.value = null
                _drawing.value = false
            }
        }
    }

    suspend fun attachment(name: String): ByteArray? = repo.readAttachment(path, name)

    class Factory(
        private val repo: LibraryRepository,
        private val settings: SettingsStore,
        private val path: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TextNoteViewModel(repo, settings, path) as T
    }
}
