package wojtoteka.ovh.kajet.storage

import kotlinx.serialization.Serializable
import wojtoteka.ovh.kajet.core.model.ItemType

/**
 * Opis wyrzuconego wpisu, zapisany obok niego w koszu.
 *
 * Kosz to katalog .trash w tym samym miejscu co biblioteka, a nie kasowanie.
 * Dzięki temu notatka wyrzucona przez pomyłkę leży dalej na dysku
 * i wraca dokładnie tam, skąd zniknęła.
 */
@Serializable
data class WpisKosza(
    val id: String,
    /** Ścieżka, z której wpis został wyrzucony, licząc od katalogu biblioteki. */
    val originalPath: String,
    /** Nazwa pliku albo katalogu na dysku. */
    val fileName: String,
    /** Nazwa pokazywana użytkownikowi. */
    val displayName: String,
    val type: ItemType,
    val deletedAt: Long,
) {
    val originalParent: String get() = originalPath.substringBeforeLast('/', "")

    companion object {
        const val KATALOG = ".trash"
        const val PLIK_OPISU = "kosz.json"
    }
}
