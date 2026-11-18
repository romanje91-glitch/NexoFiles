package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.NoteEditorUiState

@Composable
fun NoteEditorScreen(
  state: NoteEditorUiState,
  onFileNameChange: (String) -> Unit,
  onContentChange: (String) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val strings = LocalStrings.current
  val linesCount = if (state.content.isEmpty()) 0 else state.content.count { it == '\n' } + 1
  val charsCount = state.content.length

  var isReadOnlyMode by remember(state.filePath, state.isBinary, state.isVaultMode) {
    mutableStateOf(state.filePath != null || state.isBinary || state.isVaultMode)
  }
  var showHexView by remember(state.filePath, state.isBinary) {
    mutableStateOf(state.isBinary)
  }
  var useMonospace by remember(state.fileName, state.isBinary) {
    val ext = state.fileName.substringAfterLast('.', "").lowercase()
    mutableStateOf(state.isBinary || ext in setOf("json", "xml", "kt", "java", "js", "html", "css", "sh", "yaml", "yml", "log", "csv"))
  }

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
      IconButton(onClick = onBack, modifier = Modifier.testTag("note_editor_back")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = state.fileName.ifBlank { strings.noteEditorHeader },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (state.isVaultMode) {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NexoTheme.semantic.evaRed.copy(alpha = 0.16f))
                .border(1.dp, NexoTheme.semantic.evaRed, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.Lock, contentDescription = null, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(11.dp))
              Text(
                text = if (strings.isEnglish) "VAULT" else "BÓVEDA",
                style = MonoTnumStyle,
                fontWeight = FontWeight.Bold,
                color = NexoTheme.semantic.evaRed,
              )
            }
          }
        }
        val badge = buildString {
          append(strings.charsLinesStat(charsCount, linesCount))
          if (state.isBinary) append(" · ${strings.readOnlyBinaryBadge}")
          if (state.isTruncated) append(" · ${strings.truncatedBadge}")
          if (state.isDirty) append(" · ${strings.unsavedBadge}")
        }
        Text(
          text = badge,
          style = MonoTnumStyle,
          color = if (state.isDirty) NexoTheme.semantic.evaOrange else MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (!state.isVaultMode) {
        IconButton(
          onClick = {
            val copyPayload = if (showHexView && state.hexContent.isNotEmpty()) state.hexContent else state.content
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(state.fileName, copyPayload))
            Toast.makeText(context, if (strings.isEnglish) "Copied to clipboard" else "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.testTag("note_editor_copy_button"),
        ) {
          Icon(NexoIcons.ContentCopy, contentDescription = strings.actionCopy, tint = MaterialTheme.colorScheme.secondary)
        }
      }
      if (!state.isVaultMode && !state.isBinary && (!isReadOnlyMode || state.isDirty)) {
        Button(
          onClick = onSave,
          modifier = Modifier.testTag("note_editor_save_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(6.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Icon(NexoIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.save.uppercase(), fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChip(
        selected = isReadOnlyMode && !showHexView,
        onClick = {
          isReadOnlyMode = true
          showHexView = false
        },
        leadingIcon = { Icon(NexoIcons.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(strings.modeTextLabel.uppercase()) },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.testTag("note_mode_read"),
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = MaterialTheme.colorScheme.primary,
          selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
          selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
          containerColor = MaterialTheme.colorScheme.surface,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      )
      if (state.hexContent.isNotEmpty()) {
        FilterChip(
          selected = showHexView,
          onClick = {
            isReadOnlyMode = true
            showHexView = true
          },
          leadingIcon = { Icon(NexoIcons.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(strings.modeHexLabel.uppercase()) },
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.testTag("note_mode_hex"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        )
      }
      if (!state.isBinary && !state.isVaultMode) {
        FilterChip(
          selected = !isReadOnlyMode,
          onClick = {
            showHexView = false
            isReadOnlyMode = false
          },
          leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(strings.edit.uppercase()) },
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.testTag("note_mode_edit"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        )
      }
      if (!showHexView) {
        FilterChip(
          selected = useMonospace,
          onClick = { useMonospace = !useMonospace },
          label = { Text(if (useMonospace) "MONO" else "SANS") },
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.testTag("note_font_toggle"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NexoTheme.semantic.tacticalSurface,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        )
      }
    }

    if (state.isBinary) {
      Spacer(modifier = Modifier.height(6.dp))
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = NexoTheme.semantic.tacticalSurface,
        border = BorderStroke(1.dp, NexoTheme.semantic.evaOrange.copy(alpha = 0.7f)),
      ) {
        Text(
          text = strings.binaryReadOnlyHint,
          style = MaterialTheme.typography.labelSmall,
          color = NexoTheme.semantic.evaOrange,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (state.isLoading) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
      return@Column
    }

    if (state.filePath == null && !isReadOnlyMode && !state.isVaultMode) {
      OutlinedTextField(
        value = state.fileName,
        onValueChange = onFileNameChange,
        label = { Text(strings.fileNameInputLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          focusedTextColor = MaterialTheme.colorScheme.onSurface,
          unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(".txt", ".md", ".json", ".xml", ".log").forEach { ext ->
          val active = state.fileName.endsWith(ext, ignoreCase = true)
          FilterChip(
            selected = active,
            onClick = {
              val base = state.fileName.substringBeforeLast('.', state.fileName)
              onFileNameChange("$base$ext")
            },
            label = { Text(ext) },
            shape = RoundedCornerShape(6.dp),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = NexoTheme.semantic.tacticalSurface,
              selectedLabelColor = MaterialTheme.colorScheme.primary,
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }

    if (isReadOnlyMode || showHexView) {
      val displayText = if (showHexView) {
        state.hexContent.ifEmpty { state.content }
      } else {
        state.content
      }
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .testTag("note_reader_container"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        SelectionContainer {
          Text(
            text = displayText.ifEmpty { "(${strings.empty})" },
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(vScroll)
              .then(if (showHexView) Modifier.horizontalScroll(hScroll) else Modifier)
              .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
              fontFamily = if (useMonospace || showHexView) JetBrainsMonoFamily else FontFamily.Default,
              fontSize = if (showHexView) 12.sp else if (useMonospace) 13.sp else 15.sp,
              lineHeight = if (showHexView) 18.sp else if (useMonospace) 20.sp else 23.sp,
              color = if (displayText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            ),
          )
        }
      }
    } else {
      OutlinedTextField(
        value = state.content,
        onValueChange = onContentChange,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .testTag("note_editor_content_input"),
        placeholder = { Text(strings.editorPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
          fontFamily = if (useMonospace) JetBrainsMonoFamily else FontFamily.Default,
          fontSize = if (useMonospace) 13.sp else 15.sp,
          lineHeight = if (useMonospace) 20.sp else 22.sp,
          color = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun NoteEditorScreenPreview() {
  NexoFilesTheme {
    NoteEditorScreen(
      state = NoteEditorUiState(
        fileName = "config.json",
        content = "{\n  \"app\": \"NexoFiles\",\n  \"version\": 1\n}",
        isDirty = true,
      ),
      onFileNameChange = {},
      onContentChange = {},
      onSave = {},
      onBack = {},
    )
  }
}
