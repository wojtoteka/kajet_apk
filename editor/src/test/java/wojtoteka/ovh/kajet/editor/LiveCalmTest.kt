package wojtoteka.ovh.kajet.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wojtoteka.ovh.kajet.editor.LiveCalm.Shown
import wojtoteka.ovh.kajet.storage.LiveStatus

/*
  Napis łącza w pasku notatki: tylko „Połączono" albo „Offline", bez migania
  przy krótkim zerwaniu strumienia.
*/
class LiveCalmTest {

    @Test
    fun `krotkie zerwanie i ponowne laczenie zostawia polaczono`() {
        var shown = LiveCalm.shown(Shown.NONE, LiveStatus.LIVE, 0)
        shown = LiveCalm.shown(shown, LiveStatus.OFFLINE, 0)
        assertThat(shown).isEqualTo(Shown.CONNECTED)
        shown = LiveCalm.shown(shown, LiveStatus.CONNECTING, 1_000)
        assertThat(shown).isEqualTo(Shown.CONNECTED)
        shown = LiveCalm.shown(shown, LiveStatus.LIVE, 0)

        assertThat(shown).isEqualTo(Shown.CONNECTED)
    }

    @Test
    fun `dluzszy brak lacza pokazuje offline mimo prob laczenia`() {
        // Czas liczy się od zerwania, nie od każdej kolejnej próby.
        val shown = LiveCalm.shown(Shown.CONNECTED, LiveStatus.CONNECTING, LiveCalm.DROP_GRACE_MS)

        assertThat(shown).isEqualTo(Shown.OFFLINE)
        assertThat(LiveCalm.recheckIn(Shown.CONNECTED, LiveStatus.OFFLINE, 2_000)).isEqualTo(4_000)
    }

    @Test
    fun `offline zostaje do prawdziwego polaczenia`() {
        assertThat(LiveCalm.shown(Shown.OFFLINE, LiveStatus.CONNECTING, 0)).isEqualTo(Shown.OFFLINE)
        assertThat(LiveCalm.shown(Shown.OFFLINE, LiveStatus.LIVE, 0)).isEqualTo(Shown.CONNECTED)
    }

    @Test
    fun `bez sieci od otwarcia od razu offline`() {
        assertThat(LiveCalm.shown(Shown.NONE, LiveStatus.OFFLINE, 0)).isEqualTo(Shown.OFFLINE)
    }

    @Test
    fun `pierwsze laczenie chwile nic nie mowi, potem offline`() {
        assertThat(LiveCalm.shown(Shown.NONE, LiveStatus.CONNECTING, 500)).isEqualTo(Shown.NONE)
        assertThat(LiveCalm.shown(Shown.NONE, LiveStatus.CONNECTING, LiveCalm.CONNECT_GRACE_MS))
            .isEqualTo(Shown.OFFLINE)
    }

    @Test
    fun `nigdy nie pokazuje laczenia`() {
        val all = LiveStatus.entries.flatMap { status ->
            Shown.entries.map { LiveCalm.shown(it, status, 0) }
        }
        assertThat(all.toSet()).containsAnyOf(Shown.CONNECTED, Shown.OFFLINE)
        assertThat(Shown.entries).hasSize(3)
    }
}
