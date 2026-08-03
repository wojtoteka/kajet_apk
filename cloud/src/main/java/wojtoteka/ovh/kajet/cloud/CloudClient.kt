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

class CloudClient(
    private val context: Context,
    private val account: AccountStore,
) {

    sealed interface Result<out T> {
        data class Ok<T>(val data: T) : Result<T>
        data class Error(
            val message: String,
            val worthRetrying: Boolean = false,
            val mustSignIn: Boolean = false,
        ) : Result<Nothing>
    }

    fun hasNetwork(): Boolean {
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

    suspend fun fetchChanges(
        since: Long,
        afterId: String? = null,
        withContent: Boolean = true,
    ): Result<ChangesResponse> {
        val params = buildString {
            append("since=$since")
            if (!afterId.isNullOrBlank()) append("&afterId=").append(java.net.URLEncoder.encode(afterId, "UTF-8"))
            if (!withContent) append("&withContent=no")
        }
        return request(
            url = "${account.serverUrl()}/api/v1/notes?$params",
            method = "GET",
        )
    }

    suspend fun sendNote(note: OutgoingNote): Result<SaveResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes",
        method = "PUT",
        body = json.encodeToString(note),
    )

    suspend fun listAttachments(noteId: String): Result<AttachmentsResponse> = request(
        url = "${account.serverUrl()}/api/v1/notes/$noteId/attachments",
        method = "GET",
    )

    suspend fun sendAttachment(
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

    suspend fun fetchAttachment(noteId: String, name: String): Result<ByteArray> =
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

    // --- Middle ---

    private suspend inline fun <reified T> request(
        url: String,
        method: String,
        body: String? = null,
        withToken: Boolean = true,
        acceptPending: Boolean = false,
    ): Result<T> = withContext(Dispatchers.IO) {
        val raw = connect(url, method, "application/json", withToken, acceptPending) { connection ->
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
                        "Serwer odpowiedział czymś, czego nie rozumiem. " +
                            "Spróbuj ponownie za chwilę.",
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
        sendBody: (HttpURLConnection) -> Unit,
    ): Result<String> {
        if (!hasNetwork()) {
            return Result.Error(
                "Nie ma połączenia z internetem. Notatka jest zapisana na urządzeniu i wyślemy ją, gdy sieć wróci.",
                worthRetrying = true,
            )
        }

        val token = if (withToken) account.token() else null
        if (withToken && token.isNullOrBlank()) {
            return Result.Error("Nie jesteś zalogowany.", mustSignIn = true)
        }

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Error("Nie udało się połączyć z serwerem.")
        }

        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Accept", "application/json")
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
                toError(status, body)
            }
        } catch (e: UnknownHostException) {
            Result.Error(
                "Nie mogę połączyć się z serwerem. Sprawdź połączenie z internetem.",
                worthRetrying = true,
            )
        } catch (e: SocketTimeoutException) {
            Result.Error("Serwer nie odpowiedział na czas. Spróbuję jeszcze raz później.", worthRetrying = true)
        } catch (e: IOException) {
            Result.Error("Połączenie z serwerem się urwało. Spróbuję jeszcze raz później.", worthRetrying = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun connectBytes(url: String): Result<ByteArray> {
        if (!hasNetwork()) {
            return Result.Error("Nie ma połączenia z internetem.", worthRetrying = true)
        }
        val token = account.token()
        if (token.isNullOrBlank()) {
            return Result.Error("Nie jesteś zalogowany.", mustSignIn = true)
        }

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Result.Error("Nie udało się połączyć z serwerem.")
        }

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $token")

            val status = connection.responseCode
            if (status in 200..299) {
                Result.Ok(connection.inputStream.use { it.readBytes() })
            } else {
                toError(status, connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }
        } catch (e: IOException) {
            Result.Error("Nie udało się pobrać pliku. Spróbuję później.", worthRetrying = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun toError(status: Int, body: String): Result.Error {
        val fromServer = runCatching { json.decodeFromString<ServerError>(body) }.getOrNull()

        return Result.Error(
            message = fromServer?.message ?: when (status) {
                401 -> "Sesja wygasła. Zaloguj się jeszcze raz."
                403 -> "Serwer odmówił dostępu."
                404 -> "Nie ma tego na serwerze."
                409 -> "Ta notatka zmieniła się także gdzie indziej."
                507 -> "Brakuje miejsca na koncie."
                in 500..599 -> "Serwer ma kłopot. Spróbuję jeszcze raz później."
                else -> "Serwer odpowiedział błędem $status."
            },
            worthRetrying = status >= 500 || status == 408 || status == 429,
            mustSignIn = status == 401,
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 60_000

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
)

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
