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
        )
}
