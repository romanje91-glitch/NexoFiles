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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.db.RecycleBinEntity
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils

@Composable
fun RecycleBinScreen(
  items: List<RecycleBinEntity>,
  onRestore: (RecycleBinEntity) -> Unit,
  onDeleteForever: (RecycleBinEntity) -> Unit,
  onEmptyAll: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = strings.back,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.recycleBinTitle.uppercase(),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = strings.recoverableCount(items.size),
          style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      if (items.isNotEmpty()) {
        Button(
          onClick = onEmptyAll,
          colors = ButtonDefaults.buttonColors(
            containerColor = NexoTheme.semantic.evaRed,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = CutCornerShape(4.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(
            text = strings.emptyBinBtn.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (items.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          IconContainerBadge(icon = NexoIcons.DeleteOutline, contentDescription = null, size = 60.dp)
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = strings.recycleBinEmpty.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(items, key = { it.id }) { item ->
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(
                icon = NexoIcons.RestoreFromTrash,
                contentDescription = null,
                highlighted = true,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.fileName,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${FormatUtils.formatBytes(item.sizeBytes)} // ${FormatUtils.formatDate(item.deletedAt)}",
                  style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = { onRestore(item) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = CutCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              ) {
                Text(
                  text = strings.restore.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
              IconButton(onClick = { onDeleteForever(item) }) {
                Icon(
                  imageVector = NexoIcons.DeleteForever,
                  contentDescription = strings.deleteForever,
                  tint = NexoTheme.semantic.evaRed,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun RecycleBinScreenPreview() {
  NexoFilesTheme {
    RecycleBinScreen(
      items = listOf(
        RecycleBinEntity(
          id = 1,
          fileName = "Borrador_antiguo.pdf",
          originalPath = "/storage/emulated/0/Download/Borrador_antiguo.pdf",
          trashPath = "/data/user/0/com.example/files/trash/1",
          sizeBytes = 2_450_000L,
          isDirectory = false,
          mimeType = "application/pdf",
          deletedAt = System.currentTimeMillis(),
        ),
      ),
      onRestore = {},
      onDeleteForever = {},
      onEmptyAll = {},
      onBack = {},
    )
  }
}
