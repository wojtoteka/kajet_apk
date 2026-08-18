package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafAccessTest {

    @Test
    fun `a restored tree uri without persistable grant is not access`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        assertThat(SafAccess.hasPersistedGrant(context.contentResolver, uri)).isFalse()
        assertThat(SafAccess.hasPersistedGrant(context.contentResolver, null)).isFalse()
        assertThat(SafAccess.hasPersistedGrant(context.contentResolver, "")).isFalse()
    }

    @Test
    fun `takePersistable keeps the grant when the system accepts it`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        SafAccess.takePersistable(context.contentResolver, uri)
        assertThat(SafAccess.hasPersistedGrant(context.contentResolver, uri)).isTrue()
    }

    @Test
    fun `library folder is stored in the prefs file excluded from backup`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = SettingsStore(context)
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AKajet"
        store.setLibraryFolder(uri)

        val prefs = context.getSharedPreferences(SettingsStore.SAF_PREFS, Context.MODE_PRIVATE)
        assertThat(prefs.getString(SettingsStore.SAF_URI_KEY, null)).isEqualTo(uri)
        assertThat(store.settings.first().libraryFolder).isEqualTo(uri)
    }
}
