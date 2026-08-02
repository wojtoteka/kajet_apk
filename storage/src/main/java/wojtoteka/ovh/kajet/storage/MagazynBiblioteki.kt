package wojtoteka.ovh.kajet.storage

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.DrawingSource
import wojtoteka.ovh.kajet.core.model.FolderMeta
import wojtoteka.ovh.kajet.core.model.HandwritingContent
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.MindMapContent
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.NotePage
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.core.model.TextContent
import java.io.IOException
import java.util.UUID

/**
 * Biblioteka na dysku. Katalog wskazany przez użytkownika jest jedyną prawdą.
 *
 * Każdy folder w aplikacji to katalog na dysku o tej samej nazwie.
 * Każda notatka to katalog z końcówką .note, w środku plik content.json
 * i katalog assets na zdjęcia. Po odinstalowaniu aplikacji wszystko zostaje.
 */
class MagazynBiblioteki(
    private val resolver: ContentResolver,
    val korzen: DocumentFile,
) {

    val korzenUri: String get() = korzen.uri.toString()

    // Odnajdywanie miejsc w drzewie

    /** Katalog pod ścieżką liczoną od korzenia. Pusta ścieżka oznacza korzeń. */
    fun katalog(sciezka: String): DocumentFile? {
        if (sciezka.isBlank()) return korzen
        var biezacy: DocumentFile = korzen
        for (czesc in sciezka.split('/')) {
            if (czesc.isEmpty()) continue
            val nastepny = biezacy.findFile(czesc) ?: return null
            if (!nastepny.isDirectory) return null
            biezacy = nastepny
        }
        return biezacy
    }

    /** Dowolny wpis pod ścieżką, katalog albo plik. */
    fun wpis(sciezka: String): DocumentFile? {
        if (sciezka.isBlank()) return korzen
        val rodzic = katalog(sciezka.substringBeforeLast('/', "")) ?: return null
        return rodzic.findFile(sciezka.substringAfterLast('/'))
    }

    private fun wymagajKatalog(sciezka: String): DocumentFile =
        katalog(sciezka) ?: throw IOException(
            "Nie ma już folderu $sciezka. Ktoś mógł go przenieść poza aplikacją.",
        )

    // Czytanie zawartości folderu

    /** Wpisy w folderze, posortowane: najpierw foldery, potem reszta alfabetycznie. */
    fun wypisz(sciezka: String): List<LibraryItem> {
        val katalog = katalog(sciezka) ?: return emptyList()
        return katalog.listFiles()
            .mapNotNull { doWpisu(it, sciezka) }
            .sortedWith(
                compareBy<LibraryItem> { it.type != ItemType.FOLDER }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }

    private fun doWpisu(plik: DocumentFile, sciezkaRodzica: String): LibraryItem? {
        val nazwa = plik.name ?: return null
        if (NazwyPlikow.czyUkryty(nazwa)) return null
        val sciezka = if (sciezkaRodzica.isEmpty()) nazwa else "$sciezkaRodzica/$nazwa"

        return when {
            plik.isDirectory && NazwyPlikow.czyNotatka(nazwa) -> {
                LibraryItem(
                    id = plik.uri.toString(),
                    path = sciezka,
                    name = NazwyPlikow.bezRozszerzeniaNote(nazwa),
                    type = ItemType.NOTATKA,
                    documentUri = plik.uri.toString(),
                    updatedAt = plik.lastModified(),
                    noteKind = null,
                )
            }

            plik.isDirectory -> {
                val meta = czytajFolderMeta(plik)
                LibraryItem(
                    id = meta?.id ?: plik.uri.toString(),
                    path = sciezka,
                    name = meta?.displayName ?: nazwa,
                    type = ItemType.FOLDER,
                    documentUri = plik.uri.toString(),
                    updatedAt = plik.lastModified(),
                    colorId = meta?.colorId,
                    iconId = meta?.iconId,
                    childCount = plik.listFiles().count { it.name?.startsWith('.') == false },
                )
            }

            else -> {
                val jezyk = CodeLanguage.fromExtension(nazwa)
                if (nazwa == FolderMeta.PLIK) return null
                LibraryItem(
                    id = plik.uri.toString(),
                    path = sciezka,
                    name = nazwa,
                    type = if (jezyk != null) ItemType.PLIK_KODU else ItemType.INNY_PLIK,
                    documentUri = plik.uri.toString(),
                    updatedAt = plik.lastModified(),
                    language = jezyk,
                )
            }
        }
    }

    // Foldery

    fun czytajFolderMeta(katalog: DocumentFile): FolderMeta? {
        val plik = katalog.findFile(FolderMeta.PLIK) ?: return null
        if (!plik.isFile) return null
        return runCatching { NoteCodec.czytajFolder(Pliki.czytajTekst(resolver, plik)) }.getOrNull()
    }

    fun zapiszFolderMeta(katalog: DocumentFile, meta: FolderMeta) {
        val plik = Pliki.plikDoZapisu(katalog, FolderMeta.PLIK, Pliki.MIME_JSON)
        Pliki.zapiszTekst(resolver, plik, NoteCodec.zapiszFolder(meta))
    }

    fun utworzFolder(
        sciezkaRodzica: String,
        nazwa: String,
        colorId: String = "grafit",
        iconId: String = "folder",
    ): LibraryItem {
        val rodzic = wymagajKatalog(sciezkaRodzica)
        val nazwaNaDysku = NazwyPlikow.unikalna(nazwa, Pliki.zajeteNazwy(rodzic))
        val katalog = rodzic.createDirectory(nazwaNaDysku)
            ?: throw IOException("Nie udało się utworzyć folderu $nazwa. Sprawdź, czy jest miejsce na dysku.")

        val meta = FolderMeta(
            id = UUID.randomUUID().toString(),
            displayName = nazwa.trim().ifEmpty { nazwaNaDysku },
            colorId = colorId,
            iconId = iconId,
            createdAt = System.currentTimeMillis(),
        )
        zapiszFolderMeta(katalog, meta)

        val sciezka = if (sciezkaRodzica.isEmpty()) nazwaNaDysku else "$sciezkaRodzica/$nazwaNaDysku"
        return LibraryItem(
            id = meta.id,
            path = sciezka,
            name = meta.displayName,
            type = ItemType.FOLDER,
            documentUri = katalog.uri.toString(),
            updatedAt = katalog.lastModified(),
            colorId = colorId,
            iconId = iconId,
        )
    }

    fun zmienWygladFolderu(sciezka: String, colorId: String, iconId: String) {
        val katalog = wymagajKatalog(sciezka)
        val stare = czytajFolderMeta(katalog) ?: FolderMeta(
            id = UUID.randomUUID().toString(),
            displayName = katalog.name.orEmpty(),
            createdAt = System.currentTimeMillis(),
        )
        zapiszFolderMeta(katalog, stare.copy(colorId = colorId, iconId = iconId))
    }

    // Notatki

    fun utworzNotatke(
        sciezkaRodzica: String,
        tytul: String,
        rodzaj: NoteKind,
        pageMode: PageMode = PageMode.A4,
        tlo: PageBackground = PageBackground.LINIE,
    ): LibraryItem {
        val rodzic = wymagajKatalog(sciezkaRodzica)
        val nazwaNaDysku = NazwyPlikow.unikalna(
            nazwa = tytul,
            zajete = Pliki.zajeteNazwy(rodzic),
            rozszerzenie = NoteDocument.ROZSZERZENIE,
        )
        val katalog = rodzic.createDirectory(nazwaNaDysku)
            ?: throw IOException("Nie udało się utworzyć notatki. Sprawdź, czy jest miejsce na dysku.")

        val teraz = System.currentTimeMillis()
        val dokument = NoteDocument(
            id = UUID.randomUUID().toString(),
            kind = rodzaj,
            title = tytul.trim().ifEmpty { "Bez tytułu" },
            createdAt = teraz,
            updatedAt = teraz,
            handwriting = if (rodzaj == NoteKind.ODRECZNA) {
                HandwritingContent(
                    pageMode = pageMode,
                    background = tlo,
                    pages = listOf(nowaStrona(pageMode)),
                )
            } else {
                null
            },
            text = if (rodzaj == NoteKind.TEKSTOWA) TextContent() else null,
            mindMap = if (rodzaj == NoteKind.MAPA) MindMapContent() else null,
        )
        zapiszDokument(katalog, dokument)

        val sciezka = if (sciezkaRodzica.isEmpty()) nazwaNaDysku else "$sciezkaRodzica/$nazwaNaDysku"
        return LibraryItem(
            id = dokument.id,
            path = sciezka,
            name = NazwyPlikow.bezRozszerzeniaNote(nazwaNaDysku),
            type = ItemType.NOTATKA,
            documentUri = katalog.uri.toString(),
            updatedAt = teraz,
            noteKind = rodzaj,
        )
    }

    private fun nowaStrona(tryb: PageMode): NotePage = NotePage(
        id = UUID.randomUUID().toString(),
        width = NotePage.SZEROKOSC_A4,
        height = if (tryb == PageMode.A4) NotePage.WYSOKOSC_A4 else NotePage.PRZYROST_WSTEGI,
    )

    fun czytajNotatke(sciezka: String): NoteDocument {
        val katalog = wpis(sciezka)?.takeIf { it.isDirectory }
            ?: throw IOException("Nie ma już notatki $sciezka.")
        return czytajDokument(katalog)
    }

    /**
     * Czyta content.json. Jeśli plik jest uszkodzony, sięga po kopię z ostatniego zapisu.
     * Kopia powstaje przy każdym zapisie, więc strata to najwyżej jedna zmiana.
     */
    fun czytajDokument(katalogNotatki: DocumentFile): NoteDocument {
        val glowny = katalogNotatki.findFile(NoteDocument.PLIK_TRESCI)
        if (glowny != null && glowny.isFile) {
            val wynik = runCatching { NoteCodec.czytajNotatke(Pliki.czytajTekst(resolver, glowny)) }
            wynik.getOrNull()?.let { return it }
        }
        val kopia = katalogNotatki.findFile(PLIK_KOPII)
        if (kopia != null && kopia.isFile) {
            val wynik = runCatching { NoteCodec.czytajNotatke(Pliki.czytajTekst(resolver, kopia)) }
            wynik.getOrNull()?.let { return it }
        }
        throw BladFormatuException(
            "Nie da się odczytać notatki ${katalogNotatki.name}. Plik content.json jest uszkodzony.",
        )
    }

    fun zapiszNotatke(sciezka: String, dokument: NoteDocument) {
        val katalog = wpis(sciezka)?.takeIf { it.isDirectory }
            ?: throw IOException("Nie ma już notatki $sciezka. Zmiany nie zostały zapisane.")
        zapiszDokument(katalog, dokument)
    }

    /**
     * Zapisuje treść dwa razy: najpierw do kopii, potem do pliku głównego.
     *
     * Gdyby tablet zasnął albo padła bateria w środku drugiego zapisu,
     * kopia jest już kompletna i zawiera dokładnie tę samą treść.
     * Odczyt sam po nią sięgnie.
     */
    fun zapiszDokument(katalogNotatki: DocumentFile, dokument: NoteDocument) {
        val tresc = NoteCodec.zapiszNotatke(dokument.copy(updatedAt = System.currentTimeMillis()))
        val kopia = Pliki.plikDoZapisu(katalogNotatki, PLIK_KOPII, Pliki.MIME_JSON)
        Pliki.zapiszTekst(resolver, kopia, tresc)
        val glowny = Pliki.plikDoZapisu(katalogNotatki, NoteDocument.PLIK_TRESCI, Pliki.MIME_JSON)
        Pliki.zapiszTekst(resolver, glowny, tresc)
    }

    // Załączniki notatki

    fun katalogZalacznikow(katalogNotatki: DocumentFile): DocumentFile =
        Pliki.katalogDoZapisu(katalogNotatki, NoteDocument.KATALOG_ZALACZNIKOW)

    fun zapiszZalacznik(sciezkaNotatki: String, nazwa: String, dane: ByteArray, mime: String): String {
        val katalog = wpis(sciezkaNotatki)?.takeIf { it.isDirectory }
            ?: throw IOException("Nie ma już notatki $sciezkaNotatki.")
        val assets = katalogZalacznikow(katalog)
        val nazwaNaDysku = NazwyPlikow.unikalna(
            nazwa = nazwa.substringBeforeLast('.'),
            zajete = Pliki.zajeteNazwy(assets),
            rozszerzenie = "." + nazwa.substringAfterLast('.', "bin"),
        )
        val plik = Pliki.utworzPlik(assets, nazwaNaDysku, mime)
        Pliki.zapiszBajty(resolver, plik, dane)
        return plik.name ?: nazwaNaDysku
    }

    fun czytajZalacznik(sciezkaNotatki: String, nazwa: String): ByteArray? {
        val katalog = wpis(sciezkaNotatki)?.takeIf { it.isDirectory } ?: return null
        val assets = katalog.findFile(NoteDocument.KATALOG_ZALACZNIKOW) ?: return null
        val plik = assets.findFile(nazwa)?.takeIf { it.isFile } ?: return null
        return runCatching { Pliki.czytajBajty(resolver, plik) }.getOrNull()
    }

    fun zapiszRysunekWTekscie(sciezkaNotatki: String, nazwa: String, rysunek: DrawingSource) {
        val katalog = wpis(sciezkaNotatki)?.takeIf { it.isDirectory }
            ?: throw IOException("Nie ma już notatki $sciezkaNotatki.")
        val assets = katalogZalacznikow(katalog)
        val plik = Pliki.plikDoZapisu(assets, nazwa, Pliki.MIME_JSON)
        Pliki.zapiszTekst(resolver, plik, NoteCodec.zapiszRysunek(rysunek))
    }

    fun czytajRysunekWTekscie(sciezkaNotatki: String, nazwa: String): DrawingSource? {
        val dane = czytajZalacznik(sciezkaNotatki, nazwa) ?: return null
        return runCatching { NoteCodec.czytajRysunek(dane.toString(Charsets.UTF_8)) }.getOrNull()
    }

    // Pliki tekstowe i pliki z kodem

    fun utworzPlikKodu(sciezkaRodzica: String, nazwa: String, jezyk: CodeLanguage): LibraryItem {
        val rodzic = wymagajKatalog(sciezkaRodzica)
        val rozszerzenie = "." + jezyk.extensions.first()
        val pelnaNazwa = if (nazwa.endsWith(rozszerzenie)) nazwa else nazwa + rozszerzenie
        val nazwaNaDysku = NazwyPlikow.unikalna(
            nazwa = pelnaNazwa.substringBeforeLast('.'),
            zajete = Pliki.zajeteNazwy(rodzic),
            rozszerzenie = rozszerzenie,
        )
        val plik = Pliki.utworzPlik(rodzic, nazwaNaDysku, Pliki.MIME_TEKST)
        Pliki.zapiszTekst(resolver, plik, szablonKodu(jezyk))

        val sciezka = if (sciezkaRodzica.isEmpty()) nazwaNaDysku else "$sciezkaRodzica/$nazwaNaDysku"
        return LibraryItem(
            id = plik.uri.toString(),
            path = sciezka,
            name = plik.name ?: nazwaNaDysku,
            type = ItemType.PLIK_KODU,
            documentUri = plik.uri.toString(),
            updatedAt = plik.lastModified(),
            language = jezyk,
        )
    }

    fun czytajTekst(sciezka: String): String {
        val plik = wpis(sciezka)?.takeIf { it.isFile }
            ?: throw IOException("Nie ma już pliku $sciezka.")
        return Pliki.czytajTekst(resolver, plik)
    }

    fun zapiszTekst(sciezka: String, tresc: String) {
        val plik = wpis(sciezka)?.takeIf { it.isFile }
            ?: throw IOException("Nie ma już pliku $sciezka. Zmiany nie zostały zapisane.")
        Pliki.zapiszTekst(resolver, plik, tresc)
    }

    // Zmiana nazwy, przenoszenie, kopiowanie

    fun zmienNazwe(sciezka: String, nowaNazwa: String): String {
        val wpis = wpis(sciezka) ?: throw IOException("Nie ma już wpisu $sciezka.")
        val rodzic = katalog(sciezka.substringBeforeLast('/', ""))
            ?: throw IOException("Nie ma już folderu nadrzędnego.")
        val staraNazwa = wpis.name.orEmpty()

        val jestNotatka = wpis.isDirectory && NazwyPlikow.czyNotatka(staraNazwa)
        val rozszerzenie = when {
            jestNotatka -> NoteDocument.ROZSZERZENIE
            wpis.isFile && staraNazwa.contains('.') -> "." + staraNazwa.substringAfterLast('.')
            else -> ""
        }
        val zajete = Pliki.zajeteNazwy(rodzic) - staraNazwa
        val docelowa = NazwyPlikow.unikalna(
            nazwa = nowaNazwa.removeSuffix(rozszerzenie),
            zajete = zajete,
            rozszerzenie = rozszerzenie,
        )
        if (!wpis.renameTo(docelowa)) {
            throw IOException("Nie udało się zmienić nazwy na $nowaNazwa.")
        }

        // Prawdziwa nazwa idzie też do środka, bo na dysku mogła zostać okrojona.
        if (jestNotatka) {
            val dokument = runCatching { czytajDokument(wpis) }.getOrNull()
            if (dokument != null) zapiszDokument(wpis, dokument.copy(title = nowaNazwa.trim()))
        } else if (wpis.isDirectory) {
            val meta = czytajFolderMeta(wpis)
            if (meta != null) zapiszFolderMeta(wpis, meta.copy(displayName = nowaNazwa.trim()))
        }

        val rodzicSciezka = sciezka.substringBeforeLast('/', "")
        return if (rodzicSciezka.isEmpty()) docelowa else "$rodzicSciezka/$docelowa"
    }

    fun przenies(sciezka: String, docelowyFolder: String): String {
        val zrodlo = wpis(sciezka) ?: throw IOException("Nie ma już wpisu $sciezka.")
        val cel = wymagajKatalog(docelowyFolder)
        if (sciezka == docelowyFolder || docelowyFolder.startsWith("$sciezka/")) {
            throw IOException("Nie można przenieść folderu do jego własnego wnętrza.")
        }
        val nazwa = zrodlo.name ?: throw IOException("Wpis nie ma nazwy.")
        val nowaNazwa = NazwyPlikow.unikalna(
            nazwa = nazwa.substringBeforeLast('.', nazwa),
            zajete = Pliki.zajeteNazwy(cel),
            rozszerzenie = if (nazwa.contains('.')) "." + nazwa.substringAfterLast('.') else "",
        )
        Pliki.kopiujRekurencyjnie(resolver, zrodlo, cel, nowaNazwa)
        Pliki.usunRekurencyjnie(zrodlo)
        return if (docelowyFolder.isEmpty()) nowaNazwa else "$docelowyFolder/$nowaNazwa"
    }

    fun kopiuj(sciezka: String, docelowyFolder: String): String {
        val zrodlo = wpis(sciezka) ?: throw IOException("Nie ma już wpisu $sciezka.")
        val cel = wymagajKatalog(docelowyFolder)
        val nazwa = zrodlo.name ?: throw IOException("Wpis nie ma nazwy.")
        val trzon = if (NazwyPlikow.czyNotatka(nazwa)) {
            NazwyPlikow.bezRozszerzeniaNote(nazwa)
        } else {
            nazwa.substringBeforeLast('.', nazwa)
        }
        val rozszerzenie = when {
            NazwyPlikow.czyNotatka(nazwa) -> NoteDocument.ROZSZERZENIE
            nazwa.contains('.') -> "." + nazwa.substringAfterLast('.')
            else -> ""
        }
        val nowaNazwa = NazwyPlikow.unikalna(
            nazwa = "$trzon (kopia)",
            zajete = Pliki.zajeteNazwy(cel),
            rozszerzenie = rozszerzenie,
        )
        Pliki.kopiujRekurencyjnie(resolver, zrodlo, cel, nowaNazwa)
        return if (docelowyFolder.isEmpty()) nowaNazwa else "$docelowyFolder/$nowaNazwa"
    }

    // Kosz

    private fun katalogKosza(): DocumentFile = Pliki.katalogDoZapisu(korzen, WpisKosza.KATALOG)

    fun doKosza(sciezka: String) {
        val zrodlo = wpis(sciezka) ?: throw IOException("Nie ma już wpisu $sciezka.")
        val nazwa = zrodlo.name ?: throw IOException("Wpis nie ma nazwy.")
        val kosz = katalogKosza()
        val id = UUID.randomUUID().toString()
        val schowek = kosz.createDirectory(id)
            ?: throw IOException("Nie udało się otworzyć kosza. Sprawdź, czy jest miejsce na dysku.")

        val typ = when {
            zrodlo.isDirectory && NazwyPlikow.czyNotatka(nazwa) -> ItemType.NOTATKA
            zrodlo.isDirectory -> ItemType.FOLDER
            CodeLanguage.fromExtension(nazwa) != null -> ItemType.PLIK_KODU
            else -> ItemType.INNY_PLIK
        }
        val nazwaWidoczna = when (typ) {
            ItemType.NOTATKA -> NazwyPlikow.bezRozszerzeniaNote(nazwa)
            ItemType.FOLDER -> czytajFolderMeta(zrodlo)?.displayName ?: nazwa
            else -> nazwa
        }

        val opis = WpisKosza(
            id = id,
            originalPath = sciezka,
            fileName = nazwa,
            displayName = nazwaWidoczna,
            type = typ,
            deletedAt = System.currentTimeMillis(),
        )
        val plikOpisu = Pliki.plikDoZapisu(schowek, WpisKosza.PLIK_OPISU, Pliki.MIME_JSON)
        Pliki.zapiszTekst(resolver, plikOpisu, NoteCodec.json.encodeToString(opis))

        Pliki.kopiujRekurencyjnie(resolver, zrodlo, schowek, nazwa)
        Pliki.usunRekurencyjnie(zrodlo)
    }

    fun wypiszKosz(): List<WpisKosza> {
        val kosz = korzen.findFile(WpisKosza.KATALOG)?.takeIf { it.isDirectory } ?: return emptyList()
        return kosz.listFiles().mapNotNull { schowek ->
            if (!schowek.isDirectory) return@mapNotNull null
            val opis = schowek.findFile(WpisKosza.PLIK_OPISU) ?: return@mapNotNull null
            runCatching {
                NoteCodec.json.decodeFromString<WpisKosza>(Pliki.czytajTekst(resolver, opis))
            }.getOrNull()
        }.sortedByDescending { it.deletedAt }
    }

    /** Przywraca wpis tam, skąd zniknął. Brakujące foldery po drodze tworzy od nowa. */
    fun przywroc(id: String): String {
        val kosz = korzen.findFile(WpisKosza.KATALOG)?.takeIf { it.isDirectory }
            ?: throw IOException("Kosz jest pusty.")
        val schowek = kosz.findFile(id)?.takeIf { it.isDirectory }
            ?: throw IOException("Tego wpisu nie ma już w koszu.")
        val opisPlik = schowek.findFile(WpisKosza.PLIK_OPISU)
            ?: throw IOException("Wpis w koszu nie ma opisu, więc nie wiadomo, gdzie go odłożyć.")
        val opis = NoteCodec.json.decodeFromString<WpisKosza>(Pliki.czytajTekst(resolver, opisPlik))

        val zrodlo = schowek.findFile(opis.fileName)
            ?: throw IOException("W koszu nie ma już pliku ${opis.fileName}.")

        var cel = korzen
        for (czesc in opis.originalParent.split('/')) {
            if (czesc.isEmpty()) continue
            cel = Pliki.katalogDoZapisu(cel, czesc)
        }

        val nazwa = NazwyPlikow.unikalna(
            nazwa = opis.fileName.substringBeforeLast('.', opis.fileName),
            zajete = Pliki.zajeteNazwy(cel),
            rozszerzenie = if (opis.fileName.contains('.')) {
                "." + opis.fileName.substringAfterLast('.')
            } else {
                ""
            },
        )
        Pliki.kopiujRekurencyjnie(resolver, zrodlo, cel, nazwa)
        Pliki.usunRekurencyjnie(schowek)

        return if (opis.originalParent.isEmpty()) nazwa else "${opis.originalParent}/$nazwa"
    }

    fun usunTrwale(id: String) {
        val kosz = korzen.findFile(WpisKosza.KATALOG)?.takeIf { it.isDirectory } ?: return
        val schowek = kosz.findFile(id) ?: return
        Pliki.usunRekurencyjnie(schowek)
    }

    fun oproznijKosz() {
        val kosz = korzen.findFile(WpisKosza.KATALOG)?.takeIf { it.isDirectory } ?: return
        for (schowek in kosz.listFiles()) {
            Pliki.usunRekurencyjnie(schowek)
        }
    }

    // Przejście po całym drzewie, potrzebne przy budowie indeksu

    /**
     * Odwiedza wszystkie notatki i pliki w bibliotece. Kosz pomija.
     * Wywoływane w tle, bo przy dużej bibliotece to setki zapytań do dostawcy plików.
     */
    fun przejdzDrzewo(odwiedz: (LibraryItem) -> Unit) {
        fun zejdz(katalog: DocumentFile, sciezka: String) {
            for (dziecko in katalog.listFiles()) {
                val nazwa = dziecko.name ?: continue
                if (NazwyPlikow.czyUkryty(nazwa)) continue
                val wpis = doWpisu(dziecko, sciezka) ?: continue
                odwiedz(wpis)
                if (wpis.type == ItemType.FOLDER) {
                    zejdz(dziecko, wpis.path)
                }
            }
        }
        zejdz(korzen, "")
    }

    companion object {
        /** Kopia treści notatki z ostatniego zapisu. Ratuje notatkę, gdy zapis się urwie. */
        const val PLIK_KOPII = "content.bak.json"

        fun szablonKodu(jezyk: CodeLanguage): String = when (jezyk) {
            CodeLanguage.PYTHON -> "print(\"Cześć\")\n"
            CodeLanguage.C -> "#include <stdio.h>\n\nint main(void) {\n    printf(\"Cześć\\n\");\n    return 0;\n}\n"
            CodeLanguage.CPP -> "#include <iostream>\n\nint main() {\n    std::cout << \"Cześć\" << std::endl;\n    return 0;\n}\n"
            CodeLanguage.JAVA -> "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Cześć\");\n    }\n}\n"
            CodeLanguage.KOTLIN -> "fun main() {\n    println(\"Cześć\")\n}\n"
            CodeLanguage.JAVASCRIPT -> "console.log(\"Cześć\")\n"
            CodeLanguage.TYPESCRIPT -> "const powitanie: string = \"Cześć\"\nconsole.log(powitanie)\n"
            CodeLanguage.CSHARP -> "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"Cześć\");\n    }\n}\n"
            CodeLanguage.GO -> "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Cześć\")\n}\n"
            CodeLanguage.RUST -> "fn main() {\n    println!(\"Cześć\");\n}\n"
            CodeLanguage.PHP -> "<?php\necho \"Cześć\\n\";\n"
            CodeLanguage.RUBY -> "puts \"Cześć\"\n"
            CodeLanguage.BASH -> "echo \"Cześć\"\n"
            CodeLanguage.SQL -> "select 'Cześć';\n"
            CodeLanguage.TEKST -> ""
        }
    }
}
