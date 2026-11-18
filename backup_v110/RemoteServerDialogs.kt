package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FtpMode
import com.example.data.model.ServerEncoding
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme

@Composable
fun NewFtpServerDialog(
  initialHost: String = "",
  onConfirm: (host: String, port: Int, mode: FtpMode, username: String, password: String, anonymous: Boolean, encoding: ServerEncoding, displayAs: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var server by remember { mutableStateOf(initialHost) }
  var port by remember { mutableStateOf("21") }
  var mode by remember { mutableStateOf(FtpMode.PASSIVE) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var anonymous by remember { mutableStateOf(false) }
  var encoding by remember { mutableStateOf(ServerEncoding.AUTO) }
  var encodingExpanded by remember { mutableStateOf(false) }
  var displayAs by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Text(
        text = (if (strings.isEnglish) "NEW FTP SERVER" else "NUEVO SERVIDOR FTP"),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = server,
          onValueChange = { server = it },
          label = { Text(strings.hostOrIpLabel) },
          placeholder = { Text("192.168.1.59", style = MonoTnumStyle) },
          textStyle = MonoTnumStyle,
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("ftp_server_input"),
        )
        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter(Char::isDigit) },
          label = { Text(strings.portLabel) },
          textStyle = MonoTnumStyle,
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = strings.ftpModeLabel.uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FtpMode.entries.forEach { m ->
            FilterChip(
              selected = mode == m,
              onClick = { mode = m },
              label = { Text(m.label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
              shape = RoundedCornerShape(4.dp),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = mode == m,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
              ),
            )
          }
        }
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          enabled = !anonymous,
          label = { Text(strings.userLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          enabled = !anonymous,
          label = { Text(strings.passwordLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = anonymous,
            onCheckedChange = { anonymous = it },
            colors = CheckboxDefaults.colors(
              checkedColor = MaterialTheme.colorScheme.primary,
              checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
          )
          Text(strings.anonymousAccess, style = MaterialTheme.typography.bodyMedium)
        }
        Box {
          OutlinedButton(
            onClick = { encodingExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(
              text = "${strings.encodingLabel} ${encoding.label}",
              style = MonoTnumStyle,
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(NexoIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
          DropdownMenu(
            expanded = encodingExpanded,
            onDismissRequest = { encodingExpanded = false },
            containerColor = NexoTheme.semantic.modalSurface,
          ) {
            ServerEncoding.entries.forEach { enc ->
              DropdownMenuItem(
                text = { Text(enc.label, style = MonoTnumStyle) },
                onClick = {
                  encoding = enc
                  encodingExpanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(
          value = displayAs,
          onValueChange = { displayAs = it },
          label = { Text(strings.aliasNameLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (server.isNotBlank()) {
            onConfirm(server.trim(), port.toIntOrNull() ?: 21, mode, username, password, anonymous, encoding, displayAs.trim())
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
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
fun NewWebDavServerDialog(
  initialHost: String = "",
  initialDisplayName: String = "",
  onConfirm: (host: String, port: Int, useHttps: Boolean, username: String, password: String, displayAs: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var server by remember { mutableStateOf(initialHost) }
  var useHttps by remember { mutableStateOf(false) }
  var port by remember { mutableStateOf("80") }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var displayAs by remember { mutableStateOf(initialDisplayName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Text(
        text = (if (strings.isEnglish) "NEW WEBDAV SERVER" else "NUEVO SERVIDOR WEBDAV"),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = server,
          onValueChange = { server = it },
          label = { Text(strings.urlOrHostLabel) },
          placeholder = { Text("cloud.example.com/remote.php/dav", style = MonoTnumStyle) },
          textStyle = MonoTnumStyle,
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("webdav_server_input"),
        )
        OutlinedTextField(
          value = port,
          onValueChange = { port = it.filter(Char::isDigit) },
          label = { Text(strings.portLabel) },
          textStyle = MonoTnumStyle,
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = useHttps,
            onCheckedChange = { checked ->
              useHttps = checked
              if (checked && port == "80") port = "443"
              if (!checked && port == "443") port = "80"
            },
            colors = CheckboxDefaults.colors(
              checkedColor = MaterialTheme.colorScheme.primary,
              checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
          )
          Text(strings.useHttpsLabel, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text(strings.userLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(strings.passwordLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = displayAs,
          onValueChange = { displayAs = it },
          label = { Text(strings.aliasNameLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (server.isNotBlank()) {
            onConfirm(server.trim(), port.toIntOrNull() ?: if (useHttps) 443 else 80, useHttps, username, password, displayAs.trim())
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
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
fun NewSmbServerDialog(
  initialHost: String = "",
  onConfirm: (domain: String, server: String, username: String, password: String, anonymous: Boolean, displayAs: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var domain by remember { mutableStateOf("") }
  var server by remember { mutableStateOf(initialHost) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var anonymous by remember { mutableStateOf(false) }
  var displayAs by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.primary,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    title = {
      Text(
        text = "LAN / SMB SERVER",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = domain,
          onValueChange = { domain = it },
          label = { Text(strings.domainLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = server,
          onValueChange = { server = it },
          label = { Text(strings.hostOrIpLabel) },
          supportingText = { Text("192.168.1.100/Shared", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
          textStyle = MonoTnumStyle,
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("smb_server_input"),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          enabled = !anonymous,
          label = { Text(strings.userLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          enabled = !anonymous,
          label = { Text(strings.passwordLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = anonymous,
            onCheckedChange = { anonymous = it },
            colors = CheckboxDefaults.colors(
              checkedColor = MaterialTheme.colorScheme.primary,
              checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
          )
          Text(strings.anonymousAccess, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
          value = displayAs,
          onValueChange = { displayAs = it },
          label = { Text(strings.aliasNameLabel) },
          shape = RoundedCornerShape(4.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (server.isNotBlank()) {
            onConfirm(domain.trim(), server.trim(), username, password, anonymous, displayAs.trim())
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
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
