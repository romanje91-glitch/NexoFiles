package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.CropAspectPreset
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PhotoEditParams
import com.example.data.model.PhotoExportFormat
import com.example.data.model.TacticalFilterPreset
import com.example.ui.components.BichromaticCard
import com.example.ui.components.NexoIcons
import com.example.ui.editor.PhotoEditorEngine
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.min

@Composable
fun NexoPhotoEditorScreen(
  sourceFilePath: String,
  isUploadingToGooglePhotos: Boolean = false,
  onBack: () -> Unit,
  onSavedToDevice: (File) -> Unit,
  onUploadCopyToGooglePhotos: (File, PhotoExportFormat) -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val strings = LocalStrings.current
  val scope = rememberCoroutineScope()
  val sourceFile = remember(sourceFilePath) { File(sourceFilePath) }

  var sourceBitmap by remember(sourceFilePath) { mutableStateOf<Bitmap?>(null) }
  var isLoadingBitmap by remember(sourceFilePath) { mutableStateOf(true) }
  var isExportingLocal by remember { mutableStateOf(false) }

  var editParams by remember(sourceFilePath) { mutableStateOf(PhotoEditParams()) }
  var exportFormat by remember { mutableStateOf(PhotoExportFormat.JPEG) }
  var activeToolTab by remember { mutableIntStateOf(0) }

  LaunchedEffect(sourceFilePath) {
    isLoadingBitmap = true
    sourceBitmap = PhotoEditorEngine.loadEditableBitmap(sourceFile, maxDimension = 2048)
    isLoadingBitmap = false
  }

  val composeColorFilter = remember(editParams) {
    val androidMatrix = PhotoEditorEngine.buildCompositeColorMatrix(editParams)
    ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array))
  }

  suspend fun renderAndSaveCopy(): Result<File> {
    val bmp = sourceBitmap ?: return Result.failure(IllegalStateException(strings.imageLoadError))
    return withContext(Dispatchers.Default) {
      val rendered = PhotoEditorEngine.renderEditedBitmap(bmp, editParams)
      val savedRes = PhotoEditorEngine.exportEditedBitmapToDevice(
        context = context,
        sourceName = sourceFile.name,
        renderedBitmap = rendered,
        format = exportFormat,
        quality = 95,
      )
      if (rendered !== bmp) {
        rendered.recycle()
      }
      savedRes
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
              .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            val tabs = listOf(
              strings.editorTabAdjust,
              strings.editorTabFilters,
              strings.editorTabTransform,
            )
            tabs.forEachIndexed { idx, label ->
              val selected = activeToolTab == idx
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                  .clickable { activeToolTab = idx }
                  .padding(vertical = 8.dp)
                  .testTag("photo_editor_tab_$idx"),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label.uppercase(),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          when (activeToolTab) {
            0 -> AdjustSlidersPanel(
              params = editParams,
              onChange = { editParams = it },
            )
            1 -> TacticalFiltersPanel(
              selected = editParams.filterPreset,
              onSelect = { editParams = editParams.copy(filterPreset = it) },
            )
            else -> TransformAndCropPanel(
              params = editParams,
              bitmapWidth = sourceBitmap?.width ?: 1000,
              bitmapHeight = sourceBitmap?.height ?: 1000,
              onChange = { editParams = it },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(
              onClick = {
                if (isExportingLocal || sourceBitmap == null) return@Button
                scope.launch {
                  isExportingLocal = true
                  val res = renderAndSaveCopy()
                  isExportingLocal = false
                  res.onSuccess { out -> onSavedToDevice(out) }
                }
              },
              enabled = sourceBitmap != null && !isExportingLocal && !isUploadingToGooglePhotos,
              modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .testTag("photo_editor_save_device"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
              if (isExportingLocal) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
              } else {
                Icon(NexoIcons.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text(
                text = strings.saveToDeviceBtn.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            OutlinedButton(
              onClick = {
                if (isExportingLocal || isUploadingToGooglePhotos || sourceBitmap == null) return@OutlinedButton
                scope.launch {
                  isExportingLocal = true
                  val res = renderAndSaveCopy()
                  isExportingLocal = false
                  res.onSuccess { out ->
                    onUploadCopyToGooglePhotos(out, exportFormat)
                  }
                }
              },
              enabled = sourceBitmap != null && !isExportingLocal && !isUploadingToGooglePhotos,
              modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .testTag("photo_editor_upload_google_photos"),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
              if (isUploadingToGooglePhotos) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  color = MaterialTheme.colorScheme.secondary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
              } else {
                Icon(NexoIcons.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
              }
              Text(
                text = strings.uploadCopyGooglePhotosBtn.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("photo_editor_back"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = strings.back,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = strings.photoEditorTitle.uppercase(),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${sourceFile.name} · ${strings.photoEditorNonDestructiveSub}",
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
              .padding(2.dp),
          ) {
            PhotoExportFormat.entries.forEach { fmt ->
              val selected = exportFormat == fmt
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(3.dp))
                  .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                  .clickable { exportFormat = fmt }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
                  .testTag("photo_editor_fmt_${fmt.name.lowercase()}"),
              ) {
                Text(
                  text = fmt.name,
                  style = MonoTnumStyle,
                  fontWeight = FontWeight.Bold,
                  color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          if (editParams.hasChanges) {
            TextButton(
              onClick = { editParams = PhotoEditParams() },
              modifier = Modifier.testTag("photo_editor_reset"),
            ) {
              Text(strings.resetEditsBtn.uppercase(), color = NexoTheme.semantic.evaOrange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(12.dp),
        contentAlignment = Alignment.Center,
      ) {
        val bmp = sourceBitmap
        when {
          isLoadingBitmap -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          bmp == null -> BichromaticCard {
            Text(
              text = strings.imageLoadError,
              modifier = Modifier.padding(20.dp),
              color = NexoTheme.semantic.evaRed,
            )
          }
          else -> LiveEditedPhotoCanvas(
            bitmap = bmp,
            params = editParams,
            colorFilter = composeColorFilter,
            showCropHandles = activeToolTab == 2 || !editParams.cropRect.isFullFrame,
            onCropRectChange = { updatedRect ->
              editParams = editParams.copy(
                cropAspect = CropAspectPreset.FREE,
                cropRect = updatedRect,
              )
            },
          )
        }
      }
    }
  }
}

@Composable
private fun LiveEditedPhotoCanvas(
  bitmap: Bitmap,
  params: PhotoEditParams,
  colorFilter: ColorFilter,
  showCropHandles: Boolean,
  onCropRectChange: (NormalizedCropRect) -> Unit,
) {
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  val neonAccent = MaterialTheme.colorScheme.primary
  val evaWarning = NexoTheme.semantic.evaOrange

  val rotatedQuarter = ((params.rotationDegrees / 90) % 2) != 0
  val effectiveW = if (rotatedQuarter) bitmap.height.toFloat() else bitmap.width.toFloat()
  val effectiveH = if (rotatedQuarter) bitmap.width.toFloat() else bitmap.height.toFloat()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { boxSize = it },
    contentAlignment = Alignment.Center,
  ) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = "Preview",
      contentScale = ContentScale.Fit,
      colorFilter = colorFilter,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          rotationZ = params.rotationDegrees.toFloat()
          scaleX = if (params.flipHorizontal) -1f else 1f
          scaleY = if (params.flipVertical) -1f else 1f
        },
    )

    if (showCropHandles && boxSize.width > 0 && boxSize.height > 0 && effectiveW > 0f && effectiveH > 0f) {
      val fitScale = min(boxSize.width / effectiveW, boxSize.height / effectiveH)
      val drawnW = effectiveW * fitScale
      val drawnH = effectiveH * fitScale
      val offsetX = (boxSize.width - drawnW) / 2f
      val offsetY = (boxSize.height - drawnH) / 2f

      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(drawnW, drawnH, offsetX, offsetY, params.cropRect) {
            detectDragGestures { change, dragAmount ->
              if (drawnW <= 1f || drawnH <= 1f) return@detectDragGestures
              val pos = change.position
              val dx = dragAmount.x / drawnW
              val dy = dragAmount.y / drawnH
              val cur = params.cropRect
              val leftPx = offsetX + cur.left * drawnW
              val rightPx = offsetX + cur.right * drawnW
              val topPx = offsetY + cur.top * drawnH
              val bottomPx = offsetY + cur.bottom * drawnH

              val nearLeft = kotlin.math.abs(pos.x - leftPx) < 90f
              val nearRight = kotlin.math.abs(pos.x - rightPx) < 90f
              val nearTop = kotlin.math.abs(pos.y - topPx) < 90f
              val nearBottom = kotlin.math.abs(pos.y - bottomPx) < 90f

              val next = when {
                nearLeft || nearRight || nearTop || nearBottom -> {
                  NormalizedCropRect(
                    left = if (nearLeft) (cur.left + dx).coerceIn(0f, cur.right - 0.15f) else cur.left,
                    top = if (nearTop) (cur.top + dy).coerceIn(0f, cur.bottom - 0.15f) else cur.top,
                    right = if (nearRight) (cur.right + dx).coerceIn(cur.left + 0.15f, 1f) else cur.right,
                    bottom = if (nearBottom) (cur.bottom + dy).coerceIn(cur.top + 0.15f, 1f) else cur.bottom,
                  )
                }
                else -> {
                  val w = cur.right - cur.left
                  val h = cur.bottom - cur.top
                  val nl = (cur.left + dx).coerceIn(0f, 1f - w)
                  val nt = (cur.top + dy).coerceIn(0f, 1f - h)
                  NormalizedCropRect(nl, nt, nl + w, nt + h)
                }
              }
              change.consume()
              onCropRectChange(next)
            }
          },
      ) {
        val cl = offsetX + params.cropRect.left * drawnW
        val ct = offsetY + params.cropRect.top * drawnH
        val cw = (params.cropRect.right - params.cropRect.left) * drawnW
        val ch = (params.cropRect.bottom - params.cropRect.top) * drawnH

        val maskColor = Color.Black.copy(alpha = 0.56f)
        drawRect(maskColor, Offset(offsetX, offsetY), Size(drawnW, (ct - offsetY).coerceAtLeast(0f)))
        drawRect(maskColor, Offset(offsetX, ct + ch), Size(drawnW, (offsetY + drawnH - (ct + ch)).coerceAtLeast(0f)))
        drawRect(maskColor, Offset(offsetX, ct), Size((cl - offsetX).coerceAtLeast(0f), ch))
        drawRect(maskColor, Offset(cl + cw, ct), Size((offsetX + drawnW - (cl + cw)).coerceAtLeast(0f), ch))

        drawRect(
          color = neonAccent,
          topLeft = Offset(cl, ct),
          size = Size(cw, ch),
          style = Stroke(width = 2.dp.toPx()),
        )
        for (i in 1..2) {
          val gx = cl + (cw * i / 3f)
          val gy = ct + (ch * i / 3f)
          drawLine(neonAccent.copy(alpha = 0.35f), Offset(gx, ct), Offset(gx, ct + ch), strokeWidth = 1.dp.toPx())
          drawLine(neonAccent.copy(alpha = 0.35f), Offset(cl, gy), Offset(cl + cw, gy), strokeWidth = 1.dp.toPx())
        }

        val bracket = 16.dp.toPx()
        val strokePx = 3.2.dp.toPx()
        drawLine(evaWarning, Offset(cl, ct), Offset(cl + bracket, ct), strokePx)
        drawLine(evaWarning, Offset(cl, ct), Offset(cl, ct + bracket), strokePx)
        drawLine(evaWarning, Offset(cl + cw - bracket, ct), Offset(cl + cw, ct), strokePx)
        drawLine(evaWarning, Offset(cl + cw, ct), Offset(cl + cw, ct + bracket), strokePx)
        drawLine(evaWarning, Offset(cl, ct + ch), Offset(cl + bracket, ct + ch), strokePx)
        drawLine(evaWarning, Offset(cl, ct + ch - bracket), Offset(cl, ct + ch), strokePx)
        drawLine(evaWarning, Offset(cl + cw - bracket, ct + ch), Offset(cl + cw, ct + ch), strokePx)
        drawLine(evaWarning, Offset(cl + cw, ct + ch - bracket), Offset(cl + cw, ct + ch), strokePx)
      }
    }
  }
}

@Composable
private fun AdjustSlidersPanel(
  params: PhotoEditParams,
  onChange: (PhotoEditParams) -> Unit,
) {
  val strings = LocalStrings.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(176.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    NeonAdjustmentSlider(
      label = strings.sliderBrightness,
      valueText = String.format(Locale.US, "%+.0f", params.brightness),
      value = params.brightness,
      valueRange = -100f..100f,
      onValueChange = { onChange(params.copy(brightness = it)) },
    )
    NeonAdjustmentSlider(
      label = strings.sliderContrast,
      valueText = String.format(Locale.US, "%.2fx", params.contrast),
      value = params.contrast,
      valueRange = 0.5f..1.8f,
      onValueChange = { onChange(params.copy(contrast = it)) },
    )
    NeonAdjustmentSlider(
      label = strings.sliderSaturation,
      valueText = String.format(Locale.US, "%.2fx", params.saturation),
      value = params.saturation,
      valueRange = 0f..2f,
      onValueChange = { onChange(params.copy(saturation = it)) },
    )
    NeonAdjustmentSlider(
      label = strings.sliderWarmth,
      valueText = String.format(Locale.US, "%+.0f", params.warmth),
      value = params.warmth,
      valueRange = -100f..100f,
      onValueChange = { onChange(params.copy(warmth = it)) },
    )
  }
}

@Composable
private fun NeonAdjustmentSlider(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
      Text(
        text = valueText,
        style = MonoTnumStyle,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.height(28.dp),
      colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outline,
      ),
    )
  }
}

@Composable
private fun TacticalFiltersPanel(
  selected: TacticalFilterPreset,
  onSelect: (TacticalFilterPreset) -> Unit,
) {
  val strings = LocalStrings.current
  val presets = listOf(
    TacticalFilterPreset.ORIGINAL to strings.filterOriginal,
    TacticalFilterPreset.VIVID to strings.filterVivid,
    TacticalFilterPreset.HIGH_CONTRAST to strings.filterHighContrast,
    TacticalFilterPreset.MONO to strings.filterMono,
    TacticalFilterPreset.NIGHT_VIOLET to strings.filterNightViolet,
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    presets.forEach { (preset, label) ->
      val isSelected = selected == preset
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(preset) },
        leadingIcon = {
          Icon(
            imageVector = NexoIcons.FilterVintage,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        label = { Text(label.uppercase(), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        shape = RoundedCornerShape(6.dp),
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = MaterialTheme.colorScheme.primary,
          selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
          selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.testTag("filter_chip_${preset.name.lowercase()}"),
      )
    }
  }
}

@Composable
private fun TransformAndCropPanel(
  params: PhotoEditParams,
  bitmapWidth: Int,
  bitmapHeight: Int,
  onChange: (PhotoEditParams) -> Unit,
) {
  val strings = LocalStrings.current
  val rotatedQuarter = ((params.rotationDegrees / 90) % 2) != 0
  val effW = if (rotatedQuarter) bitmapHeight else bitmapWidth
  val effH = if (rotatedQuarter) bitmapWidth else bitmapHeight

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val aspects = listOf(
        CropAspectPreset.FREE to strings.cropFree,
        CropAspectPreset.SQUARE_1_1 to "1:1",
        CropAspectPreset.STANDARD_4_3 to "4:3",
        CropAspectPreset.WIDE_16_9 to "16:9",
      )
      aspects.forEach { (aspect, label) ->
        val selected = params.cropAspect == aspect
        FilterChip(
          selected = selected,
          onClick = {
            val rect = PhotoEditorEngine.computeAspectCropRect(aspect, effW, effH)
            onChange(params.copy(cropAspect = aspect, cropRect = rect))
          },
          leadingIcon = {
            Icon(NexoIcons.Crop, contentDescription = null, modifier = Modifier.size(15.dp))
          },
          label = { Text(label.uppercase()) },
          shape = RoundedCornerShape(6.dp),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier.testTag("crop_aspect_${aspect.name.lowercase()}"),
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = {
          onChange(params.copy(rotationDegrees = (params.rotationDegrees + 90) % 360))
        },
        modifier = Modifier
          .weight(1f)
          .testTag("editor_rotate_90"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
      ) {
        Icon(NexoIcons.RotateRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(strings.rotate90Label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
      }

      OutlinedButton(
        onClick = {
          onChange(params.copy(flipHorizontal = !params.flipHorizontal))
        },
        modifier = Modifier
          .weight(1f)
          .testTag("editor_flip_h"),
        border = BorderStroke(
          1.dp,
          if (params.flipHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
      ) {
        Icon(NexoIcons.FlipHorizontal, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(strings.flipHorizontalLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
      }

      OutlinedButton(
        onClick = {
          onChange(params.copy(flipVertical = !params.flipVertical))
        },
        modifier = Modifier
          .weight(1f)
          .testTag("editor_flip_v"),
        border = BorderStroke(
          1.dp,
          if (params.flipVertical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
      ) {
        Icon(NexoIcons.FlipVertical, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(strings.flipVerticalLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
      }
    }
  }
}
