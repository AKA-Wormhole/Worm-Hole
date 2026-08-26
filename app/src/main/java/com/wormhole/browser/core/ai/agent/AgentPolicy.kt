package com.wormhole.browser.core.ai.agent

object AgentPolicy {
    private val SECRET_TOOLS = setOf(
        "passkeys", "passkey", "api_key", "apikey", "gemini_key", "get_api_key",
        "set_api_key", "read_credentials", "export_passkeys", "password",
    )

    // Tool names this app's BrowserAgent actually registers that can take a
    // consequential, hard-to-undo action: submitting/typing into a form field
    // (which may submit purchases, account changes, etc.), tapping an
    // arbitrary element (same risk -- "Buy now", "Delete account", ...),
    // rewriting page text, running arbitrary JS, or wiping history.
    // The previous list named tools ("submit_form", "purchase", "delete_data",
    // ...) that BrowserAgent never actually registers, so it silently gated
    // nothing real except clear_history and execute_js.
    fun requiresConfirmation(toolName: String): Boolean = toolName in setOf(
        "type_text", "tap", "edit_page", "execute_js",
        "clear_history",
    )

    fun isAllowedTool(toolName: String): Boolean =
        toolName.matches(Regex("[a-z][a-z0-9_]{1,63}")) && !isDeniedSecretTool(toolName)

    fun isDeniedSecretTool(toolName: String): Boolean {
        val n = toolName.lowercase()
        return n in SECRET_TOOLS || SECRET_TOOLS.any { n.contains(it) }
    }
}
