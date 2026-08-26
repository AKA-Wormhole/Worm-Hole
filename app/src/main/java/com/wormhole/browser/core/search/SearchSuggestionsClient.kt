package com.wormhole.browser.core.search

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SearchSuggestionsClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun suggestionsFor(query: String, limit: Int = 5): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://suggestqueries.google.com/complete/search" +
            "?client=firefox&q=$encoded"
        val request = Request.Builder().url(url).build()

        return try {
            suspendCancellableCoroutine { continuation ->
                val call = httpClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }

                    override fun onResponse(call: Call, response: okhttp3.Response) {
                        val result = response.use { resp ->
                            if (!resp.isSuccessful) return@use emptyList()
                            val body = resp.body?.string() ?: return@use emptyList()
                            parseSuggestions(body, limit)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSuggestions(body: String, limit: Int): List<String> {
        return try {
            val root = json.parseToJsonElement(body).jsonArray
            val completions: JsonArray = root.getOrNull(1)?.jsonArray ?: return emptyList()
            completions.mapNotNull { element ->
                (element as? JsonPrimitive)?.content
            }.take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val defaultHttpClient = OkHttpClient.Builder()

            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }
}
