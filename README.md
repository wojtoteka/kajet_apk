# Kajet

Notatnik na tablet z Androidem. Pismo odręczne rysikiem, notatki tekstowe,
mapy myśli i edytor kodu, który potrafi ten kod uruchomić.

Notatki leżą w katalogu wskazanym przez Ciebie, a nie w katalogu aplikacji,
więc zostają na tablecie także po odinstalowaniu Kajetu.

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
| `:code` | Edytor kodu i uruchamianie przez serwer |
| `:export` | PDF, DOCX, Markdown, PNG, wydruk, udostępnianie |
| `:app` | Nawigacja, biblioteka, ustawienia, Python na tablecie |

Format zapisu opisuje [FORMAT.md](FORMAT.md).
Stan prac i listę rzeczy do sprawdzenia na tablecie trzyma [POSTEP.md](POSTEP.md).

## Uruchamianie kodu

Python liczy się na tablecie i działa bez internetu. Pozostałe języki liczy
serwer Piston, więc potrzebne jest połączenie. Adres serwera zmienisz
w ustawieniach, także na własny.

## Licencje

Kroje pisma Archivo oraz IBM Plex są na licencji SIL Open Font License,
teksty licencji leżą w `core/licenses/`. KaTeX jest na licencji MIT.
