package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.repo.WebServerState
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.QrCodeComposable
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme

@Composable
fun ViewOnPcScreen(
  state: WebServerState,
  configuredPort: Int,
  onPortChange: (Int) -> Unit,
  onToggleServer: () -> Unit,
  onCopiedUrl: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val strings = LocalStrings.current
  var portInput by remember(configuredPort) { mutableStateOf(configuredPort.toString()) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column {
        Text(strings.viewOnPcTitle.uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(strings.viewOnPcSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    BichromaticCard(
      modifier = Modifier.fillMaxWidth(),
      accentBorder = state.isRunning,
      elevated = true,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        IconContainerBadge(
          icon = NexoIcons.Computer,
          contentDescription = null,
          size = 64.dp,
          highlighted = state.isRunning,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = (if (state.isRunning) strings.httpServerActive else strings.httpServerStopped).uppercase(),
          style = MaterialTheme.typography.titleLarge,
          color = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (state.isRunning) {
          Text(
            text = state.url,
            style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          QrCodeComposable(content = state.url, size = 160.dp)
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cm.setPrimaryClip(ClipData.newPlainText("NexoFiles URL", state.url))
              onCopiedUrl()
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(NexoIcons.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.copyAddressBtn.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
          }
        } else {
          Text(
            text = strings.viewOnPcInstruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
          onClick = onToggleServer,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("view_on_pc_toggle_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (state.isRunning) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.primary,
            contentColor = if (state.isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(4.dp),
          contentPadding = PaddingValues(vertical = 14.dp),
        ) {
          Icon(NexoIcons.PowerSettingsNew, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = (if (state.isRunning) strings.stopHttpServerBtn else strings.startHttpServerPort(configuredPort)).uppercase(),
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    BichromaticCard(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.portConfigTitle.uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = portInput,
            onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text(strings.httpPortLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            enabled = !state.isRunning,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(6.dp),
            textStyle = MonoTnumStyle,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
          )
          Button(
            onClick = { portInput.toIntOrNull()?.let(onPortChange) },
            enabled = !state.isRunning,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.apply.uppercase(), fontWeight = FontWeight.Bold)
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(NexoIcons.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = strings.transfersStat(state.lastEvent, state.transferredCount),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ViewOnPcScreenPreview() {
  NexoFilesTheme {
    ViewOnPcScreen(
      state = WebServerState(
        isRunning = true,
        url = "http://192.168.1.59:8080",
        port = 8080,
        lastEvent = "Escuchando en la red Wi-Fi",
        transferredCount = 3,
      ),
      configuredPort = 8080,
      onPortChange = {},
      onToggleServer = {},
      onCopiedUrl = {},
      onBack = {},
    )
  }
}
