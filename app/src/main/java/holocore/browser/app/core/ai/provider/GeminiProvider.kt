package holocore.browser.app.core.ai.provider

import holocore.browser.app.core.ai.GeminiClient

class GeminiProvider(private val client: GeminiClient = GeminiClient()) : AiProvider {
    override val id: String = "gemini"
    override suspend fun generate(apiKey: String, prompt: String, systemInstruction: String?): AiResponse {
        return when (val result = client.generateText(apiKey, prompt, systemInstruction)) {
            is GeminiClient.Result.Success -> AiResponse(result.text, result.model)
            is GeminiClient.Result.Failure -> throw IllegalStateException(result.message)
        }
    }
}
