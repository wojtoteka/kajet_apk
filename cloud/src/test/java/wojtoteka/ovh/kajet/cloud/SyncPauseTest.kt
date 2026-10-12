package wojtoteka.ovh.kajet.cloud

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent

/**
 * Wstrzymana synchronizacja ([SyncPause]) ma działać jak brak sieci:
 * nic nie jedzie, kolejka czeka, a po wznowieniu wszystko idzie od razu.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPauseTest {

    private lateinit var context: Context
    private lateinit var library: FakeLibrary
    private lateinit var transport: FakeTransport
    private lateinit var queue: SendQueue
    private lateinit var pause: SyncPause
    private lateinit var sync: Sync

    private val noteId = "nota-pauza"
    private val path = "szkola/pauza.note"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        library = FakeLibrary()
        transport = FakeTransport()
        queue = SendQueue(context)
        queue.clear()
        pause = SyncPause(context)
        pause.resume()
        sync = Sync(context, library, FakeAccount(), transport, queue, CodeFileIds(context))
        sync.pauseCheck = pause::isPaused
        library.notes[path] = NoteDocument(
            id = noteId,
            kind = NoteKind.TEXT,
            title = "Pauza",
            createdAt = 1_000,
            updatedAt = 2_000,
            text = TextContent(markdown = "treść"),
        )
        queue.add(path, noteId)
    }

    @Test
    fun `wstrzymana synchronizacja nic nie wysyła i trzyma kolejkę`() {
        pause.pauseUntil(System.currentTimeMillis() + 60_000)

        val result = runBlocking { sync.synchronise() }

        assertThat(transport.sentNotes).isEmpty()
        assertThat(queue.size()).isEqualTo(1)
        assertThat(result.worthRetrying).isFalse()
        assertThat(sync.state.value).isEqualTo(SyncState.Paused(1))
        // Szara chmura w notatce, choć sieć jest.
        assertThat(sync.cloudSave(path, noteId)).isFalse()
    }

    @Test
    fun `pauza do odwołania trwa, dopóki ktoś jej nie zdejmie`() {
        pause.pauseUntil(SyncPause.FOREVER)
        assertThat(pause.isPaused()).isTrue()

        pause.resume()
        runBlocking { sync.synchronise() }

        assertThat(transport.sentNotes.map { it.id }).containsExactly(noteId)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `pauza z przeszłości już nie obowiązuje`() {
        pause.pauseUntil(System.currentTimeMillis() - 1_000)

        runBlocking { sync.synchronise() }

        assertThat(pause.isPaused()).isFalse()
        assertThat(transport.sentNotes.map { it.id }).containsExactly(noteId)
    }
}
