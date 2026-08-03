package wojtoteka.ovh.kajet.editor.text

import wojtoteka.ovh.kajet.core.model.TextMarkers

object Markdown {

    const val ATTACHMENT_BASE_URL = "https://kajet.local/assets/"

    fun toHtml(source: String): String {
        val lines = source.split('\n')
        val result = StringBuilder()
        var i = 0
        var openList: String? = null

        fun closeList() {
            if (openList != null) {
                result.append("</").append(openList).append(">\n")
                openList = null
            }
        }

        fun startList(kind: String) {
            if (openList != kind) {
                closeList()
                result.append("<").append(kind).append(">\n")
                openList = kind
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blok kodu
            if (trimmed.startsWith("```")) {
                closeList()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.append(escape(lines[i])).append('\n')
                    i++
                }
                i++
                result.append("<pre class=\"kod\" data-jezyk=\"")
                    .append(escape(language))
                    .append("\"><code>")
                    .append(code)
                    .append("</code></pre>\n")
                continue
            }

            // Wzór w osobnej linii. Zostawiamy go w spokoju, policzy go KaTeX.
            if (trimmed.startsWith("$$")) {
                closeList()
                val formula = StringBuilder()
                if (trimmed.length > 2 && trimmed.endsWith("$$") && trimmed.length > 4) {
                    formula.append(trimmed.removeSurrounding("$$"))
                    i++
                } else {
                    formula.append(trimmed.removePrefix("$$")).append('\n')
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        formula.append(lines[i]).append('\n')
                        i++
                    }
                    if (i < lines.size) {
                        formula.append(lines[i].trim().removeSuffix("$$"))
                        i++
                    }
                }
                result.append("<div class=\"wzor\">$$").append(escape(formula.toString())).append("$$</div>\n")
                continue
            }

            if (trimmed.isEmpty()) {
                closeList()
                i++
                continue
            }

            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                closeList()
                result.append("<hr>\n")
                i++
                continue
            }

            // Nagłówki
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                closeList()
                val content = inline(trimmed.drop(level + 1))
                result.append("<h").append(level).append('>').append(content)
                    .append("</h").append(level).append(">\n")
                i++
                continue
            }

            // Cytat
            if (trimmed.startsWith("> ")) {
                closeList()
                val content = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    content.append(inline(lines[i].trim().removePrefix(">").trim())).append(' ')
                    i++
                }
                result.append("<blockquote>").append(content.toString().trim()).append("</blockquote>\n")
                continue
            }

            // Lista zadań
            val task = Regex("^[-*+] \\[([ xX])] (.*)$").find(trimmed)
            if (task != null) {
                startList("ul")
                val checked = task.groupValues[1].lowercase() == "x"
                result.append("<li class=\"zadanie\"><input type=\"checkbox\" data-wiersz=\"")
                    .append(i)
                    .append('"')
                    .append(if (checked) " checked" else "")
                    .append("><span")
                    .append(if (checked) " class=\"zrobione\"" else "")
                    .append('>')
                    .append(inline(task.groupValues[2]))
                    .append("</span></li>\n")
                i++
                continue
            }

            // Lista zwykła
            if (Regex("^[-*+] ").containsMatchIn(trimmed)) {
                startList("ul")
                result.append("<li>").append(inline(trimmed.drop(2))).append("</li>\n")
                i++
                continue
            }

            // Lista numerowana
            val numbered = Regex("^(\\d+)[.)] (.*)$").find(trimmed)
            if (numbered != null) {
                startList("ol")
                result.append("<li>").append(inline(numbered.groupValues[2])).append("</li>\n")
                i++
                continue
            }

            // Zwykły akapit. Kolejne wiersze bez pustej linii sklejamy w jeden.
            closeList()
            val paragraph = StringBuilder(line.trim())
            i++
            while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i].trim())) {
                paragraph.append(' ').append(lines[i].trim())
                i++
            }
            result.append("<p>").append(inline(paragraph.toString())).append("</p>\n")
        }
        closeList()
        return result.toString()
    }

    private fun startsBlock(line: String): Boolean =
        line.startsWith("#") ||
            line.startsWith("> ") ||
            line.startsWith("```") ||
            line.startsWith("$$") ||
            line == "---" ||
            Regex("^[-*+] ").containsMatchIn(line) ||
            Regex("^\\d+[.)] ").containsMatchIn(line)

    private val colorMarker = TextMarkers.colorPattern
    private val underlineMarker = TextMarkers.underline

    fun inline(text: String): String {
        // Kolor i podkreślenie zapisujemy znacznikiem HTML, bo Markdown nie ma
        // na nie własnego zapisu. Wyjmujemy je przed ucieczką znaków, żeby
        // nie zamieniły się w widoczny napis z nawiasami trójkątnymi.
        val hidden = mutableListOf<String>()
        var prepared = colorMarker.replace(text) { match ->
            val color = Regex("""color:([^"]*)""").find(match.value)?.groupValues?.get(1).orEmpty()
            hidden += "<span style=\"color:${escape(color)}\">" +
                escape(match.groupValues[1]) + "</span>"
            placeholder("HTML", hidden.size - 1)
        }
        prepared = underlineMarker.replace(prepared) { match ->
            hidden += "<u>" + escape(match.groupValues[1]) + "</u>"
            placeholder("HTML", hidden.size - 1)
        }

        var result = escape(prepared)

        // Kod w tekście musi być pierwszy, żeby gwiazdki w kodzie nie robiły kursywy.
        val codeFragments = mutableListOf<String>()
        result = Regex("`([^`]+)`").replace(result) { match ->
            codeFragments += match.groupValues[1]
            "\u0000KOD${codeFragments.size - 1}\u0000"
        }

        // Wzory w tekście zostawiamy nietknięte dla KaTeX.
        val formulas = mutableListOf<String>()
        result = Regex("\\$([^$\n]+)\\$").replace(result) { match ->
            formulas += match.groupValues[1]
            "\u0000WZOR${formulas.size - 1}\u0000"
        }

        result = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)").replace(result) { match ->
            val alt = match.groupValues[1]
            val url = attachmentUrl(match.groupValues[2])
            "<img src=\"$url\" alt=\"$alt\">"
        }
        result = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(result) { match ->
            "<a href=\"${match.groupValues[2]}\">${match.groupValues[1]}</a>"
        }

        result = Regex("\\*\\*([^*]+)\\*\\*").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        result = Regex("__([^_]+)__").replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        result = Regex("(?<![*\\w])\\*([^*]+)\\*(?![*\\w])").replace(result) { "<em>${it.groupValues[1]}</em>" }
        result = Regex("(?<![_\\w])_([^_]+)_(?![_\\w])").replace(result) { "<em>${it.groupValues[1]}</em>" }
        result = Regex("~~([^~]+)~~").replace(result) { "<del>${it.groupValues[1]}</del>" }
        result = Regex("==([^=]+)==").replace(result) { "<mark>${it.groupValues[1]}</mark>" }

        codeFragments.forEachIndexed { number, code ->
            result = result.replace("\u0000KOD$number\u0000", "<code>$code</code>")
        }
        formulas.forEachIndexed { number, formula ->
            result = result.replace("\u0000WZOR$number\u0000", "\\($formula\\)")
        }
        hidden.forEachIndexed { number, html ->
            result = result.replace(placeholder("HTML", number), html)
        }
        return result
    }

    private fun placeholder(name: String, number: Int): String = "\u0000$name$number\u0000"

    fun attachmentUrl(path: String): String = when {
        path.startsWith("assets/") -> ATTACHMENT_BASE_URL + path.removePrefix("assets/")
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> ATTACHMENT_BASE_URL + path
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun firstLine(source: String): String = source
        .lineSequence()
        .map { it.trim().trimStart('#', ' ') }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}
