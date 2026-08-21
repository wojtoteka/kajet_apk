package wojtoteka.ovh.kajet.awaria

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zapis awarii do pliku - żeby po restarcie dało się przeczytać, co się
 * stało, i przekleić szczegóły do zgłoszenia.
 *
 * Raporty leżą w `files/awarie/`, zostaje pięć najnowszych.
 */
object CrashLog {

    private const val DIR_NAME = "awarie"
    private const val KEEP = 5
    private const val SEEN_NAME = "awaria-pokazana.txt"

    /**
     * Tyle znaków opisu awarii wolno włożyć do intentu - Binder ma limit
     * ~1 MB na CAŁĄ transakcję, więc zostawiamy szeroki zapas.
     */
    const val LONGEST_EXTRA = 100_000

    /** Zapisuje gotowy opis z [report]. Ten sam tekst idzie potem na ekran. */
    fun write(context: Context, report: String): File? = runCatching {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, "awaria-${System.currentTimeMillis()}.txt")
        file.writeText(report)
        rotate(dir)
        file
    }.getOrNull()

    fun report(context: Context, thread: Thread, failure: Throwable): String = buildString {
        // Numer wydania w nawiasie: nazwa wersji bywa ta sama w kilku
        // kolejnych plikach .apk, a numer rośnie zawsze i po nim widać, która
        // to dokładnie wersja.
        appendLine("Kajet ${versionName(context)} (${versionCode(context)})")
        appendLine(
            "Czas: " +
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.forLanguageTag("pl")).format(Date()),
        )
        appendLine(device())
        appendLine("Wątek: ${thread.name}")
        appendLine()
        append(stackTrace(failure))
    }

    fun stackTrace(failure: Throwable): String {
        val writer = StringWriter()
        failure.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    /** Opis obcięty do rozmiaru, który zniesie intent. */
    fun clip(text: String): String =
        if (text.length > LONGEST_EXTRA) text.take(LONGEST_EXTRA) else text

    // --- Wysyłka na serwer ---

    /**
     * Doklejane do nazwy pliku po wysłaniu raportu.
     *
     * Znacznik siedzi w NAZWIE, nie w osobnym spisie: plik i jego stan są
     * wtedy jedną rzeczą, więc przerwana wysyłka albo skasowany plik nie
     * zostawiają po sobie wpisu, który do niczego nie pasuje.
     */
    const val SENT_SUFFIX = ".wyslane"

    /** Raporty, które jeszcze nie doszły na serwer - od najstarszego. */
    fun unsentCrashes(context: Context): List<File> =
        File(context.filesDir, DIR_NAME).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(SENT_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()

    fun markSent(file: File): Boolean =
        runCatching { file.renameTo(File(file.parentFile, file.name + SENT_SUFFIX)) }
            .getOrDefault(false)

    /** To, co da się wyczytać z nagłówka raportu - do pól osobno na serwerze. */
    data class Facts(
        val appVersion: String? = null,
        val versionCode: Int? = null,
        val device: String? = null,
        val android: String? = null,
        val thread: String? = null,
    )

    /*
      Nagłówek układa [report] i tylko on zna jego kształt, więc czytanie go
      siedzi tuż obok pisania. Każde pole osobno i każde może się nie udać:
      raport zapisany starszą wersją Kajetu nie ma numeru wydania, a i tak ma
      dojść na serwer.
    */
    private val VERSION_LINE = Regex("""^Kajet\s+(\S+?)(?:\s+\((\d+)\))?\s*$""", RegexOption.MULTILINE)
    private val DEVICE_LINE = Regex("""^Urządzenie:\s*(.+?)(?:,\s*Android\s*(.+?))?\s*$""", RegexOption.MULTILINE)
    private val THREAD_LINE = Regex("""^Wątek:\s*(.+?)\s*$""", RegexOption.MULTILINE)

    fun facts(report: String): Facts {
        val version = VERSION_LINE.find(report)
        val device = DEVICE_LINE.find(report)
        return Facts(
            appVersion = version?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
            versionCode = version?.groupValues?.get(2)?.toIntOrNull(),
            device = device?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
            android = device?.groupValues?.get(2)?.takeIf { it.isNotBlank() },
            thread = THREAD_LINE.find(report)?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
        )
    }

    fun lastCrash(context: Context): File? =
        File(context.filesDir, DIR_NAME).listFiles()?.maxByOrNull { it.name }

    fun lastCrashTime(context: Context): Long = lastCrash(context)?.lastModified() ?: 0L

    /**
     * Awaria, o której jeszcze nie powiedzieliśmy.
     *
     * Od Androida 12 aplikacja w tle nie może otworzyć ekranu, więc awaria,
     * która trafi Kajet zwinięty do tła, kończy się cichym zniknięciem: raport
     * leży w pliku, ale nikt go nie widzi. Przy najbliższym otwarciu aplikacji
     * ten raport idzie na ekran po awarii.
     */
    fun unseenCrash(context: Context): File? {
        val last = lastCrash(context) ?: return null
        return last.takeIf { it.lastModified() > seenAt(context) }
    }

    fun markSeen(context: Context) {
        val last = lastCrash(context) ?: return
        runCatching { seenFile(context).writeText(last.lastModified().toString()) }
    }

    /*
      Znacznik siedzi w PLIKU, nie w ustawieniach aplikacji. Ekran po awarii
      chodzi w osobnym procesie (`:blad`), a SharedPreferences każdy proces
      trzyma u siebie w pamięci i nie widzi cudzych zapisów. Plik widzą oba.

      Leży obok katalogu z raportami, nie w środku - inaczej sprzątanie starych
      raportów (rotate) liczyłoby go jako raport i potrafiło skasować.
    */
    private fun seenAt(context: Context): Long =
        runCatching { seenFile(context).readText().trim().toLong() }.getOrDefault(0L)

    private fun seenFile(context: Context) = File(context.filesDir, SEEN_NAME)

    /** Zostaje [keep] najnowszych raportów; nazwy niosą czas, więc sortują się same. */
    fun rotate(dir: File, keep: Int = KEEP) {
        val files = dir.listFiles()?.sortedByDescending { it.name } ?: return
        for (file in files.drop(keep)) {
            runCatching { file.delete() }
        }
    }

    private fun device(): String = runCatching {
        "Urządzenie: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    }.getOrDefault("Urządzenie: nieznane")

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun versionCode(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)
}
