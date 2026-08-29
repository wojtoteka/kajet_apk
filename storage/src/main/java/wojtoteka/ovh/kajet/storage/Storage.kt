package wojtoteka.ovh.kajet.storage

import android.content.Context
import wojtoteka.ovh.kajet.storage.index.IndexDatabase

object Storage {

    fun settings(context: Context): SettingsStore = SettingsStore(context.applicationContext)

    fun library(context: Context, settings: SettingsStore): LibraryRepository =
        LibraryRepository(
            context = context.applicationContext,
            settings = settings,
            dao = IndexDatabase.get(context).index(),
            retries = DeleteRetryQueue(context.applicationContext),
        )

    /** Sprzątanie po kasowaniu - woła je aplikacja przy starcie, raz na dobę. */
    fun housekeeping(context: Context, library: LibraryRepository): Housekeeping =
        Housekeeping(context.applicationContext, library)

    fun uploads(context: Context): FileUploadStore =
        FileUploadStore(context.applicationContext, IndexDatabase.get(context).uploads())
}
