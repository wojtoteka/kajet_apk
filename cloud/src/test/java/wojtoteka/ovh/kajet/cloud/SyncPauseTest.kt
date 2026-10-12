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
import wojtoteka.ovh.kajet.storage.NoteCodec

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

    @Test
    fun `KajetAI przy pauzie bierze treść z dysku i zapisuje wynik u siebie, do kolejki`() {
        pause.pauseUntil(SyncPause.FOREVER)
        queue.clear()

        val local = runBlocking { sync.localForAi(noteId) }!!
        assertThat(local.kind).isEqualTo("TEXT")
        assertThat(NoteCodec.decodeNote(local.content).text?.markdown).isEqualTo("treść")

        val changed = NoteCodec.encodeNote(
            NoteCodec.decodeNote(local.content).copy(text = TextContent(markdown = "treść i chleb")),
        )
        assertThat(runBlocking { sync.applyAiResult(local, changed) }).isTrue()

        assertThat(library.notes.values.map { it.text?.markdown }).contains("treść i chleb")
        // Na serwer nic nie poszło - zmiana czeka w kolejce na wznowienie.
        assertThat(transport.sentNotes).isEmpty()
        assertThat(queue.size()).isEqualTo(1)
    }

    @Test
    fun `rozmowa z pauzy czeka przy notatce i schodzi po dosłaniu`() {
        val pending = PendingAiTurns(context)
        pending.forget(noteId)

        pending.add(noteId, AiTurn("dopisz masło", "Dopisano masło."))
        pending.add(noteId, AiTurn("dopisz chleb", "Dopisano chleb."))
        assertThat(pending.of(noteId).map { it.request }).containsExactly("dopisz masło", "dopisz chleb").inOrder()
        assertThat(pending.noteIds()).contains(noteId)

        pending.dropFirst(noteId, 2)
        assertThat(pending.of(noteId)).isEmpty()
        assertThat(pending.noteIds()).doesNotContain(noteId)
    }
}
