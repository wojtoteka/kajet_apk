package wojtoteka.ovh.kajet.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.SectionLabel
import wojtoteka.ovh.kajet.core.text.AppLanguage
import wojtoteka.ovh.kajet.core.text.newVersionFound
import wojtoteka.ovh.kajet.core.text.shortenMiddle
import wojtoteka.ovh.kajet.core.text.signedInAs
import wojtoteka.ovh.kajet.core.text.spaceUsedPercent
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.core.design.component.IconAction
import wojtoteka.ovh.kajet.core.design.component.HorizontalRule
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.component.KajetMark
import wojtoteka.ovh.kajet.core.design.component.InlineNotice
import wojtoteka.ovh.kajet.core.design.component.marginRule
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.cloud.AccountStore
import wojtoteka.ovh.kajet.cloud.KajetLinks
import wojtoteka.ovh.kajet.cloud.LinkOutcome
import wojtoteka.ovh.kajet.cloud.SignInState
import wojtoteka.ovh.kajet.cloud.UpdateCheck
import wojtoteka.ovh.kajet.cloud.humanSize
import wojtoteka.ovh.kajet.cloud.openLink
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.ui.library.ChoiceRow
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.storage.ThemeChoice
import wojtoteka.ovh.kajet.storage.ToolbarSide
import wojtoteka.ovh.kajet.storage.FingerBehavior

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    repo: LibraryRepository,
    account: AccountStore,
    onBack: () -> Unit,
    onRebuildIndex: () -> Unit,
    /*
      Co się dzieje z odbudową spisu. Postęp i wynik przychodzą tu z zewnątrz,
      bo robotę prowadzi biblioteka i to ona przeżywa wyjście z ustawień.
      Napis o postępie stoi obok przycisku, a nie na ekranie biblioteki, do
      którego trzeba by wrócić, żeby cokolwiek zobaczyć.
    */
    rebuildProgress: String?,
    rebuildDone: Boolean,
    rebuildProblem: String?,
    onRebuildNoticeRead: () -> Unit,
    onAccount: () -> Unit,
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(KajetSettings())
    val signIn by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val words = LocalStrings.current
    val context = LocalContext.current

    /*
      Czemu dokument się nie otworzył. Zdanie stoi przy przyciskach, a nie w
      dymku: człowiek patrzy właśnie tutaj i tutaj ma przeczytać, że zabrakło
      internetu. Znika przy następnej udanej próbie.
    */
    var linkProblem by remember { mutableStateOf<String?>(null) }

    /*
      Wejście na ekran zaczyna z czystym kontem. Bez tego napis „Spis notatek
      odbudowany" z poprzedniej wizyty witałby tu przy każdym następnym
      wejściu, jakby odbudowa właśnie się wydarzyła.
    */
    LaunchedEffect(Unit) { onRebuildNoticeRead() }

    /*
      Nowa wersja w sekcji „O aplikacji".

      [updateFound] trzyma wydanie do pobrania i stoi na ekranie tak długo, jak
      długo ono na serwerze jest — nie trzeba w tym celu niczego naciskać.
      Sprawdzenie idzie samo przy wejściu w ustawienia i zwykle nie rusza
      nawet sieci: wynik z ostatniej godziny leży w pamięci ([UpdateCheck]).

      Wcześniej o nowym wydaniu mówił wyłącznie komunikat przy uruchomieniu,
      raz na zimny start. Kto go zamknął, nie miał go już gdzie odszukać —
      ustawienia milczały, dopóki nie nacisnęło się „Sprawdź aktualizacje".

      [updateAnswer] zostaje na odpowiedzi, dla których nie ma paska: „masz
      najnowszą" i „nie udało się zapytać".
    */
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateAnswer by remember { mutableStateOf<String?>(null) }
    var updateFound by remember { mutableStateOf<UpdateCheck.Release?>(null) }

    LaunchedEffect(Unit) {
        // W środku siedzi withContext(Dispatchers.IO) i runCatching na wszystkim,
        // więc ani to nie blokuje rysowania, ani nie ma jak stąd wylecieć wyjątek.
        val outcome = UpdateCheck.check(context)
        if (outcome is UpdateCheck.Outcome.Newer) updateFound = outcome.release
    }

    fun openDocument(url: String) {
        linkProblem = when (openLink(context, url)) {
            LinkOutcome.OPENED -> null
            LinkOutcome.NO_NETWORK -> words.documentNoNetwork
            LinkOutcome.NO_BROWSER -> words.documentNoBrowser
        }
    }
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
            IconAction(KajetIcons.BackArrow, words.backToLibrary, onBack)
        }

        Column(
            Modifier
                // weight, nie fillMaxSize: kolumna ma dostać dokładnie to, co
                // zostaje obok szyny - ten sam wzorzec co w bibliotece.
                .fillMaxHeight()
                .weight(1f)
                .background(Kajet.colors.sheet)
                .verticalScroll(rememberScrollState())
                .padding(start = sheetPadding, end = sheetPadding, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(words.settings, style = Kajet.type.display, color = Kajet.colors.text)

            SettingsSection(
                title = words.cloudAccount,
                description = when (signIn) {
                    is SignInState.SignedIn ->
                        words.cloudAccountOn

                    is SignInState.SignedOut,
                    is SignInState.SessionExpired,
                    ->
                        words.cloudAccountOff
                },
            ) {
                // Stan zalogowania widać od razu, bez wchodzenia w ekran konta.
                when (val current = signIn) {
                    is SignInState.SignedIn -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                KajetIcons.CloudMark,
                                contentDescription = null,
                                tint = Kajet.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                // Środek, nie koniec: przy adresie e-mail to
                                // końcówka mówi, czyje to konto.
                                text = words.signedInAs(
                                    shortenMiddle(current.login.ifBlank { current.email }, 48),
                                ),
                                style = Kajet.type.body,
                                color = Kajet.colors.text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (current.login.isNotBlank() && current.email.isNotBlank()) {
                            Text(
                                text = current.email,
                                style = Kajet.type.meta,
                                color = Kajet.colors.muted,
                                modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                            )
                        }
                        if (!current.unlimited) {
                            Text(
                                text = words.spaceUsedPercent(
                                    humanSize(current.usedBytes),
                                    humanSize(current.quotaBytes),
                                    current.usedPercent,
                                ),
                                style = Kajet.type.meta,
                                color = Kajet.colors.muted,
                            )
                        }
                        SecondaryButton(
                            text = words.account,
                            onClick = onAccount,
                            icon = KajetIcons.CloudMark,
                        )
                    }

                    is SignInState.SessionExpired -> {
                        // Serwer przestał uznawać token (np. wylogowanie
                        // wszystkich sesji przez stronę). Bez tej gałęzi
                        // Ustawienia dalej pokazywały „zalogowano jako…".
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                KajetIcons.CloudMark,
                                contentDescription = null,
                                tint = Kajet.colors.danger,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = words.sessionExpired,
                                style = Kajet.type.body,
                                color = Kajet.colors.text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SecondaryButton(
                            text = words.signIn,
                            onClick = onAccount,
                            icon = KajetIcons.CloudMark,
                        )
                    }

                    is SignInState.SignedOut -> {
                        Text(
                            text = words.notSignedIn,
                            style = Kajet.type.body,
                            color = Kajet.colors.muted,
                        )
                        SecondaryButton(
                            text = words.signIn,
                            onClick = onAccount,
                            icon = KajetIcons.CloudMark,
                        )
                    }
                }
            }

            SettingsSection(
                title = words.notesFolder,
                description = words.notesFolderAbout,
            ) {
                Text(
                    text = settings.libraryFolder?.let { readableFolder(it) }
                        ?: words.noFolderPicked,
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
                // Zawijanie jak w oknie eksportu: przy dużej czcionce dwa
                // przyciski w sztywnym wierszu ściskały jeden drugiego do zera.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = words.changeFolder,
                        onClick = { folderPicker.launch(null) },
                        icon = KajetIcons.Folder,
                    )
                    SecondaryButton(
                        text = words.rebuildIndex,
                        onClick = onRebuildIndex,
                        icon = KajetIcons.Restore,
                        // Drugie naciśnięcie w trakcie kasowałoby spis w pół
                        // odbudowy i zaczynało wszystko od nowa.
                        enabled = rebuildProgress == null,
                    )
                }
                if (rebuildProgress != null) {
                    // Zdanie przychodzi z biblioteki w całości — patrz
                    // LibraryViewModel.rebuildIndex.
                    InlineNotice(
                        icon = KajetIcons.Restore,
                        text = rebuildProgress,
                        color = Kajet.colors.accent,
                    )
                } else if (rebuildProblem != null) {
                    InlineNotice(
                        icon = KajetIcons.ErrorMark,
                        text = rebuildProblem,
                        color = Kajet.colors.danger,
                    ) {
                        SecondaryButton(words.understood, onRebuildNoticeRead)
                    }
                } else if (rebuildDone) {
                    InlineNotice(
                        icon = KajetIcons.Restore,
                        text = words.indexRebuilt,
                        color = Kajet.colors.accent,
                    ) {
                        SecondaryButton(words.understood, onRebuildNoticeRead)
                    }
                }
                Text(
                    text = words.rebuildIndexAbout,
                    style = Kajet.type.meta,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )
            }

            SettingsSection(
                title = words.stylusAndFinger,
                description = words.stylusAndFingerAbout,
            ) {
                FingerBehavior.entries.forEach { option ->
                    ChoiceRow(
                        text = option.label(words),
                        description = if (option == FingerBehavior.SCROLL) {
                            words.fingerScrollsAbout
                        } else {
                            words.fingerDrawsAbout
                        },
                        selected = settings.fingerBehavior == option,
                        onClick = { scope.launch { settingsStore.setFingerBehavior(option) } },
                    )
                }
            }

            SettingsSection(
                title = words.settingsLanguage,
                description = words.settingsLanguageAbout,
            ) {
                AppLanguage.entries.forEach { option ->
                    ChoiceRow(
                        text = when (option) {
                            AppLanguage.SYSTEM -> words.languageSystem
                            AppLanguage.POLISH -> words.languagePolish
                            AppLanguage.ENGLISH -> words.languageEnglish
                        },
                        description = null,
                        selected = AppLanguage.fromId(settings.language) == option,
                        onClick = { scope.launch { settingsStore.setLanguage(option.id) } },
                    )
                }
            }

            SettingsSection(
                title = words.settingsAppearance,
                description = words.appearanceAbout,
            ) {
                ThemeChoice.entries.forEach { option ->
                    ChoiceRow(
                        text = option.label(words),
                        description = null,
                        selected = settings.theme == option,
                        onClick = { scope.launch { settingsStore.setTheme(option) } },
                    )
                }
            }

            SettingsSection(
                title = words.settingsToolbarSide,
                description = words.settingsToolbarSideAbout,
            ) {
                ChoiceRow(
                    text = words.toolbarLeft,
                    description = null,
                    selected = settings.toolbarSide == ToolbarSide.LEFT,
                    onClick = { scope.launch { settingsStore.setToolbarSide(ToolbarSide.LEFT) } },
                )
                ChoiceRow(
                    text = words.toolbarRight,
                    description = null,
                    selected = settings.toolbarSide == ToolbarSide.RIGHT,
                    onClick = { scope.launch { settingsStore.setToolbarSide(ToolbarSide.RIGHT) } },
                )
            }

            SettingsSection(
                title = words.newHandwrittenNote,
                description = words.newHandwrittenNoteAbout,
            ) {
                SectionLabel(words.pageKind)
                PageMode.entries.forEach { option ->
                    ChoiceRow(
                        text = option.label(words),
                        description = null,
                        selected = settings.defaultPageMode == option,
                        onClick = { scope.launch { settingsStore.setDefaultPageMode(option) } },
                    )
                }
                SectionLabel(words.pageBackgroundLabel)
                PageBackground.entries.forEach { option ->
                    ChoiceRow(
                        text = option.label(words),
                        description = null,
                        selected = settings.defaultBackground == option,
                        onClick = { scope.launch { settingsStore.setDefaultBackground(option) } },
                    )
                }
            }

            SettingsSection(
                title = words.settingsCode,
                description = words.settingsCodeAbout,
            ) {
                ChoiceRow(
                    text = words.codeAssistOn,
                    description = words.codeAssistOnAbout,
                    selected = settings.codeAssist,
                    onClick = { scope.launch { settingsStore.setCodeAssist(true) } },
                )
                ChoiceRow(
                    text = words.codeAssistOff,
                    description = words.codeAssistOffAbout,
                    selected = !settings.codeAssist,
                    onClick = { scope.launch { settingsStore.setCodeAssist(false) } },
                )
            }

            SettingsSection(
                title = words.autosaveSection,
                description = words.autosaveAbout,
            ) {}

            SettingsSection(
                title = words.legalSection,
                description = words.legalSectionAbout,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = words.termsOfService,
                        onClick = { openDocument(KajetLinks.terms()) },
                        icon = KajetIcons.Scales,
                    )
                    SecondaryButton(
                        text = words.privacyPolicy,
                        onClick = { openDocument(KajetLinks.privacy()) },
                        icon = KajetIcons.Shield,
                    )
                }
                linkProblem?.let { problem ->
                    Text(
                        text = problem,
                        style = Kajet.type.meta,
                        color = Kajet.colors.danger,
                        modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                    )
                }
            }

            SettingsSection(
                title = words.aboutSection,
                description = words.aboutSectionAbout,
            ) {
                Text(
                    text = "${words.appVersionWord} ${appVersion(context)}",
                    style = Kajet.type.body,
                    color = Kajet.colors.muted,
                    modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                )

                // Pasek stoi tu sam z siebie, dopóki nowsze wydanie czeka na
                // serwerze — z numerem wersji i drogą do pobrania pod ręką.
                updateFound?.let { release ->
                    InlineNotice(
                        icon = KajetIcons.Export,
                        text = words.newVersionFound(release.version),
                        color = Kajet.colors.accent,
                    ) {
                        SecondaryButton(
                            text = words.updateInstall,
                            onClick = {
                                openDocument(release.pageUrl.ifBlank { KajetLinks.download() })
                            },
                        )
                    }
                }

                /*
                  Sprawdzenie z ręki, w odróżnieniu od tego przy uruchomieniu,
                  MÓWI o niepowodzeniu. Tam brak sieci znaczy „nie zawracamy
                  głowy", tutaj człowiek sam o to poprosił i czekanie
                  w nieskończoność na nic byłoby zwykłym zbyciem.
                */
                SecondaryButton(
                    text = if (checkingUpdates) words.checkingUpdates else words.checkUpdates,
                    onClick = {
                        if (checkingUpdates) return@SecondaryButton
                        checkingUpdates = true
                        updateAnswer = null
                        scope.launch {
                            when (val outcome = UpdateCheck.check(context, force = true)) {
                                is UpdateCheck.Outcome.Newer -> {
                                    updateFound = outcome.release
                                    updateAnswer = null
                                }

                                UpdateCheck.Outcome.UpToDate -> {
                                    // Wydanie zeszło z serwera albo właśnie
                                    // je zainstalowano — pasek nie ma już
                                    // czego zapowiadać.
                                    updateFound = null
                                    updateAnswer = words.upToDate
                                }

                                // Nieudane pytanie nie zmienia tego, co już
                                // wiadomo: pasek z poprzedniej odpowiedzi
                                // zostaje, bo wydanie nie zniknęło.
                                UpdateCheck.Outcome.Unknown ->
                                    updateAnswer = words.updateCheckFailed
                            }
                            checkingUpdates = false
                        }
                    },
                    icon = KajetIcons.CloudMark,
                    enabled = !checkingUpdates,
                )

                updateAnswer?.let { answer ->
                    Text(
                        text = answer,
                        style = Kajet.type.meta,
                        color = if (answer == words.updateCheckFailed) {
                            Kajet.colors.danger
                        } else {
                            Kajet.colors.muted
                        },
                        modifier = Modifier.widthIn(max = Kajet.dimens.readingWidth),
                    )
                }
            }
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

/**
 * Wersja aplikacji: nazwa i numer wydania w nawiasie, na przykład „1.0 (2)".
 *
 * Numer w nawiasie bierze się stąd, że nazwa wersji bywa ta sama w kilku
 * kolejnych plikach .apk - a przy zgłaszaniu usterki liczy się to, KTÓRE
 * dokładnie wydanie stoi na urządzeniu.
 */
private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName ?: "?"} (${info.longVersionCode})"
}.getOrDefault("?")

fun readableFolder(uri: String): String {
    val decoded = Uri.decode(uri)
    val tail = decoded.substringAfterLast("/tree/")
    // Ukośnik doklejamy tutaj, a nie w słowniku: w napisie do tłumaczenia
    // wyglądał jak literówka i ginie przy pierwszej poprawce.
    return tail.replace("primary:", words.deviceStorage + " / ").ifBlank { decoded }
}
