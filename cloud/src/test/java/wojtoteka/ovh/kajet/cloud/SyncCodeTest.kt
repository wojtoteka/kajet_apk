package wojtoteka.ovh.kajet.cloud

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Co synchronizacja robi z PLIKIEM Z KODEM.
 *
 * Plik z kodem to na dysku zwykły tekst bez identyfikatora — na serwerze żyje
 * jako notatka CODE, a jedno z drugim skleja [CodeFileIds]. Rozjazd w tym
 * rejestrze nie kończy się komunikatem, tylko duplikatem w bibliotece albo
 * wskrzeszonym plikiem, którego ktoś skasował na drugim urządzeniu. Dlatego
 * te testy patrzą na PLIK, KOLEJKĘ i ZAPAMIĘTANĄ WERSJĘ, a nie na to, czy
 * odpowiedź serwera dała się odczytać — od tego jest [ServerContractTest].
 */
@RunWith(RobolectricTestRunner::class)
class SyncCodeTest {

    private lateinit var context: Context
    private lateinit var library: FakeLibrary
    private lateinit var transport: FakeTransport
    private lateinit var account: FakeAccount
    private lateinit var queue: SendQueue
    private lateinit var codeIds: CodeFileIds
    private lateinit var sync: Sync

    private val path = "szkola/skrypt.py"

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

    /** Notatka CODE z serwera — treść w kształcie, który rozumie strona. */
    private fun serverCode(
        id: String,
        title: String = "skrypt.py",
        version: Int = 4,
        source: String = "print(2)",
    ) = ServerNote(
        id = id,
        title = title,
        kind = "CODE",
        version = version,
        updatedAt = 5_000,
        content = """{"format":1,"id":"$id","kind":"code","title":"$title",""" +
            """"tags":[],"favorite":false,"code":{"language":"python","source":"$source"}}""",
    )

    private fun knownVersion(id: String): Int =
        context.getSharedPreferences("kajet-versions", Context.MODE_PRIVATE).getInt(id, 0)

    private fun sync(): SyncResult = runBlocking { sync.synchronise() }

    // --- Wysyłka ---

    @Test
    fun `wyslany plik z kodem jedzie jako notatka CODE i schodzi z kolejki`() {
        library.texts[path] = "print(1)"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "ok", version = 3)) }

        sync()

        val sent = transport.sentNotes.single { it.id == id }
        assertThat(sent.kind).isEqualTo("CODE")
        // Tytułem notatki jest nazwa pliku — po niej strona buduje plik z powrotem.
        assertThat(sent.title).isEqualTo("skrypt.py")
        assertThat(sent.content).contains("\"source\":\"print(1)\"")
        assertThat(sent.baseVersion).isEqualTo(0)
        assertThat(knownVersion(id)).isEqualTo(3)
        assertThat(queue.size()).isEqualTo(0)
    }

    // --- Konflikt ---

    @Test
    fun `konflikt zaklada kopie obok a lokalny plik zostaje nietkniety`() {
        library.texts[path] = "moja wersja"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        transport.onSendNote = { note ->
            // Konfliktem odpowiada tylko sporny plik; kopię konfliktu wyśle
            // zaraz uzgadnianie jako nowość i ta ma przejść normalnie.
            if (note.id == id) {
                CloudClient.Result.Ok(
                    SaveResponse(
                        status = "conflict",
                        onServer = serverCode(id, version = 4, source = "wersja z serwera"),
                    ),
                )
            } else {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
            }
        }

        val result = sync()

        assertThat(result.conflicts).isEqualTo(1)
        // Plik pod palcem nietknięty, treść z serwera leży OBOK, w tym folderze.
        assertThat(library.texts[path]).isEqualTo("moja wersja")
        val copy = library.texts.keys.single { it != path }
        assertThat(copy).startsWith("szkola/skrypt (kopia z chmury")
        assertThat(copy).endsWith(".py")
        assertThat(library.texts[copy]).isEqualTo("wersja z serwera")
        // Konflikt rozliczony: wersja zapamiętana, wpis zdjęty z kolejki.
        assertThat(knownVersion(id)).isEqualTo(4)
        assertThat(queue.all().none { it.path == path }).isTrue()
    }

    @Test
    fun `kopia konfliktu niesie znacznik czasu bez dwukropka`() {
        library.texts[path] = "moja wersja"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        transport.onSendNote = { note ->
            if (note.id == id) {
                CloudClient.Result.Ok(
                    SaveResponse(
                        status = "conflict",
                        onServer = serverCode(id, version = 4, source = "wersja z serwera"),
                    ),
                )
            } else {
                CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1))
            }
        }

        sync()

        // Znacznik jak w kopii notatki, tylko godzina z podkreślnikiem —
        // dwukropek nie przechodzi w nazwie pliku.
        val copy = library.texts.keys.single { it != path }
        assertThat(copy).contains("(kopia z chmury, ")
        assertThat(copy).doesNotContain(":")
        assertThat(copy).endsWith(".py")
    }

    @Test
    fun `nieudana kopia nie zdejmuje wpisu i nie zapamietuje wersji`() {
        library.texts[path] = "moja wersja"
        library.failWrites = true
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        transport.onSendNote = {
            CloudClient.Result.Ok(
                SaveResponse(status = "conflict", onServer = serverCode(id, version = 4)),
            )
        }

        val result = sync()

        // Bez kopii nie ma rozliczenia konfliktu — inaczej treść z serwera
        // przepadłaby po cichu, a wpis wyglądałby na załatwiony.
        assertThat(result.conflicts).isEqualTo(0)
        assertThat(knownVersion(id)).isEqualTo(0)
        assertThat(queue.all().single { it.path == path }.failedAttempts).isEqualTo(1)
    }

    // --- Konflikt z serwerowym koszem ---

    @Test
    fun `kosz serwera wygrywa przy wpisie z uzgadniania`() {
        library.texts[path] = "print(1)"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE, reconciled = true)
        transport.onSendNote = {
            CloudClient.Result.Ok(
                SaveResponse(
                    status = "conflict",
                    onServer = serverCode(id, version = 9).copy(deletedAt = 4_000),
                ),
            )
        }

        sync()

        // Plik skasowano gdzie indziej — idzie do lokalnego kosza zamiast
        // wskrzeszać się przy każdym przelogowaniu.
        assertThat(library.trashedFilePaths).containsExactly(path)
        assertThat(library.texts).isEmpty()
        assertThat(knownVersion(id)).isEqualTo(9)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `zapis czlowieka wskrzesza plik z serwerowego kosza`() {
        library.texts[path] = "pisane dalej"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        var calls = 0
        transport.onSendNote = { note ->
            calls += 1
            if (calls == 1) {
                CloudClient.Result.Ok(
                    SaveResponse(
                        status = "conflict",
                        onServer = serverCode(id, version = 9).copy(deletedAt = 4_000),
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
        assertThat(library.trashedFilePaths).isEmpty()
        assertThat(library.texts[path]).isEqualTo("pisane dalej")
        assertThat(knownVersion(id)).isEqualTo(10)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `gone wyrzuca plik do lokalnego kosza i zwalnia jego numer`() {
        library.texts[path] = "print(1)"
        val id = codeIds.idFor(path)
        queue.add(path, id, QueueEntry.KIND_CODE)
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "gone")) }

        sync()

        assertThat(library.trashedFilePaths).containsExactly(path)
        // Numer przestaje obowiązywać: nowy plik pod tą nazwą nie ma prawa
        // odziedziczyć go i wskrzesić skasowanej notatki.
        assertThat(codeIds.existingIdFor(path)).isNull()
        assertThat(knownVersion(id)).isEqualTo(0)
        assertThat(queue.size()).isEqualTo(0)
    }

    // --- Pobieranie: zerwany rejestr (reinstalacja, wyczyszczenie danych) ---

    @Test
    fun `plik o tej samej tresci wiaze sie z powrotem zamiast dublowac`() {
        library.texts["skrypt.py"] = "print(1)"
        transport.serverNotes = listOf(serverCode("c1", version = 5, source = "print(1)"))

        sync()

        // Ten sam plik, nie drugi taki sam obok.
        assertThat(library.texts.keys).containsExactly("skrypt.py")
        assertThat(codeIds.existingIdFor("skrypt.py")).isEqualTo("c1")
        assertThat(knownVersion("c1")).isEqualTo(5)
    }

    @Test
    fun `plik o innej tresci dostaje kopie konfliktu i nic nie ginie`() {
        library.texts["skrypt.py"] = "print(1)"
        transport.serverNotes = listOf(serverCode("c1", version = 5, source = "print(2)"))
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1)) }

        val result = sync()

        // Jak przy notatkach: lokalny plik nietknięty i to ON przejmuje
        // tożsamość, a treść serwera leży obok jako podpisana kopia konfliktu.
        assertThat(library.texts["skrypt.py"]).isEqualTo("print(1)")
        val copy = library.texts.keys.single { it != "skrypt.py" }
        assertThat(copy).startsWith("skrypt (kopia z chmury")
        assertThat(library.texts[copy]).isEqualTo("print(2)")
        assertThat(codeIds.existingIdFor("skrypt.py")).isEqualTo("c1")
        assertThat(knownVersion("c1")).isEqualTo(5)
        assertThat(result.conflicts).isEqualTo(1)
    }

    @Test
    fun `plik ze znanym numerem i nieznana wersja nie jest nadpisywany`() {
        library.texts[path] = "moja wersja"
        codeIds.bind(path, "c1")
        transport.serverNotes = listOf(serverCode("c1", version = 5, source = "serwerowa"))
        transport.onSendNote = { CloudClient.Result.Ok(SaveResponse(status = "ok", version = 1)) }

        val result = sync()

        // Rejestr zna plik, ale wersji nie znamy (np. plik przywrócony
        // z kosza po nagrobku) — serwer nie ma prawa po cichu nadpisać.
        assertThat(library.texts[path]).isEqualTo("moja wersja")
        val copy = library.texts.keys.single { it != path }
        assertThat(copy).startsWith("szkola/skrypt (kopia z chmury")
        assertThat(library.texts[copy]).isEqualTo("serwerowa")
        assertThat(knownVersion("c1")).isEqualTo(5)
        assertThat(result.conflicts).isEqualTo(1)
    }

    @Test
    fun `pierwsze pobranie na puste urzadzenie tworzy plik`() {
        transport.serverNotes = listOf(serverCode("c1", version = 5, source = "print(9)"))

        sync()

        // Pustego urządzenia nie ma przed czym chronić — plik po prostu
        // powstaje i od razu nosi numer oraz wersję z serwera.
        val created = library.texts.keys.single()
        assertThat(library.texts[created]).isEqualTo("print(9)")
        assertThat(codeIds.existingIdFor(created)).isEqualTo("c1")
        assertThat(knownVersion("c1")).isEqualTo(5)
    }
}
