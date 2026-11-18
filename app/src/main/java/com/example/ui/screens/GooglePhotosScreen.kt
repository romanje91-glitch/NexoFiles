package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.GooglePhotoMediaItem
import com.example.ui.components.BichromaticCard
import com.example.ui.components.ChamferedTechCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.GooglePhotosUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GooglePhotosScreen(
  photosState: GooglePhotosUiState,
  onBack: () -> Unit,
  onConnectPhotos: (Activity) -> Unit,
  onSilentRefreshPhotos: () -> Unit = {},
  onConsentIntentResult: (Intent?) -> Unit,
  onConsumePendingConsent: () -> Unit,
  onDisconnectPhotos: () -> Unit,
  onStartPickerSession: () -> Unit,
  onCheckPickerSelection: () -> Unit,
  onClearSession: () -> Unit,
  onEnsureCached: (GooglePhotoMediaItem) -> Unit,
  onSaveToDevice: (GooglePhotoMediaItem) -> Unit,
  onEditInNexo: (GooglePhotoMediaItem) -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("google_photos_back"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = strings.back,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = strings.googlePhotosTitle.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = photosState.accountEmail ?: strings.googlePhotosSubtitle,
            style = MonoTnumStyle,
            color = if (photosState.isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (photosState.isAuthorized) {
          IconButton(
            onClick = onDisconnectPhotos,
            modifier = Modifier.testTag("google_photos_disconnect"),
          ) {
            Icon(
              imageVector = NexoIcons.Logout,
              contentDescription = strings.disconnectBtn,
              tint = NexoTheme.semantic.evaRed,
            )
          }
        }
      }

      GooglePhotosBodyContent(
        photosState = photosState,
        onConnectPhotos = onConnectPhotos,
        onSilentRefreshPhotos = onSilentRefreshPhotos,
        onConsentIntentResult = onConsentIntentResult,
        onConsumePendingConsent = onConsumePendingConsent,
        onDisconnectPhotos = onDisconnectPhotos,
        onStartPickerSession = onStartPickerSession,
        onCheckPickerSelection = onCheckPickerSelection,
        onClearSession = onClearSession,
        onEnsureCached = onEnsureCached,
        onSaveToDevice = onSaveToDevice,
        onEditInNexo = onEditInNexo,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
fun GooglePhotosBodyContent(
  photosState: GooglePhotosUiState,
  onConnectPhotos: (Activity) -> Unit,
  onSilentRefreshPhotos: () -> Unit = {},
  onConsentIntentResult: (Intent?) -> Unit,
  onConsumePendingConsent: () -> Unit,
  onDisconnectPhotos: () -> Unit,
  onStartPickerSession: () -> Unit,
  onCheckPickerSelection: () -> Unit,
  onClearSession: () -> Unit,
  onEnsureCached: (GooglePhotoMediaItem) -> Unit,
  onSaveToDevice: (GooglePhotoMediaItem) -> Unit,
  onEditInNexo: (GooglePhotoMediaItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val strings = LocalStrings.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var previewItem by remember { mutableStateOf<GooglePhotoMediaItem?>(null) }

  LaunchedEffect(photosState.accountEmail, photosState.accessToken) {
    if (!photosState.accountEmail.isNullOrBlank() && photosState.accessToken.isNullOrBlank()) {
      onSilentRefreshPhotos()
    }
  }

  val consentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    onConsentIntentResult(result.data)
  }

  LaunchedEffect(photosState.pendingConsentIntent) {
    val pending = photosState.pendingConsentIntent ?: return@LaunchedEffect
    onConsumePendingConsent()
    consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
  }

  DisposableEffect(lifecycleOwner, photosState.activeSession?.sessionId) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME && photosState.activeSession != null && !photosState.activeSession.mediaItemsSet) {
        onCheckPickerSelection()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (!photosState.isAuthorized) {
      item {
        ChamferedTechCard(
          modifier = Modifier.fillMaxWidth(),
          chamfer = 10.dp,
          accentGlow = true,
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconContainerBadge(
                icon = NexoIcons.PhotoLibrary,
                contentDescription = null,
                highlighted = true,
                size = 46.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = strings.googlePhotosTitle.uppercase(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = strings.googlePhotosSubtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Text(
              text = strings.googlePhotosNotConnectedDesc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = { activity?.let(onConnectPhotos) },
              enabled = !photosState.isLoading && activity != null,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("google_photos_connect_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(6.dp),
            ) {
              if (photosState.isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text(strings.connectGooglePhotosBtn.uppercase(), fontWeight = FontWeight.Bold)
            }
            photosState.errorMessage?.let { err ->
              Text(err, style = MaterialTheme.typography.bodySmall, color = NexoTheme.semantic.evaRed)
            }
          }
        }
      }
    } else {
      item {
        BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconContainerBadge(
                icon = NexoIcons.PhotoLibrary,
                contentDescription = null,
                highlighted = true,
                size = 40.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = photosState.accountName ?: strings.googlePhotosTitle,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = photosState.accountEmail ?: "PICKER API · LIBRARY APPEND",
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (photosState.pickedItems.isNotEmpty()) {
                TextButton(onClick = onClearSession) {
                  Text(strings.clearPickedSessionBtn.uppercase(), color = NexoTheme.semantic.evaOrange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
              }
            }

            Button(
              onClick = onStartPickerSession,
              enabled = !photosState.isLoading,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("google_photos_open_picker_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(6.dp),
            ) {
              Icon(NexoIcons.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.openPhotosPickerBtn.uppercase(), fontWeight = FontWeight.Bold)
            }

            photosState.errorMessage?.let { err ->
              Text(err, style = MaterialTheme.typography.bodySmall, color = NexoTheme.semantic.evaRed)
            }
          }
        }
      }

      if (photosState.activeSession != null && !photosState.activeSession.mediaItemsSet) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth(), accentBorder = true) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = NexoTheme.semantic.evaOrange,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = strings.pickerPollingProgress.uppercase(),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = NexoTheme.semantic.evaOrange,
                )
              }
              Text(
                text = strings.pickerWaitingBanner,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              LinearProgressIndicator(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
              )
              OutlinedButton(
                onClick = onCheckPickerSelection,
                modifier = Modifier.align(Alignment.End),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp),
              ) {
                Icon(NexoIcons.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.checkPickerSelectionBtn.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      if (photosState.isLoading && photosState.pickedItems.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
      } else if (photosState.pickedItems.isEmpty()) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              IconContainerBadge(icon = NexoIcons.Image, contentDescription = null, size = 52.dp)
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = strings.noPickedPhotosYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        item {
          Text(
            text = strings.pickedPhotosSectionTitle(photosState.pickedItems.size).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        val rows = photosState.pickedItems.chunked(2)
        items(rows, key = { it.first().id }) { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            pair.forEach { item ->
              Box(modifier = Modifier.weight(1f)) {
                PickedGooglePhotoCard(
                  item = item,
                  isDownloading = item.id in photosState.downloadingItemIds,
                  onEnsureCached = { onEnsureCached(item) },
                  onPreview = { previewItem = item },
                  onSaveLocal = { onSaveToDevice(item) },
                  onEditInNexo = { onEditInNexo(item) },
                )
              }
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }

  previewItem?.let { item ->
    GooglePhotoPreviewDialog(
      item = item,
      isDownloading = item.id in photosState.downloadingItemIds,
      onSaveLocal = {
        previewItem = null
        onSaveToDevice(item)
      },
      onEditInNexo = {
        previewItem = null
        onEditInNexo(item)
      },
      onDismiss = { previewItem = null },
    )
  }
}

@Composable
private fun PickedGooglePhotoCard(
  item: GooglePhotoMediaItem,
  isDownloading: Boolean,
  onEnsureCached: () -> Unit,
  onPreview: () -> Unit,
  onSaveLocal: () -> Unit,
  onEditInNexo: () -> Unit,
) {
  val strings = LocalStrings.current
  var thumbBitmap by remember(item.cachedFilePath) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(item.id, item.cachedFilePath) {
    if (item.cachedFilePath == null) {
      onEnsureCached()
    } else {
      thumbBitmap = withContext(Dispatchers.IO) {
        decodeThumbnailSafe(item.cachedFilePath, 380)
      }
    }
  }

  BichromaticCard(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("picked_photo_card_${item.id}"),
    accentBorder = item.cachedFilePath != null,
    onClick = onPreview,
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        val bmp = thumbBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = item.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else if (isDownloading) {
          CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.2.dp,
          )
        } else {
          Icon(
            imageVector = NexoIcons.Image,
            contentDescription = item.filename,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(38.dp),
          )
        }

        if (item.width > 0 && item.height > 0) {
          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(6.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = "${item.width}×${item.height}",
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        if (item.cachedFilePath != null) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.primary)
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = strings.downloadedReadyBadge.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
      }

      Text(
        text = item.filename,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Button(
        onClick = onEditInNexo,
        enabled = !isDownloading,
        modifier = Modifier
          .fillMaxWidth()
          .height(38.dp)
          .testTag("edit_picked_photo_${item.id}"),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = strings.editInNexoPhotoEditor.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      OutlinedButton(
        onClick = onSaveLocal,
        enabled = !isDownloading,
        modifier = Modifier
          .fillMaxWidth()
          .height(34.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
      ) {
        Icon(NexoIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(strings.downloadPhotoToLocalBtn.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
      }
    }
  }
}

@Composable
private fun GooglePhotoPreviewDialog(
  item: GooglePhotoMediaItem,
  isDownloading: Boolean,
  onSaveLocal: () -> Unit,
  onEditInNexo: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var previewBitmap by remember(item.cachedFilePath) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(item.cachedFilePath) {
    val path = item.cachedFilePath ?: return@LaunchedEffect
    previewBitmap = withContext(Dispatchers.IO) {
      decodeThumbnailSafe(path, 1280)
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = NexoTheme.semantic.modalSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.filename,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (item.width > 0) "${item.width} × ${item.height} · ${item.mimeType}" else item.mimeType,
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(NexoTheme.semantic.tacticalSurface),
          ) {
            Icon(NexoIcons.Close, contentDescription = strings.close, tint = MaterialTheme.colorScheme.onSurface)
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 340.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center,
        ) {
          val bmp = previewBitmap
          when {
            bmp != null -> Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = item.filename,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxWidth(),
            )
            isDownloading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            else -> Icon(NexoIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onSaveLocal,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(strings.downloadPhotoToLocalBtn.uppercase(), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
          }
          Button(
            onClick = onEditInNexo,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(6.dp),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.edit.uppercase(), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

internal fun decodeThumbnailSafe(path: String, maxSide: Int): Bitmap? {
  val file = File(path)
  if (!file.exists() || !file.canRead()) return null
  return runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
      sample *= 2
    }
    BitmapFactory.decodeFile(
      file.absolutePath,
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      },
    )
  }.getOrNull()
}
