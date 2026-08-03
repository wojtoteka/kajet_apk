"""Uruchamianie kodu użytkownika po stronie Pythona.

Kod dostaje własną, pustą przestrzeń nazw, a standardowe wejście i wyjście
podstawiamy na bufory w pamięci. Dzięki temu funkcja input czyta to,
co użytkownik wpisał w zakładce Wejście, a print trafia do panelu Wynik,
a nie do dziennika systemu.
"""

import io
import sys
import traceback


def run(code, stdin=""):
    saved_stdout = sys.stdout
    saved_stderr = sys.stderr
    saved_stdin = sys.stdin

    out = io.StringIO()
    errors = io.StringIO()
    sys.stdout = out
    sys.stderr = errors
    sys.stdin = io.StringIO(stdin or "")

    exit_code = 0
    namespace = {"__name__": "__main__", "__doc__": None}

    try:
        compiled = compile(code, "<program>", "exec")
        exec(compiled, namespace)
    except SystemExit as system_exit:
        exit_code = int(system_exit.code or 0)
    except SyntaxError as error:
        exit_code = 1
        # Błąd składni pokazujemy krótko, bo pełny ślad stosu tylko myli.
        errors.write("Błąd składni w wierszu {}: {}\n".format(error.lineno, error.msg))
        if error.text:
            errors.write(error.text.rstrip() + "\n")
    except BaseException:
        exit_code = 1
        trace = traceback.format_exc().splitlines()
        # Pierwszy wiersz dotyczy tego pliku, a nie kodu użytkownika.
        cleaned = [line for line in trace if "kajet_runner.py" not in line]
        errors.write("\n".join(cleaned) + "\n")
    finally:
        sys.stdout = saved_stdout
        sys.stderr = saved_stderr
        sys.stdin = saved_stdin

    return {
        "stdout": out.getvalue(),
        "stderr": errors.getvalue(),
        "code": exit_code,
    }
