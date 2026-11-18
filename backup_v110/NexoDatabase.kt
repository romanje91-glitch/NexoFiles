package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    BookmarkEntity::class,
    RecycleBinEntity::class,
    DownloadHistoryEntity::class,
    NetworkServerEntity::class,
    AutoBackupRuleEntity::class,
    RecentFileActionEntity::class,
    SenderHistoryEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class NexoDatabase : RoomDatabase() {
  abstract fun nexoDao(): NexoDao

  companion object {
    @Volatile
    private var INSTANCE: NexoDatabase? = null

    fun getInstance(context: Context): NexoDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          NexoDatabase::class.java,
          "nexofiles_db",
        ).fallbackToDestructiveMigration(true).build()
        INSTANCE = instance
        instance
      }
    }
  }
}
