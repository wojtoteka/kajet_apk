package wojtoteka.ovh.kajet.cloud

import android.content.Context
import wojtoteka.ovh.kajet.storage.LibraryRepository

object Cloud {

    @Volatile
    private var parts: Parts? = null

    class Parts(
        val account: AccountStore,
        val client: CloudClient,
        val queue: SendQueue,
        val sync: Sync,
    )

    fun parts(context: Context, repository: LibraryRepository): Parts {
        parts?.let { return it }

        return synchronized(this) {
            parts ?: run {
                val appContext = context.applicationContext
                val account = AccountStore(appContext)
                val client = CloudClient(appContext, account)
                val queue = SendQueue(appContext)
                val sync = Sync(appContext, repository, account, client, queue)

                val created = Parts(account, client, queue, sync)
                parts = created

                // Background work runs in a separate process and has no other way
                // of getting these objects than through this single place.
                SyncWork.syncProvider = { parts?.sync }

                if (account.isSignedIn()) {
                    SyncWork.schedulePeriodic(appContext)
                    SyncWork.scheduleNow(appContext)
                }

                created
            }
        }
    }
}
