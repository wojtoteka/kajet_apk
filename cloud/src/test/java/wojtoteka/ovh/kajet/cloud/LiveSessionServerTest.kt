package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import wojtoteka.ovh.kajet.core.live.LiveMerge
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.storage.LiveStatus
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.SharedNotes
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Edycja na żywo na prawdziwym serwerze: dwa „urządzenia" z tą samą notatką.
 *
 * Test chodzi tylko wtedy, gdy wskaże mu się serwer:
 *
 *     KAJET_LIVE_SERVER=http://localhost:9081 KAJET_LIVE_TOKEN=<token aplikacji> \
 *         ./gradlew :cloud:testDebugUnitTest --tests '*LiveSessionServerTest*'
 *
 * Bez tych zmiennych jest pomijany - zwykłe `./gradlew test` nie potrzebuje
 * serwera.
 */
class LiveSessionServerTest {

    private val server: String? = System.getenv("KAJET_LIVE_SERVER")
    private val token: String? = System.getenv("KAJET_LIVE_TOKEN")

    /** Ekran urządzenia: treść notatki w pamięci, jak w edytorze. */
    private class Screen(initial: JsonObject) : LiveSession.LiveDocument {
        @Volatile
        var content: JsonObject = initial

        @Volatile
        var gone = false

        override suspend fun snapshot() = LiveSession.Snapshot(content, content)

        override suspend fun replace(token: Any, merged: JsonObject, author: String): Boolean {
            synchronized(this) {
                if (content !== token) return false
                content = merged
                return true
            }
        }

        override fun normalize(content: JsonObject): JsonObject = SyncBase.normalize(content)

        override fun gone() {
            gone = true
        }

        fun edit(transform: (String) -> String) {
            synchronized(this) {
                val text = content["text"]!!.jsonObject
                val markdown = text["markdown"]!!.jsonPrimitive.content
                content = JsonObject(
                    content + ("text" to JsonObject(text + ("markdown" to kotlinx.serialization.json.JsonPrimitive(transform(markdown))))),
                )
            }
        }

        val markdown: String get() = content["text"]!!.jsonObject["markdown"]!!.jsonPrimitive.content
    }

    private class Device(val screen: Screen, val session: LiveSession) {
        @Volatile
        var synced: Pair<Int, JsonObject>? = null
    }

    private fun device(
        noteId: String,
        name: String,
        screen: Screen,
        start: LiveSession.Start,
    ): Device {
        val api = LiveApi(baseUrl = { server!! }, token = { token }, clientId = "test-$name-${UUID.randomUUID().toString().take(6)}")
        lateinit var device: Device
        val session = LiveSession(
            api = api,
            noteId = noteId,
            shareToken = null,
            document = screen,
            start = start,
            onSynced = { version, base -> device.synced = version to base },
        )
        device = Device(screen, session)
        session.start()
        return device
    }

    private fun createNote(markdown: String): Pair<String, JsonObject> {
        val id = UUID.randomUUID().toString()
        val document = SharedNotes.emptyDocument(id, NoteKind.TEXT, "Na żywo")
            .let { it.copy(text = TextContent(markdown = markdown)) }
        val body = buildJsonObject {
            put("id", id)
            put("title", document.title)
            put("kind", "TEXT")
            put("content", NoteCodec.encodeNote(document))
            put("baseVersion", 0)
        }.toString()
        val connection = URL("$server/api/v1/notes").openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        val answer = connection.inputStream.bufferedReader().readText()
        assertThat(answer).contains("\"created\"")
        return id to SyncBase.normalize(document)
    }

    private suspend fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        try {
            withTimeout(timeoutMs) {
                while (!condition()) delay(100)
            }
        } catch (e: Exception) {
            throw AssertionError("Nie doczekałem się: $what", e)
        }
    }

    @Test
    fun `dwa urządzenia piszą na żywo, także naraz i po pracy bez sieci`() = runBlocking {
        assumeTrue(server != null && token != null)
        val (noteId, initial) = createNote("Pierwszy akapit\nDrugi akapit\n")
        val start = LiveSession.Start(base = initial, version = 1, knownVersion = 1, pendingLocal = false)

        val tablet = device(noteId, "tablet", Screen(initial), start)
        val phone = device(noteId, "telefon", Screen(initial), start)
        waitFor("połączenia obu") {
            tablet.session.status.value == LiveStatus.LIVE && phone.session.status.value == LiveStatus.LIVE
        }
        waitFor("obecności") { tablet.session.people.value.isNotEmpty() && phone.session.people.value.isNotEmpty() }

        // 1. Tablet dopisuje - telefon widzi.
        tablet.screen.edit { it + "Z tabletu\n" }
        tablet.session.localChanged()
        waitFor("zmiany z tabletu na telefonie") { phone.screen.markdown.contains("Z tabletu") }

        // 2. Telefon poprawia pierwszy akapit - tablet widzi.
        phone.screen.edit { it.replace("Pierwszy akapit", "Pierwszy akapit, poprawiony") }
        phone.session.localChanged()
        waitFor("zmiany z telefonu na tablecie") { tablet.screen.markdown.contains("poprawiony") }

        // 3. Oboje naraz, w różnych miejscach.
        tablet.screen.edit { it + "Tablet naraz\n" }
        phone.screen.edit { "Telefon naraz\n" + it }
        tablet.session.localChanged()
        phone.session.localChanged()
        waitFor("zbieżności po pisaniu naraz") {
            tablet.screen.markdown == phone.screen.markdown &&
                tablet.screen.markdown.contains("Tablet naraz") &&
                tablet.screen.markdown.contains("Telefon naraz")
        }
        println("Po pisaniu naraz:\n${tablet.screen.markdown}")

        // 4. Telefon traci sieć (sesja zamknięta), oba piszą dalej.
        waitFor("potwierdzenia telefonu") { phone.synced != null }
        delay(500)
        val (phoneVersion, phoneBase) = phone.synced!!
        phone.session.close()
        tablet.screen.edit { it + "Tablet, gdy telefon był bez sieci\n" }
        tablet.session.localChanged()
        phone.screen.edit { it.replace("Drugi akapit", "Drugi akapit, pisany bez sieci") }
        waitFor("zapisu tabletu na serwerze") {
            tablet.synced?.second?.let { base ->
                base["text"]!!.jsonObject["markdown"]!!.jsonPrimitive.content.contains("bez sieci")
            } == true
        }

        // Sieć wraca: nowa sesja od starej bazy, z czekającą zmianą.
        val back = device(
            noteId,
            "telefon",
            phone.screen,
            LiveSession.Start(base = phoneBase, version = phoneVersion, knownVersion = phoneVersion, pendingLocal = true),
        )
        waitFor("scalenia po powrocie sieci") {
            tablet.screen.markdown == back.screen.markdown &&
                back.screen.markdown.contains("gdy telefon był bez sieci") &&
                back.screen.markdown.contains("pisany bez sieci")
        }
        println("Po powrocie sieci:\n${back.screen.markdown}")

        // Serwer ma to samo, co oba ekrany.
        val state = LiveApi({ server!! }, { token }, "test-odczyt").state(noteId, null)
        val onServer = SyncBase.normalize(LiveApi.json.parseToJsonElement(state.content).jsonObject)
        assertThat(LiveMerge.jsonEqual(onServer["text"], back.screen.content["text"])).isTrue()

        tablet.session.close()
        back.session.close()
    }
}
