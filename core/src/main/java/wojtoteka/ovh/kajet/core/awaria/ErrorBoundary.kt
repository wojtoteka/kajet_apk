package wojtoteka.ovh.kajet.core.awaria

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.text.LocalStrings
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.coroutines.cancellation.CancellationException

/**
 * Granica błędu dla ekranu.
 *
 * Wyjątek rzucony przy składaniu albo mierzeniu [content] zatrzymuje się tutaj:
 * zamiast przewrócić wątek główny i zostawić samo tło okna (a biurko w ciemnym
 * motywie to praktycznie czerń, więc wygląda to jak zawieszenie), pokazuje się
 * ekran z opisem błędu i przyciskiem do złożenia ekranu jeszcze raz.
 *
 * Dlaczego [SubcomposeLayout], a nie zwykłe `try` wokół [content]: kompilator
 * Compose nie pozwala objąć wywołania composable blokiem `try`. W mierzeniu
 * składanie idzie przez `subcompose`, czyli zwykłe wywołanie funkcji - i to
 * wolno złapać.
 *
 * Przeładowanie zmienia numer podejścia, który jest zarazem kluczem gniazda.
 * Poddrzewo powstaje wtedy od zera, bo po przerwanym składaniu nie ma czego
 * odzyskiwać.
 *
 * Czego to NIE łapie: wyjątków z korutyn (od tego jest [failureHandler]) i
 * z rysowania. Te idą do domyślnego handlera wątku, czyli kończą się ekranem
 * po awarii z przyciskiem ponownego uruchomienia.
 */
@Composable
fun ErrorBoundary(
    modifier: Modifier = Modifier,
    onFailure: ((Throwable) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var broken by remember { mutableStateOf<Throwable?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    val failure = broken
    if (failure != null) {
        BrokenScreen(
            failure = failure,
            onReload = {
                broken = null
                attempt += 1
            },
        )
        return
    }

    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        // `broken` jest tu tylko zapisywane, nigdy czytane. Odczyt w mierzeniu
        // razem z zapisem tej samej wartości kręciłby układ w kółko.
        val placeables = try {
            subcompose(attempt) { content() }.map { it.measure(constraints) }
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Throwable) {
            Log.e(TAG, "Ekran się nie złożył", thrown)
            broken = thrown
            runCatching { onFailure?.invoke(thrown) }
            emptyList()
        }

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.maxOfOrNull { it.width } ?: 0
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            placeables.maxOfOrNull { it.height } ?: 0
        }

        layout(width, height) {
            for (placeable in placeables) placeable.place(0, 0)
        }
    }
}

@Composable
private fun BrokenScreen(failure: Throwable, onReload: () -> Unit) {
    val words = LocalStrings.current
    val details = remember(failure) {
        val writer = StringWriter()
        failure.printStackTrace(PrintWriter(writer))
        writer.toString()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(words.screenBrokeTitle, style = Kajet.type.title, color = Kajet.colors.text)
        Text(words.screenBrokeAbout, style = Kajet.type.body, color = Kajet.colors.muted)

        Text(
            text = details,
            style = Kajet.type.code,
            color = Kajet.colors.text,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Kajet.colors.sheet, RoundedCornerShape(Kajet.dimens.corner))
                .border(1.dp, Kajet.colors.line, RoundedCornerShape(Kajet.dimens.corner))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        )

        PrimaryButton(text = words.screenBrokeReload, onClick = onReload)
    }
}

private const val TAG = "Kajet"
