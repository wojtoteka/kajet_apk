# Kajet — notatnik na Androida

Notatnik na tablet z rysikiem: pismo odręczne, notatki tekstowe w Markdownie, mapy myśli,
notatki z kodem (z Pythonem uruchamianym na urządzeniu), eksport do PDF/DOCX i synchronizacja
z własną chmurą. Kotlin + Jetpack Compose, osiem modułów Gradle.

Serwer, z którym się synchronizuje, leży w repo
[`kajet_server`](https://github.com/Wojtoteka/kajet_server).

---

## Co to potrafi

**Pismo odręczne.** Kreska stawiana przez `androidx.ink`, z rozpoznawaniem figur (prostuje
koło, prostokąt, strzałkę), gumką, zaznaczaniem, ramkami na zdjęcia i polami tekstowymi
na kartce. Do tego haptyka pióra i formaty stron — notatka pamięta swój rozmiar kartki.

**Notatki tekstowe.** Markdown z tabelami, listami, obrazkami ze skalowaniem szerokości
(`![alt|60%]`) i rozmiarem czcionki. Rozmiar `0` znaczy „domyślny z motywu", więc notatka
nie zamarza na wartości sprzed zmiany motywu.

**Mapy myśli.** Węzły owalne i prostokątne, zawijanie długich haseł, płótno biorące rozmiar
z kontenera zamiast sztywnych wymiarów.

**Notatki z kodem.** Edytor z podświetlaniem składni i podpowiedziami, konsola HTML z podglądem
w WebView oraz uruchamianie: Python bezpośrednio na tablecie (Chaquopy, `arm64-v8a`) albo
zdalnie na serwerze Kajetu w kontenerze.

**Przeglądarka plików.** Obrazy, PDF-y i nieznane binaria otwierają się w podglądzie tylko do
odczytu — nigdy nie lądują w edytorze kodu jako UTF-8.

**Udostępnianie w obie strony.** Apka przyjmuje pliki przez Android Share i „Otwórz w",
kopiując je najpierw do katalogu notatek, a notatkę wypuszcza linkiem z serwera.

**Eksport.** PDF (z formatowaniem tekstu, zdjęciami i rysunkiem), DOCX, Markdown.

**Chmura.** Konto, logowanie urządzenia własnym tokenem, synchronizacja notatek i folderów,
ikona chmurki dopiero po faktycznym zapisie na serwerze, kolejka ponawiania usunięć i osłona
przed nadpisaniem notatki skasowanej zdalnie.

**KajetAI.** Panel asystenta wpięty w notatkę — z ekranem zgody, limitami i komunikatami
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
Synchronizacja porównuje dokumenty, nie modele — to samo założenie po obu stronach.

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

## Uwagi

Adres chmury domyślnie wskazuje na `kajet.wojtoteka.ovh`. Żeby wpiąć własny serwer, postaw
[`kajet_server`](https://github.com/Wojtoteka/kajet_server) i zmień adres w `cloud/AccountStore.kt`.

## Licencja

Kod udostępniony do wglądu w celach portfolio.
