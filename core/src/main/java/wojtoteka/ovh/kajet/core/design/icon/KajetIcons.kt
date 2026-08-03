package wojtoteka.ovh.kajet.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

object KajetIcons {

    // Biblioteka i pliki
    val Library by lazy {
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

    val FolderOpen by lazy {
        icon("Folder otwarty") {
            moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f)
            lineTo(19f, 8.5f); lineTo(19f, 11.5f)
            moveTo(3.5f, 19f); lineTo(6.5f, 11.5f); lineTo(22f, 11.5f); lineTo(19f, 19f); close()
        }
    }

    val HandwrittenNote by lazy {
        icon("Notatka odręczna") {
            sheet()
            moveTo(8.5f, 11.5f)
            curveTo(9.8f, 8.5f, 11.2f, 14.5f, 12.5f, 11.5f)
            curveTo(13.4f, 9.5f, 14.5f, 11f, 15.5f, 11.5f)
            moveTo(8.5f, 15.5f); lineTo(15.5f, 15.5f)
        }
    }

    val TextNote by lazy {
        icon("Notatka tekstowa") {
            sheet()
            moveTo(8.5f, 8.5f); lineTo(15.5f, 8.5f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            moveTo(8.5f, 15.5f); lineTo(12.5f, 15.5f)
        }
    }

    val MindMapIcon by lazy {
        icon("Mapa myśli") {
            moveTo(2.5f, 9.5f); lineTo(9f, 9.5f); lineTo(9f, 14.5f); lineTo(2.5f, 14.5f); close()
            moveTo(15f, 4.5f); lineTo(21.5f, 4.5f); lineTo(21.5f, 9f); lineTo(15f, 9f); close()
            moveTo(15f, 15f); lineTo(21.5f, 15f); lineTo(21.5f, 19.5f); lineTo(15f, 19.5f); close()
            moveTo(9f, 11.5f); curveTo(12f, 11.5f, 12f, 6.75f, 15f, 6.75f)
            moveTo(9f, 12.5f); curveTo(12f, 12.5f, 12f, 17.25f, 15f, 17.25f)
        }
    }

    val CodeFile by lazy {
        icon("Plik z kodem") {
            sheet()
            moveTo(10.5f, 9.5f); lineTo(8f, 12f); lineTo(10.5f, 14.5f)
            moveTo(13.5f, 9.5f); lineTo(16f, 12f); lineTo(13.5f, 14.5f)
        }
    }

    val Favourites by lazy {
        icon("Ulubione") {
            moveTo(12f, 4.3f); lineTo(14f, 9.55f); lineTo(19.61f, 9.83f); lineTo(15.23f, 13.35f)
            lineTo(16.7f, 18.77f); lineTo(12f, 15.7f); lineTo(7.3f, 18.77f); lineTo(8.77f, 13.35f)
            lineTo(4.39f, 9.83f); lineTo(10f, 9.55f); close()
        }
    }

    val Recent by lazy {
        icon("Ostatnio otwarte") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(12f, 12.3f); lineTo(15.8f, 14f)
        }
    }

    val Tag by lazy {
        icon("Tag") {
            moveTo(20.5f, 12.5f); lineTo(12.5f, 20.5f); lineTo(3.5f, 11.5f); lineTo(3.5f, 3.5f)
            lineTo(11.5f, 3.5f); close()
            circle(7.4f, 7.4f, 1.3f)
        }
    }

    val Bin by lazy {
        icon("Kosz") {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9.5f, 6.5f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.5f, 6.5f)
            moveTo(10.5f, 10f); lineTo(10.8f, 16.5f)
            moveTo(13.5f, 10f); lineTo(13.2f, 16.5f)
        }
    }

    val Search by lazy {
        icon("Szukaj") {
            circle(10.5f, 10.5f, 6.2f)
            moveTo(15.1f, 15.1f); lineTo(20f, 20f)
        }
    }

    val Plus by lazy {
        icon("Dodaj") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val MoreDots by lazy {
        icon("Więcej") {
            dot(12f, 5.6f); dot(12f, 12f); dot(12f, 18.4f)
        }
    }

    val ArrowRight by lazy {
        icon("Rozwiń") {
            moveTo(9.5f, 5f); lineTo(16.5f, 12f); lineTo(9.5f, 19f)
        }
    }

    val ArrowDown by lazy {
        icon("Zwiń") {
            moveTo(5f, 9.5f); lineTo(12f, 16.5f); lineTo(19f, 9.5f)
        }
    }

    val BackArrow by lazy {
        icon("Wstecz") {
            moveTo(19.5f, 12f); lineTo(4.5f, 12f)
            moveTo(10.5f, 6f); lineTo(4.5f, 12f); lineTo(10.5f, 18f)
        }
    }

    val SettingsCog by lazy {
        icon("Ustawienia") {
            moveTo(3.5f, 7f); lineTo(20.5f, 7f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(3.5f, 17f); lineTo(20.5f, 17f)
            circle(15f, 7f, 2.1f)
            circle(8.5f, 12f, 2.1f)
            circle(16.5f, 17f, 2.1f)
        }
    }

    // Narzędzia do pisania
    val Pen by lazy {
        icon("Pióro") {
            moveTo(4.2f, 19.8f); lineTo(5.6f, 15.2f); lineTo(16.6f, 4.2f); lineTo(19.8f, 7.4f)
            lineTo(8.8f, 18.4f); close()
            moveTo(5.6f, 15.2f); lineTo(8.8f, 18.4f)
            moveTo(14.4f, 6.4f); lineTo(17.6f, 9.6f)
        }
    }

    val Highlighter by lazy {
        icon("Zakreślacz") {
            moveTo(9f, 14.5f); lineTo(15f, 8.5f); lineTo(18.5f, 12f); lineTo(12.5f, 18f); close()
            moveTo(15f, 8.5f); lineTo(17f, 6.5f); lineTo(20.5f, 10f); lineTo(18.5f, 12f)
            moveTo(9f, 14.5f); lineTo(6f, 17.5f); lineTo(9.5f, 18f); lineTo(12.5f, 18f)
            moveTo(4f, 21f); lineTo(20f, 21f)
        }
    }

    val Eraser by lazy {
        icon("Gumka") {
            moveTo(5f, 18.5f); lineTo(5f, 15.5f); lineTo(14f, 6.5f); lineTo(18.5f, 11f)
            lineTo(11f, 18.5f); close()
            moveTo(9.5f, 11f); lineTo(14f, 15.5f)
            moveTo(4f, 21.2f); lineTo(20f, 21.2f)
        }
    }

    val EraserStroke by lazy {
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

    val Ruler by lazy {
        icon("Linijka") {
            moveTo(2.5f, 8.5f); lineTo(21.5f, 8.5f); lineTo(21.5f, 15.5f); lineTo(2.5f, 15.5f); close()
            moveTo(6f, 8.5f); lineTo(6f, 12.5f)
            moveTo(9f, 8.5f); lineTo(9f, 11f)
            moveTo(12f, 8.5f); lineTo(12f, 12.5f)
            moveTo(15f, 8.5f); lineTo(15f, 11f)
            moveTo(18f, 8.5f); lineTo(18f, 12.5f)
        }
    }

    val Undo by lazy {
        icon("Cofnij") {
            moveTo(8.5f, 7f); lineTo(4f, 11.5f); lineTo(8.5f, 16f)
            moveTo(4f, 11.5f); lineTo(13.5f, 11.5f)
            curveTo(17.5f, 11.5f, 20f, 14f, 20f, 17.5f)
            lineTo(20f, 19.5f)
        }
    }

    val Redo by lazy {
        icon("Ponów") {
            moveTo(15.5f, 7f); lineTo(20f, 11.5f); lineTo(15.5f, 16f)
            moveTo(20f, 11.5f); lineTo(10.5f, 11.5f)
            curveTo(6.5f, 11.5f, 4f, 14f, 4f, 17.5f)
            lineTo(4f, 19.5f)
        }
    }

    val TextBox by lazy {
        icon("Pole tekstowe") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
            moveTo(8.5f, 9.5f); lineTo(15.5f, 9.5f)
            moveTo(12f, 9.5f); lineTo(12f, 15f)
        }
    }

    val PhotoFrame by lazy {
        icon("Zdjęcie") {
            moveTo(3.5f, 5f); lineTo(20.5f, 5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            circle(8.5f, 9.5f, 1.5f)
            moveTo(3.5f, 16f); lineTo(9f, 10.5f); lineTo(14f, 15.5f); lineTo(16.5f, 13f); lineTo(20.5f, 17f)
        }
    }

    val CameraBody by lazy {
        icon("Aparat") {
            moveTo(3.5f, 7.5f); lineTo(8f, 7.5f); lineTo(9.5f, 5.5f); lineTo(14.5f, 5.5f)
            lineTo(16f, 7.5f); lineTo(20.5f, 7.5f); lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            circle(12f, 13f, 3.6f)
        }
    }

    val DrawingPad by lazy {
        icon("Rysunek") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
            moveTo(6.5f, 14.5f)
            curveTo(9f, 8.5f, 11f, 17f, 13.5f, 12f)
            curveTo(15f, 9f, 16.5f, 11.5f, 17.5f, 10f)
        }
    }

    val ColorSwatch by lazy {
        icon("Kolor") {
            moveTo(12f, 3.2f)
            curveTo(12f, 3.2f, 5.5f, 11f, 5.5f, 15f)
            curveTo(5.5f, 18.6f, 8.4f, 21f, 12f, 21f)
            curveTo(15.6f, 21f, 18.5f, 18.6f, 18.5f, 15f)
            curveTo(18.5f, 11f, 12f, 3.2f, 12f, 3.2f)
            close()
        }
    }

    val PageRuling by lazy {
        icon("Tło strony") {
            moveTo(4.5f, 3.5f); lineTo(19.5f, 3.5f); lineTo(19.5f, 20.5f); lineTo(4.5f, 20.5f); close()
            moveTo(4.5f, 9.2f); lineTo(19.5f, 9.2f)
            moveTo(4.5f, 14.8f); lineTo(19.5f, 14.8f)
            moveTo(9.5f, 3.5f); lineTo(9.5f, 20.5f)
            moveTo(14.5f, 3.5f); lineTo(14.5f, 20.5f)
        }
    }

    val AddPage by lazy {
        icon("Dodaj stronę") {
            moveTo(5f, 3.5f); lineTo(19f, 3.5f); lineTo(19f, 20.5f); lineTo(5f, 20.5f); close()
            moveTo(12f, 8.5f); lineTo(12f, 15.5f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
        }
    }

    val FingerDraws by lazy {
        icon("Palec rysuje") {
            moveTo(9f, 12.5f); lineTo(9f, 5.8f)
            curveTo(9f, 4.2f, 11.4f, 4.2f, 11.4f, 5.8f)
            lineTo(11.4f, 11.5f)
            moveTo(11.4f, 8.6f)
            curveTo(11.4f, 7.2f, 13.7f, 7.2f, 13.7f, 8.6f)
            lineTo(13.7f, 11.8f)
            moveTo(13.7f, 9.6f)
            curveTo(13.7f, 8.3f, 16f, 8.3f, 16f, 9.6f)
            lineTo(16f, 14.5f)
            curveTo(16f, 19f, 13.2f, 20.8f, 10.6f, 20.8f)
            curveTo(8f, 20.8f, 6.6f, 19f, 5.8f, 16.6f)
            lineTo(4.6f, 13.4f)
            curveTo(4.1f, 12f, 6.2f, 11.2f, 6.9f, 12.6f)
            lineTo(9f, 16.2f)
            moveTo(17.5f, 4.5f); lineTo(21f, 4.5f)
        }
    }

    val FingerScrolls by lazy {
        icon("Palec przewija stronę") {
            moveTo(9f, 12.5f); lineTo(9f, 5.8f)
            curveTo(9f, 4.2f, 11.4f, 4.2f, 11.4f, 5.8f)
            lineTo(11.4f, 11.5f)
            moveTo(11.4f, 8.6f)
            curveTo(11.4f, 7.2f, 13.7f, 7.2f, 13.7f, 8.6f)
            lineTo(13.7f, 11.8f)
            moveTo(13.7f, 9.6f)
            curveTo(13.7f, 8.3f, 16f, 8.3f, 16f, 9.6f)
            lineTo(16f, 14.5f)
            curveTo(16f, 19f, 13.2f, 20.8f, 10.6f, 20.8f)
            curveTo(8f, 20.8f, 6.6f, 19f, 5.8f, 16.6f)
            lineTo(4.6f, 13.4f)
            curveTo(4.1f, 12f, 6.2f, 11.2f, 6.9f, 12.6f)
            lineTo(9f, 16.2f)
            moveTo(19.5f, 3.5f); lineTo(19.5f, 9.5f)
            moveTo(17.5f, 7.5f); lineTo(19.5f, 9.5f); lineTo(21.5f, 7.5f)
        }
    }

    val RecogniseText by lazy {
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
    val Export by lazy {
        icon("Eksportuj") {
            moveTo(4.5f, 14.5f); lineTo(4.5f, 20f); lineTo(19.5f, 20f); lineTo(19.5f, 14.5f)
            moveTo(12f, 15.5f); lineTo(12f, 4f)
            moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f)
        }
    }

    val ShareArrow by lazy {
        icon("Udostępnij") {
            circle(6f, 12f, 2.4f)
            circle(17.2f, 6.4f, 2.4f)
            circle(17.2f, 17.6f, 2.4f)
            moveTo(8.15f, 10.93f); lineTo(15.05f, 7.47f)
            moveTo(8.15f, 13.07f); lineTo(15.05f, 16.53f)
        }
    }

    val Printer by lazy {
        icon("Drukuj") {
            moveTo(7.5f, 8.5f); lineTo(7.5f, 3.5f); lineTo(16.5f, 3.5f); lineTo(16.5f, 8.5f)
            moveTo(4.5f, 8.5f); lineTo(19.5f, 8.5f); lineTo(19.5f, 16.5f); lineTo(4.5f, 16.5f); close()
            moveTo(7.5f, 13.5f); lineTo(16.5f, 13.5f); lineTo(16.5f, 20.5f); lineTo(7.5f, 20.5f); close()
            dot(16.8f, 11f)
        }
    }

    val Close by lazy {
        icon("Zamknij") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Confirm by lazy {
        icon("Zatwierdź") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 6.5f)
        }
    }

    // Kod
    val PlayRun by lazy {
        icon("Uruchom") {
            moveTo(7.5f, 4.5f); lineTo(19.5f, 12f); lineTo(7.5f, 19.5f); close()
        }
    }

    val StopSquare by lazy {
        icon("Zatrzymaj") {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close()
        }
    }

    val OutputPanel by lazy {
        icon("Wynik") {
            moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
            moveTo(7f, 9.5f); lineTo(10f, 12.5f); lineTo(7f, 15.5f)
            moveTo(12.5f, 15.5f); lineTo(17f, 15.5f)
        }
    }

    val ErrorMark by lazy {
        icon("Błędy") {
            moveTo(12f, 4f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
            moveTo(12f, 10f); lineTo(12f, 15f)
            dot(12f, 17.6f)
        }
    }

    val InputArrow by lazy {
        icon("Wejście") {
            moveTo(12f, 3.5f); lineTo(12f, 13.5f)
            moveTo(8f, 9.5f); lineTo(12f, 13.5f); lineTo(16f, 9.5f)
            moveTo(4.5f, 17f); lineTo(4.5f, 20.5f); lineTo(19.5f, 20.5f); lineTo(19.5f, 17f)
        }
    }

    val WordWrap by lazy {
        icon("Zawijanie wierszy") {
            moveTo(4f, 6.5f); lineTo(20f, 6.5f)
            moveTo(4f, 12f); lineTo(16f, 12f)
            curveTo(19f, 12f, 19f, 16.5f, 16f, 16.5f)
            lineTo(11.5f, 16.5f)
            moveTo(13.5f, 14.5f); lineTo(11.5f, 16.5f); lineTo(13.5f, 18.5f)
        }
    }

    val Offline by lazy {
        icon("Brak internetu") {
            moveTo(3.5f, 9.5f); curveTo(6f, 7.2f, 9f, 6f, 12f, 6f)
            curveTo(15f, 6f, 18f, 7.2f, 20.5f, 9.5f)
            moveTo(7f, 13f); curveTo(8.5f, 11.7f, 10.2f, 11f, 12f, 11f)
            curveTo(13.2f, 11f, 14.4f, 11.3f, 15.5f, 11.9f)
            dot(12f, 17.5f)
            moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
        }
    }

    // Działania na plikach
    val Move by lazy {
        icon("Przenieś") {
            moveTo(3.5f, 19f); lineTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f)
            lineTo(20.5f, 8.5f); lineTo(20.5f, 19f); close()
            moveTo(8f, 13.8f); lineTo(15.5f, 13.8f)
            moveTo(13f, 11.3f); lineTo(15.5f, 13.8f); lineTo(13f, 16.3f)
        }
    }

    val Copy by lazy {
        icon("Kopiuj") {
            moveTo(8.5f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 15.5f); lineTo(8.5f, 15.5f); close()
            moveTo(15.5f, 15.5f); lineTo(15.5f, 20.5f); lineTo(3.5f, 20.5f); lineTo(3.5f, 8.5f)
            lineTo(8.5f, 8.5f)
        }
    }

    val Restore by lazy {
        icon("Przywróć") {
            moveTo(4.5f, 12f)
            curveTo(4.5f, 7.9f, 7.9f, 4.5f, 12f, 4.5f)
            curveTo(16.1f, 4.5f, 19.5f, 7.9f, 19.5f, 12f)
            curveTo(19.5f, 16.1f, 16.1f, 19.5f, 12f, 19.5f)
            curveTo(9.4f, 19.5f, 7.1f, 18.2f, 5.8f, 16.2f)
            moveTo(2.2f, 9.4f); lineTo(4.5f, 12.2f); lineTo(6.8f, 9.4f)
        }
    }

    val FitToView by lazy {
        icon("Dopasuj do ekranu") {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        }
    }

    val NodeDot by lazy {
        icon("Nowy węzeł") {
            moveTo(3.5f, 8.5f); lineTo(14.5f, 8.5f); lineTo(14.5f, 15.5f); lineTo(3.5f, 15.5f); close()
            moveTo(19f, 8.5f); lineTo(19f, 15.5f)
            moveTo(15.5f, 12f); lineTo(22.5f, 12f)
        }
    }

    // Ikony do wyboru przy folderze przedmiotu

    val Letters by lazy {
        icon("Litery") {
            moveTo(5f, 17.5f); lineTo(11f, 4f); lineTo(17f, 17.5f)
            moveTo(7.5f, 12.2f); lineTo(14.5f, 12.2f)
            moveTo(4f, 20.8f); lineTo(20f, 20.8f)
        }
    }

    val Operations by lazy {
        icon("Działania") {
            moveTo(7f, 4f); lineTo(7f, 10f)
            moveTo(4f, 7f); lineTo(10f, 7f)
            moveTo(14f, 7f); lineTo(20f, 7f)
            moveTo(4.9f, 14.9f); lineTo(9.1f, 19.1f)
            moveTo(9.1f, 14.9f); lineTo(4.9f, 19.1f)
            moveTo(14f, 17f); lineTo(20f, 17f)
            dot(17f, 14.2f)
            dot(17f, 19.8f)
        }
    }

    val MusicNote by lazy {
        icon("Nuta") {
            circle(7.4f, 17.4f, 2.6f)
            circle(17f, 15.4f, 2.6f)
            moveTo(10f, 17.4f); lineTo(10f, 5.6f)
            moveTo(19.6f, 15.4f); lineTo(19.6f, 3.6f)
            moveTo(10f, 5.6f); lineTo(19.6f, 3.6f)
            moveTo(10f, 8.6f); lineTo(19.6f, 6.6f)
        }
    }

    val Flask by lazy {
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

    val Globe by lazy {
        icon("Globus") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 3.5f)
            curveTo(15.2f, 6.2f, 15.2f, 17.8f, 12f, 20.5f)
            curveTo(8.8f, 17.8f, 8.8f, 6.2f, 12f, 3.5f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(5.4f, 7.4f); curveTo(8.2f, 9f, 15.8f, 9f, 18.6f, 7.4f)
            moveTo(5.4f, 16.6f); curveTo(8.2f, 15f, 15.8f, 15f, 18.6f, 16.6f)
        }
    }

    val BrushTip by lazy {
        icon("Pędzel") {
            moveTo(19.5f, 3.5f); lineTo(9.5f, 13.5f)
            moveTo(10.5f, 14.5f); lineTo(20.5f, 4.5f)
            moveTo(9.5f, 13.5f); lineTo(10.5f, 14.5f)
            moveTo(8.5f, 14.5f)
            curveTo(6.5f, 14.5f, 5.5f, 16f, 5.5f, 17.5f)
            curveTo(5.5f, 19f, 4.5f, 19.5f, 3.5f, 20f)
            curveTo(5f, 21f, 9.5f, 21f, 9.5f, 17.5f)
            curveTo(9.5f, 16f, 9.5f, 14.5f, 8.5f, 14.5f)
            close()
        }
    }

    val Heart by lazy {
        icon("Serce") {
            moveTo(12f, 20.2f)
            lineTo(4.3f, 12.6f)
            curveTo(2.2f, 10.5f, 2.6f, 6.7f, 5.4f, 5.1f)
            curveTo(7.6f, 3.9f, 10.3f, 4.7f, 12f, 7f)
            curveTo(13.7f, 4.7f, 16.4f, 3.9f, 18.6f, 5.1f)
            curveTo(21.4f, 6.7f, 21.8f, 10.5f, 19.7f, 12.6f)
            close()
        }
    }

    val Atom by lazy {
        icon("Atom") {
            circle(12f, 12f, 2.2f)
            moveTo(12f, 3.2f)
            curveTo(17.5f, 3.2f, 21.5f, 7.2f, 21.5f, 12f)
            curveTo(21.5f, 16.8f, 17.5f, 20.8f, 12f, 20.8f)
            curveTo(6.5f, 20.8f, 2.5f, 16.8f, 2.5f, 12f)
            curveTo(2.5f, 7.2f, 6.5f, 3.2f, 12f, 3.2f)
            close()
            moveTo(5.2f, 5.2f)
            curveTo(8.5f, 2.4f, 15.5f, 2.4f, 18.8f, 5.2f)
        }
    }

    val Dna by lazy {
        icon("Nić DNA") {
            moveTo(7f, 3.5f)
            curveTo(7f, 8f, 17f, 8f, 17f, 12f)
            curveTo(17f, 16f, 7f, 16f, 7f, 20.5f)
            moveTo(17f, 3.5f)
            curveTo(17f, 8f, 7f, 8f, 7f, 12f)
            curveTo(7f, 16f, 17f, 16f, 17f, 20.5f)
            moveTo(8.6f, 6.5f); lineTo(15.4f, 6.5f)
            moveTo(8.6f, 17.5f); lineTo(15.4f, 17.5f)
        }
    }

    val MapPin by lazy {
        icon("Mapa") {
            moveTo(3.5f, 6.5f); lineTo(9f, 4f); lineTo(15f, 7f); lineTo(20.5f, 4.5f)
            lineTo(20.5f, 17.5f); lineTo(15f, 20f); lineTo(9f, 17f); lineTo(3.5f, 19.5f)
            close()
            moveTo(9f, 4f); lineTo(9f, 17f)
            moveTo(15f, 7f); lineTo(15f, 20f)
        }
    }

    val Cog by lazy {
        icon("Zębatka") {
            circle(12f, 12f, 3.2f)
            moveTo(12f, 2.8f); lineTo(12f, 5.4f)
            moveTo(12f, 18.6f); lineTo(12f, 21.2f)
            moveTo(2.8f, 12f); lineTo(5.4f, 12f)
            moveTo(18.6f, 12f); lineTo(21.2f, 12f)
            moveTo(5.5f, 5.5f); lineTo(7.3f, 7.3f)
            moveTo(16.7f, 16.7f); lineTo(18.5f, 18.5f)
            moveTo(18.5f, 5.5f); lineTo(16.7f, 7.3f)
            moveTo(7.3f, 16.7f); lineTo(5.5f, 18.5f)
        }
    }

    val Bulb by lazy {
        icon("Żarówka") {
            moveTo(9f, 17.5f)
            curveTo(9f, 15f, 5.5f, 13.5f, 5.5f, 10f)
            curveTo(5.5f, 6.4f, 8.4f, 3.5f, 12f, 3.5f)
            curveTo(15.6f, 3.5f, 18.5f, 6.4f, 18.5f, 10f)
            curveTo(18.5f, 13.5f, 15f, 15f, 15f, 17.5f)
            close()
            moveTo(9.5f, 20.5f); lineTo(14.5f, 20.5f)
        }
    }

    val Compass by lazy {
        icon("Kompas") {
            circle(12f, 12f, 8.5f)
            moveTo(15.5f, 8.5f); lineTo(13.5f, 13.5f); lineTo(8.5f, 15.5f); lineTo(10.5f, 10.5f)
            close()
        }
    }

    val Rocket by lazy {
        icon("Rakieta") {
            moveTo(12f, 3f)
            curveTo(15.5f, 6f, 16.5f, 10f, 16f, 14.5f)
            lineTo(8f, 14.5f)
            curveTo(7.5f, 10f, 8.5f, 6f, 12f, 3f)
            close()
            circle(12f, 9f, 1.8f)
            moveTo(8f, 12f); lineTo(5f, 15f); lineTo(5f, 18f); lineTo(8.4f, 16.4f)
            moveTo(16f, 12f); lineTo(19f, 15f); lineTo(19f, 18f); lineTo(15.6f, 16.4f)
            moveTo(10.5f, 17.5f); lineTo(12f, 21f); lineTo(13.5f, 17.5f)
        }
    }

    val Crown by lazy {
        icon("Korona") {
            moveTo(4f, 18.5f); lineTo(20f, 18.5f)
            moveTo(4f, 15.5f); lineTo(2.8f, 6.5f); lineTo(8f, 11f); lineTo(12f, 4.5f)
            lineTo(16f, 11f); lineTo(21.2f, 6.5f); lineTo(20f, 15.5f)
            close()
        }
    }

    val Cup by lazy {
        icon("Filiżanka") {
            moveTo(4.5f, 8.5f); lineTo(16.5f, 8.5f); lineTo(16.5f, 15f)
            curveTo(16.5f, 17.5f, 14.5f, 19.5f, 12f, 19.5f)
            lineTo(9f, 19.5f)
            curveTo(6.5f, 19.5f, 4.5f, 17.5f, 4.5f, 15f)
            close()
            moveTo(16.5f, 10.5f); lineTo(19f, 10.5f)
            curveTo(20.9f, 10.5f, 20.9f, 15.5f, 19f, 15.5f)
            lineTo(16.5f, 15.5f)
            moveTo(8f, 3.5f); lineTo(8f, 5.5f)
            moveTo(12f, 3.5f); lineTo(12f, 5.5f)
        }
    }

    val Tree by lazy {
        icon("Drzewo") {
            moveTo(12f, 21f); lineTo(12f, 14f)
            moveTo(12f, 14f)
            curveTo(8.5f, 14f, 6f, 11.8f, 6f, 9f)
            curveTo(6f, 5.8f, 8.7f, 3.2f, 12f, 3.2f)
            curveTo(15.3f, 3.2f, 18f, 5.8f, 18f, 9f)
            curveTo(18f, 11.8f, 15.5f, 14f, 12f, 14f)
            close()
            moveTo(12f, 17f); lineTo(9f, 14.5f)
            moveTo(12f, 18.5f); lineTo(15f, 16f)
        }
    }

    val Mountain by lazy {
        icon("Góra") {
            moveTo(2.5f, 19.5f); lineTo(9f, 7f); lineTo(13f, 14f); lineTo(15.5f, 10f)
            lineTo(21.5f, 19.5f)
            close()
            moveTo(7f, 10.6f); lineTo(11f, 10.6f)
        }
    }

    val CloudMark by lazy {
        icon("Chmura") {
            moveTo(7.5f, 18.5f)
            curveTo(4.7f, 18.5f, 2.5f, 16.3f, 2.5f, 13.5f)
            curveTo(2.5f, 10.9f, 4.5f, 8.8f, 7f, 8.5f)
            curveTo(7.9f, 5.9f, 10.3f, 4.2f, 13f, 4.5f)
            curveTo(16.1f, 4.8f, 18.4f, 7.4f, 18.5f, 10.4f)
            curveTo(20.4f, 11f, 21.6f, 12.9f, 21.4f, 14.9f)
            curveTo(21.2f, 16.9f, 19.5f, 18.5f, 17.5f, 18.5f)
            close()
        }
    }

    val KeyShape by lazy {
        icon("Klucz") {
            circle(7.5f, 14.5f, 4f)
            moveTo(10.4f, 11.6f); lineTo(20.5f, 3.5f)
            moveTo(17.5f, 6f); lineTo(19.5f, 8f)
            moveTo(15f, 8f); lineTo(17f, 10f)
        }
    }

    val Clock by lazy {
        icon("Zegar") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 6.8f); lineTo(12f, 12f); lineTo(15.6f, 14.2f)
        }
    }

    val Calendar by lazy {
        icon("Kalendarz") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 20.5f); lineTo(3.5f, 20.5f); close()
            moveTo(3.5f, 10f); lineTo(20.5f, 10f)
            moveTo(8f, 3f); lineTo(8f, 7.5f)
            moveTo(16f, 3f); lineTo(16f, 7.5f)
            dot(8f, 14f)
            dot(12f, 14f)
            dot(16f, 14f)
            dot(8f, 17.5f)
            dot(12f, 17.5f)
        }
    }

    val Flag by lazy {
        icon("Flaga") {
            moveTo(5.5f, 21f); lineTo(5.5f, 3.5f)
            moveTo(5.5f, 4.5f); lineTo(19f, 4.5f); lineTo(16f, 9f); lineTo(19f, 13.5f); lineTo(5.5f, 13.5f)
        }
    }

    val Microscope by lazy {
        icon("Mikroskop") {
            moveTo(6.5f, 20.5f); lineTo(20.5f, 20.5f)
            moveTo(9f, 20.5f)
            curveTo(9f, 16.5f, 11.5f, 13.5f, 15.5f, 13.5f)
            curveTo(19.5f, 13.5f, 20.5f, 17f, 20.5f, 20.5f)
            moveTo(8f, 6f); lineTo(12.5f, 3.5f); lineTo(15.5f, 9f); lineTo(11f, 11.5f); close()
            moveTo(6f, 9.5f); lineTo(9.5f, 7.5f)
            moveTo(11f, 11.5f); lineTo(9.5f, 14f)
        }
    }

    val Ball by lazy {
        icon("Piłka") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(16.2f, 10.1f); lineTo(14.6f, 15.1f); lineTo(9.4f, 15.1f)
            lineTo(7.8f, 10.1f)
            close()
            moveTo(12f, 3.5f); lineTo(12f, 7f)
            moveTo(20.1f, 9.4f); lineTo(16.2f, 10.1f)
            moveTo(17f, 19.3f); lineTo(14.6f, 15.1f)
            moveTo(7f, 19.3f); lineTo(9.4f, 15.1f)
            moveTo(3.9f, 9.4f); lineTo(7.8f, 10.1f)
        }
    }

    val Mask by lazy {
        icon("Maska") {
            moveTo(4f, 5.5f)
            curveTo(9f, 4f, 15f, 4f, 20f, 5.5f)
            curveTo(20f, 14f, 17f, 20.5f, 12f, 20.5f)
            curveTo(7f, 20.5f, 4f, 14f, 4f, 5.5f)
            close()
            dot(9f, 10.5f)
            dot(15f, 10.5f)
            moveTo(9.5f, 15.5f)
            curveTo(10.8f, 16.8f, 13.2f, 16.8f, 14.5f, 15.5f)
        }
    }

    val Scales by lazy {
        icon("Waga") {
            moveTo(12f, 4f); lineTo(12f, 20.5f)
            moveTo(6f, 20.5f); lineTo(18f, 20.5f)
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 7f); lineTo(1.8f, 13f); lineTo(6.2f, 13f); close()
            moveTo(20f, 7f); lineTo(17.8f, 13f); lineTo(22.2f, 13f); close()
            circle(12f, 4f, 1.4f)
        }
    }

    val Shield by lazy {
        icon("Tarcza") {
            moveTo(12f, 3f); lineTo(19.5f, 6f)
            curveTo(19.5f, 14f, 16.5f, 19f, 12f, 21f)
            curveTo(7.5f, 19f, 4.5f, 14f, 4.5f, 6f)
            close()
            moveTo(8.8f, 12f); lineTo(11f, 14.4f); lineTo(15.4f, 9.5f)
        }
    }

    val House by lazy {
        icon("Dom") {
            moveTo(3.5f, 10.5f); lineTo(12f, 3.5f); lineTo(20.5f, 10.5f)
            moveTo(5.5f, 9f); lineTo(5.5f, 20.5f); lineTo(18.5f, 20.5f); lineTo(18.5f, 9f)
            moveTo(9.8f, 20.5f); lineTo(9.8f, 14f); lineTo(14.2f, 14f); lineTo(14.2f, 20.5f)
        }
    }

    // Formatowanie tekstu

    val Bold by lazy {
        icon("Pogrubienie") {
            moveTo(6.5f, 4f); lineTo(6.5f, 20f)
            moveTo(6.5f, 4f); lineTo(13f, 4f)
            curveTo(16f, 4f, 16f, 11.5f, 13f, 11.5f)
            lineTo(6.5f, 11.5f)
            moveTo(6.5f, 11.5f); lineTo(14f, 11.5f)
            curveTo(17.5f, 11.5f, 17.5f, 20f, 14f, 20f)
            lineTo(6.5f, 20f)
        }
    }

    val Italic by lazy {
        icon("Kursywa") {
            moveTo(9f, 4.5f); lineTo(18f, 4.5f)
            moveTo(6f, 19.5f); lineTo(15f, 19.5f)
            moveTo(14f, 4.5f); lineTo(10f, 19.5f)
        }
    }

    val Underline by lazy {
        icon("Podkreślenie") {
            moveTo(6.5f, 3.5f); lineTo(6.5f, 11.5f)
            curveTo(6.5f, 18f, 17.5f, 18f, 17.5f, 11.5f)
            lineTo(17.5f, 3.5f)
            moveTo(5f, 20.5f); lineTo(19f, 20.5f)
        }
    }

    val Strikethrough by lazy {
        icon("Przekreślenie") {
            moveTo(7.5f, 5.5f)
            curveTo(9f, 3.8f, 15f, 3.8f, 16f, 6.5f)
            moveTo(16.5f, 16.5f)
            curveTo(15.5f, 19.5f, 8f, 19.8f, 7f, 16.5f)
            moveTo(9.5f, 12f); lineTo(15.5f, 12f)
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        }
    }

    val TextColour by lazy {
        icon("Kolor pisma") {
            moveTo(5.5f, 15f); lineTo(11f, 3.8f); lineTo(16.5f, 15f)
            moveTo(7.6f, 11f); lineTo(14.4f, 11f)
            moveTo(4.5f, 19.5f); lineTo(19.5f, 19.5f)
        }
    }

    val Highlight by lazy {
        icon("Wyróżnienie tekstu") {
            moveTo(8f, 13.5f); lineTo(14f, 4.5f); lineTo(19f, 8f); lineTo(13f, 17f); close()
            moveTo(8f, 13.5f); lineTo(5.5f, 16.5f); lineTo(9.5f, 17f); lineTo(13f, 17f)
            moveTo(3.5f, 20.5f); lineTo(20.5f, 20.5f)
        }
    }

    val TextSize by lazy {
        icon("Rozmiar pisma") {
            moveTo(2.8f, 19.5f); lineTo(8.4f, 5.5f); lineTo(14f, 19.5f)
            moveTo(5f, 14.5f); lineTo(11.8f, 14.5f)
            moveTo(15.5f, 19.5f); lineTo(18.4f, 11.5f); lineTo(21.3f, 19.5f)
            moveTo(16.6f, 16.7f); lineTo(20.2f, 16.7f)
        }
    }

    val DividerLine by lazy {
        icon("Linia oddzielająca") {
            moveTo(3.5f, 12f); lineTo(20.5f, 12f)
            moveTo(5.5f, 6.5f); lineTo(18.5f, 6.5f)
            moveTo(5.5f, 17.5f); lineTo(18.5f, 17.5f)
        }
    }

    val AlignLeft by lazy {
        icon("Wyrównaj do lewej") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f)
            moveTo(3.5f, 10.5f); lineTo(14f, 10.5f)
            moveTo(3.5f, 15.5f); lineTo(20.5f, 15.5f)
            moveTo(3.5f, 20.5f); lineTo(14f, 20.5f)
        }
    }

    val AlignCentre by lazy {
        icon("Wyrównaj do środka") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f)
            moveTo(6.8f, 10.5f); lineTo(17.2f, 10.5f)
            moveTo(3.5f, 15.5f); lineTo(20.5f, 15.5f)
            moveTo(6.8f, 20.5f); lineTo(17.2f, 20.5f)
        }
    }

    val AlignRight by lazy {
        icon("Wyrównaj do prawej") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f)
            moveTo(10f, 10.5f); lineTo(20.5f, 10.5f)
            moveTo(3.5f, 15.5f); lineTo(20.5f, 15.5f)
            moveTo(10f, 20.5f); lineTo(20.5f, 20.5f)
        }
    }

    val BulletList by lazy {
        icon("Lista punktowana") {
            dot(4.5f, 6.5f)
            dot(4.5f, 12f)
            dot(4.5f, 17.5f)
            moveTo(9f, 6.5f); lineTo(20.5f, 6.5f)
            moveTo(9f, 12f); lineTo(20.5f, 12f)
            moveTo(9f, 17.5f); lineTo(20.5f, 17.5f)
        }
    }

    val NumberedList by lazy {
        icon("Lista numerowana") {
            moveTo(3.4f, 4.6f); lineTo(4.6f, 4f); lineTo(4.6f, 8.4f)
            moveTo(3.2f, 12f)
            curveTo(3.2f, 10.6f, 5.6f, 10.6f, 5.6f, 12f)
            curveTo(5.6f, 13.2f, 3.2f, 13.8f, 3.2f, 15.4f)
            lineTo(5.8f, 15.4f)
            moveTo(3.3f, 18.4f)
            curveTo(4.4f, 17.6f, 5.8f, 18.2f, 5.6f, 19.2f)
            curveTo(5.5f, 19.9f, 4.7f, 20.1f, 4.2f, 20.1f)
            curveTo(4.7f, 20.1f, 5.7f, 20.3f, 5.7f, 21.2f)
            moveTo(9f, 6.2f); lineTo(20.5f, 6.2f)
            moveTo(9f, 13.4f); lineTo(20.5f, 13.4f)
            moveTo(9f, 19.8f); lineTo(20.5f, 19.8f)
        }
    }

    val TaskList by lazy {
        icon("Lista zadań") {
            moveTo(3.5f, 4.5f); lineTo(7.5f, 4.5f); lineTo(7.5f, 8.5f); lineTo(3.5f, 8.5f); close()
            moveTo(3.5f, 15.5f); lineTo(7.5f, 15.5f); lineTo(7.5f, 19.5f); lineTo(3.5f, 19.5f); close()
            moveTo(4.3f, 17.5f); lineTo(5.4f, 18.6f); lineTo(7.2f, 16.2f)
            moveTo(10.5f, 6.5f); lineTo(20.5f, 6.5f)
            moveTo(10.5f, 17.5f); lineTo(20.5f, 17.5f)
        }
    }

    val TableGrid by lazy {
        icon("Tabela") {
            moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
            moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f)
            moveTo(3.5f, 14.5f); lineTo(20.5f, 14.5f)
            moveTo(9.5f, 4.5f); lineTo(9.5f, 19.5f)
            moveTo(15f, 4.5f); lineTo(15f, 19.5f)
        }
    }

    val HeadingMark by lazy {
        icon("Nagłówek") {
            moveTo(4.5f, 4f); lineTo(4.5f, 20f)
            moveTo(13.5f, 4f); lineTo(13.5f, 20f)
            moveTo(4.5f, 12f); lineTo(13.5f, 12f)
            moveTo(17f, 12.5f); lineTo(18.8f, 11.5f); lineTo(18.8f, 20f)
        }
    }

    val Quote by lazy {
        icon("Cytat") {
            moveTo(9.5f, 6f)
            curveTo(6f, 6f, 4.5f, 8.5f, 4.5f, 12f)
            curveTo(4.5f, 15f, 6f, 16.5f, 8f, 16.5f)
            curveTo(10f, 16.5f, 11f, 15f, 11f, 13.3f)
            curveTo(11f, 11.5f, 9.7f, 10.3f, 8f, 10.3f)
            moveTo(20f, 6f)
            curveTo(16.5f, 6f, 15f, 8.5f, 15f, 12f)
            curveTo(15f, 15f, 16.5f, 16.5f, 18.5f, 16.5f)
            curveTo(20.5f, 16.5f, 21.5f, 15f, 21.5f, 13.3f)
            curveTo(21.5f, 11.5f, 20.2f, 10.3f, 18.5f, 10.3f)
        }
    }

    val LinkChain by lazy {
        icon("Odnośnik") {
            moveTo(10f, 14f)
            curveTo(11.6f, 15.6f, 14f, 15.6f, 15.5f, 14f)
            lineTo(19f, 10.5f)
            curveTo(20.6f, 9f, 20.6f, 6.5f, 19f, 5f)
            curveTo(17.5f, 3.4f, 15f, 3.4f, 13.5f, 5f)
            lineTo(12f, 6.5f)
            moveTo(14f, 10f)
            curveTo(12.4f, 8.4f, 10f, 8.4f, 8.5f, 10f)
            lineTo(5f, 13.5f)
            curveTo(3.4f, 15f, 3.4f, 17.5f, 5f, 19f)
            curveTo(6.5f, 20.6f, 9f, 20.6f, 10.5f, 19f)
            lineTo(12f, 17.5f)
        }
    }

    val Opacity by lazy {
        icon("Krycie") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 3.5f)
            lineTo(12f, 20.5f)
            moveTo(12f, 5.5f); lineTo(18.2f, 5.5f)
            moveTo(12f, 9f); lineTo(20.4f, 9f)
            moveTo(12f, 12.5f); lineTo(20.5f, 12.5f)
            moveTo(12f, 16f); lineTo(19.2f, 16f)
            moveTo(12f, 19.5f); lineTo(15.5f, 19.5f)
        }
    }

    val Thickness by lazy {
        icon("Grubość kreski") {
            moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f)
            moveTo(3.5f, 10f); lineTo(20.5f, 10f)
            moveTo(3.5f, 15f); lineTo(20.5f, 15f)
            moveTo(3.5f, 20f); lineTo(20.5f, 20f)
        }
    }

    val Fineliner by lazy {
        icon("Cienkopis") {
            moveTo(8f, 20.5f); lineTo(4f, 20.5f); lineTo(4f, 16.5f); lineTo(16.5f, 4f)
            lineTo(20.5f, 8f); lineTo(8f, 20.5f)
            close()
            moveTo(14f, 6.5f); lineTo(18f, 10.5f)
        }
    }

    val Pencil by lazy {
        icon("Ołówek") {
            moveTo(4f, 20.5f); lineTo(5.6f, 15.6f); lineTo(16.6f, 4.6f)
            curveTo(17.4f, 3.8f, 18.6f, 3.8f, 19.4f, 4.6f)
            curveTo(20.2f, 5.4f, 20.2f, 6.6f, 19.4f, 7.4f)
            lineTo(8.4f, 18.4f)
            close()
            moveTo(15f, 6.2f); lineTo(17.8f, 9f)
            moveTo(5.6f, 15.6f); lineTo(8.4f, 18.4f)
        }
    }

    val DashedLine by lazy {
        icon("Linia przerywana") {
            moveTo(3.5f, 12f); lineTo(6.5f, 12f)
            moveTo(10.5f, 12f); lineTo(13.5f, 12f)
            moveTo(17.5f, 12f); lineTo(20.5f, 12f)
        }
    }

    val Connect by lazy {
        icon("Połącz węzły") {
            circle(5.5f, 6f, 2.5f)
            circle(18.5f, 18f, 2.5f)
            moveTo(7.6f, 7.4f); lineTo(16.4f, 16.6f)
        }
    }

    val Saved by lazy {
        icon("Zapisano") {
            circle(12f, 12f, 8.5f)
            moveTo(8.2f, 12.2f); lineTo(11f, 15f); lineTo(15.9f, 9.2f)
        }
    }

    val CloudDone by lazy {
        icon("W chmurze") {
            moveTo(7.5f, 17.5f)
            curveTo(4.7f, 17.5f, 2.5f, 15.3f, 2.5f, 12.5f)
            curveTo(2.5f, 9.9f, 4.5f, 7.8f, 7f, 7.5f)
            curveTo(7.9f, 4.9f, 10.3f, 3.2f, 13f, 3.5f)
            curveTo(16.1f, 3.8f, 18.4f, 6.4f, 18.5f, 9.4f)
            curveTo(20.4f, 10f, 21.6f, 11.9f, 21.4f, 13.9f)
            curveTo(21.2f, 15.9f, 19.5f, 17.5f, 17.5f, 17.5f)
            close()
            moveTo(9.5f, 13f); lineTo(12f, 15.5f); lineTo(16f, 10.5f)
        }
    }

    val Account by lazy {
        icon("Konto") {
            circle(12f, 8f, 4f)
            moveTo(4.5f, 20.5f)
            curveTo(4.5f, 16.4f, 7.9f, 14f, 12f, 14f)
            curveTo(16.1f, 14f, 19.5f, 16.4f, 19.5f, 20.5f)
        }
    }

    val folderIcons: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "folder" to Folder,
            "ksiazki" to Library,
            "litery" to Letters,
            "dzialania" to Operations,
            "nuta" to MusicNote,
            "kolba" to Flask,
            "globus" to Globe,
            "kod" to CodeFile,
            "gwiazdka" to Favourites,
            "pedzel" to BrushTip,
            "serce" to Heart,
            "atom" to Atom,
            "dna" to Dna,
            "mapa" to MapPin,
            "zebatka" to Cog,
            "zarowka" to Bulb,
            "kompas" to Compass,
            "rakieta" to Rocket,
            "korona" to Crown,
            "filizanka" to Cup,
            "drzewo" to Tree,
            "gora" to Mountain,
            "cloud" to CloudMark,
            "klucz" to KeyShape,
            "zegar" to Clock,
            "kalendarz" to Calendar,
            "flaga" to Flag,
            "mikroskop" to Microscope,
            "pilka" to Ball,
            "maska" to Mask,
            "waga" to Scales,
            "tarcza" to Shield,
            "dom" to House,
            "aparat" to CameraBody,
            "zdjecie" to PhotoFrame,
            "rysunek" to DrawingPad,
            "wezel" to NodeDot,
            "tag" to Tag,
        )
    }

    fun folderIcon(id: String?): ImageVector =
        folderIcons.firstOrNull { it.first == id }?.second ?: Folder
}

// Elementy wspólne dla kilku ikon

private fun PathBuilder.sheet() {
    moveTo(5.5f, 3.5f); lineTo(18.5f, 3.5f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

private fun PathBuilder.dot(cx: Float, cy: Float) {
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
