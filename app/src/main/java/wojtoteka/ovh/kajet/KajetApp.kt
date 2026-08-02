package wojtoteka.ovh.kajet

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import wojtoteka.ovh.kajet.code.RejestrUruchamiania
import wojtoteka.ovh.kajet.code.SerwerKodu
import wojtoteka.ovh.kajet.kod.PythonNaTablecie
import wojtoteka.ovh.kajet.storage.Magazyn
import wojtoteka.ovh.kajet.storage.MagazynUstawien
import wojtoteka.ovh.kajet.storage.RepozytoriumBiblioteki
import wojtoteka.ovh.kajet.storage.UstawieniaKajetu

/**
 * Wszystkie wspólne obiekty aplikacji w jednym miejscu.
 *
 * Bez biblioteki do wstrzykiwania zależności. Przy kilku obiektach
 * ręczne złożenie jest krótsze i widać z niego, co od czego zależy.
 */
class Kontener(context: Context) {

    val ustawienia: MagazynUstawien = Magazyn.ustawienia(context)

    val biblioteka: RepozytoriumBiblioteki = Magazyn.biblioteka(context, ustawienia)

    private val zakres = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Adres serwera trzymany pod ręką, żeby uruchomienie nie czekało na odczyt ustawień. */
    private val adresSerwera = ustawienia.ustawienia
        .map { it.adresSerweraKodu }
        .stateIn(zakres, SharingStarted.Eagerly, UstawieniaKajetu.DOMYSLNY_SERWER_KODU)

    /**
     * Sposoby uruchamiania kodu. Python liczy się na tablecie, reszta na serwerze.
     * Kolejność ma znaczenie: pierwszy pasujący bez internetu wygrywa.
     */
    val uruchamianie: RejestrUruchamiania = RejestrUruchamiania(
        listOf(
            PythonNaTablecie(context),
            SerwerKodu(context) { adresSerwera.value },
        ),
    )
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
