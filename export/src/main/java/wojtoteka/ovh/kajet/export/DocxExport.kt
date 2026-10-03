package wojtoteka.ovh.kajet.export

import wojtoteka.ovh.kajet.core.model.ImageLines
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.text.docxImageHere
import wojtoteka.ovh.kajet.core.text.words
import wojtoteka.ovh.kajet.core.model.NoteAlign
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.ParagraphAlign
import wojtoteka.ovh.kajet.core.model.RichTextCodec
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object DocxExport {

    @Synchronized
    fun write(document: NoteDocument, output: OutputStream) {
        align = null
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
            // Ułożenie akapitu siedzi w znaczniku obejmującym wiersz - w pliku
            // Worda to ułożenie akapitu (w:jc), a nie tekst.
            align = ParagraphAlign.alignOf(line.trim())
            val trimmed = ParagraphAlign.unwrap(line.trim()).trim()
            if (trimmed.isEmpty()) continue

            /*
              Treść każdego wiersza - także nagłówka, punktu, zadania i cytatu
              - idzie przez przebiegi formatu. Wcześniej tylko zwykły akapit:
              pogrubienie w nagłówku albo w liście trafiało do Worda jako
              gołe „**".
            */
            val level = RichTextCodec.headingLevelOfLine(trimmed)
            val task = taskLine.find(trimmed)
            when {
                level > 0 -> {
                    val content = trimmed.substring(RichTextCodec.headingPrefixLength(trimmed))
                    // Word zna trzy style nagłówków, jak notatka na stronie.
                    append(formattedParagraph(content, style = "Heading${level.coerceAtMost(3)}"))
                }

                task != null -> {
                    val done = task.groupValues[1].equals("x", ignoreCase = true)
                    append(
                        formattedParagraph(
                            trimmed.substring(task.value.length),
                            style = "ListParagraph",
                            lead = if (done) "[x]  " else "[ ]  ",
                        ),
                    )
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                    append(formattedParagraph(trimmed.drop(2), style = "ListParagraph", lead = "•  "))

                numbered.find(trimmed) != null -> {
                    val number = numbered.find(trimmed)!!.value
                    append(formattedParagraph(trimmed.substring(number.length), style = "ListParagraph", lead = number))
                }

                trimmed.startsWith("> ") ->
                    append(formattedParagraph(trimmed.drop(2), style = "Quote"))

                trimmed.startsWith("![") -> {
                    // Kilka zdjęć obok siebie to kilka zdjęć - każde ma w pliku
                    // Worda swój wpis, a nie jeden wspólny.
                    val photos = ImageLines.read(trimmed).orEmpty()
                    if (photos.isEmpty()) {
                        append(paragraph(words.docxImageHere(imageCaption(trimmed)), italic = true))
                    } else {
                        photos.forEach { photo ->
                            val caption = photo.alt.ifBlank { words.noDescription }
                            append(paragraph(words.docxImageHere(caption), italic = true))
                        }
                    }
                }

                trimmed.startsWith("$$") ->
                    append(paragraph(trimmed.trim('$').trim(), monospace = true))

                else -> append(formattedParagraph(trimmed))
            }
        }
    }

    private fun fromMindMap(map: MindMapContent): String = buildString {
        append(paragraph(words.mapNodes, style = "Heading1"))
        val byId = map.nodes.associateBy { it.id }
        val hasParent = map.edges.map { it.toId }.toSet()
        val children = map.edges.groupBy({ it.fromId }, { it.toId })

        fun walk(id: String, depth: Int, visited: MutableSet<String>) {
            if (!visited.add(id)) return
            val node = byId[id] ?: return
            val indent = "    ".repeat(depth)
            append(paragraph(indent + "•  " + node.text.ifBlank { words.noCaption }, style = "ListParagraph"))
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
                words.handwrittenInDocx,
                italic = true,
            ),
        )
        pages.forEachIndexed { index, page ->
            val pieces = page.recognized.map { it.text } + page.texts.map { it.text }
            val nonEmpty = pieces.filter { it.isNotBlank() }
            if (nonEmpty.isEmpty()) return@forEachIndexed
            append(paragraph("${words.pageWord} ${index + 1}", style = "Heading2"))
            nonEmpty.forEach { append(paragraph(it)) }
        }
    }

    private fun imageCaption(line: String): String {
        val alt = Regex("!\\[([^\\]]*)]").find(line)?.groupValues?.get(1).orEmpty()
        return ImageLines.plainAlt(alt).ifBlank { words.noDescription }
    }

    private val taskLine = Regex("""^[-*+] \[([ xX])] """)
    private val numbered = Regex("""^\d+[.)] """)

    /**
     * Akapit z formatami fragmentów - przebiegi czyta ten sam [RichTextCodec]
     * co notatka (przez [PdfMarkdown.runs]), więc formaty jeden w drugim
     * i nagłówek na kawałku zdania przechodzą do Worda tak, jak wyglądają.
     * [lead] to znak listy albo kwadracik przed treścią.
     */
    private fun formattedParagraph(text: String, style: String? = null, lead: String = ""): String {
        val runs = StringBuilder()
        if (lead.isNotEmpty()) runs.append(textRun(lead))
        for (run in PdfMarkdown.runs(text)) {
            runs.append(
                textRun(
                    text = run.text,
                    bold = run.bold || run.heading != null,
                    italic = run.italic,
                    monospace = run.code,
                    underline = run.underline,
                    strikethrough = run.strike,
                    highlight = run.highlight,
                    color = run.color?.let { "%06X".format(it and 0xFFFFFF) },
                    halfPoints = run.sizePx?.let { (it / TextContent.DEFAULT_SIZE * BODY_HALF_POINTS).roundToInt() }
                        ?: run.heading?.let { headingHalfPoints(it) },
                ),
            )
        }
        return "<w:p>${paragraphProperties(style)}$runs</w:p>"
    }

    /** Pismo dokumentu: 11 pt, czyli 22 półpunkty (tak jak w [STYLES]). */
    private const val BODY_HALF_POINTS = 22

    /** Nagłówek na kawałku zdania - wielkość jak w stylach nagłówków niżej. */
    private fun headingHalfPoints(level: Int): Int = when (level) {
        1 -> 34
        2 -> 28
        3 -> 24
        else -> BODY_HALF_POINTS
    }

    private fun paragraph(
        text: String,
        style: String? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        monospace: Boolean = false,
    ): String {
        val properties = paragraphProperties(style)
        return "<w:p>$properties${textRun(text, bold, italic, monospace)}</w:p>"
    }

    /** Ułożenie akapitu, który jest właśnie składany - null to zwykłe, do lewej. */
    private var align: NoteAlign? = null

    private fun paragraphProperties(style: String?): String {
        val inner = buildString {
            if (style != null) append("<w:pStyle w:val=\"$style\"/>")
            when (align) {
                NoteAlign.CENTER -> append("<w:jc w:val=\"center\"/>")
                NoteAlign.RIGHT -> append("<w:jc w:val=\"right\"/>")
                else -> Unit
            }
        }
        return if (inner.isEmpty()) "" else "<w:pPr>$inner</w:pPr>"
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
        /** Wielkość pisma w półpunktach; null - wielkość ze stylu akapitu. */
        halfPoints: Int? = null,
    ): String {
        val decorations = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (underline) append("<w:u w:val=\"single\"/>")
            if (strikethrough) append("<w:strike/>")
            if (highlight) append("<w:highlight w:val=\"yellow\"/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            if (monospace) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
            if (halfPoints != null) append("<w:sz w:val=\"$halfPoints\"/>")
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
