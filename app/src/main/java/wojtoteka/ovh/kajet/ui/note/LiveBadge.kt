package wojtoteka.ovh.kajet.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.editor.NoteViewModel
import wojtoteka.ovh.kajet.storage.LiveStatus

/**
 * Znaczek edycji na żywo nad edytorem: napis o połączeniu, inicjały osób,
 * które mają tę notatkę otwartą w tej chwili (każda w swoim stałym kolorze,
 * tym samym co na stronie), i - przez kilka sekund po cudzej zmianie - od
 * kogo ona przyszła. Bez kropki stanu, tak jak na stronie: stan mówi napis,
 * a brak połączenia jest na czerwono.
 *
 * Stoi w osobnym okienku nad edytorem (Popup), więc żaden z trzech edytorów
 * nie musiał zmieniać swojego układu. Okienko nie łapie skupienia - pisanie
 * i rysowanie idą dalej pod nim.
 */
@Composable
fun LiveBadge(model: NoteViewModel) {
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
    val labelColor = when {
        gone || status == LiveStatus.OFFLINE -> colors.danger
        status == LiveStatus.CONNECTING -> colors.muted
        else -> colors.text
    }
    val offset = with(LocalDensity.current) { 70.dp.roundToPx() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offset),
        properties = PopupProperties(focusable = false, clippingEnabled = true),
    ) {
        Row(
            modifier = Modifier
                .background(colors.sheet, RoundedCornerShape(50))
                .border(1.dp, colors.line, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = Kajet.type.meta, color = labelColor, maxLines = 2)
            for (person in people.take(MAX_FACES)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(PERSON_COLORS[person.color.mod(PERSON_COLORS.size)], CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (person.name.trim().firstOrNull() ?: '?').uppercaseChar().toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (people.size > MAX_FACES) {
                Text("+${people.size - MAX_FACES}", style = Kajet.type.meta, color = colors.muted)
            }
            freshAuthor?.let {
                Text("${words.liveChangeFrom} $it", style = Kajet.type.meta, color = colors.muted, maxLines = 1)
            }
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
