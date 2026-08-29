package wojtoteka.ovh.kajet.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileUploaderTest {
    @Test
    fun `network timeout and 5xx are retried`() {
        assertThat(
            uploadFailureIsRetryable(
                CloudClient.Result.Error("timeout", worthRetrying = true),
            ),
        ).isTrue()
        assertThat(
            uploadFailureIsRetryable(
                CloudClient.Result.Error("server", worthRetrying = true, httpStatus = 500),
            ),
        ).isTrue()
    }

    @Test
    fun `400 and 413 are permanent`() {
        assertThat(
            uploadFailureIsRetryable(CloudClient.Result.Error("bad", httpStatus = 400)),
        ).isFalse()
        assertThat(
            uploadFailureIsRetryable(CloudClient.Result.Error("large", httpStatus = 413)),
        ).isFalse()
    }

    @Test
    fun `auth failure never enters WorkManager retry loop`() {
        assertThat(
            uploadFailureIsRetryable(
                CloudClient.Result.Error(
                    "sign in",
                    worthRetrying = true,
                    mustSignIn = true,
                    httpStatus = 401,
                ),
            ),
        ).isFalse()
    }
}
