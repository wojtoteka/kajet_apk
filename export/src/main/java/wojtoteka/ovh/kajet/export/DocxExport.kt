package wojtoteka.ovh.kajet.export

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.TextMarkers
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExport {

    fun write(document: NoteDocument, output: OutputStream) {
        val body = buildString {
            append(paragraph(document.title, style = "Title"))
            when {
                document.text != null -> append(fromMarkdown(document.text!!.markdown))
                document.mindMap != null -> append(fromMindMap(document.mindMap!!))
                document.handwriting != null -> append(fromHandwriting(document))
            }
        }

        ZipOutputStream(output).use { zip ->
            putEntry(zip, "[Content_Types].xml", CONTENT_TYPES)
            putEntry(zip, "_rels/.rels", ROOT_RELATIONSHIPS)
            putEntry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELATIONSHIPS)
            putEntry(zip, "word/styles.xml", STYLES)
            putEntry(zip, "docProps/core.xml", coreProperties(document.title))
            putEntry(zip, "word/document.xml", documentXml(body))
        }
    }

    private fun fromMarkdown(markdown: String): String = buildString {
        for (line in markdown.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val level = trimmed.takeWhile { it == '#' }.length
            when {
                level in 1..3 && trimmed.length > level ->
                    append(paragraph(trimmed.drop(level + 1), style = "Heading$level"))

                Regex("^[-*+] \\[[ xX]] ").containsMatchIn(trimmed) -> {
                    val done = trimmed.contains("[x]", ignoreCase = true)
                    val content = trimmed.replace(Regex("^[-*+] \\[[ xX]] "), "")
                    append(paragraph((if (done) "[x]  " else "[ ]  ") + content, style = "ListParagraph"))
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    append(paragraph("•  " + trimmed.drop(2), style = "ListParagraph"))

                Regex("^\\d+[.)] ").containsMatchIn(trimmed) ->
                    append(paragraph(trimmed, style = "ListParagraph"))

                trimmed.startsWith("> ") ->
                    append(paragraph(trimmed.drop(2), style = "Quote", italic = true))

                trimmed.startsWith("![") ->
                    append(paragraph("[W tym miejscu jest obrazek: " + imageCaption(trimmed) + "]", italic = true))

                trimmed.startsWith("$$") ->
                    append(paragraph(trimmed.trim('$').trim(), monospace = true))

                else -> append(formattedParagraph(trimmed))
            }
        }
    }

    private fun fromMindMap(map: MindMapContent): String = buildString {
        append(paragraph("Węzły mapy", style = "Heading1"))
        val byId = map.nodes.associateBy { it.id }
        val hasParent = map.edges.map { it.toId }.toSet()
        val children = map.edges.groupBy({ it.fromId }, { it.toId })

        fun walk(id: String, depth: Int, visited: MutableSet<String>) {
            if (!visited.add(id)) return
            val node = byId[id] ?: return
            val indent = "    ".repeat(depth)
            append(paragraph(indent + "•  " + node.text.ifBlank { "Bez podpisu" }, style = "ListParagraph"))
            children[id].orEmpty().forEach { walk(it, depth + 1, visited) }
        }

        val visited = mutableSetOf<String>()
        map.nodes.filter { it.id !in hasParent }.forEach { walk(it.id, 0, visited) }
        map.nodes.forEach { walk(it.id, 0, visited) }
    }

    private fun fromHandwriting(document: NoteDocument): String = buildString {
        val pages = document.handwriting?.pages.orEmpty()
        append(
            paragraph(
                "Ta notatka jest pisana odręcznie. W pliku DOCX znajdziesz tylko " +
                    "pola tekstowe i pismo zamienione wcześniej na tekst. " +
                    "Do zapisania samego pisma użyj eksportu do pliku PDF.",
                italic = true,
            ),
        )
        pages.forEachIndexed { index, page ->
            val pieces = page.recognized.map { it.text } + page.texts.map { it.text }
            val nonEmpty = pieces.filter { it.isNotBlank() }
            if (nonEmpty.isEmpty()) return@forEachIndexed
            append(paragraph("Strona ${index + 1}", style = "Heading2"))
            nonEmpty.forEach { append(paragraph(it)) }
        }
    }

    private fun imageCaption(line: String): String =
        Regex("!\\[([^\\]]*)]").find(line)?.groupValues?.get(1)?.ifBlank { "bez opisu" } ?: "bez opisu"

    private fun formattedParagraph(text: String): String {
        val runs = StringBuilder()
        var position = 0
        for (match in decorationPattern.findAll(text)) {
            if (match.range.first > position) {
                runs.append(textRun(TextMarkers.plain(text.substring(position, match.range.first))))
            }
            val piece = match.value
            runs.append(
                when {
                    piece.startsWith("**") ->
                        textRun(piece.removeSurrounding("**"), bold = true)

                    piece.startsWith("~~") ->
                        textRun(piece.removeSurrounding("~~"), strikethrough = true)

                    piece.startsWith("==") ->
                        textRun(piece.removeSurrounding("=="), highlight = true)

                    piece.startsWith("`") ->
                        textRun(piece.trim('`'), monospace = true)

                    piece.startsWith("<u>") ->
                        textRun(piece.removeSurrounding("<u>", "</u>"), underline = true)

                    piece.startsWith("<span") -> {
                        val inner = TextMarkers.colorPattern.find(piece)
                        val color = Regex("""color:\s*#?([0-9a-fA-F]{6})""").find(piece)
                            ?.groupValues?.get(1)
                        textRun(inner?.groupValues?.get(1).orEmpty(), color = color)
                    }

                    else -> textRun(piece.trim('*'), italic = true)
                },
            )
            position = match.range.last + 1
        }
        if (position < text.length) {
            runs.append(textRun(TextMarkers.plain(text.substring(position))))
        }
        return "<w:p>$runs</w:p>"
    }

    private val decorationPattern = Regex(
        """<span style="color:[^"]*">[^<]*</span>""" +
            """|<u>[^<]*</u>""" +
            """|\*\*[^*]+\*\*""" +
            """|~~[^~]+~~""" +
            """|==[^=]+==""" +
            """|`[^`]+`""" +
            """|\*[^*]+\*""",
    )

    private fun paragraph(
        text: String,
        style: String? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        monospace: Boolean = false,
    ): String {
        val properties = if (style != null) "<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>" else ""
        return "<w:p>$properties${textRun(text, bold, italic, monospace)}</w:p>"
    }

    private fun textRun(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        monospace: Boolean = false,
        underline: Boolean = false,
        strikethrough: Boolean = false,
        highlight: Boolean = false,
        color: String? = null,
    ): String {
        val decorations = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (underline) append("<w:u w:val=\"single\"/>")
            if (strikethrough) append("<w:strike/>")
            if (highlight) append("<w:highlight w:val=\"yellow\"/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            if (monospace) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
        }
        val properties = if (decorations.isEmpty()) "" else "<w:rPr>$decorations</w:rPr>"
        return "<w:r>$properties<w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r>"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun putEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun documentXml(body: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body>
</w:document>"""

    private fun coreProperties(title: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>${escape(title)}</dc:title><dc:creator>Kajet</dc:creator>
</cp:coreProperties>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private const val ROOT_RELATIONSHIPS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELATIONSHIPS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="48"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:pPr><w:spacing w:before="280" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="34"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:pPr><w:spacing w:before="240" w:after="100"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:pPr><w:spacing w:before="200" w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:pPr><w:ind w:left="360"/><w:spacing w:after="60"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:pPr><w:ind w:left="480"/><w:spacing w:after="120"/></w:pPr><w:rPr><w:i/></w:rPr></w:style>
</w:styles>"""
}
