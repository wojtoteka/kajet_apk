"""Uruchamianie kodu użytkownika po stronie Pythona.

Kod dostaje własną, pustą przestrzeń nazw, a standardowe wejście i wyjście
podstawiamy na bufory w pamięci. Dzięki temu funkcja input czyta to,
co użytkownik wpisał w zakładce Wejście, a print trafia do panelu Wynik,
a nie do dziennika systemu.
"""

import io
import sys
import traceback


def uruchom(kod, wejscie=""):
    stare_wyjscie = sys.stdout
    stare_bledy = sys.stderr
    stare_wejscie = sys.stdin

    bufor_wyjscia = io.StringIO()
    bufor_bledow = io.StringIO()
    sys.stdout = bufor_wyjscia
    sys.stderr = bufor_bledow
    sys.stdin = io.StringIO(wejscie or "")

    kod_wyjscia = 0
    przestrzen = {"__name__": "__main__", "__doc__": None}

    try:
        skompilowany = compile(kod, "<program>", "exec")
        exec(skompilowany, przestrzen)
    except SystemExit as wyjscie_systemowe:
        kod_wyjscia = int(wyjscie_systemowe.code or 0)
    except SyntaxError as blad:
        kod_wyjscia = 1
        # Błąd składni pokazujemy krótko, bo pełny ślad stosu tylko myli.
        bufor_bledow.write(
            "Błąd składni w wierszu {}: {}\n".format(blad.lineno, blad.msg)
        )
        if blad.text:
            bufor_bledow.write(blad.text.rstrip() + "\n")
    except BaseException:
        kod_wyjscia = 1
        slady = traceback.format_exc().splitlines()
        # Pierwszy wiersz dotyczy tego pliku, a nie kodu użytkownika.
        czyste = [w for w in slady if "kajet_runner.py" not in w]
        bufor_bledow.write("\n".join(czyste) + "\n")
    finally:
        sys.stdout = stare_wyjscie
        sys.stderr = stare_bledy
        sys.stdin = stare_wejscie

    return {
        "stdout": bufor_wyjscia.getvalue(),
        "stderr": bufor_bledow.getvalue(),
        "code": kod_wyjscia,
    }
