package com.wormhole.browser.core.library

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long,
)

@Entity(tableName = "history", indices = [Index(value = ["url"]), Index(value = ["visitedAt"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,
)

@Entity(tableName = "shortcuts", indices = [Index(value = ["url"], unique = true)])
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC LIMIT 500")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(entry: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String): Int

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 5000")
    fun history(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory(): Int

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteHistory(url: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShortcut(entry: ShortcutEntity)

    @Query("SELECT * FROM shortcuts ORDER BY createdAt ASC LIMIT 100")
    fun shortcuts(): Flow<List<ShortcutEntity>>

    @Query("DELETE FROM shortcuts WHERE url = :url")
    suspend fun deleteShortcut(url: String): Int

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE url LIKE '%' || :query || '%' ESCAPE '\' OR title LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY
            CASE WHEN url LIKE :query || '%' ESCAPE '\' OR title LIKE :query || '%' ESCAPE '\' THEN 0 ELSE 1 END,
            createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchBookmarks(query: String, limit: Int): List<BookmarkEntity>

    @Query(
        """
        SELECT * FROM history
        WHERE url LIKE '%' || :query || '%' ESCAPE '\' OR title LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY url
        ORDER BY
            CASE WHEN url LIKE :query || '%' ESCAPE '\' OR title LIKE :query || '%' ESCAPE '\' THEN 0 ELSE 1 END,
            MAX(visitedAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun searchHistory(query: String, limit: Int): List<HistoryEntity>
}

@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, ShortcutEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LibraryDatabase : androidx.room.RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile private var INSTANCE: LibraryDatabase? = null
        fun get(context: Context): LibraryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                "wormhole_library.db",
            ).build().also { INSTANCE = it }
        }
    }
}
