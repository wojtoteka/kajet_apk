package wojtoteka.ovh.kajet.editor.text

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.InkPalette
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.CalculatorAction
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.ColourDot
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.ColourPickerDialog
import wojtoteka.ovh.kajet.core.design.component.SegmentedChoice
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.NoteFont
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.SpanType
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.editor.SaveState
import wojtoteka.ovh.kajet.editor.SaveIndicator
import wojtoteka.ovh.kajet.editor.penWritingSurface
import wojtoteka.ovh.kajet.ink.PenHaptics
import kotlin.math.roundToInt

@Composable
fun TextEditor(
    model: TextNoteViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onPhotoFromGallery: () -> Unit,
    onPhotoFromCamera: () -> Unit,
    /* Puste, gdy konto nie ma asystenta - wtedy nie ma po nim ani śladu. */
    onAi: (() -> Unit)? = null,
) {
    val words = LocalStrings.current
    val document by model.document.collectAsStateWithLifecycle()
    val drawing by model.drawing.collectAsStateWithLifecycle()
    val drawingRevision by model.drawingRevision.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val saveState by model.saveState.collectAsStateWithLifecycle()
    val lastSave by model.lastSave.collectAsStateWithLifecycle()
    val inCloud by model.inCloud.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val appearance by model.appearance.collectAsStateWithLifecycle()
    val recentColors by model.recentColors.collectAsStateWithLifecycle()
    val canUndo by model.canUndo.collectAsStateWithLifecycle()
    val canRedo by model.canRedo.collectAsStateWithLifecycle()

    val colors = Kajet.colors
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth

    /*
      Pisanie rysikiem w notatce tekstowej (system zamienia kreski na litery)
      też ma drgać jak długopis - nie tylko okno rysowania. Zagnieżdżenia
      liczy PenHaptics, więc okno rysowania nad notatką niczego nie psuje;
      po jego zamknięciu wracamy do profilu pisania.
    */
    val context = LocalContext.current
    DisposableEffect(context) {
        PenHaptics.enter(context)
        onDispose { PenHaptics.leave(context) }
    }
    LaunchedEffect(drawing) {
        if (drawing == null) PenHaptics.use(context, PenHaptics.WRITING)
    }

    var field by remember(document?.id) {
        mutableStateOf(TextFieldValue(document?.text?.markdown.orEmpty()))
    }

    // Treść mogła zmienić się poza polem, na przykład przez odhaczenie zadania
    // albo przez wstawienie rysunku.
    val modelContent = document?.text?.markdown.orEmpty()
    if (modelContent != field.text) {
        field = field.copy(
            text = modelContent,
            selection = TextRange(field.selection.start.coerceAtMost(modelContent.length)),
        )
    }

    /*
     * Widok blokowy: zdjęcia widać jako zdjęcia, a nie jako `![...](...)`.
     * Włączony domyślnie, bo tak notatka wygląda tak, jak człowiek ją napisał.
     * Widok Markdown zostaje pod przyciskiem, bo przy tabelach i wzorach
     * czasem trzeba zobaczyć surowy zapis.
     */
    var blockMode by remember { mutableStateOf(true) }
    var blocks by remember(document?.id) { mutableStateOf(Blocks.split(modelContent)) }

    /*
      Zdjęcie wskazane stuknięciem. Tylko ono ma obwódkę, uchwyt rozmiaru
      i przyciski, i to na nim pracują wszystkie działania przy zdjęciach.
      Nowe zdjęcie i nowy rysunek wchodzą OBOK niego, w ten sam wiersz -
      w ten sposób stawia się zdjęcia jedno przy drugim.
    */
    var selectedPhoto by remember(document?.id) { mutableStateOf<String?>(null) }

    // Blok, w którym stoi kursor. Pasek formatowania działa właśnie na nim.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedField by remember { mutableStateOf(TextFieldValue()) }
    var setFocusedField by remember { mutableStateOf<((TextFieldValue) -> Unit)?>(null) }
    var keyToFocus by remember { mutableStateOf<String?>(null) }

    // Treść przyszła spoza edytora, na przykład wstawiono zdjęcie albo cofnięto
    // zmianę. Bloki układamy od nowa, ale tylko wtedy, gdy naprawdę się różnią,
    // żeby nie przerywać pisania przy każdym naciśnięciu klawisza.
    if (blockMode && Blocks.join(blocks) != modelContent) {
        blocks = Blocks.split(modelContent)
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.saveNow()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Jednorazowa naprawa starych notatek: zagnieżdżone i osierocone znaczniki
    // koloru i rozmiaru schodzą do czystej postaci. Naprawiona treść idzie
    // zwykłą drogą zapisu i synchronizacji, więc zdarza się to raz na notatkę.
    LaunchedEffect(document?.id) {
        if (document == null) return@LaunchedEffect
        val repaired = RichTextCodec.flatten(model.markdown)
        if (repaired != model.markdown) model.repairContent(repaired)

        /*
          Ułożenie jest teraz cechą akapitu, a nie całej notatki. Notatka, której
          kiedyś nadano jedno ułożenie dla całości, dostaje je przy każdym
          akapicie - wygląda tak samo jak wcześniej, ale od teraz każdy akapit
          da się przestawić osobno, a nowy tekst nie wskakuje już na środek
          tylko dlatego, że coś kiedyś było na środku.
        */
        val wholeNote = model.document.value?.text?.align ?: NoteAlign.LEFT
        if (wholeNote != NoteAlign.LEFT) {
            model.alignEveryParagraph(TextFormat.alignEveryLine(model.markdown, wholeNote))
        }
    }

    /*
      Format zapamiętany na przyszłość: nic nie jest zaznaczone, więc czeka na
      tekst, który człowiek zaraz napisze. Tak samo działa każdy porządny
      edytor - naciśnięcie pogrubienia przed pisaniem ma pogrubić to, co
      dopiero powstanie, a nie cofać się do słowa obok.
    */
    var pending by remember(document?.id) { mutableStateOf(PendingFormat()) }

    // Miejsce, w które ma trafić wstawiana treść: za blokiem z kursorem,
    // a nie na końcu całej notatki.
    fun insertPosition(): Int = if (blockMode) {
        // Z komórki tabelki wstawiane rzeczy idą za całą tabelką.
        focusedKey?.let { Blocks.endPosition(blocks, Blocks.cellOf(it)?.first ?: it) } ?: model.markdown.length
    } else {
        field.selection.start
    }

    /**
     * Miejsce dla nowego zdjęcia albo rysunku.
     *
     * Kiedy jakieś zdjęcie jest wybrane, nowe staje OBOK niego, w tym samym
     * wierszu i w tej samej szerokości - w ten sposób stawia się zdjęcia jedno
     * przy drugim, bez szukania czegokolwiek w ustawieniach. Kiedy nic nie jest
     * wybrane, zdjęcie idzie od nowego wiersza za blokiem z kursorem, jak dotąd.
     */
    fun photoSpot(): TextNoteViewModel.PhotoSpot {
        val chosen = if (blockMode) {
            blocks.firstOrNull { it.key == selectedPhoto } as? Block.Image
        } else {
            null
        }
        return if (chosen == null) {
            /*
              Nowe zdjęcie i rysunek nie wchodzą na całą szerokość. Na
              tablecie i telefonie trzymanym poziomo 100% to obrazek na pół
              ekranu, który i tak zawsze trzeba było zmniejszać. Na wąskim
              ekranie w pionie 35% byłoby już za drobne, więc tam połowa.
              Powiększyć można suwakiem jak dotąd.
            */
            TextNoteViewModel.PhotoSpot(
                at = insertPosition(),
                width = if (narrow) NARROW_INSERT_WIDTH else WIDE_INSERT_WIDTH,
            )
        } else {
            TextNoteViewModel.PhotoSpot(
                at = Blocks.endPosition(blocks, chosen.key),
                beside = true,
                width = chosen.width,
            )
        }
    }

    /**
     * Przestawia pole, w którym stoi kursor. Transformacja może oddać null -
     * znaczy to „nie ma czego zmienić", bo nic nie jest zaznaczone. Zwraca,
     * czy treść naprawdę się zmieniła.
     */
    fun format(transform: (TextFieldValue) -> TextFieldValue?): Boolean {
        if (blockMode) {
            val key = focusedKey ?: return false
            val set = setFocusedField ?: return false
            // Pasek formatowania pisze znaczniki markdownu - w bloku kodu
            // byłyby zwykłymi gwiazdkami w środku kodu.
            val focused = blocks.firstOrNull { it.key == key }
            val cell = Blocks.cellOf(key)
            if (focused !is Block.Text && focused !is Block.Task && cell == null) return false
            /*
              Treść bloku mogła zmienić się spoza pola (cofnięcie, wstawione
              zdjęcie), zanim pole zdążyło zgłosić nowy stan. Polecenie ma
              działać na tym, co jest w notatce teraz - inaczej przywróciłoby
              treść sprzed cofnięcia.
            */
            val current = when (focused) {
                is Block.Text -> focused.content
                is Block.Task -> focused.content
                else -> cell?.let { (table, row, column) ->
                    (blocks.firstOrNull { it.key == table } as? Block.Table)?.cell(row, column)
                }
            }
            if (current != null && current != focusedField.text) {
                focusedField = TextFieldValue(current, TextRange(focusedField.selection.start.coerceAtMost(current.length)))
            }
            val next = transform(focusedField) ?: return false

            focusedField = next
            set(next)

            val changed = if (cell != null) {
                Blocks.setCell(blocks, cell.first, cell.second, cell.third, next.text)
            } else {
                Blocks.setText(blocks, key, next.text)
            }
            blocks = changed
            model.setContent(Blocks.join(changed))
            return true
        }
        val next = transform(field) ?: return false
        field = next
        model.setContent(next.text)
        return true
    }

    // Pole, w którym stoi kursor - na nim działa pasek narzędzi.
    val cursorField = if (blockMode) focusedField else field

    /** Wiersz, w którym stoi kursor. */
    fun lineAtCursor(value: TextFieldValue): String {
        val content = value.text
        val at = value.selection.start.coerceIn(0, content.length)
        val from = content.lastIndexOf('\n', (at - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val to = content.indexOf('\n', from).let { if (it < 0) content.length else it }
        return content.substring(from.coerceAtMost(to), to)
    }

    /**
     * Przelicza bloki od nowa i wraca kursorem tam, gdzie stał.
     *
     * Notatka układa się na bloki tylko wtedy, gdy treść zmieni się z zewnątrz
     * - inaczej przeliczanie przy każdym naciśnięciu klawisza przerywałoby
     * pisanie. Znacznik zadania jest jednak wyjątkiem: zmienia budowę notatki,
     * bo wiersz przestaje być akapitem, a staje się zadaniem z kwadracikiem.
     * Bez przeliczenia od razu kwadracik pojawiałby się dopiero po ponownym
     * otwarciu notatki, a do tego czasu straszył surowy znacznik.
     */
    fun rebuildBlocks(line: String) {
        val next = Blocks.split(model.markdown)
        blocks = next
        val wanted = Blocks.taskContent(line)
        keyToFocus = next.firstOrNull { it is Block.Task && it.content == wanted }?.key
    }

    /** Podmienia bloki razem z treścią i stawia kursor tam, gdzie ma stanąć. */
    fun applySplit(split: Blocks.Split) {
        blocks = split.blocks
        model.setContent(Blocks.join(split.blocks))
        keyToFocus = split.focusKey
    }

    /** Czy kursor stoi w komórce tabelki - tam nie ma akapitów ani list. */
    fun inTableCell(): Boolean = blockMode && focusedKey?.let { Blocks.cellOf(it) } != null

    /** Czy kursor stoi w zadaniu - wtedy budowa wiersza rządzi się inaczej. */
    fun taskUnderCursor(): Block.Task? {
        if (!blockMode) return null
        val key = focusedKey ?: return null
        return blocks.firstOrNull { it.key == key } as? Block.Task
    }

    /**
     * Stuknięcie w pustą kartkę pod tekstem: kursor idzie do ostatniego
     * akapitu. Bez tego świeża notatka trzymała skupienie przy tytule i pisany
     * tekst szedł do tytułu, a stuknięcie w kartkę nie robiło nic.
     */
    fun focusPageBottom() {
        val next = Blocks.appendParagraph(blocks)
        if (next.blocks !== blocks) blocks = next.blocks
        keyToFocus = next.focusKey
    }

    // Wielkość pisma CAŁEJ notatki. Od niej liczy się wielkość fragmentu
    // i nagłówka, ale to dwie osobne rzeczy i osobne przyciski.
    val noteSize = if (appearance.fontSize > 0f) appearance.fontSize else TextContent.DEFAULT_SIZE

    // Formaty pod kursorem albo w zaznaczeniu. Pasek zapala po nich przyciski.
    val formats = remember(cursorField, pending, noteSize) {
        TextCommands.formats(cursorField, pending, noteSize)
    }

    fun isActive(type: SpanType): Boolean = formats.has(type)

    /**
     * Polecenie paska na polu z kursorem: nowa treść pola albo format
     * czekający na pisanie. Polecenie liczy się WEWNĄTRZ format(), na treści,
     * która jest w notatce teraz - po cofnięciu pole mogło nie zdążyć zgłosić
     * nowego stanu. Bez pola do pisania (kursor w bloku kodu) zostaje tylko
     * format czekający na pisanie.
     */
    fun command(run: (TextFieldValue) -> TextCommands.Result) {
        var outcome: TextCommands.Result? = null
        format { field -> run(field).also { outcome = it }.field }
        pending = (outcome ?: run(cursorField)).pending
    }

    /**
     * Format znaku - pogrubienie, nagłówek i reszta. Działa jak w Wordzie:
     * na zaznaczenie, bez zaznaczenia na słowo pod kursorem, a między słowami
     * czeka na pisanie (TextCommands).
     */
    fun toggleFormat(type: SpanType, value: String = "") {
        // Zadanie zawsze zaczyna się kwadracikiem, a komórka tabelki nie ma
        // akapitów - tam pusty wiersz nie staje się nagłówkiem, tylko nagłówek
        // czeka na pisanie.
        val lineHeading = taskUnderCursor() == null && !inTableCell()
        command { field -> TextCommands.toggle(field, type, value, pending, lineHeading) }
    }

    /**
     * Punkt, numer, zadanie, cytat - budowa całego akapitu, dla każdego
     * akapitu w zaznaczeniu.
     */
    fun toggleParagraphs(kind: LineKind) {
        // Komórka tabelki to jeden wiersz tekstu - punkt ani cytat nie mają
        // w niej sensu, a w zapisie tabelki byłyby zwykłym myślnikiem.
        if (inTableCell()) return
        val task = taskUnderCursor()
        if (task != null) {
            /*
              Kursor stoi w zadaniu. Punkt, numer i cytat to budowa wiersza,
              tak samo jak kwadracik - wiersz może być albo zadaniem, albo
              cytatem, więc zadanie ustępuje miejsca. Ten sam przycisk zadania
              po prostu je zdejmuje.
            */
            val marker = when (kind) {
                LineKind.BULLET -> "- "
                LineKind.NUMBER -> "1. "
                LineKind.QUOTE -> "> "
                else -> ""
            }
            Blocks.taskToLine(blocks, task.key, marker)?.let { applySplit(it) }
            return
        }
        val changed = format { field -> TextCommands.paragraphs(field, kind) }
        // Zadanie zmienia budowę notatki, więc bloki idą od nowa.
        if (changed && blockMode && kind == LineKind.TASK) rebuildBlocks(lineAtCursor(focusedField))
    }

    /** Skróty z klawiatury tabletu - te same, co w Wordzie. */
    fun onShortcut(shortcut: Shortcut): Boolean {
        when (shortcut) {
            Shortcut.BOLD -> toggleFormat(SpanType.BOLD)
            Shortcut.ITALIC -> toggleFormat(SpanType.ITALIC)
            Shortcut.UNDERLINE -> toggleFormat(SpanType.UNDERLINE)
            Shortcut.UNDO -> {
                pending = PendingFormat()
                model.undo()
            }

            Shortcut.REDO -> {
                pending = PendingFormat()
                model.redo()
            }
        }
        return true
    }

    /** Budowa akapitu z kursorem - świeci po niej przycisk listy albo cytatu. */
    val lineKind = if (taskUnderCursor() != null) LineKind.TASK else TextCommands.paragraphKind(cursorField)

    /**
     * Nadaje zapamiętany format tekstowi dopiero co wpisanemu. Oddaje nowe
     * pole albo null, gdy nie ma czego zmieniać - wtedy pole zostaje takie,
     * jakie przyszło z klawiatury i nic nie gubi kursora.
     */
    fun onTyped(previous: TextFieldValue, typed: TextFieldValue): TextFieldValue? {
        if (previous.text == typed.text) {
            // Sam ruch kursora: zapamiętany format przestaje obowiązywać.
            if (!pending.isEmpty) pending = PendingFormat()
            return null
        }

        /*
          W widoku blokowym znaczników nie widać, więc każda zmiana idzie przez
          model tego, co widać (TextEdit). Inaczej Compose kasuje i rozcina
          znaki ZAPISU - połówka znacznika przestaje być znacznikiem i wychodzi
          na wierzch jako goły tekst. W widoku surowego Markdownu znaczniki
          są widoczne i pisze się je wprost, więc tam nic nie pośredniczy.
        */
        if (blockMode) {
            val outcome = TextEdit.typed(previous, typed, pending)
            pending = outcome.pending
            return outcome.field
        }

        val applied = TextFormat.applyPending(typed, previous.text, pending) ?: return null
        pending = PendingFormat()
        return applied
    }

    /**
     * Blok kodu albo wzoru w miejscu kursora. W widoku blokowym to osobny
     * blok bez widocznych płotów; w surowym Markdownie - płoty wprost.
     */
    fun insertCode(fence: String) {
        if (!blockMode) {
            format { TextFormat.insert(it, "\n$fence\n\n$fence\n", fence.length + 2) }
            return
        }
        val key = focusedKey?.let { Blocks.cellOf(it)?.first ?: it }
        val cursor = if (key != null && blocks.firstOrNull { it.key == key } is Block.Text) {
            focusedField.selection.start
        } else {
            0
        }
        applySplit(Blocks.insertCode(blocks, key, cursor, fence))
    }

    /** Ułożenie akapitu, w którym stoi kursor - to ono świeci na pasku. */
    val lineAlign = TextFormat.alignAt(cursorField) ?: appearance.align

    /**
     * Ułożenie akapitów pod kursorem albo w zaznaczeniu - jak w Wordzie, a nie
     * całej notatki. Zadanie stoi zawsze przy swoim kwadraciku, więc ułożenia
     * nie dostaje.
     */
    fun alignParagraphs(align: NoteAlign) {
        if (taskUnderCursor() != null || inTableCell()) return
        format { TextFormat.alignLines(it, align, appearance.align) }
    }

    val toolbarOnRight by model.toolbarOnRight.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().background(colors.desk)) {

        // Pasek narzędzi. Domyślnie po lewej; leworęczni przestawiają go w
        // ustawieniach na prawo, żeby dłoń nie klikała go po drodze.
        val rail: @Composable () -> Unit = {
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(colors.desk)
                .marginRule(colors.line, atEnd = !toolbarOnRight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconAction(KajetIcons.BackArrow, words.backToLibrary, { model.saveNow(); onBack() })
            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            // Cofnij i ponów - jak w Wordzie. Pisanie bez przerwy cofa się
            // kawałkiem, każde polecenie paska osobno.
            IconAction(KajetIcons.Undo, words.undo, { pending = PendingFormat(); model.undo() }, enabled = canUndo)
            IconAction(KajetIcons.Redo, words.redo, { pending = PendingFormat(); model.redo() }, enabled = canRedo)
            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(KajetIcons.PhotoFrame, words.insertPhotoFromGallery, {
                model.rememberPhotoSpot(photoSpot())
                onPhotoFromGallery()
            })
            IconAction(KajetIcons.CameraBody, words.takePhoto, {
                model.rememberPhotoSpot(photoSpot())
                onPhotoFromCamera()
            })
            IconAction(KajetIcons.DrawingPad, words.insertDrawing, model::openDrawing)

            HorizontalRule(Modifier.padding(horizontal = 12.dp))

            IconAction(
                icon = KajetIcons.Favourites,
                description = if (document?.favorite == true) words.removeFromFavorites else words.addToFavorites,
                onClick = model::toggleFavorite,
                selected = document?.favorite == true,
            )
            IconAction(KajetIcons.Export, words.exportNote, onExport)
            if (onAi != null) IconAction(KajetIcons.Bulb, words.aiOpen, onAi)
            CalculatorAction()
            Spacer(Modifier.height(12.dp))
        }
        }

        if (!toolbarOnRight) rail()

        Column(Modifier.weight(1f).fillMaxHeight()) {
            NoteHeader(
                title = document?.title.orEmpty(),
                state = saveState,
                lastSave = lastSave,
                inCloud = inCloud,
                busy = busy,
                onTitle = model::setTitle,
            )
            HorizontalRule()

            if (error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.ErrorMark, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Text(error.orEmpty(), style = Kajet.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    BarTextAction(words.understood, model::dismissError)
                }
                HorizontalRule()
            }

            FormatBar(
                appearance = appearance,
                blockMode = blockMode,
                recentColors = recentColors,
                isActive = { type -> isActive(type) },
                // Wielkość fragmentu pod kursorem; zapamiętana wygrywa,
                // bo to ona trafi na tekst pisany za chwilę.
                fragmentSize = formats.sizePx,
                noteSize = noteSize,
                onBlockMode = {
                    // Surowy markdown nie ma zdjęć do wybierania.
                    selectedPhoto = null
                    blockMode = it
                },
                onFont = model::setFont,
                onNoteSize = model::setFontSize,
                onFragmentSize = { delta ->
                    // Rośnie SAM fragment. Bez zaznaczenia wielkość czeka na
                    // tekst, który człowiek zaraz napisze - całej notatki
                    // nie rusza, od tego jest osobny przycisk obok.
                    //
                    // Czekać ma jednak na CO: bez kursora w notatce nie ma
                    // gdzie pisać, a licznik i tak rósł przy każdym naciśnięciu
                    // i pokazywał wielkość, której nic nie dostawało.
                    val somewhereToType = !blockMode || focusedKey != null
                    if (somewhereToType) command { field -> TextCommands.resize(field, delta, noteSize, pending) }
                },
                onTextColor = model::setTextColor,
                onRememberColor = model::rememberColor,
                lineAlign = lineAlign,
                onAlign = { alignParagraphs(it) },
                onToggle = { type -> toggleFormat(type) },
                headingLevel = formats.heading,
                onHeading = { level -> toggleFormat(SpanType.HEADING, level.toString()) },
                lineKind = lineKind,
                onParagraph = { kind -> toggleParagraphs(kind) },
                onInsert = { fragment, stepBack -> format { TextFormat.insert(it, fragment, stepBack) } },
                onInsertCode = { fence -> insertCode(fence) },
                // Okno koloru to osobne okno - pole traci skupienie i zaznaczenie
                // zwija się, zanim człowiek wybierze barwę. Dlatego pasek bierze
                // zrzut pola PRZED otwarciem okna i to jemu nadaje kolor.
                currentField = { cursorField },
                onApplyColour = { snapshot, argb ->
                    // Treść nie mogła się zmienić przy otwartym oknie; gdyby
                    // jednak, bieżące pole wygrywa ze zrzutem. Bez zaznaczenia
                    // i poza słowem barwa czeka na pisanie.
                    command { current ->
                        TextCommands.color(if (current.text == snapshot.text) snapshot else current, argb, pending)
                    }
                    // Po zamknięciu okna kursor ma wrócić do pisania.
                    if (blockMode) keyToFocus = focusedKey
                },
            )
            HorizontalRule()

            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.sheet)
                    // Pole treści to powierzchnia pisania rysikiem
                    // (tablet zamienia kreski na litery) - tu rysik drga.
                    .penWritingSurface(context)
                    .imePadding(),
            ) {
                if (blockMode) {
                    BlockEditor(
                        blocks = blocks,
                        attachment = model::attachment,
                        attachmentRevision = drawingRevision,
                        isDrawing = { name -> appearance.drawings.any { it.asset == name } },
                        onEditDrawing = model::editDrawing,
                        onBlocksChange = { next ->
                            blocks = next
                            model.setContent(Blocks.join(next), typing = true)
                        },
                        onTapBelow = { focusPageBottom() },
                        selectedPhoto = selectedPhoto,
                        onSelectPhoto = { selectedPhoto = it },
                        keyToFocus = keyToFocus,
                        onFocusTaken = { keyToFocus = null },
                        onBlockFocused = { key, set ->
                            focusedKey = key
                            setFocusedField = set
                        },
                        onFocusBlock = { key -> keyToFocus = key },
                        onSelection = { focusedField = it },
                        onTyped = { previous, typed -> onTyped(previous, typed) },
                        onShortcut = { shortcut -> onShortcut(shortcut) },
                        appearance = appearance,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BasicTextField(
                        value = field,
                        onValueChange = { typed ->
                            val next = onTyped(field, typed) ?: typed
                            field = next
                            model.setContent(next.text, typing = true)
                        },
                        textStyle = Kajet.type.code.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 24.dp, top = 20.dp, bottom = 120.dp)
                            .widthIn(max = Kajet.dimens.readingWidth),
                    )
                    if (field.text.isEmpty()) {
                        Text(
                            text = words.rawMarkdownAbout,
                            style = Kajet.type.code,
                            color = colors.muted,
                            modifier = Modifier.padding(start = 28.dp, top = 20.dp),
                        )
                    }
                }
            }
        }

        if (toolbarOnRight) rail()
    }

    val open = drawing
    if (open != null) {
        val entry = open.entry
        DrawingDialog(
            onClose = model::closeDrawing,
            onDone = { strokes, width, height ->
                if (entry != null) {
                    // Poprawka wraca w miejsce rysunku, który już stoi w notatce.
                    model.saveDrawing(entry, strokes, width, height)
                } else {
                    // Rysunek ma trafić za wybrane zdjęcie albo za blok, w którym
                    // stoi kursor - a nie na koniec całej notatki.
                    model.insertDrawing(strokes, width, height, photoSpot())
                }
            },
            initial = open.source,
            recentColors = recentColors,
            onRememberColor = model::rememberColor,
        )
    }
}

@Composable
private fun NoteHeader(
    title: String,
    state: SaveState,
    lastSave: Long?,
    inCloud: Boolean?,
    busy: String?,
    onTitle: (String) -> Unit,
) {
    val words = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = 28.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            singleLine = true,
            textStyle = Kajet.type.display.copy(color = Kajet.colors.text),
            cursorBrush = SolidColor(Kajet.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                if (title.isEmpty()) {
                    Text(words.unnamed, style = Kajet.type.display, color = Kajet.colors.muted)
                }
                field()
            },
        )
        if (busy != null) {
            Text(busy, style = Kajet.type.meta, color = Kajet.colors.muted)
        }
        SaveIndicator(state = state, lastSave = lastSave, inCloud = inCloud)
    }
}

@Composable
private fun FormatBar(
    appearance: TextContent,
    blockMode: Boolean,
    recentColors: List<Int>,
    /** Czy format będzie miał tekst pisany od kursora - po tym zapalają się przyciski. */
    isActive: (SpanType) -> Boolean,
    /** Wielkość pisma fragmentu pod kursorem. */
    fragmentSize: Float,
    /** Wielkość pisma całej notatki - osobna rzecz, osobny przycisk. */
    noteSize: Float,
    onBlockMode: (Boolean) -> Unit,
    onFont: (NoteFont) -> Unit,
    onNoteSize: (Float) -> Unit,
    /** Plus i minus: wielkość zaznaczonego fragmentu, nigdy całej notatki. */
    onFragmentSize: (Float) -> Unit,
    onTextColor: (Int) -> Unit,
    /** Dokłada barwę do spisu „twoich kolorów" - po zamknięciu okna z tęczą. */
    onRememberColor: (Int) -> Unit,
    /** Ułożenie akapitu pod kursorem. */
    lineAlign: NoteAlign,
    onAlign: (NoteAlign) -> Unit,
    onToggle: (SpanType) -> Unit,
    /** Poziom nagłówka pod kursorem albo w zaznaczeniu - świeci jego przycisk. */
    headingLevel: Int?,
    /** H1-H3: format znaku, jak pogrubienie - na zaznaczenie albo słowo. */
    onHeading: (Int) -> Unit,
    /** Budowa akapitu z kursorem: punkt, numer, zadanie, cytat. */
    lineKind: LineKind,
    onParagraph: (LineKind) -> Unit,
    onInsert: (fragment: String, stepBack: Int) -> Unit,
    /** Blok kodu (```) albo wzoru ($$) jako osobny blok notatki. */
    onInsertCode: (fence: String) -> Unit,
    /** Pole z zaznaczeniem w chwili naciśnięcia - zrzut na czas okna koloru. */
    currentField: () -> TextFieldValue,
    /** Nadaje kolor zaznaczeniu ze zrzutu (zaznaczenie w polu już nie żyje). */
    onApplyColour: (snapshot: TextFieldValue, argb: Int) -> Unit,
) {
    val words = LocalStrings.current
    var fontPicker by remember { mutableStateOf(false) }
    var notePicker by remember { mutableStateOf(false) }
    var wholeNoteColour by remember { mutableStateOf(false) }
    var selectionColour by remember { mutableStateOf(false) }
    var selectionSnapshot by remember { mutableStateOf(TextFieldValue()) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.desk),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Krój pisma całej notatki.
            IconAction(
                icon = KajetIcons.Letters,
                description = "${words.fontFamily}: ${appearance.font.label(words)}",
                onClick = { fontPicker = !fontPicker },
                selected = fontPicker,
                iconSize = 18.dp,
            )
            Text(
                text = appearance.font.label(words),
                style = Kajet.type.label,
                color = Kajet.colors.muted,
                modifier = Modifier.padding(end = 4.dp),
            )

            Divider()

            // Wielkość pisma CAŁEJ notatki - pod osobnym przyciskiem, żeby
            // nie mieszała się z wielkością zaznaczonego fragmentu.
            IconAction(
                icon = KajetIcons.TextSize,
                description = words.wholeNoteLook,
                onClick = { notePicker = !notePicker },
                selected = notePicker,
                iconSize = 18.dp,
            )

            // Plus i minus: wielkość samego fragmentu.
            FormatGlyph("−", words.smallerText, { onFragmentSize(-1f) })
            Text(
                text = "${fragmentSize.roundToInt()}",
                style = Kajet.type.label,
                color = Kajet.colors.text,
                modifier = Modifier.width(24.dp),
            )
            FormatGlyph("+", words.largerText, { onFragmentSize(1f) })

            Divider()

            // Barwa samego zaznaczenia. Barwa CAŁEJ notatki stoi osobno,
            // pod przyciskiem obok - mieszanie ich dawało notatkę, w której
            // pokolorowanie słowa przemalowywało całą stronę.
            IconAction(
                icon = KajetIcons.TextColour,
                description = words.colourSelection,
                onClick = {
                    // Zrzut zaznaczenia PRZED otwarciem okna - samo okno
                    // zabiera skupienie i zaznaczenie znika.
                    selectionSnapshot = currentField()
                    selectionColour = true
                },
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Highlight,
                description = words.highlightSelection,
                onClick = { onToggle(SpanType.HIGHLIGHT) },
                selected = isActive(SpanType.HIGHLIGHT),
                iconSize = 18.dp,
            )

            Divider()

            IconAction(
                icon = KajetIcons.Bold,
                description = words.bold,
                onClick = { onToggle(SpanType.BOLD) },
                selected = isActive(SpanType.BOLD),
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Italic,
                description = words.italic,
                onClick = { onToggle(SpanType.ITALIC) },
                selected = isActive(SpanType.ITALIC),
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Underline,
                description = words.underline,
                onClick = { onToggle(SpanType.UNDERLINE) },
                selected = isActive(SpanType.UNDERLINE),
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Strikethrough,
                description = words.strike,
                onClick = { onToggle(SpanType.STRIKETHROUGH) },
                selected = isActive(SpanType.STRIKETHROUGH),
                iconSize = 18.dp,
            )

            Divider()

            NoteAlign.entries.forEach { variant ->
                IconAction(
                    icon = when (variant) {
                        NoteAlign.LEFT -> KajetIcons.AlignLeft
                        NoteAlign.CENTER -> KajetIcons.AlignCentre
                        NoteAlign.RIGHT -> KajetIcons.AlignRight
                    },
                    description = variant.label(words),
                    onClick = { onAlign(variant) },
                    selected = lineAlign == variant,
                    iconSize = 18.dp,
                )
            }
        }

        if (fontPicker) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedChoice(
                    options = NoteFont.entries,
                    selected = appearance.font,
                    name = { it.label(words) },
                    // Menu zostaje otwarte: krój porównuje się na żywo,
                    // zamyka się je samemu tym samym przyciskiem „abc".
                    onSelect = { font -> onFont(font) },
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        // Wielkość pisma całej notatki. Osobny rząd, bo to osobna rzecz niż
        // wielkość zaznaczonego fragmentu - mieszanie ich dawało notatkę,
        // w której powiększenie słowa skalowało całą stronę.
        if (notePicker) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = words.fontSizeWholeNote,
                    style = Kajet.type.label,
                    color = Kajet.colors.muted,
                    modifier = Modifier.padding(end = 8.dp),
                    maxLines = 1,
                )
                FormatGlyph("−", words.smallerText, { onNoteSize(noteSize - 1f) })
                Text(
                    text = "${noteSize.roundToInt()}",
                    style = Kajet.type.label,
                    color = Kajet.colors.text,
                    modifier = Modifier.width(24.dp),
                )
                FormatGlyph("+", words.largerText, { onNoteSize(noteSize + 1f) })
                // 0, nie DEFAULT_SIZE: na dysku zero znaczy motyw. 17 to tylko podgląd.
                BarTextAction(words.defaultSize) { onNoteSize(0f) }

                Divider()

                // Barwa CAŁEJ notatki - tutaj, a nie w pasku obok barwy
                // zaznaczenia, żeby nie dało się ich pomylić.
                Text(
                    text = words.colourWholeNote,
                    style = Kajet.type.label,
                    color = Kajet.colors.muted,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    maxLines = 1,
                )
                ColourDot(
                    color = if (appearance.textColor != 0) {
                        appearance.textColor
                    } else {
                        Kajet.colors.text.toArgb()
                    },
                    description = words.colourWholeNote,
                    onClick = { wholeNoteColour = true },
                )
                // Wyjście dla notatek, którym barwę całej strony nadano
                // wcześniej przez pomyłkę.
                if (appearance.textColor != 0) {
                    BarTextAction(words.defaultColour) { onTextColor(0) }
                }
            }
        }

        HorizontalRule()

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FormatGlyph("H1", words.heading1, { onHeading(1) }, bold = true, selected = headingLevel == 1)
            FormatGlyph("H2", words.heading2, { onHeading(2) }, bold = true, selected = headingLevel == 2)
            FormatGlyph("H3", words.heading3, { onHeading(3) }, bold = true, selected = headingLevel == 3)

            Divider()

            IconAction(
                icon = KajetIcons.BulletList,
                description = words.bulletList,
                onClick = { onParagraph(LineKind.BULLET) },
                selected = lineKind == LineKind.BULLET,
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.NumberedList,
                description = words.numberedList,
                onClick = { onParagraph(LineKind.NUMBER) },
                selected = lineKind == LineKind.NUMBER,
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.TaskList,
                description = words.taskList,
                onClick = { onParagraph(LineKind.TASK) },
                selected = lineKind == LineKind.TASK,
                iconSize = 18.dp,
            )
            IconAction(
                icon = KajetIcons.Quote,
                description = words.quote,
                onClick = { onParagraph(LineKind.QUOTE) },
                selected = lineKind == LineKind.QUOTE,
                iconSize = 18.dp,
            )

            Divider()

            FormatGlyph(
                glyph = "`",
                description = words.inlineCode,
                onClick = { onToggle(SpanType.CODE) },
                selected = isActive(SpanType.CODE),
            )
            IconAction(
                icon = KajetIcons.CodeFile,
                description = words.codeBlock,
                onClick = { onInsertCode(Block.CODE_FENCE) },
                iconSize = 18.dp,
            )
            FormatGlyph("Σ", words.formula, { onInsertCode(Block.FORMULA_FENCE) })
            IconAction(
                icon = KajetIcons.DividerLine,
                description = words.dividerLine,
                onClick = { onInsert("\n---\n", 0) },
                iconSize = 18.dp,
            )

            Divider()

            IconAction(
                icon = KajetIcons.CodeFile,
                description = if (blockMode) words.showRawMarkdown else words.backToContentView,
                onClick = { onBlockMode(!blockMode) },
                selected = !blockMode,
                iconSize = 18.dp,
            )
        }
    }

    if (wholeNoteColour) {
        ColourPickerDialog(
            title = words.colourWholeNoteTitle,
            color = if (appearance.textColor != 0) appearance.textColor else Kajet.colors.text.toArgb(),
            onChange = onTextColor,
            onClose = { wholeNoteColour = false; onRememberColor(appearance.textColor) },
            withAlpha = false,
            presetColors = InkPalette.pens(words),
            recentColors = recentColors,
        )
    }

    if (selectionColour) {
        // Kolor zaznaczenia bierzemy dopiero przy zamknięciu okna. Okno oddaje
        // barwę przy każdym ruchu palca po kwadracie, a przestawianie fragmentu
        // przy każdym ruchu mieliłoby treść kilkadziesiąt razy.
        //
        // Okno otwiera się na barwie, którą zaznaczony fragment już ma. Wcześniej
        // zaczynało od zera, a zero znaczyło „nic nie wybrano" i przy zamykaniu
        // nie działo się NIC - także wtedy, gdy barwa była wybrana, a potem
        // trafiona jeszcze raz ta sama.
        val startColour = TextCommands.formats(selectionSnapshot, PendingFormat(), 0f).color
            ?: appearance.textColor.takeIf { it != 0 }
            ?: Kajet.colors.text.toArgb()
        var picked by remember { mutableStateOf(startColour) }
        ColourPickerDialog(
            title = words.colourSelectionTitle,
            color = picked,
            onChange = { picked = it },
            onClose = {
                selectionColour = false
                onApplyColour(selectionSnapshot, picked)
                onRememberColor(picked)
            },
            withAlpha = false,
            presetColors = InkPalette.pens(words),
            recentColors = recentColors,
        )
    }
}

/** Pusta tabelka do wstawienia. Nagłówki idą w wybranym języku. */
private fun tableTemplate(words: Strings): String =
    "\n| ${words.tableColumn} | ${words.tableColumn} |\n| --- | --- |\n|  |  |\n"

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Kajet.colors.line),
    )
}

/*
  Akcja paska, nie SecondaryButton. Ten ma 48 dp i obwódkę - w ciasnym
  rzędzie FormatBar odcinał się od tła desk i na telefonie łamał etykiety
  („Domyślna", „Domyślny kolor", „Rozumiem"). Tu ten sam krój i gęstość
  co etykiety paska, bez ramki.
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

@Composable
private fun FormatGlyph(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    bold: Boolean = false,
    italic: Boolean = false,
    /** Zapalony, gdy format obowiązuje w miejscu, w którym stoi kursor. */
    selected: Boolean = false,
) {
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            // Jak IconAction: przycisk paska nie przejmuje skupienia, żeby
            // nie zwijać zaznaczenia w polu tekstu.
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) Kajet.colors.accentWash else androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Kajet.dimens.corner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = Kajet.type.titleSmall.copy(
                fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
            ),
            color = if (selected) Kajet.colors.accent else Kajet.colors.text,
        )
    }
}

/** Szerokość nowego zdjęcia albo rysunku na wąskim ekranie (telefon w pionie). */
private const val NARROW_INSERT_WIDTH = 0.5f

/** Szerokość nowego zdjęcia albo rysunku na tablecie i telefonie w poziomie. */
private const val WIDE_INSERT_WIDTH = 0.35f
