package wojtoteka.ovh.kajet.code

import wojtoteka.ovh.kajet.core.model.CodeLanguage

/**
 * Wynik jednego uruchomienia programu.
 *
 * Trzymamy osobno to, co program wypisał, i to, co poszło źle, bo w panelu
 * pod edytorem są to dwie różne zakładki. Czas mierzymy po naszej stronie,
 * więc przy uruchomieniu przez sieć zawiera on także drogę tam i z powrotem.
 */
data class WynikUruchomienia(
    val jezyk: CodeLanguage,
    val wyjscie: String,
    val bledy: String,
    val kodWyjscia: Int?,
    val czasMs: Long,
    val przezSiec: Boolean,
) {
    val udane: Boolean get() = kodWyjscia == 0 && bledy.isBlank()
}

/** Uruchomienie się nie powiodło. Komunikat mówi, co zrobić dalej. */
class BladUruchomienia(
    val komunikatDlaUzytkownika: String,
    przyczyna: Throwable? = null,
) : Exception(komunikatDlaUzytkownika, przyczyna)

/**
 * Sposób uruchamiania kodu. Reszta aplikacji zna tylko ten interfejs
 * i nie wie, czy program liczy się na tablecie, czy na serwerze.
 */
interface CodeRunner {

    /** Nazwa pokazywana użytkownikowi, na przykład "na tablecie" albo "na serwerze". */
    val nazwa: String

    /** Języki, które ten sposób obsługuje. */
    fun obsluguje(jezyk: CodeLanguage): Boolean

    /** Czy do uruchomienia potrzebny jest internet. */
    val wymagaInternetu: Boolean

    /**
     * Uruchamia kod i czeka na wynik.
     * Rzuca [BladUruchomienia], kiedy nie da się nawet zacząć,
     * na przykład przy braku internetu.
     */
    suspend fun uruchom(
        jezyk: CodeLanguage,
        kod: String,
        wejscie: String,
        nazwaPliku: String,
    ): WynikUruchomienia
}

/**
 * Wybiera sposób uruchomienia dla danego języka.
 *
 * Pierwszeństwo ma to, co działa bez internetu. Dopiero gdy takiego sposobu
 * nie ma, sięgamy po serwer. Dzięki temu Python nigdy nie idzie przez sieć.
 */
class RejestrUruchamiania(private val sposoby: List<CodeRunner>) {

    fun dla(jezyk: CodeLanguage): CodeRunner? =
        sposoby.firstOrNull { it.obsluguje(jezyk) && !it.wymagaInternetu }
            ?: sposoby.firstOrNull { it.obsluguje(jezyk) }

    fun offline(jezyk: CodeLanguage): Boolean =
        sposoby.any { it.obsluguje(jezyk) && !it.wymagaInternetu }
}
