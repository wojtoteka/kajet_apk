package wojtoteka.ovh.kajet.core.live

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.random.Random

/**
 * Silnik zmian notatki. Przypadki z live-vectors.json to dokładnie te same,
 * które sprawdza serwer (src/lib/live/merge.test.ts) - tablet i strona mają
 * scalać notatkę identycznie.
 */
class LiveMergeTest {

    private val vectors: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("live-vectors.json")!!.readText(),
    ).jsonObject

    private fun assertSame(actual: JsonElement?, expected: JsonElement?, name: String) {
        assertWithMessage("$name\n  wynik:    $actual\n  oczekiwane: $expected")
            .that(LiveMerge.jsonEqual(actual, expected))
            .isTrue()
    }

    @Test
    fun `wspólne przypadki scalania z serwerem`() {
        for (case in vectors["merge"]!!.jsonArray) {
            val vector = case.jsonObject
            val merged = LiveMerge.merge3(vector["base"], vector["mine"], vector["theirs"])
            assertSame(merged, vector["merged"], vector["name"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `delty z serwera nakładają się tutaj na to samo`() {
        for (case in vectors["diff"]!!.jsonArray) {
            val vector = case.jsonObject
            val name = vector["name"]!!.jsonPrimitive.content
            val base = vector["base"]!!
            val target = vector["target"]!!
            val fromServer = vector["delta"]
            if (fromServer != null && fromServer !is JsonNull) {
                assertSame(LiveMerge.applyDelta(base, fromServer.jsonObject), target, "$name (delta z serwera)")
            }
            val ours = LiveMerge.diff(base, target)
            if (ours == null) {
                assertWithMessage(name).that(fromServer == null || fromServer is JsonNull).isTrue()
            } else {
                assertSame(LiveMerge.applyDelta(base, ours), target, "$name (własna delta)")
            }
        }
    }

    private fun stroke(id: String, random: Random) = buildJsonObject {
        put("id", id)
        put("color", random.nextInt(5))
        put("points", buildJsonArray { add(JsonPrimitive(random.nextDouble())); add(JsonPrimitive(random.nextDouble())) })
    }

    private fun document(random: Random, counter: IntArray): JsonObject {
        val strokes = (0 until random.nextInt(6)).map { stroke("s${++counter[0]}", random) }
        return buildJsonObject {
            put("title", if (random.nextBoolean()) "Notatka" else "Inna")
            put("pages", buildJsonArray { add(buildJsonObject { put("id", "p1"); put("strokes", JsonArray(strokes)) }) })
            put("text", buildJsonObject {
                put("markdown", listOf("Ala", "ma", "kota", "i psa").take(1 + random.nextInt(4)).joinToString("\n"))
            })
        }
    }

    private fun mutate(document: JsonObject, random: Random, counter: IntArray): JsonObject {
        val strokes = document["pages"]!!.jsonArray[0].jsonObject["strokes"]!!.jsonArray.toMutableList()
        var markdown = document["text"]!!.jsonObject["markdown"]!!.jsonPrimitive.content
        var title = document["title"]!!.jsonPrimitive.content
        repeat(1 + random.nextInt(4)) {
            val roll = random.nextDouble()
            when {
                roll < 0.25 -> strokes.add(random.nextInt(strokes.size + 1), stroke("s${++counter[0]}", random))
                roll < 0.4 && strokes.isNotEmpty() -> strokes.removeAt(random.nextInt(strokes.size))
                roll < 0.55 && strokes.size > 1 -> {
                    val moved = strokes.removeAt(random.nextInt(strokes.size))
                    strokes.add(random.nextInt(strokes.size + 1), moved)
                }
                roll < 0.7 && strokes.isNotEmpty() -> {
                    val at = random.nextInt(strokes.size)
                    strokes[at] = JsonObject(strokes[at].jsonObject + ("color" to JsonPrimitive(random.nextInt(9))))
                }
                roll < 0.9 -> {
                    val lines = markdown.split("\n").toMutableList()
                    lines.add(random.nextInt(lines.size + 1), "wiersz ${counter[0]}")
                    markdown = lines.joinToString("\n")
                }
                else -> title = "Tytuł ${random.nextInt(3)}"
            }
        }
        return buildJsonObject {
            put("title", title)
            put("pages", buildJsonArray { add(buildJsonObject { put("id", "p1"); put("strokes", JsonArray(strokes)) }) })
            put("text", buildJsonObject { put("markdown", markdown) })
        }
    }

    @Test
    fun `nałożona różnica zawsze odtwarza cel`() {
        val random = Random(7)
        val counter = intArrayOf(0)
        repeat(400) {
            val base = document(random, counter)
            val target = mutate(base, random, counter)
            val delta = LiveMerge.diff(base, target)
            val rebuilt = if (delta == null) base else LiveMerge.applyDelta(base, delta)
            assertThat(LiveMerge.jsonEqual(rebuilt, target)).isTrue()
            // Delta przechodzi przez tekst JSON w obie strony bez zmian.
            if (delta != null) {
                val again = Json.parseToJsonElement(delta.toString()).jsonObject
                assertThat(LiveMerge.jsonEqual(LiveMerge.applyDelta(base, again), target)).isTrue()
            }
        }
    }

    @Test
    fun `scalanie z niezmienioną stroną oddaje drugą stronę`() {
        val random = Random(11)
        val counter = intArrayOf(1000)
        repeat(200) {
            val base = document(random, counter)
            val changed = mutate(base, random, counter)
            assertThat(LiveMerge.jsonEqual(LiveMerge.merge3(base, changed, base), changed)).isTrue()
            assertThat(LiveMerge.jsonEqual(LiveMerge.merge3(base, base, changed), changed)).isTrue()
        }
    }

    @Test
    fun `tekst - pisanie w tym samym zdaniu w różnych słowach`() {
        assertThat(
            LiveMerge.mergeText(
                "Spotkanie w poniedziałek o 10.\n",
                "Spotkanie zespołu w poniedziałek o 10.\n",
                "Spotkanie w poniedziałek o 11.\n",
            ),
        ).isEqualTo("Spotkanie zespołu w poniedziałek o 11.\n")
    }

    @Test
    fun `tekst - słowa, odstępy i znaki`() {
        assertThat(LiveMerge.wordTokens("Zażółć  gęślą, jaźń!\n"))
            .containsExactly("Zażółć", "  ", "gęślą", ",", " ", "jaźń", "!", "\n")
            .inOrder()
    }

    @Test
    fun `bardzo długi tekst nie zawiesza scalania`() {
        val base = (0 until 4000).joinToString("\n") { "wiersz $it" }
        val mine = base.replace("wiersz 10\n", "wiersz dziesiąty\n")
        val theirs = base.replace("wiersz 3990\n", "wiersz prawie ostatni\n")
        val started = System.currentTimeMillis()
        val merged = LiveMerge.mergeText(base, mine, theirs)
        assertThat(System.currentTimeMillis() - started).isLessThan(2_000L)
        assertThat(merged).contains("wiersz dziesiąty\n")
        assertThat(merged).contains("wiersz prawie ostatni\n")
    }

    @Test
    fun `wymiana poza zakresem nie wywraca nałożenia`() {
        val delta = Json.parseToJsonElement("""{"t":[[10,5,"x"]]}""").jsonObject
        assertThat(LiveMerge.applyDelta(JsonPrimitive("abc"), delta).jsonPrimitive.content).isEqualTo("abcx")
    }
}
