package wojtoteka.ovh.kajet.cloud

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

        return when (val response = client.runCode(language, code, input)) {
            is CloudClient.Result.Ok -> Outcome.Ready(response.data)
            is CloudClient.Result.Error -> Outcome.Refused(response.message)
        }
    }

    sealed interface Outcome {
        data class Ready(val result: CodeResult) : Outcome
        data class Refused(val reason: String) : Outcome
    }
}
