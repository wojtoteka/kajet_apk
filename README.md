# Kajet

Notatnik na Androida (telefon i tablet). Pismo odręczne rysikiem, notatki tekstowe,
mapy myśli i edytor kodu, który potrafi ten kod uruchomić.

Notatki leżą w katalogu wskazanym przez Ciebie, a nie w katalogu aplikacji,
więc zostają na urządzeniu także po odinstalowaniu Kajetu.

Na telefonie priorytetem są biblioteka, notatki tekstowe, konto i synchronizacja.
Pismo odręczne działa, ale wygodniej na większym ekranie.

## Budowanie

Potrzebne: JDK 17, Android SDK z poziomem 36 oraz Python 3 na ścieżce systemowej,
bo tłumacza Pythona wbudowuje w aplikację wtyczka Chaquopy.

```
./gradlew :app:assembleDebug
./gradlew test
```

Plik do zainstalowania powstaje w `app/build/outputs/apk/debug/`.
Aplikacja jest budowana tylko dla procesorów arm64, bo tłumacz Pythona
to kod natywny, a każda kolejna architektura dokłada kilkadziesiąt megabajtów.

## Układ projektu

| Moduł | Za co odpowiada |
| --- | --- |
| `:core` | Paleta, typografia, ikony rysowane w kodzie, model dokumentu |
| `:storage` | Katalog biblioteki, format `.note`, kosz, spis do wyszukiwania |
| `:ink` | Silnik kreski, narzędzia, rozpoznawanie pisma |
| `:editor` | Edytory notatek i historia zmian |
| `:code` | Edytor kodu i uruchamianie |
| `:export` | PDF, DOCX, Markdown, PNG, wydruk, udostępnianie |
| `:cloud` | Konto, synchronizacja, kolejka wysyłki |
| `:app` | Nawigacja, biblioteka, ustawienia, Python lokalny |

Format zapisu opisuje [FORMAT.md](FORMAT.md).
Stan prac i listę rzeczy do sprawdzenia trzyma [POSTEP.md](POSTEP.md).

## Konto i logowanie

Adres serwera jest wpisany na stałe w `cloud/.../AccountStore.kt`
(`SERVER_URL = https://kajet.wojtoteka.ovh`) i nie da się go zmienić z ustawień.

**Zaloguj przez Google** (albo hasłem na stronie): aplikacja tworzy jednorazowy
kod (`POST /api/v1/signin/device`), otwiera Custom Tabs na
`/signin/device?code=…`, a w tle odpytuje `GET /api/v1/signin/device?code=…`
aż dostanie token. Po zatwierdzeniu strona może też wrócić deep linkiem
`kajet://auth?code=…`. Token trafia do EncryptedSharedPreferences jak przy
logowaniu hasłem.

**Fallback:** adres + hasło w aplikacji, albo wklejenie tokenu ze strony
`/account`.

## Uruchamianie kodu

Python liczy się lokalnie i działa bez internetu. Pozostałe języki liczy
serwer Kajetu (`https://kajet.wojtoteka.ovh`), więc potrzebne jest połączenie.

## Licencje

Kroje pisma Archivo oraz IBM Plex są na licencji SIL Open Font License,
teksty licencji leżą w `core/licenses/`. KaTeX jest na licencji MIT.
