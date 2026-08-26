package com.wormhole.browser.core.ai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["conversationId"])],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val text: String,
    val createdAt: Long,
)

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun conversations(): Flow<List<ChatConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(entry: ChatConversationEntity): Long

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    suspend fun conversation(id: Long): ChatConversationEntity?

    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long): Int

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun messages(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insertMessage(entry: ChatMessageEntity): Long

    @Delete
    suspend fun deleteMessage(entry: ChatMessageEntity)
}

@Database(
    entities = [ChatConversationEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatHistoryDatabase : androidx.room.RoomDatabase() {
    abstract fun dao(): ChatHistoryDao

    companion object {
        @Volatile private var INSTANCE: ChatHistoryDatabase? = null
        fun get(context: Context): ChatHistoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ChatHistoryDatabase::class.java,
                "wormhole_chat_history.db",
            ).build().also { INSTANCE = it }
        }
    }
}
