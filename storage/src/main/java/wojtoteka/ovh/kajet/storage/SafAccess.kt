package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import wojtoteka.ovh.kajet.core.text.words
import java.io.IOException

/**
 * Trwały dostęp do folderu wskazanego przez SAF.
 *
 * Sam zapisany adres drzewa nic nie znaczy: po kopii zapasowej albo
 * reinstalacji wraca bez [ContentResolver.getPersistedUriPermissions], a
 * [androidx.documentfile.provider.DocumentFile.fromTreeUri] i tak zbuduje
 * obiekt, z którego listowanie wychodzi puste. Biblioteka wygląda na pustą,
 * choć pliki leżą tam, gdzie leżały.
 */
object SafAccess {

    val persistFlags: Int =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun hasPersistedGrant(resolver: ContentResolver, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return hasPersistedGrant(resolver, uri)
    }

    fun hasPersistedGrant(resolver: ContentResolver, uri: Uri): Boolean {
        val wanted = treeUri(uri)
        return resolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission &&
                permission.isWritePermission &&
                sameTree(wanted, treeUri(permission.uri))
        }
    }

    /**
     * Zapisuje trwałe uprawnienie. Gdy system odrzuci, wołamy jeszcze raz -
     * dopiero potem zgłaszamy błąd. Nie wolno po cichu zapisać adresu folderu
     * bez uprawnienia: to właśnie ta cisza dawała pustą bibliotekę.
     */
    fun takePersistable(resolver: ContentResolver, uri: Uri) {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                resolver.takePersistableUriPermission(uri, persistFlags)
                if (hasPersistedGrant(resolver, uri)) return
                last = SecurityException("uprawnienie nie zostało zapisane")
            } catch (failure: SecurityException) {
                last = failure
                Log.w(
                    "Kajet",
                    "Nie udało się zapisać trwałego dostępu do folderu (próba ${attempt + 1})",
                    failure,
                )
            }
        }
        throw IOException(words.couldNotKeepFolderAccess, last)
    }

    private fun treeUri(uri: Uri): Uri = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.buildTreeDocumentUri(
                uri.authority,
                DocumentsContract.getTreeDocumentId(uri),
            )
        } else {
            uri
        }
    }.getOrDefault(uri)

    private fun sameTree(left: Uri, right: Uri): Boolean {
        if (left == right) return true
        val a = left.toString()
        val b = right.toString()
        return a == b || Uri.decode(a) == Uri.decode(b)
    }
}
