package wojtoteka.ovh.kajet.storage

object FileNames {

    private val FORBIDDEN_CHARS = charArrayOf('"', '*', '/', ':', '<', '>', '?', '\\', '|')

    private val RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    const val MAX_LENGTH = 96
    const val FALLBACK_NAME = "Bez nazwy"

    fun safe(name: String): String {
        var result = name.trim()

        result = buildString(result.length) {
            for (char in result) {
                when {
                    char in FORBIDDEN_CHARS -> append('_')
                    char.code < 0x20 -> append('_')
                    char.code == 0x7F -> append('_')
                    else -> append(char)
                }
            }
        }

        // A trailing dot or space breaks a directory name on Windows.
        result = result.trimEnd('.', ' ')

        if (result.length > MAX_LENGTH) {
            result = result.take(MAX_LENGTH).trimEnd('.', ' ')
        }

        if (result.isEmpty()) return FALLBACK_NAME

        val stem = result.substringBefore('.').uppercase()
        if (stem in RESERVED_NAMES) return "_$result"

        // A leading dot hides the file on unix systems, and the .trash directory
        // is reserved here for the bin.
        if (result.startsWith('.')) return "_" + result.drop(1)

        return result
    }

    fun unique(name: String, occupied: Set<String>, extension: String = ""): String {
        val base = safe(name)
        val full = base + extension
        if (!occupied.containsIgnoreCase(full)) return full

        var number = 2
        while (number < 1000) {
            val candidate = "$base ($number)$extension"
            if (!occupied.containsIgnoreCase(candidate)) return candidate
            number++
        }
        return "$base (${System.currentTimeMillis()})$extension"
    }

    private fun Set<String>.containsIgnoreCase(name: String): Boolean =
        any { it.equals(name, ignoreCase = true) }

    fun withoutNoteExtension(folderName: String): String =
        folderName.removeSuffix(".note")

    fun isNote(name: String): Boolean = name.endsWith(".note", ignoreCase = true)

    fun isHidden(name: String): Boolean = name.startsWith(".")
}
