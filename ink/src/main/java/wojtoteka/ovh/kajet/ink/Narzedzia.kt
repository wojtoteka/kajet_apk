package wojtoteka.ovh.kajet.ink

import wojtoteka.ovh.kajet.core.model.InkTool

/** Narzędzie wybrane w pasku bocznym edytora notatki odręcznej. */
enum class Narzedzie {
    PIORO,
    ZAKRESLACZ,
    GUMKA_FRAGMENT,
    GUMKA_KRESKA,
    LASSO,
    LINIJKA,
    ;

    val nazwaPl: String
        get() = when (this) {
            PIORO -> "Pióro"
            ZAKRESLACZ -> "Zakreślacz"
            GUMKA_FRAGMENT -> "Gumka"
            GUMKA_KRESKA -> "Gumka do całej kreski"
            LASSO -> "Zaznaczanie"
            LINIJKA -> "Linijka"
        }

    val opisPl: String
        get() = when (this) {
            PIORO -> "Kreska grubieje tam, gdzie mocniej naciskasz rysikiem."
            ZAKRESLACZ -> "Szeroka jasna kreska, kładzie się pod tekstem."
            GUMKA_FRAGMENT -> "Wyciera tylko to, po czym przejedziesz."
            GUMKA_KRESKA -> "Kasuje całą kreskę, której dotkniesz."
            LASSO -> "Obrysuj fragment, żeby go przesunąć albo skasować."
            LINIJKA -> "Prostuje kreskę do linii. Blisko poziomu dociąga do równej."
        }

    val pisze: Boolean get() = this == PIORO || this == ZAKRESLACZ || this == LINIJKA

    val gumka: Boolean get() = this == GUMKA_FRAGMENT || this == GUMKA_KRESKA

    fun doModelu(): InkTool = if (this == ZAKRESLACZ) InkTool.ZAKRESLACZ else InkTool.PIORO
}

/** Ustawienia pisaka trzymane w edytorze. Osobne dla pióra i dla zakreślacza. */
data class UstawieniaPisaka(
    val kolorPiora: Int,
    val gruboscPiora: Float = 2.0f,
    val kolorZakreslacza: Int,
    val gruboscZakreslacza: Float = 16f,
    /** Promień gumki w jednostkach strony. */
    val promienGumki: Float = 12f,
)
