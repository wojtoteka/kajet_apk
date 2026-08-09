package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.Serializable
import wojtoteka.ovh.kajet.core.model.ItemType

@Serializable
data class TrashEntry(
    val id: String,
    val originalPath: String,
    val fileName: String,
    val displayName: String,
    val type: ItemType,
    /**
     * Kiedy wpis trafił do kosza NA TYM urządzeniu. Przy kasowaniu zgłoszonym
     * przez serwer to nie jest data skasowania na serwerze, tylko chwila, w
     * której aplikacja się o nim dowiedziała — od niej liczy się czas na
     * przywrócenie. Data serwera zrobiłaby z tego pułapkę: tablet nieużywany
     * dłużej niż termin wymazałby notatkę, zanim ktokolwiek zobaczyłby ją
     * w koszu.
     */
    val deletedAt: Long,
    /**
     * Wpis trafił tu dlatego, że notatka zniknęła na serwerze, a nie dlatego,
     * że ktoś ją tutaj wyrzucił.
     *
     * Takie wpisy mają termin: po [Housekeeping.SERVER_TRASH_DAYS] dniach znikają
     * z dysku same. Bez tego zostawałyby w koszu na zawsze — serwer nie ma już
     * po nich ani wiersza, ani nagrobka, więc nic by o nich nie przypomniało.
     * Wpisy wyrzucone ręcznie terminu nie mają i czekają, aż opróżnisz kosz
     * albo aż serwer skasuje swoją kopię.
     *
     * Domyślne false, żeby stare pliki kosza dalej dawały się odczytać.
     */
    val fromServer: Boolean = false,
) {
    val originalParent: String get() = originalPath.substringBeforeLast('/', "")

    companion object {
        const val DIRECTORY = ".trash"
        const val DESC_FILE = "kosz.json"
    }
}
