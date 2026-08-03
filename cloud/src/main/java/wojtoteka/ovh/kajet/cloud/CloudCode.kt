package wojtoteka.ovh.kajet.cloud

class CloudCode(
    private val account: AccountStore,
    private val client: CloudClient,
) {

    fun ready(): Boolean = account.isSignedIn()

    fun refusalReason(): String? = when {
        !account.isSignedIn() ->
            "Uruchamianie na serwerze wymaga konta. Zaloguj się w ustawieniach, " +
                "w sekcji „Konto w chmurze”."
        !client.hasNetwork() ->
            "Nie ma internetu, a ten język liczy się na serwerze. " +
                "Kod jest zapisany i uruchomisz go po połączeniu z siecią."
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
