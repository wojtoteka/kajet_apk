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
 * change, the tablet and the server have stopped speaking the same language —
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
        // required, tags as an array, folderId absent (not null!) so the server
        // leaves the web folder alone (point 9 in donaprawy.md).
        assertThat(fields.keys).containsAtLeast("id", "title", "kind", "content", "baseVersion")
        assertThat(fields.keys).doesNotContain("folderId")
        assertThat(fields["tags"].toString()).isEqualTo("""["szkola","fizyka"]""")
        assertThat(fields["kind"].toString()).isEqualTo("\"HANDWRITTEN\"")
    }
}
