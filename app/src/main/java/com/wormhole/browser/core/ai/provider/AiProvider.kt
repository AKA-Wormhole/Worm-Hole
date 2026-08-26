package com.wormhole.browser.core.ai.provider

interface AiProvider {
    val id: String
    suspend fun generate(apiKey: String, prompt: String, systemInstruction: String? = null): AiResponse
}

data class AiResponse(val text: String, val model: String, val raw: String? = null)

sealed interface AiProviderError {
    data object MissingCredentials : AiProviderError
    data class RequestFailed(val code: Int, val message: String) : AiProviderError
    data class Network(val message: String) : AiProviderError
}
