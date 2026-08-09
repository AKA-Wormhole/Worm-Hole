package com.knot.browser.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of a Gemini call: either the model's text, or a reason it
 * failed, distinguished so the UI can show something more useful than a
 * generic "something went wrong". */
sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    sealed interface Failure : GeminiResult {
        data object MissingApiKey : Failure
        data object NoContent : Failure
        data class Http(val code: Int, val message: String) : Failure
        data class Network(val message: String) : Failure
    }
}

/**
 * Thin client for the Gemini API's generateContent endpoint, used by both
 * the Assistant (page summarization) and Translate features -- they only
 * differ in the prompt they send, so this owns nothing feature-specific.
 *
 * Uses the API key the user pastes into Settings (see
 * SettingsRepository.geminiApiKey); Knot never ships its own key. Plain
 * OkHttp + kotlinx.serialization rather than a generated SDK, since this
 * is the only endpoint Knot talks to and a full client would be a lot of
 * dependency weight for one call shape.
 */
class GeminiClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateText(apiKey: String, prompt: String): GeminiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext GeminiResult.Failure.MissingApiKey

        val requestBody = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt))),
            ),
        )

        val bodyJson = json.encodeToString(GenerateContentRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("$ENDPOINT?key=${apiKey.trim()}")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.decodeFromString(ErrorEnvelope.serializer(), responseBody).error.message
                    }.getOrNull() ?: response.message.ifBlank { "Request failed" }
                    return@withContext GeminiResult.Failure.Http(response.code, message)
                }

                val parsed = runCatching {
                    json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
                }.getOrNull()

                val text = parsed?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()

                if (text.isNullOrBlank()) GeminiResult.Failure.NoContent else GeminiResult.Success(text)
            }
        } catch (e: IOException) {
            GeminiResult.Failure.Network(e.message ?: "Network error")
        }
    }

    companion object {
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// --- Request/response wire models --------------------------------------

@Serializable
private data class GenerateContentRequest(val contents: List<Content>)

@Serializable
private data class Content(val parts: List<Part>)

@Serializable
private data class Part(val text: String)

@Serializable
private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

@Serializable
private data class Candidate(val content: Content? = null)

@Serializable
private data class ErrorEnvelope(val error: ErrorDetail)

@Serializable
private data class ErrorDetail(val message: String = "Request failed")
