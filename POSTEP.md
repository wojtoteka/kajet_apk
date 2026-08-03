# Kajet, dziennik pracy

Ten plik służy do wracania do pracy po przerwie. Trzyma stan projektu,
zapadłe decyzje i to, co zostało do zrobienia.

Ostatnia aktualizacja: 4 sierpnia 2026.

## Czym jest Kajet

Notatnik na tablet z Androidem, do nauki i do pracy. Pismo odręczne rysikiem,
notatki tekstowe w Markdown, mapy myśli, edytor kodu z uruchamianiem
oraz foldery na przedmioty. Główne urządzenie: Lenovo Yoga Tab Plus, poziomo.

Do tego serwer pod `kajet.wojtoteka.ovh`, który trzyma notatki w chmurze
i pozwala otworzyć je na komputerze. Kod serwera leży osobno,
w `D:\Inne\kajet_server`, i ma własny README.

Aplikacja działa w całości bez konta. Chmura jest dodatkiem, nie warunkiem.

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
| 10. Dopracowanie, dostępność, wydajność | Zrobione |
| 11. Poprawki po pierwszym użyciu na tablecie | Zrobione, wymaga sprawdzenia na tablecie |
| 12. Serwer, konta, chmura, udostępnianie | Zrobione, wymaga postawienia serwera |
| 13. Mapa myśli i zdjęcia w tekście | Zrobione, wymaga sprawdzenia na tablecie |
| 14. Izolacja uruchamiania kodu | Zrobione, wymaga zbudowania obrazu na serwerze |

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
Chaquopy działa na tablecie bez internetu, reszta języków przez serwer Kajetu
(`https://kajet.wojtoteka.ovh`). Adres jest wpisany na stałe w
`cloud/.../AccountStore.kt` (stała `SERVER_URL`) i nie da się go zmienić
z ustawień. Aplikacja pisze wprost przy każdym języku, czy potrzebny jest internet.

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
| `:code` | Edytor kodu, kolorowanie składni, uruchamianie |
| `:export` | PDF, DOCX, Markdown, PNG, wydruk, udostępnianie |
| `:cloud` | Konto, synchronizacja, kolejka wysyłki |
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

## Etap 11: poprawki po pierwszym użyciu na tablecie

Uwagi z użycia i to, co z nimi zrobiono. Data: 3 sierpnia 2026.

**Pisanie odręczne nie działało. Naprawione.**

To była jedna usterka o dwóch objawach. `PlotnoKresek` podawał silnikowi
`dokumentDoWidoku` jako `strokeToWorldTransform`, choć ta macierz ma opisywać
układ, w którym silnik ma zapisać kreskę, a nie sposób pokazania jej na ekranie.
Przez to kreska w trakcie pisania miała współrzędne przeskalowane dwa razy,
czyli wyglądała na dużo grubszą, niż była ustawiona, a po oderwaniu rysika
lądowała poza kartką i znikała. Teraz silnik dostaje przesunięcie o górną
krawędź strony i oddaje kreskę gotową do zapisania, bez przeliczania.

Przy okazji: kreskę da się pisać myszą, koniec pociągnięcia gumki odkłada
wpis w historii (wcześniej `koniecGumki` nie było skąd wywołać), a numer
strony kreski trzymamy w mapie po identyfikatorze, bo silnik oddaje kreskę
osobnym wywołaniem i przy szybkim piśmie jedno pole się nadpisywało.

**Reszta poprawek**

| Uwaga | Stan |
| --- | --- |
| Domyślnie gruby biały mazak | Pisak zapamiętuje się między notatkami, kolor i grubość ustawia się suwakiem |
| Kolorów nie da się wybierać | Wspólny wybór koloru HSV z kryciem, zapisem szesnastkowym i listą ostatnio używanych |
| Ubogie menu notatki odręcznej | Rodzaj pisaka, płynna grubość, krycie, podgląd kreski, pełne formatowanie pól tekstowych |
| Zapis bez animacji | Wspólny wskaźnik: kręcące się kółko przy zapisie, ptaszek i godzina po zapisie |
| Gwiazdka otwierała notatkę | Gwiazdka jest osobnym przyciskiem i przełącza ulubione bez otwierania |
| Ostatnio otwarte zawsze puste | Zapis notatki kasował datę otwarcia, bo indeks wstawiał wiersz od nowa. Teraz data zostaje |
| Mało ikon folderów | Trzydzieści osiem ikon w przewijanej siatce |
| Tytuł „Bez nazwy” nie do zmiany | Tytuł poprawia się wprost na pasku w każdym z trzech edytorów |

## Etap 12: chmura

Serwer leży w `D:\Inne\kajet_server` i ma własny README. Aplikacja rozmawia
z nim przez API, tak jak ustaliliśmy: strona i tablet to dwaj klienci
tego samego serwera, a nie dwa osobne programy.

**Serwer.** Next.js 15, TypeScript, MySQL przez Prismę, logowanie przez Auth.js.
Konta z hasłem i przez Google, rejestracja wyłącznie na kod od administratora,
potwierdzanie adresu i odzyskiwanie hasła mailem (SMTP na 587, STARTTLS).
Panel administratora: kody zaproszeń ręczne i jako gotowe odnośniki, limity
miejsca na stałe i na czas określony, zero jako brak limitu, blokowanie kont,
zmiana loginów, nadawanie uprawnień, dziennik czynności. Udostępnianie
odnośnikiem (działa bez konta) i imiennie na adres e-mail, do czytania albo
do poprawiania. Podgląd notatki rysowany jako SVG wprost z zapisanych punktów,
więc pismo jest ostre przy każdym powiększeniu. Własne uruchamianie kodu
zamiast `emkc.org/api/v2/piston`.

**Aplikacja.** Nowy moduł `:cloud`:

| Plik | Za co odpowiada |
| --- | --- |
| `CloudClient` | Rozmowa z API, błędy zamieniane na zdania po polsku |
| `AccountStore` | Token w zaszyfrowanym magazynie, stan zalogowania, `SERVER_URL` |
| `SendQueue` | Notatki czekające, gdy nie ma internetu |
| `Sync` | Wysyłka, pobieranie załączników, rozstrzyganie rozbieżności |
| `SyncWork` | Wysyłka w tle, warunek: jest sieć |
| `AccountScreen` | Logowanie, stan miejsca, ręczna synchronizacja |

Notatka wysyła się sama po każdym zapisie. Bez internetu czeka w kolejce
i idzie, gdy sieć wróci, także przy zamkniętej aplikacji.

**Decyzja o rozbieżnościach.** Gdy ta sama notatka zmieniła się na tablecie
i na serwerze, nie wybieramy za człowieka. Wersja z serwera zapisuje się obok,
jako osobna notatka z dopiskiem i datą. Nikt nie traci pracy, a decyzja,
którą zatrzymać, należy do właściciela.

**Kosz zostaje na tablecie.** Notatka skasowana na serwerze nie znika
z tabletu. Chmura jest kopią, a nie właścicielem; kasuje się tam, gdzie
notatka naprawdę leży.

## Etap 13: mapa myśli i zdjęcia w tekście

Dwie ostatnie uwagi z listy.

**Łączenie w mapie myśli.** Wcześniej szło to tak: zaznacz węzeł, naciśnij
przycisk, dotknij drugiego węzła. Trzy kroki i w trakcie nic nie było widać.
Teraz przy zaznaczonym węźle pojawia się uchwyt na prawej krawędzi. Ciągnie
się z niego linię palcem, tak jak rysowałoby się ją na kartce: linia idzie
za palcem, węzeł pod palcem sam się podświetla, a puszczenie w pustym miejscu
zakłada tam nowy węzeł i od razu go podczepia. Stary przycisk został jako
druga droga dla kogoś, komu wygodniej stukać.

**Panel węzła.** Był kształt i sześć kolorów. Jest krój pisma, wielkość
suwakiem, pogrubienie, kursywa, wyrównanie, własny kolor węzła i pisma
z pełnego wyboru HSV, kształt oraz lista połączeń, z której da się rozłączyć
sąsiada bez szukania linii na planszy.

**Zdjęcia w notatce tekstowej.** Okno pisania pokazywało surowy Markdown,
więc wstawione zdjęcie widać było jako napis `![zdjęcie](assets/zdjecie.png)`.
Teraz notatka dzieli się na bloki: zdjęcie jest zdjęciem, z podpisem,
przesuwaniem i kasowaniem, a między zdjęciami pisze się normalnie.

Pod spodem **nadal leży ten sam Markdown** i to on idzie do pliku. Bloki są
sposobem pokazania go, a nie nowym formatem zapisu, więc notatka otwiera się
w każdym innym programie tak jak dotąd. Widok surowego Markdown został pod
przyciskiem, bo przy tabelach i wzorach czasem trzeba zobaczyć sam zapis.

Pasek formatowania dostał listy numerowane, listy zadań (czyli listę zakupów),
tabele i odnośniki. Działa na bloku, w którym stoi kursor.

Reguły formatowania siedzą w `Format.kt`, osobno od ekranu, i mają testy:
otaczanie zaznaczenia, zdejmowanie znacznika przy powtórnym naciśnięciu,
znaczniki wiersza i kursor lądujący w środku bloku kodu. Razem 39 testów
w module edytora.

## Etap 14: izolacja uruchamiania kodu

Pierwsza wersja uruchamiała cudzy kod wprost na maszynie, jako ten sam
użytkownik, na którym chodzi serwer. Miała limit czasu i odcięte zmienne
środowiskowe, i to wystarczało do pracy na własnym komputerze, ale nie
do wystawienia pod adresem, na którym konto może założyć ktoś obcy.

Co było naprawdę do wzięcia: hasło do bazy i klucze z pliku `.env`, notatki
i zdjęcia wszystkich kont z katalogu `dane/`, dostęp do MySQL na localhoście,
możliwość wysłania tego wszystkiego w świat, dopisania się do crona i puszczenia
procesu w tle, który przeżywa limit czasu, bo `SIGKILL` szedł tylko
do bezpośredniego dziecka.

Teraz każdy program dostaje własny kontener Dockera: bez sieci, z systemem
plików tylko do odczytu, ze zdjętymi uprawnieniami, jako użytkownik nikt,
z limitem pamięci, procesora i liczby procesów. Po limicie czasu zabijamy
**kontener**, a nie klienta Dockera, więc nic uruchomionego w tle nie zostaje.
Program widzi wyłącznie swój własny plik, podpięty tylko do odczytu.

Bez Dockera serwer odmawia i mówi dlaczego. `KOD_WLACZONE` jest domyślnie
wyłączone: lepiej, żeby ta jedna rzecz nie działała, niż żeby działała
bez izolacji.

Do tego limit dwunastu uruchomień na minutę na konto i przełącznik
w panelu administratora, którym da się odebrać uruchamianie jednej osobie.
Pisanie i zapisywanie kodu działa dalej.

Opis wszystkich zapór, razem z tym, przed czym każda broni, jest w README
serwera. Obraz jest w `docker/Dockerfile`: Python, JavaScript, TypeScript,
powłoka, C, C++, PHP, Ruby i SQL. Javy, Kotlina, Go, Rusta i C# tam nie ma,
bo każdy dokłada od kilkuset megabajtów do kilku gigabajtów.

## Etap 15: poprawki po pierwszym użyciu na telefonie

Zgłoszenia z 4 sierpnia 2026 i to, co z nimi zrobiono. Wersja podniesiona
do 1.0 (versionCode 2).

**Wydanie release było zepsute u korzenia.** `proguard-rules.pro` był pustym
szablonem, a release budował się z pełnym odchudzaniem R8, które zmienia nazwy
klas odnajdywanych po nazwie przez kotlinx.serialization i natywny silnik
kreski. Stąd „debug działa, zainstalowana aplikacja nie". Odchudzanie jest
wyłączone; kilkanaście megabajtów więcej to cena za działającą całość.

**Udostępnianie zamykało aplikację.** Eksport zapisywał pliki do
`cache/export`, a FileProvider znał tylko `eksport/` — jedna litera różnicy
i `getUriForFile` rzucał wyjątkiem prosto z przycisku. Katalog nazywa się
teraz tak jak w `sciezki_plikow.xml`, a „Wyślij", „Otwórz" i „Drukuj" oddają
zdanie po polsku zamiast wyjątku. Drukowanie dostaje kontekst ekranu,
bo systemowy druk odmawia pracy bez Activity.

**Pisanie ręczne na telefonie.** Dwie przyczyny „pustej kartki": palec
domyślnie tylko przewijał (na telefonie bez rysika nie dało się postawić
kreski), a edytor odręczny jako jedyny nie miał układu na wąski ekran —
kolory ginęły za tytułem, a powrót był w bocznym pasku ikon. Teraz domyślne
zachowanie palca zależy od sprzętu (bez rysika w systemie palec rysuje),
a poniżej 600 dp edytor składa się w pion: powrót i kolory na górze,
narzędzia w przewijanym pasku na dole, panele pisaka i ustawień wjeżdżają
od dołu na całą szerokość. Silnik kreski został ten sam — architektura
była zdrowa, wymieniony jest układ, nie fundament.

**Synchronizacja jak na dysku w chmurze.** Wysyłka szła wyłącznie z kolejki,
a do kolejki trafiały tylko notatki zapisane po zalogowaniu — biblioteka
sprzed zalogowania nigdy nie jechała na serwer, stąd „synchronizuję i nic
się nie dzieje". Po pobraniu zmian synchronizacja uzgadnia teraz całą
bibliotekę: notatki, o których serwer nic nie wie, dopisują się do kolejki
i jadą od razu. Do tego magazyn konta sam leczy rozjazd między plikiem
szyfrowanym a zapasowym (to od niego logowanie „raz było, raz znikało"),
a ekran konta przy każdym wejściu sprawdza token i stan miejsca na serwerze.

**Notatka tekstowa.** Okno rysowania miało sztywne 640 dp i pasek bez
przewijania — na telefonie zostawało „puste pole", a w ciemnym motywie
z czterech kolorów widać było tylko biały. Teraz okno mieści się w ekranie,
pasek się przewija, paleta jest pełna, a kropki mają obwódki zależne od
jasności. Zdjęcia: kolizja nazw załączników dawała „zdjecie (2).jpg" ze
spacją, której nie czytał wzorzec bloku obrazka — użytkownik widział surowy
Markdown. Nazwy idą teraz z zegara (bez kolizji), wzorce w blokach
i podglądzie znoszą spacje i nawiasy ze starych notatek, a zdjęcie wstawia
się za blokiem z kursorem, nie na końcu notatki.

**Zapis od razu.** Zamiast odstępu 3/5/10/20 s zapis rusza po każdej zmianie:
krótkie sklejanie 400 ms łączy serię szybkich zmian w jeden zapis, a przy
pisaniu bez przerwy treść i tak ląduje na dysku co najwyżej co 2 s. Raz
zaczęty zapis nie daje się przerwać w połowie pliku. Wybór odstępu zniknął
z ustawień.

**Mapa myśli dorównała stronie.** Uchwyt zmiany rozmiaru węzła, „Zmieść całą
mapę w oknie" liczone z obwiedni, dodawanie węzła obok (rodzeństwo), pełne
dziedziczenie stylu rodzica, podwójne dotknięcie tła zakłada węzeł w tym
miejscu, przyciski zoomu z odczytem procentu, licznik ukrytych węzłów na
zwiniętej gałęzi, linie w kolorze gałęzi, pole „Tekst węzła" w panelu
(limit 500 znaków jak na stronie), panel węzła na telefonie jako dolny
pasek na całą szerokość, stopka z liczbą węzłów i połączeń. Przewagi
aplikacji (podpisy rysikiem, łączenie przeciąganiem, rozłączanie z paska
linii) zostały nietknięte.

**Biblioteka.** „Wszystkie notatki" idą od najnowszej do najstarszej
(foldery zostają alfabetycznie na górze), daty bierze się ze spisu, nie
z SAF. Dialogi mieszczą się na wąskim ekranie, nagłówek kosza ma wariant
pionowy.

## Co zostało

- Edytor w przeglądarce. Podgląd jest, zmian jeszcze nie da się zapisać,
  choć udostępnienie „do poprawiania" już się zapisuje.
- Edycja na żywo. Tabela `ZmianaNaZywo` czeka, WebSocket jeszcze nie ma.
- Wstawianie zdjęć na stronę notatki odręcznej. Format to przewiduje
  (`ImageElement`), brakuje przycisku.
- Przycisk uruchomienia przy bloku kodu w notatce tekstowej.

## Co sprawdzić po etapach 11-14

Lista wyżej dotyczy rzeczy sprawdzanych od początku. To jest to, co doszło
i czego nikt jeszcze nie widział w działaniu.

**Pismo odręczne. Od tego zacznij, reszta może poczekać.**

1. Otwórz notatkę odręczną i napisz coś rysikiem. Kreska ma zostać na kartce
   po oderwaniu rysika. To jest cała ta usterka.
2. Kreska w trakcie pisania ma mieć tę samą grubość, co po zapisaniu.
   Jeśli grubieje albo chudnie w chwili puszczenia, transformacja nadal
   jest zła.
3. Napisz przy dolnej krawędzi. Ma dołożyć się nowa strona i kreska ma
   trafić na właściwą.
4. Powiększ dwoma palcami i napisz. Kreska ma trafić pod rysik, nie obok.
5. Napisz szybko kilka kresek jedna po drugiej. Żadna nie ma wylądować
   na innej stronie.
6. Pomaż gumką i cofnij. Ma wrócić całe pociągnięcie, a nie jeden punkt.

**Pisak i kolory**

7. Zmień rodzaj pisaka na cienkopis, ołówek i linię przerywaną.
8. Ustaw grubość i krycie suwakiem, sprawdź podgląd kreski w panelu.
9. Dobierz własny kolor kołem HSV, wpisz kod szesnastkowy.
10. Wyjdź z notatki i otwórz inną. Pisak ma być ten sam, który ustawiłeś.

**Reszta aplikacji**

11. Gwiazdka na liście notatek ma dodawać do ulubionych, a nie otwierać.
12. Otwórz kilka notatek i zajrzyj do „Ostatnio otwarte”.
13. Po pierwszym uruchomieniu nowej wersji spis odbudowuje się sam.
    Ma się pojawić pasek „Odświeżam spis notatek”, a potem ulubione
    i ostatnio otwarte mają wrócić.
14. Mapa myśli: zaznacz węzeł, pociągnij za uchwyt na prawej krawędzi.
    Linia ma iść za palcem, cel ma się podświetlać, puszczenie w pustym
    miejscu ma założyć nowy węzeł.
15. Mapa myśli: zmień krój, wielkość, kolor węzła i pisma.
16. Notatka tekstowa: wstaw zdjęcie. Ma być widoczne jako zdjęcie,
    nie jako `![zdjęcie](assets/...)`.
17. Zdjęciu zmień podpis, przesuń je w górę i skasuj.
18. Przełącz widok na Markdown i z powrotem. Treść ma się zgadzać.
19. Pasek formatowania: lista zadań, tabela, odnośnik.
20. Otwórz notatkę z długim tekstem i sprawdź, czy kursor nie skacze
    na początek przy pisaniu.

**Chmura, dopiero po postawieniu serwera**

21. Ustawienia, „Konto w chmurze”, zaloguj się adresem i hasłem.
22. Napisz notatkę i sprawdź, czy pojawiła się w `/biblioteka` w przeglądarce.
23. Wyłącz internet, napisz drugą notatkę, włącz internet.
    Ma dojść sama, bez otwierania ekranu konta.
24. Zmień tę samą notatkę na tablecie i na serwerze. Ma powstać druga
    notatka z dopiskiem „wersja z serwera”, żadna nie ma zniknąć.
25. Wstaw zdjęcie i sprawdź, czy widać je w przeglądarce.
26. Udostępnij notatkę odnośnikiem, otwórz go w oknie prywatnym.
27. Zaloguj się drugim kontem i sprawdź, że nie widzi cudzych notatek.

## Następne kroki

1. Sprawdzić pisanie odręczne na tablecie. To jest najważniejsze i tego
   nie da się sprawdzić bez urządzenia.
2. Postawić serwer: baza, `.env`, `npm run admin`, nginx. Uwaga: katalog
   `D:` jest w systemie exFAT, który nie zna dowiązań, więc Next.js nie zbuduje
   się w miejscu. Projekt trzeba przenieść na NTFS albo od razu na serwer.
3. Sprawdzić synchronizację na parze tablet plus przeglądarka.
4. Edytor notatki w przeglądarce, a po nim edycja na żywo.
5. Dołożyć wstawianie zdjęć na stronę notatki odręcznej.
6. Przycisk uruchomienia przy bloku kodu w notatce tekstowej.
