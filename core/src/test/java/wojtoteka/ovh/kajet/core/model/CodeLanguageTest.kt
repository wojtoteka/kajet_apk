package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spis języków kontra spis serwera.
 *
 * Aplikacja trzyma własną listę - serwera o nią nie pyta. Rozjazd nie kończy
 * się tu komunikatem, tylko plikiem, który otwiera się inaczej na tablecie,
 * a inaczej na stronie, albo uruchomieniem odbitym zdaniem „Tego języka Kajet
 * nie uruchomi na tym serwerze". Te testy pilnują trzech rzeczy: identyfikatora
 * co do znaku, kolejności i tego, żeby nieznany język nie wywracał notatki.
 */
class CodeLanguageTest {

    /**
     * LANGUAGES z src/lib/code-runner.ts, po identyfikatorach i w kolejności
     * stamtąd. Serwer szuka języka przez `LANGUAGES.find(l => l.id === id)`,
     * więc liczy się każdy znak: „CSharp" ani „c#" nie trafiają w nic.
     */
    private val serverOrder = listOf(
        "python", "javascript", "typescript", "bash", "c", "c++", "csharp", "java",
        "php", "ruby", "sqlite3", "mysql", "html",
    )

    /** Napis, który leci na serwer - bywa inny niż nasz [CodeLanguage.id]. */
    private val CodeLanguage.serverId: String get() = serverRuntime ?: id

    @Test
    fun `identyfikatory jada na serwer w jego wlasnym brzmieniu`() {
        assertThat(CodeLanguage.CSHARP.serverId).isEqualTo("csharp")
        assertThat(CodeLanguage.JAVA.serverId).isEqualTo("java")
        assertThat(CodeLanguage.MYSQL.serverId).isEqualTo("mysql")
        // Te dwa różnią się od naszego id i o nie najłatwiej się potknąć.
        assertThat(CodeLanguage.SQL.serverId).isEqualTo("sqlite3")
        assertThat(CodeLanguage.CPP.serverId).isEqualTo("c++")
    }

    @Test
    fun `nazwy na ekranie i rozszerzenia zgadzaja sie ze spisem serwera`() {
        // Ten sam plik ma podpisywać się tak samo na stronie i na tablecie.
        assertThat(CodeLanguage.BASH.labelPl).isEqualTo("Shell")
        assertThat(CodeLanguage.CSHARP.labelPl).isEqualTo("C#")
        assertThat(CodeLanguage.CSHARP.extensions.first()).isEqualTo("cs")
        assertThat(CodeLanguage.JAVA.labelPl).isEqualTo("Java")
        assertThat(CodeLanguage.JAVA.extensions.first()).isEqualTo("java")
        assertThat(CodeLanguage.MYSQL.labelPl).isEqualTo("MySQL")
        assertThat(CodeLanguage.MYSQL.extensions.first()).isEqualTo("sql")
    }

    @Test
    fun `spis stoi w kolejnosci serwera`() {
        val ours = CodeLanguage.entries.map { it.serverId }.filter { it in serverOrder }

        assertThat(ours).isEqualTo(serverOrder)
    }

    // --- Kolizja rozszerzenia sql ---

    @Test
    fun `plik sql jest SQLite bo sqlite3 stoi wyzej niz mysql`() {
        // To samo rozstrzygnięcie co guessLanguageFromTitle na serwerze:
        // pierwsze dopasowanie ze spisu wygrywa.
        assertThat(CodeLanguage.fromExtension("zadanie.sql")).isEqualTo(CodeLanguage.SQL)
        assertThat(CodeLanguage.fromExtension("ZADANIE.SQL")).isEqualTo(CodeLanguage.SQL)
    }

    @Test
    fun `sqlite3 stoi w spisie przed mysql`() {
        assertThat(CodeLanguage.SQL.ordinal).isLessThan(CodeLanguage.MYSQL.ordinal)
    }

    // --- Nieznany język ---

    @Test
    fun `nieznany identyfikator oddaje pustke zamiast rzucac`() {
        /*
          Notatka założona na stronie w języku, którego ta wersja aplikacji
          jeszcze nie zna, ma się otworzyć jako zwykły tekst. Wyjątek w tym
          miejscu zatrzymywałby całe pobieranie.
        */
        assertThat(CodeLanguage.fromId("elixir")).isNull()
        assertThat(CodeLanguage.fromId(null)).isNull()
        assertThat(CodeLanguage.fromId("")).isNull()
        assertThat(CodeLanguage.fromServerId("elixir")).isNull()
        assertThat(CodeLanguage.fromServerId(null)).isNull()
    }

    @Test
    fun `fromServerId zna oba brzmienia identyfikatora`() {
        assertThat(CodeLanguage.fromServerId("sqlite3")).isEqualTo(CodeLanguage.SQL)
        assertThat(CodeLanguage.fromServerId("mysql")).isEqualTo(CodeLanguage.MYSQL)
        assertThat(CodeLanguage.fromServerId("c++")).isEqualTo(CodeLanguage.CPP)
        assertThat(CodeLanguage.fromServerId("csharp")).isEqualTo(CodeLanguage.CSHARP)
        assertThat(CodeLanguage.fromServerId("java")).isEqualTo(CodeLanguage.JAVA)
        // Języki spoza serwera mają puste serverRuntime - te znajdują się po id.
        assertThat(CodeLanguage.fromServerId("kotlin")).isEqualTo(CodeLanguage.KOTLIN)
    }

    @Test
    fun `plik w nieznanym jezyku ma na czym stanac`() {
        // Ta sama droga co w CodeViewModel: rozszerzenie, a w ostateczności
        // zwykły tekst. Nazwa bez rozszerzenia też nie ma prawa niczego zerwać.
        assertThat(CodeLanguage.fromExtension("zadanie.ex")).isNull()
        assertThat(CodeLanguage.fromExtension("zadanie")).isNull()
        assertThat(CodeLanguage.fromExtension("")).isNull()
    }

    // --- HTML: ogląda się, nie uruchamia ---

    @Test
    fun `html nie jest jezykiem do uruchomienia`() {
        /*
          GET /api/v1/code nie oddaje pola `preview`, więc po samym tamtym
          spisie HTML wyglądałby na język do uruchomienia. Aplikacja wie to
          sama, z pustego serverRuntime - i po tym pozna go też runner.
        */
        assertThat(CodeLanguage.HTML.runnable).isFalse()
        assertThat(CodeLanguage.HTML.serverRuntime).isNull()
    }

    @Test
    fun `jezyki spoza serwera nie obiecuja uruchomienia`() {
        // Serwer nie ma Kotlina, Go ani Rusta. Pliki się otwierają i kolorują,
        // ale okno zakładania pliku ich nie proponuje i przycisk przy nich milczy.
        assertThat(CodeLanguage.KOTLIN.runnable).isFalse()
        assertThat(CodeLanguage.GO.runnable).isFalse()
        assertThat(CodeLanguage.RUST.runnable).isFalse()
        assertThat(CodeLanguage.PLAIN_TEXT.runnable).isFalse()
    }

    @Test
    fun `nowe jezyki uruchamiaja sie na serwerze`() {
        assertThat(CodeLanguage.CSHARP.runnable).isTrue()
        assertThat(CodeLanguage.JAVA.runnable).isTrue()
        assertThat(CodeLanguage.MYSQL.runnable).isTrue()
        // Żaden z nich nie liczy się na samym tablecie - potrzebny jest serwer.
        assertThat(CodeLanguage.CSHARP.offline).isFalse()
        assertThat(CodeLanguage.JAVA.offline).isFalse()
        assertThat(CodeLanguage.MYSQL.offline).isFalse()
    }

    @Test
    fun `kazde rozszerzenie prowadzi do jakiegos jezyka`() {
        for (extension in CodeLanguage.allExtensions) {
            assertThat(CodeLanguage.fromExtension("plik.$extension")).isNotNull()
        }
    }
}
