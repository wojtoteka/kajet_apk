package wojtoteka.ovh.kajet.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.model.CodeLanguage
import wojtoteka.ovh.kajet.core.model.ItemType
import wojtoteka.ovh.kajet.core.model.LibraryItem
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.NoteKind
import wojtoteka.ovh.kajet.core.model.PageBackground
import wojtoteka.ovh.kajet.core.model.PageMode
import wojtoteka.ovh.kajet.storage.indeks.IndeksDao
import wojtoteka.ovh.kajet.storage.indeks.TekstDoIndeksu
import wojtoteka.ovh.kajet.storage.indeks.WpisIndeksu

/**
 * Jedno miejsce, przez które reszta aplikacji rozmawia z biblioteką.
 *
 * Zapisuje do plików, a przy okazji odświeża indeks. Kolejność jest zawsze
 * taka sama: najpierw plik, potem indeks. Gdyby indeks się nie zapisał,
 * notatka i tak jest bezpieczna na dysku.
 */
class RepozytoriumBiblioteki(
    private val context: Context,
    private val ustawienia: MagazynUstawien,
    private val dao: IndeksDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Rośnie po każdej zmianie w plikach. Listy folderów słuchają tej liczby. */
    private val odswiezenie = MutableStateFlow(0)

    /** Zakres, który żyje tak długo jak aplikacja. Kończy zapisy zaczęte przy wyjściu z notatki. */
    private val zakresTla = CoroutineScope(SupervisorJob() + io)

    private var pamiec: Pair<String, MagazynBiblioteki>? = null

    val katalogWybrany: Flow<Boolean> = ustawienia.ustawienia.map { !it.katalogBiblioteki.isNullOrBlank() }

    /**
     * Zapamiętuje katalog wskazany przez użytkownika i bierze do niego trwałe prawo.
     * Bez tego prawa aplikacja straciłaby dostęp po ponownym uruchomieniu tabletu.
     */
    suspend fun ustawKatalogBiblioteki(uri: Uri) {
        val flagi = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flagi) }
        pamiec = null
        ustawienia.ustawKatalogBiblioteki(uri.toString())
        odswiez()
    }

    /** Czy aplikacja nadal ma prawo pisać do wybranego katalogu. */
    suspend fun maDostep(): Boolean = magazyn()?.korzen?.canWrite() == true

    suspend fun magazyn(): MagazynBiblioteki? = withContext(io) {
        val uri = ustawienia.ustawienia.first().katalogBiblioteki ?: return@withContext null
        pamiec?.let { (zapamietany, magazyn) -> if (zapamietany == uri) return@withContext magazyn }

        val korzen = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext null
        if (!korzen.isDirectory) return@withContext null
        val nowy = MagazynBiblioteki(context.contentResolver, korzen)
        pamiec = uri to nowy
        nowy
    }

    private suspend fun wymagajMagazyn(): MagazynBiblioteki = magazyn()
        ?: throw java.io.IOException(
            "Nie wybrano katalogu na notatki. Otwórz ustawienia i wskaż folder na tablecie.",
        )

    fun odswiez() {
        odswiezenie.value = odswiezenie.value + 1
    }

    // Odczyt list

    /** Zawartość folderu. Lista odświeża się po każdej zmianie w plikach. */
    fun folder(sciezka: String): Flow<List<LibraryItem>> =
        combine(odswiezenie, ustawienia.ustawienia) { licznik, _ -> licznik }
            .map {
                withContext(io) {
                    val magazyn = magazyn() ?: return@withContext emptyList()
                    val zDysku = magazyn.wypisz(sciezka)
                    zDysku.map { wpis -> uzupelnijZIndeksu(wpis) }
                }
            }

    private suspend fun uzupelnijZIndeksu(wpis: LibraryItem): LibraryItem {
        if (wpis.type != ItemType.NOTATKA) return wpis
        val zIndeksu = dao.znajdz(wpis.documentUri) ?: return wpis
        return wpis.copy(
            name = zIndeksu.name.ifBlank { wpis.name },
            noteKind = runCatching { zIndeksu.noteKind?.let { NoteKind.valueOf(it) } }.getOrNull(),
            favorite = zIndeksu.favorite,
            tags = zIndeksu.tags.split('|').filter { it.isNotBlank() },
            preview = zIndeksu.preview.ifBlank { null },
        )
    }

    fun ulubione(): Flow<List<LibraryItem>> = dao.ulubione().map { lista -> lista.map { it.doWpisu() } }

    fun ostatnie(ile: Int = 20): Flow<List<LibraryItem>> =
        dao.ostatnie(ile).map { lista -> lista.map { it.doWpisu() } }

    suspend fun szukaj(zapytanie: String): List<LibraryItem> = withContext(io) {
        val przyciete = zapytanie.trim()
        if (przyciete.length < 2) return@withContext emptyList()

        val poNazwie = dao.szukajWNazwach(przyciete)
        val poTresci = runCatching { dao.szukajWTresci(zapytanieFts(przyciete)) }.getOrDefault(emptyList())

        (poNazwie + poTresci)
            .distinctBy { it.documentUri }
            .map { it.doWpisu() }
    }

    /** Zamienia to, co wpisał użytkownik, na zapytanie zrozumiałe dla wyszukiwarki. */
    private fun zapytanieFts(tekst: String): String = tekst
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { slowo -> "\"" + slowo.replace("\"", "") + "\"*" }

    // Zmiany w bibliotece

    suspend fun utworzFolder(rodzic: String, nazwa: String, colorId: String, iconId: String): LibraryItem =
        withContext(io) {
            val wpis = wymagajMagazyn().utworzFolder(rodzic, nazwa, colorId, iconId)
            dao.wstaw(wpis.doIndeksu())
            odswiez()
            wpis
        }

    suspend fun utworzNotatke(
        rodzic: String,
        tytul: String,
        rodzaj: NoteKind,
        tryb: PageMode = PageMode.A4,
        tlo: PageBackground = PageBackground.LINIE,
    ): LibraryItem = withContext(io) {
        val magazyn = wymagajMagazyn()
        val wpis = magazyn.utworzNotatke(rodzic, tytul, rodzaj, tryb, tlo)
        val dokument = magazyn.czytajNotatke(wpis.path)
        zapiszDoIndeksu(wpis, dokument)
        odswiez()
        wpis
    }

    suspend fun utworzPlikKodu(rodzic: String, nazwa: String, jezyk: CodeLanguage): LibraryItem =
        withContext(io) {
            val wpis = wymagajMagazyn().utworzPlikKodu(rodzic, nazwa, jezyk)
            dao.wstaw(wpis.doIndeksu())
            odswiez()
            wpis
        }

    suspend fun czytajNotatke(sciezka: String): NoteDocument = withContext(io) {
        wymagajMagazyn().czytajNotatke(sciezka)
    }

    /**
     * Rodzaj notatki, potrzebny do wybrania edytora przed jej wczytaniem.
     * Najpierw pytamy indeks, bo to jedno zapytanie do bazy. Dopiero gdy
     * indeksu brakuje, sięgamy do pliku.
     */
    suspend fun rodzajNotatki(sciezka: String): NoteKind? = withContext(io) {
        dao.znajdzPoSciezce(sciezka)?.noteKind?.let { nazwa ->
            runCatching { NoteKind.valueOf(nazwa) }.getOrNull()
        } ?: runCatching { czytajNotatke(sciezka).kind }.getOrNull()
    }

    suspend fun zapiszNotatke(sciezka: String, dokument: NoteDocument) = withContext(io) {
        val magazyn = wymagajMagazyn()
        magazyn.zapiszNotatke(sciezka, dokument)
        val wpis = magazyn.wpis(sciezka)
        if (wpis != null) {
            zapiszDoIndeksu(
                LibraryItem(
                    id = dokument.id,
                    path = sciezka,
                    name = dokument.title,
                    type = ItemType.NOTATKA,
                    documentUri = wpis.uri.toString(),
                    updatedAt = dokument.updatedAt,
                    noteKind = dokument.kind,
                    favorite = dokument.favorite,
                    tags = dokument.tags,
                ),
                dokument,
            )
        }
    }

    /**
     * Zapis, który ma się dokończyć nawet wtedy, gdy ekran notatki już zniknął.
     * Używane przy wyjściu z edytora, żeby ostatnie kreski nie przepadły.
     */
    fun zapiszWTle(sciezka: String, dokument: NoteDocument) {
        zakresTla.launch {
            runCatching { zapiszNotatke(sciezka, dokument) }
        }
    }

    suspend fun czytajTekst(sciezka: String): String = withContext(io) {
        wymagajMagazyn().czytajTekst(sciezka)
    }

    suspend fun zapiszTekst(sciezka: String, tresc: String) = withContext(io) {
        wymagajMagazyn().zapiszTekst(sciezka, tresc)
    }

    suspend fun zapiszZalacznik(sciezkaNotatki: String, nazwa: String, dane: ByteArray, mime: String): String =
        withContext(io) { wymagajMagazyn().zapiszZalacznik(sciezkaNotatki, nazwa, dane, mime) }

    suspend fun czytajZalacznik(sciezkaNotatki: String, nazwa: String): ByteArray? =
        withContext(io) { magazyn()?.czytajZalacznik(sciezkaNotatki, nazwa) }

    suspend fun zmienNazwe(sciezka: String, nowaNazwa: String): String = withContext(io) {
        val nowaSciezka = wymagajMagazyn().zmienNazwe(sciezka, nowaNazwa)
        dao.usunGalaz(sciezka)
        przeindeksujGalaz(nowaSciezka)
        odswiez()
        nowaSciezka
    }

    suspend fun przenies(sciezka: String, docelowyFolder: String): String = withContext(io) {
        val nowaSciezka = wymagajMagazyn().przenies(sciezka, docelowyFolder)
        dao.usunGalaz(sciezka)
        przeindeksujGalaz(nowaSciezka)
        odswiez()
        nowaSciezka
    }

    suspend fun kopiuj(sciezka: String, docelowyFolder: String): String = withContext(io) {
        val nowaSciezka = wymagajMagazyn().kopiuj(sciezka, docelowyFolder)
        przeindeksujGalaz(nowaSciezka)
        odswiez()
        nowaSciezka
    }

    suspend fun zmienWygladFolderu(sciezka: String, colorId: String, iconId: String) = withContext(io) {
        wymagajMagazyn().zmienWygladFolderu(sciezka, colorId, iconId)
        przeindeksujGalaz(sciezka)
        odswiez()
    }

    suspend fun zapamietajOtwarcie(uri: String) = withContext(io) {
        dao.zapamietajOtwarcie(uri, System.currentTimeMillis())
    }

    // Kosz

    suspend fun doKosza(sciezka: String) = withContext(io) {
        wymagajMagazyn().doKosza(sciezka)
        dao.usunGalaz(sciezka)
        odswiez()
    }

    suspend fun wypiszKosz(): List<WpisKosza> = withContext(io) {
        magazyn()?.wypiszKosz() ?: emptyList()
    }

    suspend fun przywrocZKosza(id: String): String = withContext(io) {
        val sciezka = wymagajMagazyn().przywroc(id)
        przeindeksujGalaz(sciezka)
        odswiez()
        sciezka
    }

    suspend fun usunTrwale(id: String) = withContext(io) {
        wymagajMagazyn().usunTrwale(id)
        odswiez()
    }

    suspend fun oproznijKosz() = withContext(io) {
        wymagajMagazyn().oproznijKosz()
        odswiez()
    }

    // Indeks

    /**
     * Buduje indeks od nowa, przechodząc cały katalog biblioteki.
     * Uruchamiane po wskazaniu katalogu i po instalacji na nowym urządzeniu.
     */
    suspend fun przebudujIndeks(postep: ((zrobione: Int, wszystkich: Int) -> Unit)? = null) = withContext(io) {
        val magazyn = magazyn() ?: return@withContext
        dao.wyczysc()

        // Najpierw spis wszystkiego, żeby dało się pokazać, ile jeszcze zostało.
        val wpisy = ArrayList<LibraryItem>(128)
        magazyn.przejdzDrzewo { wpisy += it }

        wpisy.forEachIndexed { numer, wpis ->
            if (wpis.type == ItemType.NOTATKA) {
                val dokument = runCatching { magazyn.czytajNotatke(wpis.path) }.getOrNull()
                if (dokument != null) {
                    zapiszDoIndeksu(wpis.copy(name = dokument.title, noteKind = dokument.kind), dokument)
                } else {
                    dao.wstaw(wpis.doIndeksu())
                }
            } else {
                dao.wstaw(wpis.doIndeksu())
            }
            postep?.invoke(numer + 1, wpisy.size)
        }
        odswiez()
    }

    private suspend fun przeindeksujGalaz(sciezka: String) {
        val magazyn = magazyn() ?: return
        val wpis = magazyn.wpis(sciezka) ?: return
        if (wpis.isDirectory && NazwyPlikow.czyNotatka(wpis.name.orEmpty())) {
            val dokument = runCatching { magazyn.czytajDokument(wpis) }.getOrNull()
            val element = LibraryItem(
                id = dokument?.id ?: wpis.uri.toString(),
                path = sciezka,
                name = dokument?.title ?: NazwyPlikow.bezRozszerzeniaNote(wpis.name.orEmpty()),
                type = ItemType.NOTATKA,
                documentUri = wpis.uri.toString(),
                updatedAt = wpis.lastModified(),
                noteKind = dokument?.kind,
                favorite = dokument?.favorite ?: false,
                tags = dokument?.tags.orEmpty(),
            )
            if (dokument != null) zapiszDoIndeksu(element, dokument) else dao.wstaw(element.doIndeksu())
            return
        }
        // Folder albo zwykły plik: przechodzimy gałąź od nowa.
        val rodzicSciezka = sciezka.substringBeforeLast('/', "")
        val naDysku = magazyn.wypisz(rodzicSciezka).firstOrNull { it.path == sciezka } ?: return
        dao.wstaw(naDysku.doIndeksu())
        if (naDysku.type == ItemType.FOLDER) {
            for (dziecko in magazyn.wypisz(sciezka)) {
                przeindeksujGalaz(dziecko.path)
            }
        }
    }

    private suspend fun zapiszDoIndeksu(wpis: LibraryItem, dokument: NoteDocument) {
        dao.zapisz(
            wpis = wpis.doIndeksu().copy(
                name = dokument.title,
                noteKind = dokument.kind.name,
                favorite = dokument.favorite,
                tags = dokument.tags.joinToString("|"),
                updatedAt = dokument.updatedAt,
                preview = TekstDoIndeksu.podglad(dokument),
            ),
            tytul = dokument.title,
            tresc = TekstDoIndeksu.tresc(dokument),
        )
    }
}

private fun LibraryItem.doIndeksu() = WpisIndeksu(
    documentUri = documentUri,
    path = path,
    name = name,
    type = type.name,
    noteKind = noteKind?.name,
    language = language?.id,
    colorId = colorId,
    iconId = iconId,
    favorite = favorite,
    tags = tags.joinToString("|"),
    updatedAt = updatedAt,
    preview = preview.orEmpty(),
)

private fun WpisIndeksu.doWpisu() = LibraryItem(
    id = documentUri,
    path = path,
    name = name,
    type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.INNY_PLIK),
    documentUri = documentUri,
    updatedAt = updatedAt,
    noteKind = runCatching { noteKind?.let { NoteKind.valueOf(it) } }.getOrNull(),
    language = CodeLanguage.fromId(language),
    colorId = colorId,
    iconId = iconId,
    favorite = favorite,
    tags = tags.split('|').filter { it.isNotBlank() },
    preview = preview.ifBlank { null },
)
