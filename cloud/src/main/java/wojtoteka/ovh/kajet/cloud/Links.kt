package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Strony Kajetu otwierane z aplikacji.
 *
 * Adresy stoją TUTAJ i nigdzie indziej. Wszystkie wychodzą z [AccountStore.SERVER_URL],
 * więc przeniesienie serwera pod inny adres to jedna zmiana, a nie polowanie po
 * plikach - i nie ma jak się zdarzyć, że aplikacja wysyła notatki na jeden
 * serwer, a regulamin pokazuje z drugiego.
 *
 * Adresy są gołe - bez żadnego `?lang=`. Oba dokumenty mają w jednym pliku
 * wersję polską i angielską, a przełącznik PL/EN stoi na wierzchu przez cały
 * czas czytania, więc język wybiera się na miejscu, tak jak na stronie.
 */
object KajetLinks {
    /** Regulamin. */
    fun terms(): String = AccountStore.SERVER_URL + "/terms"

    /** Polityka prywatności. */
    fun privacy(): String = AccountStore.SERVER_URL + "/privacy"

    /**
     * Strona z plikiem aplikacji do pobrania.
     *
     * Do komunikatu o nowej wersji adres przychodzi Z ODPOWIEDZI serwera —
     * to on wie, gdzie leży plik, i może kiedyś podać co innego. Ten stoi tu na
     * wypadek, gdyby odpowiedź go nie niosła, oraz dla ustawień, gdzie nie ma
     * czego pytać.
     */
    fun download(): String = AccountStore.SERVER_URL + "/download"
}

/** Co się stało przy próbie otwarcia adresu. */
enum class LinkOutcome {
    OPENED,

    /** Urządzenie nie jest w żadnej sieci - przeglądarka pokazałaby tylko błąd. */
    NO_NETWORK,

    /** Nie ma czym otworzyć strony: urządzenie bez przeglądarki. */
    NO_BROWSER,
}

/**
 * Otwiera adres w przeglądarce.
 *
 * Najpierw Custom Tabs: strona otwiera się wtedy NA Kajecie, w jego barwach i
 * z powrotem jednym gestem, zamiast przerzucać człowieka do osobnej aplikacji.
 * Gdy żadna przeglądarka tego nie umie, idzie zwykły `ACTION_VIEW`.
 *
 * Brak sieci sprawdzamy PRZED otwarciem. Bez tego Kajet wypuszczałby człowieka
 * do przeglądarki po to tylko, żeby zobaczył jej własny komunikat o błędzie i
 * nie wiedział, czy to Kajet się zepsuł, czy internet. Nic tu nie ma prawa
 * wywrócić aplikacji - stąd odpowiedź zamiast wyjątku.
 */
fun openLink(context: Context, url: String): LinkOutcome {
    if (!hasNetwork(context)) return LinkOutcome.NO_NETWORK

    val uri = Uri.parse(url)

    val viaTabs = runCatching {
        val tabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tabs.launchUrl(context, uri)
    }
    if (viaTabs.isSuccess) return LinkOutcome.OPENED

    val plain = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    return if (plain.isSuccess) LinkOutcome.OPENED else LinkOutcome.NO_BROWSER
}

/**
 * Czy urządzenie jest w sieci, która obiecuje internet.
 *
 * Pytamy o samą możliwość, nie o potwierdzone połączenie: sieć z bramką
 * logowania (hotel, szkoła) nie ma jeszcze VALIDATED, a strona i tak się w niej
 * otworzy, bo bramka pokaże swoją. Zgadywanie w drugą stronę - odmawianie
 * otwarcia - byłoby gorsze niż przepuszczenie.
 */
private fun hasNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val network = manager.activeNetwork ?: return false
    val abilities = manager.getNetworkCapabilities(network) ?: return false
    return abilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
