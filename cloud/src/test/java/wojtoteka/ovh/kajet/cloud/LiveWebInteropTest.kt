package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.TextContent
import wojtoteka.ovh.kajet.storage.LiveStatus
import wojtoteka.ovh.kajet.storage.NoteCodec
import wojtoteka.ovh.kajet.storage.SharedNotes
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Aplikacja i strona na tej samej notatce w tej samej chwili.
 *
 * Druga strona to przeglądarka sterowana skryptem (Playwright) - umawiają się
 * plikami w katalogu KAJET_INTEROP_DIR: test zakłada notatki i zapisuje ich
 * numery, przeglądarka pisze i rysuje na stronie, a test sprawdza, że zmiany
 * doszły do „tabletu" i odwrotnie. Bez zmiennych środowiska test jest pomijany.
 */
class LiveWebInteropTest {

    private val server: String? = System.getenv("KAJET_LIVE_SERVER")
    private val token: String? = System.getenv("KAJET_LIVE_TOKEN")
    private val exchange: String? = System.getenv("KAJET_INTEROP_DIR")

    private class Screen(initial: JsonObject) : LiveSession.LiveDocument {
        @Volatile
        var content: JsonObject = initial

        override suspend fun snapshot() = LiveSession.Snapshot(content, content)

        override suspend fun replace(token: Any, merged: JsonObject, author: String): Boolean = synchronized(this) {
            if (content !== token) return false
            content = merged
            true
        }

        override fun normalize(content: JsonObject): JsonObject = SyncBase.normalize(content)

        override fun gone() = Unit

        fun document(): NoteDocument =
            NoteCodec.json.decodeFromJsonElement(NoteDocument.serializer(), content)

        fun change(transform: (NoteDocument) -> NoteDocument) = synchronized(this) {
            content = SyncBase.normalize(transform(document()))
        }
    }

    private fun create(document: NoteDocument) {
        val body = buildJsonObject {
            put("id", document.id)
            put("title", document.title)
            put("kind", document.kind.name)
            put("content", NoteCodec.encodeNote(document))
            put("baseVersion", 0)
        }.toString()
        val connection = URL("$server/api/v1/notes").openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        assertThat(connection.inputStream.bufferedReader().readText()).contains("created")
    }

    private fun session(noteId: String, screen: Screen): LiveSession {
        val api = LiveApi({ server!! }, { token }, "tablet-" + UUID.randomUUID().toString().take(6))
        return LiveSession(
            api = api,
            noteId = noteId,
            shareToken = null,
            document = screen,
            start = LiveSession.Start(base = screen.content, version = 1, knownVersion = 1, pendingLocal = false),
            onSynced = { _, _ -> },
        ).also { it.start() }
    }

    private suspend fun waitFor(what: String, timeoutMs: Long = 90_000, condition: () -> Boolean) {
        try {
            withTimeout(timeoutMs) { while (!condition()) delay(150) }
        } catch (e: Exception) {
            throw AssertionError("Nie doczekałem się: $what", e)
        }
    }

    @Test
    fun `tablet i strona piszą i rysują w tej samej notatce`() = runBlocking {
        assumeTrue(server != null && token != null && exchange != null)
        val folder = File(exchange!!).apply { mkdirs() }

        val text = SharedNotes.emptyDocument(UUID.randomUUID().toString(), NoteKind.TEXT, "Strona i tablet")
            .copy(text = TextContent(markdown = "Wspólny akapit\n"))
        val ink = SharedNotes.emptyDocument(UUID.randomUUID().toString(), NoteKind.HANDWRITTEN, "Kartka wspólna")
        create(text)
        create(ink)

        val textScreen = Screen(SyncBase.normalize(text))
        val inkScreen = Screen(SyncBase.normalize(ink))
        val textLive = session(text.id, textScreen)
        val inkLive = session(ink.id, inkScreen)
        waitFor("połączenia") {
            textLive.status.value == LiveStatus.LIVE && inkLive.status.value == LiveStatus.LIVE
        }
        File(folder, "notes.json").writeText("""{"text":"${text.id}","ink":"${ink.id}"}""")

        // --- Tekst ---
        waitFor("tekstu ze strony na tablecie") {
            textScreen.document().text?.markdown?.contains("Z PRZEGLADARKI") == true
        }
        println("Tablet widzi: ${textScreen.document().text?.markdown}")
        textScreen.change { it.copy(text = it.text!!.copy(markdown = it.text!!.markdown.trimEnd() + "\n\nZ TABLETU\n")) }
        textLive.localChanged()
        waitFor("potwierdzenia od strony (tekst)") { File(folder, "web-saw-text").exists() }

        // --- Pismo odręczne ---
        waitFor("kreski ze strony na tablecie") {
            inkScreen.document().handwriting?.pages?.firstOrNull()?.strokes?.isNotEmpty() == true
        }
        val fromWeb = inkScreen.document().handwriting!!.pages.first().strokes
        println("Tablet widzi kresek ze strony: ${fromWeb.size}")
        inkScreen.change { document ->
            val page = document.handwriting!!.pages.first()
            val stroke = InkStroke(
                id = "z-tabletu",
                color = 0xFF2850A0.toInt(),
                size = 3f,
                points = listOf(
                    100f, 600f, 0f, 0.5f, 0f, 0f,
                    300f, 650f, 10f, 0.5f, 0f, 0f,
                    450f, 600f, 20f, 0.5f, 0f, 0f,
                ),
            )
            document.copy(
                handwriting = document.handwriting!!.copy(
                    pages = listOf(page.copy(strokes = page.strokes + stroke)),
                ),
            )
        }
        inkLive.localChanged()
        waitFor("potwierdzenia od strony (kreska)") { File(folder, "web-saw-ink").exists() }

        // Serwer ma obie kreski - tę ze strony i tę z tabletu.
        val state = LiveApi({ server!! }, { token }, "odczyt").state(ink.id, null)
        val pages = LiveApi.json.parseToJsonElement(state.content).jsonObject["handwriting"]!!
            .jsonObject["pages"]!!.jsonArray
        val ids = pages.first().jsonObject["strokes"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertThat(ids).contains("z-tabletu")
        assertThat(ids.size).isAtLeast(2)

        textLive.close()
        inkLive.close()
    }
}
