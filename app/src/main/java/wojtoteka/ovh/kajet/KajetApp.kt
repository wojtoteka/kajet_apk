package wojtoteka.ovh.kajet

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import wojtoteka.ovh.kajet.awaria.CrashLog
import wojtoteka.ovh.kajet.awaria.CrashUpload
import wojtoteka.ovh.kajet.awaria.ErrorActivity
import wojtoteka.ovh.kajet.cloud.Cloud
import wojtoteka.ovh.kajet.asystent.KajetAi
import wojtoteka.ovh.kajet.cloud.CloudAi
import wojtoteka.ovh.kajet.cloud.CloudCode
import wojtoteka.ovh.kajet.core.ai.AiAssistant
import wojtoteka.ovh.kajet.code.RunnerRegistry
import wojtoteka.ovh.kajet.runner.KajetServerRunner
import wojtoteka.ovh.kajet.runner.TabletPythonRunner
import wojtoteka.ovh.kajet.export.ExportService
import wojtoteka.ovh.kajet.storage.Housekeeping
import wojtoteka.ovh.kajet.storage.Storage
import wojtoteka.ovh.kajet.storage.SettingsStore
import wojtoteka.ovh.kajet.storage.LibraryRepository

class AppContainer(context: Context) {

    private val app: Context = context.applicationContext

    val settings: SettingsStore = Storage.settings(context)

    val library: LibraryRepository = Storage.library(context, settings)

    val export: ExportService = ExportService(app, library)

    /** Sprzątanie po kasowaniu: zaległości, sieroty, stare pliki podręczne. */
    val housekeeping: Housekeeping = Storage.housekeeping(app, library)

    /*
      Leniwie, bo budowa chmury to magazyn kluczy i EncryptedSharedPreferences
      (I/O na dysku) plus plan WorkManagera — wszystko działo się na wątku
      głównym w Application.onCreate i wydłużało start. Teraz pierwszy dostęp
      robi wątek rozgrzewki (patrz KajetApp.onCreate), a nie rysowanie ekranu.
    */
    val cloud: Cloud.Parts by lazy { Cloud.parts(app, library) }

    /*
      Asystent KajetAI. Leniwie, bo ciągnie za sobą całą chmurę, a większość
      uruchomień aplikacji w ogóle go nie dotyka.

      Uprawnienie sprawdza serwer; tu chodzi tylko o to, żeby edytory miały
      kogo zapytać. Bez konta albo bez uprawnienia available() oddaje fałsz
      i po asystencie nie zostaje w interfejsie ani śladu.
    */
    val ai: AiAssistant by lazy {
        KajetAi(CloudAi(cloud.account, cloud.client, cloud.sync, cloud.codeIds))
    }

    // Tablet: Python lokalnie (Chaquopy), chyba że jest konto i sieć — wtedy CloudCode.
    // Telefon: bez Chaquopy, zawsze CloudCode → POST /api/v1/code.
    val runners: RunnerRegistry by lazy {
        val cloudCode = CloudCode(cloud.account, cloud.client)
        RunnerRegistry(
            runners = buildList {
                if (app.isKajetTablet()) {
                    add(TabletPythonRunner(app))
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
        // Pliki z kodem jeżdżą na serwer jako notatki CODE.
        library.onCodeSaved = { path ->
            cloud.sync.reportCodeChange(path)
        }
        // Kasowanie też jest zmianą: kosz zostawia na serwerze nagrobek,
        // opróżnienie kosza kasuje na stałe razem z załącznikami.
        library.onNotesTrashed = { ids ->
            cloud.sync.reportDeleted(ids, purge = false)
        }
        library.onNotesPurged = { ids ->
            cloud.sync.reportDeleted(ids, purge = true)
        }
        library.onCodeTrashed = { paths ->
            cloud.sync.reportCodeDeleted(paths)
        }
        library.onFoldersTrashed = { folderIds ->
            cloud.sync.reportFoldersDeleted(folderIds)
        }
        // Sam folder — bez notatki w środku — też jest zmianą do wysłania.
        // Synchronizacja zaczyna od uzgodnienia drzewa folderów, więc samo jej
        // odpalenie wystarczy, żeby świeży folder pojawił się na stronie.
        library.onFolderChanged = {
            cloud.sync.syncSoon()
        }
        library.onCodePurged = { paths ->
            cloud.sync.reportCodePurged(paths)
        }
        // Po notatce nie ma już śladu na dysku — chmura zapomina o niej
        // wszystko: zapamiętaną wersję, powiązanie pliku z kodem i zaległe
        // wpisy z treścią do wysłania. Bez tego zostawały na zawsze i przy
        // uzgadnianiu biblioteki potrafiły wskrzesić skasowaną notatkę.
        library.onNoteErased = { noteId ->
            cloud.sync.forgetNote(noteId)
        }
        library.onCodeErased = { path ->
            cloud.sync.forgetCodePath(path)
        }
        library.onCodeCreated = { path ->
            cloud.sync.reportCodeCreated(path)
        }
        library.onPathMoved = { oldPath, newPath ->
            cloud.sync.reportPathMoved(oldPath, newPath)
        }
    }
}

class KajetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Proces ekranu błędu (:blad) nie buduje kontenera i nie łapie
        // wyjątków — awaria ekranu błędu nie może otwierać go od nowa
        // w kółko; wtedy zostaje zwykłe zachowanie systemu.
        if (isErrorProcess()) return

        /*
          Ostatnia deska ratunku PRZED budową kontenera: każdy nieobsłużony
          wyjątek — także z budowy kontenera i z rysowania ekranu — zapisuje
          raport, otwiera ekran błędu w osobnym procesie i ubija ten proces.
          Bez tego zostawał czarny ekran, z którego wychodziło się tylko
          ubiciem aplikacji z ręki.
        */
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            // Opis powstaje RAZ i idzie w trzy miejsca: do pliku, na ekran i do
            // wysyłki. Inaczej zgłoszenie nie zgadzałoby się z tym, co widać.
            val report = runCatching { CrashLog.report(this, thread, failure) }
                .getOrElse { CrashLog.stackTrace(failure) }

            // Najpierw plik. Otwarcie ekranu potrafi się nie udać (patrz niżej),
            // a raport ma zostać zawsze.
            runCatching { CrashLog.write(this, report) }

            /*
              Od Androida 12 aplikacja w tle nie otworzy ekranu — system pisze
              w dzienniku „Background activity launch blocked". I UWAGA:
              startActivity wtedy NIE rzuca wyjątku, tylko po cichu nic nie
              robi. Po powodzeniu tego wywołania nie da się więc poznać, czy
              ekran naprawdę wstał.

              Dlatego raport odhacza jako pokazany sam ErrorActivity, w swoim
              onCreate. Gdy start został zablokowany, znacznik zostaje nietknięty
              i raport doczeka do najbliższego otwarcia Kajetu — wtedy pokaże go
              MainActivity.
            */
            runCatching { startActivity(ErrorActivity.intent(this, report)) }

            Process.killProcess(Process.myPid())
        }

        container = AppContainer(this)

        // Rozgrzewka chmury poza wątkiem głównym: magazyn kluczy i WorkManager
        // zdążą wstać, zanim MainActivity.onResume poprosi o synchronizację.
        Thread {
            runCatching {
                container.cloud
                container.runners
            }
        }.start()

        /*
          Zaległe raporty o awariach jadą na serwer.

          Osobny wątek, nie ten od rozgrzewki: wysyłka czeka na sieć do ośmiu
          sekund na raport, a chmura ma wstać jak najszybciej. Bez konta i bez
          tokenu — punkt na serwerze przyjmuje raporty od każdego, bo awaria
          trafia się także przed zalogowaniem.
        */
        Thread {
            runCatching { CrashUpload.sendPending(this) }
        }.start()

        /*
          Sprzątanie po kasowaniu — nie częściej niż raz na dobę i zawsze poza
          wątkiem głównym. Chodzi o zaległe kasowania, sieroty po plikach i
          stare zdjęcia z aparatu; nic z tego nie jest pilne, więc nie ma prawa
          opóźnić wejścia do biblioteki ani niczego przerwać.
        */
        Thread {
            runCatching {
                kotlinx.coroutines.runBlocking { container.housekeeping.runIfDue() }
            }
        }.start()
    }

    private fun isErrorProcess(): Boolean = currentProcessName().endsWith(":blad")

    private fun currentProcessName(): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            // cmdline kończy się znakiem NUL — dalej są śmieci.
            java.io.File("/proc/self/cmdline").readText().substringBefore(Char.MIN_VALUE).trim()
        }
    }.getOrDefault(packageName)
}

val Context.container: AppContainer
    get() = (applicationContext as KajetApp).container

/** Tablety (sw ≥ 600 dp) mogą liczyć Pythona lokalnie; telefony idą przez serwer. */
fun Context.isKajetTablet(): Boolean =
    resources.configuration.smallestScreenWidthDp >= 600
