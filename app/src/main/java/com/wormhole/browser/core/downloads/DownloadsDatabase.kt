package com.wormhole.browser.core.downloads

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads", indices = [Index(value = ["status"])])
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: String,

    val destinationUri: String,
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val status: String,

    val errorMessage: String? = null,

    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float?
        get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f) else null
}

enum class WormHoleDownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
    CANCELLED,
}

@Dao
interface DownloadsDao {

    @Query("SELECT * FROM downloads ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("UPDATE downloads SET bytesDownloaded = :bytesDownloaded, bytesTotal = :bytesTotal, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, bytesDownloaded: Long, bytesTotal: Long, status: String, updatedAt: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE downloads SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE downloads SET fileName = :fileName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, fileName: String, updatedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>): Int

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'RUNNING')")
    suspend fun activeDownloads(): List<DownloadRecord>
}

@Database(entities = [DownloadRecord::class], version = 2, exportSchema = false)
abstract class DownloadsDatabase : androidx.room.RoomDatabase() {
    abstract fun dao(): DownloadsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: DownloadsDatabase? = null
        fun get(context: Context): DownloadsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                DownloadsDatabase::class.java,
                "wormhole_downloads.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
