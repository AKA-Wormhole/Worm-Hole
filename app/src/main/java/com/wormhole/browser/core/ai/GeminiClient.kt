package com.wormhole.browser.core.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import java.util.concurrent.TimeUnit

class GeminiClient(private val httpClient: OkHttpClient = defaultHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Success(val text: String, val model: String) : Result
        data class Failure(val code: Int = 0, val message: String) : Result
    }

    suspend fun generateText(apiKey: String, prompt: String, systemInstruction: String? = null): Result {
        if (apiKey.isBlank()) return Result.Failure(message = "Missing Gemini API key")

        var lastFailure = Result.Failure(message = "Request failed")
        // Retry only transient failures (network hiccups, rate limiting, and
        // server-side errors) -- a bad key or malformed request will fail the
        // same way every time, so retrying those would just burn the retry
        // budget and delay surfacing a fixable error to the user.
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_BASE_DELAY_MS * (1L shl (attempt - 1)))
            when (val result = attemptOnce(apiKey, prompt, systemInstruction)) {
                is Result.Success -> return result
                is Result.Failure -> {
                    lastFailure = result
                    if (!isTransient(result)) return result
                }
            }
        }
        return lastFailure
    }

    private fun isTransient(failure: Result.Failure): Boolean =
        failure.code == 0 || // network-level IOException, no HTTP response at all
            failure.code == 429 ||
            failure.code in 500..599

    private suspend fun attemptOnce(apiKey: String, prompt: String, systemInstruction: String?): Result =
        suspendCancellableCoroutine { continuation ->
            val body = GenerateContentRequest(
                systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(text = it))) },
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            )
            val requestBody = json.encodeToString(GenerateContentRequest.serializer(), body)
            val request = Request.Builder()
                .url(ENDPOINT)
                // Pass the key via header rather than the URL query string: a
                // "?key=..." URL is far more likely to end up copied verbatim
                // into logs, crash reports, or an HTTP logging interceptor than
                // a header is, and the REST API supports both forms equally.
                .header("x-goog-api-key", apiKey.trim())
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            val call = httpClient.newCall(request)

            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(Result.Failure(message = e.message ?: "Network error"))
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!continuation.isActive) return
                        val responseBody = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val message = runCatching {
                                json.decodeFromString(ErrorEnvelope.serializer(), responseBody).error.message
                            }.getOrNull() ?: it.message.ifBlank { "Request failed" }
                            continuation.resume(Result.Failure(it.code, message))
                            return
                        }
                        val parsed = runCatching {
                            json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
                        }.getOrNull()
                        val text = parsed?.candidates?.firstOrNull()?.content?.parts
                            ?.joinToString("") { part -> part.text.orEmpty() }
                            ?.trim()
                        continuation.resume(
                            if (text.isNullOrBlank()) Result.Failure(message = "The model returned no content")
                            else Result.Success(text, MODEL),
                        )
                    }
                }
            })
        }

    companion object {

        private const val MODEL = "gemini-3.6-flash"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 500L
        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable private data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
)
@Serializable private data class Content(val parts: List<Part>)
@Serializable private data class Part(val text: String? = null)
@Serializable private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())
@Serializable private data class Candidate(val content: Content? = null)
@Serializable private data class ErrorEnvelope(val error: ErrorDetail)
@Serializable private data class ErrorDetail(val message: String = "Request failed")
