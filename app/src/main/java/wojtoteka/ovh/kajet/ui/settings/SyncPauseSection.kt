package wojtoteka.ovh.kajet.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import wojtoteka.ovh.kajet.cloud.SyncPause
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.InlineNotice
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.Strings
import wojtoteka.ovh.kajet.core.text.syncPausedUntil
import wojtoteka.ovh.kajet.ui.library.ChoiceRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Wybór, na jak długo wstrzymać synchronizację, i pasek „wstrzymana do…"
 * z przyciskiem wznowienia. Samo wstrzymanie robi chmura ([SyncPause]);
 * tu zapada tylko decyzja, do kiedy.
 */
@Composable
internal fun SyncPauseControls(
    pause: SyncPause,
    signedIn: Boolean,
    onPause: (until: Long) -> Unit,
    onResume: () -> Unit,
) {
    val words = LocalStrings.current
    val context = LocalContext.current
    val pausedUntil by pause.until.collectAsStateWithLifecycle()

    // Pauza kończy się sama - ekran ma to zauważyć bez wychodzenia z ustawień.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pausedUntil) {
        now = System.currentTimeMillis()
        if (pausedUntil > now && pausedUntil != SyncPause.FOREVER) {
            delay(pausedUntil - now)
            now = System.currentTimeMillis()
        }
    }
    val paused = pausedUntil > now
    var problem by remember { mutableStateOf<String?>(null) }

    if (!signedIn && !paused) {
        Text(
            text = words.syncPauseNeedsAccount,
            style = Kajet.type.meta,
            color = Kajet.colors.muted,
            modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
        )
        return
    }

    if (paused) {
        InlineNotice(
            icon = KajetIcons.Offline,
            text = if (pausedUntil == SyncPause.FOREVER) {
                words.syncPausedForever
            } else {
                words.syncPausedUntil(formatMoment(pausedUntil, words))
            },
            color = Kajet.colors.muted,
        ) {
            SecondaryButton(
                text = words.syncResume,
                onClick = {
                    problem = null
                    onResume()
                },
                icon = KajetIcons.CloudMark,
            )
        }
    }

    fun pauseFor(millis: Long) {
        problem = null
        onPause(System.currentTimeMillis() + millis)
    }

    ChoiceRow(words.syncPauseHour, null, selected = false) { pauseFor(HOUR) }
    ChoiceRow(words.syncPauseDay, null, selected = false) { pauseFor(24 * HOUR) }
    ChoiceRow(words.syncPauseWeek, null, selected = false) { pauseFor(7 * 24 * HOUR) }
    ChoiceRow(words.syncPauseCustom, null, selected = false) {
        problem = null
        // Data, potem godzina - zwykłe okna systemu, znane z budzika i kalendarza.
        val start = Calendar.getInstance().apply {
            if (paused && pausedUntil != SyncPause.FOREVER) timeInMillis = pausedUntil else add(Calendar.HOUR_OF_DAY, 2)
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val chosen = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (chosen <= System.currentTimeMillis()) {
                            problem = words.syncPausePastTime
                        } else {
                            onPause(chosen)
                        }
                    },
                    start.get(Calendar.HOUR_OF_DAY),
                    start.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context),
                ).show()
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1_000 }.show()
    }
    ChoiceRow(
        words.syncPauseForever,
        null,
        selected = paused && pausedUntil == SyncPause.FOREVER,
    ) {
        problem = null
        onPause(SyncPause.FOREVER)
    }

    problem?.let {
        Text(
            text = it,
            style = Kajet.type.meta,
            color = Kajet.colors.danger,
            modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
        )
    }
}

private const val HOUR = 60 * 60 * 1000L

private fun formatMoment(moment: Long, words: Strings): String {
    val locale = if (words.english) Locale.UK else Locale("pl", "PL")
    return SimpleDateFormat("d MMMM, HH:mm", locale).format(Date(moment))
}
