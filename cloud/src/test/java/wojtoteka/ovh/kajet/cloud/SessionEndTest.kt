package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kiedy odpowiedź serwera znaczy koniec sesji.
 *
 * Powody po lewej stronie są dokładnie tymi, które wypisuje serwer
 * w src/lib/api.ts (`missing`, `invalid`, `expired`, `blocked`) i w trasach
 * notatek oraz folderów (`not-yours`). Test pilnuje granicy między jednym
 * a drugim: pomyłka w tę stronę wyrzuca człowieka z konta przy dotknięciu
 * cudzej notatki, a w tamtą - zostawia w Ustawieniach „Zalogowano jako…"
 * po wylogowaniu wszystkich sesji przez stronę.
 */
class SessionEndTest {

    @Test
    fun `401 zawsze konczy sesje`() {
        for (code in listOf("missing", "invalid", "expired", "", "cokolwiek")) {
            assertThat(CloudClient.sessionDead(401, code, tokenUsed = true)).isTrue()
        }
    }

    @Test
    fun `403 konczy sesje tylko z powodu o tozsamosci`() {
        assertThat(CloudClient.sessionDead(403, "blocked", tokenUsed = true)).isTrue()
        assertThat(CloudClient.sessionDead(403, "invalid", tokenUsed = true)).isTrue()
    }

    @Test
    fun `cudza notatka nie wylogowuje`() {
        // 403 „not-yours" pada przy notatce i folderze kogoś innego. Token
        // działa dalej, więc sesja ma zostać nietknięta.
        assertThat(CloudClient.sessionDead(403, "not-yours", tokenUsed = true)).isFalse()
        assertThat(CloudClient.sessionDead(403, "", tokenUsed = true)).isFalse()
        assertThat(CloudClient.sessionDead(403, "denied", tokenUsed = true)).isFalse()
    }

    @Test
    fun `bledne haslo przy logowaniu nie rusza sesji`() {
        // Logowanie idzie bez tokenu: 401 znaczy tam „złe hasło", nie „koniec
        // sesji". Bez tego wpisanie złego hasła gasiłoby konto zalogowane
        // na tym urządzeniu.
        assertThat(CloudClient.sessionDead(401, "bad-credentials", tokenUsed = false)).isFalse()
        assertThat(CloudClient.sessionDead(403, "denied", tokenUsed = false)).isFalse()
    }

    @Test
    fun `zwykle bledy nie ruszaja sesji`() {
        for (status in listOf(404, 409, 429, 500, 503, 507)) {
            assertThat(CloudClient.sessionDead(status, "", tokenUsed = true)).isFalse()
        }
    }

    @Test
    fun `powody odmowy tozsamosci zgadzaja sie z serwerem`() {
        assertThat(CloudClient.AUTH_REASONS)
            .containsExactly("missing", "invalid", "expired", "blocked")
    }
}
