package com.wormhole.browser.core.ai

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ChatConversationSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
)

data class ChatHistoryMessage(
    val id: Long,
    val role: String,
    val text: String,
)

class ChatHistoryRepository(context: Context) {
    private val dao = ChatHistoryDatabase.get(context).dao()

    val conversations: Flow<List<ChatConversationSummary>> = dao.conversations().map { rows ->
        rows.map { ChatConversationSummary(it.id, it.title, it.updatedAt) }
    }

    fun messages(conversationId: Long): Flow<List<ChatHistoryMessage>> =
        dao.messages(conversationId).map { rows -> rows.map { ChatHistoryMessage(it.id, it.role, it.text) } }

    suspend fun createConversation(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.upsertConversation(ChatConversationEntity(title = title, createdAt = now, updatedAt = now))
    }

    suspend fun renameConversation(id: Long, title: String) {
        val existing = dao.conversation(id) ?: return
        dao.upsertConversation(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchConversation(id: Long) {
        val existing = dao.conversation(id) ?: return
        dao.upsertConversation(existing.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: Long) {
        dao.deleteMessagesForConversation(id)
        dao.deleteConversation(id)
    }

    suspend fun addMessage(conversationId: Long, role: String, text: String) {
        dao.insertMessage(ChatMessageEntity(conversationId = conversationId, role = role, text = text, createdAt = System.currentTimeMillis()))
        touchConversation(conversationId)
    }
}
