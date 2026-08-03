package wojtoteka.ovh.kajet.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import wojtoteka.ovh.kajet.AppContainer
import wojtoteka.ovh.kajet.code.CodeEditor
import wojtoteka.ovh.kajet.code.CodeViewModel
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.ui.library.LibraryScreen
import wojtoteka.ovh.kajet.ui.library.LibraryViewModel
import wojtoteka.ovh.kajet.ui.note.NoteScreen
import wojtoteka.ovh.kajet.ui.start.FolderPickerScreen
import wojtoteka.ovh.kajet.cloud.AccountScreen
import wojtoteka.ovh.kajet.cloud.AccountViewModel
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

    val model: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(container.library, container.export),
    )

    val start = if (settings.libraryFolder.isNullOrBlank()) Routes.START else Routes.LIBRARY

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
                    navController.popBackStack()
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
            CodeEditor(
                model = codeModel,
                onBack = {
                    model.refreshAfterChange()
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsStore = container.settings,
                repo = container.library,
                onBack = { navController.popBackStack() },
                onRebuildIndex = { model.rebuildIndex() },
                onAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }

        composable(Routes.ACCOUNT) {
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
                    navController.popBackStack()
                },
            )
        }
    }
}

private fun open(navController: NavHostController, item: LibraryItem) {
    when (item.type) {
        ItemType.NOTE -> navController.navigate(Routes.note(item.path))
        ItemType.CODE_FILE, ItemType.OTHER_FILE -> navController.navigate(Routes.code(item.path))
        ItemType.FOLDER -> Unit
    }
}
