package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.BichromaticCard
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private class PdfRendererHolder(
  private val pfd: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
) {
  val pageCount: Int get() = renderer.pageCount
  private val mutex = Mutex()

  suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    mutex.withLock {
      if (index !in 0 until renderer.pageCount) return@withLock null
      renderer.openPage(index).use { page ->
        val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
        val width = targetWidthPx.coerceIn(600, 2200)
        val height = (width * aspect).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(AndroidColor.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bmp
      }
    }
  }

  fun close() {
    runCatching { renderer.close() }
    runCatching { pfd.close() }
  }
}

@Composable
fun PdfViewerScreen(
  filePath: String,
  isVaultMode: Boolean = false,
  onBack: () -> Unit,
  onShare: (File) -> Unit,
  onOpenExternal: (File) -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current

  val pdfFile = remember(filePath) { File(filePath) }
  var holder by remember(filePath) { mutableStateOf<PdfRendererHolder?>(null) }
  var pageCount by remember(filePath) { mutableIntStateOf(0) }
  var currentPage by remember(filePath) { mutableIntStateOf(0) }
  var pageBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
  var isLoading by remember(filePath) { mutableStateOf(true) }
  var errorMessage by remember(filePath) { mutableStateOf<String?>(null) }
  var zoomScale by remember(filePath) { mutableFloatStateOf(1f) }

  val density = LocalDensity.current
  val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx().roundToInt() }

  LaunchedEffect(filePath) {
    isLoading = true
    errorMessage = null
    val openResult = withContext(Dispatchers.IO) {
      runCatching {
        if (!pdfFile.exists() || !pdfFile.canRead()) {
          throw IllegalStateException(strings.pdfAccessError)
        }
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        PdfRendererHolder(pfd, renderer)
      }
    }
    openResult.onSuccess { opened ->
      holder = opened
      pageCount = opened.pageCount
      if (opened.pageCount == 0) {
        errorMessage = strings.pdfEmptyError
        isLoading = false
      }
    }.onFailure { err ->
      errorMessage = when (err) {
        is SecurityException -> strings.pdfPasswordError
        else -> err.localizedMessage ?: strings.pdfGenericError
      }
      isLoading = false
    }
  }

  LaunchedEffect(holder, currentPage) {
    val activeHolder = holder ?: return@LaunchedEffect
    if (activeHolder.pageCount == 0) return@LaunchedEffect
    isLoading = true
    pageBitmap = activeHolder.renderPage(currentPage, (screenWidthPx * 1.6f).roundToInt())
    isLoading = false
  }

  DisposableEffect(holder) {
    val currentHolder = holder
    onDispose {
      currentHolder?.close()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // NERV HUD Top bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("pdf_viewer_back"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = strings.back,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = pdfFile.name.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (isVaultMode) {
                Surface(
                  shape = RoundedCornerShape(3.dp),
                  color = NexoTheme.semantic.evaRed,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Lock,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onError,
                      modifier = Modifier.size(11.dp),
                    )
                    Text(
                      text = if (strings.isEnglish) "VAULT" else "BÓVEDA",
                      style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onError,
                    )
                  }
                }
              }
            }
            Text(
              text = if (pageCount > 0) {
                "${strings.pdfPageOf(currentPage + 1, pageCount)} // ${FormatUtils.formatBytes(pdfFile.length())}"
              } else {
                FormatUtils.formatBytes(pdfFile.length())
              },
              style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          // Vault isolation: strictly forbid external app opening or sharing inside Private Vault
          if (!isVaultMode) {
            IconButton(onClick = { onShare(pdfFile) }) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = strings.share,
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
            IconButton(onClick = { onOpenExternal(pdfFile) }) {
              Icon(
                imageVector = NexoIcons.OpenInNew,
                contentDescription = strings.openExternal,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
        HorizontalDivider(
          color = if (isVaultMode) {
            NexoTheme.semantic.evaRed.copy(alpha = 0.7f)
          } else {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
          },
        )
      }
    }

    // Page canvas
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .pointerInput(filePath) {
          detectTransformGestures { _, _, zoom, _ ->
            zoomScale = (zoomScale * zoom).coerceIn(0.8f, 3f)
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      when {
        errorMessage != null -> {
          BichromaticCard(
            modifier = Modifier.padding(24.dp),
            elevated = true,
            accentBorder = true,
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = NexoIcons.PictureAsPdf,
                contentDescription = null,
                tint = NexoTheme.semantic.evaRed,
                modifier = Modifier.size(44.dp),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
              )
              if (!isVaultMode) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onOpenExternal(pdfFile) }) {
                  Text(
                    text = strings.openExternal.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
        isLoading && pageBitmap == null -> {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        pageBitmap != null -> {
          val bmp = pageBitmap!!
          val scrollV = rememberScrollState()
          val scrollH = rememberScrollState()
          val pageShape = RoundedCornerShape(6.dp)
          Box(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollV)
              .horizontalScroll(scrollH)
              .padding(12.dp),
            contentAlignment = Alignment.Center,
          ) {
            val baseDp = LocalConfiguration.current.screenWidthDp.dp - 24.dp
            Surface(
              shape = pageShape,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
              shadowElevation = 4.dp,
            ) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = strings.pdfPageOf(currentPage + 1, pageCount),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.width(baseDp * zoomScale),
              )
            }
          }
          if (isLoading) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(24.dp),
              strokeWidth = 2.dp,
            )
          }
        }
      }
    }

    // Bottom pagination & zoom HUD controls
    if (pageCount > 0) {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
          if (pageCount > 1) {
            Slider(
              value = currentPage.toFloat(),
              onValueChange = { currentPage = it.roundToInt().coerceIn(0, pageCount - 1) },
              valueRange = 0f..(pageCount - 1).toFloat(),
              steps = (pageCount - 2).coerceAtLeast(0),
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_viewer_page_slider"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              PdfControlButton(
                icon = NexoIcons.ChevronLeft,
                contentDescription = strings.prevPageCd,
                enabled = currentPage > 0,
                testTag = "pdf_viewer_prev_page",
              ) {
                if (currentPage > 0) currentPage--
              }
              Text(
                text = "${currentPage + 1} / $pageCount",
                style = MonoTnumStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              PdfControlButton(
                icon = NexoIcons.ChevronRight,
                contentDescription = strings.nextPageCd,
                enabled = currentPage < pageCount - 1,
                testTag = "pdf_viewer_next_page",
              ) {
                if (currentPage < pageCount - 1) currentPage++
              }
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              PdfControlButton(
                icon = NexoIcons.ZoomOut,
                contentDescription = strings.zoomOutPdfCd,
                enabled = zoomScale > 0.85f,
                testTag = "pdf_viewer_zoom_out",
              ) {
                zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.8f)
              }
              Text(
                text = "${(zoomScale * 100).roundToInt()}%",
                style = MonoTnumStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              PdfControlButton(
                icon = NexoIcons.ZoomIn,
                contentDescription = strings.zoomInPdfCd,
                enabled = zoomScale < 2.95f,
                testTag = "pdf_viewer_zoom_in",
              ) {
                zoomScale = (zoomScale + 0.25f).coerceAtMost(3f)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PdfControlButton(
  icon: ImageVector,
  contentDescription: String,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val btnShape = RoundedCornerShape(4.dp)
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .size(44.dp)
      .clip(btnShape)
      .background(if (enabled) NexoTheme.semantic.tacticalSurface else MaterialTheme.colorScheme.surface)
      .border(
        width = 1.dp,
        color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
        shape = btnShape,
      )
      .testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
      modifier = Modifier.size(18.dp),
    )
  }
}
