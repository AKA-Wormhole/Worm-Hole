package com.wormhole.browser.core.ai.agent

data class AiContext(
    val system: String,
    val user: String,
    val webpage: String = "",
    val searchResults: String = "",
    val toolResults: String = "",
)

object AiContextSerializer {
    fun serialize(context: AiContext): String = buildString {
        appendLine("<SYSTEM>")
        appendLine(context.system)
        appendLine("</SYSTEM>")
        appendLine("<USER>")
        appendLine(context.user)
        appendLine("</USER>")
        if (context.webpage.isNotBlank()) {
            appendLine("<UNTRUSTED_WEBPAGE_CONTENT>")
            appendLine(context.webpage)
            appendLine("</UNTRUSTED_WEBPAGE_CONTENT>")
        }
        if (context.searchResults.isNotBlank()) {
            appendLine("<UNTRUSTED_SEARCH_RESULTS>")
            appendLine(context.searchResults)
            appendLine("</UNTRUSTED_SEARCH_RESULTS>")
        }
        if (context.toolResults.isNotBlank()) {
            appendLine("<TOOL_RESULTS>")
            appendLine(context.toolResults)
            appendLine("</TOOL_RESULTS>")
        }
    }
}
