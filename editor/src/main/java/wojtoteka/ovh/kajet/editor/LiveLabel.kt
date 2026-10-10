package wojtoteka.ovh.kajet.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.storage.LiveStatus

/**
 * Stan edycji na żywo w pasku notatki, zaraz za „Zapisane": „Połączono",
 * „Offline", inicjały osób, które mają notatkę otwartą w tej chwili (każda
 * w swoim stałym kolorze, tym samym co na stronie), i - przez kilka sekund
 * po cudzej zmianie - od kogo ona przyszła.
 *
 * Do 26.10.06 to był osobny dymek (Popup) przyklejony do środka ekranu -
 * wisiał na pasku narzędzi i zasłaniał przyciski. Teraz stoi w rzędzie
 * z wskaźnikiem zapisu, więc każdy edytor ma go tam, gdzie ma swój tytuł.
 * Jeden wiersz, bez łamania - z tego samego powodu co w SaveIndicator.
 */
@Composable
fun LiveLabel(model: NoteViewModel, modifier: Modifier = Modifier) {
    val words = LocalStrings.current
    val colors = Kajet.colors
    val status by model.liveStatus.collectAsStateWithLifecycle()
    val people by model.livePeople.collectAsStateWithLifecycle()
    val author by model.liveAuthor.collectAsStateWithLifecycle()
    val readOnly by model.readOnly.collectAsStateWithLifecycle()
    val gone by model.liveGone.collectAsStateWithLifecycle()

    var freshAuthor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(author) {
        val (name, _) = author ?: return@LaunchedEffect
        freshAuthor = name
        delay(4_000)
        freshAuthor = null
    }

    if (status == LiveStatus.OFF && !readOnly && !gone) return

    val label = when {
        gone -> words.liveGone
        readOnly -> words.liveReadOnly
        // Kółka z inicjałami same mówią, że jesteśmy połączeni - napis mówi
        // wtedy, czyje to kółka.
        status == LiveStatus.LIVE && people.isNotEmpty() -> words.liveOthersHere
        status == LiveStatus.LIVE -> words.liveNow
        status == LiveStatus.CONNECTING -> words.liveConnecting
        else -> words.liveOffline
    }
    val about = when {
        gone -> words.liveGoneAbout
        status == LiveStatus.OFFLINE -> words.liveOfflineAbout
        else -> label
    }
    val labelColor = when {
        gone || status == LiveStatus.OFFLINE -> colors.danger
        else -> colors.muted
    }

    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = about },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Cienka kreska oddziela stan połączenia od stanu zapisu.
        Box(Modifier.width(1.dp).height(14.dp).background(colors.line))
        Text(
            text = label,
            style = Kajet.type.meta,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        for (person in people.take(MAX_FACES)) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(PERSON_COLORS[person.color.mod(PERSON_COLORS.size)], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (person.name.trim().firstOrNull() ?: '?').uppercaseChar().toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (people.size > MAX_FACES) {
            Text("+${people.size - MAX_FACES}", style = Kajet.type.meta, color = colors.muted)
        }
        freshAuthor?.let {
            Text(
                "${words.liveChangeFrom} $it",
                style = Kajet.type.meta,
                color = colors.muted,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val MAX_FACES = 5

/** Te same osiem barw co na stronie (globals.css, .live-person.tone-N). */
private val PERSON_COLORS = listOf(
    Color(0xFF0F6B5C),
    Color(0xFFA6392E),
    Color(0xFF2850A0),
    Color(0xFFB8860B),
    Color(0xFF6B3FA0),
    Color(0xFF4A4640),
    Color(0xFF1F7A8C),
    Color(0xFF8C4A1F),
)
