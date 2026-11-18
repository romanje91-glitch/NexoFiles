package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.data.model.CropAspectPreset
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PhotoEditParams
import com.example.data.model.PhotoExportFormat
import com.example.data.model.TacticalFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object PhotoEditorEngine {

  fun buildCompositeColorMatrix(params: PhotoEditParams): ColorMatrix {
    val result = ColorMatrix()
    val presetMatrix = buildFilterPresetMatrix(params.filterPreset)
    result.postConcat(presetMatrix)

    if (params.saturation != 1f) {
      val satMatrix = ColorMatrix().apply {
        setSaturation(params.saturation.coerceIn(0f, 2.5f))
      }
      result.postConcat(satMatrix)
    }

    val c = params.contrast.coerceIn(0.4f, 2.2f)
    val brightnessOffset = (params.brightness.coerceIn(-100f, 100f) * 0.9f)
    val translate = (1f - c) * 128f + brightnessOffset
    val contrastBrightnessMatrix = ColorMatrix(
      floatArrayOf(
        c, 0f, 0f, 0f, translate,
        0f, c, 0f, 0f, translate,
        0f, 0f, c, 0f, translate,
        0f, 0f, 0f, 1f, 0f,
      )
    )
    result.postConcat(contrastBrightnessMatrix)

    if (params.warmth != 0f) {
      val w = params.warmth.coerceIn(-100f, 100f) / 100f
      val rShift = w * 28f
      val gShift = w * 6f
      val bShift = -w * 28f
      val warmthMatrix = ColorMatrix(
        floatArrayOf(
          1f, 0f, 0f, 0f, rShift,
          0f, 1f, 0f, 0f, gShift,
          0f, 0f, 1f, 0f, bShift,
          0f, 0f, 0f, 1f, 0f,
        )
      )
      result.postConcat(warmthMatrix)
    }

    return result
  }

  fun buildFilterPresetMatrix(preset: TacticalFilterPreset): ColorMatrix {
    return when (preset) {
      TacticalFilterPreset.ORIGINAL -> ColorMatrix()
      TacticalFilterPreset.VIVID -> {
        val vivid = ColorMatrix().apply { setSaturation(1.38f) }
        vivid.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.12f, 0f, 0f, 0f, -10f,
              0f, 1.12f, 0f, 0f, -10f,
              0f, 0f, 1.12f, 0f, -10f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
        vivid
      }
      TacticalFilterPreset.HIGH_CONTRAST -> ColorMatrix(
        floatArrayOf(
          1.36f, 0f, 0f, 0f, -38f,
          0f, 1.36f, 0f, 0f, -38f,
          0f, 0f, 1.36f, 0f, -38f,
          0f, 0f, 0f, 1f, 0f,
        )
      )
      TacticalFilterPreset.MONO -> {
        val mono = ColorMatrix().apply { setSaturation(0f) }
        mono.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.15f, 0f, 0f, 0f, -12f,
              0f, 1.15f, 0f, 0f, -12f,
              0f, 0f, 1.15f, 0f, -12f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
        mono
      }
      TacticalFilterPreset.NIGHT_VIOLET -> {
        val base = ColorMatrix().apply { setSaturation(0.88f) }
        base.postConcat(
          ColorMatrix(
            floatArrayOf(
              1.06f, 0.04f, 0.12f, 0f, 6f,
              0.02f, 1.04f, 0.06f, 0f, -6f,
              0.14f, 0.05f, 1.24f, 0f, 18f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        )
        base
      }
    }
  }

  fun computeAspectCropRect(
    aspectPreset: CropAspectPreset,
    imageWidth: Int,
    imageHeight: Int,
  ): NormalizedCropRect {
    val targetRatio = aspectPreset.ratio ?: return NormalizedCropRect(0f, 0f, 1f, 1f)
    if (imageWidth <= 0 || imageHeight <= 0) return NormalizedCropRect(0f, 0f, 1f, 1f)
    val currentRatio = imageWidth.toFloat() / imageHeight.toFloat()
    return if (currentRatio > targetRatio) {
      val normWidth = (targetRatio / currentRatio).coerceIn(0.1f, 1f)
      val margin = (1f - normWidth) / 2f
      NormalizedCropRect(left = margin, top = 0f, right = 1f - margin, bottom = 1f)
    } else {
      val normHeight = (currentRatio / targetRatio).coerceIn(0.1f, 1f)
      val margin = (1f - normHeight) / 2f
      NormalizedCropRect(left = 0f, top = margin, right = 1f, bottom = 1f - margin)
    }
  }

  fun renderEditedBitmap(source: Bitmap, params: PhotoEditParams): Bitmap {
    val matrix = Matrix()
    val sx = if (params.flipHorizontal) -1f else 1f
    val sy = if (params.flipVertical) -1f else 1f
    if (sx != 1f || sy != 1f) {
      matrix.postScale(sx, sy, source.width / 2f, source.height / 2f)
    }
    val rot = ((params.rotationDegrees % 360) + 360) % 360
    if (rot != 0) {
      matrix.postRotate(rot.toFloat())
    }

    val transformed = if (!matrix.isIdentity) {
      Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
      source
    }

    val tw = transformed.width.coerceAtLeast(1)
    val th = transformed.height.coerceAtLeast(1)
    val leftNorm = params.cropRect.left.coerceIn(0f, 0.90f)
    val topNorm = params.cropRect.top.coerceIn(0f, 0.90f)
    val rightNorm = params.cropRect.right.coerceIn(leftNorm + 0.08f, 1f)
    val bottomNorm = params.cropRect.bottom.coerceIn(topNorm + 0.08f, 1f)

    val cropX = (leftNorm * tw).roundToInt().coerceIn(0, tw - 1)
    val cropY = (topNorm * th).roundToInt().coerceIn(0, th - 1)
    val cropW = ((rightNorm - leftNorm) * tw).roundToInt().coerceIn(1, tw - cropX)
    val cropH = ((bottomNorm - topNorm) * th).roundToInt().coerceIn(1, th - cropY)

    val output = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(buildCompositeColorMatrix(params))
    }
    val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH)
    val dstRect = android.graphics.Rect(0, 0, cropW, cropH)
    canvas.drawBitmap(transformed, srcRect, dstRect, paint)

    if (transformed !== source) {
      transformed.recycle()
    }
    return output
  }

  suspend fun loadEditableBitmap(file: File, maxDimension: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || !file.canRead()) return@withContext null
    runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      val origW = bounds.outWidth
      val origH = bounds.outHeight
      if (origW <= 0 || origH <= 0) return@runCatching null

      var sample = 1
      while ((origW / sample) > maxDimension || (origH / sample) > maxDimension) {
        sample *= 2
      }
      val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
      applyExifOrientation(decoded, file.absolutePath)
    }.getOrNull()
  }

  suspend fun exportEditedBitmapToDevice(
    context: Context?,
    sourceName: String,
    renderedBitmap: Bitmap,
    format: PhotoExportFormat = PhotoExportFormat.JPEG,
    quality: Int = 95,
    outputDir: File? = null,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        ?: File("/storage/emulated/0/Pictures")
      val targetDir = outputDir ?: File(picturesRoot, "NexoFiles")
      if (!targetDir.exists() && !targetDir.mkdirs()) {
        val fallback = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "NexoFiles") }
          ?: File(context?.cacheDir ?: File("."), "NexoFiles")
        fallback.mkdirs()
        return@runCatching writeBitmapFile(context, fallback, sourceName, renderedBitmap, format, quality)
      }
      writeBitmapFile(context, targetDir, sourceName, renderedBitmap, format, quality)
    }
  }

  private fun writeBitmapFile(
    context: Context?,
    dir: File,
    sourceName: String,
    bitmap: Bitmap,
    format: PhotoExportFormat,
    quality: Int,
  ): File {
    val stem = sourceName.substringBeforeLast('.', sourceName)
      .replace(Regex("[^a-zA-Z0-9_-]"), "_")
      .take(36)
      .ifBlank { "IMG" }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outFile = File(dir, "${stem}_NexoEdit_$stamp.${format.ext}")
    val compressFormat = when (format) {
      PhotoExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
      PhotoExportFormat.PNG -> Bitmap.CompressFormat.PNG
    }
    FileOutputStream(outFile).use { fos ->
      val ok = bitmap.compress(compressFormat, min(100, quality.coerceAtLeast(70)), fos)
      if (!ok) throw IllegalStateException("No se pudo comprimir la imagen editada")
      fos.flush()
    }
    if (context != null) {
      runCatching {
        MediaScannerConnection.scanFile(
          context,
          arrayOf(outFile.absolutePath),
          arrayOf(format.mimeType),
          null,
        )
      }
    }
    return outFile
  }

  private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
    val orientation = runCatching {
      ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      else -> return bitmap
    }
    return runCatching {
      val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      if (out !== bitmap) bitmap.recycle()
      out
    }.getOrDefault(bitmap)
  }
}
