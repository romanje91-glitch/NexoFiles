package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.repo.WebServerState
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme

@Composable
fun ToolsHubScreen(
  driveAccountEmail: String?,
  googlePhotosEmail: String? = null,
  webServerState: WebServerState,
  onToggleWebServer: () -> Unit,
  onOpenDrive: () -> Unit,
  onOpenGooglePhotos: () -> Unit = {},
  onOpenExternalClouds: () -> Unit = {},
  onOpenViewOnPc: () -> Unit,
  onOpenSender: () -> Unit = {},
  onOpenNetwork: () -> Unit,
  onOpenAppManager: () -> Unit,
  onOpenNoteEditor: () -> Unit,
  onOpenDownloader: () -> Unit,
  onOpenVault: () -> Unit,
  onOpenRecycleBin: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val strings = LocalStrings.current
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        text = strings.toolsHubTitle.uppercase(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = strings.toolsHubSubtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Google Drive Card
    item {
      ToolHubItemCard(
        title = strings.drawerDrive,
        subtitle = driveAccountEmail?.let { strings.connectedAs(it) } ?: strings.driveNotConnectedDesc,
        icon = NexoIcons.Cloud,
        highlighted = driveAccountEmail != null,
        tag = "tool_google_drive",
        onClick = onOpenDrive,
      )
    }

    // Google Photos Picker & Editor Card
    item {
      ToolHubItemCard(
        title = strings.googlePhotosTitle,
        subtitle = googlePhotosEmail?.let { strings.connectedAs(it) } ?: strings.googlePhotosSubtitle,
        icon = NexoIcons.PhotoLibrary,
        highlighted = true,
        tag = "tool_google_photos",
        onClick = onOpenGooglePhotos,
      )
    }

    // External Clouds (OneDrive / Dropbox / WebDAV / Nextcloud)
    item {
      ToolHubItemCard(
        title = strings.externalCloudsTitle,
        subtitle = strings.externalCloudsSubtitle,
        icon = NexoIcons.Cloud,
        highlighted = false,
        tag = "tool_external_clouds",
        onClick = onOpenExternalClouds,
      )
    }

    // View on PC Quick Toggle Card
    item {
      BichromaticCard(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("tool_view_on_pc"),
        accentBorder = webServerState.isRunning,
        onClick = onOpenViewOnPc,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconContainerBadge(
            icon = NexoIcons.Computer,
            contentDescription = null,
            highlighted = webServerState.isRunning,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.drawerViewOnPc.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
              text = if (webServerState.isRunning) "${strings.serverActive} ${webServerState.url}" else strings.serverStopped,
              style = if (webServerState.isRunning) MonoTnumStyle else MaterialTheme.typography.bodySmall,
              color = if (webServerState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = webServerState.isRunning,
            onCheckedChange = { onToggleWebServer() },
            colors = SwitchDefaults.colors(
              checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
              checkedTrackColor = MaterialTheme.colorScheme.primary,
              uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
              uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
          )
        }
      }
    }

    // Nexo Sender (Wi-Fi + QR)
    item {
      ToolHubItemCard(
        title = strings.senderTitle,
        subtitle = strings.senderDesc,
        icon = NexoIcons.Send,
        highlighted = true,
        tag = "tool_sender",
        onClick = onOpenSender,
      )
    }

    // Network LAN / SMB / FTP / SFTP
    item {
      ToolHubItemCard(
        title = strings.lanNetworkTitle,
        subtitle = strings.lanNetworkDesc,
        icon = NexoIcons.Router,
        tag = "tool_network",
        onClick = onOpenNetwork,
      )
    }

    // App Manager
    item {
      ToolHubItemCard(
        title = strings.appManagerTitle,
        subtitle = strings.appManagerDesc,
        icon = NexoIcons.Android,
        tag = "tool_app_manager",
        onClick = onOpenAppManager,
      )
    }

    // Note & Code Editor
    item {
      ToolHubItemCard(
        title = strings.noteEditorTitle,
        subtitle = strings.noteEditorDesc,
        icon = NexoIcons.EditNote,
        tag = "tool_note_editor",
        onClick = onOpenNoteEditor,
      )
    }

    // Direct Downloader
    item {
      ToolHubItemCard(
        title = strings.downloaderTitle,
        subtitle = strings.downloaderDesc,
        icon = NexoIcons.Download,
        tag = "tool_downloader",
        onClick = onOpenDownloader,
      )
    }

    // Private Vault
    item {
      ToolHubItemCard(
        title = strings.vaultTitle,
        subtitle = strings.vaultDesc,
        icon = Icons.Rounded.Lock,
        highlighted = true,
        accentTint = NexoTheme.semantic.evaOrange,
        tag = "tool_private_vault",
        onClick = onOpenVault,
      )
    }

    // Recycle Bin
    item {
      ToolHubItemCard(
        title = strings.recycleBinTitle,
        subtitle = strings.recycleBinDesc,
        icon = NexoIcons.DeleteOutline,
        tag = "tool_recycle_bin",
        onClick = onOpenRecycleBin,
      )
    }

    // Ajustes y Pantalla
    item {
      ToolHubItemCard(
        title = strings.advancedSettingsTitle,
        subtitle = strings.advancedSettingsDesc,
        icon = NexoIcons.Palette,
        tag = "tool_settings",
        onClick = onOpenSettings,
      )
    }
  }
}

@Composable
private fun ToolHubItemCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  highlighted: Boolean = false,
  accentTint: androidx.compose.ui.graphics.Color? = null,
  tag: String,
  onClick: () -> Unit,
) {
  BichromaticCard(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(tag),
    accentBorder = highlighted,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconContainerBadge(
        icon = icon,
        contentDescription = null,
        highlighted = highlighted,
        iconTint = accentTint ?: if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ToolsHubScreenPreview() {
  NexoFilesTheme {
    ToolsHubScreen(
      driveAccountEmail = "usuario@gmail.com",
      webServerState = WebServerState(isRunning = true, url = "http://192.168.1.59:8080"),
      onToggleWebServer = {},
      onOpenDrive = {},
      onOpenViewOnPc = {},
      onOpenNetwork = {},
      onOpenAppManager = {},
      onOpenNoteEditor = {},
      onOpenDownloader = {},
      onOpenVault = {},
      onOpenRecycleBin = {},
      onOpenSettings = {},
    )
  }
}
