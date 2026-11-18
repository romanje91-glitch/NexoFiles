package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.NetworkServerEntity
import com.example.data.model.CloudProviderType
import com.example.ui.components.BichromaticCard
import com.example.ui.components.ChamferedTechCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme

@Composable
fun ExternalCloudsScreen(
  savedServers: List<NetworkServerEntity>,
  onSaveWebDavCloudAccount: (String, String, Int, String, String, Boolean) -> Unit,
  onOpenServer: (NetworkServerEntity) -> Unit,
  onDeleteServer: (Long) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  var selectedProviderForm by remember { mutableStateOf<CloudProviderType?>(null) }

  val webDavCloudServers = remember(savedServers) {
    savedServers.filter { it.protocol == "WEBDAV" }
  }
  val externalProviders = remember {
    listOf(
      CloudProviderType.ONEDRIVE,
      CloudProviderType.DROPBOX,
      CloudProviderType.WEBDAV,
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
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
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("external_clouds_back"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = strings.back,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = strings.externalCloudsTitle.uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = strings.externalCloudsSubtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        Text(
          text = strings.externalCloudsProvidersTitle.uppercase(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = NexoTheme.semantic.evaOrange,
        )
      }

      items(externalProviders, key = { it.name }) { provider ->
        ChamferedTechCard(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("external_cloud_provider_${provider.name}"),
          accentGlow = true,
          onClick = { selectedProviderForm = provider },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconContainerBadge(
              icon = NexoIcons.Cloud,
              contentDescription = null,
              highlighted = true,
              size = 44.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = provider.label.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = provider.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = strings.externalCloudsSavedTitle.uppercase(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      if (webDavCloudServers.isEmpty()) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = strings.externalCloudsEmptySaved,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(webDavCloudServers, key = { "ext_cloud_${it.id}" }) { server ->
          BichromaticCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ext_cloud_saved_${server.id}"),
            accentBorder = true,
            onClick = { onOpenServer(server) },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(
                icon = NexoIcons.CloudDone,
                contentDescription = null,
                highlighted = true,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = server.name,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${if (server.useHttps) "https" else "http"}://${server.host}:${server.port}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.secondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Button(
                onClick = { onOpenServer(server) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              ) {
                Text(strings.open.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
              IconButton(onClick = { onDeleteServer(server.id) }) {
                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
              }
            }
          }
        }
      }
    }
  }

  selectedProviderForm?.let { provider ->
    NewWebDavServerDialog(
      initialHost = when (provider) {
        CloudProviderType.ONEDRIVE -> "d.docs.live.net"
        CloudProviderType.DROPBOX -> "dav.dropbox.com"
        else -> ""
      },
      initialDisplayName = provider.label,
      onConfirm = { host, port, useHttps, user, pass, display ->
        selectedProviderForm = null
        onSaveWebDavCloudAccount(display.ifBlank { provider.label }, host, port, user, pass, useHttps)
      },
      onDismiss = { selectedProviderForm = null },
    )
  }
}
