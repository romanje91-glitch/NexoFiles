package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global UncaughtExceptionHandler that captures fatal crashes + full stacktrace,
 * stores the log in internal storage (`filesDir/nexofiles_error_log.txt`) AND attempts to write
 * to `/storage/emulated/0/Download/nexofiles_error_log.txt`, and flags an unacknowledged crash
 * in SharedPreferences so the user sees a diagnostic alert on next app launch.
 */
object CrashLogger {

  private const val PREFS_NAME = "nexofiles_crash_prefs"
  private const val KEY_HAS_UNSEEN_CRASH = "has_unseen_crash"
  private const val KEY_LAST_CRASH_TIME = "last_crash_time"
  private const val KEY_LAST_CRASH_SUMMARY = "last_crash_summary"
  private const val LOG_FILE_NAME = "nexofiles_error_log.txt"
  private const val PUBLIC_DOWNLOAD_LOG_PATH = "/storage/emulated/0/Download/nexofiles_error_log.txt"
  private const val MAX_LOG_BYTES = 256_000

  @Volatile
  private var installed = false

  @Volatile
  private var previousHandler: Thread.UncaughtExceptionHandler? = null

  fun install(context: Context) {
    if (installed) return
    synchronized(this) {
      if (installed) return
      val appContext = context.applicationContext
      previousHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
          recordCrash(appContext, thread, throwable)
        }
        val prev = previousHandler
        if (prev != null && prev !== Thread.getDefaultUncaughtExceptionHandler()) {
          prev.uncaughtException(thread, throwable)
        } else {
          android.os.Process.killProcess(android.os.Process.myPid())
          kotlin.system.exitProcess(10)
        }
      }
      installed = true
    }
  }

  private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
    val now = System.currentTimeMillis()
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(now))
    val versionInfo = runCatching {
      @Suppress("DEPRECATION")
      val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
      }
      "${pInfo.versionName} (${if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode})"
    }.getOrDefault("1.0")

    val stackTrace = Log.getStackTraceString(throwable)
    val summary = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Sin mensaje"}"

    val entry = buildString {
      appendLine("============================================================")
      appendLine("NEXOFILES // CRASH TELEMETRY REPORT")
      appendLine("Timestamp : $timestamp")
      appendLine("App Ver   : $versionInfo (${context.packageName})")
      appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT} / Android ${Build.VERSION.RELEASE})")
      appendLine("Thread    : ${thread.name} (id=${thread.id})")
      appendLine("Exception : $summary")
      appendLine("------------------------------------------------------------")
      appendLine(stackTrace.trimEnd())
      appendLine()
    }

    // 1. Write atomically to internal storage (always guaranteed writable)
    val internalFile = File(context.filesDir, LOG_FILE_NAME)
    writeBoundedLog(internalFile, entry)

    // 2. Best-effort write to /storage/emulated/0/Download/nexofiles_error_log.txt
    runCatching {
      val publicFile = File(PUBLIC_DOWNLOAD_LOG_PATH)
      publicFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
      writeBoundedLog(publicFile, entry)
    }

    // 3. Flag unseen crash for next launch alert
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_HAS_UNSEEN_CRASH, true)
      .putLong(KEY_LAST_CRASH_TIME, now)
      .putString(KEY_LAST_CRASH_SUMMARY, summary)
      .commit()
  }

  private fun writeBoundedLog(file: File, newEntry: String) {
    val existing = if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
    val combined = (newEntry + existing)
    val bounded = if (combined.length > MAX_LOG_BYTES) {
      combined.take(MAX_LOG_BYTES)
    } else {
      combined
    }
    file.writeText(bounded)
  }

  fun hasUnseenCrash(context: Context): Boolean {
    return context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_HAS_UNSEEN_CRASH, false)
  }

  fun getLastCrashSummary(context: Context): String {
    return context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_LAST_CRASH_SUMMARY, "")
      .orEmpty()
  }

  fun acknowledgeCrash(context: Context) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_HAS_UNSEEN_CRASH, false)
      .apply()
  }

  fun readCrashLog(context: Context): String {
    val internal = File(context.applicationContext.filesDir, LOG_FILE_NAME)
    if (internal.exists()) {
      val text = runCatching { internal.readText() }.getOrDefault("")
      if (text.isNotBlank()) return text
    }
    val publicFile = File(PUBLIC_DOWNLOAD_LOG_PATH)
    if (publicFile.exists()) {
      val text = runCatching { publicFile.readText() }.getOrDefault("")
      if (text.isNotBlank()) return text
    }
    return ""
  }

  fun clearCrashLog(context: Context) {
    runCatching { File(context.applicationContext.filesDir, LOG_FILE_NAME).delete() }
    runCatching { File(PUBLIC_DOWNLOAD_LOG_PATH).delete() }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_HAS_UNSEEN_CRASH, false)
      .remove(KEY_LAST_CRASH_SUMMARY)
      .apply()
  }

  fun copyCrashLogToClipboard(context: Context): Boolean {
    val content = readCrashLog(context)
    if (content.isBlank()) return false
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    cm.setPrimaryClip(ClipData.newPlainText("NexoFiles Crash Report", content))
    return true
  }
}
