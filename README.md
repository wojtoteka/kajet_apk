# Kajet - notatnik na Androida

Notatnik na tablet z rysikiem: pismo odręczne, notatki tekstowe w Markdownie, mapy myśli,
notatki z kodem (z Pythonem uruchamianym na urządzeniu), eksport do PDF/DOCX i synchronizacja
z własną chmurą. Kotlin + Jetpack Compose, osiem modułów Gradle.

Serwer, z którym się synchronizuje, leży w repo
[`kajet_server`](https://github.com/Wojtoteka/kajet_server).

---

## Co to potrafi

**Pismo odręczne.** Kreska stawiana przez `androidx.ink`, z rozpoznawaniem figur (prostuje
koło, prostokąt, strzałkę), gumką, zaznaczaniem, ramkami na zdjęcia i polami tekstowymi
na kartce. Do tego haptyka pióra i formaty stron - notatka pamięta swój rozmiar kartki.

**Notatki tekstowe.** Pisze się jak w Wordzie: znaczników (`**`, `#`, `<span>`, płotów
```` ``` ````) nie widać nigdy, także pod kursorem, a Backspace i Enter nie potrafią ich
rozbić - każda zmiana z klawiatury przelicza się na to, co widać (`TextLayout`, `TextEdit`).
Przyciski paska nie doklejają znaczników, tylko zmieniają model notatki - akapity z budową
i znaki z formatami (`TextDocument`, `TextCommands`) - a zapis składa się z niego od nowa.
Formaty znaku, łącznie z nagłówkami H1-H3, działają na zaznaczenie, bez zaznaczenia na słowo
pod kursorem, a za tekstem czekają na pisanie; nagłówek kawałka zdania to
`<span class="h1">…</span>`, a cały wiersz w jednym poziomie zapisuje się jako `# Tytuł`.
Punkt, numer, zadanie i cytat dostają wszystkie zaznaczone akapity, numeracja liczy się
sama, jest Cofnij/Ponów oraz Ctrl+B/I/U/Z/Y z klawiatury.
Ułożenie (lewo, środek, prawo) ma każdy akapit osobno: `<p style="text-align:center">…</p>`
wokół wiersza, ten sam zapis po stronie serwera. Blok kodu i wzór to osobne pola z czcionką
maszynową, bez widocznych płotów. Pod spodem nadal Markdown z tabelami, listami, obrazkami
ze skalowaniem szerokości (`![alt|60%]`) i rozmiarem czcionki. Rozmiar `0` znaczy „domyślny z motywu", więc notatka
nie zamarza na wartości sprzed zmiany motywu. Zdjęcia i rysunki stojące w jednym wierszu
pliku stoją obok siebie także w notatce, a ułożenie wiersza (lewo, środek, prawo) siedzi
w tytule zdjęcia: `![a|25%](assets/a.png "srodek") ![b|25%](assets/b.png "srodek")`.
Zdjęcie wybiera się stuknięciem - dopiero wtedy ma obwódkę, uchwyt rozmiaru w rogu i pasek
działań, a palcem albo rysikiem przesuwa się je w wierszu i po notatce. Nowe zdjęcie i nowy
rysunek wchodzą obok wybranego, w ten sam wiersz i w tej samej szerokości.

**Mapy myśli.** Węzły owalne i prostokątne, zawijanie długich haseł, płótno biorące rozmiar
z kontenera zamiast sztywnych wymiarów.

**Notatki z kodem.** Edytor z podświetlaniem składni i podpowiedziami, konsola HTML z podglądem
w WebView oraz uruchamianie: Python bezpośrednio na tablecie (Chaquopy, `arm64-v8a`) albo
zdalnie na serwerze Kajetu w kontenerze.

**Przeglądarka plików.** Obrazy, PDF-y i nieznane binaria otwierają się w podglądzie tylko do
odczytu - nigdy nie lądują w edytorze kodu jako UTF-8.

**Udostępnianie w obie strony.** Apka przyjmuje przez Android Share i „Otwórz w" dokładnie
to, co potrafi pokazać - tekst i kod, zdjęcia jpg / png / gif / webp oraz PDF - kopiując pliki
najpierw do katalogu notatek, a notatkę wypuszcza linkiem z serwera.

**Eksport.** PDF (z formatowaniem tekstu, zdjęciami i rysunkiem), DOCX, Markdown.

**Chmura.** Konto, logowanie urządzenia własnym tokenem, synchronizacja notatek i folderów,
ikona chmurki dopiero po faktycznym zapisie na serwerze, kolejka ponawiania usunięć i osłona
przed nadpisaniem notatki skasowanej zdalnie.

**Edycja na żywo.** Notatka otwarta na kilku urządzeniach albo u kilku osób (także na
stronie) zmienia się u wszystkich od razu - tekst, pismo odręczne i mapa myśli. Idą tylko
małe delty przez strumień zmian (SSE); gdy nikt inny notatki nie ma otwartej, przez sieć
nie idzie nic poza krótkim „ping". Praca bez sieci scala się po powrocie z cudzymi
zmianami (`core/live/LiveMerge.kt` - te same zasady i te same przypadki testowe co na
serwerze): tekst do poziomu słów, przy prawdziwym konflikcie obie wersje jedna pod drugą,
kreski zawsze się sumują, ten sam węzeł mapy - wygrywa późniejsza zmiana.

**Udostępnione.** Odnośnik `https://kajet.wojtoteka.ovh/n/...` otwiera notatkę albo folder
od razu w aplikacji (App Links). Zwykły link działa na czas oglądania i niczego nie zostawia
w bibliotece; zaproszenie na adres e-mail po otwarciu staje w „Udostępnione mi". W folderze
z prawem edycji można dodawać, zmieniać i usuwać notatki i podfoldery.

**KajetAI.** Panel asystenta wpięty w notatkę - z ekranem zgody, limitami i komunikatami
o błędach po polsku i angielsku.

**Awarie.** Własny `ErrorBoundary` i handler wyjątków: zamiast zniknięcia apki użytkownik
dostaje ekran błędu, a log może pójść na serwer.

**Kopie zapasowe.** Notatki leżą w katalogu wybranym przez użytkownika (SAF), z ponownym
pytaniem o folder, gdy Auto Backup przywróci URI bez trwałego uprawnienia.

---

## Architektura

Osiem modułów Gradle, każdy z własną odpowiedzialnością:

| Moduł | Za co odpowiada |
|---|---|
| `app` | nawigacja, biblioteka notatek, ustawienia, import z Share, ekran awarii |
| `core` | motyw, komponenty wspólne, słownik i18n, panel AI, obsługa błędów |
| `storage` | zapis na dysku przez SAF, repozytorium biblioteki, kodek notatki, porządki |
| `ink` | kreska, pędzle, figury, gumka, haptyka rysika |
| `editor` | edytory: odręczny, mapy myśli, tekstowy; historia zmian, wskaźnik zapisu |
| `code` | edytor kodu, podświetlanie, podpowiedzi, uruchamianie, konsola HTML |
| `export` | PDF, DOCX, Markdown |
| `cloud` | konto, transport HTTP, synchronizacja, AI, zgłoszenia awarii |

Treść notatki to jeden dokument JSON (`content.json`), identyczny z tym, który trzyma serwer.
Synchronizacja porównuje dokumenty, nie modele - to samo założenie po obu stronach.
Do scalania po pracy bez sieci aplikacja trzyma ostatnią wersję uzgodnioną z serwerem
(`files/sync-base`, skompresowaną).

## Stos

Kotlin · Jetpack Compose · Material 3 · kotlinx.serialization · androidx.ink ·
DataStore · SAF · Chaquopy (Python 3.11) · WebView

- `minSdk` 26, `targetSdk` 36, budowane pod `arm64-v8a`
- R8 świadomie wyłączone w release: obfuskacja psuła `kotlinx.serialization` i natywny
  silnik kreski, które odnajdują klasy po nazwie

## Budowanie

```bash
./gradlew assembleDebug      # APK debug
./gradlew test               # testy jednostkowe (ink, export, code, storage, app)
./gradlew assembleRelease
```

`local.properties` (ścieżka do SDK) jest lokalne i nie trafia do repozytorium.

Dwa testy edycji na żywo chodzą tylko przeciw prawdziwemu serwerowi i bez zmiennych
środowiska są pomijane:

```bash
KAJET_LIVE_SERVER=http://localhost:9081 KAJET_LIVE_TOKEN=<token aplikacji> \
    ./gradlew :cloud:testDebugUnitTest --tests '*LiveSessionServerTest*'
```

`LiveWebInteropTest` dodatkowo potrzebuje `KAJET_INTEROP_DIR` i przeglądarki po drugiej
stronie (Playwright), z którą wymienia się plikami w tym katalogu.

## Uwagi

Adres chmury domyślnie wskazuje na `kajet.wojtoteka.ovh`. Żeby wpiąć własny serwer, postaw
[`kajet_server`](https://github.com/Wojtoteka/kajet_server) i zmień adres w `cloud/AccountStore.kt`.

## Licencja

Kod udostępniony do wglądu w celach portfolio.
