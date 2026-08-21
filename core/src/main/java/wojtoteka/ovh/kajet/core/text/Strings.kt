package wojtoteka.ovh.kajet.core.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Język aplikacji.
 *
 * Zasada jest prosta: bez własnego wyboru Kajet mówi tak, jak ustawiony jest
 * system - po polsku, gdy system jest po polsku, po angielsku w każdym innym
 * przypadku. Kto chce inaczej, przestawia to w ustawieniach i wybór zostaje.
 *
 * Nie idziemy przez `res/values-*` i systemowe zasoby, choć to zwykła droga na
 * Androidzie. Powód: cały ekran Kajetu jest w Compose, a napisy siedzą przy
 * kodzie, który je pokazuje (`labelPl` przy wyliczeniach, teksty wprost
 * w widokach). Przeniesienie ich do XML-a rozbiłoby to na dwa miejsca, a zmiana
 * języka wymagałaby przeładowania ekranu. Tutaj wystarczy [LocalStrings]
 * i napisy zmieniają się od razu.
 */
enum class AppLanguage(val id: String) {
    /** Tak, jak ma ustawiony system. */
    SYSTEM("system"),
    POLISH("pl"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Który zestaw napisów obowiązuje przy tym wyborze i tym ustawieniu systemu.
 *
 * [systemLanguage] to dwuliterowy kod z systemu ("pl", "en", "de"...).
 * Wszystko poza polskim dostaje angielski - to najbliższe „drugiego języka",
 * jaki ktokolwiek zrozumie.
 */
fun stringsFor(choice: AppLanguage, systemLanguage: String): Strings = when (choice) {
    AppLanguage.POLISH -> PolishStrings
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SYSTEM -> if (systemLanguage.lowercase() == "pl") PolishStrings else EnglishStrings
}

/** Język systemu urządzenia jako dwuliterowy kod. */
fun systemLanguage(): String = Locale.getDefault().language

val LocalStrings = staticCompositionLocalOf<Strings> { PolishStrings }

/**
 * Napisy dla kodu, który nie jest widokiem.
 *
 * [LocalStrings] działa tylko wewnątrz Compose, a komunikaty o błędach powstają
 * także w modelach widoku, w synchronizacji i przy czytaniu plików - a lądują
 * potem na ekranie. Trzymanie wyboru języka osobno w każdym z tych miejsc
 * znaczyłoby przekazywanie go przez każdą fabrykę i każde repozytorium.
 *
 * Kajet ma jedno okno i jeden wybór języka naraz, więc wystarczy jedno miejsce.
 * Ustawia je MainActivity przy każdej zmianie ustawień, jeszcze zanim
 * cokolwiek zdąży się nie udać.
 */
object CurrentStrings {
    @Volatile
    var value: Strings = PolishStrings
}

/** Napisy poza Compose. W widoku używa się `LocalStrings.current`. */
val words: Strings get() = CurrentStrings.value
