package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.SenderHistoryEntity
import com.example.data.repo.SenderServerState
import com.example.ui.components.BichromaticCard
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.QrCodeComposable
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils

@Composable
fun SenderScreen(
  senderState: SenderServerState,
  history: List<SenderHistoryEntity>,
  nickname: String,
  savePath: String,
  sameWifiMode: Boolean,
  availableVolumes: List<Pair<String, String>>,
  onStageAndSendUris: (List<android.net.Uri>) -> Unit,
  onStartReceiveMode: () -> Unit,
  onStartShareAppMode: () -> Unit,
  onStopServer: () -> Unit,
  onClearHistory: () -> Unit,
  onUpdateNickname: (String) -> Unit,
  onUpdateSavePath: (String) -> Unit,
  onToggleSameWifiMode: (Boolean) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  var showShareFriendsDialog by remember { mutableStateOf(false) }
  var shareStep by remember { mutableIntStateOf(1) }
  var showSenderSettings by remember { mutableStateOf(false) }

  val pickSendFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) {
      onStageAndSendUris(uris)
    }
  }

  BackHandler(onBack = onBack)

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          TextButton(
            onClick = { showSenderSettings = true },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.testTag("sender_settings_button"),
          ) {
            Icon(
              imageVector = NexoIcons.Tune,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = strings.settingsBtn.uppercase(),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          TextButton(
            onClick = onClearHistory,
            enabled = history.isNotEmpty(),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(
              imageVector = NexoIcons.DeleteSweep,
              contentDescription = null,
              tint = if (history.isNotEmpty()) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = strings.clear.uppercase(),
              style = MaterialTheme.typography.labelLarge,
              color = if (history.isNotEmpty()) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
              text = strings.senderTitleScreen.uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "${strings.visibleNameLabel}: $nickname // ${senderState.networkLabel}",
              style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      item {
        BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            SenderTransferIllustration(modifier = Modifier.size(width = 180.dp, height = 104.dp))
            Text(
              text = strings.senderSubtitleScreen,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${strings.receiveFolderLabel}: $savePath",
              style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Button(
                onClick = { pickSendFilesLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("sender_send_button"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
              ) {
                Icon(NexoIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.tabSend.uppercase(), fontWeight = FontWeight.Bold)
              }
              OutlinedButton(
                onClick = onStartReceiveMode,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("sender_receive_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(6.dp),
              ) {
                Icon(NexoIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.tabReceive.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
              }
            }

            Text(
              text = strings.shareApkBtn.uppercase(),
              style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                  shareStep = 1
                  showShareFriendsDialog = true
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("sender_no_app_link"),
            )
          }
        }
      }

      // Active Sender Session Card (with QR & URL)
      if (senderState.isServing && senderState.serverUrl.isNotBlank()) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth(), accentBorder = true) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = when (senderState.mode) {
                    "SEND" -> "${strings.tabSend.uppercase()} // ${senderState.stagedFiles.size}"
                    "RECEIVE" -> strings.receiveModeTitle.uppercase()
                    else -> strings.tabShareApp.uppercase()
                  },
                  style = MonoTnumStyle,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onStopServer) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.stopServerBtn,
                    tint = NexoTheme.semantic.evaRed,
                  )
                }
              }
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                  .padding(6.dp),
              ) {
                QrCodeComposable(content = senderState.serverUrl, size = 164.dp)
              }
              Text(
                text = senderState.serverUrl,
                style = MonoTnumStyle,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
              )
              if (senderState.lastEvent.isNotBlank()) {
                Text(
                  text = senderState.lastEvent,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedButton(
                onClick = {
                  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cm.setPrimaryClip(ClipData.newPlainText("NexoFiles Sender", senderState.serverUrl))
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(4.dp),
              ) {
                Icon(NexoIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.copyAddressBtn.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
              }
            }
          }
        }
      }

      // History
      item {
        Text(
          text = "${strings.transferHistoryTitle.uppercase()} [${history.size}]",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      if (history.isEmpty()) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = strings.noTransfersYet,
              modifier = Modifier.padding(16.dp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      } else {
        items(history, key = { it.id }) { item ->
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(
                icon = if (item.direction == "SENT") NexoIcons.Send else NexoIcons.Download,
                contentDescription = null,
                highlighted = item.direction == "SENT",
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dirLabel = if (item.direction == "SENT") {
                  if (strings.isEnglish) "Sent to" else "Enviado a"
                } else {
                  if (strings.isEnglish) "Received from" else "Recibido de"
                }
                Text(
                  text = "$dirLabel ${item.peerName} // ${FormatUtils.formatBytes(item.sizeBytes)}",
                  style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  // 2-step "Compartir con amigos" flow
  if (showShareFriendsDialog) {
    AlertDialog(
      onDismissRequest = { showShareFriendsDialog = false },
      shape = RoundedCornerShape(10.dp),
      containerColor = NexoTheme.semantic.modalSurface,
      titleContentColor = MaterialTheme.colorScheme.primary,
      textContentColor = MaterialTheme.colorScheme.onSurface,
      title = {
        Text(
          text = (if (shareStep == 1) strings.shareApkBtn else strings.shareNexoApkBtn).uppercase(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (shareStep == 1) {
            IconContainerBadge(icon = NexoIcons.Wifi, contentDescription = null, size = 54.dp, highlighted = true)
            Text(
              text = strings.shareApkDesc,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
            )
            BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = strings.sameWifiMode.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = senderState.networkLabel,
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          } else {
            val url = senderState.serverUrl.ifBlank { strings.loading }
            if (senderState.serverUrl.isNotBlank()) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                  .padding(6.dp),
              ) {
                QrCodeComposable(content = url, size = 180.dp)
              }
            }
            Text(
              text = url,
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
              textAlign = TextAlign.Center,
            )
            Text(
              text = strings.openBrowserQrHint,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      },
      confirmButton = {
        if (shareStep == 1) {
          Button(
            onClick = {
              onStartShareAppMode()
              shareStep = 2
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.confirm.uppercase(), fontWeight = FontWeight.Bold)
          }
        } else {
          Button(
            onClick = { showShareFriendsDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.doneBtn.uppercase(), fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showShareFriendsDialog = false }) {
          Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
    )
  }

  // Sender Settings Dialog (with FolderPickerDialog for save path)
  if (showSenderSettings) {
    SenderSettingsModal(
      initialNickname = nickname,
      initialSavePath = savePath,
      initialSameWifi = sameWifiMode,
      availableVolumes = availableVolumes,
      onSave = { nick, path, sameWifi ->
        onUpdateNickname(nick)
        onUpdateSavePath(path)
        onToggleSameWifiMode(sameWifi)
        showSenderSettings = false
      },
      onDismiss = { showSenderSettings = false },
    )
  }
}

@Composable
private fun SenderSettingsModal(
  initialNickname: String,
  initialSavePath: String,
  initialSameWifi: Boolean,
  availableVolumes: List<Pair<String, String>>,
  onSave: (String, String, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var nick by remember { mutableStateOf(initialNickname) }
  var path by remember { mutableStateOf(initialSavePath) }
  var sameWifi by remember { mutableStateOf(initialSameWifi) }
  var showFolderPicker by remember { mutableStateOf(false) }
  val pickerShape = RoundedCornerShape(4.dp)

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Text(
        text = strings.settingsBtn.uppercase(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = nick,
          onValueChange = { nick = it },
          label = { Text(strings.visibleNameLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = strings.receiveFolderLabel.uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(pickerShape)
            .background(NexoTheme.semantic.tacticalSurface)
            .border(1.dp, MaterialTheme.colorScheme.secondary, pickerShape)
            .clickable { showFolderPicker = true }
            .padding(12.dp)
            .testTag("sender_save_path_picker"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = path,
            style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.sameWifiMode.uppercase(), style = MaterialTheme.typography.titleSmall)
            Text(strings.sameWifiDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            checked = sameWifi,
            onCheckedChange = { sameWifi = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
              checkedTrackColor = MaterialTheme.colorScheme.primary,
              uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
              uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(nick.trim().ifBlank { initialNickname }, path, sameWifi) },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.save.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )

  if (showFolderPicker) {
    FolderPickerDialog(
      initialPath = path,
      availableVolumes = availableVolumes,
      title = strings.receiveFolderLabel,
      onSelectFolder = { picked ->
        path = picked
        showFolderPicker = false
      },
      onDismiss = { showFolderPicker = false },
    )
  }
}

@Composable
private fun SenderTransferIllustration(modifier: Modifier = Modifier) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val orange = NexoTheme.semantic.evaOrange
  val surface = MaterialTheme.colorScheme.surface
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val phoneW = w * 0.24f
    val phoneH = h * 0.78f

    // Angular Left Tactical Unit
    drawRect(
      color = primary,
      topLeft = Offset(w * 0.08f, h * 0.12f),
      size = Size(phoneW, phoneH),
      style = Stroke(width = 2.dp.toPx()),
    )
    drawRect(
      color = surface,
      topLeft = Offset(w * 0.105f, h * 0.20f),
      size = Size(phoneW * 0.8f, phoneH * 0.72f),
    )

    // Angular Right Tactical Unit
    drawRect(
      color = secondary,
      topLeft = Offset(w * 0.68f, h * 0.12f),
      size = Size(phoneW, phoneH),
      style = Stroke(width = 2.dp.toPx()),
    )
    drawRect(
      color = surface,
      topLeft = Offset(w * 0.705f, h * 0.20f),
      size = Size(phoneW * 0.8f, phoneH * 0.72f),
    )

    // Bidirectional angular HUD telemetry vectors
    val topVector = Path().apply {
      moveTo(w * 0.35f, h * 0.38f)
      lineTo(w * 0.44f, h * 0.22f)
      lineTo(w * 0.56f, h * 0.22f)
      lineTo(w * 0.65f, h * 0.38f)
    }
    drawPath(topVector, color = orange, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Square))

    val bottomVector = Path().apply {
      moveTo(w * 0.65f, h * 0.64f)
      lineTo(w * 0.56f, h * 0.80f)
      lineTo(w * 0.44f, h * 0.80f)
      lineTo(w * 0.35f, h * 0.64f)
    }
    drawPath(bottomVector, color = primary, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Square))
  }
}
