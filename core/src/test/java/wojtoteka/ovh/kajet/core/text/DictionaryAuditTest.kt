package wojtoteka.ovh.kajet.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Method

/**
 * Przegląd całego słownika naraz: oba języki mają każdy napis, a drobne
 * zasady pisowni (półpauza zamiast dywizu, brytyjski angielski, „e-mail" po
 * polsku i „email" po angielsku) nie wracają przy kolejnych zmianach.
 */
class DictionaryAuditTest {

    private fun texts(strings: Strings): Map<String, String> =
        Strings::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && isGetter(it) }
            .associate { it.name to it.invoke(strings) as String }

    private fun isGetter(method: Method) = method.name.startsWith("get")

    private val polish = texts(PolishStrings)
    private val english = texts(EnglishStrings)

    @Test
    fun `oba jezyki maja te same napisy i zaden nie jest pusty`() {
        assertThat(polish.keys).isEqualTo(english.keys)
        assertThat(polish.size).isGreaterThan(600)
        polish.values.forEach { assertThat(it).isNotEmpty() }
        english.values.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `dywiz nie udaje myslnika`() {
        (polish.values + english.values).forEach { assertThat(it).doesNotContain(" - ") }
    }

    @Test
    fun `angielski nie ma polskich liter, a polski starego slownictwa`() {
        val diacritics = Regex("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]")
        english.values.forEach { assertThat(diacritics.containsMatchIn(it)).isFalse() }
        val stalePolish = Regex("[Ss]kasuj|[Ss]kasow|Wyrzuc|katalog", RegexOption.IGNORE_CASE)
        polish.values.forEach { assertThat(stalePolish.containsMatchIn(it)).isFalse() }
    }

    @Test
    fun `angielski jest brytyjski i pisze email bez lacznika`() {
        val american = Regex("\\bcolor|\\bfavorite|\\bcenter\\b|\\be-mail|ynchroniz|ynchronis", RegexOption.IGNORE_CASE)
        english.values.forEach { assertThat(american.containsMatchIn(it)).isFalse() }
    }

    @Test
    fun `angielskie liczby pojedyncze nie dostaja koncowki liczby mnogiej`() {
        with(EnglishStrings) {
            assertThat(selectedStrokes(1)).isEqualTo("1 stroke selected")
            assertThat(pagesCount(1)).isEqualTo("This note has 1 page.")
            assertThat(conflictsNoted(1)).startsWith("1 note changed")
            assertThat(mapTally(1, 1)).isEqualTo("1 node · 1 connection")
            assertThat(notesWaiting(1)).isEqualTo("1 note waiting to upload")
            assertThat(pausedWaiting(1)).contains("1 note is waiting")
            assertThat(offlineWaiting(1)).contains("1 note is waiting")
            assertThat(bulkPartlyFailed(1)).isEqualTo("1 item could not be processed.")
            assertThat(notesWaiting(3)).isEqualTo("3 notes waiting to upload")
        }
    }

    @Test
    fun `polskie liczebniki zachowuja trzy postacie`() {
        with(PolishStrings) {
            assertThat(notesWaiting(1)).startsWith("1 notatka czeka")
            assertThat(notesWaiting(3)).startsWith("3 notatki czekają")
            assertThat(notesWaiting(5)).startsWith("5 notatek czeka")
            assertThat(bulkPartlyFailed(1)).isEqualTo("Nie udało się wykonać operacji dla jednego wpisu.")
            assertThat(bulkPartlyFailed(7)).isEqualTo("Nie udało się wykonać operacji dla 7 wpisów.")
        }
    }
}
