package com.example.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class AudioPlaybackState(
  val currentFile: File? = null,
  val playlist: List<File> = emptyList(),
  val currentIndex: Int = 0,
  val isPlaying: Boolean = false,
  val isPrepared: Boolean = false,
  val positionMs: Int = 0,
  val durationMs: Int = 0,
  val errorMessage: String? = null,
  val isVaultMode: Boolean = false,
) {
  val hasActiveTrack: Boolean get() = currentFile != null
}

class AudioPlayerManager(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private var mediaPlayer: MediaPlayer? = null
  private var tickerJob: Job? = null

  private val _state = MutableStateFlow(AudioPlaybackState())
  val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

  fun playTrack(
    file: File,
    siblingAudioFiles: List<File> = emptyList(),
    isVaultMode: Boolean = false,
  ) {
    val queue = if (isVaultMode) {
      listOf(file)
    } else {
      siblingAudioFiles.ifEmpty {
        file.parentFile?.listFiles()
          ?.filter { it.isFile && FormatUtils.isAudio(it.name) }
          ?.sortedBy { it.name.lowercase(Locale.ROOT) }
          ?.takeIf { it.isNotEmpty() }
          ?: listOf(file)
      }
    }
    val idx = queue.indexOfFirst { it.absolutePath == file.absolutePath }.let { if (it >= 0) it else 0 }
    startTrackAt(queue, idx, isVaultMode)
  }

  private fun startTrackAt(queue: List<File>, index: Int, isVaultMode: Boolean) {
    val target = queue.getOrNull(index) ?: return
    releasePlayerOnly()
    _state.value = AudioPlaybackState(
      currentFile = target,
      playlist = queue,
      currentIndex = index,
      isPlaying = false,
      isPrepared = false,
      positionMs = 0,
      durationMs = 0,
      errorMessage = null,
      isVaultMode = isVaultMode,
    )

    if (!target.exists() || !target.canRead()) {
      _state.value = _state.value.copy(errorMessage = "FILE_UNREADABLE")
      return
    }

    runCatching {
      val mp = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(target.absolutePath)
        setOnPreparedListener { player ->
          val total = player.duration.coerceAtLeast(0)
          _state.value = _state.value.copy(
            isPrepared = true,
            durationMs = total,
            isPlaying = true,
            errorMessage = null,
          )
          runCatching { player.start() }
          startTicker()
        }
        setOnCompletionListener {
          val cur = _state.value
          if (cur.playlist.size > 1 && cur.currentIndex < cur.playlist.lastIndex) {
            playNext()
          } else {
            stopTicker()
            _state.value = cur.copy(isPlaying = false, positionMs = cur.durationMs)
          }
        }
        setOnErrorListener { _, _, _ ->
          stopTicker()
          _state.value = _state.value.copy(
            isPlaying = false,
            isPrepared = false,
            errorMessage = "PLAYBACK_ERROR",
          )
          true
        }
        prepareAsync()
      }
      mediaPlayer = mp
    }.onFailure {
      _state.value = _state.value.copy(
        isPlaying = false,
        isPrepared = false,
        errorMessage = it.localizedMessage ?: "PLAYBACK_ERROR",
      )
    }
  }

  fun togglePlayPause() {
    val mp = mediaPlayer ?: return
    val cur = _state.value
    if (!cur.isPrepared) return
    runCatching {
      if (mp.isPlaying) {
        mp.pause()
        stopTicker()
        _state.value = cur.copy(isPlaying = false)
      } else {
        mp.start()
        _state.value = cur.copy(isPlaying = true)
        startTicker()
      }
    }
  }

  fun seekTo(ms: Int) {
    val mp = mediaPlayer ?: return
    val cur = _state.value
    if (!cur.isPrepared) return
    val clamped = ms.coerceIn(0, cur.durationMs.coerceAtLeast(0))
    runCatching {
      mp.seekTo(clamped)
      _state.value = cur.copy(positionMs = clamped)
    }
  }

  fun playNext() {
    val cur = _state.value
    if (cur.playlist.isEmpty()) return
    val nextIndex = if (cur.currentIndex + 1 < cur.playlist.size) cur.currentIndex + 1 else 0
    startTrackAt(cur.playlist, nextIndex, cur.isVaultMode)
  }

  fun playPrevious() {
    val cur = _state.value
    if (cur.playlist.isEmpty()) return
    if (cur.positionMs > 3000 && cur.isPrepared) {
      seekTo(0)
      return
    }
    val prevIndex = if (cur.currentIndex - 1 >= 0) cur.currentIndex - 1 else cur.playlist.lastIndex
    startTrackAt(cur.playlist, prevIndex, cur.isVaultMode)
  }

  fun stop() {
    releasePlayerOnly()
    _state.value = AudioPlaybackState()
  }

  fun release() {
    stop()
  }

  private fun releasePlayerOnly() {
    stopTicker()
    mediaPlayer?.let { mp ->
      runCatching { if (mp.isPlaying) mp.stop() }
      runCatching { mp.reset() }
      runCatching { mp.release() }
    }
    mediaPlayer = null
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = scope.launch(Dispatchers.Main) {
      while (true) {
        val mp = mediaPlayer
        val cur = _state.value
        if (mp != null && cur.isPrepared && cur.isPlaying) {
          val pos = runCatching { mp.currentPosition }.getOrDefault(cur.positionMs)
          _state.value = cur.copy(positionMs = pos.coerceIn(0, cur.durationMs.coerceAtLeast(1)))
        }
        delay(300L)
      }
    }
  }

  private fun stopTicker() {
    tickerJob?.cancel()
    tickerJob = null
  }
}

@Composable
fun AudioMiniPlayerBar(
  state: AudioPlaybackState,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onOpenFullPlayer: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val track = state.currentFile
  AnimatedVisibility(
    visible = track != null,
    enter = slideInVertically { it },
    exit = slideOutVertically { it },
    modifier = modifier,
  ) {
    if (track != null) {
      val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
      } else 0f

      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .clickable { onOpenFullPlayer() }
          .testTag("audio_mini_player_bar"),
        shape = RoundedCornerShape(8.dp),
        color = NexoTheme.semantic.modalSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
          1.dp,
          if (state.isVaultMode) NexoTheme.semantic.evaOrange else MaterialTheme.colorScheme.secondary,
        ),
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconContainerBadge(
              icon = NexoIcons.MusicNote,
              contentDescription = null,
              size = 38.dp,
              highlighted = state.isPlaying,
              iconTint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = track.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = if (state.errorMessage != null) {
                  strings.audioErrorLoad
                } else {
                  "${formatAudioTime(state.positionMs)} / ${formatAudioTime(state.durationMs)}"
                },
                style = MonoTnumStyle,
                color = if (state.errorMessage != null) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.primary,
                maxLines = 1,
              )
            }
            if (state.playlist.size > 1) {
              IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(NexoIcons.SkipPrevious, contentDescription = strings.audioPrevCd, tint = MaterialTheme.colorScheme.onSurface)
              }
            }
            IconButton(
              onClick = onTogglePlayPause,
              enabled = state.isPrepared,
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .testTag("audio_mini_play_pause"),
            ) {
              Icon(
                imageVector = if (state.isPlaying) NexoIcons.Pause else NexoIcons.PlayArrow,
                contentDescription = if (state.isPlaying) strings.audioPauseCd else strings.audioPlayCd,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            if (state.playlist.size > 1) {
              IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(NexoIcons.SkipNext, contentDescription = strings.audioNextCd, tint = MaterialTheme.colorScheme.onSurface)
              }
            }
            IconButton(
              onClick = onClose,
              modifier = Modifier
                .size(40.dp)
                .testTag("audio_mini_close"),
            ) {
              Icon(NexoIcons.Close, contentDescription = strings.audioCloseCd, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(18.dp))
            }
          }
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
              .fillMaxWidth()
              .height(2.5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
          )
        }
      }
    }
  }
}

@Composable
fun AudioPlayerFullDialog(
  state: AudioPlaybackState,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Int) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onOpenExternal: (File) -> Unit,
  onClosePlayer: () -> Unit,
  onDismissModal: () -> Unit,
) {
  val strings = LocalStrings.current
  val track = state.currentFile ?: return

  Dialog(
    onDismissRequest = onDismissModal,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .testTag("audio_full_player_dialog"),
      shape = RoundedCornerShape(10.dp),
      color = NexoTheme.semantic.modalSurface,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = strings.audioPlayerTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            if (state.isVaultMode) {
              Text(
                text = strings.vaultInternalBadge,
                style = MaterialTheme.typography.labelSmall,
                color = NexoTheme.semantic.evaOrange,
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            // Strictly prohibit system app chooser / external opening in Vault mode
            if (!state.isVaultMode) {
              IconButton(onClick = { onOpenExternal(track) }) {
                Icon(NexoIcons.OpenInNew, contentDescription = strings.openExternal, tint = MaterialTheme.colorScheme.primary)
              }
            }
            IconButton(onClick = onDismissModal) {
              Icon(NexoIcons.Close, contentDescription = strings.close, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        IconContainerBadge(
          icon = NexoIcons.Headphones,
          contentDescription = null,
          size = 86.dp,
          highlighted = state.isPlaying,
          iconTint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = track.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = if (state.playlist.size > 1) {
            "${strings.audioTrackPosition(state.currentIndex + 1, state.playlist.size)} · ${FormatUtils.formatBytes(track.length())}"
          } else {
            FormatUtils.formatBytes(track.length())
          },
          style = MonoTnumStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.errorMessage != null) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = strings.audioErrorLoad,
            style = MaterialTheme.typography.bodySmall,
            color = NexoTheme.semantic.evaRed,
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Slider(
          value = if (state.durationMs > 0) state.positionMs.toFloat() else 0f,
          onValueChange = { onSeekTo(it.toInt()) },
          valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
          enabled = state.isPrepared,
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
          ),
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = formatAudioTime(state.positionMs),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = formatAudioTime(state.durationMs),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onPrevious,
            enabled = state.playlist.isNotEmpty(),
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp)),
          ) {
            Icon(NexoIcons.SkipPrevious, contentDescription = strings.audioPrevCd, tint = MaterialTheme.colorScheme.onSurface)
          }

          IconButton(
            onClick = onTogglePlayPause,
            enabled = state.isPrepared,
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primary)
              .testTag("audio_full_play_pause"),
          ) {
            Icon(
              imageVector = if (state.isPlaying) NexoIcons.Pause else NexoIcons.PlayArrow,
              contentDescription = if (state.isPlaying) strings.audioPauseCd else strings.audioPlayCd,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(34.dp),
            )
          }

          IconButton(
            onClick = onNext,
            enabled = state.playlist.isNotEmpty(),
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp)),
          ) {
            Icon(NexoIcons.SkipNext, contentDescription = strings.audioNextCd, tint = MaterialTheme.colorScheme.onSurface)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(
            onClick = onClosePlayer,
            colors = ButtonDefaults.buttonColors(
              containerColor = NexoTheme.semantic.evaRed,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(strings.audioCloseCd.uppercase(), fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

private fun formatAudioTime(ms: Int): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val mins = totalSec / 60
  val secs = totalSec % 60
  return String.format(Locale.US, "%02d:%02d", mins, secs)
}
