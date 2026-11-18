package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArchiveEntryInfo
import com.example.data.model.FileProperties
import com.example.data.model.LocalFileItem
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import java.io.File

@Composable
fun TextInputDialog(
  title: String,
  label: String,
  initialValue: String = "",
  confirmText: String = "Aceptar",
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember(initialValue) { mutableStateOf(initialValue) }
  val strings = LocalStrings.current
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = { Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("dialog_text_input"),
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          focusedTextColor = MaterialTheme.colorScheme.onSurface,
          unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
      )
    },
    confirmButton = {
      Button(
        onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(confirmText.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )
}

@Composable
fun FilePropertiesDialog(
  properties: FileProperties?,
  isLoading: Boolean,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = { Text(strings.propertiesTitle.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
    text = {
      if (isLoading || properties == null) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Text(strings.calculatingProperties, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          PropRow(strings.propName, properties.item.name)
          PropRow(strings.propPath, properties.item.path, mono = true)
          PropRow(strings.propTotalSize, FormatUtils.formatBytes(properties.totalSizeBytes), mono = true)
          if (properties.item.isDirectory) {
            PropRow(strings.propContents, strings.propContentsValue(properties.childFiles, properties.childFolders), mono = true)
          } else {
            PropRow("MIME", properties.item.mimeType, mono = true)
          }
          PropRow(strings.propModified, FormatUtils.formatDate(properties.item.lastModified), mono = true)
          PropRow(strings.propPermissions, properties.permissionsString, mono = true)
          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          PropRow("MD5", properties.md5, mono = true)
          PropRow("SHA-256", properties.sha256, mono = true)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.close.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
  )
}

@Composable
private fun PropRow(label: String, value: String, mono: Boolean = false) {
  Column {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = if (mono) JetBrainsMonoFamily else FontFamily.Default,
        fontSize = if (mono) 11.sp else 13.sp,
      ),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun ArchiveInspectorDialog(
  archiveItem: LocalFileItem,
  entries: List<ArchiveEntryInfo>,
  isBusy: Boolean,
  availableVolumes: List<Pair<String, String>> = listOf("Almacenamiento interno" to "/storage/emulated/0"),
  onExtractHere: () -> Unit,
  onExtractToFolder: (String) -> Unit = {},
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var showDestPicker by remember { mutableStateOf(false) }
  val totalUncompressed = remember(entries) { entries.filterNot { it.isDirectory }.sumOf { it.sizeBytes } }
  val fileCount = remember(entries) { entries.count { !it.isDirectory } }
  val extBadge = remember(archiveItem.name) {
    archiveItem.name.substringAfterLast('.', "ZIP").uppercase()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(strings.inspectTitle(extBadge).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(archiveItem.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!isBusy && entries.isNotEmpty()) {
          Text(
            text = "${strings.filesCount(fileCount)} · ${FormatUtils.formatBytes(totalUncompressed)}",
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    },
    text = {
      if (isBusy) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Text(strings.loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else if (entries.isEmpty()) {
        Text(strings.empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        val listShape = RoundedCornerShape(6.dp)
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .background(MaterialTheme.colorScheme.background, listShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, listShape)
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(entries.take(120)) { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (entry.isDirectory) NexoIcons.Folder else NexoIcons.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (!entry.isDirectory) {
                Text(
                  text = FormatUtils.formatBytes(entry.sizeBytes),
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { showDestPicker = true },
          enabled = !isBusy,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
          modifier = Modifier.testTag("archive_extract_to_folder_btn"),
        ) {
          Text(strings.extractToFolder.uppercase(), color = MaterialTheme.colorScheme.secondary)
        }
        Button(
          onClick = onExtractHere,
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(4.dp),
          modifier = Modifier.testTag("archive_extract_here_btn"),
        ) {
          Text(strings.extractHere.uppercase(), fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )

  if (showDestPicker) {
    val startDir = File(archiveItem.path).parent ?: "/storage/emulated/0"
    FolderPickerDialog(
      initialPath = startDir,
      availableVolumes = availableVolumes,
      title = strings.extractToTitle(archiveItem.name),
      onSelectFolder = { chosenPath ->
        showDestPicker = false
        val subName = File(archiveItem.path).nameWithoutExtension.removeSuffix(".tar")
        onExtractToFolder(File(chosenPath, subName).absolutePath)
      },
      onDismiss = { showDestPicker = false },
    )
  }
}

@Composable
fun CompressArchiveDialog(
  selectedCount: Int,
  defaultName: String,
  currentDirPath: String,
  availableVolumes: List<Pair<String, String>> = listOf("Almacenamiento interno" to "/storage/emulated/0"),
  onCompressZip: (name: String, destDirPath: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var archiveName by remember(defaultName) { mutableStateOf(defaultName) }
  var destDirPath by remember(currentDirPath) { mutableStateOf(currentDirPath) }
  var selectedFormat by remember { mutableStateOf("ZIP") }
  var showDestPicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Column {
        Text(strings.createZipTitle.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          text = strings.selectedCount(selectedCount),
          style = MonoTnumStyle,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = archiveName,
          onValueChange = { archiveName = it },
          label = { Text(strings.zipNameLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("compress_archive_name_input"),
          shape = RoundedCornerShape(6.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
          ),
        )

        Text(
          if (strings.isEnglish) "COMPRESSION FORMAT" else "FORMATO DE COMPRESIÓN",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedFormat == "ZIP",
            onClick = { selectedFormat = "ZIP" },
            label = { Text(".ZIP", style = MonoTnumStyle) },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.testTag("compress_format_zip"),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurface,
            ),
          )
          FilterChip(
            selected = selectedFormat == "RAR",
            onClick = { selectedFormat = "RAR" },
            label = { Text(".RAR", style = MonoTnumStyle) },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.testTag("compress_format_rar"),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.surface,
              selectedLabelColor = MaterialTheme.colorScheme.secondary,
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
          )
        }
        if (selectedFormat == "RAR") {
          Text(
            text = if (strings.isEnglish) {
              "Technical note: RAR uses a proprietary RARLAB format. NexoFiles extracts .rar natively and creates universally compatible .zip archives."
            } else {
              "Nota técnica: RAR usa un algoritmo propietario de RARLAB. NexoFiles extrae archivos .rar nativamente, pero para comprimir usa .zip con compatibilidad universal."
            },
            style = MaterialTheme.typography.bodySmall,
            color = NexoTheme.semantic.evaOrange,
          )
        }

        OutlinedButton(
          onClick = { showDestPicker = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("compress_pick_folder_btn"),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = destDirPath,
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (archiveName.isNotBlank()) {
            onCompressZip(archiveName.trim(), destDirPath)
          }
        },
        enabled = selectedFormat == "ZIP" && archiveName.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.testTag("compress_confirm_btn"),
      ) {
        Text(strings.compressBtn.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )

  if (showDestPicker) {
    FolderPickerDialog(
      initialPath = destDirPath,
      availableVolumes = availableVolumes,
      title = strings.selectFolderTitle,
      onSelectFolder = { chosen ->
        destDirPath = chosen
        showDestPicker = false
      },
      onDismiss = { showDestPicker = false },
    )
  }
}
