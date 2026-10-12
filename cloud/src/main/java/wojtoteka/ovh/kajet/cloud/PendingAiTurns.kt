package wojtoteka.ovh.kajet.cloud

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer

/**
 * Rozmowa z KajetAI z czasu wstrzymanej synchronizacji.
 *
 * Serwer jej wtedy nie zapamiętuje (ai-edit z `detached`), więc trzyma ją
 * urządzenie - przy każdej notatce osobno, od najstarszej wymiany. Po
 * wznowieniu [CloudAi.sendPendingTurns] dosyła ją na serwer i dopiero wtedy
 * wpis znika. Leży w SharedPreferences, więc przeżywa zamknięcie aplikacji.
 */
class PendingAiTurns(context: Context) {

    private val prefs = context.getSharedPreferences("kajet-ai-pending", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(AiTurn.serializer())

    @Synchronized
    fun of(noteId: String): List<AiTurn> {
        val raw = prefs.getString(noteId, null) ?: return emptyList()
        return runCatching { CloudClient.json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(noteId: String, turn: AiTurn) {
        // Tyle, ile i tak zmieści się w jednym dosłaniu na serwer.
        val turns = (of(noteId) + turn).takeLast(MAX_TURNS)
        prefs.edit().putString(noteId, CloudClient.json.encodeToString(serializer, turns)).apply()
    }

    /** Notatki, przy których coś czeka. */
    @Synchronized
    fun noteIds(): List<String> = prefs.all.keys.toList()

    /** Zdejmuje [sent] pierwszych wymian - tyle, ile serwer właśnie przyjął. */
    @Synchronized
    fun dropFirst(noteId: String, sent: Int) {
        val rest = of(noteId).drop(sent)
        if (rest.isEmpty()) {
            prefs.edit().remove(noteId).apply()
        } else {
            prefs.edit().putString(noteId, CloudClient.json.encodeToString(serializer, rest)).apply()
        }
    }

    @Synchronized
    fun forget(noteId: String) {
        prefs.edit().remove(noteId).apply()
    }

    /**
     * Dosyła wszystko, co czeka - wołane na końcu udanej synchronizacji, gdy
     * notatki są już na serwerze. Nieudane zostaje na następny raz, chyba że
     * serwer powie wprost, że tej rozmowy nie przyjmie nigdy.
     */
    suspend fun sendAll(client: CloudClient) {
        for (noteId in noteIds()) {
            val turns = of(noteId)
            if (turns.isEmpty()) {
                forget(noteId)
                continue
            }
            when (val response = client.aiAppendHistory(noteId, turns)) {
                is CloudClient.Result.Ok -> dropFirst(noteId, turns.size)
                is CloudClient.Result.Error ->
                    // Cudza notatka albo konto bez asystenta - czekanie nic nie da.
                    if (response.code == "not-yours" || response.code == "no-route") forget(noteId)
            }
        }
    }

    private companion object {
        const val MAX_TURNS = 50
    }
}
