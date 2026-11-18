package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class FolderPickerItem(
  val name: String,
  val path: String,
  val childCount: Int,
)

@Composable
fun FolderPickerDialog(
  initialPath: String = "/storage/emulated/0",
  availableVolumes: List<Pair<String, String>> = listOf("Almacenamiento interno" to "/storage/emulated/0"),
  title: String? = null,
  onSelectFolder: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  val effectiveTitle = title ?: strings.selectFolderTitle
  val rootFallback = availableVolumes.firstOrNull()?.second ?: "/storage/emulated/0"
  var currentPath by remember(initialPath) {
    val f = File(initialPath)
    mutableStateOf(if (f.exists() && f.isDirectory) f.absolutePath else rootFallback)
  }
  var folders by remember { mutableStateOf<List<FolderPickerItem>>(emptyList()) }
  var showCreateInline by remember { mutableStateOf(false) }
  var newFolderName by remember { mutableStateOf("") }
  var createError by remember { mutableStateOf<String?>(null) }
  var refreshTick by remember { mutableIntStateOf(0) }

  LaunchedEffect(currentPath, refreshTick) {
    folders = withContext(Dispatchers.IO) {
      val dir = File(currentPath)
      val children = dir.listFiles()
        ?.filter { it.isDirectory && it.canRead() }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
      children.map { child ->
        val count = runCatching { child.list()?.size ?: 0 }.getOrDefault(0)
        FolderPickerItem(name = child.name, path = child.absolutePath, childCount = count)
      }
    }
  }

  val breadcrumbs = remember(currentPath) {
    val parts = currentPath.split('/').filter { it.isNotEmpty() }
    val acc = mutableListOf<Pair<String, String>>()
    var built = ""
    for (p in parts) {
      built += "/$p"
      val label = if (built == "/storage/emulated/0") "INTERNO" else p
      acc.add(label to built)
    }
    val idx = acc.indexOfFirst { it.second == "/storage/emulated/0" }
    if (idx >= 0) acc.drop(idx) else acc
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .heightIn(min = 380.dp, max = 600.dp)
        .testTag("folder_picker_dialog"),
      shape = RoundedCornerShape(8.dp),
      color = NexoTheme.semantic.modalSurface,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconContainerBadge(icon = NexoIcons.FolderOpen, contentDescription = null, size = 38.dp, highlighted = true)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = effectiveTitle.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = currentPath,
                style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = strings.close, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        if (availableVolumes.size > 1) {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            availableVolumes.forEach { (label, volPath) ->
              val active = currentPath.startsWith(volPath)
              FilterChip(
                selected = active,
                onClick = { currentPath = volPath },
                shape = RoundedCornerShape(4.dp),
                label = { Text(label.uppercase(), maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                  containerColor = MaterialTheme.colorScheme.surface,
                  labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier
              .weight(1f)
              .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            breadcrumbs.forEachIndexed { idx, (label, path) ->
              if (idx > 0) {
                Icon(
                  NexoIcons.ChevronRight,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (path == currentPath) FontWeight.Bold else FontWeight.Normal,
                color = if (path == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .clickable { currentPath = path }
                  .padding(horizontal = 6.dp, vertical = 4.dp),
              )
            }
          }
          TextButton(
            onClick = {
              showCreateInline = !showCreateInline
              createError = null
            },
            modifier = Modifier.testTag("folder_picker_new_folder_btn"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.newFolder.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
          }
        }

        if (showCreateInline) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(5.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(5.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = newFolderName,
              onValueChange = { newFolderName = it; createError = null },
              label = { Text(strings.newFolderNameLabel) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              ),
            )
            createError?.let { err ->
              Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { showCreateInline = false; newFolderName = "" }) {
                Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Button(
                onClick = {
                  val clean = newFolderName.trim()
                  if (clean.isNotEmpty()) {
                    val target = File(currentPath, clean)
                    if (target.exists() || target.mkdirs()) {
                      currentPath = target.absolutePath
                      newFolderName = ""
                      showCreateInline = false
                      refreshTick++
                    } else {
                      createError = strings.folderCreateError
                    }
                  }
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(4.dp),
              ) {
                Text(strings.create.uppercase(), fontWeight = FontWeight.Bold)
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val parentFile = File(currentPath).parentFile
          if (parentFile != null && currentPath != "/" && parentFile.canRead()) {
            item(key = "__up__") {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(4.dp))
                  .background(MaterialTheme.colorScheme.surface)
                  .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                  .clickable { currentPath = parentFile.absolutePath }
                  .padding(horizontal = 12.dp, vertical = 10.dp)
                  .testTag("folder_picker_up"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconContainerBadge(icon = NexoIcons.ArrowUpward, contentDescription = null, size = 34.dp, highlighted = true)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = strings.upOneLevel.uppercase(),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          if (folders.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = strings.noSubfolders,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            items(folders, key = { it.path }) { folder ->
              val rowShape = RoundedCornerShape(4.dp)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(rowShape)
                  .background(MaterialTheme.colorScheme.background)
                  .border(1.dp, MaterialTheme.colorScheme.outline, rowShape)
                  .clickable { currentPath = folder.path }
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconContainerBadge(icon = NexoIcons.Folder, contentDescription = null, size = 36.dp, highlighted = true)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = strings.itemsCount(folder.childCount),
                    style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Button(
            onClick = { onSelectFolder(currentPath) },
            modifier = Modifier
              .weight(1.6f)
              .testTag("folder_picker_confirm"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.selectThisFolder.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
  }
}
