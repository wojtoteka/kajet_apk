package wojtoteka.ovh.kajet.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import wojtoteka.ovh.kajet.core.text.serverError
import wojtoteka.ovh.kajet.core.text.words

class CloudClient(
    private val context: Context,
    private val account: AccountStore,
) : CloudTransport {

    sealed interface Result<out T> {
        data class Ok<T>(val data: T) : Result<T>
        data class Error(
            val message: String,
            val worthRetrying: Boolean = false,
            val mustSignIn: Boolean = false,
            // 404 — starszy serwer może nie znać nowego punktu (np. folderów);
            // wtedy tę część synchronizacji po prostu się pomija.
            val notFound: Boolean = false,
            /** Powód podany przez serwer w polu `error`, np. „not-yours". */
            val code: String = "",
        ) : Result<Nothing>
    }

    override fun hasNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- Sign-in ---

    suspend fun signIn(
        email: String,
        password: String,
        device: String,
    ): Result<SignInResponse> = request(
        url = "${account.serverUrl()}/api/v1/signin",
        method = "POST",
        body = json.encodeToString(SignInRequest(email, password, device)),
        withToken = false,
    )

    /** Starts browser login (Google or password on the website). */
    suspend fun createDeviceChallenge(device: String): Result<DeviceChallenge> = request(
        url = "${account.serverUrl()}/api/v1/signin/device",
        method = "POST",
        body = json.encodeToString(DeviceChallengeRequest(device)),
        withToken = false,
    )

    /**
     * Polls until the website challenge is approved. Pending answers use HTTP 202
     * and decode to [DevicePollResponse] with status "pending".
     */
    suspend fun pollDeviceChallenge(code: String): Result<DevicePollResponse> = request(
        url = "${account.serverUrl()}/api/v1/signin/device?code=${java.net.URLEncoder.encode(code, "UTF-8")}",
        method = "GET",
        withToken = false,
        acceptPending = true,
    )

    suspend fun accountState(): Result<AccountState> = request(
        url = "${account.serverUrl()}/api/v1/account",
        method = "GET",
    )

    // --- Notes ---

    override suspend fun fetchChanges(
        since: Long,
        afterId: String?,
        withContent: Boolean,
    ): Result<ChangesResponse> {
        val params = buildString {
            append("since=$since")
            if (!afterId.isNullOrBlank()) append("&afterId=").append(java.net.URLEncoder.encode(afterId, "UTF-8"))
            if (!withContent) append("&withContent=no")
            // Bez tego serwer oddaje tylko zwykłe notatki. Pliki z kodem jadą
            // jako notatki CODE i dostają je wyłącznie aplikacje, które o nie
            // poproszą — starsze wersje nie umiały ich odczytać.
            append("&kinds=all")
        }
        return request(
            url = "${account.serverUrl()}/api/v1/notes?$params",
            method = "GET",
        )
    }

    override suspend fun sendNote(note: OutgoingNote): Result<SaveResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes",
        method = "PUT",
        body = json.encodeToString(note),
    )

    /** Trwałe kasowanie na serwerze — z bazy, z dysku, razem z załącznikami. */
    override suspend fun deleteNote(noteId: String): Result<SaveResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId",
        method = "DELETE",
    )

    /**
     * Nagrobki: identyfikatory notatek skasowanych na zawsze od podanej chwili.
     *
     * Zwykłe pobranie zmian tego nie powie — wiersza skasowanej notatki po
     * prostu nie ma, więc „co się zmieniło od…" nigdy jej nie wymieni.
     * Starszy serwer nie zna tego punktu i odpowiada 404; wtedy zostaje
     * porównanie pełnego spisu identyfikatorów.
     */
    override suspend fun fetchDeleted(since: Long, afterId: String?): Result<DeletedResponse> {
        val params = buildString {
            append("since=$since")
            if (!afterId.isNullOrBlank()) {
                append("&afterId=").append(java.net.URLEncoder.encode(afterId, "UTF-8"))
            }
        }
        return request(
            url = "${account.serverUrl()}/api/v1/sync/deleted?$params",
            method = "GET",
        )
    }

    // --- Folders ---

    /** Cała struktura folderów konta. Starszy serwer odpowiada 404. */
    override suspend fun fetchFolders(): Result<FoldersResponse> = request(
        url = "${account.serverUrl()}/api/v1/folders",
        method = "GET",
    )

    override suspend fun sendFolder(folder: OutgoingFolder): Result<FolderSaveResponse> = request(
        url = "${account.serverUrl()}/api/v1/folders",
        method = "PUT",
        body = json.encodeToString(folder),
    )

    override suspend fun deleteFolder(folderId: String): Result<FolderSaveResponse> = request(
        url = "${account.serverUrl()}/api/v1/folders/$folderId",
        method = "DELETE",
    )

    /**
     * Wszystkie udostępnienia notatki — do panelu „Udostępnianie".
     * Serwer oddaje też starsze pole `links`, ale panel czyta pełną listę.
     */
    suspend fun listShares(noteId: String): Result<ShareListResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/share",
        method = "GET",
    )

    /**
     * Nowe udostępnienie notatki.
     *
     * Serwer oddaje ten sam odnośnik przy kolejnych prośbach o zwykły link
     * o tych samych prawach, więc udostępnienie dwa razy nie mnoży wpisów.
     * Z adresem e-mail udostępnienie jest imienne — serwer sam wysyła
     * wiadomość i mówi w `mailSent`, czy wyszła.
     */
    suspend fun createShare(
        noteId: String,
        canEdit: Boolean,
        email: String? = null,
        anonymousAllowed: Boolean = true,
        expiresInDays: Int? = null,
    ): Result<ShareEntry> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/share",
        method = "POST",
        body = json.encodeToString(
            CreateShareRequest(
                permission = if (canEdit) "edit" else "read",
                email = email,
                anonymousAllowed = anonymousAllowed,
                expiresInDays = expiresInDays,
            ),
        ),
    )

    /** Cofnięcie udostępnienia. Powtórka jest bezpieczna: „już nie ma" to też „ok". */
    suspend fun revokeShare(noteId: String, shareId: String): Result<RevokeShareResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/share/$shareId",
        method = "DELETE",
    )

    override suspend fun listAttachments(noteId: String): Result<AttachmentsResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/attachments",
        method = "GET",
    )

    override suspend fun sendAttachment(
        noteId: String,
        name: String,
        mime: String,
        data: ByteArray,
    ): Result<AttachmentResponse> = withContext(Dispatchers.IO) {
        val boundary = "----KajetBoundary${System.nanoTime()}"
        val header = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"name\"\r\n\r\n")
            append(name)
            append("\r\n--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n")
            append("Content-Type: $mime\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        connect(
            url = "${account.serverUrl()}/api/v1/notes/$noteId/attachments",
            method = "POST",
            contentType = "multipart/form-data; boundary=$boundary",
            withToken = true,
        ) { connection ->
            connection.setFixedLengthStreamingMode(header.size + data.size + footer.size)
            connection.outputStream.use { stream ->
                stream.write(header)
                stream.write(data)
                stream.write(footer)
            }
        }.let { parse(it) }
    }

    override suspend fun fetchAttachment(noteId: String, name: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(name, "UTF-8")
            connectBytes(
                url = "${account.serverUrl()}/api/v1/notes/$noteId/attachments?name=$encoded",
            )
        }

    // --- Running code ---

    suspend fun runCode(language: String, code: String, input: String): Result<CodeResult> = request(
        url = "${account.serverUrl()}/api/v1/code",
        method = "POST",
        body = json.encodeToString(CodeRequest(language, code, input)),
    )

    // --- Asystent KajetAI ---
    //
    // Wysyłamy WYŁĄCZNIE identyfikator notatki i polecenie. Treści nie -
    // serwer ją ma, a te same bajty w obie strony byłyby marnowaniem łącza.
    //
    // Konto bez uprawnienia dostaje z tych punktów dokładnie to samo, co
    // z nieistniejącego adresu (404, „no-route"). Tak ma być: odmowa nie ma
    // prawa zdradzić, że asystent w Kajecie w ogóle jest.

    /**
     * Prosi asystenta o zmianę notatki.
     *
     * Czeka dłużej niż reszta połączeń: model potrafi mielić kilkadziesiąt
     * sekund, a serwer i tak poddaje się pierwszy i mówi, dlaczego.
     */
    suspend fun aiEdit(
        noteId: String,
        instruction: String,
        baseVersion: Int,
    ): Result<AiEditResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/ai-edit",
        method = "POST",
        body = json.encodeToString(AiEditRequest(instruction, baseVersion)),
        readTimeout = AI_READ_TIMEOUT,
    )

    suspend fun aiHistory(noteId: String): Result<AiHistoryResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/ai-history",
        method = "GET",
    )

    suspend fun aiForgetHistory(noteId: String): Result<AiClearedResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/ai-history",
        method = "DELETE",
    )

    /** Zgoda na wysyłanie treści notatek do Google. Zapisana przy koncie. */
    suspend fun aiSetConsent(consented: Boolean): Result<AiConsentResponse> = request(
        url = "${account.serverUrl()}/api/v1/account/ai-consent",
        method = "POST",
        body = json.encodeToString(AiConsentRequest(consented)),
    )

    // --- Middle ---

    private suspend inline fun <reified T> request(
        url: String,
        method: String,
        body: String? = null,
        withToken: Boolean = true,
        acceptPending: Boolean = false,
        readTimeout: Int = READ_TIMEOUT,
    ): Result<T> = withContext(Dispatchers.IO) {
        val raw = connect(
            url = url,
            method = method,
            contentType = "application/json",
            withToken = withToken,
            acceptPending = acceptPending,
            readTimeout = readTimeout,
        ) { connection ->
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        parse<T>(raw)
    }

    private inline fun <reified T> parse(raw: Result<String>): Result<T> = when (raw) {
        is Result.Error -> raw
        is Result.Ok -> runCatching { json.decodeFromString<T>(raw.data) }
            .fold(
                onSuccess = { Result.Ok(it) },
                onFailure = {
                    Result.Error(
                        words.serverGibberish,
                    )
                },
            )
    }

    private suspend fun connect(
        url: String,
        method: String,
        contentType: String,
        withToken: Boolean,
        acceptPending: Boolean = false,
        /**
         * Ile czekać na odpowiedź. Asystent potrzebuje więcej niż reszta - i to
         * SERWER ma się poddać pierwszy, bo tylko on umie powiedzieć, dlaczego
         * się nie udało. Urwanie połączenia u nas zostawiłoby człowieka
         * z „brak odpowiedzi" przy zmianie, która może właśnie się zapisała.
         */
        readTimeout: Int = READ_TIMEOUT,
        sendBody: (HttpURLConnection) -> Unit,
    ): Result<String> {
        if (!hasNetwork()) {
            return Result.Error(
                words.offlineNoteQueued,
                worthRetrying = true,
            )
        }

        val token = if (withToken) account.token() else null
        if (withToken && token.isNullOrBlank()) {
            return Result.Error(words.notSignedIn, mustSignIn = true)
        }

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Error(words.serverUnreachable)
        }

        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = readTimeout
            connection.setRequestProperty("Accept", "application/json")
            /*
              Zdania o błędach układa serwer, a pokazuje je aplikacja - muszą
              więc przyjść w JEJ języku, nie w języku serwera. Stąd zwykły
              nagłówek HTTP; bez niego serwer odpowiada po polsku.
            */
            connection.setRequestProperty(
                "Accept-Language",
                if (words.english) "en" else "pl",
            )
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (method != "GET") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                sendBody(connection)
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status in 200..299) {
                // Device login poll uses 202 while waiting for approval.
                if (acceptPending || status != 202 || body.isNotBlank()) {
                    Result.Ok(body)
                } else {
                    Result.Ok("""{"status":"pending"}""")
                }
            } else {
                toError(status, body, tokenUsed = token != null)
            }
        } catch (e: UnknownHostException) {
            Result.Error(
                words.cannotReachServer,
                worthRetrying = true,
            )
        } catch (e: SocketTimeoutException) {
            Result.Error(words.serverTimedOut, worthRetrying = true)
        } catch (e: IOException) {
            Result.Error(words.connectionDropped, worthRetrying = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun connectBytes(url: String): Result<ByteArray> {
        if (!hasNetwork()) {
            return Result.Error(words.noInternet, worthRetrying = true)
        }
        val token = account.token()
        if (token.isNullOrBlank()) {
            return Result.Error(words.notSignedIn, mustSignIn = true)
        }

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Error(words.serverUnreachable)
        }

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty(
                "Accept-Language",
                if (words.english) "en" else "pl",
            )

            val status = connection.responseCode
            if (status in 200..299) {
                Result.Ok(connection.inputStream.use { it.readBytes() })
            } else {
                toError(
                    status = status,
                    body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty(),
                    tokenUsed = true,
                )
            }
        } catch (e: IOException) {
            Result.Error(words.fileDownloadFailed, worthRetrying = true)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Odpowiedź z błędem, przetłumaczona na [Result.Error] — i JEDYNE miejsce,
     * które rozstrzyga, czy sesja jeszcze żyje.
     *
     * Serwer, który nie uznaje tokenu, kończy tu każdą prośbę: pobranie zmian,
     * wysyłkę, załącznik, uruchomienie kodu. Sesja gaśnie więc od razu przy
     * pierwszej takiej odpowiedzi i wszystkie ekrany dowiadują się o tym
     * w tej samej chwili — czytają jeden [AccountStore.state].
     */
    private fun toError(status: Int, body: String, tokenUsed: Boolean): Result.Error {
        val fromServer = runCatching { json.decodeFromString<ServerError>(body) }.getOrNull()
        val code = fromServer?.error.orEmpty()

        val sessionDead = sessionDead(status, code, tokenUsed)
        if (sessionDead) account.markSessionExpired()

        return Result.Error(
            message = fromServer?.message ?: when (status) {
                401 -> words.sessionExpired
                403 -> words.serverRefused
                404 -> words.notOnServer
                409 -> words.noteChangedElsewhere
                507 -> words.outOfSpace
                in 500..599 -> words.serverTrouble
                else -> words.serverError(status)
            },
            worthRetrying = status >= 500 || status == 408 || status == 429,
            mustSignIn = sessionDead,
            notFound = status == 404,
            code = code,
        )
    }

    internal companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 60_000

        /**
         * Asystent czeka dłużej niż serwer daje modelowi (AI_TIMEOUT_SECONDS,
         * domyślnie 60 s). Zapas jest po to, żeby to serwer poddał się
         * pierwszy: on jeden wie, czy zmiana zdążyła się zapisać, i umie
         * powiedzieć, co poszło nie tak.
         */
        const val AI_READ_TIMEOUT = 90_000

        /**
         * Powody, dla których serwer odmawia tożsamości (pole `error`): brak
         * tokenu, token nieznany, token przeterminowany, konto zablokowane.
         * Każdy z nich znaczy: sesji już nie ma.
         */
        val AUTH_REASONS = setOf("missing", "invalid", "expired", "blocked")

        /**
         * Czy taka odpowiedź znaczy koniec sesji.
         *
         * 401 zawsze: ten token już nic nie otwiera.
         *
         * 403 znaczy na serwerze dwie różne rzeczy — zablokowane konto (czyli
         * też koniec sesji) albo „to nie twoja notatka" przy cudzym
         * udostępnieniu. Ślepe wylogowywanie przy każdym 403 wyrzucałoby
         * z konta przy zwykłym braku praw, dlatego liczy się powód podany
         * przez serwer.
         *
         * Prośba wysłana BEZ tokenu (logowanie hasłem, pytanie o zgodę ze
         * strony) nie mówi nic o sesji — odmowa znaczy tam tylko tyle, że
         * podane dane są nie te.
         */
        fun sessionDead(status: Int, code: String, tokenUsed: Boolean): Boolean {
            if (!tokenUsed) return false
            return status == 401 || (status == 403 && code in AUTH_REASONS)
        }

        // Internal, not private: the contract tests decode server-shaped JSON
        // with exactly this configuration, not a lookalike.
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

// --- Shape of requests and responses ---

@Serializable
private data class SignInRequest(val email: String, val password: String, val device: String)

@Serializable
private data class DeviceChallengeRequest(val device: String)

@Serializable
data class DeviceChallenge(
    val code: String,
    val verificationUri: String,
    val expiresIn: Int = 600,
    val interval: Int = 2,
)

@Serializable
data class DevicePollResponse(
    val status: String,
    val token: String? = null,
    val tokenId: String? = null,
    val account: AccountData? = null,
    val storage: Storage? = null,
) {
    fun toSignIn(): SignInResponse? {
        val readyToken = token ?: return null
        val readyAccount = account ?: return null
        val readyStorage = storage ?: return null
        return SignInResponse(
            token = readyToken,
            tokenId = tokenId.orEmpty(),
            account = readyAccount,
            storage = readyStorage,
        )
    }
}

@Serializable
data class SignInResponse(
    val token: String,
    val tokenId: String,
    val account: AccountData,
    val storage: Storage,
)

@Serializable
data class AccountData(
    val id: String,
    val login: String,
    val email: String,
    val name: String? = null,
    val admin: Boolean = false,
)

@Serializable
data class Storage(
    val quota: String = "0",
    val used: String = "0",
    val free: String? = null,
    val unlimited: Boolean = false,
    val quotaUntil: Long? = null,
) {
    val quotaBytes: Long get() = quota.toLongOrNull() ?: 0L
    val usedBytes: Long get() = used.toLongOrNull() ?: 0L
}

@Serializable
data class AccountState(
    val account: AccountData,
    val storage: Storage,
    val noteCount: Int = 0,
    /**
     * Asystent. Pole przychodzi WYŁĄCZNIE dla konta z uprawnieniem - brak
     * gałęzi znaczy „nie ma takiej funkcji", a nie „jest, ale wyłączona".
     * Dlatego aplikacja pyta o `ai != null`, nie o żadną flagę w środku.
     */
    val ai: AiState? = null,
)

@Serializable
data class AiState(
    /** Czy właściciel konta zgodził się na wysyłanie treści notatek do Google. */
    val consented: Boolean = false,
)

@Serializable
private data class AiEditRequest(val instruction: String, val baseVersion: Int)

/**
 * Odpowiedź asystenta. `status` mówi, co się stało:
 *  - „zmieniono" - notatka zapisana, `content` niesie nową treść,
 *  - „pytanie"   - asystent nie zrozumiał i dopytuje, notatka nietknięta,
 *  - „konflikt"  - ktoś zapisał notatkę w międzyczasie, nic nie nadpisano.
 */
@Serializable
data class AiEditResponse(
    val status: String = "",
    val opis: String = "",
    val pytanie: String = "",
    val version: Int = 0,
    val updatedAt: Long = 0,
    val content: String? = null,
)

@Serializable
data class AiTurn(val request: String = "", val reply: String = "")

@Serializable
data class AiHistoryResponse(val turns: List<AiTurn> = emptyList())

@Serializable
data class AiClearedResponse(val status: String = "ok", val cleared: Int = 0)

@Serializable
private data class AiConsentRequest(val consented: Boolean)

@Serializable
data class AiConsentResponse(val status: String = "ok", val consented: Boolean = false)

@Serializable
data class OutgoingNote(
    val id: String,
    val title: String,
    val kind: String,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val content: String,
    val baseVersion: Int,
    val deleted: Boolean = false,
)

@Serializable
data class SaveResponse(
    val status: String,
    val version: Int = 0,
    val updatedAt: Long = 0,
    val message: String? = null,
    val onServer: ServerNote? = null,
)

@Serializable
data class ServerNote(
    val id: String,
    val title: String = "",
    val kind: String = "TEXT",
    val favorite: Boolean = false,
    val tags: String = "",
    val version: Int = 1,
    val hash: String = "",
    val sizeBytes: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val folderId: String? = null,
    val content: String? = null,
    val attachments: List<AttachmentInfo> = emptyList(),
)

@Serializable
data class ChangesResponse(
    val notes: List<ServerNote> = emptyList(),
    val upTo: Long = 0,
    val upToId: String? = null,
    val hasMore: Boolean = false,
)

/** Strona spisu nagrobków: same identyfikatory i kursor po dacie skasowania. */
@Serializable
data class DeletedResponse(
    val ids: List<String> = emptyList(),
    val upTo: Long = 0,
    val upToId: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class AttachmentInfo(
    val name: String,
    val mime: String = "application/octet-stream",
    val sizeBytes: Int = 0,
    val hash: String = "",
)

@Serializable
data class AttachmentsResponse(val attachments: List<AttachmentInfo> = emptyList())

@Serializable
data class AttachmentResponse(
    val name: String,
    val hash: String = "",
    val sizeBytes: Int = 0,
    val url: String = "",
)

@Serializable
data class OutgoingFolder(
    val id: String,
    // Null znaczy: folder leży w korzeniu biblioteki.
    val parentId: String? = null,
    val name: String,
    val colorId: String = "grafit",
    val iconId: String = "folder",
)

@Serializable
data class ServerFolder(
    val id: String,
    val parentId: String? = null,
    val name: String = "",
    val colorId: String = "grafit",
    val iconId: String = "folder",
    val updatedAt: Long = 0,
)

@Serializable
data class FoldersResponse(val folders: List<ServerFolder> = emptyList())

@Serializable
private data class CreateShareRequest(
    /** „read" - do czytania, „edit" - do pisania. */
    val permission: String,
    /** Adres odbiorcy - udostępnienie imienne. Null to zwykły odnośnik. */
    val email: String? = null,
    /** Czy odnośnik otworzy ktoś bez konta. Imienne zawsze wymagają konta. */
    val anonymousAllowed: Boolean = true,
    /** Po ilu dniach odnośnik wygasa. Null albo zero - bezterminowo. */
    val expiresInDays: Int? = null,
)

/**
 * Jedno udostępnienie - wiersz listy z GET i zarazem odpowiedź POST
 * (tam dochodzą pola `fresh` i `mailSent`).
 */
@Serializable
data class ShareEntry(
    val id: String = "",
    val url: String = "",
    val permission: String = "read",
    val email: String? = null,
    val anonymousAllowed: Boolean = true,
    val expiresAt: Long? = null,
    val createdAt: Long = 0,
    val lastUsedAt: Long? = null,
    /** Fałsz znaczy, że taki odnośnik już istniał i dostajemy go z powrotem. */
    val fresh: Boolean = true,
    /** Czy wiadomość do adresata wyszła (tylko przy udostępnieniu imiennym). */
    val mailSent: Boolean = false,
)

@Serializable
data class ShareListResponse(val shares: List<ShareEntry> = emptyList())

@Serializable
data class RevokeShareResponse(val status: String = "ok")

@Serializable
data class FolderSaveResponse(
    val status: String = "ok",
    val updatedAt: Long = 0,
)

@Serializable
private data class CodeRequest(val language: String, val code: String, val input: String)

@Serializable
data class CodeResult(
    val output: String = "",
    val errors: String = "",
    val exitCode: Int? = null,
    val interrupted: Boolean = false,
    val timeMs: Long = 0,
)

@Serializable
private data class ServerError(val error: String = "", val message: String = "")
