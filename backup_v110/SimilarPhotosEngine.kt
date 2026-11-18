package com.example.data.repo

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.SimilarPhotoGroup
import com.example.data.model.SimilarPhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SimilarPhotosEngine(private val context: Context? = null) {

  companion object {
    const val DEFAULT_MIN_SIMILARITY_PERCENT = 88
    const val BURST_WINDOW_MS = 15_000L
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")

    fun computeDHashFromGrayscale9x8(gray9x8: IntArray): Long {
      require(gray9x8.size >= 72) { "Expected 9x8 (72) grayscale samples" }
      var hash = 0L
      var bitIndex = 0
      for (y in 0 until 8) {
        val rowOffset = y * 9
        for (x in 0 until 8) {
          if (gray9x8[rowOffset + x] < gray9x8[rowOffset + x + 1]) {
            hash = hash or (1L shl bitIndex)
          }
          bitIndex++
        }
      }
      return hash
    }

    fun computeDHash(bitmap: Bitmap): Long {
      val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
      val pixels = IntArray(9 * 8)
      scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
      if (scaled !== bitmap) scaled.recycle()
      val gray = IntArray(72) { idx ->
        val c = pixels[idx]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        (299 * r + 587 * g + 114 * b) / 1000
      }
      return computeDHashFromGrayscale9x8(gray)
    }

    fun hammingDistance(hashA: Long, hashB: Long): Int = java.lang.Long.bitCount(hashA xor hashB)

    fun similarityPercent(distance: Int): Int {
      val clamped = distance.coerceIn(0, 64)
      return (((64 - clamped) / 64.0) * 100.0).roundToInt()
    }

    fun estimateSharpness(bitmap: Bitmap): Double {
      val targetW = min(bitmap.width, 96).coerceAtLeast(8)
      val targetH = min(bitmap.height, 96).coerceAtLeast(8)
      val sample = if (bitmap.width != targetW || bitmap.height != targetH) {
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
      } else {
        bitmap
      }
      val pixels = IntArray(targetW * targetH)
      sample.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
      if (sample !== bitmap) sample.recycle()
      val lum = IntArray(pixels.size) { i ->
        val c = pixels[i]
        ((299 * ((c shr 16) and 0xFF)) + (587 * ((c shr 8) and 0xFF)) + (114 * (c and 0xFF))) / 1000
      }

      var sumSq = 0.0
      var count = 0
      for (y in 1 until targetH - 1) {
        val row = y * targetW
        for (x in 1 until targetW - 1) {
          val center = lum[row + x]
          val lap = (4 * center) - lum[row + x - 1] - lum[row + x + 1] - lum[row - targetW + x] - lum[row + targetW + x]
          sumSq += (lap * lap).toDouble()
          count++
        }
      }
      return if (count > 0) sumSq / count else 0.0
    }
  }

  private data class RawPhotoCandidate(
    val id: String,
    val name: String,
    val path: String,
    val uriString: String,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val mediaWidth: Int,
    val mediaHeight: Int,
  )

  suspend fun scanSimilarPhotos(
    customDirectories: List<File>? = null,
    minSimilarityPercent: Int = DEFAULT_MIN_SIMILARITY_PERCENT,
    maxPhotosToAnalyze: Int = 550,
    onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> },
  ): List<SimilarPhotoGroup> = withContext(Dispatchers.IO) {
    val candidates = if (customDirectories != null) {
      collectFromDirectories(customDirectories, maxPhotosToAnalyze)
    } else {
      val fromMediaStore = queryMediaStoreCandidates(maxPhotosToAnalyze)
      if (fromMediaStore.size >= 2) {
        fromMediaStore
      } else {
        val root = Environment.getExternalStorageDirectory()
        val fallbackDirs = if (root != null) {
          listOf(
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Download"),
            File(root, "WhatsApp/Media/WhatsApp Images"),
          )
        } else {
          emptyList()
        }
        (fromMediaStore + collectFromDirectories(fallbackDirs, maxPhotosToAnalyze))
          .distinctBy { it.path }
          .take(maxPhotosToAnalyze)
      }
    }

    if (candidates.size < 2) {
      onProgress(candidates.size, candidates.size.coerceAtLeast(1))
      return@withContext emptyList()
    }

    val analyzed = ArrayList<SimilarPhotoItem>(candidates.size)
    val total = candidates.size

    candidates.forEachIndexed { idx, raw ->
      coroutineContext.ensureActive()
      decodeAndAnalyzeCandidate(raw)?.let { analyzed.add(it) }
      if (idx % 10 == 0 || idx == total - 1) {
        onProgress(idx + 1, total)
      }
    }

    groupAnalyzedPhotos(analyzed, minSimilarityPercent)
  }

  fun groupAnalyzedPhotos(
    photos: List<SimilarPhotoItem>,
    minSimilarityPercent: Int = DEFAULT_MIN_SIMILARITY_PERCENT,
  ): List<SimilarPhotoGroup> {
    if (photos.size < 2) return emptyList()
    val n = photos.size
    val parent = IntArray(n) { it }
    fun find(x: Int): Int {
      var cur = x
      while (parent[cur] != cur) {
        parent[cur] = parent[parent[cur]]
        cur = parent[cur]
      }
      return cur
    }
    fun union(a: Int, b: Int) {
      val ra = find(a)
      val rb = find(b)
      if (ra != rb) parent[rb] = ra
    }

    for (i in 0 until n) {
      val a = photos[i]
      val ratioA = if (a.height > 0) a.width.toFloat() / a.height.toFloat() else 1f
      for (j in i + 1 until n) {
        val b = photos[j]
        val ratioB = if (b.height > 0) b.width.toFloat() / b.height.toFloat() else 1f
        val aspectCompatible = abs(ratioA - ratioB) <= 0.20f
        val invAspectCompatible = abs(ratioA - (1f / ratioB.coerceAtLeast(0.05f))) <= 0.12f
        val isBurstPair = a.dateTakenMs > 0L && b.dateTakenMs > 0L && abs(a.dateTakenMs - b.dateTakenMs) <= BURST_WINDOW_MS
        if (!aspectCompatible && !invAspectCompatible && !isBurstPair) continue

        val dist = hammingDistance(a.dHash, b.dHash)
        if (similarityPercent(dist) >= minSimilarityPercent) union(i, j)
      }
    }

    val clusters = HashMap<Int, MutableList<Int>>()
    for (i in 0 until n) clusters.getOrPut(find(i)) { mutableListOf() }.add(i)

    val groups = mutableListOf<SimilarPhotoGroup>()
    for ((_, indices) in clusters) {
      if (indices.size < 2) continue
      var minDist = 64
      var hasBurst = false
      for (aIdx in indices.indices) {
        for (bIdx in aIdx + 1 until indices.size) {
          val i = indices[aIdx]
          val j = indices[bIdx]
          val d = hammingDistance(photos[i].dHash, photos[j].dHash)
          if (d < minDist) minDist = d
          val ta = photos[i].dateTakenMs
          val tb = photos[j].dateTakenMs
          if (ta > 0L && tb > 0L && abs(ta - tb) <= BURST_WINDOW_MS) hasBurst = true
        }
      }

      val clusterItems = indices.map { photos[it] }
      val bestItem = clusterItems.maxWithOrNull(
        compareBy<SimilarPhotoItem> { qualityScore(it) }.thenBy { it.sizeBytes }
      )
      val markedItems = clusterItems
        .map { item -> item.copy(isBestShot = item.path == bestItem?.path) }
        .sortedWith(
          compareByDescending<SimilarPhotoItem> { it.isBestShot }
            .thenByDescending { qualityScore(it) }
        )

      groups.add(
        SimilarPhotoGroup(
          id = "sim_${markedItems.first().dHash.toULong().toString(16)}_${groups.size}",
          maxSimilarityPercent = similarityPercent(minDist).coerceIn(minSimilarityPercent, 100),
          minHammingDistance = minDist,
          items = markedItems,
          isBurstGroup = hasBurst,
        )
      )
    }

    return groups.sortedWith(
      compareByDescending<SimilarPhotoGroup> { it.maxSimilarityPercent }
        .thenByDescending { it.recoverableBytes }
    )
  }

  private fun qualityScore(item: SimilarPhotoItem): Double {
    val pixels = (item.width.toLong().coerceAtLeast(1L) * item.height.toLong().coerceAtLeast(1L)).toDouble()
    val sharpnessFactor = 1.0 + (item.sharpnessScore.coerceIn(0.0, 5000.0) / 1200.0)
    return pixels * sharpnessFactor
  }

  private fun decodeAndAnalyzeCandidate(raw: RawPhotoCandidate): SimilarPhotoItem? {
    val file = File(raw.path)
    if (!file.exists() || !file.canRead() || file.length() <= 0L) return null
    return runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      val origW = bounds.outWidth.takeIf { it > 0 } ?: raw.mediaWidth
      val origH = bounds.outHeight.takeIf { it > 0 } ?: raw.mediaHeight
      if (origW <= 0 || origH <= 0) return null

      var sampleSize = 1
      while ((origW / sampleSize) > 256 || (origH / sampleSize) > 256) sampleSize *= 2
      val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
      val dHash = computeDHash(decoded)
      val sharpness = estimateSharpness(decoded)
      decoded.recycle()

      val exifTaken = readExifTimestampMs(file.absolutePath)
      val bestTimestamp = exifTaken ?: raw.dateTakenMs.takeIf { it > 0L } ?: file.lastModified()

      SimilarPhotoItem(
        id = raw.id,
        name = raw.name,
        path = file.absolutePath,
        uriString = raw.uriString,
        width = origW,
        height = origH,
        sizeBytes = file.length(),
        dateTakenMs = bestTimestamp,
        dHash = dHash,
        sharpnessScore = sharpness,
      )
    }.getOrNull()
  }

  private fun readExifTimestampMs(path: String): Long? {
    return runCatching {
      val exif = ExifInterface(path)
      val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        ?: return@runCatching null
      SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(rawDate)?.time
    }.getOrNull()
  }

  private fun queryMediaStoreCandidates(limit: Int): List<RawPhotoCandidate> {
    val ctx = context ?: return emptyList()
    val list = mutableListOf<RawPhotoCandidate>()
    runCatching {
      val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
      )
      ctx.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
        val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
        val wCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
        val hCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

        while (cursor.moveToNext() && list.size < limit) {
          val id = cursor.getLong(idCol)
          val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
          if (path.isBlank() || path.contains("/.nexo_")) continue
          val name = cursor.getString(nameCol) ?: File(path).name
          val size = cursor.getLong(sizeCol)
          if (size <= 0L) continue
          val taken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
          val modSec = if (modCol >= 0) cursor.getLong(modCol) else 0L
          val dateMs = if (taken > 0L) taken else modSec * 1000L
          val w = if (wCol >= 0) cursor.getInt(wCol) else 0
          val h = if (hCol >= 0) cursor.getInt(hCol) else 0
          val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
          list.add(RawPhotoCandidate(id.toString(), name, path, contentUri, size, dateMs, w, h))
        }
      }
    }
    return list
  }

  private fun collectFromDirectories(dirs: List<File>, limit: Int): List<RawPhotoCandidate> {
    val collected = mutableListOf<RawPhotoCandidate>()
    fun walk(dir: File, depth: Int) {
      if (depth > 6 || collected.size >= limit || !dir.exists() || !dir.canRead()) return
      if (dir.name.startsWith(".")) return
      val children = dir.listFiles() ?: return
      for (child in children) {
        if (collected.size >= limit) break
        if (child.isDirectory) {
          walk(child, depth + 1)
        } else if (child.isFile && child.length() > 0L) {
          val ext = child.extension.lowercase(Locale.ROOT)
          if (ext in IMAGE_EXTENSIONS) {
            collected.add(
              RawPhotoCandidate(
                id = child.absolutePath.hashCode().toString(),
                name = child.name,
                path = child.absolutePath,
                uriString = "",
                sizeBytes = child.length(),
                dateTakenMs = child.lastModified(),
                mediaWidth = 0,
                mediaHeight = 0,
              )
            )
          }
        }
      }
    }
    dirs.forEach { walk(it, 0) }
    return collected
  }
}
