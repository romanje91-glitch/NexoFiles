package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

  private val esLocale = Locale("es", "ES")

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format(esLocale, if (digitGroups >= 2) "%.2f %s" else "%.0f %s", value, units[digitGroups])
  }

  fun formatDate(timestampMs: Long): String {
    if (timestampMs <= 0L) return "—"
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
      diff in 0 until 60_000 -> "Hace un momento"
      diff in 60_000 until 3_600_000 -> "Hace ${diff / 60_000} min"
      diff in 3_600_000 until 86_400_000 -> "Hoy, ${SimpleDateFormat("HH:mm", esLocale).format(Date(timestampMs))}"
      diff in 86_400_000 until 172_800_000 -> "Ayer"
      else -> SimpleDateFormat("dd/MM/yyyy", esLocale).format(Date(timestampMs))
    }
  }

  fun getMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext.isEmpty()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
      "apk" -> "application/vnd.android.package-archive"
      "pdf" -> "application/pdf"
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "gif" -> "image/gif"
      "bmp" -> "image/bmp"
      "heic", "heif" -> "image/heic"
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "webm" -> "video/webm"
      "3gp" -> "video/3gpp"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "mp3" -> "audio/mpeg"
      "wav" -> "audio/wav"
      "ogg" -> "audio/ogg"
      "m4a" -> "audio/mp4"
      "flac" -> "audio/flac"
      "aac" -> "audio/aac"
      "md" -> "text/markdown"
      "json" -> "application/json"
      "xml" -> "text/xml"
      "log", "txt", "kt", "java", "py", "js", "html", "css", "csv", "yml", "yaml" -> "text/plain"
      "zip" -> "application/zip"
      "tar" -> "application/x-tar"
      "gz", "tgz" -> "application/gzip"
      "7z" -> "application/x-7z-compressed"
      "rar" -> "application/vnd.rar"
      else -> "application/octet-stream"
    }
  }

  fun isImage(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
  }

  fun isVideo(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v")
  }

  fun isAudio(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
  }

  fun isPdf(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) == "pdf"
  }

  fun isTextEditable(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("txt", "md", "json", "xml", "log", "csv", "ini", "conf", "cfg", "properties", "kt", "java", "html", "css", "js", "sh", "yaml", "yml")
  }

  fun isArchive(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in setOf("zip", "tar", "gz", "tgz", "7z", "rar")
  }

  data class FileContentInspection(
    val textPreview: String,
    val hexPreview: String,
    val isBinary: Boolean,
    val isTruncated: Boolean,
    val totalBytes: Long,
  )

  fun readTextOrHexPreview(file: File, maxBytes: Int = 64 * 1024): FileContentInspection {
    if (!file.exists() || !file.isFile || !file.canRead()) {
      return FileContentInspection("", "", isBinary = false, isTruncated = false, totalBytes = 0L)
    }
    val totalBytes = file.length()
    val toRead = totalBytes.coerceAtMost(maxBytes.toLong()).toInt()
    val bytes = ByteArray(toRead)
    var readLen = 0
    FileInputStream(file).use { input ->
      while (readLen < toRead) {
        val n = input.read(bytes, readLen, toRead - readLen)
        if (n <= 0) break
        readLen += n
      }
    }
    val slice = if (readLen == bytes.size) bytes else bytes.copyOf(readLen)
    var nonPrintable = 0
    val printableSample = StringBuilder(slice.size)
    for (b in slice) {
      val v = b.toInt() and 0xFF
      if (v == 0) nonPrintable += 4
      val okControl = v == 9 || v == 10 || v == 13
      if (!okControl && (v < 32 || v == 127)) nonPrintable++
      printableSample.append(
        when {
          v in 32..126 -> v.toChar()
          v == 10 -> '\n'
          v == 9 -> '\t'
          else -> '.'
        }
      )
    }
    val looksBinary = slice.isNotEmpty() && (!isTextEditable(file.name) && (nonPrintable.toDouble() / slice.size.toDouble()) > 0.08 || slice.any { it.toInt() == 0 })
    val textOut = if (looksBinary) {
      printableSample.toString()
    } else {
      slice.toString(Charsets.UTF_8)
    }
    val hexOut = buildHexDump(slice, maxHexBytes = 16 * 1024)
    return FileContentInspection(
      textPreview = textOut,
      hexPreview = hexOut,
      isBinary = looksBinary,
      isTruncated = totalBytes > maxBytes,
      totalBytes = totalBytes,
    )
  }

  private fun buildHexDump(bytes: ByteArray, maxHexBytes: Int): String {
    val limit = bytes.size.coerceAtMost(maxHexBytes)
    if (limit == 0) return ""
    val sb = StringBuilder(limit * 4)
    var offset = 0
    while (offset < limit) {
      sb.append("%06X  ".format(Locale.US, offset))
      val lineEnd = (offset + 16).coerceAtMost(limit)
      for (i in offset until offset + 16) {
        if (i < lineEnd) {
          sb.append("%02X ".format(Locale.US, bytes[i].toInt() and 0xFF))
        } else {
          sb.append("   ")
        }
        if (i == offset + 7) sb.append(' ')
      }
      sb.append(" |")
      for (i in offset until lineEnd) {
        val c = bytes[i].toInt() and 0xFF
        sb.append(if (c in 32..126) c.toChar() else '.')
      }
      sb.append("|\n")
      offset += 16
    }
    return sb.toString()
  }

  fun computeFileHashes(file: File): Pair<String, String> {
    if (!file.exists() || !file.isFile) return "—" to "—"
    return runCatching {
      val md5 = MessageDigest.getInstance("MD5")
      val sha256 = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(16 * 1024)
      FileInputStream(file).use { input ->
        var read = input.read(buffer)
        while (read != -1) {
          md5.update(buffer, 0, read)
          sha256.update(buffer, 0, read)
          read = input.read(buffer)
        }
      }
      md5.digest().toHex() to sha256.digest().toHex()
    }.getOrElse { "Error" to "Error" }
  }

  fun computeFastMd5(file: File): String? {
    if (!file.exists() || !file.isFile) return null
    return runCatching {
      val md5 = MessageDigest.getInstance("MD5")
      val buffer = ByteArray(32 * 1024)
      FileInputStream(file).use { input ->
        var read = input.read(buffer)
        while (read != -1) {
          md5.update(buffer, 0, read)
          read = input.read(buffer)
        }
      }
      md5.digest().toHex()
    }.getOrNull()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  fun getFileProviderUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { getFileProviderUri(context, it) })
    val intent = if (uris.size == 1) {
      Intent(Intent.ACTION_SEND).apply {
        type = getMimeType(files.first().name)
        putExtra(Intent.EXTRA_STREAM, uris.first())
      }
    } else {
      Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    val chooser = Intent.createChooser(intent, "Compartir archivo con…").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  fun openFileWithSystem(context: Context, file: File) {
    val uri = getFileProviderUri(context, file)
    val mime = getMimeType(file.name)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, mime)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "Abrir con…").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
  }
}
