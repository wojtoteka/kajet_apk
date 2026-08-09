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
 * Co synchronizacja ROBI z odpowiedzią serwera — dalszy ciąg
 * [ServerContractTest], który sprawdza tylko, czy umie ją odczytać.
 *
 * Odpowiedź konfliktu rozgałęzia się na pięć ścieżek różniących się jedną
 * flagą albo jednym polem, a pomyłka między nimi to „skasowałem notatkę
 * i wróciła" albo „napisałem notatkę i zniknęła". Te testy przypinają każdą
 * ścieżkę z osobna, na atrapach z [SyncFakes] — prawdziwe są kolejka
 * (SharedPreferences przez Robolectric) i zapamiętane wersje.
 *
 * Pliki z kodem ma [SyncCodeTest], a kosz i trwałe kasowanie [SyncTrashTest].
 */
@RunWith(RobolectricTestRunner::class)
class SyncTest {

    private lateinit var context: Context
    private lateinit var library: FakeLibrary
    private lateinit var transport: FakeTransport
    private lateinit var account: FakeAccount
    private lateinit var queue: SendQueue
    private lateinit var sync: Sync

    private val noteId = "nota-1"
    private val path = "szkola/fizyka.note"

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
        sync = Sync(context, library, account, transport, queue, CodeFileIds(context))
    }

    private fun document(id: String = noteId, body: String = "treść") = NoteDocument(
        id = id,
        kind = NoteKind.TEXT,
        title = "Fizyka",
        createdAt = 1_000,
        updatedAt = 2_000,
        text = TextContent(markdown = body),
    )

    private fun serverCopy(body: String = "z serwera", version: Int = 7) = ServerNote(
        id = noteId,
        title = "Fizyka",
        kind = "TEXT",
        version = version,
        updatedAt = 5_000,
        content = NoteCodec.encodeNote(document(body = body)),
    )

    private fun knownVersion(id: String): Int =
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE).getInt(id, 0)

    private fun sync(): SyncResult = runBlocking { sync.synchronise() }

    // --- Konflikt: kopia obok ---

    @Test
    fun `konflikt tworzy kopie obok a lokalny plik zostaje nietkniety`() {
        library.notes[path] = document(body = "lokalna wersja")
        queue.add(path, noteId)
        transport.onSendNote = { note ->
            // Konfliktem odpowiada tylko sporna notatka; kopia konfliktu,
            // którą uzgadnianie wyśle jako nowość, przechodzi normalnie.
            if (note.id == noteId) {
                CloudClient.Result.Ok(SaveResponse(status = "conflict", onServer = serverCopy()))
            } else {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
            }
        }

        val result = sync()

        assertThat(result.conflicts).isEqualTo(1)
        // Lokalny plik dokładnie ten sam, a obok stanęła kopia z serwera.
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("lokalna wersja")
        val copy = library.notes.values.single { it.id != noteId }
        assertThat(copy.title).contains("wersja z serwera")
        assertThat(copy.text!!.markdown).isEqualTo("z serwera")
        // Konflikt rozliczony: wersja zapamiętana, wpis zdjęty z kolejki.
        assertThat(knownVersion(noteId)).isEqualTo(7)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `nieudana kopia konfliktu nie zdejmuje wpisu i nie zapamietuje wersji`() {
        library.notes[path] = document(body = "lokalna wersja")
        library.failWrites = true
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Ok(SaveResponse(status = "conflict", onServer = serverCopy()))
        }

        val result = sync()

        // Nic nie udajemy: bez kopii nie ma rozliczenia konfliktu.
        assertThat(result.conflicts).isEqualTo(0)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.all().single().failedAttempts).isEqualTo(1)
    }

    // --- Konflikt z serwerowym koszem ---

    @Test
    fun `kosz serwera wygrywa przy wpisie z uzgadniania`() {
        library.notes[path] = document()
        queue.add(path, noteId, reconciled = true)
        transport.onSendNote = {
            CloudClient.Result.Ok(
                SaveResponse(
                    status = "conflict",
                    onServer = serverCopy(version = 9).copy(deletedAt = 4_000),
                ),
            )
        }

        sync()

        assertThat(library.trashedNoteIds).containsExactly(noteId)
        assertThat(knownVersion(noteId)).isEqualTo(9)
        assertThat(queue.size()).isEqualTo(0)
        // Kopia konfliktu NIE powstaje — kosz to nie rozjazd treści.
        assertThat(library.notes.values.none { it.title.contains("wersja z serwera") }).isTrue()
    }

    @Test
    fun `zapis czlowieka wskrzesza notatke z serwerowego kosza`() {
        library.notes[path] = document(body = "pisane dalej")
        queue.add(path, noteId, reconciled = false)
        var calls = 0
        transport.onSendNote = { note ->
            calls += 1
            if (calls == 1) {
                CloudClient.Result.Ok(
                    SaveResponse(
                        status = "conflict",
                        onServer = serverCopy(version = 9).copy(deletedAt = 4_000),
                    ),
                )
            } else {
                // Druga wysyłka idzie już z zapamiętaną wersją nagrobka.
                assertThat(note.baseVersion).isEqualTo(9)
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 10))
            }
        }

        sync()

        assertThat(calls).isEqualTo(2)
        assertThat(library.trashedNoteIds).isEmpty()
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("pisane dalej")
        assertThat(knownVersion(noteId)).isEqualTo(10)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `gone wyrzuca lokalna kopie do kosza i zapomina wersje`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "gone")) }

        sync()

        assertThat(library.trashedNoteIds).containsExactly(noteId)
        assertThat(knownVersion(noteId)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(0)
    }

    // --- Nieznana podstawa (reinstalacja, wyczyszczenie danych) ---

    @Test
    fun `pobranie nie nadpisuje pliku o nieznanej podstawie tylko robi konflikt`() {
        library.notes[path] = document(body = "lokalna, może nowsza")
        transport.serverNotes = listOf(serverCopy(body = "serwerowa", version = 5))

        val result = sync()

        // Lokalny plik nietknięty, kopia serwera obok, wersja zapamiętana.
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("lokalna, może nowsza")
        val copy = library.notes.values.single { it.id != noteId }
        assertThat(copy.text!!.markdown).isEqualTo("serwerowa")
        assertThat(knownVersion(noteId)).isEqualTo(5)
        assertThat(result.conflicts).isEqualTo(1)
        // Uzgadnianie nie wysłało niczego z baseVersion = 0.
        assertThat(transport.sentNotes.none { it.id == noteId && it.baseVersion == 0 }).isTrue()
    }

    @Test
    fun `zgodna tresc przy nieznanej podstawie zostaje przyjeta bez kopii`() {
        library.notes[path] = document(body = "ta sama treść")
        transport.serverNotes = listOf(serverCopy(body = "ta sama treść", version = 5))

        sync()

        assertThat(library.notes).hasSize(1)
        assertThat(knownVersion(noteId)).isEqualTo(5)
        assertThat(transport.sentNotes).isEmpty()
    }

    @Test
    fun `niedokonczone pobranie wstrzymuje uzgadnianie biblioteki`() {
        library.notes[path] = document()
        transport.failFetch = true

        sync()

        // Bez pełnego pobrania nie wolno wysyłać w ciemno z baseVersion = 0.
        assertThat(transport.sentNotes).isEmpty()
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `pierwsza synchronizacja pustego serwera wysyla nieznane notatki`() {
        library.notes[path] = document()

        sync()

        // Serwer pusty, pobranie pełne — uzgadnianie ma prawo wysłać nowość.
        assertThat(transport.sentNotes.single().baseVersion).isEqualTo(0)
        assertThat(knownVersion(noteId)).isEqualTo(1)
    }

    // --- Bramka pierwszego pobrania (wpis w kolejce przed pierwszym fetchem) ---

    @Test
    fun `wpis sprzed pierwszego pobrania nie jedzie z zerowa podstawa`() {
        // Po reinstalacji: plik jest, wersji nie znamy, a człowiek już pisze.
        library.notes[path] = document(body = "lokalna po reinstalacji")
        queue.add(path, noteId)
        transport.serverNotes = listOf(serverCopy(body = "nowsza na serwerze", version = 7))
        transport.onSendNote = { note ->
            if (note.id == noteId) {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 8))
            } else {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
            }
        }

        sync()

        // Serwer NIGDY nie dostał zapisu z zerową podstawą — przy zerze
        // przyjąłby bezwarunkowo i nowsza wersja przepadłaby bez śladu.
        assertThat(transport.sentNotes.none { it.id == noteId && it.baseVersion == 0 }).isTrue()
        // Wpis pojechał w tym samym przebiegu, ale już po pobraniu,
        // z podstawą zapamiętaną przez straż nieznanej podstawy.
        assertThat(transport.sentNotes.single { it.id == noteId }.baseVersion).isEqualTo(7)
        // Lokalny plik nietknięty, treść serwera w kopii obok.
        assertThat(library.notes[path]!!.text!!.markdown).isEqualTo("lokalna po reinstalacji")
        val copy = library.notes.values
            .single { it.id != noteId && it.title.contains("wersja z serwera") }
        assertThat(copy.text!!.markdown).isEqualTo("nowsza na serwerze")
        assertThat(knownVersion(noteId)).isEqualTo(8)
        assertThat(queue.all().none { it.noteId == noteId }).isTrue()
    }

    @Test
    fun `bez pelnego pobrania wpis o nieznanej podstawie czeka w kolejce`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.failFetch = true

        sync()

        // Zerwane pobranie: kolejka czeka, nie wysyła w ciemno — i nie liczy
        // tego jako nieudanej próby, bo to nie wina notatki.
        assertThat(transport.sentNotes).isEmpty()
        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.all().single().failedAttempts).isEqualTo(0)

        // Sieć wróciła: pobranie przechodzi i wpis jedzie normalnie.
        transport.failFetch = false
        sync()
        assertThat(transport.sentNotes.single().id).isEqualTo(noteId)
        assertThat(queue.size()).isEqualTo(0)
    }

    // --- Utknięte wpisy ---

    @Test
    fun `po wyczerpaniu prob wpis zostaje jako utkniety i widac go w stanie`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Error("serwer odmawia", worthRetrying = false)
        }

        repeat(QueueEntry.MAX_ATTEMPTS) { sync() }

        assertThat(queue.size()).isEqualTo(1)
        assertThat(queue.stuckCount()).isEqualTo(1)
        assertThat(sync.stuck.value).isEqualTo(1)

        // Utknięty wpis nie dobija się już do serwera.
        transport.sentNotes.clear()
        sync()
        assertThat(transport.sentNotes).isEmpty()

        // Ponowienie daje nową pulę prób.
        queue.retryStuck()
        assertThat(queue.stuckCount()).isEqualTo(0)
        sync()
        assertThat(transport.sentNotes).hasSize(1)
    }

    @Test
    fun `swiezy zapis czlowieka odwiesza utknieta notatke`() {
        library.notes[path] = document()
        queue.add(path, noteId)
        transport.onSendNote = {
            CloudClient.Result.Error("serwer odmawia", worthRetrying = false)
        }
        repeat(QueueEntry.MAX_ATTEMPTS) { sync() }
        assertThat(queue.stuckCount()).isEqualTo(1)

        // Człowiek zapisał notatkę jeszcze raz — wpis dostaje pełną pulę prób.
        queue.add(path, noteId)
        assertThat(queue.stuckCount()).isEqualTo(0)
    }
}
