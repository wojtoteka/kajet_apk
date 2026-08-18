package wojtoteka.ovh.kajet.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import wojtoteka.ovh.kajet.core.model.CodeLanguage

data class CodeColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val typeName: Color,
)

enum class FragmentKind { PLAIN, KEYWORD, STRING, NUMBER, COMMENT, TYPE }

data class CodeFragment(val start: Int, val end: Int, val kind: FragmentKind)

object SyntaxHighlight {

    fun split(code: String, language: CodeLanguage): List<CodeFragment> {
        val result = ArrayList<CodeFragment>(code.length / 8 + 8)
        val words = keywords(language)
        val commentPrefix = language.commentPrefix
        val hasBlockComments = language in BLOCK_COMMENT_LANGUAGES
        val ignoreCase = language in CASE_BLIND_LANGUAGES

        var i = 0
        while (i < code.length) {
            val char = code[i]

            if (code.startsWith(commentPrefix, i)) {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                result += CodeFragment(i, end, FragmentKind.COMMENT)
                i = end
                continue
            }

            if (hasBlockComments && code.startsWith("/*", i)) {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                result += CodeFragment(i, end, FragmentKind.COMMENT)
                i = end
                continue
            }

            if (char == '"' || char == '\'') {
                val tripled = code.startsWith("$char$char$char", i)
                val end = if (tripled) {
                    val fence = "$char$char$char"
                    code.indexOf(fence, i + 3).let { if (it < 0) code.length else it + 3 }
                } else {
                    endOfString(code, i, char)
                }
                result += CodeFragment(i, end, FragmentKind.STRING)
                i = end
                continue
            }

            if (char.isDigit()) {
                var end = i
                while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '.')) end++
                result += CodeFragment(i, end, FragmentKind.NUMBER)
                i = end
                continue
            }

            if (char.isLetter() || char == '_' || char == '@' || char == '#') {
                var end = i
                while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                if (end == i) end = i + 1
                val word = code.substring(i, end)
                /*
                  SELECT i select to w SQL-u to samo słowo, a uczy się go
                  wielkimi literami. Bez tego kolorował się wyłącznie zapis
                  małymi, czyli ten, którego w zadaniach prawie nie widać.
                  W pozostałych językach wielkość liter ma znaczenie i tam
                  porównujemy znak w znak.
                */
                val kind = when {
                    word in words -> FragmentKind.KEYWORD
                    ignoreCase && word.lowercase() in words -> FragmentKind.KEYWORD
                    word.first().isUpperCase() -> FragmentKind.TYPE
                    else -> FragmentKind.PLAIN
                }
                if (kind != FragmentKind.PLAIN) result += CodeFragment(i, end, kind)
                i = end
                continue
            }

            i++
        }
        return result
    }

    private fun endOfString(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            when (code[i]) {
                '\\' -> i++
                quote -> return i + 1
                '\n' -> return i
            }
            i++
        }
        return code.length
    }

    fun highlight(code: String, language: CodeLanguage, colors: CodeColors): AnnotatedString =
        AnnotatedString.Builder(code).apply {
            addStyle(SpanStyle(color = colors.plain), 0, code.length)
            for (fragment in split(code, language)) {
                val style = when (fragment.kind) {
                    FragmentKind.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)
                    FragmentKind.STRING -> SpanStyle(color = colors.string)
                    FragmentKind.NUMBER -> SpanStyle(color = colors.number)
                    FragmentKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                    FragmentKind.TYPE -> SpanStyle(color = colors.typeName)
                    FragmentKind.PLAIN -> SpanStyle(color = colors.plain)
                }
                addStyle(style, fragment.start, fragment.end.coerceAtMost(code.length))
            }
        }.toAnnotatedString()

    private val BLOCK_COMMENT_LANGUAGES = setOf(
        CodeLanguage.C, CodeLanguage.CPP, CodeLanguage.JAVA, CodeLanguage.KOTLIN,
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT, CodeLanguage.CSHARP,
        CodeLanguage.GO, CodeLanguage.RUST, CodeLanguage.PHP,
        // Oba dialekty SQL-a mają obok „--" także komentarz w gwiazdkach.
        CodeLanguage.SQL, CodeLanguage.MYSQL,
    )

    /** Języki, w których wielkość liter w słowie kluczowym nie ma znaczenia. */
    private val CASE_BLIND_LANGUAGES = setOf(CodeLanguage.SQL, CodeLanguage.MYSQL)

    /** Słowa, które znaczą to samo w SQLite i w MySQL-u. */
    private val SQL_COMMON = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer",
        "on", "group", "by", "order", "having", "limit", "as", "and", "or", "not", "null",
        "primary", "key", "foreign", "references", "distinct", "count", "sum", "avg",
    )

    private val COMMON = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "true", "false",
        "null", "new", "class", "import", "try", "catch", "finally", "throw", "switch",
        "case", "default", "do", "in", "not", "and", "or",
    )

    fun keywords(language: CodeLanguage): Set<String> = when (language) {
        CodeLanguage.PYTHON -> setOf(
            "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
            "return", "yield", "break", "continue", "pass", "with", "try", "except", "finally",
            "raise", "lambda", "global", "nonlocal", "assert", "del", "in", "is", "not", "and",
            "or", "True", "False", "None", "self", "async", "await", "match", "case",
        )
        CodeLanguage.C, CodeLanguage.CPP -> COMMON + setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed",
            "const", "static", "struct", "union", "enum", "typedef", "sizeof", "include",
            "define", "namespace", "using", "template", "typename", "public", "private",
            "protected", "virtual", "operator", "this", "nullptr", "auto", "bool", "delete",
        )
        CodeLanguage.JAVA -> COMMON + setOf(
            "public", "private", "protected", "static", "final", "void", "int", "long",
            "double", "float", "boolean", "char", "String", "extends", "implements",
            "interface", "package", "this", "super", "abstract", "synchronized", "instanceof",
        )
        CodeLanguage.KOTLIN -> COMMON + setOf(
            "fun", "val", "var", "when", "is", "object", "data", "sealed", "interface",
            "override", "private", "internal", "public", "suspend", "companion", "init",
            "package", "this", "it", "by", "lateinit", "const", "enum", "typealias",
        )
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> COMMON + setOf(
            "function", "let", "const", "var", "typeof", "instanceof", "async", "await",
            "export", "default", "extends", "this", "undefined", "of", "interface", "type",
            "number", "string", "boolean", "any", "void",
        )
        CodeLanguage.CSHARP -> COMMON + setOf(
            "using", "namespace", "public", "private", "protected", "internal", "static",
            "void", "int", "string", "bool", "double", "var", "foreach", "this", "override",
            "virtual", "readonly", "async", "await", "get", "set",
        )
        CodeLanguage.GO -> COMMON + setOf(
            "func", "package", "var", "const", "type", "struct", "interface", "map", "chan",
            "go", "defer", "range", "select", "nil", "make", "len", "append",
        )
        CodeLanguage.RUST -> COMMON + setOf(
            "fn", "let", "mut", "struct", "enum", "impl", "trait", "pub", "use", "mod",
            "match", "loop", "ref", "self", "Self", "crate", "where", "unsafe", "dyn", "as",
        )
        CodeLanguage.PHP -> COMMON + setOf(
            "function", "echo", "print", "array", "foreach", "public", "private", "protected",
            "static", "use", "namespace", "this", "elseif", "endif", "require", "include",
        )
        CodeLanguage.RUBY -> COMMON + setOf(
            "def", "end", "module", "require", "puts", "attr_accessor", "elsif", "unless",
            "then", "yield", "self", "nil", "begin", "rescue", "ensure",
        )
        CodeLanguage.BASH -> setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "echo", "read", "local", "export", "return", "exit",
        )
        CodeLanguage.SQL -> SQL_COMMON
        /*
          MySQL to wspólny trzon SQL-a i to, czego SQLite nie zna — a właśnie te
          słowa uczeń przepisuje z lekcji: SHOW TABLES, AUTO_INCREMENT,
          ENGINE=InnoDB, typy kolumn.
        */
        CodeLanguage.MYSQL -> SQL_COMMON + setOf(
            "show", "tables", "databases", "describe", "explain", "use", "auto_increment",
            "engine", "innodb", "unsigned", "int", "tinyint", "bigint", "varchar", "char",
            "text", "date", "datetime", "timestamp", "decimal", "float", "double", "boolean",
            "default", "unique", "index", "constraint", "if", "exists", "replace", "truncate",
            "now", "curdate", "concat", "ifnull", "like", "between", "in", "is", "asc", "desc",
        )
        CodeLanguage.HTML, CodeLanguage.PLAIN_TEXT -> emptySet()
    }
}
