package wojtoteka.ovh.kajet.storage

import android.content.Context
import wojtoteka.ovh.kajet.storage.indeks.BazaIndeksu

/**
 * Jedyne wejście do warstwy plików od strony aplikacji.
 *
 * Baza indeksu zostaje schowana w tym module, żeby reszta programu
 * nie musiała nic wiedzieć o Room ani o tym, że indeks w ogóle istnieje.
 */
object Magazyn {

    fun ustawienia(context: Context): MagazynUstawien = MagazynUstawien(context.applicationContext)

    fun biblioteka(context: Context, ustawienia: MagazynUstawien): RepozytoriumBiblioteki =
        RepozytoriumBiblioteki(
            context = context.applicationContext,
            ustawienia = ustawienia,
            dao = BazaIndeksu.pobierz(context).indeks(),
        )
}
