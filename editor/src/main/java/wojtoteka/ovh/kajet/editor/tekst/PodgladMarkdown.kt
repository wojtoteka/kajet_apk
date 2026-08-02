package wojtoteka.ovh.kajet.editor.tekst

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wojtoteka.ovh.kajet.core.design.KajetColors
import java.io.ByteArrayInputStream

/**
 * Podgląd notatki tekstowej.
 *
 * Podgląd rysuje przeglądarka wbudowana w Androida, bo tylko tak dostajemy
 * poprawne wzory matematyczne bez internetu. KaTeX leży w plikach aplikacji,
 * a nie w sieci, więc podgląd działa też w pociągu.
 *
 * Zdjęcia z notatki nie idą przez adres pliku, tylko przez przechwycone
 * zapytanie, dzięki czemu przeglądarka nie dostaje dostępu do dysku.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PodgladMarkdown(
    markdown: String,
    kolory: KajetColors,
    zalacznik: suspend (String) -> ByteArray?,
    onZadanie: (numerWiersza: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var przegladarka by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { kontekst ->
            WebView(kontekst).apply {
                przegladarka = this
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.textZoom = 100
                isVerticalScrollBarEnabled = true

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun przelaczZadanie(numer: Int) {
                            post { onZadanie(numer) }
                        }
                    },
                    "Kajet",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        widok: WebView?,
                        zapytanie: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val adres = zapytanie?.url?.toString() ?: return null
                        if (!adres.startsWith(Markdown.ADRES_ZALACZNIKOW)) return null
                        val nazwa = adres.removePrefix(Markdown.ADRES_ZALACZNIKOW)
                        val dane = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            runCatching { zalacznik(nazwa) }.getOrNull()
                        } ?: return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            404,
                            "Brak pliku",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0)),
                        )
                        return WebResourceResponse(typMime(nazwa), null, ByteArrayInputStream(dane))
                    }
                }
            }
        },
    )

    LaunchedEffect(markdown, kolory.isDark) {
        val html = withContext(Dispatchers.Default) { stronaHtml(markdown, kolory) }
        przegladarka?.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "utf-8",
            null,
        )
    }
}

private fun typMime(nazwa: String): String = when (nazwa.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "application/octet-stream"
}

private fun hex(kolor: androidx.compose.ui.graphics.Color): String =
    String.format("#%06X", 0xFFFFFF and kolor.toArgb())

/**
 * Strona podglądu. Style pisane ręcznie, żeby podgląd wyglądał tak samo
 * jak reszta aplikacji: te same kolory, ta sama interlinia, tekst do lewej.
 */
private fun stronaHtml(markdown: String, kolory: KajetColors): String {
    val tresc = Markdown.doHtml(markdown)
    return """
<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex/katex.min.css">
<style>
  @font-face { font-family: 'PlexSans'; src: url('podglad/plex_sans.ttf'); }
  @font-face { font-family: 'PlexMono'; src: url('podglad/plex_mono_regular.ttf'); }
  html, body { background: transparent; margin: 0; padding: 0; }
  body {
    color: ${hex(kolory.text)};
    font-family: 'PlexSans', sans-serif;
    font-size: 16px;
    line-height: 1.62;
    padding: 24px 28px 96px 28px;
    text-align: left;
    word-wrap: break-word;
  }
  h1, h2, h3, h4, h5, h6 { font-weight: 600; line-height: 1.25; margin: 1.4em 0 0.5em 0; letter-spacing: -0.01em; }
  h1 { font-size: 1.85em; }
  h2 { font-size: 1.45em; }
  h3 { font-size: 1.18em; }
  h4, h5, h6 { font-size: 1em; }
  p { margin: 0 0 0.9em 0; }
  a { color: ${hex(kolory.accent)}; }
  hr { border: none; border-top: 1px solid ${hex(kolory.line)}; margin: 1.6em 0; }
  blockquote {
    margin: 1em 0; padding: 0.2em 0 0.2em 16px;
    border-left: 2px solid ${hex(kolory.accent)};
    color: ${hex(kolory.muted)};
  }
  ul, ol { margin: 0 0 0.9em 0; padding-left: 1.3em; }
  li { margin: 0.25em 0; }
  li.zadanie { list-style: none; margin-left: -1.3em; display: flex; gap: 10px; align-items: flex-start; }
  li.zadanie input { width: 20px; height: 20px; margin-top: 2px; accent-color: ${hex(kolory.accent)}; }
  li.zadanie .zrobione { color: ${hex(kolory.muted)}; text-decoration: line-through; }
  code {
    font-family: 'PlexMono', monospace; font-size: 0.92em;
    background: ${hex(kolory.desk)}; padding: 1px 5px; border-radius: 3px;
  }
  pre.kod {
    font-family: 'PlexMono', monospace; font-size: 0.9em; line-height: 1.5;
    background: ${hex(kolory.desk)}; padding: 14px 16px; border-radius: 3px;
    border-left: 2px solid ${hex(kolory.line)};
    overflow-x: auto;
  }
  pre.kod code { background: none; padding: 0; }
  img { max-width: 100%; height: auto; display: block; margin: 1em 0; border-radius: 2px; }
  .wzor { margin: 1.2em 0; overflow-x: auto; }
  .katex { font-size: 1.05em; }
</style>
</head>
<body>
$tresc
<script src="katex/katex.min.js"></script>
<script src="katex/auto-render.min.js"></script>
<script>
  renderMathInElement(document.body, {
    delimiters: [
      {left: '$$', right: '$$', display: true},
      {left: '\\(', right: '\\)', display: false}
    ],
    throwOnError: false
  });
  document.querySelectorAll('input[type=checkbox][data-wiersz]').forEach(function (pole) {
    pole.addEventListener('click', function () {
      Kajet.przelaczZadanie(parseInt(pole.getAttribute('data-wiersz'), 10));
    });
  });
</script>
</body>
</html>
    """.trimIndent()
}
