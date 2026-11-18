package com.example.data.repo

import com.example.data.model.DuplicateGroup
import com.example.data.model.DuplicateScanProgress
import com.example.data.model.DuplicateScanSummary
import com.example.data.model.LocalFileItem
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

class DuplicateFinderEngine {

  companion object {
    const val PARTIAL_SAMPLE_BYTES = 8 * 1024
    private val COPY_NAME_REGEX = Regex("(\\(\\d+\\)|_copy|-copy|\\bcopia\\b|\\bcopy\\b|-wa\\d+)", RegexOption.IGNORE_CASE)
  }

  suspend fun scanDuplicates(
    roots: List<File>,
    maxDepth: Int = 10,
    includeHidden: Boolean = false,
    onProgress: suspend (DuplicateScanProgress) -> Unit = {},
  ): DuplicateScanSummary = withContext(Dispatchers.IO) {
    val allFiles = mutableListOf<File>()

    fun collectFiles(dir: File, depth: Int) {
      if (depth > maxDepth || !dir.exists() || !dir.canRead()) return
      if (!includeHidden && depth > 0 && dir.name.startsWith(".")) return
      if (dir.name == ".nexo_recycle_bin" || dir.name == ".nexo_vault") return
      val children = dir.listFiles() ?: return
      for (child in children) {
        if (!includeHidden && child.name.startsWith(".")) continue
        if (child.isDirectory) collectFiles(child, depth + 1)
        else if (child.isFile && child.canRead() && child.length() > 0L) allFiles.add(child)
      }
    }

    for (root in roots) {
      coroutineContext.ensureActive()
      if (root.exists()) collectFiles(root, 0)
    }

    val totalScanned = allFiles.size
    val sizeBuckets = HashMap<Long, MutableList<File>>()
    allFiles.forEachIndexed { idx, file ->
      if (idx % 120 == 0 || idx == totalScanned - 1) {
        coroutineContext.ensureActive()
        onProgress(DuplicateScanProgress(true, 1, idx + 1, totalScanned.coerceAtLeast(1), totalScanned, "1/3 · Size"))
      }
      val exactBytes = file.length()
      if (exactBytes > 0L) sizeBuckets.getOrPut(exactBytes) { mutableListOf() }.add(file)
    }

    val equalSizeGroups = sizeBuckets.filterValues { it.size >= 2 }
    val phase1CandidateCount = equalSizeGroups.values.sumOf { it.size }
    if (equalSizeGroups.isEmpty()) {
      onProgress(DuplicateScanProgress(false, 3, 1, 1))
      return@withContext DuplicateScanSummary(emptyList(), totalScanned, 0, 0, 0)
    }

    val structuralBuckets = HashMap<String, Pair<Long, MutableList<File>>>()
    var phase2Processed = 0
    for ((size, filesWithSameSize) in equalSizeGroups) {
      for (candidate in filesWithSameSize) {
        coroutineContext.ensureActive()
        val partialSha = computeHeadTailSha256(candidate, size)
        if (partialSha != null) {
          structuralBuckets.getOrPut("$size:$partialSha") { size to mutableListOf() }.second.add(candidate)
        }
        phase2Processed++
        if (phase2Processed % 20 == 0 || phase2Processed == phase1CandidateCount) {
          onProgress(DuplicateScanProgress(true, 2, phase2Processed, phase1CandidateCount.coerceAtLeast(1), phase1CandidateCount, "2/3 · 8KB SHA-256"))
        }
      }
    }

    val matchingStructureGroups = structuralBuckets.filterValues { it.second.size >= 2 }
    val phase2CandidateCount = matchingStructureGroups.values.sumOf { it.second.size }
    if (matchingStructureGroups.isEmpty()) {
      onProgress(DuplicateScanProgress(false, 3, 1, 1))
      return@withContext DuplicateScanSummary(emptyList(), totalScanned, phase1CandidateCount, 0, 0)
    }

    val confirmedGroups = mutableListOf<DuplicateGroup>()
    var phase3Processed = 0
    for ((compositeKey, pair) in matchingStructureGroups) {
      val (size, candidates) = pair
      val partialHash = compositeKey.substringAfter(':', "")
      val md5Buckets = HashMap<String, MutableList<File>>()
      for (candidate in candidates) {
        coroutineContext.ensureActive()
        val fullMd5 = computeFullMd5(candidate)
        if (fullMd5 != null) md5Buckets.getOrPut(fullMd5) { mutableListOf() }.add(candidate)
        phase3Processed++
        onProgress(DuplicateScanProgress(true, 3, phase3Processed, phase2CandidateCount.coerceAtLeast(1), phase2CandidateCount, "3/3 · MD5"))
      }
      for ((md5Hex, identicalFiles) in md5Buckets) {
        if (identicalFiles.size >= 2) {
          confirmedGroups.add(DuplicateGroup(md5Hex, size, orderKeepFirst(identicalFiles.map { it.toLocalFileItem() }), partialHash))
        }
      }
    }

    val sortedGroups = confirmedGroups.sortedByDescending { it.sizeBytes * (it.files.size - 1).coerceAtLeast(1) }
    val totalConfirmedFiles = sortedGroups.sumOf { it.files.size }
    onProgress(DuplicateScanProgress(false, 3, phase2CandidateCount, phase2CandidateCount.coerceAtLeast(1), totalConfirmedFiles))
    DuplicateScanSummary(sortedGroups, totalScanned, phase1CandidateCount, phase2CandidateCount, totalConfirmedFiles)
  }

  fun computeHeadTailSha256(file: File, knownLength: Long = file.length()): String? {
    if (!file.exists() || !file.isFile || knownLength <= 0L) return null
    return runCatching {
      val digest = MessageDigest.getInstance("SHA-256")
      val lenBytes = ByteArray(8) { i -> ((knownLength ushr (i * 8)) and 0xFF).toByte() }
      digest.update(lenBytes)
      RandomAccessFile(file, "r").use { raf ->
        if (knownLength <= PARTIAL_SAMPLE_BYTES * 2L) {
          val all = ByteArray(knownLength.toInt())
          raf.readFully(all)
          digest.update(all)
        } else {
          val head = ByteArray(PARTIAL_SAMPLE_BYTES)
          raf.seek(0L)
          raf.readFully(head)
          digest.update(head)
          val tail = ByteArray(PARTIAL_SAMPLE_BYTES)
          raf.seek(knownLength - PARTIAL_SAMPLE_BYTES)
          raf.readFully(tail)
          digest.update(tail)
        }
      }
      digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
  }

  fun computeFullMd5(file: File): String? {
    if (!file.exists() || !file.isFile || file.length() <= 0L) return null
    return runCatching {
      val digest = MessageDigest.getInstance("MD5")
      val buffer = ByteArray(64 * 1024)
      FileInputStream(file).use { input ->
        while (true) {
          val read = input.read(buffer)
          if (read <= 0) break
          digest.update(buffer, 0, read)
        }
      }
      digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
  }

  fun orderKeepFirst(items: List<LocalFileItem>): List<LocalFileItem> {
    return items.sortedWith(
      compareBy<LocalFileItem> { folderPriorityPenalty(it.path) }
        .thenBy { if (COPY_NAME_REGEX.containsMatchIn(it.nameWithoutExtension())) 1 else 0 }
        .thenBy { if (it.lastModified > 0L) it.lastModified else Long.MAX_VALUE }
        .thenBy { it.path.length }
        .thenBy { it.path }
    )
  }

  private fun folderPriorityPenalty(path: String): Int {
    val norm = path.lowercase(Locale.ROOT)
    return when {
      norm.contains("/dcim/camera/") -> 0
      norm.contains("/dcim/") || norm.contains("/pictures/") -> 1
      norm.contains("/documents/") || norm.contains("/music/") || norm.contains("/movies/") -> 2
      norm.contains("/whatsapp/") || norm.contains("/telegram/") || norm.contains("/cache/") || norm.contains("/temp/") || norm.contains("/backup") -> 5
      norm.contains("/download") -> 3
      else -> 4
    }
  }

  private fun LocalFileItem.nameWithoutExtension(): String = name.substringBeforeLast('.', name)

  private fun File.toLocalFileItem(): LocalFileItem = LocalFileItem(
    name = name,
    path = absolutePath,
    isDirectory = false,
    sizeBytes = length(),
    lastModified = lastModified(),
    mimeType = FormatUtils.getMimeType(name),
    isHidden = name.startsWith("."),
  )
}
