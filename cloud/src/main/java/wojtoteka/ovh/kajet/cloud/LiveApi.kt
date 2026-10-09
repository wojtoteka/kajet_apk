package wojtoteka.ovh.kajet.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Edycja na żywo po stronie sieci: strumień zmian jednej notatki i wysyłka
 * delty. Czyste JVM, bez Androida - test idzie wprost na prawdziwy serwer.
 *
 * Strumień to Server-Sent Events: jedno długie żądanie GET, w którym serwer
 * dopisuje zdarzenia (`event:` + `data:` + pusty wiersz). Co 20 sekund leci
 * pusty komentarz - po nim poznajemy, że połączenie żyje; dłuższa cisza to
 * zerwane łącze i ponowne połączenie.
 */
class LiveApi(
    private val baseUrl: () -> String,
    /** Token konta albo null (otwarcie odnośnikiem bez logowania). */
    private val token: () -> String?,
    /** To urządzenie - po nim poznajemy echo własnych zmian. */
    val clientId: String,
) {

    sealed interface Event {
        data class Hello(val version: Int, val canEdit: Boolean, val people: List<Person>) : Event
        data class Change(val version: Int, val by: String, val name: String, val delta: JsonObject) : Event
        data class Reset(val version: Int, val content: String) : Event
        data class People(val people: List<Person>) : Event
        data object Gone : Event
    }

    @Serializable
    data class Person(
        val client: String = "",
        val name: String = "",
        val app: Boolean = false,
        val color: Int = 0,
    )

    @Serializable
    data class State(
        val id: String = "",
        val version: Int = 0,
        val content: String = "",
        val kind: String = "",
        val title: String = "",
        val canEdit: Boolean = false,
        val isOwner: Boolean = false,
    )

    sealed interface PushResult {
        data class Ok(val version: Int) : PushResult
        /** Serwer ma nowszą treść - trzeba dociągnąć zmiany i scalić. */
        data class Stale(val version: Int) : PushResult
        data object Gone : PushResult
        data object ReadOnly : PushResult
        /** Brak sieci albo kłopot serwera - spróbować później. */
        data class Failed(val status: Int?) : PushResult
    }

    class LiveException(val status: Int?, message: String) : IOException(message)

    private fun query(vararg pairs: Pair<String, String?>): String = pairs
        .filter { it.second != null }
        .joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }

    private fun open(url: String, method: String, readTimeout: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = readTimeout
        token()?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        connection.setRequestProperty("X-Kajet-Client", clientId)
        return connection
    }

    /** Pełna treść notatki z wersją - do pierwszego otwarcia bez własnej kopii. */
    fun state(noteId: String, shareToken: String?): State {
        val url = "${baseUrl()}/api/v1/live/${enc(noteId)}?" + query("state" to "1", "t" to shareToken)
        val connection = open(url, "GET", READ_TIMEOUT)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw LiveException(status, "HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString(State.serializer(), body)
        } finally {
            connection.disconnect()
        }
    }

    /** Wysyła deltę liczoną od wersji [base]. */
    fun push(noteId: String, base: Int, delta: JsonObject, shareToken: String?): PushResult {
        val url = "${baseUrl()}/api/v1/live/${enc(noteId)}" +
            (shareToken?.let { "?" + query("t" to it) } ?: "")
        val body = buildJsonObject {
            put("base", base)
            put("c", clientId)
            put("d", delta)
        }.toString()
        val connection = try {
            open(url, "POST", READ_TIMEOUT)
        } catch (e: IOException) {
            return PushResult.Failed(null)
        }
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val version = answer?.get("version")?.jsonPrimitive?.intOrNull ?: 0
            when {
                status == 410 -> PushResult.Gone
                status == 403 && answer?.get("error")?.jsonPrimitive?.content == "read-only" ->
                    PushResult.ReadOnly
                status == 404 -> PushResult.Gone
                status !in 200..299 -> PushResult.Failed(status)
                answer?.get("status")?.jsonPrimitive?.content == "ok" -> PushResult.Ok(version)
                answer?.get("status")?.jsonPrimitive?.content == "stale" -> PushResult.Stale(version)
                answer?.get("status")?.jsonPrimitive?.content == "gone" -> PushResult.Gone
                else -> PushResult.Failed(status)
            }
        } catch (e: IOException) {
            PushResult.Failed(null)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Słucha strumienia zmian od wersji [since] (0 - zacznij od pełnej treści)
     * i oddaje zdarzenia po kolei. Wraca, gdy serwer zamknie strumień albo
     * [stillWanted] powie „dość"; zerwane łącze kończy się wyjątkiem.
     */
    fun listen(
        noteId: String,
        since: Int,
        shareToken: String?,
        stillWanted: () -> Boolean,
        onOpen: (HttpURLConnection) -> Unit = {},
        onEvent: (Event) -> Unit,
    ) {
        val url = "${baseUrl()}/api/v1/live/${enc(noteId)}?" +
            query("since" to since.toString(), "c" to clientId, "t" to shareToken)
        val connection = open(url, "GET", STREAM_SILENCE)
        connection.setRequestProperty("Accept", "text/event-stream")
        onOpen(connection)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw LiveException(status, "HTTP $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var event = "message"
                val data = StringBuilder()
                while (stillWanted()) {
                    val line = reader.readLine() ?: return
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) parse(event, data.toString())?.let(onEvent)
                            event = "message"
                            data.setLength(0)
                        }
                        line.startsWith(":") -> Unit // podtrzymanie połączenia
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substring(5).removePrefix(" "))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(event: String, data: String): Event? = runCatching {
        val body: JsonElement = json.parseToJsonElement(data)
        when (event) {
            "hello" -> {
                val obj = body.jsonObject
                Event.Hello(
                    version = obj["version"]!!.jsonPrimitive.int,
                    canEdit = obj["canEdit"]?.jsonPrimitive?.content == "true",
                    people = obj["people"]?.let { json.decodeFromJsonElement<List<Person>>(it) }.orEmpty(),
                )
            }
            "change" -> {
                val obj = body.jsonObject
                Event.Change(
                    version = obj["v"]!!.jsonPrimitive.int,
                    by = obj["by"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    delta = obj["d"]!!.jsonObject,
                )
            }
            "reset" -> {
                val obj = body.jsonObject
                Event.Reset(obj["v"]!!.jsonPrimitive.int, obj["content"]!!.jsonPrimitive.content)
            }
            "people" -> Event.People(json.decodeFromJsonElement<List<Person>>(body))
            "gone" -> Event.Gone
            else -> null
        }
    }.getOrNull()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 30_000

        /**
         * Najdłuższa cisza w strumieniu, zanim uznamy łącze za zerwane. Serwer
         * mówi co 20 sekund - dwa przegapione sygnały to już nie przypadek.
         */
        const val STREAM_SILENCE = 50_000

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
