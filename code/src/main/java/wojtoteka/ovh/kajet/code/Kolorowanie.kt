package wojtoteka.ovh.kajet.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import wojtoteka.ovh.kajet.core.model.CodeLanguage

/** Kolory kolorowania składni. Dobrane do palety Kajetu, nie z gotowego zestawu. */
data class BarwyKodu(
    val zwykly: Color,
    val slowoKluczowe: Color,
    val napis: Color,
    val liczba: Color,
    val komentarz: Color,
    val nazwaWlasna: Color,
)

/** Rodzaj fragmentu kodu rozpoznany przez kolorowanie. */
enum class RodzajFragmentu { ZWYKLY, SLOWO, NAPIS, LICZBA, KOMENTARZ, NAZWA }

data class Fragment(val od: Int, val doIndeksu: Int, val rodzaj: RodzajFragmentu)

/**
 * Kolorowanie składni.
 *
 * To jest rozbiór leksykalny, a nie pełna analiza języka. Rozpoznaje komentarze,
 * napisy, liczby, słowa kluczowe i nazwy pisane z wielkiej litery. Tyle wystarczy,
 * żeby kod czytało się szybciej, a jednocześnie działa to tak samo dla wszystkich
 * kilkunastu języków i nie zwalnia przy dłuższym pliku.
 */
object Kolorowanie {

    fun podziel(kod: String, jezyk: CodeLanguage): List<Fragment> {
        val wynik = ArrayList<Fragment>(kod.length / 8 + 8)
        val slowa = slowaKluczowe(jezyk)
        val przedrostekKomentarza = jezyk.commentPrefix
        val blokKomentarza = jezyk in ZE_ZNACZNIKIEM_BLOKU

        var i = 0
        while (i < kod.length) {
            val znak = kod[i]

            // Komentarz do końca wiersza
            if (kod.startsWith(przedrostekKomentarza, i)) {
                val koniec = kod.indexOf('\n', i).let { if (it < 0) kod.length else it }
                wynik += Fragment(i, koniec, RodzajFragmentu.KOMENTARZ)
                i = koniec
                continue
            }

            // Komentarz blokowy
            if (blokKomentarza && kod.startsWith("/*", i)) {
                val koniec = kod.indexOf("*/", i + 2).let { if (it < 0) kod.length else it + 2 }
                wynik += Fragment(i, koniec, RodzajFragmentu.KOMENTARZ)
                i = koniec
                continue
            }

            // Napis w cudzysłowie albo w apostrofach
            if (znak == '"' || znak == '\'') {
                val potrojny = kod.startsWith("$znak$znak$znak", i)
                val koniec = if (potrojny) {
                    val szukane = "$znak$znak$znak"
                    kod.indexOf(szukane, i + 3).let { if (it < 0) kod.length else it + 3 }
                } else {
                    koniecNapisu(kod, i, znak)
                }
                wynik += Fragment(i, koniec, RodzajFragmentu.NAPIS)
                i = koniec
                continue
            }

            // Liczba
            if (znak.isDigit()) {
                var koniec = i
                while (koniec < kod.length && (kod[koniec].isLetterOrDigit() || kod[koniec] == '.')) koniec++
                wynik += Fragment(i, koniec, RodzajFragmentu.LICZBA)
                i = koniec
                continue
            }

            // Słowo
            if (znak.isLetter() || znak == '_' || znak == '@' || znak == '#') {
                var koniec = i
                while (koniec < kod.length && (kod[koniec].isLetterOrDigit() || kod[koniec] == '_')) koniec++
                if (koniec == i) koniec = i + 1
                val slowo = kod.substring(i, koniec)
                val rodzaj = when {
                    slowo in slowa -> RodzajFragmentu.SLOWO
                    slowo.first().isUpperCase() -> RodzajFragmentu.NAZWA
                    else -> RodzajFragmentu.ZWYKLY
                }
                if (rodzaj != RodzajFragmentu.ZWYKLY) wynik += Fragment(i, koniec, rodzaj)
                i = koniec
                continue
            }

            i++
        }
        return wynik
    }

    private fun koniecNapisu(kod: String, poczatek: Int, cudzyslow: Char): Int {
        var i = poczatek + 1
        while (i < kod.length) {
            when (kod[i]) {
                '\\' -> i++
                cudzyslow -> return i + 1
                '\n' -> return i
            }
            i++
        }
        return kod.length
    }

    fun pokoloruj(kod: String, jezyk: CodeLanguage, barwy: BarwyKodu): AnnotatedString =
        AnnotatedString.Builder(kod).apply {
            addStyle(SpanStyle(color = barwy.zwykly), 0, kod.length)
            for (fragment in podziel(kod, jezyk)) {
                val styl = when (fragment.rodzaj) {
                    RodzajFragmentu.SLOWO -> SpanStyle(color = barwy.slowoKluczowe, fontWeight = FontWeight.Medium)
                    RodzajFragmentu.NAPIS -> SpanStyle(color = barwy.napis)
                    RodzajFragmentu.LICZBA -> SpanStyle(color = barwy.liczba)
                    RodzajFragmentu.KOMENTARZ -> SpanStyle(color = barwy.komentarz, fontStyle = FontStyle.Italic)
                    RodzajFragmentu.NAZWA -> SpanStyle(color = barwy.nazwaWlasna)
                    RodzajFragmentu.ZWYKLY -> SpanStyle(color = barwy.zwykly)
                }
                addStyle(styl, fragment.od, fragment.doIndeksu.coerceAtMost(kod.length))
            }
        }.toAnnotatedString()

    private val ZE_ZNACZNIKIEM_BLOKU = setOf(
        CodeLanguage.C, CodeLanguage.CPP, CodeLanguage.JAVA, CodeLanguage.KOTLIN,
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT, CodeLanguage.CSHARP,
        CodeLanguage.GO, CodeLanguage.RUST, CodeLanguage.PHP,
    )

    private val WSPOLNE = setOf(
        "if", "else", "for", "while", "return", "break", "continue", "true", "false",
        "null", "new", "class", "import", "try", "catch", "finally", "throw", "switch",
        "case", "default", "do", "in", "not", "and", "or",
    )

    fun slowaKluczowe(jezyk: CodeLanguage): Set<String> = when (jezyk) {
        CodeLanguage.PYTHON -> setOf(
            "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
            "return", "yield", "break", "continue", "pass", "with", "try", "except", "finally",
            "raise", "lambda", "global", "nonlocal", "assert", "del", "in", "is", "not", "and",
            "or", "True", "False", "None", "self", "async", "await", "match", "case",
        )
        CodeLanguage.C, CodeLanguage.CPP -> WSPOLNE + setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed",
            "const", "static", "struct", "union", "enum", "typedef", "sizeof", "include",
            "define", "namespace", "using", "template", "typename", "public", "private",
            "protected", "virtual", "operator", "this", "nullptr", "auto", "bool", "delete",
        )
        CodeLanguage.JAVA -> WSPOLNE + setOf(
            "public", "private", "protected", "static", "final", "void", "int", "long",
            "double", "float", "boolean", "char", "String", "extends", "implements",
            "interface", "package", "this", "super", "abstract", "synchronized", "instanceof",
        )
        CodeLanguage.KOTLIN -> WSPOLNE + setOf(
            "fun", "val", "var", "when", "is", "object", "data", "sealed", "interface",
            "override", "private", "internal", "public", "suspend", "companion", "init",
            "package", "this", "it", "by", "lateinit", "const", "enum", "typealias",
        )
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> WSPOLNE + setOf(
            "function", "let", "const", "var", "typeof", "instanceof", "async", "await",
            "export", "default", "extends", "this", "undefined", "of", "interface", "type",
            "number", "string", "boolean", "any", "void",
        )
        CodeLanguage.CSHARP -> WSPOLNE + setOf(
            "using", "namespace", "public", "private", "protected", "internal", "static",
            "void", "int", "string", "bool", "double", "var", "foreach", "this", "override",
            "virtual", "readonly", "async", "await", "get", "set",
        )
        CodeLanguage.GO -> WSPOLNE + setOf(
            "func", "package", "var", "const", "type", "struct", "interface", "map", "chan",
            "go", "defer", "range", "select", "nil", "make", "len", "append",
        )
        CodeLanguage.RUST -> WSPOLNE + setOf(
            "fn", "let", "mut", "struct", "enum", "impl", "trait", "pub", "use", "mod",
            "match", "loop", "ref", "self", "Self", "crate", "where", "unsafe", "dyn", "as",
        )
        CodeLanguage.PHP -> WSPOLNE + setOf(
            "function", "echo", "print", "array", "foreach", "public", "private", "protected",
            "static", "use", "namespace", "this", "elseif", "endif", "require", "include",
        )
        CodeLanguage.RUBY -> WSPOLNE + setOf(
            "def", "end", "module", "require", "puts", "attr_accessor", "elsif", "unless",
            "then", "yield", "self", "nil", "begin", "rescue", "ensure",
        )
        CodeLanguage.BASH -> setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "echo", "read", "local", "export", "return", "exit",
        )
        CodeLanguage.SQL -> setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
            "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer",
            "on", "group", "by", "order", "having", "limit", "as", "and", "or", "not", "null",
            "primary", "key", "foreign", "references", "distinct", "count", "sum", "avg",
        )
        CodeLanguage.TEKST -> emptySet()
    }
}
