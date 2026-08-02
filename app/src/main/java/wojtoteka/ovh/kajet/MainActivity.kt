package wojtoteka.ovh.kajet

import android.os.Bundle
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
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.KajetTheme
import wojtoteka.ovh.kajet.nawigacja.KajetNawigacja
import wojtoteka.ovh.kajet.storage.UstawieniaKajetu
import wojtoteka.ovh.kajet.storage.WyborMotywu

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val kontener = (application as KajetApp).kontener

        setContent {
            AplikacjaKajet(kontener)
        }
    }
}

@Composable
private fun AplikacjaKajet(kontener: Kontener) {
    val ustawienia by kontener.ustawienia.ustawienia.collectAsStateWithLifecycle(null)

    // Dopoki ustawienia nie wczytaja sie z dysku, nie wiemy, czy katalog jest juz
    // wybrany. Pokazujemy wtedy samo tlo, zeby nie mrugnac ekranem powitalnym.
    val wczytane = ustawienia ?: return

    val ciemny = when (wczytane.motyw) {
        WyborMotywu.SYSTEM -> isSystemInDarkTheme()
        WyborMotywu.JASNY -> false
        WyborMotywu.CIEMNY -> true
    }

    KajetTheme(darkTheme = ciemny) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.desk)
                .systemBarsPadding(),
        ) {
            KajetNawigacja(kontener = kontener, ustawienia = wczytane)
        }
    }
}
