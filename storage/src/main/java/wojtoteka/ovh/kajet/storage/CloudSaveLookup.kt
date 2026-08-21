package wojtoteka.ovh.kajet.storage

import kotlinx.coroutines.flow.Flow

/**
 * Stan wysyłki otwartej notatki albo pliku z kodem.
 *
 * Źródłem prawdy jest kolejka i zapamiętana wersja serwera - nie sam lokalny
 * zapis. „Zapisane" na dysku nie znaczy, że serwer już to ma.
 *
 * - **true** - nie ma wpisu w kolejce i znamy serwerową wersję (wysłane albo
 *   pobrane)
 * - **false** - jest konto, ale zmiana czeka w kolejce albo nie ma
 *   potwierdzenia z serwera
 * - **null** - nikt nie jest zalogowany; ikony chmury nie pokazujemy, bo nie
 *   ma czego twierdzić
 */
interface CloudSaveLookup {

    /** Cokolwiek, po czym warto odczytać [inCloud] jeszcze raz. */
    fun changes(): Flow<Unit>

    fun inCloud(path: String, noteId: String?): Boolean?
}
