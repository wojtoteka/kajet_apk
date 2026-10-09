package wojtoteka.ovh.kajet.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.AppContainer
import wojtoteka.ovh.kajet.code.CodeEditor
import wojtoteka.ovh.kajet.code.CodeViewModel
import wojtoteka.ovh.kajet.core.design.Kajet
import wojtoteka.ovh.kajet.core.design.component.CalculatorControl
import wojtoteka.ovh.kajet.core.design.component.CalculatorPanel
import wojtoteka.ovh.kajet.core.design.component.LocalCalculator
import wojtoteka.ovh.kajet.core.design.component.NoticeBar
import wojtoteka.ovh.kajet.core.design.component.SecondaryButton
import wojtoteka.ovh.kajet.core.design.icon.KajetIcons
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.OtherFileKind
import wojtoteka.ovh.kajet.core.text.LocalStrings
import wojtoteka.ovh.kajet.ui.file.FileViewer
import wojtoteka.ovh.kajet.ui.library.CloudStuckNotes
import wojtoteka.ovh.kajet.ui.library.LibraryScreen
import wojtoteka.ovh.kajet.ui.library.LibraryViewModel
import wojtoteka.ovh.kajet.ui.note.CodeAiOverlay
import wojtoteka.ovh.kajet.ui.note.NoteScreen
import wojtoteka.ovh.kajet.ui.note.remoteDeletionGuard
import wojtoteka.ovh.kajet.ui.start.FolderPickerScreen
import wojtoteka.ovh.kajet.cloud.AccountScreen
import wojtoteka.ovh.kajet.cloud.AccountViewModel
import wojtoteka.ovh.kajet.cloud.DeviceAuthBridge
import wojtoteka.ovh.kajet.cloud.SharedLinkBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.ui.settings.SettingsScreen
import wojtoteka.ovh.kajet.storage.KajetSettings
import wojtoteka.ovh.kajet.share.ShareIncoming

object Routes {
    const val START = "start"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"

    const val NOTE = "note/{path}"
    const val CODE = "code/{path}"
    const val VIEW = "view/{path}"
    const val SHARED = "shared/{token}?folder={folder}"

    fun note(path: String) = "note/" + Uri.encode(path)
    fun shared(token: String, folder: String? = null) =
        "shared/" + Uri.encode(token) + (folder?.let { "?folder=" + Uri.encode(it) } ?: "")
    fun code(path: String) = "code/" + Uri.encode(path)
    fun view(path: String) = "view/" + Uri.encode(path)
}

@Composable
fun KajetNavigation(container: AppContainer, settings: KajetSettings) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authUri by DeviceAuthBridge.pending.collectAsStateWithLifecycle()
    val incomingShare by ShareIncoming.pending.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()

    val model: LibraryViewModel = viewModel(
        // Chmura wchodzi tu furtką, a nie gotowym obiektem: jej budowa to I/O
        // na dysku, a model powstaje przy pierwszym rysowaniu biblioteki.
        factory = LibraryViewModel.Factory(
            repo = container.library,
            export = container.export,
            stuck = { CloudStuckNotes(container.cloud.sync, container.cloud.queue) },
            uploader = { container.cloud.uploads },
        ),
    )
    val libraryError by model.error.collectAsStateWithLifecycle()
    val words = LocalStrings.current
    val route = backStack?.destination?.route

    /*
      Ekran startowy ustalamy RAZ, przy pierwszym złożeniu.

      Wcześniej liczyło się to przy każdym przerysowaniu, z bieżących ustawień.
      Zmiana wartości każe navigation-compose zbudować graf od nowa i wstawić go
      do kontrolera - a wtedy dotychczasowy stos ekranów przestaje do niego
      pasować i potrafi zostać pusty. Pusty stos to nic do narysowania, czyli
      samo tło biurka: dokładnie ten „czarny ekran", z którego wychodziło się
      tylko ubiciem aplikacji. Po wskazaniu katalogu i tak przechodzimy do
      biblioteki wprost (niżej), więc przeliczanie tego jest niepotrzebne.
    */
    val start = rememberSaveable {
        // Nie wystarczy niepusty adres: po Auto Backup wraca bez uprawnienia SAF.
        if (container.library.hasPersistedAccess(settings.libraryFolder)) {
            Routes.LIBRARY
        } else {
            Routes.START
        }
    }

    /*
      Bezpiecznik na wypadek, gdyby stos ekranów mimo wszystko został pusty -
      po synchronizacji, po powrocie z notatki skasowanej gdzie indziej albo po
      zmianie rozmiaru okna w trybie pulpitu. Zamiast gołego tła wracamy do
      biblioteki. Chwila zwłoki, bo przy pierwszym złożeniu stos jest pusty
      z natury i to jest w porządku.
    */
    LaunchedEffect(backStack) {
        if (backStack != null) return@LaunchedEffect
        delay(600)
        if (navController.currentBackStackEntry != null) return@LaunchedEffect
        Log.w("Kajet", "Stos ekranów został pusty - wracam na $start")
        runCatching { navController.navigate(start) { launchSingleTop = true } }
    }

    // Deep link from the website after device approval → open account screen.
    LaunchedEffect(authUri, backStack?.destination?.route) {
        if (authUri == null) return@LaunchedEffect
        if (!container.library.hasPersistedAccess(settings.libraryFolder)) return@LaunchedEffect
        if (backStack?.destination?.route == Routes.ACCOUNT) return@LaunchedEffect
        navController.navigate(Routes.ACCOUNT) {
            launchSingleTop = true
        }
    }

    // Udostępnij / Otwórz w: bez katalogu notatek najpierw wybór folderu,
    // potem kopia do biblioteki i ten sam open() co przy stuknięciu w spisie.
    // Pending kasujemy dopiero po udanej kopii - nieudane wzięcie nie zjada
    // intencji, więc da się spróbować jeszcze raz, póki system trzyma URI.
    LaunchedEffect(incomingShare, settings.libraryFolder, route) {
        val share = incomingShare ?: return@LaunchedEffect
        if (!container.library.hasPersistedAccess(settings.libraryFolder)) return@LaunchedEffect
        if (route == null || route == Routes.START) return@LaunchedEffect
        val taken = ShareIncoming.tryBegin() ?: return@LaunchedEffect
        model.importIncoming(
            share = taken,
            context = context,
            onImported = { item ->
                ShareIncoming.finish(success = true)
                open(navController, item)
            },
            onFailed = { ShareIncoming.finish(success = false) },
        )
    }

    /*
      Cudze notatki i foldery: lista „Udostępnione mi" w bibliotece
      i odnośniki /n/<token> otwierane z zewnątrz. Chmura powstaje leniwie
      (I/O na dysku), więc sięgamy po nią poza wątkiem głównym.
    */
    var shared by remember { mutableStateOf<wojtoteka.ovh.kajet.cloud.SharedLibrary?>(null) }
    LaunchedEffect(Unit) {
        shared = withContext(Dispatchers.Default) { runCatching { container.cloud.shared }.getOrNull() }
    }
    val sharedItems by (shared?.items ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        .collectAsStateWithLifecycle()
    LaunchedEffect(shared, route) {
        if (route == Routes.LIBRARY) runCatching { shared?.refresh() }
    }

    val pendingLink by SharedLinkBridge.pending.collectAsStateWithLifecycle()
    var opening by remember { mutableStateOf(false) }
    var openProblem by remember { mutableStateOf<wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Failed?>(null) }

    fun openWeb(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun showOpened(result: wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened) {
        when (result) {
            is wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Note ->
                navController.navigate(Routes.note(result.path))
            is wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Folder ->
                navController.navigate(Routes.shared(result.item.token))
            is wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Web -> openWeb(result.url)
            is wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Failed -> openProblem = result
        }
    }

    /*
      Otwieranie idzie w zasięgu całej nawigacji, a nie w LaunchedEffect niżej:
      efekt zaczyna się od zdjęcia odnośnika z kolejki, co zmienia jego klucz -
      Compose przerwałby wtedy w pół otwieranie, które dopiero co ruszyło.
    */
    fun launchOpen(work: suspend () -> wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened) {
        opening = true
        scope.launch {
            val result = try {
                work()
            } catch (e: kotlinx.coroutines.CancellationException) {
                opening = false
                throw e
            } catch (e: Exception) {
                wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Failed(words.sharedOpenFailed)
            }
            opening = false
            showOpened(result)
        }
    }

    LaunchedEffect(pendingLink, shared, route) {
        val link = pendingLink ?: return@LaunchedEffect
        val library = shared ?: return@LaunchedEffect
        if (route == null) return@LaunchedEffect
        SharedLinkBridge.clear()
        launchOpen {
            val opened = library.open(link.token)
            // Odnośnik do folderu wskazał od razu notatkę w nim (?note=...).
            val inFolder = link.noteId
            if (opened is wojtoteka.ovh.kajet.cloud.SharedLibrary.Opened.Folder && inFolder != null) {
                library.openNote(
                    link.token,
                    wojtoteka.ovh.kajet.cloud.SharedNoteEntry(id = inFolder),
                    opened.item,
                )
            } else {
                opened
            }
        }
    }

    fun openSharedItem(item: wojtoteka.ovh.kajet.cloud.SharedItem) {
        val library = shared ?: return
        val note = item.note
        if (item.folder != null) {
            navController.navigate(Routes.shared(item.token))
            return
        }
        if (note == null) return
        launchOpen { library.openNote(item.token, note, item) }
    }

    /*
      Kalkulator stoi nad całym stosem ekranów, a nie w każdym edytorze osobno:
      jeden stan dla notatki odręcznej, tekstowej, mapy myśli i kodu, a edytor
      stawia tylko przycisk (CalculatorAction). Poza edytorami się chowa -
      w bibliotece czy ustawieniach nie ma czego liczyć.
    */
    var calculatorOpen by rememberSaveable { mutableStateOf(false) }
    val calculator = remember(calculatorOpen) {
        CalculatorControl(calculatorOpen) { calculatorOpen = !calculatorOpen }
    }
    LaunchedEffect(route) {
        if (route != Routes.NOTE && route != Routes.CODE && route != Routes.VIEW) calculatorOpen = false
    }

    CompositionLocalProvider(LocalCalculator provides calculator) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        /*
          Pasek z biblioteki nie widać na notatce, kodzie ani podglądzie.
          Ten sam komunikat stoi więc tu, nad całym stosem - bez osobnego
          systemu snackbarów. W bibliotece i ustawieniach już jest swoje miejsce.
        */
        val showAwayFromLibrary = libraryError != null &&
            route != null &&
            route != Routes.LIBRARY &&
            route != Routes.SETTINGS &&
            route != Routes.START
        if (showAwayFromLibrary) {
            NoticeBar(
                icon = KajetIcons.ErrorMark,
                text = libraryError.orEmpty(),
                color = Kajet.colors.danger,
            ) {
                SecondaryButton(words.understood, model::dismissError)
            }
        }

        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.weight(1f),
        ) {

        composable(Routes.START) {
            var pickProblem by remember { mutableStateOf<String?>(null) }
            FolderPickerScreen(
                lostGrant = !settings.libraryFolder.isNullOrBlank(),
                pickProblem = pickProblem,
                onPicked = { uri ->
                    scope.launch {
                        pickProblem = null
                        val kept = runCatching { container.library.setLibraryFolder(uri) }
                        if (kept.isFailure) {
                            pickProblem = kept.exceptionOrNull()?.message
                                ?: words.couldNotKeepFolderAccess
                            return@launch
                        }
                        model.rebuildIndex()
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.START) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.LIBRARY) {
            var sharingFolder by remember { mutableStateOf<wojtoteka.ovh.kajet.ui.note.ShareTarget.Folder?>(null) }
            var folderProblem by remember { mutableStateOf<String?>(null) }
            LibraryScreen(
                model = model,
                repo = container.library,
                defaultMode = settings.defaultPageMode,
                defaultBackground = settings.defaultBackground,
                onOpenItem = { item -> open(navController, item) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                sharedItems = sharedItems,
                onOpenShared = ::openSharedItem,
                onLeaveShared = { item -> scope.launch { shared?.leave(item) } },
                onShareFolder = { item ->
                    scope.launch {
                        // Folder dla serwera to jego identyfikator z chmury - jest
                        // dopiero po synchronizacji.
                        val id = withContext(Dispatchers.IO) {
                            runCatching { container.library.allCloudFolders() }.getOrDefault(emptyList())
                                .firstOrNull { it.path == item.path }?.id
                        }
                        val signedIn = runCatching { container.cloud.account.isSignedIn() }.getOrDefault(false)
                        if (id == null || !signedIn) {
                            folderProblem = words.shareFolderNeedsSync
                        } else {
                            sharingFolder = wojtoteka.ovh.kajet.ui.note.ShareTarget.Folder(id, item.name)
                        }
                    }
                },
            )
            sharingFolder?.let { target ->
                wojtoteka.ovh.kajet.ui.note.ShareDialog(
                    target = target,
                    cloud = container.cloud,
                    service = container.export,
                    onClose = { sharingFolder = null },
                )
            }
            folderProblem?.let { message ->
                wojtoteka.ovh.kajet.ui.library.KajetDialog(words.shareFolderTitle, onClose = { folderProblem = null }) {
                    Text(message, style = Kajet.type.body, color = Kajet.colors.text)
                }
            }
        }

        composable(
            route = Routes.SHARED,
            arguments = listOf(
                navArgument("token") { type = NavType.StringType },
                navArgument("folder") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val token = Uri.decode(entry.arguments?.getString("token").orEmpty())
            val folder = entry.arguments?.getString("folder")?.let(Uri::decode)
            val library = shared
            if (library != null) {
                wojtoteka.ovh.kajet.ui.shared.SharedFolderScreen(
                    shared = library,
                    token = token,
                    folderId = folder,
                    onOpenNote = { navController.navigate(Routes.note(it)) },
                    onOpenFolder = { navController.navigate(Routes.shared(token, it)) },
                    onOpenWeb = ::openWeb,
                    onBack = { popOnce(navController, entry) },
                )
            }
        }

        composable(
            route = Routes.NOTE,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Uri.decode(entry.arguments?.getString("path").orEmpty())
            NoteScreen(
                path = path,
                repo = container.library,
                settings = container.settings,
                loaded = settings,
                export = container.export,
                onBack = {
                    model.refreshAfterChange()
                    popOnce(navController, entry)
                },
            )
        }

        composable(
            route = Routes.CODE,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Uri.decode(entry.arguments?.getString("path").orEmpty())
            val codeModel: CodeViewModel = viewModel(
                key = "code-$path",
                factory = CodeViewModel.Factory(
                    repo = container.library,
                    settings = container.settings,
                    registry = container.runners,
                    path = path,
                    // Na telefonie wiersz kodu i tak się nie mieści, więc od
                    // początku go zawijamy. Przycisk w pasku dalej rządzi.
                    wrapByDefault = LocalConfiguration.current.screenWidthDp < 600,
                    assistEnabled = settings.codeAssist,
                ),
            )

            // Ta sama osłona co przy notatkach: plik skasowany w trakcie
            // pisania na innym urządzeniu pyta o los niezapisanej treści.
            val words = LocalStrings.current
            val deleted by codeModel.remotelyDeleted.collectAsStateWithLifecycle()
            val saved by codeModel.saved.collectAsStateWithLifecycle()
            val guardedBack = remoteDeletionGuard(
                deleted = deleted,
                unsaved = !saved,
                message = words.fileDeletedElsewhereAbout,
                saveLabel = words.saveAsNewFile,
                onSaveAsNew = codeModel::saveAsNewFile,
                onDiscard = codeModel::discardChanges,
                onBack = {
                    model.refreshAfterChange()
                    popOnce(navController, entry)
                },
            )

            val assistant = container.ai.takeIf { it.available() }
            var aiOpen by remember(codeModel.path) { mutableStateOf(false) }

            CodeEditor(
                model = codeModel,
                onBack = guardedBack,
                onAi = if (assistant != null) ({ aiOpen = true }) else null,
            )
            if (assistant != null) {
                CodeAiOverlay(
                    assistant = assistant,
                    open = aiOpen,
                    path = codeModel.path,
                    hooks = codeModel,
                    onClose = { aiOpen = false },
                )
            }
        }

        composable(
            route = Routes.VIEW,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Uri.decode(entry.arguments?.getString("path").orEmpty())
            FileViewer(
                path = path,
                repo = container.library,
                onBack = { popOnce(navController, entry) },
            )
        }

        composable(Routes.SETTINGS) { entry ->
            // Odbudowa spisu idzie w bibliotece i tam żyje jej stan. Ustawienia
            // dostają go tutaj, żeby przycisk miał czym odpowiedzieć.
            val rebuildProgress by model.progress.collectAsStateWithLifecycle()
            val rebuildDone by model.indexRebuilt.collectAsStateWithLifecycle()
            val rebuildProblem by model.error.collectAsStateWithLifecycle()

            SettingsScreen(
                settingsStore = container.settings,
                repo = container.library,
                account = container.cloud.account,
                onBack = { popOnce(navController, entry) },
                onRebuildIndex = { model.rebuildIndex() },
                rebuildProgress = rebuildProgress,
                rebuildDone = rebuildDone,
                rebuildProblem = rebuildProblem,
                onRebuildNoticeRead = {
                    model.dismissIndexRebuilt()
                    model.dismissError()
                },
                onAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }

        composable(Routes.ACCOUNT) { entry ->
            val context = LocalContext.current
            val accountModel: AccountViewModel = viewModel(
                factory = AccountViewModel.Factory(
                    context = context,
                    account = container.cloud.account,
                    client = container.cloud.client,
                    sync = container.cloud.sync,
                    queue = container.cloud.queue,
                ),
            )
            AccountScreen(
                model = accountModel,
                onBack = {
                    model.refreshAfterChange()
                    popOnce(navController, entry)
                },
            )
        }
    }
    }

    if (calculatorOpen) CalculatorPanel(onClose = { calculatorOpen = false })

    if (opening) {
        wojtoteka.ovh.kajet.ui.library.KajetDialog(words.sharedSection, onClose = {}) {
            Text(words.sharedOpening, style = Kajet.type.body, color = Kajet.colors.muted)
        }
    }
    openProblem?.let { problem ->
        wojtoteka.ovh.kajet.ui.library.KajetDialog(words.sharedOpenFailed, onClose = { openProblem = null }) {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(problem.message, style = Kajet.type.body, color = Kajet.colors.text)
                if (problem.mustSignIn) {
                    SecondaryButton(words.account, {
                        openProblem = null
                        navController.navigate(Routes.ACCOUNT) { launchSingleTop = true }
                    })
                }
            }
        }
    }
    }
    }

    /*
      Po kopii zapasowej albo reinstalacji adres folderu potrafi wrócić bez
      trwałego uprawnienia SAF. Wtedy biblioteka wygląda na pustą. Zanim
      cokolwiek pokażemy ze spisu, wracamy do wyboru folderu.
    */
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, settings.libraryFolder, route) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (container.library.hasPersistedAccess(settings.libraryFolder)) return@repeatOnLifecycle
            if (route == null || route == Routes.START) return@repeatOnLifecycle
            Log.w("Kajet", "Brak trwałego dostępu do folderu - wracam do wyboru katalogu")
            runCatching {
                navController.navigate(Routes.START) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}

/*
  Powrót zdejmuje TYLKO ekran, który wciąż jest na wierzchu.

  Dwa szybkie stuknięcia „wstecz" wołały popBackStack dwa razy: drugie
  zdejmowało ze stosu także bibliotekę i zostawał pusty stos - czyli samo tło
  biurka, w ciemnym motywie praktycznie czarne, wyglądające jak zawieszona
  aplikacja. Po pierwszym zdjęciu ekran przestaje być RESUMED, więc spóźnione
  stuknięcie nie robi już nic. Bezpiecznik na pusty stos wyżej zostaje jako
  druga linia obrony.
*/
private fun popOnce(navController: NavHostController, entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        navController.popBackStack()
    }
}

private fun open(navController: NavHostController, item: LibraryItem) {
    when (item.type) {
        ItemType.NOTE -> navController.navigate(Routes.note(item.path))
        ItemType.CODE_FILE -> navController.navigate(Routes.code(item.path))
        ItemType.OTHER_FILE -> when (OtherFileKind.of(item.name)) {
            OtherFileKind.TEXT -> navController.navigate(Routes.code(item.path))
            OtherFileKind.IMAGE, OtherFileKind.PDF, OtherFileKind.BINARY ->
                navController.navigate(Routes.view(item.path))
        }
        ItemType.FOLDER -> Unit
    }
}
