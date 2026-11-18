package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileCategory
import com.example.data.model.LocalFileItem
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils

@Composable
fun CategoryFilesScreen(
  category: FileCategory,
  files: List<LocalFileItem>,
  isLoading: Boolean,
  onBack: () -> Unit,
  onOpenFile: (LocalFileItem) -> Unit,
  onShareFile: (LocalFileItem) -> Unit,
  onMoveToTrash: (LocalFileItem) -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val categoryTitle = strings.categoryLabel(category)

  val catIcon = when (category) {
    FileCategory.IMAGES -> NexoIcons.Image
    FileCategory.VIDEOS -> NexoIcons.VideoLibrary
    FileCategory.DOCUMENTS -> NexoIcons.Description
    FileCategory.MUSIC -> NexoIcons.MusicNote
    FileCategory.APKS -> NexoIcons.Android
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(categoryTitle.uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(strings.filesCount(files.size), style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    if (isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    } else if (files.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          IconContainerBadge(icon = catIcon, contentDescription = null, size = 58.dp)
          Spacer(modifier = Modifier.height(10.dp))
          Text(strings.categoryEmpty(categoryTitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(files, key = { it.path }) { item ->
          BichromaticCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenFile(item) },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(icon = catIcon, contentDescription = null, highlighted = true)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.name,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatDate(item.lastModified)}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { onShareFile(item) }) {
                Icon(Icons.Rounded.Share, contentDescription = strings.share, tint = MaterialTheme.colorScheme.secondary)
              }
              IconButton(onClick = { onMoveToTrash(item) }) {
                Icon(NexoIcons.DeleteOutline, contentDescription = strings.moveToTrashCd, tint = NexoTheme.semantic.evaOrange)
              }
            }
          }
        }
      }
    }
  }
}
