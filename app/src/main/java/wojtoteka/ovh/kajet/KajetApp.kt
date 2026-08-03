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

    // Tablet: Python lokalnie (Chaquopy), chyba że jest konto i sieć — wtedy CloudCode.
    // Telefon: bez Chaquopy, zawsze CloudCode → POST /api/v1/code.
    val runners: RunnerRegistry = run {
        val cloudCode = CloudCode(cloud.account, cloud.client)
        RunnerRegistry(
            runners = buildList {
                if (context.isKajetTablet()) {
                    add(TabletPythonRunner(context))
                }
                add(KajetServerRunner(cloudCode))
            },
            preferServer = {
                cloud.account.isSignedIn() && cloud.client.hasNetwork()
            },
        )
    }

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

/** Tablety (sw ≥ 600 dp) mogą liczyć Pythona lokalnie; telefony idą przez serwer. */
fun Context.isKajetTablet(): Boolean =
    resources.configuration.smallestScreenWidthDp >= 600
