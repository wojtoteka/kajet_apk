package wojtoteka.ovh.kajet.storage

import android.util.LruCache
import kotlinx.serialization.Serializable
import wojtoteka.ovh.kajet.core.model.InkStroke
import wojtoteka.ovh.kajet.core.model.NoteDocument
import wojtoteka.ovh.kajet.core.model.ShapeElement
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Miniatura notatki odręcznej na liście - sama góra pierwszej strony.
 *
 * Kreski są tu łamanymi w całych jednostkach strony, bez nacisku, czasu
 * i pochylenia, z pominiętymi punktami leżącymi tuż obok poprzednich. Na
 * pasku wysokim na kilka milimetrów nie widać różnicy, a zostaje z tego
 * drobny ułamek pełnego zapisu kreski.
 */
@Serializable
class PageThumbnail(
    /** Data notatki, z której miniatura powstała - starsza miniatura idzie do kosza. */
    val updatedAt: Long,
    /** Każda kreska jako x, y, x, y… - tak samo jak obrysy kształtów w podglądzie. */
    val lines: List<IntArray> = emptyList(),
    /** Kształty zostają w całości, bo obrys liczy się dopiero przy rysowaniu. */
    val shapes: List<ShapeElement> = emptyList(),
    val format: Int = FORMAT,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && shapes.isEmpty()

    companion object {
        const val FORMAT = 1

        /** Tyle kresek pokazywał podgląd od zawsze - dalsze i tak nie trafiały na pasek. */
        const val MAX_STROKES = 240

        /** Punkt bliżej poprzedniego niż tyle jednostek strony nic na pasku nie zmienia. */
        private const val MIN_STEP = 2

        fun of(document: NoteDocument, updatedAt: Long): PageThumbnail {
            val page = document.handwriting?.pages?.firstOrNull()
                ?: return PageThumbnail(updatedAt)
            return PageThumbnail(
                updatedAt = updatedAt,
                lines = page.strokes.take(MAX_STROKES).mapNotNull(::simplified),
                shapes = page.shapes,
            )
        }

        private fun simplified(stroke: InkStroke): IntArray? {
            val count = stroke.pointCount
            if (count == 0) return null
            val out = ArrayList<Int>(minOf(count, 64) * 2)
            var lastX = stroke.x(0).roundToInt()
            var lastY = stroke.y(0).roundToInt()
            out += lastX
            out += lastY
            for (i in 1 until count) {
                val x = stroke.x(i).roundToInt()
                val y = stroke.y(i).roundToInt()
                val last = i == count - 1
                if (x == lastX && y == lastY) continue
                if (!last && abs(x - lastX) + abs(y - lastY) < MIN_STEP) continue
                out += x
                out += y
                lastX = x
                lastY = y
            }
            return out.toIntArray()
        }
    }
}

/**
 * Pamięć miniatur: najpierw w pamięci procesu, potem w plikach w katalogu
 * podręcznym aplikacji.
 *
 * Dawniej każdy wiersz notatki odręcznej na liście czytał i rozkodowywał
 * CAŁĄ notatkę - wszystkie strony, każdy punkt każdej kreski - żeby
 * narysować pasek wysoki na kilka milimetrów. Przy kilkudziesięciu takich
 * notatkach przewijanie stawało, a pamięć szła w górę. Miniatura powstaje
 * teraz przy zapisie notatki do spisu, kiedy treść i tak jest w pamięci.
 *
 * Katalog podręczny system może wyczyścić w każdej chwili - wtedy miniatura
 * powstaje od nowa z notatki, jeden raz.
 */
class NoteThumbnails(private val directory: File) {

    private val memory = LruCache<String, PageThumbnail>(MEMORY_ENTRIES)

    /** Miniatura dla notatki w tej wersji albo null, gdy jej jeszcze nie ma. */
    fun get(documentUri: String, updatedAt: Long): PageThumbnail? {
        memory.get(documentUri)?.let { if (it.updatedAt == updatedAt) return it }
        val file = fileFor(documentUri)
        val stored = runCatching {
            NoteCodec.json.decodeFromString<PageThumbnail>(file.readText())
        }.getOrNull() ?: return null
        if (stored.format != PageThumbnail.FORMAT || stored.updatedAt != updatedAt) return null
        memory.put(documentUri, stored)
        // Data pliku mówi sprzątaniu, że miniatura jest w użyciu.
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return stored
    }

    fun put(documentUri: String, thumbnail: PageThumbnail) {
        memory.put(documentUri, thumbnail)
        runCatching {
            synchronized(this) {
                directory.mkdirs()
                val target = fileFor(documentUri)
                // Najpierw obok, potem podmiana - urwany zapis nie zostawi
                // pliku, którego nie da się przeczytać.
                val written = File(directory, target.name + ".tmp")
                written.writeText(NoteCodec.json.encodeToString(thumbnail))
                if (!written.renameTo(target)) {
                    target.delete()
                    written.renameTo(target)
                }
            }
        }
    }

    private fun fileFor(documentUri: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(documentUri.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$name.json")
    }

    companion object {
        /** Katalog w pamięci podręcznej aplikacji - sprząta go też [Housekeeping]. */
        const val DIRECTORY = "miniatury"

        private const val MEMORY_ENTRIES = 300
    }
}
