package wojtoteka.ovh.kajet

import android.app.Application
import android.content.Context
import wojtoteka.ovh.kajet.storage.Magazyn
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki

/**
 * Wszystkie wspólne obiekty aplikacji w jednym miejscu.
 *
 * Bez biblioteki do wstrzykiwania zależności. Przy kilku obiektach
 * ręczne złożenie jest krótsze i widać z niego, co od czego zależy.
 */
class Kontener(context: Context) {
    val ustawienia: MagazynUstawien = Magazyn.ustawienia(context)

    val biblioteka: RepozytoriumBiblioteki = Magazyn.biblioteka(context, ustawienia)
}

class KajetApp : Application() {
    lateinit var kontener: Kontener
        private set

    override fun onCreate() {
        super.onCreate()
        kontener = Kontener(this)
    }
}

val Context.kontener: Kontener
    get() = (applicationContext as KajetApp).kontener
