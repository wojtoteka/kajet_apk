# Kajet — poradnik dla programisty

Wszystko, co trzeba wiedzieć, siadając do Kajetu po przerwie: gdzie co leży,
jak to uruchomić, co jest z gitem i co jest do zrobienia.

Stan na 3 sierpnia 2026.

---

## 1. Gdzie co leży

| Co | Ścieżka | Czym jest |
| --- | --- | --- |
| **Aplikacja na tablet** | `C:\Users\Wojte\AndroidStudioProjects\Notatnik` | Android, Kotlin, Jetpack Compose |
| **Serwer i strona** | `D:\Inne\kajet_server` | Next.js 15, React 19, Prisma, PostgreSQL |
| **Serwer na żywo** | `https://kajet.wojtoteka.ovh` | adres wpisany w aplikację na stałe |
| **Serwer lokalnie** | `http://localhost:9081` | port ustawiony w `package.json` |

Adres serwera siedzi w jednym miejscu w aplikacji:
`cloud/src/main/java/wojtoteka/ovh/kajet/cloud/AccountStore.kt`, stała `SERVER_URL`.
Nie da się go zmienić z ustawień i tak ma zostać.

### Katalog serwera od środka

```
D:\Inne\kajet_server\
  src/app/api/v1/          API dla tabletu: signin, account, notes, code
  src/app/                 strony: library, note/[id], account, admin, n/[token]
  src/lib/                 document.ts, api.ts, auth.ts, quota.ts, files.ts, ...
  src/components/          NotePreview.tsx i reszta
  prisma/                  schemat bazy
  scripts/                 prisma.mjs, database.mjs, grant-admin.ts
  docker/Dockerfile
  apka/                    KOPIE dokumentów z aplikacji, patrz punkt 5
  donaprawy.md             lista rzeczy do naprawy na serwerze
  .env                     sekrety, nigdy do gita
```

---

## 2. Git — najpierw to

Tu jest najpilniejsza rzecz w całym pliku.

### Serwer nie jest repozytorium

`D:\Inne\kajet_server` **nie ma katalogu `.git`**. Cały serwer — API, baza,
strony, konfiguracja — nie ma historii. Jedno nieostrożne zapisanie pliku
i nie ma jak wrócić.

Dobra wiadomość: `.gitignore` **już tam leży i jest poprawny** — pomija `.env`,
`node_modules/`, `.next/`, `dane/` i `*.tsbuildinfo`. Ktoś go napisał
w oczekiwaniu na repozytorium, którego nigdy nie założono.

```bash
cd /d/Inne/kajet_server
git init
git add -A
git status          # sprawdź, czy .env i node_modules NIE są na liście
git commit -m "Punkt wyjscia: serwer Kajetu"
```

Sprawdź `git status` przed commitem mimo wszystko. Jeden sekret wpuszczony do
historii zostaje w niej na zawsze.

### Aplikacja: repozytorium jest, ale nigdzie nie leci

```
gałąź:   master
commity: 9 (od "Punkt wyjscia" do "Etap 10")
remote:  BRAK
```

Wszystko stoi tylko na tym dysku. Warto dołożyć zdalne repozytorium
(choćby prywatne) albo przynajmniej kopię na drugim nośniku.

### Niezacommitowana zmiana

W drzewie roboczym siedzi duża, nieopisana zmiana: **przemianowanie wszystkich
plików i klas z polskich nazw na angielskie** (`EkranNotatki.kt` → `NoteScreen.kt`,
`ModelBiblioteki.kt` → `LibraryViewModel.kt` i tak dalej), a na wierzchu
poprawki z 3 sierpnia. `git status` pokazuje ponad sto plików.

Przy okazji z drzewa roboczego **zniknęły `FORMAT.md`, `POSTEP.md` i `README.md`**.
To nie jest to samo co skasowanie — w `HEAD` dalej są, tylko w starej wersji.
Patrz punkt 5.

Zanim ruszysz cokolwiek nowego, zamknij tę zmianę commitem. Inaczej każda
następna poprawka mieszka w tym samym worku, co refaktor na dwieście plików.

---

## 3. Jak to uruchomić

### Aplikacja

Potrzebne: **JDK 17**, **Android SDK 36**, **Python 3** na ścieżce systemowej
(wtyczka Chaquopy wbudowuje tłumacza Pythona w APK).

```bash
./gradlew :app:assembleDebug          # APK w app/build/outputs/apk/debug/
./gradlew test                        # wszystkie testy jednostkowe
./gradlew :editor:testDebugUnitTest   # testy jednego modułu
```

Aplikacja jest budowana **tylko na arm64** — każda kolejna architektura dokłada
kilkadziesiąt megabajtów przez natywny kod Pythona.

Konfiguracja: `namespace` i `applicationId` to `wojtoteka.ovh.kajet`,
`minSdk 26`, `compileSdk`/`targetSdk 36`, `jvmTarget 17`, wersja `0.1`.
Budowa debug dostaje przyrostek `.debug`, więc obie wersje mogą stać na tablecie naraz.

Przy pracy bez sieci dodaj `--offline`, oszczędza kilkanaście sekund na starcie.

### Serwer

```bash
cd /d/Inne/kajet_server
npm install
npm run db:push      # schemat do bazy (albo db:migrate na produkcji)
npm run dev          # http://localhost:9081
```

Reszta poleceń:

| Polecenie | Do czego |
| --- | --- |
| `npm run build` | generuje klienta Prismy i buduje Next.js |
| `npm run start` | wersja produkcyjna, port 9081 |
| `npm run typecheck` | `tsc --noEmit`, **puszczaj przed każdym commitem** |
| `npm run lint` | ESLint |
| `npm run db:studio` | przeglądarka bazy |
| `npm run admin` | nadaje komuś prawa administratora |

Serwer nie ma testów. Przy dokładaniu edytora na stronie to zaczyna być problem —
patrz punkt 6.

---

## 4. Układ aplikacji: gdzie co zmieniać

Osiem modułów Gradle. Zależności idą w jedną stronę: `:app` zna wszystkich,
`:core` nie zna nikogo.

| Moduł | Za co odpowiada | Kiedy tu wchodzisz |
| --- | --- | --- |
| `:core` | paleta, typografia, ikony rysowane w kodzie, **model dokumentu** | zmiana formatu notatki, kolory, wspólne przyciski |
| `:storage` | katalog biblioteki, format `.note`, kosz, spis do wyszukiwania | zapis na dysk, Room, ustawienia |
| `:ink` | silnik kreski, narzędzia, rozpoznawanie pisma | rysowanie, gumka, dotyk rysika i palca |
| `:editor` | edytory notatek, historia zmian | odręczna, tekstowa, mapa myśli |
| `:code` | edytor kodu i uruchamianie | podświetlanie składni, wynik |
| `:export` | PDF, DOCX, Markdown, PNG, druk | eksport |
| `:cloud` | konto, synchronizacja, kolejka wysyłki | rozmowa z serwerem |
| `:app` | nawigacja, biblioteka, ustawienia, Python na tablecie | ekrany główne, sklejenie całości |

### Miejsca, do których wraca się najczęściej

| Chcesz zmienić | Plik |
| --- | --- |
| format notatki | `core/.../model/NoteDocument.kt` + `storage/.../NoteCodec.kt` |
| adres serwera | `cloud/.../AccountStore.kt`, stała `SERVER_URL` |
| co leci na serwer i kiedy | `cloud/.../Sync.kt` |
| połączenie HTTP, błędy | `cloud/.../CloudClient.kt` |
| dotyk rysika i palca | `ink/.../StrokeCanvas.kt`, metoda `onDown` |
| pasek narzędzi przy pisaniu | `editor/.../handwriting/HandwritingEditor.kt` |
| bloki notatki tekstowej | `editor/.../text/Blocks.kt` |
| ekrany biblioteki | `app/.../ui/library/` |
| spis do wyszukiwania | `storage/.../index/IndexDatabase.kt` |

### Zasady, których trzyma się ten kod

- **Cały interfejs po polsku.** Nazwy plików i klas po angielsku, teksty dla
  człowieka po polsku, bez wyjątków.
- **Prawdą są pliki, nie baza.** Room to tylko spis do wyszukiwania — można go
  skasować, a aplikacja odbuduje go z katalogu.
- **Nazwy w JSON są ustalone** przez `@SerialName`. Zmiana nazwy pola w Kotlinie
  bez `@SerialName` psuje wszystkie zapisane notatki i serwer naraz.
- **Testy jednostkowe tam, gdzie jest logika bez Androida**: `Blocks`, `Strokes`,
  `Markdown`, `NoteCodec`, `MindMapLayout`, `FileNames`, `LibraryStore`.
  Compose i Room nie mają testów i na razie tak zostaje.

---

## 5. Format notatki to kontrakt

Notatka jest opisana w dwóch miejscach i **te dwa miejsca muszą się zgadzać**:

- tablet: `core/.../model/NoteDocument.kt` (klasy) + `storage/.../NoteCodec.kt` (zapis),
- serwer: `src/lib/document.ts` — lustro tych samych typów, ale **tylko do czytania**.

### Uwaga: kopie dokumentu rozjechały się

Najnowszy opis formatu leży **na serwerze**, nie w aplikacji:

| Wersja | Gdzie | Stan |
| --- | --- | --- |
| **najnowsza, 266 wierszy** | `D:\Inne\kajet_server\apka\FORMAT.md` | zgadza się z dzisiejszym kodem |
| stara, 244 wiersze | `git show HEAD:FORMAT.md` w repo aplikacji | **przestarzała**, ma polskie nazwy wartości |
| — | drzewo robocze aplikacji | **skasowana** |

Stara wersja mówi `"kind": "odreczna"`, `"tool": "pioro"`, `"background": "kratka"`.
Dzisiejszy kod zapisuje `"handwritten"`, `"pen"`, `"grid"`. Nie idź za tą z `HEAD`.

**Do zrobienia:** przenieść nowszy `FORMAT.md` z powrotem do repozytorium aplikacji
i przestać trzymać kopie. Jedno źródło prawdy, a serwer niech go czyta albo ma
u siebie odnośnik. Kopie zawsze się rozejdą — właśnie się rozeszły.

W tym samym pliku jest jeszcze jeden błąd do poprawienia: przykład węzła mapy
myśli podaje `"font": "tekstowy"`, a kod zapisuje `heading`, `body` albo `mono`.

### Także do poprawienia w dokumentach

`apka/README.md` pisze, że kod liczy „serwer Piston" i że adres serwera zmienia
się w ustawieniach. Ani jedno, ani drugie już nie jest prawdą — kod liczy serwer
Kajetu, adres jest wpisany na stałe, a sekcja z ustawień została skasowana.
Tabela modułów w tym README nie wymienia `:cloud`.

---

## 6. Co jest do zrobienia

### A. Edytowanie i tworzenie notatek na stronie

Cel: notatkę da się napisać i poprawić także na komputerze, przez przeglądarkę.

Dziś strona notatki **tylko czyta** (`src/components/NotePreview.tsx` renderuje
wszystkie trzy rodzaje). W udostępnianiu jest już `permission: "EDIT"`, którego
nikt nie używa. Kolejność ma znaczenie:

**Krok 1 — `FORMAT.md` z powrotem na miejsce.** Póki pisze tylko tablet, brak
spisanego formatu boli mało. Od chwili, gdy pisze też strona, kończy się cichym
psuciem notatek. W dokumencie jest pole `format: number` — używaj go.

**Krok 2 — dwie poprawki w aplikacji.** Obie opisane w
`D:\Inne\kajet_server\donaprawy.md`, obie muszą być gotowe **zanim** strona
zacznie zapisywać:

- *Konflikty.* Serwer odsyła konflikt jako HTTP 409, a `CloudClient` traktuje
  wszystko spoza 2xx jako błąd — więc obsługa konfliktu w `Sync.kt` nigdy się nie
  wykonuje. Dziś nie boli, bo notatkę zmienia jedno urządzenie. Przy dwóch
  edytorach zmiana z tabletu po cichu utknie w kolejce.
- *Pobieranie załączników.* `Sync` wysyła zdjęcia na serwer, ale nigdy ich nie
  ściąga. Notatka utworzona na stronie przyjedzie na tablet z `![zdjęcie](assets/…)`
  i bez pliku.

**Krok 3 — jedna droga zapisu na serwerze.** Strona pisałaby przez server action
prosto do Prismy i ominęłaby logikę z `PUT /api/v1/notes`: `version: { increment: 1 }`,
`hash`, `fitsInQuota`. Wtedy wykrywanie konfliktów przestaje działać dla obu stron.
Wyciągnąć tę logikę do `src/lib/` i wołać ją i z API, i z akcji strony.

**Krok 4 — edytor.** Trzy rodzaje notatek to trzy różne projekty:

| Rodzaj | Nakład | Uwagi |
| --- | --- | --- |
| tekstowa | mały | to `text.markdown` w polu tekstowym |
| mapa myśli | średni | węzły i linie na SVG, przeciąganie myszą |
| odręczna | duży | rysik w przeglądarce, nacisk, wygładzanie kresek |

Zacząć od **tekstowej**: daje pełną wartość przy najmniejszym nakładzie i od razu
sprawdza całą drogę — zapis na stronie, wersja, synchronizacja, tablet. Notatki
odręczne zostawić na stronie do czytania.

**Tworzenie nowych notatek** przy okazji edytora tekstowego: nowy dokument
z `format`, `id` (UUID), `kind` i pustą treścią.

**Krok 5 — testy na serwerze.** Dziś nie ma żadnych. Dwa edytory piszące ten sam
dokument bez testu na obieg „zapis → wersja → konflikt" to proszenie się o kłopoty.
Minimum: test drogi zapisu i test kształtu odpowiedzi API.

### B. Reszta z `donaprawy.md`

`D:\Inne\kajet_server\donaprawy.md` ma osiem punktów. Poza konfliktami najgroźniejszy
jest drugi: **stronicowanie po samym `updatedAt` gubi notatki**, gdy dwie mają tę
samą milisekundę na granicy strony. Dotyczy pierwszej synchronizacji po zalogowaniu,
czyli sytuacji z największą liczbą notatek.

### C. Do sprawdzenia na tablecie

Poprawki z 3 sierpnia zostały zbudowane i przeszły testy jednostkowe, ale **nie były
uruchomione na sprzęcie**. Do sprawdzenia ręcznie:

- rysik pisze, palec przesuwa kartkę, dwa palce skalują;
- pasek kolorów i grubości nad kartką;
- zmiana wielkości zdjęcia i pisanie pod nim;
- rozłączanie węzłów przez dotknięcie linii;
- „Ostatnio otwarte" — **to najmniej pewna z poprawek**, dokładnej przyczyny nie
  udało się odtworzyć, załatana została najbardziej prawdopodobna dziura;
- powrót do biblioteki po synchronizacji, czy nie ma czarnego ekranu.

---

## 7. Ściąga

```bash
# aplikacja
cd /c/Users/Wojte/AndroidStudioProjects/Notatnik
./gradlew :app:assembleDebug --offline
./gradlew test --offline
git status --short | head -40

# serwer
cd /d/Inne/kajet_server
npm run dev            # localhost:9081
npm run typecheck
npm run db:studio
```

| Dokument | Gdzie | O czym |
| --- | --- | --- |
| `ROZWOJ.md` | repo aplikacji | ten plik |
| `FORMAT.md` | `kajet_server/apka/` | format zapisu notatki, **najnowszy tam** |
| `POSTEP.md` | `kajet_server/apka/` | stan prac, lista do sprawdzenia na tablecie |
| `donaprawy.md` | `kajet_server/` | co naprawić na serwerze |
| `INSTALACJA.md` | `kajet_server/` | stawianie serwera |
