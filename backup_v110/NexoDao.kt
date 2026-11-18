package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NexoDao {
  // Bookmarks
  @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
  fun observeBookmarks(): Flow<List<BookmarkEntity>>

  @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
  suspend fun isBookmarked(path: String): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBookmark(bookmark: BookmarkEntity): Long

  @Query("DELETE FROM bookmarks WHERE id = :id")
  suspend fun deleteBookmarkById(id: Long)

  @Query("DELETE FROM bookmarks WHERE path = :path")
  suspend fun deleteBookmarkByPath(path: String)

  // Recycle Bin
  @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
  fun observeRecycleBin(): Flow<List<RecycleBinEntity>>

  @Query("SELECT * FROM recycle_bin")
  suspend fun getAllRecycleBinSync(): List<RecycleBinEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRecycleItem(item: RecycleBinEntity): Long

  @Query("DELETE FROM recycle_bin WHERE id = :id")
  suspend fun deleteRecycleItemById(id: Long)

  @Query("DELETE FROM recycle_bin")
  suspend fun clearRecycleBin()

  // Download History
  @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
  fun observeDownloadHistory(): Flow<List<DownloadHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDownload(item: DownloadHistoryEntity): Long

  @Update
  suspend fun updateDownload(item: DownloadHistoryEntity)

  @Query("DELETE FROM download_history WHERE id = :id")
  suspend fun deleteDownloadById(id: Long)

  @Query("DELETE FROM download_history")
  suspend fun clearDownloadHistory()

  // Network Servers
  @Query("SELECT * FROM network_servers ORDER BY createdAt DESC")
  fun observeNetworkServers(): Flow<List<NetworkServerEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNetworkServer(server: NetworkServerEntity): Long

  @Query("DELETE FROM network_servers WHERE id = :id")
  suspend fun deleteNetworkServerById(id: Long)

  // Auto Backup Rules
  @Query("SELECT * FROM auto_backup_rules ORDER BY id DESC")
  fun observeAutoBackupRules(): Flow<List<AutoBackupRuleEntity>>

  @Query("SELECT * FROM auto_backup_rules WHERE enabled = 1")
  suspend fun getEnabledAutoBackupRules(): List<AutoBackupRuleEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAutoBackupRule(rule: AutoBackupRuleEntity): Long

  @Update
  suspend fun updateAutoBackupRule(rule: AutoBackupRuleEntity)

  @Query("DELETE FROM auto_backup_rules WHERE id = :id")
  suspend fun deleteAutoBackupRuleById(id: Long)

  // Recent File Actions
  @Query("SELECT * FROM recent_file_actions ORDER BY accessedAt DESC LIMIT 30")
  fun observeRecentFiles(): Flow<List<RecentFileActionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRecentFile(item: RecentFileActionEntity)

  @Query("DELETE FROM recent_file_actions")
  suspend fun clearRecentFiles()

  // Sender History
  @Query("SELECT * FROM sender_history ORDER BY createdAt DESC")
  fun observeSenderHistory(): Flow<List<SenderHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSenderHistory(item: SenderHistoryEntity): Long

  @Query("DELETE FROM sender_history")
  suspend fun clearSenderHistory()

  // Sync reads for backup export
  @Query("SELECT * FROM bookmarks")
  suspend fun getBookmarksSync(): List<BookmarkEntity>

  @Query("SELECT * FROM network_servers")
  suspend fun getNetworkServersSync(): List<NetworkServerEntity>
}
