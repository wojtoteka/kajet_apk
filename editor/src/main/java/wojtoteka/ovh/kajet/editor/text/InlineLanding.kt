package wojtoteka.ovh.kajet.editor.text

import wojtoteka.ovh.kajet.core.live.LiveMerge

/**
 * Jedna zmiana między dwoma tekstami: w starym [start, oldEnd) zastąpione
 * przez [start, newEnd) z nowego. Wspólny początek i koniec zostają poza nią.
 */
internal class TextSpan(val start: Int, val oldEnd: Int, val newEnd: Int) {
    companion object {
        fun of(old: String, new: String): TextSpan {
            var start = 0
            val shorter = minOf(old.length, new.length)
            while (start < shorter && old[start] == new[start]) start++
            var oldEnd = old.length
            var newEnd = new.length
            while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) {
                oldEnd--
                newEnd--
            }
            return TextSpan(start, oldEnd, newEnd)
        }
    }
}

/*
  Wynik polecenia pisanego w notatce (InlinePrompt), gdy wraca z serwera.

  Trzy wersje tekstu: [before] - notatka w chwili wysłania, ekran - to, co
  jest na nim teraz (człowiek mógł pisać dalej), i wynik asystenta z serwera.
  [merged] mówi, czy edycja na żywo wstawiła już coś z zewnątrz na ekran -
  wtedy zmiana asystenta zwykle już na nim jest.

  Najważniejsze jest tu cofanie (Esc): ma zdjąć zmianę asystenta zawsze
  i w całości. Gdy pisanie człowieka zahacza o tę zmianę, przepada pisanie,
  a nie cofanie - dlatego to nie LiveMerge, które przy sporze zostawia obie
  wersje, jedna pod drugą.
*/
internal object InlineLanding {

    /** Co staje na ekranie i jak wygląda ta sama notatka bez zmiany asystenta. */
    data class Landed(val shown: String, val withoutAi: String)

    fun land(before: String, screen: String, fromAi: String, merged: Boolean): Landed = when {
        merged -> Landed(screen, revert(shown = fromAi, withoutAi = before, current = screen))
        screen == before -> Landed(fromAi, before)
        else -> Landed(combine(before, screen, fromAi), screen)
    }

    /*
      Pisanie człowieka ([mine]) i zmiana asystenta ([theirs]), obie od
      [before]. Najczęściej asystent uzupełnia miejsce, w którym stało
      polecenie, a człowiek pisze dalej tuż za nim - LiveMerge uznałby to za
      spór i zostawił obie wersje jedna pod drugą. Gdy się nie nakładają,
      wchodzą obie w całości, w tym samym miejscu najpierw asystent. Dopiero
      gdy naprawdę na siebie zachodzą, scala LiveMerge, jak przy edycji na żywo.
    */
    private fun combine(before: String, mine: String, theirs: String): String {
        val ai = TextSpan.of(before, theirs)
        val user = TextSpan.of(before, mine)
        val typed = mine.substring(user.start, user.newEnd)
        return when {
            user.start >= ai.oldEnd -> {
                val shift = theirs.length - before.length
                theirs.replaceRange(user.start + shift, user.oldEnd + shift, typed)
            }
            user.oldEnd <= ai.start -> theirs.replaceRange(user.start, user.oldEnd, typed)
            else -> LiveMerge.mergeText(before, mine, theirs)
        }
    }

    /** Ekran po zatrzymaniu: bez zmiany asystenta, z tym, co człowiek napisał. */
    fun stop(before: String, screen: String, fromAi: String, merged: Boolean): String =
        if (merged) revert(shown = fromAi, withoutAi = before, current = screen) else screen

    /**
     * [current] bez zmiany asystenta. [shown] to tekst z tą zmianą, a
     * [withoutAi] ten sam tekst bez niej. To, co człowiek zmienił od [shown]
     * do [current], zostaje - o ile nie dotyka zmiany asystenta.
     */
    fun revert(shown: String, withoutAi: String, current: String): String {
        if (current == shown) return withoutAi
        val ai = TextSpan.of(withoutAi, shown)
        val mine = TextSpan.of(shown, current)
        val typed = current.substring(mine.start, mine.newEnd)
        return when {
            mine.oldEnd <= ai.start -> withoutAi.replaceRange(mine.start, mine.oldEnd, typed)
            mine.start >= ai.newEnd -> {
                val shift = shown.length - withoutAi.length
                withoutAi.replaceRange(mine.start - shift, mine.oldEnd - shift, typed)
            }
            else -> withoutAi
        }
    }
}
