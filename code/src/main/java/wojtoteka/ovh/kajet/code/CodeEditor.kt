package wojtoteka.ovh.kajet.code

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.LanguageIcons
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.searchFound

@Composable
fun CodeEditor(
    model: CodeViewModel,
    onBack: () -> Unit,
    /* Puste, gdy konto nie ma asystenta - wtedy nie ma po nim ani śladu. */
    onAi: (() -> Unit)? = null,
) {
    val words = LocalStrings.current
    val code by model.code.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    val result by model.result.collectAsStateWithLifecycle()
    val tab by model.tab.collectAsStateWithLifecycle()
    val running by model.running.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val wordWrap by model.wordWrap.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val matches by model.matches.collectAsStateWithLifecycle()

    val colors = Kajet.colors
    var searchVisible by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }

    val codeColors = remember(colors.isDark) {
        CodeColors(
            plain = colors.text,
            keyword = colors.accent,
            string = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFFD6A648) else androidx.compose.ui.graphics.Color(0xFF8A6212),
            number = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF5AA8C4) else androidx.compose.ui.graphics.Color(0xFF1C5C74),
            comment = colors.muted,
            typeName = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF9EB367) else androidx.compose.ui.graphics.Color(0xFF56662A),
        )
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.saveNow()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val toolbarOnRight by model.toolbarOnRight.collectAsStateWithLifecycle()

    // Telefon. Ta sama granica co w bibliotece, ustawieniach i edytorze tekstu.
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth

    Row(Modifier.fillMaxSize().background(colors.desk)) {

        // Pasek narzędzi; leworęczni przestawiają go w ustawieniach na prawo.
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

            if (running) {
                IconAction(KajetIcons.StopSquare, words.codeStop, model::stop)
            } else {
                IconAction(
                    icon = KajetIcons.PlayRun,
                    description = words.codeRun,
                    onClick = model::run,
                    enabled = model.runner != null,
                    selected = model.runner != null,
                )
            }
            if (model.language == CodeLanguage.HTML) {
                IconAction(
                    icon = KajetIcons.Globe,
                    description = words.codePagePreview,
                    onClick = { model.saveNow(); previewVisible = !previewVisible },
                    selected = previewVisible,
                )
            }
            IconAction(KajetIcons.Search, words.codeSearchInFile, { searchVisible = !searchVisible }, selected = searchVisible)
            IconAction(KajetIcons.WordWrap, words.codeWordWrap, model::toggleWordWrap, selected = wordWrap)
            if (onAi != null) IconAction(KajetIcons.Bulb, words.aiOpen, onAi)
            Spacer(Modifier.height(12.dp))
        }
        }

        if (!toolbarOnRight) rail()

        Column(Modifier.weight(1f).fillMaxHeight()) {

            FileHeader(model = model, saved = saved, narrow = narrow)
            HorizontalRule()

            if (searchVisible) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.Search, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = model::search,
                        singleLine = true,
                        textStyle = Kajet.type.code.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            query.length < 2 -> words.codeTypeTwoLetters
                            matches.isEmpty() -> words.libSearchNothing
                            else -> words.searchFound(matches.size)
                        },
                        style = Kajet.type.meta,
                        color = colors.muted,
                    )
                }
                HorizontalRule()
            }

            if (error != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.desk)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KajetIcons.Offline, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                    Text(error.orEmpty(), style = Kajet.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    SecondaryButton(words.understood, model::dismissError)
                }
                HorizontalRule()
            }

            val sidewaysScroll = rememberScrollState()
            Box(
                Modifier
                    /*
                      Na telefonie wyjście dostaje więcej miejsca niż na
                      tablecie. Przy 800 dp wysokości 38% to niecałe 190 dp na
                      sam wydruk programu — na tyle mało, że po jednym zdaniu
                      trzeba było przewijać.
                    */
                    .weight(if (narrow) 0.55f else 0.62f)
                    .fillMaxWidth()
                    .background(colors.sheet)
                    /*
                      Przycięcie do granic. Compose sam z siebie nie ucina
                      niczego, co wystaje poza ramkę, a podgląd strony to widok
                      Androida, który w trakcie ładowania potrafi na kilka
                      klatek zmierzyć się większy niż jego miejsce. Bez tej
                      linii wychodził wtedy na pasek narzędzi i zasłaniał
                      strzałkę powrotu.
                    */
                    .clipToBounds()
                    .imePadding(),
            ) {
                if (previewVisible && model.language == CodeLanguage.HTML) {
                    HtmlPreview(code)
                } else {
                    val verticalScroll = rememberScrollState()
                    Row(Modifier.fillMaxSize().verticalScroll(verticalScroll)) {
                        LineNumberGutter(code)
                        Box(
                            Modifier
                                .weight(1f)
                                .then(
                                    if (wordWrap) Modifier else Modifier.horizontalScroll(sidewaysScroll),
                                ),
                        ) {
                            /*
                              Pole trzyma TextFieldValue, a nie sam napis, bo
                              pomocnik przy pisaniu musi móc ustawić kursor —
                              po domknięciu nawiasu ma on zostać W ŚRODKU pary,
                              a nie za nią.

                              Treść z zewnątrz (wczytany plik) wchodzi tu przez
                              porównanie napisów: gdy różni się od tego, co jest
                              w polu, przepisujemy je i stawiamy kursor na końcu.
                            */
                            var field by remember { mutableStateOf(TextFieldValue(code)) }
                            if (field.text != code) {
                                field = TextFieldValue(code, TextRange(code.length))
                            }

                            BasicTextField(
                                value = field,
                                onValueChange = { typed -> field = model.onTyping(field, typed) },
                                textStyle = Kajet.type.code.copy(color = colors.text),
                                cursorBrush = SolidColor(colors.accent),
                                /*
                                  Klawiatura ma trzymać ręce przy sobie.
                                  Autokorekta robiła z „def proba(" — „def
                                  próba(", czyli z poprawnego Pythona błąd,
                                  a wielka litera po kropce psuła nazwy pól.
                                  W kodzie liczy się znak w znak to, co
                                  wpisano.
                                */
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                ),
                                visualTransformation = { text ->
                                    androidx.compose.ui.text.input.TransformedText(
                                        SyntaxHighlight.highlight(text.text, model.language, codeColors),
                                        androidx.compose.ui.text.input.OffsetMapping.Identity,
                                    )
                                },
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 40.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }

                    // Ślad po przewijaniu w bok. Na telefonie bez niego nie
                    // było wiadomo, że dalszy ciąg wiersza w ogóle istnieje.
                    if (narrow && !wordWrap) {
                        SidewaysScrollMark(
                            state = sidewaysScroll,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }

            HorizontalRule()
            ResultPanel(
                tab = tab,
                result = result,
                input = input,
                running = running,
                offline = model.offline,
                narrow = narrow,
                modifier = Modifier.weight(if (narrow) 0.45f else 0.38f),
                onTab = model::selectTab,
                onInput = model::onInputChange,
            )
        }

        if (toolbarOnRight) rail()
    }
}

/**
 * Podgląd strony HTML wprost z edytora. Treść ładuje się z pamięci, bez
 * adresu bazowego — strona może dociągać rzeczy z internetu, ale nie widzi
 * plików urządzenia.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPreview(code: String) {
    val sheet = Kajet.colors.sheet.toArgb()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                /*
                  Rozmiar z góry, jeszcze przed pierwszym ładowaniem. Bez tego
                  widok mierzy się sam, a punktem wyjścia jest dla niego okno,
                  nie przydzielone miejsce — stąd skok układu w trakcie
                  ładowania. Granice ustala teraz rodzic i tylko on.
                */
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Zanim strona się namaluje, widać kolor arkusza, a nie białą
                // płachtę — w ciemnym motywie było to uderzenie w oczy.
                setBackgroundColor(sheet)
                // Odnośniki otwierają się w podglądzie. Bez tego pierwsze
                // kliknięcie wyrzucało z Kajetu do przeglądarki.
                webViewClient = WebViewClient()
                // Strony uczniowskie mają prawo używać JavaScriptu.
                settings.javaScriptEnabled = true
            }
        },
        update = { view ->
            view.setBackgroundColor(sheet)
            // Przeładowanie tylko przy zmianie treści; zwykła rekompozycja nie
            // ma zrzucać strony do początku.
            if (view.tag != code) {
                view.tag = code
                view.loadDataWithBaseURL(null, code, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FileHeader(model: CodeViewModel, saved: Boolean, narrow: Boolean) {
    val words = LocalStrings.current
    val side = if (narrow) 14.dp else 20.dp
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = side, end = side, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 12.dp),
    ) {
        Icon(
            imageVector = LanguageIcons.forLanguage(model.language),
            contentDescription = model.language.label(words),
            tint = Kajet.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = model.fileName,
                style = Kajet.type.title,
                color = Kajet.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    model.language == CodeLanguage.HTML ->
                        words.codeHtmlHint
                    model.runner == null -> "${model.language.label(words)}. ${words.codeWontRunHere}"
                    model.offline -> "${model.language.label(words)}. ${words.codeRunsOnTablet}"
                    else -> "${model.language.label(words)}. ${words.codeRunsOnServer}"
                },
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Jeden wiersz: kolumna obok ma wagę, więc mierzy się PO tym napisie,
        // ale przy wąskim ekranie i tak nie ma tu miejsca na zawijanie.
        Text(
            text = if (saved) words.saved else words.codeUnsaved,
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Cienki ślad pokazujący, ile wiersza zostało poza ekranem i w którym miejscu
 * właśnie jesteśmy.
 *
 * Compose nie ma własnych pasków przewijania, a bez żadnego znaku wiersz ucięty
 * przy krawędzi wygląda po prostu na skończony. Znika, gdy przewijać nie ma
 * czego, żeby nie zaśmiecał ekranu przy krótkim kodzie.
 */
@Composable
private fun SidewaysScrollMark(state: ScrollState, modifier: Modifier = Modifier) {
    val colors = Kajet.colors
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val hidden = state.maxValue
        if (hidden <= 0) return@Canvas

        val shown = size.width / (size.width + hidden)
        val markWidth = size.width * shown
        val travel = size.width - markWidth
        val progress = state.value.toFloat() / hidden

        drawRect(
            color = colors.line,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height),
        )
        drawRect(
            color = colors.muted.copy(alpha = 0.6f),
            topLeft = Offset(travel * progress, 0f),
            size = Size(markWidth, size.height),
        )
    }
}

@Composable
private fun LineNumberGutter(code: String) {
    val lineCount = code.count { it == '\n' } + 1
    Column(
        Modifier
            .width(52.dp)
            .background(Kajet.colors.desk)
            .marginRule(Kajet.colors.line)
            .padding(top = 8.dp, end = 8.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (number in 1..lineCount) {
            Text(
                text = number.toString(),
                style = Kajet.type.codeGutter,
                color = Kajet.colors.muted,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ResultPanel(
    tab: PanelTab,
    result: RunResult?,
    input: String,
    running: Boolean,
    offline: Boolean,
    narrow: Boolean,
    modifier: Modifier,
    onTab: (PanelTab) -> Unit,
    onInput: (String) -> Unit,
) {
    val words = LocalStrings.current
    val colors = Kajet.colors

    // Skąd program szedł, jak długo i z jakim kodem wyjścia. Puste, dopóki nic
    // się nie wydarzyło.
    val status: String? = when {
        running -> if (offline) words.codeWorkingOnTablet else words.codeSendingToServer
        result != null -> buildString {
            append(if (result.viaNetwork) words.codeFromServer else words.codeFromTablet)
            append(", ")
            append(result.durationMs)
            append(" ms")
            if (result.exitCode != null) {
                append(", ")
                append(words.codeExitCode)
                append(" ")
                append(result.exitCode)
            }
        }
        else -> null
    }

    Column(modifier.fillMaxWidth().background(colors.desk)) {
        Row(
            Modifier
                .fillMaxWidth()
                /*
                  Na telefonie trzy zakładki zajmują niemal całą szerokość, więc
                  wystarczy dłuższy napis, żeby ostatnia wypadła za krawędź.
                  Pasek przewija się wtedy w bok — tak samo jak pasek pisaków
                  w notatniku odręcznym.
                */
                .then(if (narrow) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelTab.entries.forEach { variant ->
                val mark = when (variant) {
                    PanelTab.ERRORS -> if (!result?.errors.isNullOrBlank()) " !" else ""
                    else -> ""
                }
                Box(
                    Modifier
                        .height(44.dp)
                        .clickable(onClickLabel = variant.label(words)) { onTab(variant) }
                        .background(if (tab == variant) colors.sheet else colors.desk)
                        .padding(horizontal = if (narrow) 10.dp else 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = when (variant) {
                                PanelTab.OUTPUT -> KajetIcons.OutputPanel
                                PanelTab.ERRORS -> KajetIcons.ErrorMark
                                PanelTab.INPUT -> KajetIcons.InputArrow
                            },
                            contentDescription = null,
                            tint = if (tab == variant) colors.accent else colors.muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = variant.label(words) + mark,
                            style = Kajet.type.label,
                            color = if (tab == variant) colors.text else colors.muted,
                        )
                    }
                }
            }
            /*
              Na szerokim ekranie status stoi przy prawej krawędzi tego samego
              wiersza. Jeden wiersz i wielokropek, bo Compose oddaje ostatniemu
              dziecku bez wagi TO, CO ZOSTAŁO po zakładkach — a zostawało
              czasem tyle, że napis łamał się po jednej literze i rósł w dół,
              zasłaniając wydruk programu.
            */
            if (!narrow && status != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = status,
                    style = Kajet.type.meta,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }

        // Na telefonie status idzie do własnego wiersza pod zakładkami. Tam ma
        // całą szerokość i nie walczy z nimi o miejsce.
        if (narrow && status != null) {
            Text(
                text = status,
                style = Kajet.type.meta,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            when (tab) {
                PanelTab.OUTPUT -> PanelText(
                    text = result?.output.orEmpty(),
                    placeholder = words.codeOutputEmpty,
                )

                PanelTab.ERRORS -> PanelText(
                    text = result?.errors.orEmpty(),
                    placeholder = words.codeNoErrors,
                    color = colors.danger,
                )

                PanelTab.INPUT -> Column {
                    SectionLabel(words.codeInputLabel)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        textStyle = Kajet.type.code.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelText(
    text: String,
    placeholder: String,
    color: androidx.compose.ui.graphics.Color = Kajet.colors.text,
) {
    if (text.isBlank()) {
        Text(placeholder, style = Kajet.type.body, color = Kajet.colors.muted)
    } else {
        Text(
            text = text,
            style = Kajet.type.code,
            color = color,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
    }
}
