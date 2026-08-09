package wojtoteka.ovh.kajet

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.awaria.CrashLog
import wojtoteka.ovh.kajet.awaria.ErrorActivity
import wojtoteka.ovh.kajet.cloud.DeviceAuthBridge
import wojtoteka.ovh.kajet.core.awaria.ErrorBoundary
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.core.design.component.KajetMark
import wojtoteka.ovh.kajet.core.text.AppLanguage
import wojtoteka.ovh.kajet.ink.PenHaptics
import wojtoteka.ovh.kajet.core.text.CurrentStrings
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.text.stringsFor
import wojtoteka.ovh.kajet.core.text.systemLanguage
import wojtoteka.ovh.kajet.navigation.KajetNavigation
import wojtoteka.ovh.kajet.ui.update.UpdateNotice
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.storage.ThemeChoice

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        captureAuthIntent(intent)

        // Awaria, która trafiła Kajet zwinięty do tła, nie mogła wtedy otworzyć
        // ekranu — od Androida 12 system na to nie pozwala. Raport został w
        // pliku i pokazujemy go teraz, przy pierwszym otwarciu aplikacji.
        if (showUnseenCrash()) return

        val container = (application as KajetApp).container

        setContent {
            KajetAppRoot(container)
        }
    }

    private fun showUnseenCrash(): Boolean {
        val report = runCatching { CrashLog.unseenCrash(this)?.readText() }.getOrNull()
            ?: return false
        CrashLog.markSeen(this)
        val opened = runCatching { startActivity(ErrorActivity.intent(this, report)) }.isSuccess
        if (opened) finish()
        return opened
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        /*
          Wejście do aplikacji to najlepszy moment na zajrzenie do chmury:
          człowiek właśnie patrzy na spis notatek i chce w nim widzieć to, co
          dopisał gdzie indziej.

          Ale POZA wątkiem głównym. `container.cloud` to `by lazy`, czyli zamek,
          a za nim magazyn kluczy, zaszyfrowane ustawienia i plan WorkManagera.
          Wołane stąd wprost, wątek główny czekał na wątek rozgrzewki i ekran
          stał — a tło okna w ciemnym motywie jest niemal czarne, więc wyglądało
          to na zawieszoną aplikację.
        */
        /*
          Rozkaz o drganiu rysika gaśnie, gdy Kajet schodzi w tło. Jeśli
          notatnik odręczny jest wciąż otwarty, po powrocie zamawiamy profil
          od nowa; bez otwartego notatnika to nic nie robi.
        */
        runCatching { PenHaptics.register(this) }

        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                val cloud = (application as KajetApp).container.cloud
                // Najpierw token, potem notatki. Sesja mogła paść, gdy Kajet
                // leżał w tle — wylogowanie przez stronę nie ma jak się tu
                // zgłosić samo.
                cloud.auth.check()
                cloud.sync.syncSoon()
            }
        }
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

    /*
      Dopóki ustawienia nie wczytają się z dysku, nie wiemy, czy katalog jest
      już wybrany.

      Stało tu kiedyś samo tło w kolorze biurka. A biurko w ciemnym motywie to
      #171614, czyli praktycznie czerń — więc człowiek widział czarny ekran nie
      do odróżnienia od zawieszonej aplikacji i ratował się jej ubijaniem.
      Teraz widać znak Kajetu i napis, a gdyby odczyt naprawdę się zaciął, po
      chwili dochodzi wyjaśnienie. Ten ekran ma zawsze coś mówić.
    */
    val loaded = settings
    if (loaded == null) {
        // Ustawień jeszcze nie ma, więc i wybranego języka nie znamy — na tym
        // jednym ekranie idziemy wprost za systemem.
        val words = remember { stringsFor(AppLanguage.SYSTEM, systemLanguage()) }
        KajetTheme(darkTheme = isSystemInDarkTheme()) {
            CompositionLocalProvider(LocalStrings provides words) { StartingUp() }
        }
        return
    }

    val dark = when (loaded.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    /*
      Język. Bez własnego wyboru bierzemy ten z systemu — po polsku, gdy system
      jest po polsku, po angielsku w każdym innym przypadku. Wybór z ustawień
      wygrywa i działa od razu, bez przeładowania ekranu.
    */
    val words = remember(loaded.language) {
        stringsFor(AppLanguage.fromId(loaded.language), systemLanguage())
    }

    // Ten sam wybór dla kodu spoza Compose: modele widoku i synchronizacja
    // też układają zdania, które trafiają potem na ekran.
    SideEffect { CurrentStrings.value = words }

    KajetTheme(darkTheme = dark) {
      CompositionLocalProvider(LocalStrings provides words) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.desk)
                .systemBarsPadding()
                // Otwarta klawiatura zwęża treść zamiast ją zasłaniać —
                // przyciski pod polami tekstowymi zostają widoczne.
                .imePadding(),
        ) {
            // Granica błędu obejmuje CAŁĄ nawigację, więc wyjątek z dowolnego
            // ekranu kończy się opisem i przyciskiem przeładowania, a nie
            // samym tłem biurka.
            ErrorBoundary {
                KajetNavigation(container = container, settings = loaded)
            }

            /*
              Komunikat o nowej wersji stoi OBOK nawigacji, nie w niej: ma się
              pokazać niezależnie od tego, na którym ekranie zaczyna się praca,
              także przy pierwszym uruchomieniu na wyborze katalogu. Zapytanie
              serwera idzie w tle i niczego nie wstrzymuje, a gdy nie ma sieci,
              nie dzieje się nic.
            */
            UpdateNotice()
        }
      }
    }
}

/**
 * Ekran na te ułamki sekundy, w których ustawienia jadą jeszcze z dysku.
 *
 * Po pięciu sekundach dochodzi zdanie o tym, co robić — bo jeśli tyle to trwa,
 * to znaczy, że coś stoi, a człowiek ma prawo wiedzieć, że to nie on zepsuł.
 */
@Composable
private fun StartingUp() {
    var takingLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        takingLong = true
    }

    Box(
        Modifier.fillMaxSize().background(Kajet.colors.desk),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KajetMark(Modifier.size(64.dp), color = Kajet.colors.accent)
            Text(
                text = LocalStrings.current.starting,
                style = Kajet.type.body,
                color = Kajet.colors.muted,
            )
            if (takingLong) {
                Text(
                    text = LocalStrings.current.startingSlow,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}
