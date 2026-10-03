package wojtoteka.ovh.kajet.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/*
  Port testów note-title.test.ts z serwera - obie strony mają podpowiadać
  ten sam tytuł z tej samej treści.
*/
class NoteTitlesTest {

    @Test
    fun `bierze pierwszy wiersz`() {
        assertThat(NoteTitles.fromMarkdown("Lista zakupów\nmleko\nchleb"))
            .isEqualTo("Lista zakupów")
    }

    @Test
    fun `nie nazywa notatki od wiersza pisanego w tej chwili`() {
        // Autozapis rusza ułamek sekundy po pierwszym znaku - „L" nie może
        // zostać tytułem notatki na zawsze.
        assertThat(NoteTitles.fromMarkdown("L")).isNull()
        assertThat(NoteTitles.fromMarkdown("Lista")).isNull()
        // Dość długi wiersz mówi już, o czym to jest, nawet bez Entera.
        assertThat(NoteTitles.fromMarkdown("Lista zakupów na sobotę"))
            .isEqualTo("Lista zakupów na sobotę")
        // Enter znaczy „ten wiersz mam skończony".
        assertThat(NoteTitles.fromMarkdown("Lista\n")).isEqualTo("Lista")
    }

    @Test
    fun `pomija puste wiersze na poczatku`() {
        assertThat(NoteTitles.fromMarkdown("\n\n   \nWłaściwa treść"))
            .isEqualTo("Właściwa treść")
    }

    @Test
    fun `z naglowka na kawalku zdania zdejmuje znacznik`() {
        // Ten sam wynik co note-title.ts na serwerze - inaczej notatki
        // przetytułowywałyby się nawzajem przy synchronizacji.
        assertThat(NoteTitles.fromMarkdown("<span class=\"h1\">Zakupy</span> na sobotę\nmleko"))
            .isEqualTo("Zakupy na sobotę")
        assertThat(TextMarkers.plain("<span class=\"h2\">Ala</span> ma kota")).isEqualTo("Ala ma kota")
    }

    @Test
    fun `z naglowka zdejmuje kratki`() {
        assertThat(NoteTitles.fromMarkdown("## Zebranie w piątek\n\ntreść"))
            .isEqualTo("Zebranie w piątek")
    }

    @Test
    fun `z pozycji listy zdejmuje znaczek`() {
        assertThat(NoteTitles.fromMarkdown("- kupić mleko\n")).isEqualTo("kupić mleko")
        assertThat(NoteTitles.fromMarkdown("1. pierwszy punkt\n")).isEqualTo("pierwszy punkt")
        assertThat(NoteTitles.fromMarkdown("- [ ] zadanie do zrobienia\n"))
            .isEqualTo("zadanie do zrobienia")
    }

    @Test
    fun `z cytatu tez`() {
        assertThat(NoteTitles.fromMarkdown("> tak powiedział\n")).isEqualTo("tak powiedział")
    }

    @Test
    fun `zdejmuje pogrubienie, kursywe i kolor`() {
        assertThat(NoteTitles.fromMarkdown("**Ważne** i *pilne*\n")).isEqualTo("Ważne i pilne")
        assertThat(
            NoteTitles.fromMarkdown("""<span style="color:#c81e1e">Czerwone</span> słowo"""),
        ).isEqualTo("Czerwone słowo")
        assertThat(NoteTitles.fromMarkdown("<u>Podkreślone</u>\n")).isEqualTo("Podkreślone")
        assertThat(NoteTitles.fromMarkdown("`kod` w zdaniu\n")).isEqualTo("kod w zdaniu")
    }

    @Test
    fun `zdejmuje tez rozmiar pisma i zagniezdzone znaczniki`() {
        assertThat(
            NoteTitles.fromMarkdown("""<span style="font-size:21px">Duże słowa</span> w zdaniu"""),
        ).isEqualTo("Duże słowa w zdaniu")
        assertThat(
            NoteTitles.fromMarkdown(
                """# <span style="font-size:21px"><span style="color:#665222">Ważne</span></span> słowo""" + "\n",
            ),
        ).isEqualTo("Ważne słowo")
    }

    @Test
    fun `z odnosnika zostaje sam opis`() {
        assertThat(NoteTitles.fromMarkdown("[Kajet](https://kajet.wojtoteka.ovh) to notatnik"))
            .isEqualTo("Kajet to notatnik")
    }

    @Test
    fun `przechodzi nad blokiem kodu i wzorem`() {
        assertThat(NoteTitles.fromMarkdown("```\nprint(1)\n```\nOpis programu"))
            .isEqualTo("Opis programu")
        assertThat(NoteTitles.fromMarkdown("$$\na+b\n$$\nWzór na sumę"))
            .isEqualTo("Wzór na sumę")
    }

    @Test
    fun `pomija linie pozioma`() {
        assertThat(NoteTitles.fromMarkdown("---\nTreść pod linią")).isEqualTo("Treść pod linią")
    }

    @Test
    fun `obcina bardzo dlugi wiersz`() {
        val long = "słowo ".repeat(40).trim()
        val title = NoteTitles.fromMarkdown(long)

        assertThat(title).isNotNull()
        // 48 znaków granicy plus wielokropek - ta sama liczba co na serwerze.
        assertThat(title!!.length).isAtMost(51)
        assertThat(title.endsWith("...")).isTrue()
    }

    @Test
    fun `tnie na spacji, nie w polowie slowa`() {
        val zdanie = "Pomaganie drugiemu człowiekowi to jedna z najważniejszych wartości w życiu."
        val title = NoteTitles.fromMarkdown(zdanie)!!

        assertThat(title.endsWith("...")).isTrue()
        val bezKropek = title.dropLast(3)
        assertThat(zdanie.startsWith(bezKropek)).isTrue()
        assertThat(zdanie[bezKropek.length]).isEqualTo(' ')
    }

    @Test
    fun `jedno slowo dluzsze niz granica tniemy rowno`() {
        assertThat(NoteTitles.fromMarkdown("a".repeat(120))).isEqualTo("a".repeat(48) + "...")
    }

    @Test
    fun `pusta tresc nie daje tytulu`() {
        assertThat(NoteTitles.fromMarkdown("")).isNull()
        assertThat(NoteTitles.fromMarkdown("\n\n   \n")).isNull()
        assertThat(NoteTitles.fromMarkdown("```\nsam kod\n```")).isNull()
        // Samo zdjęcie bez opisu też nie ma czego nazwać.
        assertThat(NoteTitles.fromMarkdown("![](assets/kot.png)")).isNull()
    }

    @Test
    fun `mapa mysli bierze pierwszy opisany wezel`() {
        fun node(text: String) = MindNode(id = text.ifBlank { "pusty" }, x = 0f, y = 0f, text = text)

        assertThat(
            NoteTitles.fromMindMap(listOf(node(""), node("  "), node("Plan roku"))),
        ).isEqualTo("Plan roku")
        assertThat(NoteTitles.fromMindMap(emptyList())).isNull()
        assertThat(NoteTitles.fromMindMap(listOf(node("**Środek** mapy"))))
            .isEqualTo("Środek mapy")
    }

    @Test
    fun `podstawiony tytul poznaje sie w obu jezykach`() {
        assertThat(NoteTitles.isPlaceholder("Bez tytułu")).isTrue()
        assertThat(NoteTitles.isPlaceholder("Bez nazwy")).isTrue()
        assertThat(NoteTitles.isPlaceholder("Untitled")).isTrue()
        assertThat(NoteTitles.isPlaceholder("")).isTrue()
        assertThat(NoteTitles.isPlaceholder("   ")).isTrue()
        assertThat(NoteTitles.isPlaceholder("Lista zakupów")).isFalse()
    }
}
