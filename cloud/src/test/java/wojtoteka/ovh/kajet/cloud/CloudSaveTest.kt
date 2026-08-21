package wojtoteka.ovh.kajet.cloud

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

class CloudSaveStateTest {

    @Test
    fun `wylogowany nie twierdzi ze notatka jest w chmurze`() {
        assertThat(cloudSaveState(signedIn = false, inQueue = false, knownOnServer = true)).isNull()
        assertThat(cloudSaveState(signedIn = false, inQueue = true, knownOnServer = false)).isNull()
    }

    @Test
    fun `wpis w kolejce to oczekiwanie nawet gdy serwer znal poprzednia wersje`() {
        assertThat(cloudSaveState(signedIn = true, inQueue = true, knownOnServer = true)).isFalse()
    }

    @Test
    fun `znana wersja poza kolejka to serwer`() {
        assertThat(cloudSaveState(signedIn = true, inQueue = false, knownOnServer = true)).isTrue()
    }

    @Test
    fun `zalogowany bez potwierdzenia nie klamie ze jest w chmurze`() {
        assertThat(cloudSaveState(signedIn = true, inQueue = false, knownOnServer = false)).isFalse()
    }
}

/**
 * [Sync.cloudSave] czyta kolejkę i zapamiętane wersje - te same, którymi
 * żyje wysyłka. Atrapa konta jest zalogowana, jak w pozostałych testach sync.
 */
@RunWith(RobolectricTestRunner::class)
class CloudSaveTest {

    private lateinit var context: Context
    private lateinit var queue: SendQueue
    private lateinit var sync: Sync

    private val noteId = "nota-1"
    private val path = "szkola/fizyka.note"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        queue = SendQueue(context)
        queue.clear()
        sync = Sync(
            context,
            FakeLibrary(),
            FakeAccount(),
            FakeTransport(),
            queue,
            CodeFileIds(context),
        )
    }

    @Test
    fun `kolejka podnosi rewizje przy dopisaniu i zdjeciu`() {
        val before = queue.revision.value
        queue.add(path, noteId)
        assertThat(queue.revision.value).isGreaterThan(before)
        val afterAdd = queue.revision.value
        queue.remove(path)
        assertThat(queue.revision.value).isGreaterThan(afterAdd)
    }

    @Test
    fun `wpis w kolejce to nie chmura`() {
        queue.add(path, noteId)
        assertThat(sync.cloudSave(path, noteId)).isFalse()
    }

    @Test
    fun `zapamietana wersja poza kolejka to chmura`() {
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().putInt(noteId, 4).commit()
        assertThat(sync.cloudSave(path, noteId)).isTrue()
    }

    @Test
    fun `zalogowany bez wersji i bez kolejki nie twierdzi ze serwer ma notatke`() {
        assertThat(sync.cloudSave(path, noteId)).isFalse()
    }

    @Test
    fun `kolejka wygrywa z zapamietana wersja`() {
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().putInt(noteId, 4).commit()
        queue.add(path, noteId)
        assertThat(sync.cloudSave(path, noteId)).isFalse()
    }
}
