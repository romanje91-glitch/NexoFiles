package com.example.data.repo

import android.os.Environment
import android.os.StatFs
import com.example.data.model.AppFolderInfo
import com.example.data.model.CategoryBreakdown
import com.example.data.model.DuplicateScanProgress
import com.example.data.model.LocalFileItem
import com.example.data.model.StorageAnalysisResult
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class StorageAnalyzerRepository(
  private val duplicateFinder: DuplicateFinderEngine = DuplicateFinderEngine(),
  private val similarPhotosEngine: SimilarPhotosEngine = SimilarPhotosEngine(),
) {

  private val photoExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "raw", "dng")
  private val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "flv")
  private val docExts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "epub")
  private val appExts = setOf("apk", "apks", "xapk", "obb")
  private val junkExts = setOf("tmp", "temp", "log", "bak", "chk", "dmp")

  suspend fun runFullAnalysis(
    onStageProgress: suspend (stageIndex: Int, message: String) -> Unit = { _, _ -> },
    onDuplicateFunnelProgress: suspend (DuplicateScanProgress) -> Unit = {},
    onSimilarPhotosProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
  ): StorageAnalysisResult = withContext(Dispatchers.IO) {
    val statPath = runCatching { Environment.getDataDirectory()?.absolutePath }.getOrNull()
      ?: Environment.getExternalStorageDirectory()?.absolutePath
      ?: "/"
    val statFs = runCatching { StatFs(statPath) }.getOrNull()
    val deviceTotalBytes = statFs?.totalBytes?.takeIf { it > 0L } ?: 0L
    val deviceAvailableBytes = statFs?.availableBytes?.coerceAtLeast(0L) ?: 0L
    val deviceUsedBytes = (deviceTotalBytes - deviceAvailableBytes).coerceAtLeast(0L)

    val root = Environment.getExternalStorageDirectory() ?: return@withContext StorageAnalysisResult(
      deviceTotalBytes = deviceTotalBytes,
      deviceUsedBytes = deviceUsedBytes,
    )
    var photos = 0L
    var videos = 0L
    var apps = 0L
    var docs = 0L
    var others = 0L

    val largeFiles = mutableListOf<LocalFileItem>()
    val junkFiles = mutableListOf<LocalFileItem>()
    val emptyFolders = mutableListOf<LocalFileItem>()
    val recentFiles = mutableListOf<LocalFileItem>()
    val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val largeThreshold = 50L * 1024L * 1024L

    onStageProgress(1, "Escaneando fotos e imágenes…")

    fun scanDir(dir: File, depth: Int) {
      if (depth > 8) return
      if (dir.name == ".nexo_recycle_bin" || dir.name == ".nexo_vault") return
      val children = dir.listFiles() ?: return
      if (children.isEmpty() && dir.absolutePath != root.absolutePath && !dir.name.startsWith("DCIM")) {
        emptyFolders.add(
          LocalFileItem(
            name = dir.name,
            path = dir.absolutePath,
            isDirectory = true,
            sizeBytes = 0L,
            lastModified = dir.lastModified(),
            mimeType = "inode/directory",
          )
        )
        return
      }
      for (file in children) {
        if (file.isDirectory) {
          if (file.name == ".thumbnails" || file.name.equals("cache", ignoreCase = true)) {
            val cacheChildren = file.listFiles() ?: emptyArray()
            for (cf in cacheChildren) {
              if (cf.isFile) junkFiles.add(cf.toItem())
            }
          }
          scanDir(file, depth + 1)
        } else if (file.isFile) {
          val size = file.length()
          val ext = file.extension.lowercase(Locale.ROOT)
          when {
            ext in photoExts -> photos += size
            ext in videoExts -> videos += size
            ext in appExts -> apps += size
            ext in docExts -> docs += size
            else -> others += size
          }
          if (size >= largeThreshold) largeFiles.add(file.toItem())
          if (ext in junkExts || file.name.endsWith(".tmp") || file.name.startsWith("._")) {
            junkFiles.add(file.toItem())
          }
          if (file.lastModified() >= sevenDaysAgo && !file.name.startsWith(".")) {
            recentFiles.add(file.toItem())
          }
        }
      }
    }

    scanDir(root, 0)
    onStageProgress(2, "Clasificando videos y multimedia…")
    onStageProgress(3, "Analizando aplicaciones y paquetes APK…")
    onStageProgress(4, "Embudo de 3 fases: peso exacto + 8 KB SHA-256 + MD5…")

    val dupSummary = duplicateFinder.scanDuplicates(
      roots = listOf(root),
      maxDepth = 9,
      includeHidden = false,
      onProgress = onDuplicateFunnelProgress,
    )

    onStageProgress(4, "Detectando fotos similares (dHash perceptual + EXIF)…")
    val similarPhotoGroups = similarPhotosEngine.scanSimilarPhotos(
      onProgress = onSimilarPhotosProgress,
    )

    onStageProgress(5, "Inspeccionando basura, caché y carpetas de apps…")

    val appFolderCandidates = mutableListOf<AppFolderInfo>()
    val topDirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
    for (dir in topDirs) {
      if (dir.name == "Android") {
        listOf("data", "media", "obb").forEach { sub ->
          val androidSub = File(dir, sub)
          if (androidSub.exists()) {
            androidSub.listFiles()?.filter { it.isDirectory }?.take(20)?.forEach { pkgDir ->
              val (bytes, count) = quickFolderStats(pkgDir, maxFiles = 250)
              if (bytes > 0L || count > 0) {
                appFolderCandidates.add(
                  AppFolderInfo("Android/$sub/${pkgDir.name}", pkgDir.absolutePath, bytes, count)
                )
              }
            }
          }
        }
      } else {
        val (bytes, count) = quickFolderStats(dir, maxFiles = 400)
        appFolderCandidates.add(AppFolderInfo(dir.name, dir.absolutePath, bytes, count))
      }
    }

    StorageAnalysisResult(
      breakdown = CategoryBreakdown(photos, videos, apps, docs, others),
      deviceTotalBytes = deviceTotalBytes,
      deviceUsedBytes = deviceUsedBytes,
      duplicateGroups = dupSummary.groups,
      duplicateSummaryStats = dupSummary,
      similarPhotoGroups = similarPhotoGroups,
      largeFiles = largeFiles.sortedByDescending { it.sizeBytes },
      junkFiles = junkFiles.sortedByDescending { it.sizeBytes },
      emptyFolders = emptyFolders,
      appFolders = appFolderCandidates.sortedByDescending { it.sizeBytes }.take(25),
      recentlyCreatedFiles = recentFiles.sortedByDescending { it.lastModified }.take(60),
      isScanning = false,
      scanStageIndex = 5,
      scanProgressMessage = "Análisis completado",
    )
  }

  private fun quickFolderStats(dir: File, maxFiles: Int): Pair<Long, Int> {
    var total = 0L
    var count = 0
    val stack = ArrayDeque<File>()
    stack.addLast(dir)
    while (stack.isNotEmpty() && count < maxFiles) {
      val current = stack.removeFirst()
      val children = current.listFiles() ?: continue
      for (child in children) {
        if (child.isDirectory) stack.addLast(child) else {
          total += child.length()
          count++
        }
        if (count >= maxFiles) break
      }
    }
    return total to count
  }

  private fun File.toItem(): LocalFileItem = LocalFileItem(
    name = name,
    path = absolutePath,
    isDirectory = isDirectory,
    sizeBytes = length(),
    lastModified = lastModified(),
    mimeType = FormatUtils.getMimeType(name),
    isHidden = name.startsWith("."),
  )
}
