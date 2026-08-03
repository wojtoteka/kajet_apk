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
    val deletedAt: Long,
) {
    val originalParent: String get() = originalPath.substringBeforeLast('/', "")

    companion object {
        const val DIRECTORY = ".trash"
        const val DESC_FILE = "kosz.json"
    }
}
