package wojtoteka.ovh.kajet

import android.content.Intent
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
import wojtoteka.ovh.kajet.cloud.DeviceAuthBridge
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.KajetTheme
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
