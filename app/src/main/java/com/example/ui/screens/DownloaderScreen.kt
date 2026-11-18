package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.DownloadHistoryEntity
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import java.io.File

@Composable
fun DownloaderScreen(
  downloads: List<DownloadHistoryEntity>,
  activeProgress: Pair<String, Float>?,
  onReadClipboardUrl: () -> String? = { null },
  onSuggestFileName: (String) -> String = { "archivo_descargado" },
  onStartDownload: (String, String?) -> Unit,
  onOpenDownloadedFile: (File) -> Unit,
  onDeleteRecord: (Long) -> Unit,
  onClearHistory: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  var selectedTab by remember { mutableIntStateOf(0) }
  var showDownloadModal by remember { mutableStateOf(false) }

  val completedItems = remember(downloads) { downloads.filter { it.status != "RUNNING" } }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          DownloaderBottomAction(Icons.Rounded.Add, if (strings.isEnglish) "+ NEW" else "+ NUEVA") { showDownloadModal = true }
          DownloaderBottomAction(NexoIcons.Refresh, strings.refresh.uppercase()) { }
          DownloaderBottomAction(NexoIcons.Layers, if (strings.isEnglish) "TABS" else "PESTAÑAS") {
            selectedTab = if (selectedTab == 0) 1 else 0
          }
          DownloaderBottomAction(NexoIcons.History, strings.downloadHistoryTitle.uppercase()) {
            selectedTab = 1
          }
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
              Text(strings.downloaderScreenTitle.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Text(strings.downloaderScreenSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Button(
            onClick = { showDownloadModal = true },
            modifier = Modifier.testTag("downloader_new_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (strings.isEnglish) "NEW" else "NUEVA", fontWeight = FontWeight.Bold)
          }
        }
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val activeTabLabel = if (strings.isEnglish) "ACTIVE (${if (activeProgress != null) 1 else 0})" else "ACTIVAS (${if (activeProgress != null) 1 else 0})"
          val doneTabLabel = if (strings.isEnglish) "DONE (${completedItems.size})" else "COMPLETADAS (${completedItems.size})"
          FilterChip(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            label = { Text(activeTabLabel, style = MonoTnumStyle) },
            shape = RoundedCornerShape(4.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
          )
          FilterChip(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            label = { Text(doneTabLabel, style = MonoTnumStyle) },
            shape = RoundedCornerShape(4.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
          )
          if (completedItems.isNotEmpty() && selectedTab == 1) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClearHistory) {
              Text(strings.clear.uppercase(), color = NexoTheme.semantic.evaRed)
            }
          }
        }
      }

      if (selectedTab == 0) {
        if (activeProgress == null) {
          item {
            BichromaticCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = if (strings.isEnglish) "No active downloads right now." else "No hay descargas activas en este momento.",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = if (strings.isEnglish) "Tap '+ NEW' to paste an HTTP/HTTPS URL and start downloading." else "Pulsa '+ NUEVA' para pegar una URL HTTP/HTTPS e iniciar la descarga.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          val (fileName, progress) = activeProgress
          item {
            BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.downloadingProgress(fileName, (progress * 100).toInt().coerceAtLeast(0)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (progress >= 0f) {
                  LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.background,
                  )
                  Text("${(progress * 100).toInt()}%", style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary)
                } else {
                  LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.background,
                  )
                }
              }
            }
          }
        }
      } else {
        if (completedItems.isEmpty()) {
          item {
            BichromaticCard(modifier = Modifier.fillMaxWidth()) {
              Text(strings.noDownloadsYet, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        } else {
          items(completedItems, key = { it.id }) { item ->
            BichromaticCard(
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                if (item.status == "COMPLETED") {
                  val f = File(item.destinationPath)
                  if (f.exists()) onOpenDownloadedFile(f)
                }
              },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconContainerBadge(
                  icon = when (item.status) {
                    "COMPLETED" -> Icons.Rounded.CheckCircle
                    "FAILED" -> NexoIcons.ErrorOutline
                    else -> NexoIcons.Download
                  },
                  contentDescription = null,
                  highlighted = item.status == "COMPLETED",
                  iconTint = if (item.status == "FAILED") NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = if (item.status == "FAILED") item.errorMessage ?: "Error" else "${FormatUtils.formatBytes(item.downloadedBytes)} · ${FormatUtils.formatDate(item.createdAt)}",
                    style = MonoTnumStyle,
                    color = if (item.status == "FAILED") NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                IconButton(onClick = { onDeleteRecord(item.id) }) {
                  Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showDownloadModal) {
    DownloadNewModal(
      initialClipboardUrl = onReadClipboardUrl().orEmpty(),
      suggestFileName = onSuggestFileName,
      onDownloadNow = { url, name ->
        showDownloadModal = false
        selectedTab = 0
        onStartDownload(url, name.ifBlank { null })
      },
      onDismiss = { showDownloadModal = false },
    )
  }
}

@Composable
private fun DownloadNewModal(
  initialClipboardUrl: String,
  suggestFileName: (String) -> String,
  onDownloadNow: (String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var pathUrl by remember(initialClipboardUrl) { mutableStateOf(initialClipboardUrl) }
  var fileName by remember(initialClipboardUrl) {
    mutableStateOf(if (initialClipboardUrl.isNotBlank()) suggestFileName(initialClipboardUrl) else "")
  }
  var userEditedName by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = { Text(strings.downloaderScreenTitle.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = pathUrl,
          onValueChange = { next ->
            pathUrl = next
            if (!userEditedName && (next.startsWith("http://", true) || next.startsWith("https://", true))) {
              fileName = suggestFileName(next)
            }
          },
          label = { Text(strings.pasteUrlLabel) },
          placeholder = { Text("https://example.com/file.zip") },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth().testTag("downloader_url_input"),
        )
        OutlinedTextField(
          value = fileName,
          onValueChange = {
            fileName = it
            userEditedName = true
          },
          label = { Text(strings.optionalFileNameLabel) },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pathUrl.startsWith("http://", true) || pathUrl.startsWith("https://", true)) {
            onDownloadNow(pathUrl.trim(), fileName.trim())
          }
        },
        enabled = pathUrl.startsWith("http://", true) || pathUrl.startsWith("https://", true),
        modifier = Modifier.testTag("downloader_start_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.downloadNowBtn.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel.uppercase()) }
    },
  )
}

@Composable
private fun DownloaderBottomAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
  }
}
