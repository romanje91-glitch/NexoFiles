package com.example.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object NexoInternalViewerIds {
  const val AUDIO = "internal:AUDIO"
  const val VIDEO = "internal:VIDEO"
  const val IMAGE = "internal:IMAGE"
  const val PDF = "internal:PDF"
  const val TEXT_HEX = "internal:TEXT"
  const val ARCHIVE = "internal:ARCHIVE"
}

sealed interface AppLaunchOption {
  val optionId: String
  val title: String
  val subtitle: String

  data class InternalViewer(
    override val optionId: String,
    override val title: String,
    override val subtitle: String,
    val icon: ImageVector,
  ) : AppLaunchOption

  data class ExternalApp(
    val packageName: String,
    val activityName: String,
    override val title: String,
    override val subtitle: String,
    val iconBitmap: ImageBitmap?,
  ) : AppLaunchOption {
    override val optionId: String = "pkg:$packageName/$activityName"
  }
}

fun buildInternalViewerOptions(file: File, strings: AppStrings): List<AppLaunchOption.InternalViewer> {
  val name = file.name
  val audio = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.AUDIO,
    title = strings.viewerAudioTitle,
    subtitle = strings.viewerAudioSub,
    icon = NexoIcons.Headphones,
  )
  val video = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.VIDEO,
    title = strings.viewerVideoTitle,
    subtitle = strings.viewerVideoSub,
    icon = NexoIcons.VideoLibrary,
  )
  val image = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.IMAGE,
    title = strings.viewerImageTitle,
    subtitle = strings.viewerImageSub,
    icon = NexoIcons.Image,
  )
  val pdf = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.PDF,
    title = strings.viewerPdfTitle,
    subtitle = strings.viewerPdfSub,
    icon = NexoIcons.PictureAsPdf,
  )
  val textHex = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.TEXT_HEX,
    title = strings.viewerTextHexTitle,
    subtitle = strings.viewerTextHexSub,
    icon = NexoIcons.Code,
  )
  val archive = AppLaunchOption.InternalViewer(
    optionId = NexoInternalViewerIds.ARCHIVE,
    title = strings.viewerArchiveTitle,
    subtitle = strings.viewerArchiveSub,
    icon = NexoIcons.FolderZip,
  )

  return when {
    FormatUtils.isAudio(name) -> listOf(audio, textHex)
    FormatUtils.isVideo(name) -> listOf(video, audio, textHex)
    FormatUtils.isImage(name) -> listOf(image, textHex)
    FormatUtils.isPdf(name) -> listOf(pdf, textHex)
    FormatUtils.isArchive(name) -> listOf(archive, textHex)
    FormatUtils.isTextEditable(name) -> listOf(textHex, pdf)
    else -> listOf(textHex, audio, video, image, pdf)
  }
}

suspend fun queryExternalAppsForFile(context: Context, file: File): List<AppLaunchOption.ExternalApp> =
  withContext(Dispatchers.IO) {
    runCatching {
      val pm = context.packageManager
      val uri = FormatUtils.getFileProviderUri(context, file)
      val detectedMime = FormatUtils.getMimeType(file.name)
      val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, detectedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      var resolved = pm.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
      if (resolved.isEmpty() && detectedMime == "application/octet-stream") {
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, "*/*")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resolved = pm.queryIntentActivities(fallbackIntent, PackageManager.MATCH_DEFAULT_ONLY)
      }
      resolved
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        .map { ri ->
          val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(ri.activityInfo.packageName)
          val bmp = runCatching { ri.loadIcon(pm).toSafeImageBitmap() }.getOrNull()
          AppLaunchOption.ExternalApp(
            packageName = ri.activityInfo.packageName,
            activityName = ri.activityInfo.name,
            title = label,
            subtitle = ri.activityInfo.packageName,
            iconBitmap = bmp,
          )
        }
        .sortedBy { it.title.lowercase() }
    }.getOrDefault(emptyList())
  }

fun launchExternalAppOption(
  context: Context,
  file: File,
  packageName: String,
  activityName: String,
): Boolean {
  return runCatching {
    val uri = FormatUtils.getFileProviderUri(context, file)
    val mime = FormatUtils.getMimeType(file.name)
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, mime)
      component = ComponentName(packageName, activityName)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    true
  }.getOrDefault(false)
}

private fun Drawable.toSafeImageBitmap(sizePx: Int = 96): ImageBitmap {
  if (this is BitmapDrawable && bitmap != null) {
    return bitmap.asImageBitmap()
  }
  val width = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
  val height = intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
  val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)
  setBounds(0, 0, canvas.width, canvas.height)
  draw(canvas)
  return bmp.asImageBitmap()
}

@Composable
fun SelectAppChooserDialog(
  file: File,
  onSelectOption: (option: AppLaunchOption, always: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current

  val internalOptions = remember(file.absolutePath, strings.isEnglish) {
    buildInternalViewerOptions(file, strings)
  }
  val externalApps by produceState<List<AppLaunchOption.ExternalApp>>(initialValue = emptyList(), file.absolutePath) {
    value = queryExternalAppsForFile(context, file)
  }

  var selectedOptionId by remember(file.absolutePath, internalOptions) {
    mutableStateOf(internalOptions.firstOrNull()?.optionId.orEmpty())
  }
  val allOptions = remember(internalOptions, externalApps) {
    internalOptions + externalApps
  }
  val selectedOption = allOptions.firstOrNull { it.optionId == selectedOptionId } ?: internalOptions.firstOrNull()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .heightIn(min = 320.dp, max = 580.dp)
        .testTag("select_app_chooser_dialog"),
      shape = RoundedCornerShape(10.dp),
      color = NexoTheme.semantic.modalSurface,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
      ),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = strings.chooserTitle.uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = strings.chooserSubtitle(file.name),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          val ext = file.extension.uppercase().ifEmpty { "BIN" }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text(
              text = ".$ext",
              style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          item(key = "hdr_internal") {
            Text(
              text = strings.chooserInternalSection.uppercase(),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
          }
          items(internalOptions, key = { it.optionId }) { opt ->
            ChooserOptionRow(
              title = opt.title,
              subtitle = opt.subtitle,
              selected = selectedOptionId == opt.optionId,
              leadingContent = {
                IconContainerBadge(
                  icon = opt.icon,
                  contentDescription = null,
                  size = 40.dp,
                  highlighted = true,
                )
              },
              onClick = { selectedOptionId = opt.optionId },
              testTag = "chooser_option_${opt.optionId}",
            )
          }

          item(key = "hdr_external") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = strings.chooserExternalSection.uppercase(),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
          }
          if (externalApps.isEmpty()) {
            item(key = "no_external") {
              Text(
                text = strings.chooserNoExternalApps,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
              )
            }
          } else {
            items(externalApps, key = { it.optionId }) { app ->
              ChooserOptionRow(
                title = app.title,
                subtitle = app.subtitle,
                selected = selectedOptionId == app.optionId,
                leadingContent = {
                  if (app.iconBitmap != null) {
                    Image(
                      bitmap = app.iconBitmap,
                      contentDescription = null,
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    )
                  } else {
                    IconContainerBadge(
                      icon = NexoIcons.Android,
                      contentDescription = null,
                      size = 40.dp,
                    )
                  }
                },
                onClick = { selectedOptionId = app.optionId },
                testTag = "chooser_option_${app.packageName}",
              )
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = {
              selectedOption?.let { onSelectOption(it, true) }
            },
            enabled = selectedOption != null,
            modifier = Modifier
              .weight(1f)
              .testTag("chooser_always_btn"),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(
              text = strings.chooserAlwaysBtn.uppercase(),
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
            )
          }
          Button(
            onClick = {
              selectedOption?.let { onSelectOption(it, false) }
            },
            enabled = selectedOption != null,
            modifier = Modifier
              .weight(1f)
              .testTag("chooser_once_btn"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(
              text = strings.chooserOnceBtn.uppercase(),
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChooserOptionRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  leadingContent: @Composable () -> Unit,
  onClick: () -> Unit,
  testTag: String,
) {
  val rowShape = RoundedCornerShape(6.dp)
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(rowShape)
      .clickable(onClick = onClick)
      .testTag(testTag),
    shape = rowShape,
    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
    border = BorderStroke(
      width = 1.dp,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      leadingContent()
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      RadioButton(
        selected = selected,
        onClick = onClick,
        colors = RadioButtonDefaults.colors(
          selectedColor = MaterialTheme.colorScheme.primary,
          unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      )
    }
  }
}
