# Kajet, dziennik pracy

Ten plik służy do wracania do pracy po przerwie. Trzyma stan projektu,
zapadłe decyzje i to, co zostało do zrobienia.

Ostatnia aktualizacja: 2 sierpnia 2026.

## Czym jest Kajet

Notatnik na tablet z Androidem, do nauki i do pracy. Pismo odręczne rysikiem,
notatki tekstowe w Markdown, mapy myśli, edytor kodu z uruchamianiem
oraz foldery na przedmioty. Główne urządzenie: Lenovo Yoga Tab Plus, poziomo.

## Stan na dziś

| Etap | Stan |
| --- | --- |
| 1. Plan, nazwa, paleta | Zrobione |
| 2. Szkielet, moduły, motyw, nawigacja | Zrobione |
| 3. Warstwa plików, format .note, kosz, testy | Zrobione |
| 4. Rysowanie rysikiem, narzędzia, cofanie | Zrobione, wymaga sprawdzenia na tablecie |
| 5. Notatki tekstowe, zdjęcia, rysunki, wzory | Zrobione, wymaga sprawdzenia na tablecie |
| 6. Mapy myśli | Zrobione, wymaga sprawdzenia na tablecie |
| 7. Edytor kodu, Python offline, serwer | Zrobione, wymaga sprawdzenia na tablecie |
| 8. Eksport, drukowanie, udostępnianie | Zrobione, wymaga sprawdzenia na tablecie |
| 9. Rozpoznawanie pisma i wyszukiwanie | Zrobione, wymaga sprawdzenia na tablecie |
| 10. Dopracowanie, dostępność, wydajność | W toku |

Projekt się buduje, testy jednostkowe przechodzą. Cała ścieżka od wskazania
katalogu, przez utworzenie folderu i notatki, po pisanie, zapis i eksport
jest przejezdna w kodzie. Rzeczy, których nie da się sprawdzić bez urządzenia,
są wypisane niżej.

## Decyzje i ich powody

**Nazwa Kajet.** Dawne polskie słowo na szkolny zeszyt. Krótkie, wymawialne
po polsku i po angielsku, nikt go dziś nie zajmuje.

**Znak rozpoznawczy: margines.** Pionowa linia w stałej odległości od lewej
krawędzi, przeniesiona ze szkolnego zeszytu. Na każdym ekranie niesie co innego:
w bibliotece kolory folderów, w notatce prawdziwy margines kartki,
w edytorze kodu rynnę z numerami linii.

**Paleta.** Motyw jasny: biurko `#E7E2D6`, kartka `#F4F1EA`, tekst `#23211D`,
tekst drugorzędny `#67635A`, akcent `#0F6B5C`, linie `#D3CCBC`.
Motyw ciemny: biurko `#171614`, kartka `#24231F`, tekst `#E8E4DA`,
tekst drugorzędny `#9A948A`, akcent `#4FB39C`, linie `#35332C`.
Poza paletą jeden kolor ostrzegawczy: `#A6392E` w jasnym, `#E2857A` w ciemnym.
W planie tekst drugorzędny miał być `#6E6A61`, ale na tle biurka dawał kontrast
4,17:1, więc został przyciemniony do `#67635A` i teraz ma 4,63:1.

**Kroje pisma.** Archivo w nagłówkach, IBM Plex Sans w treści,
IBM Plex Mono w kodzie. Pliki leżą w repozytorium, nic nie pobiera się z sieci.

**Silnik kreski: androidx.ink.** Kreska pisana teraz idzie przez
`InProgressStrokesView`, który na Androidzie 10 i nowszym rysuje po froncie
bufora i sam przewiduje ruch rysika. Kreski już zapisane rysujemy sami
w warstwie pod spodem, więc pisanie nie przerysowuje całej strony.

**Notatka to katalog, nie plik ZIP.** Zapis automatyczny co kilka sekund
przy pliku ZIP oznaczałby przepisywanie całego archiwum i ryzyko uszkodzenia,
gdyby tablet zasnął w środku zapisu.

**Zapis podwójny.** Przy każdym zapisie ta sama treść idzie najpierw do
`content.bak.json`, potem do `content.json`. Przerwany zapis nie kasuje notatki.

**Uruchamianie kodu.** Interfejs `CodeRunner` z dwoma sposobami: Python przez
Chaquopy działa na tablecie bez internetu, reszta języków przez serwer Piston
z adresem w ustawieniach. Aplikacja pisze wprost przy każdym języku,
czy potrzebny jest internet.

**Podgląd notatki tekstowej w przeglądarce wbudowanej.** To jedyny sposób,
żeby wzory w LaTeX wyglądały poprawnie bez internetu. KaTeX leży w plikach
aplikacji, a zdjęcia z notatki idą przez przechwycone zapytanie,
więc przeglądarka nie dostaje dostępu do dysku.

**DOCX budowany od zera.** Nie ma biblioteki OOXML, która sensownie działa
na Androidzie. Apache POI ciągnie kilkanaście megabajtów i przekracza limit
metod. Ograniczenia własnego generatora są opisane w kodzie i w oknie eksportu.

**Wersje bibliotek.** AndroidX jest przypięty do wydań sprzed wymogu
`compileSdk 37`. Najnowsze wydania żądają wtyczki Android Gradle w wersji 9.1,
a projekt stoi na 8.13.2. Podnoszenie tego teraz oznaczałoby przepisywanie
plików budowania w środku pracy.

## Moduły

| Moduł | Za co odpowiada |
| --- | --- |
| `:core` | Paleta, typografia, ikony rysowane w kodzie, model dokumentu |
| `:storage` | Katalog biblioteki, format `.note`, kosz, spis do wyszukiwania, ustawienia |
| `:ink` | Silnik kreski, narzędzia, gumka, lasso, linijka, rozpoznawanie pisma |
| `:editor` | Edytory: odręczny, tekstowy, mapa myśli, historia zmian, autozapis |
| `:code` | Edytor kodu, kolorowanie składni, uruchamianie przez serwer |
| `:export` | PDF, DOCX, Markdown, PNG, wydruk, udostępnianie |
| `:app` | Nawigacja, biblioteka, ustawienia, złożenie całości, Python na tablecie |

## Co sprawdzić na tablecie

Tych rzeczy nie da się sprawdzić bez urządzenia z rysikiem.

**Kreska i rysik**

1. Czy kreska nadąża za rysikiem przy szybkim pisaniu. Porównaj z aplikacją,
   której używasz na co dzień.
2. Czy nacisk zmienia grubość kreski w sposób, który wygląda naturalnie.
3. Czy pochylenie rysika jest widoczne w kresce.
4. Oprzyj dłoń na ekranie i pisz. Dłoń nie powinna zostawiać śladu.
5. Oderwij rysik i od razu dotknij palcem. Przez pół sekundy palec ma nie rysować.
6. Przełącz w ustawieniach na "palec też rysuje" i sprawdź, czy działa.
7. Napisz stronę pełną tekstu, potem przewijaj i skaluj. Sprawdź, czy nie zacina.
8. Gumka: przejedź przez środek litery i sprawdź, czy zostają dwa kawałki.
9. Lasso: obrysuj słowo, przeciągnij je i cofnij. Cofnięcie ma wrócić do miejsca.
10. Linijka: narysuj kreskę prawie poziomą, powinna dociągnąć się do równej.

**Strony i zapis**

11. Napisz coś przy dolnej krawędzi ostatniej strony. Ma dołożyć się kolejna.
12. Wyjdź z notatki i wróć. Treść ma być na miejscu.
13. Wyłącz tablet w trakcie pisania i włącz ponownie. Notatka ma się otworzyć.
14. Odinstaluj aplikację i zainstaluj ponownie. Wskaż ten sam katalog.
    Cała biblioteka ma wrócić.

**Katalog i kosz**

15. Sprawdź, czy katalog `.trash` jest widoczny dla aplikacji po ponownym
    uruchomieniu. Niektóre dostawcy plików chowają katalogi z kropką.
16. Skopiuj notatkę z komputera do katalogu biblioteki i odbuduj spis
    w ustawieniach. Notatka ma pojawić się na liście.

**Notatki tekstowe**

17. Wpisz wzór `$$\int_0^1 x^2 dx$$` i sprawdź podgląd bez internetu.
18. Wstaw zdjęcie z aparatu i z galerii.
19. Wstaw mały rysunek i sprawdź, czy pojawia się w podglądzie.
20. Odhacz zadanie w podglądzie i sprawdź, czy zmienił się tekst po lewej.

**Kod**

21. Uruchom program w Pythonie z funkcją `input`. Wpisz dane w zakładce Wejście.
22. Wyłącz internet i uruchom Pythona. Ma zadziałać.
23. Wyłącz internet i uruchom C++. Ma pojawić się spokojny komunikat,
    a kod ma zostać zapisany.
24. Włącz internet i uruchom C++, Javę oraz Kotlin.

**Eksport i wydruk**

25. Zapisz notatkę odręczną do PDF i sprawdź, czy pole tekstowe da się
    zaznaczyć w czytniku, czyli czy jest tekstem, a nie obrazkiem.
26. Zapisz notatkę tekstową do DOCX i otwórz w edytorze tekstu.
27. Wydrukuj notatkę na drukarce albo do pliku PDF przez systemowy wydruk.
28. Zapisz cały folder i sprawdź układ katalogów w pliku ZIP.

**Rozpoznawanie pisma**

29. Napisz zdanie po polsku, zaznacz lassem i wybierz zamianę na tekst.
    Model pobierze się raz, potrzebny jest wtedy internet.
30. Wyłącz internet i powtórz. Ma działać.
31. Wyszukaj słowo z rozpoznanego pisma w wyszukiwarce.

**Dostępność i wygląd**

32. Włącz czytnik ekranu i przejdź po pasku narzędzi. Każda ikona ma mieć opis.
33. Wyłącz animacje w ustawieniach systemu i sprawdź, czy nic nie miga.
34. Przełącz motyw na ciemny i sprawdź czytelność na kartce.
35. Obróć tablet do pionu i sprawdź, czy biblioteka nadal działa.

## Co nie jest zrobione

- Blok kodu w notatce tekstowej nie ma jeszcze przycisku uruchomienia.
  Kod się koloruje i zapisuje, ale uruchomienie działa tylko w osobnym pliku.
- Eksport notatki tekstowej do PDF składa tekst prosto, bez wzorów w LaTeX
  i bez zdjęć. Do notatek ze wzorami lepszy jest wydruk przez podgląd.
- Zdjęcia wstawione na stronę notatki odręcznej da się zapisać w formacie,
  ale nie ma jeszcze przycisku, który by je tam wstawiał. Zdjęcia działają
  w notatkach tekstowych.
- Kopiowanie i wklejanie zaznaczenia w notatce odręcznej. Jest przesuwanie
  i kasowanie, nie ma kopiowania.
- Podgląd wyniku rysunku wstawionego w tekst nie pozwala go jeszcze poprawić.
  Kreski są zapisane obok obrazka, więc dane na to są.
- Tagi są zapisywane w pliku i wchodzą do spisu, ale nie ma jeszcze ekranu
  do ich przeglądania.
- Ustawienie `modelPismaPobrany` jest zapisywane w ustawieniach, ale nic go
  jeszcze nie czyta. Rozpoznawanie samo sprawdza obecność modelu przy każdym
  użyciu, więc pole jest na razie zbędne. Do usunięcia albo do wykorzystania
  przy ekranie ustawień z wcześniejszym pobraniem modelu.
- Rysowanie kresek już zapisanych nie ma jeszcze zapamiętanego obrazu strony.
  Przy bardzo gęstej stronie przewijanie może zwalniać. To jest do zmierzenia
  na tablecie, punkt 7 listy wyżej.

## Następne kroki

1. Sprawdzić listę powyżej na tablecie i zapisać wyniki w tym pliku.
2. Dołożyć przycisk uruchomienia do bloku kodu w notatce tekstowej.
3. Dołożyć wstawianie zdjęć na stronę notatki odręcznej.
4. Zmierzyć opóźnienie kreski i, jeśli trzeba, dołożyć zapamiętany obraz strony.
5. Przejrzeć wszystkie napisy pod kątem zrozumiałości dla kogoś,
   kto nie zna się na komputerach.
