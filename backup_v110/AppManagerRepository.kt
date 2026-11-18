package com.example.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LocalFileItem
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppManagerRepository(private val context: Context) {

  val backedUpAppsDir: File
    get() {
      val ext = Environment.getExternalStorageDirectory()
      val candidate = if (ext != null && ext.canWrite()) {
        File(ext, "NexoFiles/Backed-up Apps")
      } else {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "Backed-up Apps")
      }
      if (!candidate.exists()) candidate.mkdirs()
      return candidate
    }

  suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION")
      pm.getInstalledPackages(0)
    }

    packages.mapNotNull { pkgInfo ->
      val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
      val apkFile = File(appInfo.sourceDir)
      val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
      InstalledAppInfo(
        appName = appInfo.loadLabel(pm).toString(),
        packageName = pkgInfo.packageName,
        versionName = pkgInfo.versionName ?: "1.0",
        apkPath = appInfo.sourceDir,
        apkSizeBytes = if (apkFile.exists()) apkFile.length() else 0L,
        isSystemApp = isSystem,
        installedAt = pkgInfo.firstInstallTime,
        icon = runCatching { appInfo.loadIcon(pm) }.getOrNull(),
      )
    }.sortedBy { it.appName.lowercase() }
  }

  fun resolveBackupDir(customPath: String? = null): File {
    val custom = customPath?.takeIf { it.isNotBlank() }?.let { File(it) }
    val dir = if (custom != null && (custom.exists() || custom.mkdirs())) {
      custom
    } else {
      backedUpAppsDir
    }
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  suspend fun backupApk(app: InstalledAppInfo, customBackupDir: String? = null): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val src = File(app.apkPath)
      require(src.exists() && src.canRead()) { "No se puede leer el APK origen" }
      val safeAppName = app.appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val dest = File(resolveBackupDir(customBackupDir), "${safeAppName}_v${app.versionName}.apk")
      FileInputStream(src).use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      }
      dest
    }
  }

  suspend fun listBackedUpApks(customBackupDir: String? = null): List<LocalFileItem> = withContext(Dispatchers.IO) {
    val dir = resolveBackupDir(customBackupDir)
    dir.listFiles()
      ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
      ?.map { file ->
        LocalFileItem(
          name = file.name,
          path = file.absolutePath,
          isDirectory = false,
          sizeBytes = file.length(),
          lastModified = file.lastModified(),
          mimeType = "application/vnd.android.package-archive",
        )
      }
      ?.sortedByDescending { it.lastModified }
      ?: emptyList()
  }

  fun launchInstallApk(apkFile: File) {
    val uri = FormatUtils.getFileProviderUri(context, apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun launchUninstallApp(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
