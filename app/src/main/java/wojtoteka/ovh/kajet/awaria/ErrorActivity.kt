package wojtoteka.ovh.kajet.awaria

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.MainActivity
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.core.design.component.PrimaryButton
import wojtoteka.ovh.kajet.core.text.AppLanguage
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.stringsFor
import wojtoteka.ovh.kajet.core.text.systemLanguage

/**
 * Ekran po awarii - zamiast czarnego ekranu, z którego wychodziło się tylko
 * ubiciem aplikacji.
 *
 * Działa w OSOBNYM procesie (`:blad` w manifeście): po nieobsłużonym wyjątku
 * wątek główny starego procesu jest już martwy, więc ekran w tym samym
 * procesie mógłby w ogóle się nie narysować. Z tego samego powodu nie wolno
 * tu dotykać [wojtoteka.ovh.kajet.AppContainer] - ten ekran ma wstać zawsze,
 * nawet gdy awaria siedzi właśnie w budowie kontenera.
 */
class ErrorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
          Raport jest pokazany dopiero teraz - kiedy ten ekran naprawdę wstał.

          Odhaczenie musi siedzieć TUTAJ, a nie przy wywołaniu startActivity:
          zablokowany start z tła nie rzuca wyjątku, więc po stronie wołającego
          wygląda jak powodzenie i raport przepadłby nieprzeczytany.

          Znacznik idzie do pliku, bo ten ekran chodzi w osobnym procesie
          (`:blad`) i ustawień aplikacji nie dzieli z resztą Kajetu.
        */
        runCatching { CrashLog.markSeen(this) }

        val details = intent.getStringExtra(EXTRA_DETAILS)
            ?: runCatching { CrashLog.lastCrash(this)?.readText() }.getOrNull()
            ?: ""

        setContent {
            // Ustawienia (a z nimi wybrany język i motyw) mogą być nieosiągalne
            // po awarii - idziemy wprost za systemem, jak ekran rozruchu.
            val words = remember { stringsFor(AppLanguage.SYSTEM, systemLanguage()) }
            KajetTheme(darkTheme = isSystemInDarkTheme()) {
                CompositionLocalProvider(LocalStrings provides words) {
                    ErrorScreen(
                        details = details,
                        onRestart = ::restartApp,
                        onSend = { text -> sendLog(text, words.errorSendTitle) },
                    )
                }
            }
        }
    }

    /**
     * Podaje opis awarii dalej systemowym „Udostępnij" - pocztą, komunikatorem,
     * czymkolwiek. Sam tekst, bez pliku: nie trzeba wtedy dostawcy plików,
     * który po awarii może być właśnie tym, co nie działa.
     */
    private fun sendLog(text: String, subject: String) {
        val message = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, CrashLog.clip(text))
        }
        runCatching { startActivity(Intent.createChooser(message, subject)) }
    }

    private fun restartApp() {
        // Stary proces już nie żyje (Process.killProcess po zapisie raportu),
        // więc ten intent stawia aplikację od zera, ze świeżym kontenerem.
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finishAffinity()
    }

    companion object {
        private const val EXTRA_DETAILS = "szczegoly"

        /**
         * [report] to gotowy opis z [CrashLog.report] - z wersją aplikacji,
         * modelem urządzenia i nazwą wątku. Ten sam tekst idzie do pliku, na
         * ekran i do wysyłki, żeby zgłoszenie zgadzało się z tym, co widać.
         */
        fun intent(context: Context, report: String): Intent =
            Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_DETAILS, CrashLog.clip(report))
            }
    }
}

@Composable
private fun ErrorScreen(
    details: String,
    onRestart: () -> Unit,
    onSend: (String) -> Unit,
) {
    val words = LocalStrings.current
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(words.errorScreenTitle, style = Kajet.type.display, color = Kajet.colors.text)
        Text(
            text = words.errorScreenAbout,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
        )

        Text(
            text = details.ifBlank { words.errorNoDetails },
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

        // Restart zostaje PrimaryButton. Kopiowanie i wysyłka są akcjami
        // tekstowymi jak „Wyczyść" w konsoli i schodzą pod spód - dwa
        // SecondaryButton 48 dp w jednym rzędzie nie mieszczą się na telefonie.
        PrimaryButton(text = words.errorRestart, onClick = onRestart)
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BarTextAction(words.errorCopyDetails) {
                clipboard.setText(AnnotatedString(details))
            }
            BarTextAction(words.errorSendDetails) {
                onSend(details.ifBlank { words.errorNoDetails })
            }
        }
    }
}

/*
  Akcja tekstowa, nie SecondaryButton. Ten ma 48 dp i obwódkę - w rzędzie
  obok „Uruchom ponownie" odcinał się od tła i na telefonie wychodził
  poza ekran. Tu ten sam krój co etykiety, bez ramki.
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
