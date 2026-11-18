package com.example.data.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.BookmarkEntity
import com.example.data.db.NexoDao
import com.example.data.db.RecentFileActionEntity
import com.example.data.db.RecycleBinEntity
import com.example.data.model.FileCategory
import com.example.data.model.FileProperties
import com.example.data.model.LocalFileItem
import com.example.data.model.SortBy
import com.example.data.model.StorageStats
import com.example.util.FormatUtils
import com.example.util.StoragePermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

class LocalFileRepository(
  private val context: Context,
  private val dao: NexoDao,
) {

  val rootStoragePath: String
    get() = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

  private val recycleBinDir: File
    get() = File(context.filesDir, "recycle_bin").apply { if (!exists()) mkdirs() }

  fun observeBookmarks(): Flow<List<BookmarkEntity>> = dao.observeBookmarks()
  fun observeRecycleBin(): Flow<List<RecycleBinEntity>> = dao.observeRecycleBin()
  fun observeRecentActions(): Flow<List<RecentFileActionEntity>> = dao.observeRecentFiles()

  suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
    val root = Environment.getExternalStorageDirectory() ?: context.filesDir
    val statFs = runCatching { StatFs(root.absolutePath) }.getOrNull()
    val total = statFs?.totalBytes ?: 0L
    val free = statFs?.availableBytes ?: 0L
    val used = (total - free).coerceAtLeast(0L)
    StorageStats(total, used, free, root.absolutePath, StoragePermissionHelper.hasAllFilesAccess(context))
  }

  fun getAvailableVolumes(): List<Pair<String, String>> {
    val volumes = mutableListOf<Pair<String, String>>()
    volumes.add("Almacenamiento interno" to rootStoragePath)
    val externalDirs = context.getExternalFilesDirs(null)
    externalDirs.drop(1).forEachIndexed { idx, dir ->
      if (dir != null) {
        val rootPath = dir.absolutePath.substringBefore("/Android/data")
        if (File(rootPath).exists()) volumes.add("Tarjeta SD ${idx + 1}" to rootPath)
      }
    }
    return volumes
  }

  suspend fun listDirectory(
    path: String,
    showHidden: Boolean,
    sortBy: SortBy,
    ascending: Boolean,
  ): Result<List<LocalFileItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) throw IllegalStateException("La carpeta no existe o no es accesible")
      val children = dir.listFiles()?.toList() ?: emptyList()
      val filtered = children.filter { showHidden || !it.name.startsWith(".") }.map { it.toLocalFileItem() }
      val comparator = when (sortBy) {
        SortBy.NAME -> compareBy<LocalFileItem> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
        SortBy.DATE -> compareBy<LocalFileItem> { !it.isDirectory }.thenBy { it.lastModified }
        SortBy.SIZE -> compareBy<LocalFileItem> { !it.isDirectory }.thenBy { it.sizeBytes }
      }
      val sorted = filtered.sortedWith(comparator)
      if (ascending) sorted else sorted.sortedWith(
        compareBy<LocalFileItem> { !it.isDirectory }.thenByDescending {
          when (sortBy) {
            SortBy.NAME -> it.name.lowercase(Locale.ROOT)
            SortBy.DATE -> it.lastModified.toString().padStart(20, '0')
            SortBy.SIZE -> it.sizeBytes.toString().padStart(20, '0')
          }
        }
      )
    }
  }

  suspend fun listCategoryFiles(category: FileCategory, showHidden: Boolean): List<LocalFileItem> =
    withContext(Dispatchers.IO) {
      val results = mutableListOf<LocalFileItem>()
      val seenPaths = HashSet<String>()
      val mediaUri: Uri? = when (category) {
        FileCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        FileCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        FileCategory.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> null
      }
      if (mediaUri != null) {
        runCatching {
          val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
          )
          context.contentResolver.query(mediaUri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext() && results.size < 400) {
              val path = cursor.getString(dataCol) ?: continue
              val file = File(path)
              if (!file.exists() || (!showHidden && file.name.startsWith("."))) continue
              if (seenPaths.add(path)) {
                results.add(
                  LocalFileItem(
                    name = cursor.getString(nameCol) ?: file.name,
                    path = path,
                    isDirectory = false,
                    sizeBytes = cursor.getLong(sizeCol).takeIf { it > 0 } ?: file.length(),
                    lastModified = (cursor.getLong(dateCol) * 1000L).takeIf { it > 0 } ?: file.lastModified(),
                    mimeType = cursor.getString(mimeCol) ?: FormatUtils.getMimeType(file.name),
                    isHidden = file.name.startsWith("."),
                  )
                )
              }
            }
          }
        }
      }

      if (category == FileCategory.DOCUMENTS || category == FileCategory.APKS || results.isEmpty()) {
        val targetExts = when (category) {
          FileCategory.IMAGES -> setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg")
          FileCategory.VIDEOS -> setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp")
          FileCategory.MUSIC -> setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus")
          FileCategory.DOCUMENTS -> setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "epub")
          FileCategory.APKS -> setOf("apk", "apks", "xapk")
        }
        walkLimited(File(rootStoragePath), maxFiles = 2500, maxDepth = 6) { file ->
          if (file.isFile && (showHidden || !file.name.startsWith("."))) {
            val ext = file.extension.lowercase(Locale.ROOT)
            if (ext in targetExts && seenPaths.add(file.absolutePath)) results.add(file.toLocalFileItem())
          }
        }
      }

      results.sortedByDescending { it.lastModified }
    }

  suspend fun globalSearch(query: String, showHidden: Boolean): List<LocalFileItem> =
    withContext(Dispatchers.IO) {
      val clean = query.trim().lowercase(Locale.ROOT)
      if (clean.isEmpty()) return@withContext emptyList()
      val matches = mutableListOf<LocalFileItem>()
      walkLimited(File(rootStoragePath), maxFiles = 4000, maxDepth = 7) { file ->
        if ((showHidden || !file.name.startsWith(".")) && file.name.lowercase(Locale.ROOT).contains(clean)) {
          matches.add(file.toLocalFileItem())
        }
      }
      matches.sortedWith(compareBy<LocalFileItem> { !it.isDirectory }.thenByDescending { it.lastModified }).take(150)
    }

  suspend fun getRecentFiles(limit: Int = 15): List<LocalFileItem> = withContext(Dispatchers.IO) {
    val root = File(rootStoragePath)
    val recent = mutableListOf<LocalFileItem>()
    val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    listOf("DCIM", "Download", "Documents", "Pictures", "Movies", "Music")
      .map { File(root, it) }
      .filter { it.exists() && it.isDirectory }
      .forEach { dir ->
        walkLimited(dir, maxFiles = 600, maxDepth = 4) { file ->
          if (file.isFile && !file.name.startsWith(".") && file.lastModified() >= sevenDaysAgo) {
            recent.add(file.toLocalFileItem())
          }
        }
      }
    recent.sortedByDescending { it.lastModified }.take(limit)
  }

  suspend fun createFolder(parentPath: String, name: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val clean = name.trim()
      require(clean.isNotEmpty() && !clean.contains("/")) { "Nombre de carpeta no válido" }
      val target = File(parentPath, clean)
      if (target.exists()) throw IllegalStateException("Ya existe una carpeta o archivo con ese nombre")
      if (!target.mkdirs()) throw IllegalStateException("No se pudo crear la carpeta. Verifica los permisos")
      target
    }
  }

  suspend fun createTextFile(parentPath: String, name: String, content: String = ""): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val clean = name.trim()
        require(clean.isNotEmpty() && !clean.contains("/")) { "Nombre de archivo no válido" }
        val target = File(parentPath, clean)
        if (target.exists()) throw IllegalStateException("El archivo ya existe")
        target.writeText(content)
        recordRecentAction(target)
        target
      }
    }

  suspend fun renameFile(path: String, newName: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val clean = newName.trim()
      require(clean.isNotEmpty() && !clean.contains("/")) { "Nombre no válido" }
      val source = File(path)
      val target = File(source.parentFile, clean)
      if (target.exists()) throw IllegalStateException("Ya existe un elemento con ese nombre")
      if (!source.renameTo(target)) throw IllegalStateException("No se pudo renombrar el elemento")
      target
    }
  }

  suspend fun copyOrMoveFiles(sourcePaths: List<String>, destDirPath: String, move: Boolean): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        val destDir = File(destDirPath)
        require(destDir.exists() && destDir.isDirectory) { "Carpeta de destino inválida" }
        var count = 0
        for (srcPath in sourcePaths) {
          val src = File(srcPath)
          if (!src.exists()) continue
          val target = generateUniqueTarget(destDir, src.name)
          if (move) {
            if (src.renameTo(target)) count++ else {
              copyRecursive(src, target)
              deleteRecursive(src)
              count++
            }
          } else {
            copyRecursive(src, target)
            count++
          }
        }
        count
      }
    }

  suspend fun moveToRecycleBin(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      var moved = 0
      for (path in paths) {
        val src = File(path)
        if (!src.exists()) continue
        val trashFile = File(recycleBinDir, "${UUID.randomUUID()}_${src.name}")
        val size = computeRecursiveSize(src)
        val success = src.renameTo(trashFile) || run {
          copyRecursive(src, trashFile)
          deleteRecursive(src)
        }
        if (success) {
          dao.insertRecycleItem(
            RecycleBinEntity(
              originalPath = src.absolutePath,
              trashPath = trashFile.absolutePath,
              fileName = src.name,
              sizeBytes = size,
              isDirectory = trashFile.isDirectory,
              mimeType = if (trashFile.isDirectory) "inode/directory" else FormatUtils.getMimeType(src.name),
            )
          )
          moved++
        }
      }
      moved
    }
  }

  suspend fun restoreFromRecycleBin(item: RecycleBinEntity): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val trashFile = File(item.trashPath)
      val origFile = File(item.originalPath)
      origFile.parentFile?.mkdirs()
      val finalDest = if (origFile.exists()) {
        generateUniqueTarget(origFile.parentFile ?: File(rootStoragePath), origFile.name)
      } else origFile
      if (trashFile.exists()) {
        if (!trashFile.renameTo(finalDest)) {
          copyRecursive(trashFile, finalDest)
          deleteRecursive(trashFile)
        }
      }
      dao.deleteRecycleItemById(item.id)
    }
  }

  suspend fun deletePermanently(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      var count = 0
      for (path in paths) {
        val file = File(path)
        if (file.exists() && deleteRecursive(file)) count++
      }
      count
    }
  }

  suspend fun deleteRecycleItemPermanently(item: RecycleBinEntity): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val file = File(item.trashPath)
      if (file.exists()) deleteRecursive(file)
      dao.deleteRecycleItemById(item.id)
    }
  }

  suspend fun emptyRecycleBin(): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      dao.getAllRecycleBinSync().forEach {
        val f = File(it.trashPath)
        if (f.exists()) deleteRecursive(f)
      }
      dao.clearRecycleBin()
    }
  }

  suspend fun getFileProperties(path: String): FileProperties = withContext(Dispatchers.IO) {
    val file = File(path)
    var childFiles = 0
    var childFolders = 0
    var totalSize = 0L
    if (file.isDirectory) {
      walkLimited(file, maxFiles = 5000, maxDepth = 10) { f ->
        if (f.isDirectory) childFolders++ else {
          childFiles++
          totalSize += f.length()
        }
      }
    } else totalSize = file.length()
    val (md5, sha256) = if (file.isFile && file.length() <= 250L * 1024 * 1024) FormatUtils.computeFileHashes(file)
    else if (file.isFile) "Archivo > 250 MB" to "Archivo > 250 MB" else "Carpeta" to "Carpeta"
    val perms = buildString {
      append(if (file.isDirectory) 'd' else '-')
      append(if (file.canRead()) 'r' else '-')
      append(if (file.canWrite()) 'w' else '-')
      append(if (file.canExecute()) 'x' else '-')
    }
    FileProperties(file.toLocalFileItem(), childFiles, childFolders, totalSize, md5, sha256, perms)
  }

  suspend fun toggleBookmark(item: LocalFileItem): Boolean = withContext(Dispatchers.IO) {
    if (dao.isBookmarked(item.path)) {
      dao.deleteBookmarkByPath(item.path)
      false
    } else {
      dao.insertBookmark(BookmarkEntity(name = item.name, path = item.path, isDirectory = item.isDirectory, sourceType = "LOCAL"))
      true
    }
  }

  suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) { dao.deleteBookmarkById(id) }

  suspend fun recordRecentAction(file: File) = withContext(Dispatchers.IO) {
    if (file.exists() && file.isFile) {
      dao.upsertRecentFile(RecentFileActionEntity(file.absolutePath, file.name, file.length(), FormatUtils.getMimeType(file.name)))
    }
  }

  suspend fun importFromSafUri(uri: Uri, destDirPath: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val doc = DocumentFile.fromSingleUri(context, uri)
      val name = doc?.name ?: "importado_${System.currentTimeMillis()}"
      val destDir = File(destDirPath).apply { if (!exists()) mkdirs() }
      val dest = generateUniqueTarget(destDir, name)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")
      recordRecentAction(dest)
      dest
    }
  }

  suspend fun exportToSafUri(sourceFile: File, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val resolver: ContentResolver = context.contentResolver
      resolver.openOutputStream(destUri)?.use { output ->
        FileInputStream(sourceFile).use { input -> input.copyTo(output) }
      } ?: throw IllegalStateException("No se pudo escribir en el destino SAF")
      Unit
    }
  }

  private fun File.toLocalFileItem(): LocalFileItem = LocalFileItem(
    name = name.ifEmpty { absolutePath },
    path = absolutePath,
    isDirectory = isDirectory,
    sizeBytes = if (isDirectory) 0L else length(),
    lastModified = lastModified(),
    childCount = if (isDirectory) list()?.size ?: 0 else 0,
    mimeType = if (isDirectory) "inode/directory" else FormatUtils.getMimeType(name),
    isHidden = name.startsWith("."),
    canRead = canRead(),
    canWrite = canWrite(),
  )

  private fun generateUniqueTarget(dir: File, originalName: String): File {
    var candidate = File(dir, originalName)
    if (!candidate.exists()) return candidate
    val base = originalName.substringBeforeLast('.', originalName)
    val ext = originalName.substringAfterLast('.', "")
    var index = 1
    while (candidate.exists()) {
      val nextName = if (ext.isEmpty() || base == originalName) "${base}_($index)" else "${base}_($index).$ext"
      candidate = File(dir, nextName)
      index++
    }
    return candidate
  }

  private fun copyRecursive(src: File, dst: File) {
    if (src.isDirectory) {
      if (!dst.exists()) dst.mkdirs()
      src.listFiles()?.forEach { child -> copyRecursive(child, File(dst, child.name)) }
    } else {
      dst.parentFile?.mkdirs()
      FileInputStream(src).use { input -> FileOutputStream(dst).use { output -> input.copyTo(output) } }
    }
  }

  private fun deleteRecursive(file: File): Boolean {
    if (file.isDirectory) file.listFiles()?.forEach { deleteRecursive(it) }
    return file.delete()
  }

  private fun computeRecursiveSize(file: File): Long {
    if (file.isFile) return file.length()
    var sum = 0L
    file.listFiles()?.forEach { sum += computeRecursiveSize(it) }
    return sum
  }

  private fun walkLimited(
    dir: File,
    maxFiles: Int,
    maxDepth: Int,
    currentDepth: Int = 0,
    visited: IntArray = intArrayOf(0),
    onFile: (File) -> Unit,
  ) {
    if (currentDepth > maxDepth || visited[0] >= maxFiles) return
    val files = dir.listFiles() ?: return
    for (file in files) {
      if (visited[0] >= maxFiles) return
      visited[0]++
      onFile(file)
      if (file.isDirectory && file.name != "Android") {
        walkLimited(file, maxFiles, maxDepth, currentDepth + 1, visited, onFile)
      }
    }
  }
}
