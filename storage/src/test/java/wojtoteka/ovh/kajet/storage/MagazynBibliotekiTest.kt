package wojtoteka.ovh.kajet.storage

import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.File

/**
 * Warstwa plików sprawdzana na prawdziwym katalogu na dysku.
 *
 * DocumentFile działa tak samo na katalogu wskazanym przez użytkownika
 * i na zwykłym katalogu, więc te testy chodzą po tym samym kodzie,
 * co aplikacja na tablecie.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MagazynBibliotekiTest {

    @get:Rule
    val katalogTymczasowy = TemporaryFolder()

    private lateinit var korzen: File
    private lateinit var magazyn: MagazynBiblioteki

    @Before
    fun przygotuj() {
        korzen = katalogTymczasowy.newFolder("biblioteka")
        val context = RuntimeEnvironment.getApplication()
        magazyn = MagazynBiblioteki(context.contentResolver, DocumentFile.fromFile(korzen))
    }

    @Test
    fun `nowy folder powstaje jako katalog na dysku`() {
        val folder = magazyn.utworzFolder("", "Matematyka", colorId = "morski", iconId = "dzialania")

        assertThat(File(korzen, "Matematyka").isDirectory).isTrue()
        assertThat(File(korzen, "Matematyka/folder.json").isFile).isTrue()
        assertThat(folder.path).isEqualTo("Matematyka")
        assertThat(folder.colorId).isEqualTo("morski")
    }

    @Test
    fun `folder z dwukropkiem dostaje bezpieczna nazwe na dysku, ale zachowuje prawdziwa`() {
        val folder = magazyn.utworzFolder("", "Fizyka: mechanika")

        assertThat(File(korzen, "Fizyka_ mechanika").isDirectory).isTrue()
        assertThat(folder.name).isEqualTo("Fizyka: mechanika")

        val zListy = magazyn.wypisz("").first()
        assertThat(zListy.name).isEqualTo("Fizyka: mechanika")
        assertThat(zListy.path).isEqualTo("Fizyka_ mechanika")
    }

    @Test
    fun `zagniezdzanie folderow nie ma ograniczenia`() {
        magazyn.utworzFolder("", "Szkoła")
        magazyn.utworzFolder("Szkoła", "Matematyka")
        magazyn.utworzFolder("Szkoła/Matematyka", "Całki")
        magazyn.utworzFolder("Szkoła/Matematyka/Całki", "Podstawienia")

        assertThat(File(korzen, "Szkoła/Matematyka/Całki/Podstawienia").isDirectory).isTrue()
        assertThat(magazyn.wypisz("Szkoła/Matematyka/Całki")).hasSize(1)
    }

    @Test
    fun `notatka to katalog z koncowka note i plikiem content json`() {
        val notatka = magazyn.utworzNotatke(
            "", "Pochodne", NoteKind.ODRECZNA, PageMode.A4, PageBackground.KRATKA,
        )

        val katalog = File(korzen, "Pochodne.note")
        assertThat(katalog.isDirectory).isTrue()
        assertThat(File(katalog, "content.json").isFile).isTrue()

        val dokument = magazyn.czytajNotatke(notatka.path)
        assertThat(dokument.title).isEqualTo("Pochodne")
        assertThat(dokument.kind).isEqualTo(NoteKind.ODRECZNA)
        val odreczna = dokument.handwriting!!
        assertThat(odreczna.background).isEqualTo(PageBackground.KRATKA)
        assertThat(odreczna.pages).hasSize(1)
    }

    @Test
    fun `zapis notatki zostawia kopie na wypadek przerwanego zapisu`() {
        val notatka = magazyn.utworzNotatke("", "Zadania", NoteKind.TEKSTOWA)
        val dokument = magazyn.czytajNotatke(notatka.path)
        magazyn.zapiszNotatke(notatka.path, dokument.copy(text = TextContent(markdown = "# Zadanie 1")))

        val katalog = File(korzen, "Zadania.note")
        assertThat(File(katalog, MagazynBiblioteki.PLIK_KOPII).isFile).isTrue()
        assertThat(File(katalog, "content.json").readText()).contains("Zadanie 1")
        assertThat(File(katalog, MagazynBiblioteki.PLIK_KOPII).readText()).contains("Zadanie 1")
    }

    @Test
    fun `uszkodzony plik glowny nie kasuje notatki, bo wczytuje sie kopia`() {
        val notatka = magazyn.utworzNotatke("", "Ważne", NoteKind.TEKSTOWA)
        val dokument = magazyn.czytajNotatke(notatka.path)
        magazyn.zapiszNotatke(notatka.path, dokument.copy(text = TextContent(markdown = "treść nie do stracenia")))

        // Tak wyglada plik po zapisie przerwanym w polowie.
        File(korzen, "Ważne.note/content.json").writeText("{\"format\":1,\"id\":\"n")

        val odzyskany = magazyn.czytajNotatke(notatka.path)
        assertThat(odzyskany.text!!.markdown).isEqualTo("treść nie do stracenia")
    }

    @Test
    fun `dwie notatki o tej samej nazwie nie nadpisuja sie`() {
        magazyn.utworzNotatke("", "Notatka", NoteKind.TEKSTOWA)
        val druga = magazyn.utworzNotatke("", "Notatka", NoteKind.TEKSTOWA)

        assertThat(druga.path).isEqualTo("Notatka (2).note")
        assertThat(File(korzen, "Notatka.note").isDirectory).isTrue()
        assertThat(File(korzen, "Notatka (2).note").isDirectory).isTrue()
    }

    @Test
    fun `zmiana nazwy notatki zmienia katalog i tytul w srodku`() {
        val notatka = magazyn.utworzNotatke("", "Stara nazwa", NoteKind.TEKSTOWA)
        val nowaSciezka = magazyn.zmienNazwe(notatka.path, "Nowa nazwa")

        assertThat(nowaSciezka).isEqualTo("Nowa nazwa.note")
        assertThat(File(korzen, "Stara nazwa.note").exists()).isFalse()
        assertThat(magazyn.czytajNotatke(nowaSciezka).title).isEqualTo("Nowa nazwa")
    }

    @Test
    fun `przeniesienie notatki do folderu przenosi caly katalog z zalacznikami`() {
        magazyn.utworzFolder("", "Fizyka")
        val notatka = magazyn.utworzNotatke("", "Drgania", NoteKind.ODRECZNA)
        magazyn.zapiszZalacznik(notatka.path, "wykres.png", byteArrayOf(1, 2, 3), "image/png")

        val nowaSciezka = magazyn.przenies(notatka.path, "Fizyka")

        assertThat(nowaSciezka).isEqualTo("Fizyka/Drgania.note")
        assertThat(File(korzen, "Drgania.note").exists()).isFalse()
        assertThat(File(korzen, "Fizyka/Drgania.note/content.json").isFile).isTrue()
        assertThat(magazyn.czytajZalacznik(nowaSciezka, "wykres.png")).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `nie da sie przeniesc folderu do samego siebie`() {
        magazyn.utworzFolder("", "Szkoła")
        magazyn.utworzFolder("Szkoła", "Matematyka")

        val blad = assertThrows(java.io.IOException::class.java) {
            magazyn.przenies("Szkoła", "Szkoła/Matematyka")
        }
        assertThat(blad.message).contains("własnego wnętrza")
    }

    @Test
    fun `kopiowanie notatki tworzy osobny katalog z ta sama trescia`() {
        val notatka = magazyn.utworzNotatke("", "Wzory", NoteKind.TEKSTOWA)
        val dokument = magazyn.czytajNotatke(notatka.path)
        magazyn.zapiszNotatke(notatka.path, dokument.copy(text = TextContent(markdown = "E = mc^2")))

        val kopia = magazyn.kopiuj(notatka.path, "")

        assertThat(kopia).isEqualTo("Wzory (kopia).note")
        assertThat(magazyn.czytajNotatke(kopia).text!!.markdown).isEqualTo("E = mc^2")
        assertThat(magazyn.czytajNotatke(notatka.path).text!!.markdown).isEqualTo("E = mc^2")
    }

    @Test
    fun `kosz nie kasuje pliku, tylko przenosi go do katalogu trash`() {
        val notatka = magazyn.utworzNotatke("", "Do wyrzucenia", NoteKind.TEKSTOWA)
        magazyn.doKosza(notatka.path)

        assertThat(File(korzen, "Do wyrzucenia.note").exists()).isFalse()
        assertThat(File(korzen, ".trash").isDirectory).isTrue()

        val kosz = magazyn.wypiszKosz()
        assertThat(kosz).hasSize(1)
        assertThat(kosz.first().displayName).isEqualTo("Do wyrzucenia")
        assertThat(kosz.first().originalPath).isEqualTo("Do wyrzucenia.note")
    }

    @Test
    fun `przywrocenie z kosza odklada notatke tam, skad zniknela`() {
        magazyn.utworzFolder("", "Polski")
        val notatka = magazyn.utworzNotatke("Polski", "Lektury", NoteKind.TEKSTOWA)
        val dokument = magazyn.czytajNotatke(notatka.path)
        magazyn.zapiszNotatke(notatka.path, dokument.copy(text = TextContent(markdown = "Pan Tadeusz")))

        magazyn.doKosza(notatka.path)
        val przywrocona = magazyn.przywroc(magazyn.wypiszKosz().first().id)

        assertThat(przywrocona).isEqualTo("Polski/Lektury.note")
        assertThat(magazyn.czytajNotatke(przywrocona).text!!.markdown).isEqualTo("Pan Tadeusz")
        assertThat(magazyn.wypiszKosz()).isEmpty()
    }

    @Test
    fun `przywrocenie odtwarza brakujacy folder nadrzedny`() {
        magazyn.utworzFolder("", "Chemia")
        val notatka = magazyn.utworzNotatke("Chemia", "Kwasy", NoteKind.TEKSTOWA)
        magazyn.doKosza(notatka.path)
        magazyn.doKosza("Chemia")

        val wpisNotatki = magazyn.wypiszKosz().first { it.originalPath.endsWith("Kwasy.note") }
        val przywrocona = magazyn.przywroc(wpisNotatki.id)

        assertThat(przywrocona).isEqualTo("Chemia/Kwasy.note")
        assertThat(File(korzen, "Chemia/Kwasy.note/content.json").isFile).isTrue()
    }

    @Test
    fun `oproznienie kosza kasuje wszystko na dobre`() {
        magazyn.doKosza(magazyn.utworzNotatke("", "Raz", NoteKind.TEKSTOWA).path)
        magazyn.doKosza(magazyn.utworzNotatke("", "Dwa", NoteKind.TEKSTOWA).path)
        assertThat(magazyn.wypiszKosz()).hasSize(2)

        magazyn.oproznijKosz()

        assertThat(magazyn.wypiszKosz()).isEmpty()
    }

    @Test
    fun `kosz nie pokazuje sie na liscie folderow`() {
        magazyn.doKosza(magazyn.utworzNotatke("", "Coś", NoteKind.TEKSTOWA).path)
        magazyn.utworzFolder("", "Widoczny")

        val wpisy = magazyn.wypisz("")

        assertThat(wpisy.map { it.name }).containsExactly("Widoczny")
    }

    @Test
    fun `plik folder json nie pokazuje sie jako notatka`() {
        magazyn.utworzFolder("", "Historia")
        val wpisy = magazyn.wypisz("Historia")
        assertThat(wpisy).isEmpty()
    }

    @Test
    fun `plik z kodem dostaje rozszerzenie i rozpoznany jezyk`() {
        val plik = magazyn.utworzPlikKodu("", "zadanie", wojtoteka.ovh.kajet.core.model.CodeLanguage.PYTHON)

        assertThat(plik.name).isEqualTo("zadanie.py")
        assertThat(plik.type).isEqualTo(ItemType.PLIK_KODU)
        assertThat(plik.language).isEqualTo(wojtoteka.ovh.kajet.core.model.CodeLanguage.PYTHON)
        assertThat(magazyn.czytajTekst(plik.path)).contains("print")
    }

    @Test
    fun `przejscie po drzewie znajduje wszystko poza koszem`() {
        magazyn.utworzFolder("", "Matematyka")
        magazyn.utworzNotatke("Matematyka", "Całki", NoteKind.ODRECZNA)
        magazyn.utworzNotatke("", "Luźne myśli", NoteKind.TEKSTOWA)
        magazyn.doKosza(magazyn.utworzNotatke("", "Wyrzucona", NoteKind.TEKSTOWA).path)

        val znalezione = mutableListOf<String>()
        magazyn.przejdzDrzewo { znalezione += it.path }

        assertThat(znalezione).containsExactly(
            "Matematyka",
            "Matematyka/Całki.note",
            "Luźne myśli.note",
        )
    }

    @Test
    fun `foldery sa na liscie przed notatkami`() {
        magazyn.utworzNotatke("", "Aaa notatka", NoteKind.TEKSTOWA)
        magazyn.utworzFolder("", "Zzz folder")

        val wpisy = magazyn.wypisz("")

        assertThat(wpisy.map { it.type }).containsExactly(ItemType.FOLDER, ItemType.NOTATKA).inOrder()
    }

    @Test
    fun `zalacznik trafia do katalogu assets wewnatrz notatki`() {
        val notatka = magazyn.utworzNotatke("", "Ze zdjęciem", NoteKind.TEKSTOWA)
        val nazwa = magazyn.zapiszZalacznik(notatka.path, "tablica.jpg", byteArrayOf(9, 8, 7), "image/jpeg")

        assertThat(File(korzen, "Ze zdjęciem.note/assets/$nazwa").isFile).isTrue()
        assertThat(magazyn.czytajZalacznik(notatka.path, nazwa)).isEqualTo(byteArrayOf(9, 8, 7))
    }

    @Test
    fun `zmiana koloru folderu zostaje zapisana w folder json`() {
        magazyn.utworzFolder("", "Biologia")
        magazyn.zmienWygladFolderu("Biologia", "oliwka", "kolba")

        val wpis = magazyn.wypisz("").first()
        assertThat(wpis.colorId).isEqualTo("oliwka")
        assertThat(wpis.iconId).isEqualTo("kolba")
        assertThat(wpis.name).isEqualTo("Biologia")
    }
}
