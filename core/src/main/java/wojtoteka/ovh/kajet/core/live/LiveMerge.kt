package wojtoteka.ovh.kajet.core.live

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Zmiany notatki: różnica, nałożenie i scalanie trzech wersji.
 *
 * To ten sam silnik, linijka w linijkę, co src/lib/live/merge.ts na serwerze.
 * Treść notatki to jeden dokument JSON (content.json), więc edycja na żywo
 * i powrót z trybu offline sprowadzają się do trzech rzeczy:
 *
 * - [diff] - mała delta zamiast całej notatki w sieci,
 * - [applyDelta] - to samo po drugiej stronie,
 * - [merge3] - złożenie dwóch niezależnych zmian tej samej wersji bazowej,
 *   bez gubienia żadnej z nich.
 *
 * Silnik nie zna rodzajów notatek. Tablice obiektów z polem "id" (strony,
 * kreski, kształty, pola tekstowe, węzły i krawędzie mapy) traktuje jak zbiory
 * rzeczy, a długi tekst (markdown, źródło kodu) scala wierszami, a w obrębie
 * spornego fragmentu słowami.
 *
 * Reguły sporów: zmiana wygrywa ze skasowaniem; dwa różne teksty w tym samym
 * miejscu zostają oba, jeden pod drugim (najpierw ten z serwera); krótka
 * wartość (liczba, kolor, hasło węzła w jednym wierszu) bierze późniejszą
 * zmianę - „moją".
 *
 * Wspólne przypadki testowe z serwerem leżą w live-vectors.json.
 */
object LiveMerge {

    private val FORBIDDEN_KEYS = setOf("__proto__", "constructor", "prototype")
    private const val SHORT_TEXT = 64
    private const val MAX_LINE_EDITS = 3_000
    private const val MAX_WORD_EDITS = 2_000
    private const val MAX_WORD_REGION = 60_000

    // --- Podstawy ---

    /** Głęboka równość dwóch wartości JSON; null znaczy „wartości nie ma". */
    fun jsonEqual(a: JsonElement?, b: JsonElement?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a is JsonPrimitive && b is JsonPrimitive) {
            if (a.isString != b.isString) return false
            if (a.isString) return a.content == b.content
            val left = a.booleanOrNull
            val right = b.booleanOrNull
            if (left != null || right != null) return left == right
            val x = a.doubleOrNull
            val y = b.doubleOrNull
            if (x != null && y != null) return x == y
            return a.content == b.content
        }
        if (a is JsonArray && b is JsonArray) {
            if (a.size != b.size) return false
            for (i in a.indices) if (!jsonEqual(a[i], b[i])) return false
            return true
        }
        if (a is JsonObject && b is JsonObject) {
            if (a.size != b.size) return false
            for ((key, value) in a) {
                if (!b.containsKey(key)) return false
                if (!jsonEqual(value, b[key])) return false
            }
            return true
        }
        return false
    }

    private fun idOf(value: JsonElement): String? {
        val obj = value as? JsonObject ?: return null
        val id = obj["id"] as? JsonPrimitive ?: return null
        return if (id.isString) id.content else null
    }

    /** Czy tablica to zbiór rzeczy z identyfikatorami (pusta też się liczy). */
    fun isKeyed(values: JsonArray): Boolean {
        val seen = HashSet<String>()
        for (value in values) {
            val id = idOf(value) ?: return false
            if (!seen.add(id)) return false
        }
        return true
    }

    private fun byId(values: JsonArray): LinkedHashMap<String, JsonElement> {
        val map = LinkedHashMap<String, JsonElement>()
        for (value in values) map[idOf(value)!!] = value
        return map
    }

    // --- Różnica ---

    /** Delta z [base] do [target]; null, gdy niczego nie zmieniono. */
    fun diff(base: JsonElement?, target: JsonElement): JsonObject? {
        if (jsonEqual(base, target)) return null
        if (base == null) return replace(target)

        if (base is JsonPrimitive && base.isString && target is JsonPrimitive && target.isString) {
            val before = base.content
            val after = target.content
            if (before.length + after.length <= SHORT_TEXT && '\n' !in before && '\n' !in after) {
                return replace(target)
            }
            return buildJsonObject {
                put("t", buildJsonArray { add(textSplice(before, after)) })
            }
        }

        if (base is JsonObject && target is JsonObject) {
            val changes = LinkedHashMap<String, JsonElement>()
            for (key in base.keys) if (!target.containsKey(key)) changes[key] = JsonNull
            for ((key, value) in target) {
                if (key in FORBIDDEN_KEYS) continue
                val inner = diff(base[key], value)
                if (inner != null) changes[key] = inner
            }
            return if (changes.isEmpty()) null else buildJsonObject { put("o", JsonObject(changes)) }
        }

        if (base is JsonArray && target is JsonArray && isKeyed(base) && isKeyed(target) &&
            (base.isNotEmpty() || target.isNotEmpty())
        ) {
            return diffKeyed(base, target)
        }

        return replace(target)
    }

    private fun replace(value: JsonElement): JsonObject = buildJsonObject { put("$", value) }

    /** Jedna wymiana obejmująca wszystko między wspólnym początkiem i końcem. */
    private fun textSplice(base: String, target: String): JsonArray {
        var start = 0
        val shorter = minOf(base.length, target.length)
        while (start < shorter && base[start] == target[start]) start++
        var endBase = base.length
        var endTarget = target.length
        while (endBase > start && endTarget > start && base[endBase - 1] == target[endTarget - 1]) {
            endBase--
            endTarget--
        }
        return buildJsonArray {
            add(JsonPrimitive(start))
            add(JsonPrimitive(endBase - start))
            add(JsonPrimitive(target.substring(start, endTarget)))
        }
    }

    private fun diffKeyed(base: JsonArray, target: JsonArray): JsonObject? {
        val before = byId(base)
        val after = byId(target)

        val deleted = base.mapNotNull(::idOf).filter { it !in after }

        val updated = LinkedHashMap<String, JsonElement>()
        for (value in target) {
            val id = idOf(value)!!
            val old = before[id] ?: continue
            val inner = diff(old, value)
            if (inner != null) updated[id] = inner
        }

        // Elementy, które zachowują wzajemną kolejność, zostają na miejscu -
        // przesuwać trzeba tylko resztę (najdłuższy podciąg rosnący).
        val position = HashMap<String, Int>()
        base.forEachIndexed { index, value -> position[idOf(value)!!] = index }
        val common = target.mapNotNull(::idOf).filter { it in before }
        val stable = longestIncreasing(common) { position.getValue(it) }

        val placements = ArrayList<JsonElement>()
        var previous: String? = null
        for (value in target) {
            val id = idOf(value)!!
            val after: JsonElement = previous?.let { JsonPrimitive(it) } ?: JsonNull
            if (id !in before) {
                placements.add(buildJsonArray { add(after); add(JsonPrimitive(id)); add(value) })
            } else if (id !in stable) {
                placements.add(buildJsonArray { add(after); add(JsonPrimitive(id)) })
            }
            previous = id
        }

        if (deleted.isEmpty() && updated.isEmpty() && placements.isEmpty()) return null
        val keyed = LinkedHashMap<String, JsonElement>()
        if (deleted.isNotEmpty()) keyed["d"] = JsonArray(deleted.map(::JsonPrimitive))
        if (updated.isNotEmpty()) keyed["u"] = JsonObject(updated)
        if (placements.isNotEmpty()) keyed["p"] = JsonArray(placements)
        return buildJsonObject { put("k", JsonObject(keyed)) }
    }

    private fun longestIncreasing(ids: List<String>, rank: (String) -> Int): Set<String> {
        val tails = ArrayList<Int>()
        val tailIndex = ArrayList<Int>()
        val parent = IntArray(ids.size) { -1 }
        ids.forEachIndexed { index, id ->
            val value = rank(id)
            var low = 0
            var high = tails.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (tails[middle] < value) low = middle + 1 else high = middle
            }
            if (low == tails.size) {
                tails += value
                tailIndex += index
            } else {
                tails[low] = value
                tailIndex[low] = index
            }
            parent[index] = if (low > 0) tailIndex[low - 1] else -1
        }
        val result = HashSet<String>()
        var cursor = if (tails.isNotEmpty()) tailIndex[tails.size - 1] else -1
        while (cursor >= 0) {
            result += ids[cursor]
            cursor = parent[cursor]
        }
        return result
    }

    // --- Nałożenie ---

    /** Nakłada [delta] na [base]. Nie zmienia [base] - oddaje nową wartość. */
    fun applyDelta(base: JsonElement?, delta: JsonObject): JsonElement {
        delta["$"]?.let { return it }

        delta["o"]?.let { changes ->
            val result = LinkedHashMap<String, JsonElement>()
            if (base is JsonObject) result.putAll(base)
            for ((key, change) in changes.jsonObject) {
                if (key in FORBIDDEN_KEYS) continue
                if (change is JsonNull) {
                    result.remove(key)
                } else {
                    result[key] = applyDelta(result[key], change.jsonObject)
                }
            }
            return JsonObject(result)
        }

        delta["t"]?.let { splices ->
            val text = (base as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            val out = StringBuilder()
            var at = 0
            for (splice in splices.jsonArray) {
                val parts = splice.jsonArray
                val from = parts[0].jsonPrimitive.intOrNull ?: 0
                val removed = parts[1].jsonPrimitive.intOrNull ?: 0
                val inserted = parts[2].jsonPrimitive.contentOrNull ?: ""
                val start = maxOf(at, minOf(from, text.length))
                out.append(text, at, start).append(inserted)
                at = minOf(text.length, start + maxOf(0, removed))
            }
            out.append(text, at, text.length)
            return JsonPrimitive(out.toString())
        }

        val change = delta["k"]?.jsonObject ?: return base ?: JsonNull
        val list = ArrayList<JsonElement>()
        if (base is JsonArray) list.addAll(base)
        change["d"]?.jsonArray?.let { ids ->
            val gone = ids.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            list.removeAll { idOf(it)?.let { id -> id in gone } == true }
        }
        change["u"]?.jsonObject?.let { updates ->
            for (i in list.indices) {
                val id = idOf(list[i]) ?: continue
                val inner = updates[id] ?: continue
                list[i] = applyDelta(list[i], inner.jsonObject)
            }
        }
        change["p"]?.jsonArray?.forEach { entry ->
            val placement = entry.jsonArray
            val after = (placement[0] as? JsonPrimitive)?.contentOrNull
            val id = placement[1].jsonPrimitive.content
            val index = list.indexOfFirst { idOf(it) == id }
            val element: JsonElement
            if (placement.size > 2) {
                element = placement[2]
                if (index >= 0) list.removeAt(index)
            } else {
                if (index < 0) return@forEach
                element = list.removeAt(index)
            }
            val at = if (after == null) {
                0
            } else {
                val anchor = list.indexOfFirst { idOf(it) == after }
                if (anchor >= 0) anchor + 1 else list.size
            }
            list.add(at, element)
        }
        return JsonArray(list)
    }

    // --- Scalanie ---

    /**
     * Składa dwie niezależne zmiany tej samej wersji [base]: [mine] (tutaj,
     * jeszcze niewysłane albo zrobione offline) i [theirs] (już na serwerze).
     * null znaczy „tej wartości nie ma" - pola albo elementu.
     */
    fun merge3(base: JsonElement?, mine: JsonElement?, theirs: JsonElement?): JsonElement? {
        if (jsonEqual(mine, theirs)) return mine
        if (jsonEqual(base, mine)) return theirs
        if (jsonEqual(base, theirs)) return mine
        // Jedna strona skasowała, druga zmieniła - zmiana wygrywa.
        if (mine == null) return theirs
        if (theirs == null) return mine

        if (mine is JsonObject && theirs is JsonObject) {
            val original = base as? JsonObject ?: JsonObject(emptyMap())
            val keys = LinkedHashSet<String>()
            keys += theirs.keys
            keys += mine.keys
            keys += original.keys
            val result = LinkedHashMap<String, JsonElement>()
            for (key in keys) {
                if (key in FORBIDDEN_KEYS) continue
                val merged = merge3(original[key], mine[key], theirs[key])
                if (merged != null) result[key] = merged
            }
            return JsonObject(result)
        }

        if (mine is JsonArray && theirs is JsonArray && isKeyed(mine) && isKeyed(theirs) &&
            (base == null || base is JsonNull || (base is JsonArray && isKeyed(base)))
        ) {
            return mergeKeyed(base as? JsonArray ?: JsonArray(emptyList()), mine, theirs)
        }

        if (mine is JsonPrimitive && mine.isString && theirs is JsonPrimitive && theirs.isString) {
            val original = (base as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            return JsonPrimitive(mergeText(original, mine.content, theirs.content))
        }

        // Liczba, kolor, przełącznik, punkty kreski - późniejsza zmiana.
        return mine
    }

    private fun mergeKeyed(base: JsonArray, mine: JsonArray, theirs: JsonArray): JsonArray {
        val original = byId(base)
        val left = byId(mine)
        val right = byId(theirs)

        val merged = LinkedHashMap<String, JsonElement>()
        val ids = LinkedHashSet<String>()
        ids += right.keys
        ids += left.keys
        ids += original.keys
        for (id in ids) {
            val value = merge3(original[id], left[id], right[id])
            if (value != null) merged[id] = value
        }

        val mineReordered = reordered(base, mine)
        val theirsReordered = reordered(base, theirs)
        val (primary, secondary) = if (mineReordered && !theirsReordered) mine to theirs else theirs to mine

        val order = ArrayList<String>()
        val placed = HashSet<String>()
        for (value in primary) {
            val id = idOf(value)!!
            if (id in merged) {
                order += id
                placed += id
            }
        }
        var previous: String? = null
        for (value in secondary) {
            val id = idOf(value)!!
            if (id !in merged) continue
            if (id !in placed) {
                val at = if (previous == null) 0 else order.indexOf(previous) + 1
                order.add(at, id)
                placed += id
            }
            previous = id
        }
        return JsonArray(order.map { merged.getValue(it) })
    }

    private fun reordered(base: JsonArray, values: JsonArray): Boolean {
        val inBase = base.mapNotNull(::idOf).toSet()
        val inValues = values.mapNotNull(::idOf).toSet()
        val fromBase = base.mapNotNull(::idOf).filter { it in inValues }
        val fromValues = values.mapNotNull(::idOf).filter { it in inBase }
        return fromBase != fromValues
    }

    // --- Scalanie tekstu ---

    private class Hunk(var oS: Int, var oE: Int, var aS: Int, var aE: Int, val side: Int = 0)

    /** Dzieli tekst na wiersze razem ze znakiem końca - złączenie oddaje całość. */
    fun splitLines(text: String): List<String> {
        val lines = ArrayList<String>()
        var start = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                lines += text.substring(start, i + 1)
                start = i + 1
            }
        }
        if (start < text.length) lines += text.substring(start)
        return lines
    }

    /** Słowa, odstępy i pojedyncze znaki - na nich druga próba scalenia. */
    fun wordTokens(text: String): List<String> {
        val tokens = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            var j = i + 1
            if (isWordChar(c)) {
                while (j < text.length && isWordChar(text[j])) j++
            } else if (c == ' ' || c == '\t') {
                while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
            }
            tokens += text.substring(i, j)
            i = j
        }
        return tokens
    }

    private fun isWordChar(c: Char): Boolean {
        if (c.isSurrogate()) return true
        if (c.code < 128) return c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z'
        return Character.isLetter(c) || Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER.toInt()
    }

    /** Różnica metodą Myersa; null, gdy ciągi różnią się bardziej niż [maxEdits]. */
    private fun editHunks(n: Int, m: Int, maxEdits: Int, same: (Int, Int) -> Boolean): List<Hunk>? {
        var start = 0
        while (start < n && start < m && same(start, start)) start++
        var endN = n
        var endM = m
        while (endN > start && endM > start && same(endN - 1, endM - 1)) {
            endN--
            endM--
        }
        val lenA = endN - start
        val lenB = endM - start
        if (lenA == 0 && lenB == 0) return emptyList()
        if (lenA == 0 || lenB == 0) return listOf(Hunk(start, endN, start, endM))

        val max = lenA + lenB
        val limit = minOf(max, maxEdits)
        val offset = max + 1
        val frontier = IntArray(2 * max + 3)
        val trace = ArrayList<IntArray>()
        var found = -1

        var d = 0
        while (d <= limit && found < 0) {
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && frontier[offset + k - 1] < frontier[offset + k + 1])) {
                    frontier[offset + k + 1]
                } else {
                    frontier[offset + k - 1] + 1
                }
                var y = x - k
                while (x < lenA && y < lenB && same(start + x, start + y)) {
                    x++
                    y++
                }
                frontier[offset + k] = x
                if (x >= lenA && y >= lenB) {
                    found = d
                    break
                }
                k += 2
            }
            trace += frontier.copyOfRange(offset - d, offset + d + 1)
            d++
        }
        if (found < 0) return null

        // 0 - ten sam element, 1 - skasowany z bazy, 2 - wstawiony z drugiej strony
        val moves = ArrayList<Int>()
        var x = lenA
        var y = lenB
        for (step in found downTo 1) {
            val previous = trace[step - 1]
            fun at(k: Int) = previous[k + step - 1]
            val k = x - y
            val down = k == -step || (k != step && at(k - 1) < at(k + 1))
            val previousK = if (down) k + 1 else k - 1
            val previousX = at(previousK)
            val previousY = previousX - previousK
            val middleX = if (down) previousX else previousX + 1
            while (x > middleX) {
                moves += 0
                x--
                y--
            }
            moves += if (down) 2 else 1
            x = previousX
            y = previousY
        }
        while (x > 0) {
            moves += 0
            x--
        }
        moves.reverse()

        val hunks = ArrayList<Hunk>()
        var i = 0
        var j = 0
        var open: Hunk? = null
        for (move in moves) {
            if (move == 0) {
                open?.let {
                    it.oE = start + i
                    it.aE = start + j
                    hunks += it
                }
                open = null
                i++
                j++
                continue
            }
            if (open == null) open = Hunk(start + i, start + i, start + j, start + j)
            if (move == 1) i++ else j++
        }
        open?.let {
            it.oE = start + i
            it.aE = start + j
            hunks += it
        }
        return hunks
    }

    /** Czy odcinek wchodzi w grupę albo jej dotyka wstawką. */
    private fun touches(hunk: Hunk, groupStart: Int, groupEnd: Int): Boolean {
        if (hunk.oS < groupEnd) return true
        if (hunk.oS != groupEnd) return false
        return hunk.oS == hunk.oE || groupStart == groupEnd
    }

    private fun sideRegion(base: List<String>, side: List<String>, hunks: List<Hunk>, from: Int, to: Int): String {
        val out = StringBuilder()
        var at = from
        for (hunk in hunks) {
            for (i in at until hunk.oS) out.append(base[i])
            for (i in hunk.aS until hunk.aE) out.append(side[i])
            at = hunk.oE
        }
        for (i in at until to) out.append(base[i])
        return out.toString()
    }

    private class Merged(val text: String, val clean: Boolean)

    private fun mergeSequences(
        base: List<String>,
        mine: List<String>,
        theirs: List<String>,
        mineHunks: List<Hunk>,
        theirsHunks: List<Hunk>,
        resolve: (String, String, String) -> Merged,
    ): Merged {
        val all = (mineHunks.map { Hunk(it.oS, it.oE, it.aS, it.aE, 0) } +
            theirsHunks.map { Hunk(it.oS, it.oE, it.aS, it.aE, 1) })
            .sortedWith(compareBy<Hunk>({ it.oS }, { it.oE }, { it.side }))

        val out = StringBuilder()
        var clean = true
        var position = 0
        var index = 0
        while (index < all.size) {
            val group = arrayListOf(all[index])
            var groupStart = all[index].oS
            var groupEnd = all[index].oE
            index++
            while (index < all.size && touches(all[index], groupStart, groupEnd)) {
                groupStart = minOf(groupStart, all[index].oS)
                groupEnd = maxOf(groupEnd, all[index].oE)
                group += all[index]
                index++
            }
            for (i in position until groupStart) out.append(base[i])

            val fromMine = group.filter { it.side == 0 }
            val fromTheirs = group.filter { it.side == 1 }
            val mineText = sideRegion(base, mine, fromMine, groupStart, groupEnd)
            val theirsText = sideRegion(base, theirs, fromTheirs, groupStart, groupEnd)
            when {
                fromTheirs.isEmpty() -> out.append(mineText)
                fromMine.isEmpty() -> out.append(theirsText)
                mineText == theirsText -> out.append(mineText)
                else -> {
                    val baseText = buildString { for (i in groupStart until groupEnd) append(base[i]) }
                    val resolved = resolve(baseText, mineText, theirsText)
                    out.append(resolved.text)
                    if (!resolved.clean) clean = false
                }
            }
            position = groupEnd
        }
        for (i in position until base.size) out.append(base[i])
        return Merged(out.toString(), clean)
    }

    /**
     * Scalanie tekstu z trzech wersji. Wierszami, a gdzie obie strony ruszyły
     * te same wiersze - słowami. Kiedy i słowa się gryzą, zostają obie wersje.
     */
    fun mergeText(base: String, mine: String, theirs: String): String {
        if (mine == theirs) return mine
        if (base == mine) return theirs
        if (base == theirs) return mine

        val baseLines = splitLines(base)
        val mineLines = splitLines(mine)
        val theirsLines = splitLines(theirs)
        val mineHunks = editHunks(baseLines.size, mineLines.size, MAX_LINE_EDITS) { i, j ->
            baseLines[i] == mineLines[j]
        }
        val theirsHunks = editHunks(baseLines.size, theirsLines.size, MAX_LINE_EDITS) { i, j ->
            baseLines[i] == theirsLines[j]
        }
        if (mineHunks == null || theirsHunks == null) return keepBoth(mine, theirs)

        return mergeSequences(baseLines, mineLines, theirsLines, mineHunks, theirsHunks, ::mergeRegion).text
    }

    private fun mergeRegion(base: String, mine: String, theirs: String): Merged {
        if (base.length + mine.length + theirs.length <= MAX_WORD_REGION) {
            val baseWords = wordTokens(base)
            val mineWords = wordTokens(mine)
            val theirsWords = wordTokens(theirs)
            val mineHunks = editHunks(baseWords.size, mineWords.size, MAX_WORD_EDITS) { i, j ->
                baseWords[i] == mineWords[j]
            }
            val theirsHunks = editHunks(baseWords.size, theirsWords.size, MAX_WORD_EDITS) { i, j ->
                baseWords[i] == theirsWords[j]
            }
            if (mineHunks != null && theirsHunks != null) {
                val merged = mergeSequences(baseWords, mineWords, theirsWords, mineHunks, theirsHunks) { _, _, _ ->
                    Merged("", false)
                }
                if (merged.clean) return merged
            }
        }
        return Merged(keepBoth(mine, theirs), false)
    }

    /**
     * Prawdziwy spór. Tekst wielowierszowy zatrzymuje obie wersje - najpierw tę
     * z serwera, pod nią nową. Krótki napis w jednym wierszu bierze późniejszą.
     */
    private fun keepBoth(mine: String, theirs: String): String {
        if ('\n' !in mine && '\n' !in theirs) return mine
        if (theirs.isEmpty()) return mine
        if (mine.isEmpty()) return theirs
        val separator = if (theirs.endsWith("\n")) "" else "\n"
        return theirs + separator + mine
    }
}
