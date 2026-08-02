package wojtoteka.ovh.kajet.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * Zestaw ikon narysowany na potrzeby Kajetu. Nie jest to biblioteka Material Icons,
 * bo tam wszystko jest wypełnione i zaokrąglone, a tu wszystko jest kreską
 * o tej samej grubości, tak jakby ktoś narysował to piórem na kartce.
 *
 * Reguły zestawu: siatka 24 na 24, treść w polu od 3 do 21, kreska 1.75,
 * zakończenia i łączenia okrągłe, brak wypełnień.
 * Kolor ustawia się przez parametr tint w elemencie Icon.
 */
object KajetIcons {

    // Biblioteka i pliki
    val Biblioteka by lazy {
        icon("Biblioteka") {
            moveTo(3.5f, 20.5f); lineTo(20.5f, 20.5f)
            moveTo(5.5f, 20.5f); lineTo(5.5f, 8f); lineTo(9f, 8f); lineTo(9f, 20.5f)
            moveTo(10.5f, 20.5f); lineTo(10.5f, 4.5f); lineTo(14f, 4.5f); lineTo(14f, 20.5f)
            moveTo(15.5f, 20.5f); lineTo(15.5f, 11f); lineTo(19f, 11f); lineTo(19f, 20.5f)
        }
    }

    val Folder by lazy {
        icon("Folder") {
            moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f)
            lineTo(20.5f, 8.5f); lineTo(20.5f, 19f); close()
        }
    }

    val FolderOtwarty by lazy {
        icon("Folder otwarty") {
            moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f)
            lineTo(19f, 8.5f); lineTo(19f, 11.5f)
            moveTo(3.5f, 19f); lineTo(6.5f, 11.5f); lineTo(22f, 11.5f); lineTo(19f, 19f); close()
        }
    }

    val NotatkaOdreczna by lazy {
        icon("Notatka odręczna") {
            kartka()
            moveTo(8.5f, 11.5f)
            curveTo(9.8f, 8.5f, 11.2f, 14.5f, 12.5f, 11.5f)
            curveTo(13.4f, 9.5f, 14.5f, 11f, 15.5f, 11.5f)
            moveTo(8.5f, 15.5f); lineTo(15.5f, 15.5f)
        }
    }

    val NotatkaTekstowa by lazy {
        icon("Notatka tekstowa") {
            kartka()
            moveTo(8.5f, 8.5f); lineTo(15.5f, 8.5f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            moveTo(8.5f, 15.5f); lineTo(12.5f, 15.5f)
        }
    }

    val MapaMysli by lazy {
        icon("Mapa myśli") {
            moveTo(2.5f, 9.5f); lineTo(9f, 9.5f); lineTo(9f, 14.5f); lineTo(2.5f, 14.5f); close()
            moveTo(15f, 4.5f); lineTo(21.5f, 4.5f); lineTo(21.5f, 9f); lineTo(15f, 9f); close()
            moveTo(15f, 15f); lineTo(21.5f, 15f); lineTo(21.5f, 19.5f); lineTo(15f, 19.5f); close()
            moveTo(9f, 11.5f); curveTo(12f, 11.5f, 12f, 6.75f, 15f, 6.75f)
            moveTo(9f, 12.5f); curveTo(12f, 12.5f, 12f, 17.25f, 15f, 17.25f)
        }
    }

    val PlikKodu by lazy {
        icon("Plik z kodem") {
            kartka()
            moveTo(10.5f, 9.5f); lineTo(8f, 12f); lineTo(10.5f, 14.5f)
            moveTo(13.5f, 9.5f); lineTo(16f, 12f); lineTo(13.5f, 14.5f)
        }
    }

    val Ulubione by lazy {
        icon("Ulubione") {
            moveTo(12f, 4.3f); lineTo(14f, 9.55f); lineTo(19.61f, 9.83f); lineTo(15.23f, 13.35f)
            lineTo(16.7f, 18.77f); lineTo(12f, 15.7f); lineTo(7.3f, 18.77f); lineTo(8.77f, 13.35f)
            lineTo(4.39f, 9.83f); lineTo(10f, 9.55f); close()
        }
    }

    val Ostatnie by lazy {
        icon("Ostatnio otwarte") {
            kolo(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(12f, 12.3f); lineTo(15.8f, 14f)
        }
    }

    val Tag by lazy {
        icon("Tag") {
            moveTo(20.5f, 12.5f); lineTo(12.5f, 20.5f); lineTo(3.5f, 11.5f); lineTo(3.5f, 3.5f)
            lineTo(11.5f, 3.5f); close()
            kolo(7.4f, 7.4f, 1.3f)
        }
    }

    val Kosz by lazy {
        icon("Kosz") {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9.5f, 6.5f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.5f, 6.5f)
            moveTo(10.5f, 10f); lineTo(10.8f, 16.5f)
            moveTo(13.5f, 10f); lineTo(13.2f, 16.5f)
        }
    }

    val Szukaj by lazy {
        icon("Szukaj") {
            kolo(10.5f, 10.5f, 6.2f)
            moveTo(15.1f, 15.1f); lineTo(20f, 20f)
        }
    }

    val Dodaj by lazy {
        icon("Dodaj") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Wiecej by lazy {
        icon("Więcej") {
            kropka(12f, 5.6f); kropka(12f, 12f); kropka(12f, 18.4f)
        }
    }

    val StrzalkaWPrawo by lazy {
        icon("Rozwiń") {
            moveTo(9.5f, 5f); lineTo(16.5f, 12f); lineTo(9.5f, 19f)
        }
    }

    val StrzalkaWDol by lazy {
        icon("Zwiń") {
            moveTo(5f, 9.5f); lineTo(12f, 16.5f); lineTo(19f, 9.5f)
        }
    }

    val Wstecz by lazy {
        icon("Wstecz") {
            moveTo(19.5f, 12f); lineTo(4.5f, 12f)
            moveTo(10.5f, 6f); lineTo(4.5f, 12f); lineTo(10.5f, 18f)
        }
    }

    val Ustawienia by lazy {
        icon("Ustawienia") {
            moveTo(3.5f, 7f); lineTo(20.5f, 7f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(3.5f, 17f); lineTo(20.5f, 17f)
            kolo(15f, 7f, 2.1f)
            kolo(8.5f, 12f, 2.1f)
            kolo(16.5f, 17f, 2.1f)
        }
    }

    // Narzędzia do pisania
    val Pioro by lazy {
        icon("Pióro") {
            moveTo(4.2f, 19.8f); lineTo(5.6f, 15.2f); lineTo(16.6f, 4.2f); lineTo(19.8f, 7.4f)
            lineTo(8.8f, 18.4f); close()
            moveTo(5.6f, 15.2f); lineTo(8.8f, 18.4f)
            moveTo(14.4f, 6.4f); lineTo(17.6f, 9.6f)
        }
    }

    val Zakreslacz by lazy {
        icon("Zakreślacz") {
            moveTo(9f, 14.5f); lineTo(15f, 8.5f); lineTo(18.5f, 12f); lineTo(12.5f, 18f); close()
            moveTo(15f, 8.5f); lineTo(17f, 6.5f); lineTo(20.5f, 10f); lineTo(18.5f, 12f)
            moveTo(9f, 14.5f); lineTo(6f, 17.5f); lineTo(9.5f, 18f); lineTo(12.5f, 18f)
            moveTo(4f, 21f); lineTo(20f, 21f)
        }
    }

    val Gumka by lazy {
        icon("Gumka") {
            moveTo(5f, 18.5f); lineTo(5f, 15.5f); lineTo(14f, 6.5f); lineTo(18.5f, 11f)
            lineTo(11f, 18.5f); close()
            moveTo(9.5f, 11f); lineTo(14f, 15.5f)
            moveTo(4f, 21.2f); lineTo(20f, 21.2f)
        }
    }

    val GumkaKreska by lazy {
        icon("Gumka do całej kreski") {
            moveTo(5f, 18.5f); lineTo(5f, 15.5f); lineTo(14f, 6.5f); lineTo(18.5f, 11f)
            lineTo(11f, 18.5f); close()
            moveTo(9.5f, 11f); lineTo(14f, 15.5f)
            moveTo(4f, 21.2f); lineTo(7f, 21.2f)
            moveTo(10.5f, 21.2f); lineTo(13.5f, 21.2f)
            moveTo(17f, 21.2f); lineTo(20f, 21.2f)
        }
    }

    val Lasso by lazy {
        icon("Zaznaczanie") {
            moveTo(12f, 4.5f)
            curveTo(17.5f, 4.5f, 21f, 7.4f, 21f, 11f)
            curveTo(21f, 14.6f, 17.5f, 17.5f, 12f, 17.5f)
            curveTo(6.5f, 17.5f, 3f, 14.6f, 3f, 11f)
            curveTo(3f, 7.4f, 6.5f, 4.5f, 12f, 4.5f)
            close()
            moveTo(8.6f, 16.9f)
            curveTo(8f, 19f, 9.5f, 20.6f, 11.2f, 20.6f)
        }
    }

    val Linijka by lazy {
        icon("Linijka") {
            moveTo(2.5f, 8.5f); lineTo(21.5f, 8.5f); lineTo(21.5f, 15.5f); lineTo(2.5f, 15.5f); close()
            moveTo(6f, 8.5f); lineTo(6f, 12.5f)
            moveTo(9f, 8.5f); lineTo(9f, 11f)
            moveTo(12f, 8.5f); lineTo(12f, 12.5f)
            moveTo(15f, 8.5f); lineTo(15f, 11f)
            moveTo(18f, 8.5f); lineTo(18f, 12.5f)
        }
    }

    val Cofnij by lazy {
        icon("Cofnij") {
            moveTo(8.5f, 7f); lineTo(4f, 11.5f); lineTo(8.5f, 16f)
            moveTo(4f, 11.5f); lineTo(13.5f, 11.5f)
            curveTo(17.5f, 11.5f, 20f, 14f, 20f, 17.5f)
            lineTo(20f, 19.5f)
        }
    }

    val Ponow by lazy {
        icon("Ponów") {
            moveTo(15.5f, 7f); lineTo(20f, 11.5f); lineTo(15.5f, 16f)
            moveTo(20f, 11.5f); lineTo(10.5f, 11.5f)
            curveTo(6.5f, 11.5f, 4f, 14f, 4f, 17.5f)
            lineTo(4f, 19.5f)
        }
    }

    val PoleTekstowe by lazy {
        icon("Pole tekstowe") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
            moveTo(8.5f, 9.5f); lineTo(15.5f, 9.5f)
            moveTo(12f, 9.5f); lineTo(12f, 15f)
        }
    }

    val Zdjecie by lazy {
        icon("Zdjęcie") {
            moveTo(3.5f, 5f); lineTo(20.5f, 5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            kolo(8.5f, 9.5f, 1.5f)
            moveTo(3.5f, 16f); lineTo(9f, 10.5f); lineTo(14f, 15.5f); lineTo(16.5f, 13f); lineTo(20.5f, 17f)
        }
    }

    val Aparat by lazy {
        icon("Aparat") {
            moveTo(3.5f, 7.5f); lineTo(8f, 7.5f); lineTo(9.5f, 5.5f); lineTo(14.5f, 5.5f)
            lineTo(16f, 7.5f); lineTo(20.5f, 7.5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            kolo(12f, 13f, 3.6f)
        }
    }

    val Rysunek by lazy {
        icon("Rysunek") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
            moveTo(6.5f, 14.5f)
            curveTo(9f, 8.5f, 11f, 17f, 13.5f, 12f)
            curveTo(15f, 9f, 16.5f, 11.5f, 17.5f, 10f)
        }
    }

    val Kolor by lazy {
        icon("Kolor") {
            moveTo(12f, 3.2f)
            curveTo(12f, 3.2f, 5.5f, 11f, 5.5f, 15f)
            curveTo(5.5f, 18.6f, 8.4f, 21f, 12f, 21f)
            curveTo(15.6f, 21f, 18.5f, 18.6f, 18.5f, 15f)
            curveTo(18.5f, 11f, 12f, 3.2f, 12f, 3.2f)
            close()
        }
    }

    val TloStrony by lazy {
        icon("Tło strony") {
            moveTo(4.5f, 3.5f); lineTo(19.5f, 3.5f); lineTo(19.5f, 20.5f); lineTo(4.5f, 20.5f); close()
            moveTo(4.5f, 9.2f); lineTo(19.5f, 9.2f)
            moveTo(4.5f, 14.8f); lineTo(19.5f, 14.8f)
            moveTo(9.5f, 3.5f); lineTo(9.5f, 20.5f)
            moveTo(14.5f, 3.5f); lineTo(14.5f, 20.5f)
        }
    }

    val DodajStrone by lazy {
        icon("Dodaj stronę") {
            moveTo(5f, 3.5f); lineTo(19f, 3.5f); lineTo(19f, 20.5f); lineTo(5f, 20.5f); close()
            moveTo(12f, 8.5f); lineTo(12f, 15.5f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
        }
    }

    val RozpoznajPismo by lazy {
        icon("Zamień pismo na tekst") {
            moveTo(2.8f, 15f)
            curveTo(4.4f, 9.5f, 6.4f, 17.5f, 8.4f, 12.5f)
            moveTo(10.5f, 13.5f); lineTo(14f, 13.5f)
            moveTo(12.6f, 11.8f); lineTo(14.3f, 13.5f); lineTo(12.6f, 15.2f)
            moveTo(16.2f, 9f); lineTo(21.2f, 9f)
            moveTo(18.7f, 9f); lineTo(18.7f, 17.5f)
        }
    }

    // Eksport i udostępnianie
    val Eksport by lazy {
        icon("Eksportuj") {
            moveTo(4.5f, 14.5f); lineTo(4.5f, 20f); lineTo(19.5f, 20f); lineTo(19.5f, 14.5f)
            moveTo(12f, 15.5f); lineTo(12f, 4f)
            moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f)
        }
    }

    val Udostepnij by lazy {
        icon("Udostępnij") {
            kolo(6f, 12f, 2.4f)
            kolo(17.2f, 6.4f, 2.4f)
            kolo(17.2f, 17.6f, 2.4f)
            moveTo(8.15f, 10.93f); lineTo(15.05f, 7.47f)
            moveTo(8.15f, 13.07f); lineTo(15.05f, 16.53f)
        }
    }

    val Drukuj by lazy {
        icon("Drukuj") {
            moveTo(7.5f, 8.5f); lineTo(7.5f, 3.5f); lineTo(16.5f, 3.5f); lineTo(16.5f, 8.5f)
            moveTo(4.5f, 8.5f); lineTo(19.5f, 8.5f); lineTo(19.5f, 16.5f); lineTo(4.5f, 16.5f); close()
            moveTo(7.5f, 13.5f); lineTo(16.5f, 13.5f); lineTo(16.5f, 20.5f); lineTo(7.5f, 20.5f); close()
            kropka(16.8f, 11f)
        }
    }

    val Zamknij by lazy {
        icon("Zamknij") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Zatwierdz by lazy {
        icon("Zatwierdź") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
        }
    }

    // Kod
    val Uruchom by lazy {
        icon("Uruchom") {
            moveTo(7.5f, 4.5f); lineTo(19.5f, 12f); lineTo(7.5f, 19.5f); close()
        }
    }

    val Zatrzymaj by lazy {
        icon("Zatrzymaj") {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close()
        }
    }

    val Wynik by lazy {
        icon("Wynik") {
            moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
            moveTo(7f, 9.5f); lineTo(10f, 12.5f); lineTo(7f, 15.5f)
            moveTo(12.5f, 15.5f); lineTo(17f, 15.5f)
        }
    }

    val Blad by lazy {
        icon("Błędy") {
            moveTo(12f, 4f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
            moveTo(12f, 10f); lineTo(12f, 15f)
            kropka(12f, 17.6f)
        }
    }

    val Wejscie by lazy {
        icon("Wejście") {
            moveTo(12f, 3.5f); lineTo(12f, 13.5f)
            moveTo(8f, 9.5f); lineTo(12f, 13.5f); lineTo(16f, 9.5f)
            moveTo(4.5f, 17f); lineTo(4.5f, 20.5f); lineTo(19.5f, 20.5f); lineTo(19.5f, 17f)
        }
    }

    val Zawijanie by lazy {
        icon("Zawijanie wierszy") {
            moveTo(4f, 6.5f); lineTo(20f, 6.5f)
            moveTo(4f, 12f); lineTo(16f, 12f)
            curveTo(19f, 12f, 19f, 16.5f, 16f, 16.5f)
            lineTo(11.5f, 16.5f)
            moveTo(13.5f, 14.5f); lineTo(11.5f, 16.5f); lineTo(13.5f, 18.5f)
        }
    }

    val BezSieci by lazy {
        icon("Brak internetu") {
            moveTo(3.5f, 9.5f); curveTo(6f, 7.2f, 9f, 6f, 12f, 6f)
            curveTo(15f, 6f, 18f, 7.2f, 20.5f, 9.5f)
            moveTo(7f, 13f); curveTo(8.5f, 11.7f, 10.2f, 11f, 12f, 11f)
            curveTo(13.2f, 11f, 14.4f, 11.3f, 15.5f, 11.9f)
            kropka(12f, 17.5f)
            moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        }
    }

    // Działania na plikach
    val Przenies by lazy {
        icon("Przenieś") {
            moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f)
            lineTo(20.5f, 8.5f); lineTo(20.5f, 19f); close()
            moveTo(8f, 13.8f); lineTo(15.5f, 13.8f)
            moveTo(13f, 11.3f); lineTo(15.5f, 13.8f); lineTo(13f, 16.3f)
        }
    }

    val Kopiuj by lazy {
        icon("Kopiuj") {
            moveTo(8.5f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 15.5f); lineTo(8.5f, 15.5f); close()
            moveTo(15.5f, 15.5f); lineTo(15.5f, 20.5f); lineTo(3.5f, 20.5f); lineTo(3.5f, 8.5f)
            lineTo(8.5f, 8.5f)
        }
    }

    val Przywroc by lazy {
        icon("Przywróć") {
            moveTo(4.5f, 12f)
            curveTo(4.5f, 7.9f, 7.9f, 4.5f, 12f, 4.5f)
            curveTo(16.1f, 4.5f, 19.5f, 7.9f, 19.5f, 12f)
            curveTo(19.5f, 16.1f, 16.1f, 19.5f, 12f, 19.5f)
            curveTo(9.4f, 19.5f, 7.1f, 18.2f, 5.8f, 16.2f)
            moveTo(2.2f, 9.4f); lineTo(4.5f, 12.2f); lineTo(6.8f, 9.4f)
        }
    }

    val Dopasuj by lazy {
        icon("Dopasuj do ekranu") {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        }
    }

    val Wezel by lazy {
        icon("Nowy węzeł") {
            moveTo(3.5f, 8.5f); lineTo(14.5f, 8.5f); lineTo(14.5f, 15.5f); lineTo(3.5f, 15.5f); close()
            moveTo(19f, 8.5f); lineTo(19f, 15.5f)
            moveTo(15.5f, 12f); lineTo(22.5f, 12f)
        }
    }

    // Ikony do wyboru przy folderze przedmiotu

    val Litery by lazy {
        icon("Litery") {
            moveTo(5f, 17.5f); lineTo(11f, 4f); lineTo(17f, 17.5f)
            moveTo(7.5f, 12.2f); lineTo(14.5f, 12.2f)
            moveTo(4f, 20.8f); lineTo(20f, 20.8f)
        }
    }

    val Dzialania by lazy {
        icon("Działania") {
            moveTo(7f, 4f); lineTo(7f, 10f)
            moveTo(4f, 7f); lineTo(10f, 7f)
            moveTo(14f, 7f); lineTo(20f, 7f)
            moveTo(4.9f, 14.9f); lineTo(9.1f, 19.1f)
            moveTo(9.1f, 14.9f); lineTo(4.9f, 19.1f)
            moveTo(14f, 17f); lineTo(20f, 17f)
            kropka(17f, 14.2f)
            kropka(17f, 19.8f)
        }
    }

    val Nuta by lazy {
        icon("Nuta") {
            kolo(7.4f, 17.4f, 2.6f)
            kolo(17f, 15.4f, 2.6f)
            moveTo(10f, 17.4f); lineTo(10f, 5.6f)
            moveTo(19.6f, 15.4f); lineTo(19.6f, 3.6f)
            moveTo(10f, 5.6f); lineTo(19.6f, 3.6f)
            moveTo(10f, 8.6f); lineTo(19.6f, 6.6f)
        }
    }

    val Kolba by lazy {
        icon("Kolba") {
            moveTo(9.5f, 3.5f); lineTo(9.5f, 9.5f); lineTo(4.6f, 18.4f)
            curveTo(3.8f, 19.9f, 4.7f, 21f, 6f, 21f)
            lineTo(18f, 21f)
            curveTo(19.3f, 21f, 20.2f, 19.9f, 19.4f, 18.4f)
            lineTo(14.5f, 9.5f); lineTo(14.5f, 3.5f)
            moveTo(8f, 3.5f); lineTo(16f, 3.5f)
            moveTo(7.2f, 14.5f); lineTo(16.8f, 14.5f)
        }
    }

    val Globus by lazy {
        icon("Globus") {
            kolo(12f, 12f, 8.5f)
            moveTo(12f, 3.5f)
            curveTo(15.2f, 6.2f, 15.2f, 17.8f, 12f, 20.5f)
            curveTo(8.8f, 17.8f, 8.8f, 6.2f, 12f, 3.5f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(5.4f, 7.4f); curveTo(8.2f, 9f, 15.8f, 9f, 18.6f, 7.4f)
            moveTo(5.4f, 16.6f); curveTo(8.2f, 15f, 15.8f, 15f, 18.6f, 16.6f)
        }
    }

    /** Ikona folderu wybrana przez użytkownika. */
    fun folderIcon(id: String?): ImageVector = when (id) {
        "ksiazki" -> Biblioteka
        "litery" -> Litery
        "dzialania" -> Dzialania
        "nuta" -> Nuta
        "kolba" -> Kolba
        "globus" -> Globus
        "kod" -> PlikKodu
        "gwiazdka" -> Ulubione
        else -> Folder
    }
}

// Elementy wspólne dla kilku ikon

/** Ramka kartki, wspólna dla ikon notatek. */
private fun PathBuilder.kartka() {
    moveTo(5.5f, 3.5f); lineTo(18.5f, 3.5f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
}

/** Okrąg złożony z dwóch półłuków, bo PathBuilder nie ma gotowego okręgu. */
private fun PathBuilder.kolo(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

/** Kropka. Bardzo krótki odcinek z okrągłym zakończeniem daje pełne kółko. */
private fun PathBuilder.kropka(cx: Float, cy: Float) {
    moveTo(cx, cy)
    lineTo(cx + 0.01f, cy)
}

private fun icon(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathData(build),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
