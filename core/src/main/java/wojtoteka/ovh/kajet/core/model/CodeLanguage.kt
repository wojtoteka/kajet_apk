package wojtoteka.ovh.kajet.core.model

/**
 * Języki, które Kajet umie otworzyć i uruchomić.
 *
 * [offline] mówi, czy program da się uruchomić bez internetu.
 * Dziś offline działa tylko Python, bo jego tłumacz jest wbudowany w aplikację.
 * Reszta idzie przez serwer i wymaga połączenia. Aplikacja pisze to wprost
 * przy każdym języku, żeby nie było niespodzianki w pociągu.
 */
enum class CodeLanguage(
    val id: String,
    val labelPl: String,
    val extensions: List<String>,
    /** Nazwa środowiska po stronie serwera Piston. */
    val pistonRuntime: String?,
    val offline: Boolean,
    /** Nazwa pliku wysyłanego na serwer. Java wymaga zgodności z nazwą klasy. */
    val remoteFileName: String,
    val commentPrefix: String = "//",
) {
    PYTHON("python", "Python", listOf("py"), "python", true, "main.py", "#"),
    C("c", "C", listOf("c", "h"), "c", false, "main.c"),
    CPP("cpp", "C++", listOf("cpp", "cc", "cxx", "hpp"), "c++", false, "main.cpp"),
    JAVA("java", "Java", listOf("java"), "java", false, "Main.java"),
    KOTLIN("kotlin", "Kotlin", listOf("kt", "kts"), "kotlin", false, "Main.kt"),
    JAVASCRIPT("javascript", "JavaScript", listOf("js", "mjs"), "javascript", false, "main.js"),
    TYPESCRIPT("typescript", "TypeScript", listOf("ts"), "typescript", false, "main.ts"),
    CSHARP("csharp", "C#", listOf("cs"), "csharp", false, "Main.cs"),
    GO("go", "Go", listOf("go"), "go", false, "main.go"),
    RUST("rust", "Rust", listOf("rs"), "rust", false, "main.rs"),
    PHP("php", "PHP", listOf("php"), "php", false, "main.php", "#"),
    RUBY("ruby", "Ruby", listOf("rb"), "ruby", false, "main.rb", "#"),
    BASH("bash", "Bash", listOf("sh", "bash"), "bash", false, "main.sh", "#"),
    SQL("sql", "SQL", listOf("sql"), "sqlite3", false, "main.sql", "--"),
    TEKST("tekst", "Zwykły tekst", listOf("txt", "log", "csv"), null, false, "main.txt", "#"),
    ;

    val uruchamialny: Boolean get() = pistonRuntime != null

    companion object {
        fun fromExtension(fileName: String): CodeLanguage? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return null
            return entries.firstOrNull { ext in it.extensions }
        }

        fun fromId(id: String?): CodeLanguage? = entries.firstOrNull { it.id == id }

        /** Rozszerzenia, które biblioteka pokazuje jako pliki z kodem. */
        val wszystkieRozszerzenia: Set<String> = entries.flatMap { it.extensions }.toSet()
    }
}
