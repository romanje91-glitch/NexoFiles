package com.example.data.repo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.db.DownloadHistoryEntity
import com.example.data.db.NexoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloaderRepository(
  private val context: Context,
  private val dao: NexoDao,
) {

  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val channelId = "nexofiles_downloads"

  fun observeDownloads(): Flow<List<DownloadHistoryEntity>> = dao.observeDownloadHistory()

  fun readClipboardHttpUrl(): String? {
    return runCatching {
      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
      val clip = cm.primaryClip ?: return null
      if (clip.itemCount == 0) return null
      val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
      if (text.startsWith("http://", true) || text.startsWith("https://", true)) text else null
    }.getOrNull()
  }

  fun suggestFileNameFromUrl(url: String): String {
    val clean = url.trim().substringBefore('?').substringBefore('#')
    val raw = clean.substringAfterLast('/')
    return raw.takeIf { it.isNotBlank() && it.contains('.') } ?: "archivo_${System.currentTimeMillis() % 10000}"
  }

  suspend fun startDownload(
    url: String,
    customFileName: String? = null,
    destinationDirPath: String? = null,
    multithreadBuffer: Boolean = true,
    dismissOnFinish: Boolean = false,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
  ): Result<DownloadHistoryEntity> = withContext(Dispatchers.IO) {
    val cleanUrl = url.trim()
    val guessedName = customFileName?.takeIf { it.isNotBlank() } ?: suggestFileNameFromUrl(cleanUrl)

    val customDir = destinationDirPath?.takeIf { it.isNotBlank() }?.let { File(it) }
      ?.takeIf { it.exists() || it.mkdirs() }
      ?.takeIf { it.canWrite() }
    val downloadsDir = customDir
      ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ?.takeIf { it.exists() || it.mkdirs() }
        ?.takeIf { it.canWrite() }
      ?: File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Downloads").apply { mkdirs() }

    val destFile = File(downloadsDir, guessedName)
    var entity = DownloadHistoryEntity(
      url = cleanUrl,
      fileName = guessedName,
      destinationPath = destFile.absolutePath,
      status = "DOWNLOADING",
    )
    val rowId = dao.insertDownload(entity)
    entity = entity.copy(id = rowId)
    val notifId = (rowId.toInt() and 0x7FFF) + 4100
    showProgressNotification(notifId, guessedName, 0, true)

    runCatching {
      val request = Request.Builder().url(cleanUrl).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        val body = response.body ?: throw IllegalStateException("Respuesta sin contenido")
        val total = body.contentLength()
        var downloaded = 0L
        var lastDbUpdate = 0L
        entity = entity.copy(sizeBytes = total.coerceAtLeast(0L))
        dao.updateDownload(entity)

        val bufSize = if (multithreadBuffer) 64 * 1024 else 16 * 1024
        body.byteStream().use { input ->
          FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(bufSize)
            var read = input.read(buffer)
            while (read != -1) {
              output.write(buffer, 0, read)
              downloaded += read
              onProgress(downloaded, total)
              val now = System.currentTimeMillis()
              if (now - lastDbUpdate > 400L) {
                lastDbUpdate = now
                dao.updateDownload(entity.copy(downloadedBytes = downloaded))
                val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                showProgressNotification(notifId, guessedName, pct, total <= 0)
              }
              read = input.read(buffer)
            }
          }
        }
        val completed = entity.copy(
          sizeBytes = if (total > 0) total else downloaded,
          downloadedBytes = downloaded,
          status = "COMPLETED",
        )
        dao.updateDownload(completed)
        if (dismissOnFinish) cancelNotification(notifId) else showCompletedNotification(notifId, guessedName, true)
        completed
      }
    }.onFailure { err ->
      dao.updateDownload(entity.copy(status = "FAILED", errorMessage = err.localizedMessage ?: "Error en descarga"))
      showCompletedNotification(notifId, guessedName, false)
    }
  }

  suspend fun deleteDownloadRecord(id: Long) {
    dao.deleteDownloadById(id)
  }

  suspend fun clearHistory() {
    dao.clearDownloadHistory()
  }

  private fun ensureNotificationChannel(manager: NotificationManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, "Descargas de NexoFiles", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Progreso de descargas HTTP y HTTPS"
      }
      manager.createNotificationChannel(channel)
    }
  }

  private fun canPostNotifications(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  private fun showProgressNotification(notifId: Int, fileName: String, progress: Int, indeterminate: Boolean) {
    if (!canPostNotifications()) return
    runCatching {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      ensureNotificationChannel(manager)
      val notif = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Descargando $fileName")
        .setContentText(if (indeterminate) "Descargando…" else "$progress%")
        .setProgress(100, progress, indeterminate)
        .setOngoing(true)
        .setSilent(true)
        .build()
      manager.notify(notifId, notif)
    }
  }

  private fun showCompletedNotification(notifId: Int, fileName: String, success: Boolean) {
    if (!canPostNotifications()) return
    runCatching {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      ensureNotificationChannel(manager)
      val notif = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
        .setContentTitle(if (success) "Descarga completada" else "Descarga fallida")
        .setContentText(fileName)
        .setAutoCancel(true)
        .build()
      manager.notify(notifId, notif)
    }
  }

  private fun cancelNotification(notifId: Int) {
    runCatching {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      manager.cancel(notifId)
    }
  }
}
