package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val path: String,
  val isDirectory: Boolean = true,
  val sourceType: String = "LOCAL", // LOCAL or DRIVE
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val originalPath: String,
  val trashPath: String,
  val fileName: String,
  val sizeBytes: Long,
  val isDirectory: Boolean,
  val mimeType: String,
  val deletedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val fileName: String,
  val destinationPath: String,
  val sizeBytes: Long = 0L,
  val downloadedBytes: Long = 0L,
  val status: String = "COMPLETED", // DOWNLOADING, COMPLETED, FAILED, CANCELLED
  val errorMessage: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "network_servers")
data class NetworkServerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val protocol: String, // FTP, FTPS, SFTP, WEBDAV, SMB
  val host: String,
  val port: Int,
  val username: String = "",
  val password: String = "",
  val remotePath: String = "/",
  val ftpMode: String = "PASSIVE", // ACTIVE, PASSIVE
  val encoding: String = "AUTO", // AUTO, UTF_8, ISO_8859_1, WINDOWS_1252
  val useHttps: Boolean = false,
  val domain: String = "",
  val anonymous: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "auto_backup_rules")
data class AutoBackupRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val localFolderPath: String,
  val localFolderName: String,
  val driveFolderId: String,
  val driveFolderName: String,
  val enabled: Boolean = true,
  val wifiOnly: Boolean = true,
  val frequency: String = "WIFI_AUTO",
  val lastSyncAt: Long = 0L,
  val lastSyncCount: Int = 0,
)

@Entity(tableName = "recent_file_actions")
data class RecentFileActionEntity(
  @PrimaryKey val path: String,
  val name: String,
  val sizeBytes: Long,
  val mimeType: String,
  val accessedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sender_history")
data class SenderHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val fileName: String,
  val direction: String, // SENT or RECEIVED
  val peerName: String,
  val filePath: String,
  val sizeBytes: Long = 0L,
  val createdAt: Long = System.currentTimeMillis(),
)
