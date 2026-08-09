package wojtoteka.ovh.kajet.asystent

import wojtoteka.ovh.kajet.cloud.CloudAi
import wojtoteka.ovh.kajet.core.ai.AiAssistant
import wojtoteka.ovh.kajet.core.ai.AiNoteKind
import wojtoteka.ovh.kajet.core.ai.AiOutcome
import wojtoteka.ovh.kajet.core.ai.AiTurn

/**
 * Asystent złożony z chmury - tak samo jak KajetServerRunner składa
 * uruchamianie kodu.
 *
 * Edytory znają sam interfejs z modułu core i nic więcej; wiedza o tym, że po
 * drugiej stronie jest HTTP, synchronizacja i konto, kończy się tutaj.
 */
class KajetAi(private val cloud: CloudAi) : AiAssistant {

    override fun available(): Boolean = cloud.available()

    override fun consented(): Boolean = cloud.consented()

    override suspend fun setConsent(consented: Boolean): Boolean = cloud.setConsent(consented)

    override suspend fun ask(
        noteId: String,
        kind: AiNoteKind,
        instruction: String,
    ): AiOutcome = when (val outcome = cloud.ask(noteId, instruction)) {
        // Typ notatki rozpoznaje serwer po tym, co ma w bazie - tutaj jest
        // tylko po to, żeby edytor nie musiał zgadywać, czy asystent go
        // obsłuży, zanim cokolwiek wyśle.
        is CloudAi.Outcome.Changed -> AiOutcome.Changed(outcome.summary, outcome.version)
        is CloudAi.Outcome.Question -> AiOutcome.Question(outcome.question)
        is CloudAi.Outcome.Refused -> AiOutcome.Refused(outcome.reason)
    }

    override fun codeNoteId(path: String): String = cloud.codeNoteId(path)

    override suspend fun history(noteId: String): List<AiTurn> =
        cloud.history(noteId).map { AiTurn(it.request, it.reply) }

    override suspend fun forgetHistory(noteId: String): Boolean = cloud.forgetHistory(noteId)
}

/** Czy asystent obsługuje ten rodzaj notatki. Odręczne są poza zakresem. */
fun aiKindFor(kind: String): AiNoteKind? = when (kind.uppercase()) {
    "TEXT" -> AiNoteKind.TEXT
    "MINDMAP" -> AiNoteKind.MINDMAP
    "CODE" -> AiNoteKind.CODE
    else -> null
}
