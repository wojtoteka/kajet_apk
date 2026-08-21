package wojtoteka.ovh.kajet.awaria

import android.content.Context
import wojtoteka.ovh.kajet.cloud.CrashReporter

/**
 * Dosyłanie zaległych raportów o awariach.
 *
 * Wysyłka NIE dzieje się w chwili awarii. Wtedy proces właśnie się kończy,
 * wątek główny jest martwy, a czekanie na odpowiedź serwera opóźniałoby tylko
 * pokazanie ekranu błędu - i tak czy owak nie doszłaby, gdyby akurat nie było
 * sieci. Raport idzie na dysk od razu, a stąd na serwer przy najbliższym
 * uruchomieniu Kajetu.
 *
 * Wołać poza wątkiem głównym.
 */
object CrashUpload {

    fun sendPending(context: Context) {
        for (file in CrashLog.unsentCrashes(context)) {
            val report = runCatching { file.readText() }.getOrNull()
            if (report.isNullOrBlank()) {
                // Pusty albo nieczytelny plik nie ma czego nieść. Odhaczamy,
                // żeby nie wracał przy każdym uruchomieniu.
                CrashLog.markSent(file)
                continue
            }

            val facts = CrashLog.facts(report)
            val outcome = CrashReporter.send(
                report = report,
                appVersion = facts.appVersion,
                versionCode = facts.versionCode,
                device = facts.device,
                android = facts.android,
                thread = facts.thread,
            )

            when (outcome) {
                CrashReporter.Outcome.SENT,
                CrashReporter.Outcome.REJECTED,
                -> CrashLog.markSent(file)

                // Nie ma sieci albo serwer nie odpowiada. Kolejne raporty
                // trafiłyby na to samo, więc kończymy i wracamy do nich przy
                // następnym uruchomieniu - zamiast czekać po osiem sekund na
                // każdy z pięciu plików.
                CrashReporter.Outcome.RETRY -> return
            }
        }
    }
}
