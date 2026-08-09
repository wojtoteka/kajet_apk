package wojtoteka.ovh.kajet.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.text.LocalStrings
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
import wojtoteka.ovh.kajet.ui.settings.SettingsScreen
import wojtoteka.ovh.kajet.storage.KajetSettings

object Routes {
    const val START = "start"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"

    const val NOTE = "note/{path}"
    const val CODE = "code/{path}"

    fun note(path: String) = "note/" + Uri.encode(path)
    fun code(path: String) = "code/" + Uri.encode(path)
}

@Composable
fun KajetNavigation(container: AppContainer, settings: KajetSettings) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val authUri by DeviceAuthBridge.pending.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()

    val model: LibraryViewModel = viewModel(
        // Chmura wchodzi tu furtką, a nie gotowym obiektem: jej budowa to I/O
        // na dysku, a model powstaje przy pierwszym rysowaniu biblioteki.
        factory = LibraryViewModel.Factory(container.library, container.export) {
            CloudStuckNotes(container.cloud.sync, container.cloud.queue)
        },
    )

    /*
      Ekran startowy ustalamy RAZ, przy pierwszym złożeniu.

      Wcześniej liczyło się to przy każdym przerysowaniu, z bieżących ustawień.
      Zmiana wartości każe navigation-compose zbudować graf od nowa i wstawić go
      do kontrolera — a wtedy dotychczasowy stos ekranów przestaje do niego
      pasować i potrafi zostać pusty. Pusty stos to nic do narysowania, czyli
      samo tło biurka: dokładnie ten „czarny ekran", z którego wychodziło się
      tylko ubiciem aplikacji. Po wskazaniu katalogu i tak przechodzimy do
      biblioteki wprost (niżej), więc przeliczanie tego jest niepotrzebne.
    */
    val start = rememberSaveable {
        if (settings.libraryFolder.isNullOrBlank()) Routes.START else Routes.LIBRARY
    }

    /*
      Bezpiecznik na wypadek, gdyby stos ekranów mimo wszystko został pusty —
      po synchronizacji, po powrocie z notatki skasowanej gdzie indziej albo po
      zmianie rozmiaru okna w trybie pulpitu. Zamiast gołego tła wracamy do
      biblioteki. Chwila zwłoki, bo przy pierwszym złożeniu stos jest pusty
      z natury i to jest w porządku.
    */
    LaunchedEffect(backStack) {
        if (backStack != null) return@LaunchedEffect
        delay(600)
        if (navController.currentBackStackEntry != null) return@LaunchedEffect
        Log.w("Kajet", "Stos ekranów został pusty — wracam na $start")
        runCatching { navController.navigate(start) { launchSingleTop = true } }
    }

    // Deep link from the website after device approval → open account screen.
    LaunchedEffect(authUri, backStack?.destination?.route) {
        if (authUri == null) return@LaunchedEffect
        if (settings.libraryFolder.isNullOrBlank()) return@LaunchedEffect
        if (backStack?.destination?.route == Routes.ACCOUNT) return@LaunchedEffect
        navController.navigate(Routes.ACCOUNT) {
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = start) {

        composable(Routes.START) {
            FolderPickerScreen(
                onPicked = { uri ->
                    scope.launch {
                        container.library.setLibraryFolder(uri)
                        model.rebuildIndex()
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.START) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                model = model,
                repo = container.library,
                defaultMode = settings.defaultPageMode,
                defaultBackground = settings.defaultBackground,
                onOpenItem = { item -> open(navController, item) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
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

        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                settingsStore = container.settings,
                repo = container.library,
                account = container.cloud.account,
                onBack = { popOnce(navController, entry) },
                onRebuildIndex = { model.rebuildIndex() },
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

/*
  Powrót zdejmuje TYLKO ekran, który wciąż jest na wierzchu.

  Dwa szybkie stuknięcia „wstecz" wołały popBackStack dwa razy: drugie
  zdejmowało ze stosu także bibliotekę i zostawał pusty stos — czyli samo tło
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
        ItemType.CODE_FILE, ItemType.OTHER_FILE -> navController.navigate(Routes.code(item.path))
        ItemType.FOLDER -> Unit
    }
}
