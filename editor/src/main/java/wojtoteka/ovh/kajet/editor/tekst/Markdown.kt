package wojtoteka.ovh.kajet.editor.tekst

/**
 * Zamiana Markdown na HTML pokazywany w podglądzie.
 *
 * Piszemy to sami zamiast dokładać bibliotekę, bo potrzebujemy trzech rzeczy,
 * których gotowe biblioteki nie robią po naszemu: pola do odhaczania zadań
 * z numerem wiersza, wzorów w LaTeX zostawionych w spokoju dla KaTeX
 * oraz obrazków wskazujących na katalog assets wewnątrz notatki.
 *
 * Obsługiwane: nagłówki, pogrubienie, kursywa, kod w tekście, blok kodu,
 * cytat, listy zwykłe i numerowane, listy zadań, obrazki, odnośniki,
 * linia pozioma i wzory matematyczne.
 */
object Markdown {

    /** Adres, spod którego podgląd prosi o załączniki notatki. */
    const val ADRES_ZALACZNIKOW = "https://kajet.local/assets/"

    fun doHtml(zrodlo: String): String {
        val wiersze = zrodlo.split('\n')
        val wynik = StringBuilder()
        var i = 0
        var wListe: String? = null

        fun zamknijListe() {
            if (wListe != null) {
                wynik.append("</").append(wListe).append(">\n")
                wListe = null
            }
        }

        fun otworzListe(rodzaj: String) {
            if (wListe != rodzaj) {
                zamknijListe()
                wynik.append("<").append(rodzaj).append(">\n")
                wListe = rodzaj
            }
        }

        while (i < wiersze.size) {
            val wiersz = wiersze[i]
            val przyciety = wiersz.trim()

            // Blok kodu
            if (przyciety.startsWith("```")) {
                zamknijListe()
                val jezyk = przyciety.removePrefix("```").trim()
                val kod = StringBuilder()
                i++
                while (i < wiersze.size && !wiersze[i].trim().startsWith("```")) {
                    kod.append(uciekaj(wiersze[i])).append('\n')
                    i++
                }
                i++
                wynik.append("<pre class=\"kod\" data-jezyk=\"")
                    .append(uciekaj(jezyk))
                    .append("\"><code>")
                    .append(kod)
                    .append("</code></pre>\n")
                continue
            }

            // Wzór w osobnej linii. Zostawiamy go w spokoju, policzy go KaTeX.
            if (przyciety.startsWith("$$")) {
                zamknijListe()
                val wzor = StringBuilder()
                if (przyciety.length > 2 && przyciety.endsWith("$$") && przyciety.length > 4) {
                    wzor.append(przyciety.removeSurrounding("$$"))
                    i++
                } else {
                    wzor.append(przyciety.removePrefix("$$")).append('\n')
                    i++
                    while (i < wiersze.size && !wiersze[i].trim().endsWith("$$")) {
                        wzor.append(wiersze[i]).append('\n')
                        i++
                    }
                    if (i < wiersze.size) {
                        wzor.append(wiersze[i].trim().removeSuffix("$$"))
                        i++
                    }
                }
                wynik.append("<div class=\"wzor\">$$").append(uciekaj(wzor.toString())).append("$$</div>\n")
                continue
            }

            if (przyciety.isEmpty()) {
                zamknijListe()
                i++
                continue
            }

            if (przyciety == "---" || przyciety == "***" || przyciety == "___") {
                zamknijListe()
                wynik.append("<hr>\n")
                i++
                continue
            }

            // Nagłówki
            val poziom = przyciety.takeWhile { it == '#' }.length
            if (poziom in 1..6 && przyciety.length > poziom && przyciety[poziom] == ' ') {
                zamknijListe()
                val tresc = wLinii(przyciety.drop(poziom + 1))
                wynik.append("<h").append(poziom).append('>').append(tresc)
                    .append("</h").append(poziom).append(">\n")
                i++
                continue
            }

            // Cytat
            if (przyciety.startsWith("> ")) {
                zamknijListe()
                val tresc = StringBuilder()
                while (i < wiersze.size && wiersze[i].trim().startsWith(">")) {
                    tresc.append(wLinii(wiersze[i].trim().removePrefix(">").trim())).append(' ')
                    i++
                }
                wynik.append("<blockquote>").append(tresc.toString().trim()).append("</blockquote>\n")
                continue
            }

            // Lista zadań
            val zadanie = Regex("^[-*+] \\[([ xX])] (.*)$").find(przyciety)
            if (zadanie != null) {
                otworzListe("ul")
                val odhaczone = zadanie.groupValues[1].lowercase() == "x"
                wynik.append("<li class=\"zadanie\"><input type=\"checkbox\" data-wiersz=\"")
                    .append(i)
                    .append('"')
                    .append(if (odhaczone) " checked" else "")
                    .append("><span")
                    .append(if (odhaczone) " class=\"zrobione\"" else "")
                    .append('>')
                    .append(wLinii(zadanie.groupValues[2]))
                    .append("</span></li>\n")
                i++
                continue
            }

            // Lista zwykła
            if (Regex("^[-*+] ").containsMatchIn(przyciety)) {
                otworzListe("ul")
                wynik.append("<li>").append(wLinii(przyciety.drop(2))).append("</li>\n")
                i++
                continue
            }

            // Lista numerowana
            val numerowana = Regex("^(\\d+)[.)] (.*)$").find(przyciety)
            if (numerowana != null) {
                otworzListe("ol")
                wynik.append("<li>").append(wLinii(numerowana.groupValues[2])).append("</li>\n")
                i++
                continue
            }

            // Zwykły akapit. Kolejne wiersze bez pustej linii sklejamy w jeden.
            zamknijListe()
            val akapit = StringBuilder(wiersz.trim())
            i++
            while (i < wiersze.size && wiersze[i].isNotBlank() && !zaczynaBlok(wiersze[i].trim())) {
                akapit.append(' ').append(wiersze[i].trim())
                i++
            }
            wynik.append("<p>").append(wLinii(akapit.toString())).append("</p>\n")
        }
        zamknijListe()
        return wynik.toString()
    }

    private fun zaczynaBlok(wiersz: String): Boolean =
        wiersz.startsWith("#") ||
            wiersz.startsWith("> ") ||
            wiersz.startsWith("```") ||
            wiersz.startsWith("$$") ||
            wiersz == "---" ||
            Regex("^[-*+] ").containsMatchIn(wiersz) ||
            Regex("^\\d+[.)] ").containsMatchIn(wiersz)

    /** Znaczniki działające w środku wiersza. */
    fun wLinii(tekst: String): String {
        var wynik = uciekaj(tekst)

        // Kod w tekście musi być pierwszy, żeby gwiazdki w kodzie nie robiły kursywy.
        val fragmentyKodu = mutableListOf<String>()
        wynik = Regex("`([^`]+)`").replace(wynik) { dopasowanie ->
            fragmentyKodu += dopasowanie.groupValues[1]
            "\u0000KOD${fragmentyKodu.size - 1}\u0000"
        }

        // Wzory w tekście zostawiamy nietknięte dla KaTeX.
        val wzory = mutableListOf<String>()
        wynik = Regex("\\$([^$\n]+)\\$").replace(wynik) { dopasowanie ->
            wzory += dopasowanie.groupValues[1]
            "\u0000WZOR${wzory.size - 1}\u0000"
        }

        wynik = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)").replace(wynik) { dopasowanie ->
            val opis = dopasowanie.groupValues[1]
            val adres = adresZalacznika(dopasowanie.groupValues[2])
            "<img src=\"$adres\" alt=\"$opis\">"
        }
        wynik = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(wynik) { dopasowanie ->
            "<a href=\"${dopasowanie.groupValues[2]}\">${dopasowanie.groupValues[1]}</a>"
        }

        wynik = Regex("\\*\\*([^*]+)\\*\\*").replace(wynik) { "<strong>${it.groupValues[1]}</strong>" }
        wynik = Regex("__([^_]+)__").replace(wynik) { "<strong>${it.groupValues[1]}</strong>" }
        wynik = Regex("(?<![*\\w])\\*([^*]+)\\*(?![*\\w])").replace(wynik) { "<em>${it.groupValues[1]}</em>" }
        wynik = Regex("(?<![_\\w])_([^_]+)_(?![_\\w])").replace(wynik) { "<em>${it.groupValues[1]}</em>" }
        wynik = Regex("~~([^~]+)~~").replace(wynik) { "<del>${it.groupValues[1]}</del>" }

        fragmentyKodu.forEachIndexed { numer, kod ->
            wynik = wynik.replace("\u0000KOD$numer\u0000", "<code>$kod</code>")
        }
        wzory.forEachIndexed { numer, wzor ->
            wynik = wynik.replace("\u0000WZOR$numer\u0000", "\\($wzor\\)")
        }
        return wynik
    }

    /** Obrazki z katalogu notatki dostają adres, który przechwytuje podgląd. */
    fun adresZalacznika(sciezka: String): String = when {
        sciezka.startsWith("assets/") -> ADRES_ZALACZNIKOW + sciezka.removePrefix("assets/")
        sciezka.startsWith("http://") || sciezka.startsWith("https://") -> sciezka
        else -> ADRES_ZALACZNIKOW + sciezka
    }

    fun uciekaj(tekst: String): String = tekst
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * Odhacza albo odznacza zadanie w podanym wierszu.
     * Numer wiersza bierze się z podglądu, więc liczymy od zera.
     */
    fun przelaczZadanie(zrodlo: String, numerWiersza: Int): String {
        val wiersze = zrodlo.split('\n').toMutableList()
        if (numerWiersza !in wiersze.indices) return zrodlo
        val wiersz = wiersze[numerWiersza]
        wiersze[numerWiersza] = when {
            wiersz.contains("[ ]") -> wiersz.replaceFirst("[ ]", "[x]")
            wiersz.contains("[x]") -> wiersz.replaceFirst("[x]", "[ ]")
            wiersz.contains("[X]") -> wiersz.replaceFirst("[X]", "[ ]")
            else -> wiersz
        }
        return wiersze.joinToString("\n")
    }

    /** Pierwszy nagłówek albo pierwsze zdanie. Używane jako podpowiedź tytułu. */
    fun pierwszaLinia(zrodlo: String): String = zrodlo
        .lineSequence()
        .map { it.trim().trimStart('#', ' ') }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}
