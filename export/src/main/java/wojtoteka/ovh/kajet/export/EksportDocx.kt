package wojtoteka.ovh.kajet.export

import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Eksport do pliku DOCX budowany od zera.
 *
 * Nie ma dziś biblioteki do formatu OOXML, która sensownie działa na Androidzie.
 * Apache POI ciągnie za sobą kilkanaście megabajtów zależności napisanych
 * pod komputer i przekracza limit metod, więc plik składamy sami.
 * DOCX to zwykły ZIP z kilkoma plikami XML, a my potrzebujemy tylko
 * akapitów, nagłówków, list i pogrubienia.
 *
 * Czego to rozwiązanie nie umie i o czym trzeba wiedzieć:
 * osadzania obrazków, tabel, wzorów matematycznych, przypisów ani stylów
 * innych niż wypisane niżej. Wzory zostają w pliku jako tekst w postaci LaTeX,
 * a obrazki jako informacja, że w tym miejscu było zdjęcie. Do notatek
 * ze zdjęciami i wzorami lepszy jest eksport do pliku PDF.
 */
object EksportDocx {

    fun zapisz(dokument: NoteDocument, wyjscie: OutputStream) {
        val akapity = buildString {
            append(akapit(dokument.title, styl = "Title"))
            when {
                dokument.text != null -> append(zMarkdown(dokument.text!!.markdown))
                dokument.mindMap != null -> append(zMapy(dokument.mindMap!!))
                dokument.handwriting != null -> append(zPismaOdrecznego(dokument))
            }
        }

        ZipOutputStream(wyjscie).use { zip ->
            wpisz(zip, "[Content_Types].xml", TYPY_TRESCI)
            wpisz(zip, "_rels/.rels", RELACJE_GLOWNE)
            wpisz(zip, "word/_rels/document.xml.rels", RELACJE_DOKUMENTU)
            wpisz(zip, "word/styles.xml", STYLE)
            wpisz(zip, "docProps/core.xml", wlasciwosci(dokument.title))
            wpisz(zip, "word/document.xml", dokumentXml(akapity))
        }
    }

    private fun zMarkdown(markdown: String): String = buildString {
        for (wiersz in markdown.split('\n')) {
            val przyciety = wiersz.trim()
            if (przyciety.isEmpty()) continue

            val poziom = przyciety.takeWhile { it == '#' }.length
            when {
                poziom in 1..3 && przyciety.length > poziom ->
                    append(akapit(przyciety.drop(poziom + 1), styl = "Heading$poziom"))

                Regex("^[-*+] \\[[ xX]] ").containsMatchIn(przyciety) -> {
                    val zrobione = przyciety.contains("[x]", ignoreCase = true)
                    val tresc = przyciety.replace(Regex("^[-*+] \\[[ xX]] "), "")
                    append(akapit((if (zrobione) "[x]  " else "[ ]  ") + tresc, styl = "ListParagraph"))
                }

                przyciety.startsWith("- ") || przyciety.startsWith("* ") ->
                    append(akapit("•  " + przyciety.drop(2), styl = "ListParagraph"))

                Regex("^\\d+[.)] ").containsMatchIn(przyciety) ->
                    append(akapit(przyciety, styl = "ListParagraph"))

                przyciety.startsWith("> ") ->
                    append(akapit(przyciety.drop(2), styl = "Quote", kursywa = true))

                przyciety.startsWith("![") ->
                    append(akapit("[W tym miejscu jest obrazek: " + opisObrazka(przyciety) + "]", kursywa = true))

                przyciety.startsWith("$$") ->
                    append(akapit(przyciety.trim('$').trim(), monospace = true))

                else -> append(akapitZFormatowaniem(przyciety))
            }
        }
    }

    private fun zMapy(mapa: MindMapContent): String = buildString {
        append(akapit("Węzły mapy", styl = "Heading1"))
        val poId = mapa.nodes.associateBy { it.id }
        val maRodzica = mapa.edges.map { it.toId }.toSet()
        val dzieci = mapa.edges.groupBy({ it.fromId }, { it.toId })

        fun zejdz(id: String, poziom: Int, odwiedzone: MutableSet<String>) {
            if (!odwiedzone.add(id)) return
            val wezel = poId[id] ?: return
            val wciecie = "    ".repeat(poziom)
            append(akapit(wciecie + "•  " + wezel.text.ifBlank { "Bez podpisu" }, styl = "ListParagraph"))
            dzieci[id].orEmpty().forEach { zejdz(it, poziom + 1, odwiedzone) }
        }

        val odwiedzone = mutableSetOf<String>()
        mapa.nodes.filter { it.id !in maRodzica }.forEach { zejdz(it.id, 0, odwiedzone) }
        mapa.nodes.forEach { zejdz(it.id, 0, odwiedzone) }
    }

    private fun zPismaOdrecznego(dokument: NoteDocument): String = buildString {
        val strony = dokument.handwriting?.pages.orEmpty()
        append(
            akapit(
                "Ta notatka jest pisana odręcznie. W pliku DOCX znajdziesz tylko " +
                    "pola tekstowe i pismo zamienione wcześniej na tekst. " +
                    "Do zapisania samego pisma użyj eksportu do pliku PDF.",
                kursywa = true,
            ),
        )
        strony.forEachIndexed { numer, kartka ->
            val fragmenty = kartka.recognized.map { it.text } + kartka.texts.map { it.text }
            val niepuste = fragmenty.filter { it.isNotBlank() }
            if (niepuste.isEmpty()) return@forEachIndexed
            append(akapit("Strona ${numer + 1}", styl = "Heading2"))
            niepuste.forEach { append(akapit(it)) }
        }
    }

    private fun opisObrazka(wiersz: String): String =
        Regex("!\\[([^\\]]*)]").find(wiersz)?.groupValues?.get(1)?.ifBlank { "bez opisu" } ?: "bez opisu"

    /** Akapit z pogrubieniem i kursywą rozpoznanymi z Markdown. */
    private fun akapitZFormatowaniem(tekst: String): String {
        val fragmenty = StringBuilder()
        val wzorzec = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|`[^`]+`)")
        var pozycja = 0
        for (dopasowanie in wzorzec.findAll(tekst)) {
            if (dopasowanie.range.first > pozycja) {
                fragmenty.append(przebieg(tekst.substring(pozycja, dopasowanie.range.first)))
            }
            val fragment = dopasowanie.value
            when {
                fragment.startsWith("**") -> fragmenty.append(przebieg(fragment.trim('*'), pogrubienie = true))
                fragment.startsWith("`") -> fragmenty.append(przebieg(fragment.trim('`'), monospace = true))
                else -> fragmenty.append(przebieg(fragment.trim('*'), kursywa = true))
            }
            pozycja = dopasowanie.range.last + 1
        }
        if (pozycja < tekst.length) fragmenty.append(przebieg(tekst.substring(pozycja)))
        return "<w:p>$fragmenty</w:p>"
    }

    private fun akapit(
        tekst: String,
        styl: String? = null,
        pogrubienie: Boolean = false,
        kursywa: Boolean = false,
        monospace: Boolean = false,
    ): String {
        val wlasciwosci = if (styl != null) "<w:pPr><w:pStyle w:val=\"$styl\"/></w:pPr>" else ""
        return "<w:p>$wlasciwosci${przebieg(tekst, pogrubienie, kursywa, monospace)}</w:p>"
    }

    private fun przebieg(
        tekst: String,
        pogrubienie: Boolean = false,
        kursywa: Boolean = false,
        monospace: Boolean = false,
    ): String {
        val ozdoby = buildString {
            if (pogrubienie) append("<w:b/>")
            if (kursywa) append("<w:i/>")
            if (monospace) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
        }
        val wlasciwosci = if (ozdoby.isEmpty()) "" else "<w:rPr>$ozdoby</w:rPr>"
        return "<w:r>$wlasciwosci<w:t xml:space=\"preserve\">${uciekaj(tekst)}</w:t></w:r>"
    }

    private fun uciekaj(tekst: String): String = tekst
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun wpisz(zip: ZipOutputStream, nazwa: String, tresc: String) {
        zip.putNextEntry(ZipEntry(nazwa))
        zip.write(tresc.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun dokumentXml(akapity: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$akapity<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body>
</w:document>"""

    private fun wlasciwosci(tytul: String): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>${uciekaj(tytul)}</dc:title><dc:creator>Kajet</dc:creator>
</cp:coreProperties>"""

    private const val TYPY_TRESCI = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

    private const val RELACJE_GLOWNE = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

    private const val RELACJE_DOKUMENTU = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLE = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
