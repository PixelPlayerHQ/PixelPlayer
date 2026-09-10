package com.theveloper.pixelplay.data.network.jellyfin

import com.theveloper.pixelplay.data.jellyfin.model.JellyfinCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [request] must expose the HTTP status code as a typed [JellyfinHttpException] instead
 * of burying it inside a message string, and must never rewrap a [CancellationException]
 * into a [Result.failure].
 *
 * No mock server dependency is added for this: a fake [okhttp3.Interceptor] on the client injected
 * into [JellyfinApiService] returns a canned [Response] — real OkHttp objects, no new test
 * dependency. It works because `newBuilder()` carries interceptors over.
 */
class JellyfinApiServiceErrorHandlingTest {

    private fun serviceRespondingWith(
        code: Int,
        headers: Map<String, String> = emptyMap(),
        body: String = "{}",
    ): JellyfinApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                headers.forEach { (name, value) -> builder.addHeader(name, value) }
                builder.build()
            }
            .build()

        return JellyfinApiService(client).apply {
            setCredentials(
                JellyfinCredentials(
                    serverUrl = "http://example.invalid",
                    username = "user",
                    password = "pw",
                    accessToken = "test-token",
                    userId = "user-1",
                )
            )
        }
    }

    @Test
    fun `a non-2xx response fails with a JellyfinHttpException carrying the status code`() = runTest {
        val service = serviceRespondingWith(code = 500)

        val result = service.ping()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is JellyfinHttpException)
        assertEquals(500, (error as JellyfinHttpException).statusCode)
    }

    @Test
    fun `a 429 with Retry-After carries the raw header value, unparsed`() = runTest {
        val service = serviceRespondingWith(code = 429, headers = mapOf("Retry-After" to "30"))

        val result = service.ping()

        val error = result.exceptionOrNull() as JellyfinHttpException
        assertEquals(429, error.statusCode)
        assertEquals("30", error.retryAfter)
    }

    @Test
    fun `a failure without a Retry-After header leaves it null, not throws`() = runTest {
        val service = serviceRespondingWith(code = 404)

        val result = service.ping()

        val error = result.exceptionOrNull() as JellyfinHttpException
        assertEquals(404, error.statusCode)
        assertNull(error.retryAfter)
    }

    @Test
    fun `a successful response still succeeds — unchanged happy path`() = runTest {
        val service = serviceRespondingWith(code = 200)

        val result = service.ping()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    // ─── Case borde: cancellation propagates instead of becoming a Result ───────
    //
    // Same deterministic pattern as `GitHubAnnouncementPropertiesServiceTest` (P.9): cancel the
    // coroutine before the suspend function is even entered, so the cancellation check inside
    // `withContext` fires before any network code runs — no dependency on real socket timing.

    @Test
    fun `a coroutine cancelled before the call starts propagates cancellation, not a Result`() = runTest {
        val service = serviceRespondingWith(code = 200)
        var sawCancellation = false

        val job = launch {
            cancel()
            try {
                service.ping()
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(sawCancellation)
    }

    // Same bug, same fix, a different method in this file (surfaced by self-review, not R19 —
    // authenticateByName() isn't part of F3.3's error taxonomy, but the cancellation-swallowing
    // catch is identical and just as real).

    @Test
    fun `authenticateByName also propagates cancellation instead of becoming a Result`() = runTest {
        val service = serviceRespondingWith(code = 200)
        var sawCancellation = false

        val job = launch {
            cancel()
            try {
                service.authenticateByName("http://example.invalid", "user", "pw")
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(sawCancellation)
    }
}
