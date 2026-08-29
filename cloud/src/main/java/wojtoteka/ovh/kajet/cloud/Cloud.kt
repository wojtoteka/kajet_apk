package wojtoteka.ovh.kajet.cloud

import android.content.Context
import wojtoteka.ovh.kajet.storage.LibraryRepository
import wojtoteka.ovh.kajet.storage.Storage

object Cloud {

    @Volatile
    private var parts: Parts? = null

    class Parts(
        val account: AccountStore,
        val client: CloudClient,
        val queue: SendQueue,
        val sync: Sync,
        val auth: AuthWatch,
        /*
          Rejestr „ścieżka pliku -> identyfikator notatki CODE". Do tej pory
          znała go sama synchronizacja; asystentowi też jest potrzebny, bo
          plik z kodem nie niesie identyfikatora w sobie, a serwer rozpoznaje
          notatki wyłącznie po nim.
        */
        val codeIds: CodeFileIds,
        val uploads: FileUploader,
    )

    fun parts(context: Context, repository: LibraryRepository): Parts {
        parts?.let { return it }

        return synchronized(this) {
            parts ?: run {
                val appContext = context.applicationContext
                val account = AccountStore(appContext)
                val client = CloudClient(appContext, account)
                val queue = SendQueue(appContext)
                val codeIds = CodeFileIds(appContext)
                val uploads = FileUploader(appContext, Storage.uploads(appContext), repository, client)
                val sync = Sync(appContext, repository, account, client, queue, codeIds, uploads)
                val auth = AuthWatch(account, client)

                val created = Parts(account, client, queue, sync, auth, codeIds, uploads)
                parts = created

                // Background work runs in a separate process and has no other way
                // of getting these objects than through this single place.
                SyncWork.syncProvider = { parts?.sync }

                if (account.isSignedIn()) {
                    SyncWork.schedulePeriodic(appContext)
                    SyncWork.scheduleNow(appContext)
                    // Start aplikacji: token trzeba sprawdzić, zanim ktokolwiek
                    // zobaczy „Zalogowano jako…". Wylogowanie przez stronę
                    // mogło się zdarzyć, gdy Kajet był zamknięty.
                    auth.check()
                }

                created
            }
        }
    }
}
