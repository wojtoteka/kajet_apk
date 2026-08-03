package wojtoteka.ovh.kajet.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.KajetMark
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.ui.library.ChoiceRow
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.storage.ThemeChoice
import wojtoteka.ovh.kajet.storage.FingerBehavior

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    repo: LibraryRepository,
    onBack: () -> Unit,
    onRebuildIndex: () -> Unit,
    onAccount: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(KajetSettings())
    val scope = rememberCoroutineScope()
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val railWidth = if (narrow) 48.dp else Kajet.dimens.railWidth
    val sheetPadding = if (narrow) 16.dp else 32.dp

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                repo.setLibraryFolder(uri)
                onRebuildIndex()
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Kajet.colors.desk),
    ) {
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .marginRule(Kajet.colors.line),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                KajetMark(modifier = Modifier.size(26.dp), color = Kajet.colors.accent)
            }
            IconAction(KajetIcons.BackArrow, "Wróć do biblioteki", onBack)
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Kajet.colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(start = sheetPadding, end = sheetPadding, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("Ustawienia", style = Kajet.type.display, color = Kajet.colors.text)

            SettingsSection(
                title = "Konto w chmurze",
                description = "Bez konta Kajet działa normalnie, a notatki leżą tylko na tym urządzeniu. " +
                    "Z kontem trafiają też na serwer i otworzysz je na komputerze.",
            ) {
                SecondaryButton(
                    text = "Otwórz konto",
                    onClick = onAccount,
                    icon = KajetIcons.CloudMark,
                )
            }

            SettingsSection(
                title = "Katalog na notatki",
                description = "Tu leżą wszystkie Twoje pliki. Po zmianie katalogu Kajet przeczyta go od nowa.",
            ) {
                Text(
                    text = settings.libraryFolder?.let { readableFolder(it) }
                        ?: "Jeszcze nie wybrano folderu.",
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Zmień folder",
                        onClick = { folderPicker.launch(null) },
                        icon = KajetIcons.Folder,
                    )
                    SecondaryButton(
                        text = "Odbuduj spis notatek",
                        onClick = onRebuildIndex,
                        icon = KajetIcons.Restore,
                    )
                }
                Text(
                    text = "Odbuduj spis wtedy, gdy skopiowałeś notatki z komputera albo " +
                        "wyszukiwanie nie znajduje czegoś, co na pewno masz.",
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
            }

            SettingsSection(
                title = "Rysik i palec",
                description = "Kiedy rysik dotyka ekranu, dłoń nigdy nie rysuje. To działa zawsze.",
            ) {
                FingerBehavior.entries.forEach { option ->
                    ChoiceRow(
                        text = option.labelPl,
                        description = if (option == FingerBehavior.SCROLL) {
                            "Palcem przesuwasz stronę, rysikiem piszesz. Tak jest najwygodniej."
                        } else {
                            "Palcem też rysujesz. Przydaje się, kiedy nie masz przy sobie rysika."
                        },
                        selected = settings.fingerBehavior == option,
                        onClick = { scope.launch { settingsStore.setFingerBehavior(option) } },
                    )
                }
            }

            SettingsSection(
                title = "Wygląd",
                description = "Motyw jasny i ciemny są rysowane osobno, a nie odwracane kolorami.",
            ) {
                ThemeChoice.entries.forEach { option ->
                    ChoiceRow(
                        text = option.labelPl,
                        description = null,
                        selected = settings.theme == option,
                        onClick = { scope.launch { settingsStore.setTheme(option) } },
                    )
                }
            }

            SettingsSection(
                title = "Nowa notatka odręczna",
                description = "Te ustawienia podpowiadają się przy tworzeniu notatki. Zawsze możesz je zmienić.",
            ) {
                SectionLabel("Rodzaj strony")
                PageMode.entries.forEach { option ->
                    ChoiceRow(
                        text = option.labelPl,
                        description = null,
                        selected = settings.defaultPageMode == option,
                        onClick = { scope.launch { settingsStore.setDefaultPageMode(option) } },
                    )
                }
                SectionLabel("Tło strony")
                PageBackground.entries.forEach { option ->
                    ChoiceRow(
                        text = option.labelPl,
                        description = null,
                        selected = settings.defaultBackground == option,
                        onClick = { scope.launch { settingsStore.setDefaultBackground(option) } },
                    )
                }
            }

            SettingsSection(
                title = "Zapis automatyczny",
                description = "Notatka zapisuje się sama po każdej zmianie. " +
                    "Nie ma przycisku zapisz i nie musisz o tym myśleć.",
            ) {}
        }
    }
}

@Composable
private fun SettingsSection(title: String, description: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = Kajet.type.title, color = Kajet.colors.text)
        Text(
            text = description,
            style = Kajet.type.body,
            color = Kajet.colors.muted,
            modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
        )
        content()
        HorizontalRule()
    }
}

fun readableFolder(uri: String): String {
    val decoded = Uri.decode(uri)
    val tail = decoded.substringAfterLast("/tree/")
    return tail.replace("primary:", "Pamięć urządzenia / ").ifBlank { decoded }
}
