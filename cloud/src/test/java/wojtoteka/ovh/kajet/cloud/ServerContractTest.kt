package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * Contract tests against the server in D:\Inne\kajet_server.
 *
 * Each JSON below is written exactly the way the corresponding route in
 * src/app/api/v1 builds it. When one of these tests goes red after a server
 * change, the tablet and the server have stopped speaking the same language -
 * fix the models or the route, not the test, unless the change was deliberate
 * on both sides.
 */
class ServerContractTest {

    private val json = CloudClient.json

    // --- GET /api/v1/notes ---

    @Test
    fun `parses a page of changes`() {
        val body = """
            {"notes":[{"id":"n1","title":"Fizyka","kind":"HANDWRITTEN","favorite":false,
            "tags":"szkola|fizyka","version":3,"hash":"abc123","sizeBytes":2048,
            "updatedAt":1722700000000,"deletedAt":null,"folderId":null,
            "content":"{\"id\":\"n1\"}",
            "attachments":[{"name":"zdjecie-1.png","mime":"image/png","sizeBytes":10,"hash":"h1"}]}],
            "upTo":1722700000000,"upToId":"n1","hasMore":true}
        """.trimIndent()

        val page = json.decodeFromString<ChangesResponse>(body)

        assertThat(page.notes).hasSize(1)
        val note = page.notes.first()
        assertThat(note.id).isEqualTo("n1")
        assertThat(note.version).isEqualTo(3)
        assertThat(note.deletedAt).isNull()
        assertThat(note.content).isEqualTo("{\"id\":\"n1\"}")
        assertThat(note.attachments.single().name).isEqualTo("zdjecie-1.png")
        assertThat(page.upTo).isEqualTo(1722700000000L)
        assertThat(page.upToId).isEqualTo("n1")
        assertThat(page.hasMore).isTrue()
    }

    @Test
    fun `parses a deleted note and an empty page`() {
        val deleted = """
            {"notes":[{"id":"n2","title":"","kind":"TEXT","favorite":false,"tags":"",
            "version":9,"hash":"","sizeBytes":0,"updatedAt":1722700000001,
            "deletedAt":1722700000002,"folderId":"f1","content":"{}","attachments":[]}],
            "upTo":1722700000001,"upToId":"n2","hasMore":false}
        """.trimIndent()
        val empty = """{"notes":[],"upTo":0,"upToId":null,"hasMore":false}"""

        assertThat(json.decodeFromString<ChangesResponse>(deleted).notes.single().deletedAt)
            .isEqualTo(1722700000002L)
        val page = json.decodeFromString<ChangesResponse>(empty)
        assertThat(page.notes).isEmpty()
        assertThat(page.upToId).isNull()
        assertThat(page.hasMore).isFalse()
    }

    // --- GET /api/v1/sync/deleted ---
    //
    // Nagrobki po notatkach skasowanych na zawsze. Zwykłe pobranie zmian tego
    // nie powie: wiersza skasowanej notatki nie ma, więc „co się zmieniło od…"
    // nigdy jej nie wymieni.

    @Test
    fun `parses a page of tombstones`() {
        val body = """
            {"ids":["n1","n2","n3"],"upTo":1722700000005,"upToId":"n3","hasMore":true}
        """.trimIndent()

        val page = json.decodeFromString<DeletedResponse>(body)

        assertThat(page.ids).containsExactly("n1", "n2", "n3").inOrder()
        assertThat(page.upTo).isEqualTo(1722700000005L)
        assertThat(page.upToId).isEqualTo("n3")
        assertThat(page.hasMore).isTrue()
    }

    @Test
    fun `parses an empty page of tombstones`() {
        val page = json.decodeFromString<DeletedResponse>(
            """{"ids":[],"upTo":0,"upToId":null,"hasMore":false}""",
        )

        assertThat(page.ids).isEmpty()
        assertThat(page.upTo).isEqualTo(0L)
        assertThat(page.upToId).isNull()
        assertThat(page.hasMore).isFalse()
    }

    // --- PUT /api/v1/notes ---

    @Test
    fun `parses saved, created and unchanged answers`() {
        val saved = """{"status":"saved","version":4,"updatedAt":1722700000000}"""
        val created = """{"status":"created","version":1,"updatedAt":1722700000000}"""
        val unchanged = """{"status":"unchanged","version":4}"""

        assertThat(json.decodeFromString<SaveResponse>(saved).version).isEqualTo(4)
        assertThat(json.decodeFromString<SaveResponse>(created).status).isEqualTo("created")
        assertThat(json.decodeFromString<SaveResponse>(unchanged).version).isEqualTo(4)
    }

    @Test
    fun `parses a conflict with the server copy`() {
        // note-write.ts: status + message + onServer with the narrow select.
        val body = """
            {"status":"conflict",
            "message":"Ta notatka zmieniła się także gdzie indziej.",
            "onServer":{"id":"n1","title":"Fizyka","kind":"HANDWRITTEN","favorite":true,
            "tags":"szkola","content":"{\"id\":\"n1\"}","version":7,"updatedAt":1722700000000}}
        """.trimIndent()

        val response = json.decodeFromString<SaveResponse>(body)

        assertThat(response.status).isEqualTo("conflict")
        val onServer = response.onServer
        assertThat(onServer).isNotNull()
        // Sync remembers this version after saving the copy alongside (point 10
        // in donaprawy.md); without it the next fetch overwrites local edits.
        assertThat(onServer!!.version).isEqualTo(7)
        assertThat(onServer.content).isEqualTo("{\"id\":\"n1\"}")
        // The conflict answer has no attachments list; Sync then asks the
        // attachments route instead.
        assertThat(onServer.attachments).isEmpty()
    }

    // --- POST /api/v1/signin and /api/v1/signin/device ---

    @Test
    fun `parses a password sign-in answer`() {
        // BigInt quotas travel as text, api.ts json().
        val body = """
            {"token":"tajny","tokenId":"tok-1",
            "account":{"id":"u1","login":"wojtek","email":"free@wojtoteka.ovh","name":null,"admin":true},
            "storage":{"quota":"524288000","used":"1234","unlimited":false}}
        """.trimIndent()

        val response = json.decodeFromString<SignInResponse>(body)

        assertThat(response.token).isEqualTo("tajny")
        assertThat(response.account.admin).isTrue()
        assertThat(response.storage.quotaBytes).isEqualTo(524_288_000L)
        assertThat(response.storage.usedBytes).isEqualTo(1234L)
    }

    @Test
    fun `parses the device challenge and both poll answers`() {
        val challenge = """
            {"code":"abc","verificationUri":"https://kajet.wojtoteka.ovh/signin/device?code=abc",
            "expiresIn":600,"interval":2}
        """.trimIndent()
        val pending = """{"status":"pending"}"""
        val ready = """
            {"status":"ready","token":"tajny","tokenId":"tok-1",
            "account":{"id":"u1","login":"wojtek","email":"free@wojtoteka.ovh","name":null,"admin":false},
            "storage":{"quota":"0","used":"0","unlimited":true}}
        """.trimIndent()

        assertThat(json.decodeFromString<DeviceChallenge>(challenge).interval).isEqualTo(2)
        assertThat(json.decodeFromString<DevicePollResponse>(pending).toSignIn()).isNull()
        val signIn = json.decodeFromString<DevicePollResponse>(ready).toSignIn()
        assertThat(signIn).isNotNull()
        assertThat(signIn!!.storage.unlimited).isTrue()
    }

    // --- GET /api/v1/account ---

    @Test
    fun `parses the account state`() {
        val body = """
            {"account":{"id":"u1","login":"wojtek","email":"free@wojtoteka.ovh","name":"Wojtek","admin":false},
            "storage":{"quota":"524288000","used":"1234","free":"524286766","unlimited":false,"quotaUntil":null},
            "noteCount":12}
        """.trimIndent()

        val state = json.decodeFromString<AccountState>(body)

        assertThat(state.noteCount).isEqualTo(12)
        assertThat(state.storage.free).isEqualTo("524286766")
        assertThat(state.storage.quotaUntil).isNull()
    }

    // --- Attachments ---

    @Test
    fun `parses the attachment list and upload answer`() {
        val list = """{"attachments":[{"name":"zdjecie-1.png","mime":"image/png","sizeBytes":10,"hash":"h1"}]}"""
        val uploaded = """{"name":"zdjecie-1.png","hash":"h1","sizeBytes":10,
            "url":"/api/v1/notes/n1/attachments?name=zdjecie-1.png"}"""

        assertThat(json.decodeFromString<AttachmentsResponse>(list).attachments.single().hash)
            .isEqualTo("h1")
        assertThat(json.decodeFromString<AttachmentResponse>(uploaded).sizeBytes).isEqualTo(10)
    }

    // --- POST /api/v1/code ---

    @Test
    fun `parses a code run result`() {
        val body = """{"output":"4\n","errors":"","exitCode":0,"interrupted":false,"timeMs":153}"""

        val result = json.decodeFromString<CodeResult>(body)

        assertThat(result.output).isEqualTo("4\n")
        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.interrupted).isFalse()
    }

    @Test
    fun `pusty kod wyjscia zostaje pusty`() {
        /*
          exitCode przychodzi jako liczba ALBO null i null zdarza się w dwóch
          sytuacjach: program przerwano po limicie czasu, albo wypisał więcej,
          niż wolno pokazać (wynik jest wtedy ucięty i kończy się zdaniem
          o ucięciu).

          Zera tu podstawić nie wolno: zero znaczy „skończył się dobrze".
          Kiedyś w tym polu stawał napis ERR_CHILD_PROCESS_STDIO_MAXBUFFER
          udający liczbę - serwer już go nie odsyła i aplikacja nigdzie go nie
          rozbiera.
        */
        val przerwany =
            """{"output":"","errors":"Program działał dłużej niż 10 s i został przerwany.",
            "exitCode":null,"interrupted":true,"timeMs":10004}"""
        val uciety =
            """{"output":"1\n2\n\n\n[...] Wynik był dłuższy niż 20000 znaków i reszta została ucięta.",
            "errors":"","exitCode":null,"interrupted":false,"timeMs":812}"""

        assertThat(json.decodeFromString<CodeResult>(przerwany).exitCode).isNull()
        assertThat(json.decodeFromString<CodeResult>(przerwany).interrupted).isTrue()
        assertThat(json.decodeFromString<CodeResult>(uciety).exitCode).isNull()
        assertThat(json.decodeFromString<CodeResult>(uciety).interrupted).isFalse()
    }

    @Test
    fun `niezerowy kod wyjscia przy pustym stderr to nie awaria serwera`() {
        /*
          Program, który zwraca 1 i nic nie wypisuje na wyjście błędów, jest
          programem DZIAŁAJĄCYM. Serwer nie dokłada już do tego zdania
          o awarii uruchamiania: errors jest po prostu puste, a numer stoi
          w exitCode. Aplikacja nie ma prawa robić z tego usterki.
        */
        val body = """{"output":"","errors":"","exitCode":1,"interrupted":false,"timeMs":91}"""

        val result = json.decodeFromString<CodeResult>(body)

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.errors).isEmpty()
        assertThat(result.interrupted).isFalse()
    }

    @Test
    fun `parses a conflict with a note lying in the server bin`() {
        // note-write.ts adds deletedAt to onServer; Sync uses it to restore the
        // note instead of saving a copy of the bin alongside.
        val body = """
            {"status":"conflict",
            "message":"Ta notatka zmieniła się także gdzie indziej.",
            "onServer":{"id":"n1","title":"Fizyka","kind":"TEXT","favorite":false,
            "tags":"","content":"{}","version":9,"updatedAt":1722700000000,
            "deletedAt":1722700000001}}
        """.trimIndent()

        val response = json.decodeFromString<SaveResponse>(body)

        assertThat(response.onServer!!.deletedAt).isEqualTo(1722700000001L)
    }

    @Test
    fun `parses a CODE note from the changes feed`() {
        // GET /api/v1/notes?kinds=all returns code files as CODE notes.
        val body = """
            {"notes":[{"id":"c1","title":"program.py","kind":"CODE","favorite":false,
            "tags":"","version":2,"hash":"h","sizeBytes":64,"updatedAt":1722700000000,
            "deletedAt":null,"folderId":null,
            "content":"{\"format\":1,\"kind\":\"code\",\"code\":{\"language\":\"python\",\"source\":\"print(1)\"}}",
            "attachments":[]}],
            "upTo":1722700000000,"upToId":"c1","hasMore":false}
        """.trimIndent()

        val note = json.decodeFromString<ChangesResponse>(body).notes.single()

        assertThat(note.kind).isEqualTo("CODE")
        assertThat(note.content).contains("print(1)")
    }

    // --- What the tablet sends: PUT /api/v1/notes body ---

    @Test
    fun `encodes an outgoing note the way the server schema expects`() {
        val note = OutgoingNote(
            id = "n1",
            title = "Fizyka",
            kind = "HANDWRITTEN",
            favorite = true,
            tags = listOf("szkola", "fizyka"),
            content = "{\"id\":\"n1\"}",
            baseVersion = 3,
        )

        val encoded = json.encodeToString(OutgoingNote.serializer(), note)
        val fields = json.parseToJsonElement(encoded).jsonObject

        // outgoingNoteSchema in note-write.ts: id/title/kind/content/baseVersion
        // required, tags as an array. folderId ABSENT when null - „zostaw
        // notatkę tam, gdzie jest" (np. serwer bez obsługi folderów).
        assertThat(fields.keys).containsAtLeast("id", "title", "kind", "content", "baseVersion")
        assertThat(fields.keys).doesNotContain("folderId")
        assertThat(fields["tags"].toString()).isEqualTo("""["szkola","fizyka"]""")
        assertThat(fields["kind"].toString()).isEqualTo("\"HANDWRITTEN\"")
    }

    @Test
    fun `encodes the folder field the way folder sync expects`() {
        // Od synchronizacji folderów: notatka w folderze niesie jego
        // identyfikator, notatka w korzeniu pusty tekst (serializator gubi
        // null, a jawny korzeń musi jakoś jechać - serwer mapuje "" na null).
        val inFolder = OutgoingNote(
            id = "n1", title = "Fizyka", kind = "TEXT",
            folderId = "f-123", content = "{}", baseVersion = 1,
        )
        val atRoot = inFolder.copy(folderId = "")

        val folderFields = json.parseToJsonElement(
            json.encodeToString(OutgoingNote.serializer(), inFolder),
        ).jsonObject
        val rootFields = json.parseToJsonElement(
            json.encodeToString(OutgoingNote.serializer(), atRoot),
        ).jsonObject

        assertThat(folderFields["folderId"].toString()).isEqualTo("\"f-123\"")
        assertThat(rootFields["folderId"].toString()).isEqualTo("\"\"")
    }

    @Test
    fun `decodes the folder listing the server returns`() {
        // GET /api/v1/folders - kształt z src/app/api/v1/folders/route.ts.
        val response = json.decodeFromString(
            FoldersResponse.serializer(),
            """{"folders":[
                {"id":"f1","parentId":null,"name":"Szkoła","colorId":"morski",
                 "iconId":"folder","updatedAt":1722700000000},
                {"id":"f2","parentId":"f1","name":"Fizyka","colorId":"grafit",
                 "iconId":"folder","updatedAt":1722700000001}
            ]}""",
        )

        assertThat(response.folders).hasSize(2)
        assertThat(response.folders[0].parentId).isNull()
        assertThat(response.folders[1].parentId).isEqualTo("f1")
        assertThat(response.folders[1].name).isEqualTo("Fizyka")
    }

    @Test
    fun `encodes a tombstone the way the server schema expects`() {
        // Sync sends this when a note lands in the bin: empty content, the
        // deleted flag on. outgoingNoteSchema makes content optional only for
        // tombstones, so the empty string keeps old servers happy too.
        val tombstone = OutgoingNote(
            id = "n1",
            title = "",
            kind = "TEXT",
            content = "",
            baseVersion = 3,
            deleted = true,
        )

        val fields = json.parseToJsonElement(
            json.encodeToString(OutgoingNote.serializer(), tombstone),
        ).jsonObject

        assertThat(fields["deleted"].toString()).isEqualTo("true")
        assertThat(fields["content"].toString()).isEqualTo("\"\"")
        assertThat(fields.keys).doesNotContain("folderId")
    }

    // --- POST /api/v1/crash ---

    @Test
    fun `encodes a crash report the way crashBody expects`() {
        val body = CrashReporter.bodyOf(
            report = "Kajet 1.0 (2)\nWątek: main\n\njava.lang.IllegalStateException: pękło",
            appVersion = "1.0",
            versionCode = 2,
            device = "LENOVO TB520FU",
            android = "16",
            thread = "main",
        )

        val fields = json.parseToJsonElement(body).jsonObject

        assertThat(fields.keys).containsExactly(
            "report", "appVersion", "versionCode", "device", "android", "thread",
        )
        assertThat(fields["versionCode"].toString()).isEqualTo("2")
        assertThat(fields["device"].toString()).isEqualTo("\"LENOVO TB520FU\"")
    }

    @Test
    fun `leaves out fields it could not read from the header`() {
        // Raport bez czytelnego nagłówka. Serwer ma wszystkie pola poza
        // `report` jako nieobowiązkowe, więc puste po prostu wypadają - nie
        // idą jako null, bo explicitNulls jest wyłączone.
        val body = CrashReporter.bodyOf(report = "java.lang.OutOfMemoryError")

        val fields = json.parseToJsonElement(body).jsonObject

        assertThat(fields.keys).containsExactly("report")
    }

    // --- GET /api/v1/app/latest ---

    @Test
    fun `parses the newest release`() {
        // Dokładnie to, co składa src/app/api/v1/app/latest/route.ts.
        val body = """
            {"release":{"version":"26.08.02","versionCode":5,"notes":"Poprawki synchronizacji.",
            "fileName":"app-release.apk","sizeBytes":34371499,"hash":"7bb54fc6",
            "url":"https://kajet.wojtoteka.ovh/download/file",
            "pageUrl":"https://kajet.wojtoteka.ovh/download",
            "publishedAt":1786128038965,"releaseDate":"2026-08-02","minSupportedRelease":0}}
        """.trimIndent()

        val release = json.decodeFromString<Answer>(body).release

        assertThat(release).isNotNull()
        assertThat(release!!.version).isEqualTo("26.08.02")
        assertThat(release.versionCode).isEqualTo(5)
        assertThat(release.pageUrl).isEqualTo("https://kajet.wojtoteka.ovh/download")
        assertThat(release.releaseDate).isEqualTo("2026-08-02")
        assertThat(release.minSupportedRelease).isEqualTo(0)
    }

    @Test
    fun `takes a server with no release put up yet`() {
        val release = json.decodeFromString<Answer>("""{"release":null}""").release

        assertThat(release).isNull()
    }

    @Test
    fun `takes a release without notes and without the new fields`() {
        // Starszy serwer: bez releaseDate i bez minSupportedRelease. Aplikacja
        // ma go zrozumieć, bo wdrożenie idzie serwer-najpierw, ale kolejność
        // da się odwrócić przez pomyłkę.
        val body = """
            {"release":{"version":"26.08.01","versionCode":4,"notes":null,
            "pageUrl":"https://kajet.wojtoteka.ovh/download"}}
        """.trimIndent()

        val release = json.decodeFromString<Answer>(body).release

        assertThat(release).isNotNull()
        assertThat(release!!.notes).isNull()
        assertThat(release.releaseDate).isNull()
        assertThat(release.minSupportedRelease).isEqualTo(0)
    }

    /** Kształt odpowiedzi z `app/latest`. Prywatny w [UpdateCheck], więc powtórzony. */
    @kotlinx.serialization.Serializable
    private data class Answer(val release: UpdateCheck.Release? = null)
}
