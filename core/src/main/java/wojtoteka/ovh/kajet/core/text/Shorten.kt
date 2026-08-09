package wojtoteka.ovh.kajet.core.text

/**
 * Skraca długi ciąg wielokropkiem W ŚRODKU: „poczatek…koncowka".
 *
 * Środek, nie koniec, bo w adresie e-mail, nazwie urządzenia czy adresie
 * serwera to końcówka mówi, co to jest — ucięcie ogona zostawiałoby same
 * nieodróżnialne początki. Głowa dostaje więcej niż ogon: początek czyta się
 * pierwszy i po nim człowiek poznaje SWÓJ adres.
 *
 * Do jednowierszowych miejsc przy ikonach. Tam, gdzie tekst może się po
 * prostu zawinąć, zawijanie jest lepsze — nic nie ginie.
 */
fun shortenMiddle(text: String, longest: Int): String {
    if (text.length <= longest || longest < 5) return text
    val head = (longest - 1) * 3 / 5
    val tail = longest - 1 - head
    return text.take(head) + "…" + text.takeLast(tail)
}
