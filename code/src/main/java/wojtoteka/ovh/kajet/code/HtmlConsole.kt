package wojtoteka.ovh.kajet.code

import android.webkit.ConsoleMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.codeConsoleAtLine
import wojtoteka.ovh.kajet.core.text.codeConsoleFull

/**
 * Konsola podglądu strony.
 *
 * W notatce „JavaScript" stoi Node — bez `document` i bez `alert` — więc szkolne
 * zadania z JavaScriptu robi się w notatce HTML. Na tablecie nie ma jak otworzyć
 * narzędzi przeglądarki, a bez nich `console.log` wpada w próżnię i zadania
 * z pętlą albo tablicą nie da się sprawdzić inaczej niż przez wypisywanie
 * wszystkiego do treści strony.
 *
 * Nic tu nie wstrzykujemy w cudzą stronę. WebView oddaje te wiersze sam, przez
 * [android.webkit.WebChromeClient.onConsoleMessage] — razem z błędami skryptów
 * i nieobsłużonymi obietnicami, których żaden dopisany `console.log` i tak by
 * nie złapał.
 */
class HtmlConsoleState {

    private val entries: SnapshotStateList<ConsoleLine> =
        emptyList<ConsoleLine>().toMutableStateList()

    val lines: List<ConsoleLine> get() = entries

    /**
     * Czy konsola przestała zbierać, bo doszła do [LIMIT].
     *
     * To nie to samo co „lista jest pełna": po wyczyszczeniu wraca na fałsz
     * i zbieranie rusza od nowa.
     */
    var full by mutableStateOf(false)
        private set

    /** Nowy wpis od strony. Wołane przez WebView, zawsze z wątku ekranu. */
    fun add(message: ConsoleMessage) {
        if (entries.size >= LIMIT) {
            full = true
            return
        }
        entries += ConsoleLine.of(message)
    }

    /**
     * Wpis, który nie przyszedł z `console.log` — na razie tylko okna
     * `alert`, `confirm` i `prompt`, przechwycone zamiast pokazane.
     */
    fun addRaw(text: String) {
        if (entries.size >= LIMIT) {
            full = true
            return
        }
        entries += ConsoleLine(mark = "›", text = text, warning = false, line = 0)
    }

    /**
     * Czyszczenie. Robi to i przycisk, i każde nowe ładowanie strony — podgląd
     * odświeża się po każdej literze, więc bez tego zostałaby po nim mieszanka
     * wpisów z dziesiątek kolejnych wersji tej samej strony.
     */
    fun clear() {
        entries.clear()
        full = false
    }

    companion object {
        /**
         * Granica zbierania.
         *
         * Pętla z console.log wypisuje tysiące wierszy szybciej, niż da się je
         * narysować — bez tej granicy podgląd zwieszał się razem z edytorem,
         * a wiersz numer cztery tysiące i tak nikomu nic nie mówi.
         */
        const val LIMIT = 300
    }
}

/**
 * Jeden wiersz konsoli.
 *
 * [mark] jest po to, żeby rodzaj wpisu było widać BEZ koloru — na jasnym
 * ekranie w słońcu i przy niedowidzeniu barw ostrzeżenie odróżnia się znakiem,
 * a nie odcieniem.
 *
 * [line] to numer wiersza w kodzie strony; zero znaczy, że WebView go nie podał.
 * Napis układa się z niego dopiero przy rysowaniu, bo zależy od wybranego
 * języka Kajetu, a ten może się zmienić już po zebraniu wpisu.
 */
data class ConsoleLine(
    val mark: String,
    val text: String,
    val warning: Boolean,
    val line: Int,
) {
    companion object {
        fun of(message: ConsoleMessage): ConsoleLine {
            val level = message.messageLevel()
            val error = level == ConsoleMessage.MessageLevel.ERROR
            val warning = level == ConsoleMessage.MessageLevel.WARNING

            return ConsoleLine(
                mark = when {
                    error -> "✕"
                    warning -> "▲"
                    else -> "›"
                },
                text = message.message().orEmpty(),
                warning = error || warning,
                /*
                  Numer wiersza dokładamy tylko przy błędach i ostrzeżeniach.
                  Zwykły console.log też go niesie, ale przy pięćdziesięciu
                  wypisanych liczbach pod rząd jest wyłącznie szumem — szuka
                  się miejsca w kodzie wtedy, gdy coś poszło nie tak.
                */
                line = if (error || warning) message.lineNumber() else 0,
            )
        }
    }
}

@Composable
fun rememberHtmlConsole(): HtmlConsoleState = remember { HtmlConsoleState() }

/**
 * Konsola pod podglądem — stoi dokładnie tam, gdzie przy pozostałych językach
 * stoi wynik uruchomienia.
 */
@Composable
fun HtmlConsolePanel(state: HtmlConsoleState, modifier: Modifier = Modifier) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val listState = rememberLazyListState()

    // Konsola nadąża za stroną: świeży wiersz ma być widoczny bez przewijania,
    // tak samo jak w narzędziach przeglądarki.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex)
    }

    Column(modifier.fillMaxWidth().background(colors.desk)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KajetIcons.OutputPanel,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(words.codeConsole, style = Kajet.type.label, color = colors.text)
            Spacer(Modifier.weight(1f))
            SecondaryButton(words.codeConsoleClear, state::clear)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.sheet)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (state.lines.isEmpty()) {
                Text(words.codeConsoleEmpty, style = Kajet.type.body, color = colors.muted)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.lines) { entry ->
                        Text(
                            text = buildString {
                                append(entry.mark)
                                append(' ')
                                append(entry.text)
                                if (entry.line > 0) {
                                    append("  (")
                                    append(words.codeConsoleAtLine(entry.line))
                                    append(')')
                                }
                            },
                            style = Kajet.type.code,
                            color = if (entry.warning) colors.danger else colors.text,
                        )
                    }
                    if (state.full) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = words.codeConsoleFull(HtmlConsoleState.LIMIT),
                                style = Kajet.type.meta,
                                color = colors.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
