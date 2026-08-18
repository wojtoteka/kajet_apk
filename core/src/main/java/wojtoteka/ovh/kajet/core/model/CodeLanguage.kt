package wojtoteka.ovh.kajet.core.model

import wojtoteka.ovh.kajet.core.text.Strings

/**
 * Języki, które Kajet rozpoznaje w plikach z kodem.
 *
 * KOLEJNOŚĆ WPISÓW JEST CZĘŚCIĄ ZACHOWANIA, nie porządkiem alfabetycznym.
 * Rządzi dwiema rzeczami: tym, co widać w oknie zakładania pliku, i tym, na
 * jaki język wypada plik przy dwóch wpisach o tym samym rozszerzeniu —
 * [fromExtension] bierze pierwsze dopasowanie. Pierwsze trzynaście wpisów
 * stoi dokładnie tak jak LANGUAGES w src/lib/code-runner.ts na serwerze;
 * przestawienie ich rozjeżdża aplikację ze stroną.
 *
 * Języki, których serwer nie ma, stoją na końcu i mają puste [serverRuntime].
 */
enum class CodeLanguage(
    val id: String,
    val labelPl: String,
    val extensions: List<String>,
    val serverRuntime: String?,
    val offline: Boolean,
    val remoteFileName: String,
    val commentPrefix: String = "//",
) {
    PYTHON("python", "Python", listOf("py"), "python", true, "main.py", "#"),
    JAVASCRIPT("javascript", "JavaScript", listOf("js", "mjs"), "javascript", false, "main.js"),
    TYPESCRIPT("typescript", "TypeScript", listOf("ts"), "typescript", false, "main.ts"),
    // „Shell", nie „Bash" — tak ten język nazywa się w spisie serwera, a ten
    // sam plik .sh nie ma prawa podpisywać się inaczej na stronie, a inaczej
    // na tablecie.
    BASH("bash", "Shell", listOf("sh", "bash"), "bash", false, "main.sh", "#"),
    C("c", "C", listOf("c", "h"), "c", false, "main.c"),
    CPP("cpp", "C++", listOf("cpp", "cc", "cxx", "hpp"), "c++", false, "main.cpp"),
    CSHARP("csharp", "C#", listOf("cs"), "csharp", false, "Main.cs"),
    JAVA("java", "Java", listOf("java"), "java", false, "Main.java"),
    PHP("php", "PHP", listOf("php"), "php", false, "main.php", "#"),
    RUBY("ruby", "Ruby", listOf("rb"), "ruby", false, "main.rb", "#"),
    /*
      SQLite i MySQL dzielą rozszerzenie sql i ta para MUSI stać w tej
      kolejności. Serwer rozstrzyga kolizję pierwszym dopasowaniem ze spisu
      (guessLanguageFromTitle w code-note.ts), SQLite stoi tam wyżej, więc
      zadanie.sql jest wszędzie SQLite. Odwrócenie tego wymaga zmiany tu
      i w code-runner.ts naraz — samo tutaj zrobi tylko tyle, że ten sam plik
      będzie się otwierał inaczej na tablecie, a inaczej na stronie.
    */
    SQL("sql", "SQL", listOf("sql"), "sqlite3", false, "main.sql", "--"),
    /*
      MySQL to nie ten sam język co SQLite — szkoła uczy SHOW TABLES,
      AUTO_INCREMENT i NOW(), których SQLite nie zna.

      Na tablecie tego wpisu nie da się wybrać, bo aplikacja czyta język
      wyłącznie z rozszerzenia pliku, a .sql należy do SQLite. Wpis jest tu po
      to, żeby notatka założona na stronie jako MySQL zeszła na tablet jako
      zwykły plik .sql z kolorowaniem (a nie .txt) i żeby wróciła na serwer
      dalej jako MySQL — pilnuje tego pamięć języka w CodeFileIds.
    */
    MYSQL("mysql", "MySQL", listOf("sql"), "mysql", false, "main.sql", "--"),
    // HTML się nie uruchamia jak program — zamiast tego edytor kodu ma podgląd
    // strony. Prefiks komentarza jest liniowy z braku lepszego mechanizmu.
    HTML("html", "HTML", listOf("html", "htm"), null, false, "index.html", "<!--"),

    /*
      Dalej to, czego serwer nie uruchamia. Puste serverRuntime znaczy „Kajet
      tego nie policzy": takiego języka nie ma w oknie zakładania pliku i nie
      ma przy nim działającego przycisku uruchomienia. Pliki, które już leżą
      w bibliotece, otwierają się normalnie — z kolorowaniem i własną ikoną.
    */
    KOTLIN("kotlin", "Kotlin", listOf("kt", "kts"), null, false, "Main.kt"),
    GO("go", "Go", listOf("go"), null, false, "main.go"),
    RUST("rust", "Rust", listOf("rs"), null, false, "main.rs"),
    PLAIN_TEXT("text", "Zwykły tekst", listOf("txt", "log", "csv"), null, false, "main.txt", "#"),
    ;

    /*
      Nazwy języków są własne („Python", „C++") i tłumaczeniu nie podlegają.
      Jedyny wyjątek to zwykły tekst, który nazwą nie jest.
    */
    fun label(words: Strings): String =
        if (words.english && this == PLAIN_TEXT) "Plain text" else labelPl

    val runnable: Boolean get() = serverRuntime != null

    companion object {
        /**
         * Język pliku po jego nazwie. Przy dwóch językach o tym samym
         * rozszerzeniu wygrywa ten, który stoi wyżej w spisie — tak samo jak
         * na serwerze.
         */
        fun fromExtension(fileName: String): CodeLanguage? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return null
            return entries.firstOrNull { extension in it.extensions }
        }

        /**
         * Język po identyfikatorze z serwera. Nieznany identyfikator oddaje
         * `null`, a NIE rzuca — notatka w języku, którego ta wersja aplikacji
         * jeszcze nie zna, ma się otworzyć jako zwykły tekst, a nie wywrócić
         * pobieranie.
         */
        fun fromId(id: String?): CodeLanguage? = entries.firstOrNull { it.id == id }

        /**
         * To samo dla identyfikatorów, którymi posługuje się serwer: SQLite
         * jest tam „sqlite3", a C++ „c++", więc samo [fromId] ich nie znajdzie.
         */
        fun fromServerId(id: String?): CodeLanguage? {
            // Bez tej straży pusty identyfikator trafiałby w HTML — to pierwszy
            // wpis, który ma puste serverRuntime, więc "== id" byłoby prawdą.
            if (id.isNullOrEmpty()) return null
            return entries.firstOrNull { it.serverRuntime == id } ?: fromId(id)
        }

        val allExtensions: Set<String> = entries.flatMap { it.extensions }.toSet()
    }
}
