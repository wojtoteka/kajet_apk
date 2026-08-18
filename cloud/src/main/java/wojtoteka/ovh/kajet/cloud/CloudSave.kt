package wojtoteka.ovh.kajet.cloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import wojtoteka.ovh.kajet.storage.CloudSaveLookup

/**
 * Trzy stany paska zapisu, ze znanych sygnałów kolejki i wersji serwera.
 *
 * Nie zgadujemy: bez konta ikona chmury milczy, a „na serwerze" tylko wtedy,
 * gdy wpisu nie ma w kolejce i pamiętamy wersję po wysyłce albo pobraniu.
 */
fun cloudSaveState(
    signedIn: Boolean,
    inQueue: Boolean,
    knownOnServer: Boolean,
): Boolean? {
    if (!signedIn) return null
    if (inQueue) return false
    return knownOnServer
}

/**
 * Furtka dla edytora: kolejka, stan konta i przebieg synchronizacji mówią,
 * kiedy warto odczytać [Sync.cloudSave] jeszcze raz.
 *
 * [cloud] jest leniwe — budowa chmury to I/O, a tę furtkę stawia kontener
 * przy starcie, zanim ktokolwiek otworzy notatkę.
 */
class CloudSaveStatus(
    private val cloud: () -> Cloud.Parts,
) : CloudSaveLookup {

    override fun changes(): Flow<Unit> {
        val parts = cloud()
        return merge(
            parts.queue.revision.map { },
            parts.account.state.map { },
            parts.sync.state.map { },
            parts.sync.stuck.map { },
        )
    }

    override fun inCloud(path: String, noteId: String?): Boolean? =
        cloud().sync.cloudSave(path, noteId)
}
