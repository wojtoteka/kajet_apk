package wojtoteka.ovh.kajet

import android.app.Application
import android.content.Context
import wojtoteka.ovh.kajet.cloud.Cloud
import wojtoteka.ovh.kajet.cloud.CloudCode
import wojtoteka.ovh.kajet.code.RunnerRegistry
import wojtoteka.ovh.kajet.runner.KajetServerRunner
import wojtoteka.ovh.kajet.runner.TabletPythonRunner
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.storage.Storage
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

class AppContainer(context: Context) {

    val settings: SettingsStore = Storage.settings(context)

    val library: LibraryRepository = Storage.library(context, settings)

    val export: ExportService = ExportService(context.applicationContext, library)

    val cloud: Cloud.Parts = Cloud.parts(context, library)

    val runners: RunnerRegistry = RunnerRegistry(
        listOf(
            TabletPythonRunner(context),
            KajetServerRunner(CloudCode(cloud.account, cloud.client)),
        ),
    )

    init {
        // The repository only reports that something was saved; the container decides
        // that it goes to the cloud.
        library.onNoteSaved = { path, id ->
            cloud.sync.reportChange(path, id)
        }
    }
}

class KajetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as KajetApp).container
