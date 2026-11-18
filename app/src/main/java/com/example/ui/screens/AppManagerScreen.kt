package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.InstalledAppInfo
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.AppManagerUiState
import com.example.util.FormatUtils
import com.example.util.StoragePermissionHelper

@Composable
fun AppManagerScreen(
  state: AppManagerUiState,
  onLoadApps: () -> Unit,
  onSearchChange: (String) -> Unit,
  onBackupApk: (InstalledAppInfo) -> Unit,
  onInstallApk: (String) -> Unit,
  onUninstallApp: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val strings = LocalStrings.current
  var selectedTab by remember { mutableIntStateOf(0) }

  LaunchedEffect(Unit) {
    if (state.userApps.isEmpty() && !state.isLoading) {
      onLoadApps()
    }
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
      Column {
        Text(strings.appManagerScreenTitle.uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(strings.appManagerDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    OutlinedTextField(
      value = state.searchQuery,
      onValueChange = onSearchChange,
      placeholder = { Text(strings.searchAppPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("app_manager_search"),
      shape = RoundedCornerShape(6.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
      ),
    )

    Spacer(modifier = Modifier.height(10.dp))

    val tabs = listOf(
      strings.tabUserApps(state.userApps.size).uppercase(),
      strings.tabSystemApps(state.systemApps.size).uppercase(),
      strings.tabBackedUpApks(state.backedUpApks.size).uppercase(),
    )
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
      indicator = { positions ->
        SecondaryIndicator(
          modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
          color = MaterialTheme.colorScheme.primary,
        )
      },
    ) {
      tabs.forEachIndexed { idx, title ->
        Tab(
          selected = selectedTab == idx,
          onClick = { selectedTab = idx },
          text = {
            Text(
              text = title,
              style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
              color = if (selectedTab == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          },
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    } else if (selectedTab == 2) {
      if (state.backedUpApks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconContainerBadge(icon = NexoIcons.Android, contentDescription = null, size = 60.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(strings.noBackedUpApks, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.backedUpApks, key = { it.path }) { apk ->
            BichromaticCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconContainerBadge(icon = NexoIcons.Android, contentDescription = null, highlighted = true)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(apk.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("${FormatUtils.formatBytes(apk.sizeBytes)} · ${FormatUtils.formatDate(apk.lastModified)}", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                  if (!StoragePermissionHelper.canInstallPackages(context)) {
                    context.startActivity(StoragePermissionHelper.createInstallPermissionIntent(context))
                  } else {
                    onInstallApk(apk.path)
                  }
                }) {
                  Icon(NexoIcons.InstallMobile, contentDescription = strings.installBtn, tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    } else {
      val list = (if (selectedTab == 0) state.userApps else state.systemApps).filter {
        state.searchQuery.isBlank() ||
          it.appName.contains(state.searchQuery, ignoreCase = true) ||
          it.packageName.contains(state.searchQuery, ignoreCase = true)
      }
      LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(list, key = { it.packageName }) { app ->
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val bmp = remember(app.packageName) {
                runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
              }
              if (bmp != null) {
                Image(
                  bitmap = bmp,
                  contentDescription = app.appName,
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                )
              } else {
                IconContainerBadge(icon = NexoIcons.Android, contentDescription = app.appName)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "v${app.versionName} · ${FormatUtils.formatBytes(app.apkSizeBytes)}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { onBackupApk(app) }) {
                Icon(NexoIcons.Backup, contentDescription = strings.backupBtn, tint = MaterialTheme.colorScheme.primary)
              }
              if (!app.isSystemApp) {
                IconButton(onClick = { onUninstallApp(app.packageName) }) {
                  Icon(Icons.Rounded.Delete, contentDescription = strings.uninstallCd, tint = NexoTheme.semantic.evaRed)
                }
              }
            }
          }
        }
      }
    }
  }
}
