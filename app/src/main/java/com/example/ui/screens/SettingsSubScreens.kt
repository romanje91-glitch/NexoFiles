package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GestureBinding
import com.example.data.model.VaultLockMethod
import com.example.data.prefs.BottomBarPreferences
import com.example.ui.components.NexoIcons
import com.example.ui.components.TacticalPatternGrid
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.CrashLogger
import kotlin.math.abs

@Composable
fun SettingsSwitchRow(
  title: String,
  subtitle: String? = null,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCheckedChange(!checked) }
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
      if (!subtitle.isNullOrBlank()) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
    )
  }
}

@Composable
fun FolderSettingRow(
  label: String,
  path: String,
  onPick: () -> Unit,
) {
  val shape = RoundedCornerShape(6.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(MaterialTheme.colorScheme.background)
      .border(1.dp, MaterialTheme.colorScheme.outline, shape)
      .clickable(onClick = onPick)
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(label.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
      Text(path, style = MonoTnumStyle, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun DrawerManagementSection(
  orderIds: List<String>,
  hiddenIds: Set<String>,
  onSave: (List<String>, Set<String>) -> Unit,
) {
  val strings = LocalStrings.current
  val labels = remember(strings) {
    mapOf(
      "HOME" to strings.drawerHome,
      "FILES" to strings.drawerFiles,
      "ANALYZE" to strings.drawerAnalyze,
      "SENDER" to strings.drawerSender,
      "DRIVE" to strings.drawerDrive,
      "GOOGLE_PHOTOS" to strings.drawerGooglePhotos,
      "EXTERNAL_CLOUDS" to strings.drawerExternalClouds,
      "VIEW_ON_PC" to strings.drawerViewOnPc,
      "VAULT" to strings.drawerVault,
      "RECYCLE_BIN" to strings.drawerRecycleBin,
    )
  }
  val rowShape = RoundedCornerShape(5.dp)
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    orderIds.forEachIndexed { idx, id ->
      val visible = id !in hiddenIds
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.background, rowShape)
          .border(1.dp, MaterialTheme.colorScheme.outline, rowShape)
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(labels[id] ?: id, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(
          onClick = {
            if (idx > 0) {
              val mutable = orderIds.toMutableList()
              val tmp = mutable[idx - 1]
              mutable[idx - 1] = mutable[idx]
              mutable[idx] = tmp
              onSave(mutable, hiddenIds)
            }
          },
          enabled = idx > 0,
          modifier = Modifier.size(32.dp),
        ) {
          Icon(NexoIcons.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        IconButton(
          onClick = {
            if (idx < orderIds.lastIndex) {
              val mutable = orderIds.toMutableList()
              val tmp = mutable[idx + 1]
              mutable[idx + 1] = mutable[idx]
              mutable[idx] = tmp
              onSave(mutable, hiddenIds)
            }
          },
          enabled = idx < orderIds.lastIndex,
          modifier = Modifier.size(32.dp),
        ) {
          Icon(NexoIcons.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Switch(
          checked = visible,
          onCheckedChange = { show ->
            val nextHidden = hiddenIds.toMutableSet()
            if (show) nextHidden.remove(id) else nextHidden.add(id)
            onSave(orderIds, nextHidden)
          },
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
          ),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolbarCustomizationSection(
  enabledActions: List<String>,
  onUpdateActions: (List<String>) -> Unit,
) {
  val strings = LocalStrings.current
  val groups = remember(strings) {
    listOf(
      strings.navFiles to listOf("COPY" to strings.actionCopy, "MOVE" to strings.actionMove, "RENAME" to strings.rename, "ZIP" to strings.compressBtn, "SHARE" to strings.share, "DELETE" to strings.delete),
      strings.networkTitle to listOf("FTP" to "FTP", "WEBDAV" to "WebDAV", "SMB" to "LAN / SMB"),
      strings.toolsHubTitle to listOf("SENDER" to strings.toolSender, "VIEW_PC" to strings.toolViewOnPc),
    )
  }
  val chipShape = RoundedCornerShape(4.dp)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    groups.forEach { (groupTitle, items) ->
      Text(groupTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, label) ->
          val enabled = id in enabledActions
          Surface(
            shape = chipShape,
            color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .clip(chipShape)
              .clickable {
                val next = enabledActions.toMutableList()
                if (enabled) next.remove(id) else next.add(id)
                onUpdateActions(next)
              },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (enabled) "[-] $label" else "[+] $label",
                style = MonoTnumStyle,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MasterPinSetupDialog(
  hasExistingPin: Boolean,
  onSavePin: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = { Text(strings.masterPinDialogTitle.uppercase(), color = MaterialTheme.colorScheme.primary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null },
          label = { Text(strings.newPinLabel) },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = confirmPin,
          onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); error = null },
          label = { Text(strings.confirm) },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NexoTheme.semantic.evaRed) }
        if (hasExistingPin) {
          TextButton(onClick = { onSavePin(""); onDismiss() }) {
            Text(strings.disablePin.uppercase(), color = NexoTheme.semantic.evaRed)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pin.length < 4) {
            error = strings.newPinLabel
          } else if (pin != confirmPin) {
            error = strings.masterPinIncorrect
          } else {
            onSavePin(pin)
            onDismiss()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.save.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel.uppercase()) } },
  )
}

@Composable
fun VaultVerifyCurrentDialog(
  currentMethod: VaultLockMethod,
  onVerifySecret: (String) -> Boolean,
  onVerified: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var input by remember { mutableStateOf("") }
  val patternNodes = remember { mutableStateListOf<Int>() }
  var isError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Text(
        text = strings.vaultVerifyCurrentTitle.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = strings.vaultVerifyCurrentDesc(strings.vaultMethodLabel(currentMethod)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (currentMethod) {
          VaultLockMethod.PATTERN -> {
            TacticalPatternGrid(
              selectedNodes = patternNodes,
              onNodeTap = { node ->
                if (node !in patternNodes) {
                  patternNodes.add(node)
                  isError = false
                }
              },
              onReset = {
                patternNodes.clear()
                isError = false
              },
              isError = isError,
            )
          }
          VaultLockMethod.PIN -> {
            OutlinedTextField(
              value = input,
              onValueChange = {
                input = it.filter(Char::isDigit).take(8)
                isError = false
              },
              label = { Text(strings.vaultPinHint) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              isError = isError,
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_verify_pin_input"),
            )
          }
          VaultLockMethod.PASSWORD -> {
            OutlinedTextField(
              value = input,
              onValueChange = {
                input = it
                isError = false
              },
              label = { Text(strings.vaultPasswordHint) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              isError = isError,
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_verify_password_input"),
            )
          }
          VaultLockMethod.BIOMETRIC -> Unit
        }
        if (isError) {
          Text(
            text = strings.vaultCredentialIncorrect,
            style = MaterialTheme.typography.labelSmall,
            color = NexoTheme.semantic.evaRed,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val candidate = if (currentMethod == VaultLockMethod.PATTERN) {
            patternNodes.joinToString("-")
          } else {
            input
          }
          if (onVerifySecret(candidate)) {
            onVerified()
          } else {
            isError = true
            patternNodes.clear()
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.testTag("vault_verify_confirm_btn"),
      ) {
        Text(strings.confirm.uppercase(), fontWeight = FontWeight.Bold)
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
fun VaultSecurityConfigDialog(
  currentMethod: VaultLockMethod,
  onSaveConfig: (VaultLockMethod, String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var selectedMethod by remember(currentMethod) { mutableStateOf(currentMethod) }
  var secretDraft by remember { mutableStateOf("") }
  var confirmDraft by remember { mutableStateOf("") }
  val patternNodes = remember { mutableStateListOf<Int>() }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Text(
        text = strings.vaultSelectNewMethodTitle.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VaultLockMethod.entries.forEach { method ->
          val active = selectedMethod == method
          val rowShape = RoundedCornerShape(6.dp)
          Surface(
            shape = rowShape,
            color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .fillMaxWidth()
              .clip(rowShape)
              .clickable {
                selectedMethod = method
                secretDraft = ""
                confirmDraft = ""
                patternNodes.clear()
                errorMsg = null
              }
              .testTag("settings_vault_method_${method.name}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = active,
                onClick = {
                  selectedMethod = method
                  secretDraft = ""
                  confirmDraft = ""
                  patternNodes.clear()
                  errorMsg = null
                },
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = strings.vaultMethodLabel(method),
                style = MaterialTheme.typography.titleSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        if (selectedMethod != VaultLockMethod.BIOMETRIC) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          Text(
            text = strings.vaultSetupCredentialHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        when (selectedMethod) {
          VaultLockMethod.BIOMETRIC -> Unit
          VaultLockMethod.PATTERN -> {
            Text(
              text = strings.vaultPatternHint,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TacticalPatternGrid(
              selectedNodes = patternNodes,
              onNodeTap = { node ->
                if (node !in patternNodes) {
                  patternNodes.add(node)
                  errorMsg = null
                }
              },
              onReset = {
                patternNodes.clear()
                errorMsg = null
              },
              isError = errorMsg != null,
            )
          }
          VaultLockMethod.PIN -> {
            OutlinedTextField(
              value = secretDraft,
              onValueChange = { secretDraft = it.filter(Char::isDigit).take(8); errorMsg = null },
              label = { Text(strings.vaultPinHint) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_vault_pin_input"),
            )
            OutlinedTextField(
              value = confirmDraft,
              onValueChange = { confirmDraft = it.filter(Char::isDigit).take(8); errorMsg = null },
              label = { Text(strings.confirm) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          VaultLockMethod.PASSWORD -> {
            OutlinedTextField(
              value = secretDraft,
              onValueChange = { secretDraft = it; errorMsg = null },
              label = { Text(strings.vaultPasswordHint) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_vault_password_input"),
            )
            OutlinedTextField(
              value = confirmDraft,
              onValueChange = { confirmDraft = it; errorMsg = null },
              label = { Text(strings.confirm) },
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        errorMsg?.let { msg ->
          Text(msg, style = MaterialTheme.typography.labelSmall, color = NexoTheme.semantic.evaRed)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when (selectedMethod) {
            VaultLockMethod.BIOMETRIC -> {
              onSaveConfig(VaultLockMethod.BIOMETRIC, null)
              onDismiss()
            }
            VaultLockMethod.PATTERN -> {
              if (patternNodes.size < 4) {
                errorMsg = strings.vaultPatternHint
              } else {
                onSaveConfig(VaultLockMethod.PATTERN, patternNodes.joinToString("-"))
                onDismiss()
              }
            }
            VaultLockMethod.PIN -> {
              when {
                secretDraft.length < 4 -> errorMsg = strings.vaultPinHint
                secretDraft != confirmDraft -> errorMsg = strings.vaultCredentialIncorrect
                else -> {
                  onSaveConfig(VaultLockMethod.PIN, secretDraft)
                  onDismiss()
                }
              }
            }
            VaultLockMethod.PASSWORD -> {
              when {
                secretDraft.length < 4 -> errorMsg = strings.vaultPasswordHint
                secretDraft != confirmDraft -> errorMsg = strings.vaultCredentialIncorrect
                else -> {
                  onSaveConfig(VaultLockMethod.PASSWORD, secretDraft)
                  onDismiss()
                }
              }
            }
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.testTag("settings_vault_save_btn"),
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
}

@Composable
fun GestureSettingsDialog(
  gesturesEnabled: Boolean,
  bindings: List<GestureBinding>,
  onToggleGestures: (Boolean) -> Unit,
  onSaveGesture: (GestureBinding) -> Unit,
  onDeleteGesture: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  val points = remember { mutableStateListOf<Offset>() }
  val hintText = strings.gestureShapeLabel
  var detectedShape by remember(hintText) { mutableStateOf(hintText) }
  val targets = remember(strings) {
    listOf(
      "/storage/emulated/0/Download" to strings.downloadDirLabel,
      "/storage/emulated/0/DCIM/Camera" to strings.catImages,
      "route:drive" to strings.drawerDrive,
      "route:sender" to strings.toolSender,
      "route:analyze" to strings.storageAnalysisTitle,
    )
  }
  var selectedTarget by remember(targets) { mutableStateOf(targets.first()) }

  fun classifyShape(pts: List<Offset>): String {
    if (pts.size < 6) return "CUSTOM"
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val first = pts.first()
    val last = pts.last()
    val mid = pts[pts.size / 2]
    val distEnd = kotlin.math.hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble())
    val width = (maxX - minX).coerceAtLeast(1f)
    return when {
      distEnd < width * 0.45f -> "O"
      mid.y > first.y + 30f && mid.y > last.y + 30f -> "V"
      abs(first.x - mid.x) < width * 0.35f && last.x > mid.x + width * 0.35f -> "L"
      first.x < last.x && mid.y < last.y -> "Z"
      else -> "CUSTOM"
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = { Text(strings.gesturesSettingTitle.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSwitchRow(title = strings.gesturesSettingTitle, checked = gesturesEnabled, onCheckedChange = onToggleGestures)
        Text(strings.gestureShapeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val strokeColor = MaterialTheme.colorScheme.primary
        val padShape = RoundedCornerShape(8.dp)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(padShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.secondary, padShape)
            .pointerInput(Unit) {
              detectDragGestures(
                onDragStart = { start ->
                  points.clear()
                  points.add(start)
                },
                onDrag = { change, _ ->
                  change.consume()
                  points.add(change.position)
                },
                onDragEnd = {
                  detectedShape = classifyShape(points.toList())
                },
              )
            }
            .testTag("gesture_canvas_pad"),
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size > 1) {
              val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { p -> lineTo(p.x, p.y) }
              }
              drawPath(path, color = strokeColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
          }
          Text(
            text = detectedShape,
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(6.dp),
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          targets.take(3).forEach { target ->
            FilterChip(
              selected = selectedTarget.first == target.first,
              onClick = { selectedTarget = target },
              label = { Text(target.second, style = MaterialTheme.typography.labelSmall) },
              shape = RoundedCornerShape(4.dp),
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
          }
        }

        Button(
          onClick = {
            val shape = if (detectedShape == hintText) "V" else detectedShape
            onSaveGesture(
              GestureBinding(
                id = "g_${System.currentTimeMillis()}",
                gestureShape = shape,
                label = selectedTarget.second,
                targetRouteOrPath = selectedTarget.first,
              )
            )
            points.clear()
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text("${strings.addGestureBtn.uppercase()} '${if (detectedShape == hintText) "V" else detectedShape}'", fontWeight = FontWeight.Bold)
        }

        if (bindings.isNotEmpty()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          bindings.forEach { b ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text("[${b.gestureShape}] -> ${b.label}", modifier = Modifier.weight(1f), style = MonoTnumStyle)
              IconButton(onClick = { onDeleteGesture(b.id) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close.uppercase()) } },
  )
}

@Composable
fun MasterPinVerifyDialog(
  onVerifyPin: (String) -> Boolean,
  onVerified: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var pinInput by remember { mutableStateOf("") }
  var pinError by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = { Text(strings.masterPinRequiredTitle.uppercase(), color = MaterialTheme.colorScheme.primary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = strings.masterPinRequiredDesc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = pinInput,
          onValueChange = {
            pinInput = it
            pinError = false
          },
          label = { Text(strings.masterPinLabel) },
          visualTransformation = PasswordVisualTransformation(),
          isError = pinError,
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        if (pinError) {
          Text(
            text = strings.masterPinIncorrect,
            style = MaterialTheme.typography.labelSmall,
            color = NexoTheme.semantic.evaRed,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (onVerifyPin(pinInput)) {
            onVerified()
          } else {
            pinError = true
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.enterBtn.uppercase(), fontWeight = FontWeight.Bold)
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
fun BottomBarCustomizationDialog(
  activeTabs: List<String>,
  onToggleTab: (String) -> Boolean,
  onMoveTab: (String, Int) -> Unit,
  onResetDefaults: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var limitNotice by remember { mutableStateOf<String?>(null) }

  val tabLabels = remember(strings) {
    mapOf(
      "HOME" to strings.navHome,
      "FILES" to strings.navFiles,
      "VAULT" to strings.navVault,
      "ANALYZE" to strings.navAnalyze,
      "TOOLS" to strings.navTools,
      "DRIVE" to strings.drawerDrive,
      "EXTERNAL_CLOUDS" to strings.drawerExternalClouds,
      "NETWORK" to strings.networkTitle,
      "RECYCLE_BIN" to strings.drawerRecycleBin,
      "SETTINGS" to strings.drawerSettings,
    )
  }

  val orderedForDisplay = remember(activeTabs) {
    activeTabs + BottomBarPreferences.ALL_AVAILABLE_TAB_IDS.filterNot { it in activeTabs }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Column {
        Text(
          text = strings.bottomBarSetupTitle.uppercase(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "${activeTabs.size} / ${BottomBarPreferences.MAX_TABS}",
          style = MonoTnumStyle,
          color = NexoTheme.semantic.evaOrange,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = strings.bottomBarSetupDesc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        limitNotice?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.labelSmall,
            color = NexoTheme.semantic.evaRed,
            fontWeight = FontWeight.Bold,
          )
        }
        val rowShape = RoundedCornerShape(6.dp)
        orderedForDisplay.forEach { tabId ->
          val activeIdx = activeTabs.indexOf(tabId)
          val isActive = activeIdx >= 0
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(rowShape)
              .background(if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
              .border(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, rowShape)
              .clickable {
                val ok = onToggleTab(tabId)
                limitNotice = if (ok) null else strings.bottomBarLimitToast
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
              .testTag("bottom_bar_opt_$tabId"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = isActive,
              onCheckedChange = {
                val ok = onToggleTab(tabId)
                limitNotice = if (ok) null else strings.bottomBarLimitToast
              },
              colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
              ),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = (tabLabels[tabId] ?: tabId).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
              )
              if (isActive) {
                Text(
                  text = "#${activeIdx + 1}",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            if (isActive) {
              IconButton(
                onClick = { onMoveTab(tabId, -1) },
                enabled = activeIdx > 0,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(NexoIcons.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
              }
              IconButton(
                onClick = { onMoveTab(tabId, 1) },
                enabled = activeIdx < activeTabs.lastIndex,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(NexoIcons.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
        OutlinedButton(
          onClick = {
            limitNotice = null
            onResetDefaults()
          },
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text(strings.bottomBarResetBtn.uppercase(), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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
        Text(strings.doneBtn.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
  )
}

@Composable
fun CrashDiagnosticsDialog(
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  var logText by remember { mutableStateOf(CrashLogger.readCrashLog(context)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Text(
        text = strings.crashDiagTitle.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = NexoTheme.semantic.evaOrange,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = strings.crashDiagSubtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.background,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 320.dp),
        ) {
          Text(
            text = logText.ifBlank { strings.crashDiagEmpty },
            style = MonoTnumStyle,
            color = if (logText.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
              .verticalScroll(rememberScrollState())
              .padding(10.dp),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = {
              if (CrashLogger.copyCrashLogToClipboard(context)) {
                Toast.makeText(context, strings.crashCopiedToast, Toast.LENGTH_SHORT).show()
              }
            },
            enabled = logText.isNotBlank(),
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(NexoIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.crashCopyReportBtn.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = {
              CrashLogger.clearCrashLog(context)
              logText = ""
            },
            enabled = logText.isNotBlank(),
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, NexoTheme.semantic.evaRed),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.crashClearLogBtn.uppercase(), style = MaterialTheme.typography.labelSmall, color = NexoTheme.semantic.evaRed)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
      }
    },
  )
}

