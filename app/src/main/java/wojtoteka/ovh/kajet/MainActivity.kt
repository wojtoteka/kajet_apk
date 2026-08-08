package wojtoteka.ovh.kajet

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wojtoteka.ovh.kajet.cloud.DeviceAuthBridge
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.navigation.KajetNavigation
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.storage.ThemeChoice

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        captureAuthIntent(intent)

        val container = (application as KajetApp).container

        setContent {
            KajetAppRoot(container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        /*
          Rozkaz o drganiu rysika gaśnie, gdy Kajet schodzi w tło. Jeśli
          notatnik odręczny jest wciąż otwarty, po powrocie zamawiamy profil
          od nowa; bez otwartego notatnika to nic nie robi.
        */
        runCatching { PenHaptics.register(this) }

        // Wejście do aplikacji to najlepszy moment na zajrzenie do chmury:
        // człowiek właśnie patrzy na spis notatek i chce w nim widzieć to,
        // co dopisał gdzie indziej.
        (application as KajetApp).container.cloud.sync.syncSoon()
    }

    /*
      Zejście w tło oddaje zastane brzmienie rysika. To ustawienie całego
      urządzenia, więc Kajet nie zostawia go po sobie przestawionego —
      a usługa i tak gasi wtedy haptykę sama.
    */
    override fun onStop() {
        super.onStop()
        runCatching { PenHaptics.quiet(this) }
    }

    /*
      Rozkaz o drganiu rysika jest ulotny: usługa Lenovo gasi go, gdy rysik
      odjeżdża od ekranu albo gdy Kajet znika pod zasłoną powiadomień — a ta
      nie przechodzi przez onStop/onResume. Jedyny pewny moment na
      przypomnienie to zbliżenie rysika: najechanie (hover) przychodzi, zanim
      końcówka dotknie ekranu, więc pierwsza kreska po powrocie ma już
      właściwe drganie. Bez otwartego notatnika odręcznego przypomnienie
      kończy się na jednym porównaniu, a powtórki PenHaptics odrzuca samo.
    */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_HOVER_ENTER &&
            ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        ) {
            runCatching { PenHaptics.refresh(this) }
            // Usługa Lenovo wznawia drganie przy KAŻDYM najechaniu nad
            // aplikację — także nad paskami i menu. Poza powierzchnią
            // pisania trzeba je od razu dogasić.
            runCatching { PenHaptics.settle(this) }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    // Zapas na tablety, które nie zgłaszają najechania: samo dotknięcie
    // rysikiem też przypomina usłudze obowiązujący profil.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        ) {
            runCatching { PenHaptics.refresh(this) }
            runCatching { PenHaptics.settle(this) }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Powrót ostrości okna — zasłona powiadomień właśnie zjechała, rozkaz
    // trzeba wysłać od nowa i to bez czekania na odstęp między powtórkami.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) runCatching { PenHaptics.wake(this) }
    }

    private fun captureAuthIntent(intent: Intent?) {
        DeviceAuthBridge.offer(intent?.data)
    }
}

@Composable
private fun KajetAppRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)

    // Dopóki ustawienia nie wczytają się z dysku, nie wiemy, czy katalog jest już
    // wybrany. Pokazujemy wtedy samo tło, żeby nie mrugnąć ekranem powitalnym.
    val loaded = settings ?: return

    val dark = when (loaded.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    KajetTheme(darkTheme = dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.desk)
                .systemBarsPadding(),
        ) {
            KajetNavigation(container = container, settings = loaded)
        }
    }
}
