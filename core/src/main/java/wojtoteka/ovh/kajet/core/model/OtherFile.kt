package wojtoteka.ovh.kajet.core.model

/**
 * Czym jest plik, który nie jest notatką Kajetu ani plikiem z kodu.
 *
 * Biblioteka oznacza takie wpisy jako [ItemType.OTHER_FILE]. Edytor kodu
 * czyta je jak UTF-8: na ekranie śmieci, a pierwszy klawisz psuje bajty
 * na dysku. Stąd osobne drogi - podgląd zdjęcia, podgląd PDF albo komunikat
 * - i żadnego pola tekstu na surowych bajtach.
 */
enum class OtherFileKind {
    IMAGE,
    PDF,
    TEXT,
    BINARY,
    ;

    companion object {
        private val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp")

        /*
          Tekst, którego Kajet nie ma w spisie języków. .txt / .log / .csv
          i tak idą do edytora kodu jako PLAIN_TEXT - tu są na wypadek, gdyby
          któryś kiedyś spadł do OTHER_FILE.
        */
        private val TEXT = setOf(
            "md", "markdown", "json", "xml", "yaml", "yml", "toml",
            "ini", "conf", "cfg", "css", "scss", "svg", "rst", "tex",
            "properties",
        )

        fun of(fileName: String): OtherFileKind {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            if (extension.isEmpty()) return BINARY
            return when {
                extension in IMAGES -> IMAGE
                extension == "pdf" -> PDF
                CodeLanguage.fromExtension(fileName) != null -> OtherFileKind.TEXT
                extension in TEXT -> OtherFileKind.TEXT
                else -> BINARY
            }
        }
    }
}
