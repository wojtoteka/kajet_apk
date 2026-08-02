# Format zapisu Kajetu

Ten plik opisuje, jak Kajet trzyma notatki na dysku. Piszę go po to, żeby dało się
odzyskać treść bez aplikacji, gdyby kiedyś przestała działać albo przestała istnieć.

## Zasada nadrzędna

Prawdą są pliki. Baza danych wewnątrz aplikacji to tylko spis na potrzeby
wyszukiwania i szybkiego rysowania listy. Można ją skasować w każdej chwili,
a Kajet odbuduje ją, czytając katalog biblioteki od nowa.

## Gdzie to leży

Przy pierwszym uruchomieniu wskazujesz katalog przez systemowe okno wyboru folderu.
Kajet bierze do niego trwałe uprawnienie i od tej pory zapisuje tam wszystko.
Katalog jest zwykłym miejscem na dysku, więc po odinstalowaniu aplikacji notatki
zostają na tablecie i da się je skopiować na komputer.

## Układ katalogu

```
<wybrany katalog>/
  Matematyka/                     folder w aplikacji to katalog na dysku
    folder.json                   kolor, ikona i prawdziwa nazwa folderu
    Całki oznaczone.note/         notatka to katalog z końcówką .note
      content.json                cała treść notatki
      content.bak.json            kopia z ostatniego zapisu
      assets/                     zdjęcia i rysunki wstawione w notatkę
        zdjecie-1.jpg
        rysunek-1.png
        rysunek-1.strokes.json    kreski rysunku, żeby dało się go poprawić
    zadanie1.py                   plik z kodem, zwykły plik tekstowy
  Polski/
    folder.json
  .trash/                         kosz, katalog ukryty
    3f2b1a.../                    jeden wyrzucony wpis
      kosz.json                   skąd pochodzi i kiedy trafił do kosza
      Notatka.note/               sam wyrzucony wpis
```

Notatka jest katalogiem, a nie plikiem ZIP. Powód jest praktyczny: notatka
zapisuje się sama co kilka sekund, a przepisywanie całego archiwum przy każdym
zapisie trwałoby długo i groziło uszkodzeniem pliku, gdyby tablet zasnął w środku.
Przy katalogu zapisujemy sam plik `content.json`, a zdjęcia leżą nietknięte.
Do wysłania komuś notatki służy eksport, który pakuje ją na życzenie.

## Nazwy na dysku

Nazwa katalogu odpowiada nazwie wpisanej przez użytkownika, ale znaki, których
nie przyjmie karta pamięci sformatowana jako FAT, zamieniamy na podkreślenie.
Dotyczy to `" * / : < > ? \ |` oraz znaków sterujących. Prawdziwa nazwa zostaje
zapisana wewnątrz: dla folderu w polu `displayName` pliku `folder.json`,
dla notatki w polu `title` pliku `content.json`. Aplikacja pokazuje tę prawdziwą.

## Plik content.json

Kodowanie UTF-8, jeden wiersz bez wcięć. Przykład notatki odręcznej,
tu rozbity na wiersze dla czytelności:

```json
{
  "format": 1,
  "id": "1f0c...",
  "kind": "odreczna",
  "title": "Całki oznaczone",
  "createdAt": 1730000000000,
  "updatedAt": 1730000600000,
  "tags": ["matematyka"],
  "favorite": true,
  "handwriting": {
    "pageMode": "a4",
    "background": "kratka",
    "pages": [
      {
        "id": "8a1e...",
        "width": 595.0,
        "height": 842.0,
        "strokes": [
          {
            "id": "c41d...",
            "tool": "pioro",
            "color": -14606819,
            "size": 2.4,
            "epsilon": 0.1,
            "input": "rysik",
            "points": [120.5, 300.25, 0.0, 0.35, 0.9, 1.2,
                       122.0, 301.0, 8.0, 0.51, 0.88, 1.19]
          }
        ],
        "texts": [],
        "images": [],
        "recognized": []
      }
    ]
  }
}
```

### Pola wspólne

| Pole | Znaczenie |
| --- | --- |
| `format` | Numer wersji formatu. Dziś 1. Rośnie, gdy zmiana psuje zgodność wstecz. |
| `id` | Identyfikator notatki, nadawany raz przy utworzeniu. |
| `kind` | `odreczna`, `tekstowa` albo `mapa`. |
| `title` | Tytuł widoczny w aplikacji, także z polskimi znakami. |
| `createdAt`, `updatedAt` | Czas w milisekundach od 1 stycznia 1970. |
| `tags` | Lista tagów. |
| `favorite` | Czy notatka jest w ulubionych. |

Nieznane pola przy odczycie są pomijane, więc plik zapisany przez nowszą wersję
Kajetu otworzy się w starszej, o ile nie zmienił się numer `format`.

### Jednostki

Współrzędne są w punktach typograficznych, czyli 1/72 cala, tak samo jak w pliku PDF.
Strona A4 ma 595 na 842 punkty. Dzięki temu eksport i wydruk nie wymagają
przeliczania, a notatka wygląda tak samo na każdym ekranie.

### Kreski

Kreska trzyma surowe punkty z rysika, a nie gotowy kształt. Tylko z surowych
punktów da się odrysować pismo w pełnej jakości na innym ekranie oraz wysłać je
do rozpoznawania pisma.

Punkty są spakowane w jedną listę liczb, po sześć na punkt, w kolejności:

1. `x` w punktach strony,
2. `y` w punktach strony,
3. czas w milisekundach od początku kreski,
4. nacisk od 0 do 1, wartość `-1` gdy rysik go nie podał,
5. pochylenie w radianach, `-1` gdy brak,
6. obrót w radianach, `-1` gdy brak.

Taki zapis jest kilka razy mniejszy od listy obiektów i szybciej się wczytuje.
Współrzędne są zaokrąglane do dwóch miejsc po przecinku, nacisk do trzech.

Pole `color` to liczba całkowita w formacie ARGB, ta sama, której używa Android.
Pole `tool` przyjmuje `pioro` albo `zakreslacz`. Pole `input` mówi, czym pisano:
`rysik`, `palec` albo `mysz`.

### Tła stron

`gladkie`, `linie`, `kratka`, `kropki`, `pieciolinia`. Tło ustawione przy stronie
ma pierwszeństwo nad tłem ustawionym dla całej notatki.

### Tryb strony

`a4` to osobne kartki po 595 na 842 punkty. `wstega` to jedna strona,
która rośnie w dół, gdy dopisujesz przy dolnej krawędzi.

### Notatka tekstowa

```json
"text": {
  "markdown": "# Zadania\n\n- [ ] Przeczytać rozdział 3\n",
  "drawings": [
    {
      "asset": "rysunek-1.png",
      "source": "rysunek-1.strokes.json",
      "width": 560.0,
      "height": 300.0
    }
  ]
}
```

Treść to zwykły Markdown, dokładnie taki, jaki wychodzi przy eksporcie.
Zdjęcia i rysunki są w nim zapisane jako `![opis](assets/nazwa.png)`.
Rysunek wstawiony w tekst ma dwa pliki: obrazek do pokazania i plik z kreskami
do dalszego poprawiania. Plik z kreskami ma postać:

```json
{ "format": 1, "width": 560.0, "height": 300.0, "strokes": [ ... ] }
```

### Mapa myśli

```json
"mindMap": {
  "nodes": [
    { "id": "w1", "x": 0.0, "y": 0.0, "width": 160.0, "height": 64.0,
      "shape": "prostokat", "text": "Ruch", "ink": [], "colorId": "zielen",
      "collapsed": false }
  ],
  "edges": [ { "id": "e1", "fromId": "w1", "toId": "w2" } ],
  "viewX": 0.0, "viewY": 0.0, "zoom": 1.0
}
```

Rodzic i dziecko wynikają z kierunku linii: `fromId` jest rodzicem. Nie trzymamy
tego drugi raz w węźle, żeby nie dało się doprowadzić do sprzeczności.
Podpis pisany rysikiem leży w polu `ink` węzła, we współrzędnych liczonych
od lewego górnego rogu tego węzła.

## Plik folder.json

```json
{
  "format": 1,
  "id": "b41f...",
  "displayName": "Fizyka: mechanika",
  "colorId": "morski",
  "iconId": "kolba",
  "createdAt": 1730000000000,
  "order": 0
}
```

## Kopia zapasowa i ratowanie notatki

Przy każdym zapisie Kajet zapisuje tę samą treść dwa razy: najpierw do
`content.bak.json`, potem do `content.json`. Gdyby zapis urwał się w połowie
drugiego pliku, kopia jest już kompletna i zawiera dokładnie tę samą treść.
Przy odczycie Kajet sięga po kopię, kiedy pliku głównego nie da się wczytać.

Gdy oba pliki są uszkodzone, notatkę można odzyskać ręcznie: `content.json`
to zwykły tekst, więc da się go otworzyć w edytorze i poprawić.

## Kosz

Wyrzucony wpis trafia do katalogu `.trash/<identyfikator>/`. Obok niego leży
`kosz.json`:

```json
{
  "id": "3f2b...",
  "originalPath": "Matematyka/Całki.note",
  "fileName": "Całki.note",
  "displayName": "Całki",
  "type": "NOTATKA",
  "deletedAt": 1730000900000
}
```

Przywrócenie odkłada wpis dokładnie tam, skąd zniknął, a brakujące foldery
po drodze tworzy od nowa.

## Zgodność w przyszłości

Zmiany, które nie psują zgodności, czyli nowe pola z wartością domyślną,
nie podnoszą numeru `format`. Zmiany, które psują zgodność, podnoszą go o jeden,
a starsza wersja Kajetu odmawia otwarcia takiego pliku i mówi wprost,
że trzeba zaktualizować aplikację.
