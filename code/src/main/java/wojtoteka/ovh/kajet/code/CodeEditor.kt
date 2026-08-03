package wojtoteka.ovh.kajet.code

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.LanguageIcons
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons

@Composable
fun CodeEditor(
    model: CodeViewModel,
    onBack: () -> Unit,
) {
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

            if (running) {
                IconAction(KajetIcons.StopSquare, "Zatrzymaj", model::stop)
            } else {
                IconAction(
                    icon = KajetIcons.PlayRun,
                    description = "Uruchom program",
                    onClick = model::run,
                    enabled = model.runner != null,
                    selected = model.runner != null,
                )
            }
            IconAction(KajetIcons.Search, "Szukaj w pliku", { searchVisible = !searchVisible }, selected = searchVisible)
            IconAction(KajetIcons.WordWrap, "Zawijanie wierszy", model::toggleWordWrap, selected = wordWrap)
            Spacer(Modifier.height(12.dp))
        }

        Column(Modifier.fillMaxSize()) {

            FileHeader(model = model, saved = saved)
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
                            query.length < 2 -> "Wpisz co najmniej dwie litery"
                            matches.isEmpty() -> "Nic nie znalazłem"
                            else -> "Znalazłem ${matches.size}"
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
                    SecondaryButton("Rozumiem", model::dismissError)
                }
                HorizontalRule()
            }

            Box(
                Modifier
                    .weight(0.62f)
                    .fillMaxWidth()
                    .background(colors.sheet)
                    .imePadding(),
            ) {
                val verticalScroll = rememberScrollState()
                Row(Modifier.fillMaxSize().verticalScroll(verticalScroll)) {
                    LineNumberGutter(code)
                    Box(
                        Modifier
                            .weight(1f)
                            .then(
                                if (wordWrap) Modifier else Modifier.horizontalScroll(rememberScrollState()),
                            ),
                    ) {
                        BasicTextField(
                            value = code,
                            onValueChange = model::onCodeChange,
                            textStyle = Kajet.type.code.copy(color = colors.text),
                            cursorBrush = SolidColor(colors.accent),
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
            }

            HorizontalRule()
            ResultPanel(
                tab = tab,
                result = result,
                input = input,
                running = running,
                offline = model.offline,
                modifier = Modifier.weight(0.38f),
                onTab = model::selectTab,
                onInput = model::onInputChange,
            )
        }
    }
}

@Composable
private fun FileHeader(model: CodeViewModel, saved: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kajet.colors.sheet)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = LanguageIcons.forLanguage(model.language),
            contentDescription = model.language.labelPl,
            tint = Kajet.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(model.fileName, style = Kajet.type.title, color = Kajet.colors.text)
            Text(
                text = when {
                    model.runner == null -> "${model.language.labelPl}. Tego języka Kajet nie uruchomi."
                    model.offline -> "${model.language.labelPl}. Uruchamia się na tablecie, bez internetu."
                    else -> "${model.language.labelPl}. Uruchamia się na serwerze, potrzebny internet."
                },
                style = Kajet.type.meta,
                color = Kajet.colors.muted,
            )
        }
        Text(
            text = if (saved) "Zapisane" else "Zmiany czekają",
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
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
    modifier: Modifier,
    onTab: (PanelTab) -> Unit,
    onInput: (String) -> Unit,
) {
    val colors = Kajet.colors

    Column(modifier.fillMaxWidth().background(colors.desk)) {
        Row(
            Modifier.fillMaxWidth(),
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
                        .clickable(onClickLabel = variant.labelPl) { onTab(variant) }
                        .background(if (tab == variant) colors.sheet else colors.desk)
                        .padding(horizontal = 18.dp),
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
                            text = variant.labelPl + mark,
                            style = Kajet.type.label,
                            color = if (tab == variant) colors.text else colors.muted,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (running) {
                Text(
                    text = if (offline) "Liczę na tablecie..." else "Wysyłam na serwer...",
                    style = Kajet.type.meta,
                    color = colors.muted,
                    modifier = Modifier.padding(end = 16.dp),
                )
            } else if (result != null) {
                Text(
                    text = buildString {
                        append(if (result.viaNetwork) "Serwer" else "Tablet")
                        append(", ")
                        append(result.durationMs)
                        append(" ms")
                        if (result.exitCode != null) {
                            append(", kod wyjścia ")
                            append(result.exitCode)
                        }
                    },
                    style = Kajet.type.meta,
                    color = colors.muted,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
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
                    placeholder = "Naciśnij przycisk uruchomienia po lewej stronie. Tu pojawi się to, co program wypisze.",
                )

                PanelTab.ERRORS -> PanelText(
                    text = result?.errors.orEmpty(),
                    placeholder = "Nie ma błędów.",
                    color = colors.danger,
                )

                PanelTab.INPUT -> Column {
                    SectionLabel("Dane, które program przeczyta")
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
