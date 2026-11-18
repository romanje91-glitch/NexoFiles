package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.db.NetworkServerEntity
import com.example.data.model.RemoteFileEntry
import com.example.data.model.RemoteProtocol
import com.example.ui.components.BichromaticCard
import com.example.ui.components.EsViewSortDialog
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.NetworkUiState
import com.example.util.FormatUtils

private enum class NetworkFilterTab(val label: String) {
  ALL("TODOS"),
  FTP("FTP"),
  WEBDAV("WEBDAV"),
  SMB("LAN / SMB"),
}

@Composable
fun NetworkScreen(
  state: NetworkUiState,
  savedServers: List<NetworkServerEntity>,
  onScanLan: () -> Unit,
  onSaveServer: (
    name: String,
    protocol: RemoteProtocol,
    host: String,
    port: Int,
    username: String,
    password: String,
    ftpMode: String,
    encoding: String,
    useHttps: Boolean,
    domain: String,
    anonymous: Boolean,
  ) -> Unit,
  onDeleteServer: (Long) -> Unit,
  onConnectServer: (NetworkServerEntity, String) -> Unit,
  onDisconnectServer: () -> Unit,
  onDownloadRemoteFile: (RemoteFileEntry) -> Unit,
  onBack: () -> Unit,
) {
  val strings = LocalStrings.current
  var selectedTab by remember { mutableStateOf(NetworkFilterTab.ALL) }
  var showProtocolMenu by remember { mutableStateOf(false) }
  var activeDialogProtocol by remember { mutableStateOf<RemoteProtocol?>(null) }
  var prefillHost by remember { mutableStateOf("") }
  var compactView by remember { mutableStateOf(false) }
  var showNetEsViewDialog by remember { mutableStateOf(false) }
  var netEsViewMode by remember { mutableStateOf("MEDIUM_LIST") }

  BackHandler {
    if (state.activeServer != null) {
      onDisconnectServer()
    } else {
      onBack()
    }
  }

  val filteredServers = remember(savedServers, selectedTab) {
    when (selectedTab) {
      NetworkFilterTab.ALL -> savedServers
      NetworkFilterTab.FTP -> savedServers.filter { it.protocol in setOf(RemoteProtocol.FTP.name, RemoteProtocol.FTPS.name, RemoteProtocol.SFTP.name) }
      NetworkFilterTab.WEBDAV -> savedServers.filter { it.protocol == RemoteProtocol.WEBDAV.name }
      NetworkFilterTab.SMB -> savedServers.filter { it.protocol == RemoteProtocol.SMB.name }
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface),
      ) {
        if (state.activeServer == null) {
          Button(
            onClick = onScanLan,
            enabled = !state.isScanningLan,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag("scan_lan_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
          ) {
            if (state.isScanningLan) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.scanningLan.uppercase(), fontWeight = FontWeight.Bold)
            } else {
              Icon(NexoIcons.Radar, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (strings.isEnglish) "SCAN LAN" else "ESCANEAR LAN", fontWeight = FontWeight.Bold)
            }
          }
        }
        Surface(
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            NetworkBottomAction(Icons.Rounded.Add, if (strings.isEnglish) "+ NEW" else "+ NUEVO") {
              prefillHost = ""
              activeDialogProtocol = when (selectedTab) {
                NetworkFilterTab.WEBDAV -> RemoteProtocol.WEBDAV
                NetworkFilterTab.SMB -> RemoteProtocol.SMB
                else -> RemoteProtocol.FTP
              }
            }
            NetworkBottomAction(NexoIcons.Refresh, strings.refresh) {
              if (state.activeServer != null) {
                onConnectServer(state.activeServer, state.currentRemotePath)
              } else {
                onScanLan()
              }
            }
            NetworkBottomAction(if (compactView) NexoIcons.ViewList else NexoIcons.GridView, if (strings.isEnglish) "VIEW" else "VISTA") {
              showNetEsViewDialog = true
            }
            NetworkBottomAction(NexoIcons.Layers, if (strings.isEnglish) "WINDOWS" else "VENTANAS") {
              if (state.activeServer != null) onDisconnectServer()
            }
            NetworkBottomAction(NexoIcons.History, if (strings.isEnglish) "HISTORY" else "HISTORIAL") {
              selectedTab = NetworkFilterTab.ALL
            }
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
        ) {
          IconButton(onClick = { if (state.activeServer != null) onDisconnectServer() else onBack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.width(6.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = (state.activeServer?.name ?: strings.networkTitle).uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = state.activeServer?.let { "${it.protocol} · ${state.currentRemotePath}" } ?: strings.networkSubtitle,
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (state.activeServer == null) {
            Box {
              OutlinedButton(
                onClick = { showProtocolMenu = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_ftp_server_button"),
              ) {
                Text((if (strings.isEnglish) "NEW" else "NUEVO"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }
              DropdownMenu(
                expanded = showProtocolMenu,
                onDismissRequest = { showProtocolMenu = false },
                modifier = Modifier
                  .background(NexoTheme.semantic.modalSurface)
                  .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                DropdownMenuItem(
                  text = { Text("FTP SERVER", fontWeight = FontWeight.SemiBold) },
                  onClick = { showProtocolMenu = false; prefillHost = ""; activeDialogProtocol = RemoteProtocol.FTP },
                )
                DropdownMenuItem(
                  text = { Text("WEBDAV SERVER", fontWeight = FontWeight.SemiBold) },
                  onClick = { showProtocolMenu = false; prefillHost = ""; activeDialogProtocol = RemoteProtocol.WEBDAV },
                )
                DropdownMenuItem(
                  text = { Text("LAN / SMB SERVER", fontWeight = FontWeight.SemiBold) },
                  onClick = { showProtocolMenu = false; prefillHost = ""; activeDialogProtocol = RemoteProtocol.SMB },
                )
              }
            }
          }
        }
      }

      if (state.activeServer != null) {
        if (state.isConnectingRemote) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Text(if (strings.isEnglish) "Connecting to ${state.activeServer.host}…" else "Conectando a ${state.activeServer.host}…", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        } else if (state.remoteError != null) {
          item {
            BichromaticCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(state.remoteError, color = NexoTheme.semantic.evaRed, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                  onClick = onDisconnectServer,
                  border = BorderStroke(1.dp, NexoTheme.semantic.evaRed),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(strings.disconnectShort.uppercase(), color = NexoTheme.semantic.evaRed, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        } else {
          if (state.currentRemotePath != "/") {
            item {
              OutlinedButton(
                onClick = {
                  val parent = state.currentRemotePath.substringBeforeLast('/').ifBlank { "/" }
                  onConnectServer(state.activeServer, parent)
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(strings.upOneLevel.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }
            }
          }
          items(state.remoteEntries, key = { it.path }) { entry ->
            BichromaticCard(
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                if (entry.isDirectory) {
                  onConnectServer(state.activeServer, entry.path)
                } else {
                  onDownloadRemoteFile(entry)
                }
              },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconContainerBadge(
                  icon = if (entry.isDirectory) NexoIcons.Folder else NexoIcons.InsertDriveFile,
                  contentDescription = null,
                  highlighted = entry.isDirectory,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(entry.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = if (entry.isDirectory) (if (strings.isEnglish) "Remote folder" else "Carpeta remota") else FormatUtils.formatBytes(entry.sizeBytes),
                    style = MonoTnumStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (!entry.isDirectory) {
                  Icon(NexoIcons.Download, contentDescription = if (strings.isEnglish) "Download" else "Descargar", tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
        return@LazyColumn
      }

      // Protocol tabs
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          NetworkFilterTab.entries.forEach { tab ->
            val tabLabel = if (tab == NetworkFilterTab.ALL && strings.isEnglish) "ALL" else tab.label
            FilterChip(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              label = { Text(tabLabel, fontWeight = FontWeight.Bold) },
              shape = RoundedCornerShape(6.dp),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedTab == tab,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
              ),
            )
          }
        }
      }

      if (state.lanDevices.isNotEmpty()) {
        item {
          Text("${strings.lanDevicesTitle.uppercase()} (${state.lanDevices.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(state.lanDevices, key = { it.ipAddress }) { device ->
          BichromaticCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              prefillHost = device.ipAddress
              activeDialogProtocol = when {
                21 in device.openPorts -> RemoteProtocol.FTP
                80 in device.openPorts || 443 in device.openPorts -> RemoteProtocol.WEBDAV
                else -> RemoteProtocol.SMB
              }
            },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(icon = NexoIcons.Computer, contentDescription = null, highlighted = true)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(device.hostname, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                  "${device.ipAddress} · ${device.services.joinToString(", ")}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
              Text(strings.connectBtn.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }

      item {
        Text("${strings.savedServersTitle.uppercase()} (${filteredServers.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      }

      if (filteredServers.isEmpty()) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(strings.noSavedServers, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { prefillHost = ""; activeDialogProtocol = RemoteProtocol.FTP }, shape = RoundedCornerShape(6.dp)) {
                  Text("+ FTP", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { prefillHost = ""; activeDialogProtocol = RemoteProtocol.WEBDAV }, shape = RoundedCornerShape(6.dp)) {
                  Text("+ WEBDAV", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { prefillHost = ""; activeDialogProtocol = RemoteProtocol.SMB }, shape = RoundedCornerShape(6.dp)) {
                  Text("+ SMB", fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      } else {
        items(filteredServers, key = { it.id }) { srv ->
          BichromaticCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onConnectServer(srv, srv.remotePath) },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactView) 10.dp else 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(icon = NexoIcons.Dns, contentDescription = null, highlighted = true)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(srv.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                val protoTag = if (srv.protocol == RemoteProtocol.WEBDAV.name && srv.useHttps) "HTTPS/WebDAV" else srv.protocol
                Text(
                  "$protoTag · ${srv.host}:${srv.port} · ${srv.encoding}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(onClick = { onDeleteServer(srv.id) }) {
                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
              }
            }
          }
        }
      }
    }
  }

  when (activeDialogProtocol) {
    RemoteProtocol.FTP, RemoteProtocol.FTPS, RemoteProtocol.SFTP -> {
      NewFtpServerDialog(
        initialHost = prefillHost,
        onConfirm = { host, port, mode, user, pass, anon, enc, display ->
          activeDialogProtocol = null
          onSaveServer(
            display.ifBlank { host },
            RemoteProtocol.FTP,
            host,
            port,
            user,
            pass,
            mode.name,
            enc.name,
            false,
            "",
            anon,
          )
        },
        onDismiss = { activeDialogProtocol = null },
      )
    }
    RemoteProtocol.WEBDAV -> {
      NewWebDavServerDialog(
        initialHost = prefillHost,
        onConfirm = { host, port, useHttps, user, pass, display ->
          activeDialogProtocol = null
          onSaveServer(
            display.ifBlank { host },
            RemoteProtocol.WEBDAV,
            host,
            port,
            user,
            pass,
            "PASSIVE",
            "UTF_8",
            useHttps,
            "",
            false,
          )
        },
        onDismiss = { activeDialogProtocol = null },
      )
    }
    RemoteProtocol.SMB -> {
      NewSmbServerDialog(
        initialHost = prefillHost,
        onConfirm = { domain, server, user, pass, anon, display ->
          activeDialogProtocol = null
          onSaveServer(
            display.ifBlank { server },
            RemoteProtocol.SMB,
            server,
            445,
            user,
            pass,
            "PASSIVE",
            "UTF_8",
            false,
            domain,
            anon,
          )
        },
        onDismiss = { activeDialogProtocol = null },
      )
    }
    null -> Unit
  }

  if (showNetEsViewDialog) {
    EsViewSortDialog(
      currentMode = netEsViewMode,
      sortBy = "NAME",
      sortAsc = true,
      showSortSection = false,
      onSelectMode = {
        netEsViewMode = it
        compactView = it.startsWith("SMALL_") || it.endsWith("_ICON")
        showNetEsViewDialog = false
      },
      onDismiss = { showNetEsViewDialog = false },
    )
  }
}

@Composable
private fun NetworkBottomAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
  }
}
