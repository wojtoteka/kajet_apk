package wojtoteka.ovh.kajet.code

import wojtoteka.ovh.kajet.core.model.CodeLanguage

/** Granice chroniące edytor Compose przed kosztownym składem wielkich plików. */
object CodeFileLimits {
    const val PLAIN_TEXT_EDITOR_BYTES = 250 * 1024
    const val CODE_EDITOR_BYTES = 400 * 1024
    const val SYNTAX_HIGHLIGHT_CHARS = 100 * 1024
    const val LINE_NUMBER_LIMIT = 10_000

    fun editorBytes(language: CodeLanguage): Int =
        if (language == CodeLanguage.PLAIN_TEXT) PLAIN_TEXT_EDITOR_BYTES else CODE_EDITOR_BYTES

    fun shouldHighlight(characterCount: Int): Boolean =
        characterCount <= SYNTAX_HIGHLIGHT_CHARS

    fun shouldShowLineNumbers(lineCount: Int): Boolean =
        lineCount <= LINE_NUMBER_LIMIT
}
