package wojtoteka.ovh.kajet.cloud

import wojtoteka.ovh.kajet.core.text.codeInputTooLong
import wojtoteka.ovh.kajet.core.text.codeTooLong
import wojtoteka.ovh.kajet.core.text.words

class CloudCode(
    private val account: AccountStore,
    private val client: CloudClient,
) {

    fun ready(): Boolean = account.isSignedIn()

    fun refusalReason(): String? = when {
        !account.isSignedIn() ->
            words.runOnServerNeedsAccount
        !client.hasNetwork() ->
            words.runOnServerNeedsInternet
        else -> null
    }

    suspend fun run(language: String, code: String, input: String): Outcome {
        refusalReason()?.let { return Outcome.Refused(it) }
        tooLongReason(code, input)?.let { return Outcome.Refused(it) }

        return when (val response = client.runCode(language, code, input)) {
            is CloudClient.Result.Ok -> Outcome.Ready(response.data)
            is CloudClient.Result.Error -> Outcome.Refused(response.message)
        }
    }

    /**
     * Granice, które serwer i tak postawi — tyle że po swojemu.
     *
     * Nadmiarowa wysyłka wraca stamtąd jako 400 ze zdaniem „Podaj język
     * i kod", bo tam wszystkie odrzucone kształty żądania mają jeden
     * komunikat. Uczeń czytałby więc, że czegoś nie podał, choć podał za
     * dużo. Lepiej powiedzieć to tutaj i nie zajmować łącza.
     */
    private fun tooLongReason(code: String, input: String): String? = when {
        code.length > MAX_CODE_CHARS -> words.codeTooLong(MAX_CODE_CHARS)
        input.length > MAX_INPUT_CHARS -> words.codeInputTooLong(MAX_INPUT_CHARS)
        else -> null
    }

    sealed interface Outcome {
        data class Ready(val result: CodeResult) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    private companion object {
        // Za tymi liczbami stoi src/app/api/v1/code/route.ts (schemat żądania).
        const val MAX_CODE_CHARS = 200_000
        const val MAX_INPUT_CHARS = 100_000
    }
}
