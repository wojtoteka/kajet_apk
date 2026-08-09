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
import wojtoteka.ovh.kajet.storage.ServerDeletion

/**
 * Kosz i trwałe kasowanie: co synchronizacja robi z kolejką, zapamiętaną
 * wersją i plikiem, kiedy notatka znika — tutaj albo na serwerze.
 *
 * Te dwie drogi wyglądają podobnie, a znaczą co innego: kosz zostawia na
 * serwerze nagrobek (notatkę da się jeszcze wyjąć), trwałe kasowanie usuwa
 * wiersz razem z załącznikami. Pomyłka między nimi to „skasowałem notatkę
 * i wróciła" albo „wyrzuciłem do kosza i przepadła".
 */
@RunWith(RobolectricTestRunner::class)
class SyncTrashTest {

    private lateinit var context: Context
    private lateinit var library: FakeLibrary
    private lateinit var transport: FakeTransport
    private lateinit var account: FakeAccount
    private lateinit var queue: SendQueue
    private lateinit var codeIds: CodeFileIds
    private lateinit var sync: Sync

    private val noteId = "nota-1"
    private val notePath = "szkola/fizyka.note"
    private val codePath = "szkola/skrypt.py"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        library = FakeLibrary()
        transport = FakeTransport()
        account = FakeAccount()
        queue = SendQueue(context)
        queue.clear()
        codeIds = CodeFileIds(context)
        codeIds.clear()
        sync = Sync(context, library, account, transport, queue, codeIds)
    }

    private fun document() = NoteDocument(
        id = noteId,
        kind = NoteKind.TEXT,
        title = "Fizyka",
        createdAt = 1_000,
        updatedAt = 2_000,
        text = TextContent(markdown = "treść"),
    )

    private fun knownVersion(id: String): Int =
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE).getInt(id, 0)

    /** Wersja zapamiętana wcześniejszym przebiegiem — ten sam schowek co Sync. */
    private fun rememberVersion(id: String, version: Int) {
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE)
            .edit().putInt(id, version).commit()
    }

    private fun sync(): SyncResult = runBlocking { sync.synchronise() }

    // --- Kosz kontra trwałe kasowanie ---

    @Test
    fun `kosz jedzie jako nagrobek i zapamietuje jego wersje`() {
        queue.addDeletion(noteId, purge = false)
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "ok", version = 12)) }

        sync()

        val sent = transport.sentNotes.single()
        assertThat(sent.id).isEqualTo(noteId)
        assertThat(sent.deleted).isTrue()
        assertThat(sent.content).isEmpty()
        // Wersję nagrobka trzeba pamiętać, inaczej najbliższe pobranie wzięłoby
        // go za nowość i notatka wróciłaby na urządzenie.
        assertThat(knownVersion(noteId)).isEqualTo(12)
        // Kosz to nie trwałe kasowanie — adres kasujący zostaje nietknięty.
        assertThat(transport.deletedIds).isEmpty()
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `trwale kasowanie idzie osobnym adresem i zapomina wersje`() {
        rememberVersion(noteId, 12)
        queue.addDeletion(noteId, purge = true)

        sync()

        assertThat(transport.deletedIds).containsExactly(noteId)
        assertThat(transport.sentNotes).isEmpty()
        // Po notatce nie ma śladu, więc nie ma czego pamiętać.
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `trwale kasowanie wygrywa z koszem bez wzgledu na kolejnosc`() {
        queue.addDeletion(noteId, purge = false)
        queue.addDeletion(noteId, purge = true)
        assertThat(queue.all().single().kind).isEqualTo(QueueEntry.KIND_PURGE)

        // I w drugą stronę: zgłoszony kosz nie cofa trwałego kasowania.
        queue.addDeletion(noteId, purge = false)
        assertThat(queue.all().single().kind).isEqualTo(QueueEntry.KIND_PURGE)

        sync()

        assertThat(transport.deletedIds).containsExactly(noteId)
        assertThat(transport.sentNotes).isEmpty()
    }

    @Test
    fun `trwale skasowany plik z kodem zwalnia swoj numer`() {
        val id = codeIds.idFor(codePath)
        queue.addDeletion(id, purge = true)

        sync()

        assertThat(transport.deletedIds).containsExactly(id)
        // Numer jest wolny: nowy plik pod tą samą ścieżką dostanie świeży,
        // a nie odziedziczy ten po skasowanej notatce i jej nie wskrzesi.
        assertThat(codeIds.existingIdFor(codePath)).isNull()
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `nieudane kasowanie zostaje w kolejce i po probach utyka`() {
        queue.addDeletion(noteId, purge = true)
        transport.onDeleteNote = {
            CloudClient.Result.Error("serwer odmawia", worthRetrying = false)
        }

        repeat(QueueEntry.MAX_ATTEMPTS) { sync() }

        // Wpis nie znika po cichu — kiedyś znikał i notatka zostawała na
        // serwerze na zawsze, choć na urządzeniu jej nie było.
        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.stuckCount()).isEqualTo(1)
        assertThat(sync.stuck.value).isEqualTo(1)
    }

    // --- Nagrobki z serwera ---

    @Test
    fun `nagrobek wyrzuca notatke i zapomina jej wersje`() {
        rememberVersion(noteId, 4)
        library.notes[notePath] = document()
        transport.tombstones = listOf(noteId)
        library.serverDeletions[noteId] = ServerDeletion.TRASHED

        sync()

        assertThat(library.serverDeletionCalls).containsExactly(noteId)
        assertThat(library.notes).doesNotContainKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(0)
    }

    @Test
    fun `nagrobek nie rusza notatki z czekajaca wysylka`() {
        rememberVersion(noteId, 4)
        library.notes[notePath] = document()
        queue.add(notePath, noteId)
        // Wysyłka nie dochodzi do skutku, więc wpis zostaje w kolejce.
        transport.onSendNote = {
            CloudClient.Result.Error("zerwana sieć", worthRetrying = true)
        }
        transport.tombstones = listOf(noteId)
        library.serverDeletions[noteId] = ServerDeletion.ERASED

        sync()

        // Notatkę z czekającą zmianą rozstrzyga wysyłka (serwer odpowie „gone"),
        // a nie ciche sprzątanie po nagrobku.
        assertThat(library.serverDeletionCalls).isEmpty()
        assertThat(library.notes).containsKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(4)
        assertThat(queue.size()).isEqualTo(1)
    }

    // --- Nieudane przenoszenie do kosza (błąd SAF, cofnięte uprawnienie) ---

    @Test
    fun `nieudany kosz przy gone zostawia wpis i wersje`() {
        library.notes[notePath] = document()
        rememberVersion(noteId, 4)
        queue.add(notePath, noteId)
        library.failTrash = true
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "gone")) }

        sync()

        // Nic nie udajemy: notatka na miejscu, wersja niezapomniana, wpis
        // w kolejce z odnotowaną porażką — kiedyś wszystko schodziło mimo
        // nieudanego przeniesienia i plik zostawał bezpański.
        assertThat(library.notes).containsKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(4)
        assertThat(queue.all().single().failedAttempts).isEqualTo(1)

        // Awaria ustąpiła — wszystko dochodzi do końca.
        library.failTrash = false
        sync()
        assertThat(library.trashedNoteIds).containsExactly(noteId)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `nieudany kosz przy konflikcie z uzgadniania zostawia wpis`() {
        library.notes[notePath] = document()
        queue.add(notePath, noteId, reconciled = true)
        library.failTrash = true
        transport.onSendNote = {
            CloudClient.Result.Ok(
                SaveResponse(
                    status = "conflict",
                    onServer = ServerNote(id = noteId, version = 9, deletedAt = 4_000),
                ),
            )
        }

        sync()

        assertThat(library.notes).containsKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.all().single().failedAttempts).isEqualTo(1)
    }

    @Test
    fun `nieudany kosz pliku z kodem nie zwalnia numeru`() {
        library.texts[codePath] = "print(1)"
        val id = codeIds.idFor(codePath)
        rememberVersion(id, 3)
        queue.add(codePath, id, QueueEntry.KIND_CODE)
        library.failTrash = true
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "gone")) }

        sync()

        // Numer nie schodzi — inaczej plik dostałby świeży i wrócił na
        // serwer jako duplikat, choć wciąż leży w bibliotece.
        assertThat(library.texts).containsKey(codePath)
        assertThat(codeIds.existingIdFor(codePath)).isEqualTo(id)
        assertThat(knownVersion(id)).isEqualTo(3)
        assertThat(queue.all().single().failedAttempts).isEqualTo(1)
    }

    @Test
    fun `nieudany kosz przy pobranym nagrobku nie zapamietuje wersji`() {
        library.notes[notePath] = document()
        rememberVersion(noteId, 4)
        library.failTrash = true
        transport.serverNotes = listOf(
            ServerNote(id = noteId, title = "Fizyka", version = 6, updatedAt = 5_000, deletedAt = 4_500),
        )

        sync()

        // Zapamiętana wersja nagrobka udawałaby, że zadziałał — notatka
        // zostałaby przy życiu na zawsze.
        assertThat(library.notes).containsKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(4)

        library.failTrash = false
        sync()
        assertThat(library.notes).doesNotContainKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(6)
    }

    @Test
    fun `nieudane sprzatniecie nagrobka nie przesuwa znacznika`() {
        rememberVersion(noteId, 4)
        library.notes[notePath] = document()
        transport.tombstones = listOf(noteId)
        library.serverDeletions[noteId] = ServerDeletion.TRASHED
        library.failServerDeletions = true

        sync()

        // Znacznik stoi — przesunięty schowałby nagrobek na zawsze.
        assertThat(account.lastDeleted).isEqualTo(0)
        assertThat(library.notes).containsKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(4)

        library.failServerDeletions = false
        sync()
        assertThat(library.notes).doesNotContainKey(notePath)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(account.lastDeleted).isGreaterThan(0L)
    }

    @Test
    fun `nagrobek pliku z kodem trafia w plik po sciezce z rejestru`() {
        library.texts[codePath] = "print(1)"
        val id = codeIds.idFor(codePath)
        rememberVersion(id, 3)
        transport.tombstones = listOf(id)
        library.serverCodeDeletions[codePath] = ServerDeletion.TRASHED

        sync()

        // Plik z kodem poznaje się po ścieżce z rejestru, nie po identyfikatorze
        // notatki — bez tego nagrobek trafiałby w próżnię.
        assertThat(library.serverCodeDeletionCalls).containsExactly(codePath)
        assertThat(library.serverDeletionCalls).isEmpty()
        assertThat(library.texts).doesNotContainKey(codePath)
        assertThat(knownVersion(id)).isEqualTo(0)
    }
}
