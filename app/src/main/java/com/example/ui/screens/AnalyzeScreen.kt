package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.db.RecycleBinEntity
import com.example.data.model.CategoryBreakdown
import com.example.data.model.DuplicateGroup
import com.example.data.model.DuplicateScanProgress
import com.example.data.model.LocalFileItem
import com.example.data.model.SimilarPhotoGroup
import com.example.data.model.SimilarPhotoItem
import com.example.data.model.SimilarPhotosScanState
import com.example.data.model.StorageAnalysisResult
import com.example.ui.components.BichromaticCard
import com.example.ui.components.CategoryBarChart
import com.example.ui.components.ChamferedTechCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.CleanupAnimationState
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AnalyzeScreen(
  analysis: StorageAnalysisResult,
  selectedDuplicates: Set<String>,
  recycleBinItems: List<RecycleBinEntity>,
  cleanupState: CleanupAnimationState = CleanupAnimationState(),
  duplicateProgress: DuplicateScanProgress = DuplicateScanProgress(),
  similarScanState: SimilarPhotosScanState = SimilarPhotosScanState(),
  selectedSimilarPhotos: Set<String> = emptySet(),
  onRescan: () -> Unit,
  onRescanDuplicates: () -> Unit = onRescan,
  onToggleDuplicate: (String) -> Unit,
  onSmartSelectDuplicates: () -> Unit,
  onDeleteSelectedDuplicates: () -> Unit,
  onScanSimilarPhotos: () -> Unit = onRescan,
  onToggleSimilarPhoto: (String) -> Unit = {},
  onSmartSelectSimilarPhotos: () -> Unit = {},
  onDeleteSelectedSimilarPhotos: () -> Unit = {},
  onEditPhotoPath: (String) -> Unit = {},
  onCleanJunk: () -> Unit,
  onTrashLargeFile: (String) -> Unit,
  onOpenFolderPath: (String) -> Unit,
  onRestoreRecycleItem: (RecycleBinEntity) -> Unit,
  onDeleteRecycleForever: (RecycleBinEntity) -> Unit,
  onEmptyRecycleBin: () -> Unit,
  onDismissCleanupConfirmation: () -> Unit = {},
  onUndoLastCleanup: () -> Unit = {},
) {
  var expandedSection by remember { mutableStateOf(0) }
  var chartAnimKey by remember { mutableIntStateOf(0) }
  var revealCards by remember { mutableStateOf(true) }
  var previewSimilarPhoto by remember { mutableStateOf<SimilarPhotoItem?>(null) }
  val strings = LocalStrings.current

  LaunchedEffect(analysis.isScanning) {
    if (!analysis.isScanning) {
      chartAnimKey++
      revealCards = false
      delay(40)
      revealCards = true
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // NERV HUD Telemetry Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = strings.storageAnalysisTitle.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = if (analysis.isScanning) {
              analysis.scanProgressMessage
            } else if (strings.isEnglish) {
              "3-PHASE MD5 FUNNEL · DHASH SIMILARITY TELEMETRY"
            } else {
              "EMBUDO MD5 DE 3 FASES · TELEMETRÍA DHASH"
            },
            style = MonoTnumStyle,
            color = if (analysis.isScanning) NexoTheme.semantic.evaOrange else MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onRescan,
          enabled = !analysis.isScanning && !cleanupState.isRunning,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .testTag("analyze_rescan_button"),
        ) {
          if (analysis.isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NexoTheme.semantic.evaOrange, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Rounded.Refresh, contentDescription = strings.rescanCd, tint = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    // Active Cleanup Progress Ring + Freed Byte Counter + Path Ticker
    if (cleanupState.isRunning) {
      item {
        BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
                strokeWidth = 4.dp,
                modifier = Modifier.fillMaxSize(),
              )
              Icon(NexoIcons.CleaningServices, contentDescription = null, tint = NexoTheme.semantic.evaOrange, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.cleaningInProgress.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NexoTheme.semantic.evaOrange,
              )
              Text(
                text = "${strings.spaceFreedLabel}: ${FormatUtils.formatBytes(cleanupState.freedBytesCounter)}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = cleanupState.scanningPath.ifBlank { strings.loading },
                style = MonoTnumStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }

    // Circular Radar Scanning Animation
    if (analysis.isScanning) {
      item {
        RadarScanCard(
          stageIndex = analysis.scanStageIndex,
          progressMessage = analysis.scanProgressMessage,
        )
      }
    }

    // Proportional Vertical Grid Capsule Bar Chart
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (analysis.deviceTotalBytes > 0L) {
          Text(
            text = if (strings.isEnglish) {
              "CAPACITY SHARE (${FormatUtils.formatBytes(analysis.deviceTotalBytes)})"
            } else {
              "PROPORCIÓN CAPACIDAD TOTAL (${FormatUtils.formatBytes(analysis.deviceTotalBytes)})"
            },
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        CategoryBarChart(
          breakdown = analysis.breakdown,
          deviceTotalBytes = analysis.deviceTotalBytes,
          animationKey = chartAnimKey,
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }
    }

    // Bento Summary Row: Duplicate Files + Similar Photos
    item {
      AnimatedVisibility(
        visible = revealCards,
        enter = slideInHorizontally(tween(360)) { it / 2 } + fadeIn(tween(360)),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          ChamferedTechCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("bento_duplicates_card"),
            chamfer = 10.dp,
            accentGlow = analysis.duplicateGroups.isNotEmpty(),
            onClick = { expandedSection = 0 },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = strings.duplicateFilesTitle.uppercase(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                  text = strings.duplicateSummary(analysis.duplicateGroups.size, FormatUtils.formatBytes(analysis.duplicateWasteBytes)),
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = strings.dupFunnelSubtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                TwinDuplicatesVector(modifier = Modifier.size(width = 76.dp, height = 52.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                  onClick = { expandedSection = 0 },
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                  shape = RoundedCornerShape(6.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = NexoTheme.semantic.tacticalSurface,
                    contentColor = MaterialTheme.colorScheme.primary,
                  ),
                  contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                ) {
                  Text(
                    text = strings.review.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }

          // Similar Photos Bento Card
          ChamferedTechCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("bento_similar_photos_card"),
            chamfer = 10.dp,
            accentGlow = analysis.similarPhotoGroups.isNotEmpty(),
            onClick = { expandedSection = 1 },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconContainerBadge(
                icon = NexoIcons.BurstMode,
                contentDescription = null,
                highlighted = true,
                size = 46.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = strings.similarPhotosTitle.uppercase(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = strings.similarPhotosSubtitle(
                    analysis.similarPhotoGroups.size,
                    FormatUtils.formatBytes(analysis.similarPhotosWasteBytes),
                  ),
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
              OutlinedButton(
                onClick = { expandedSection = 1 },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
              ) {
                Text(
                  text = strings.review.uppercase(),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }
        }
      }
    }

    // Secondary Bento Row: Large Files + Junk/Cache + Quick Clean Broom
    item {
      AnimatedVisibility(
        visible = revealCards,
        enter = slideInHorizontally(tween(420, delayMillis = 60)) { it / 2 } + fadeIn(tween(420, delayMillis = 60)),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            ChamferedTechCard(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
              chamfer = 8.dp,
              onClick = { expandedSection = 2 },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = strings.largeFilesTitle.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "${analysis.largeFiles.size} · ${FormatUtils.formatBytes(analysis.largeFilesTotalBytes)}",
                    style = MonoTnumStyle,
                    color = NexoTheme.semantic.evaOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Icon(
                  imageVector = NexoIcons.InventoryBox,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(28.dp),
                )
              }
            }

            ChamferedTechCard(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
              chamfer = 8.dp,
              onClick = { expandedSection = 3 },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = strings.junkCacheTitle.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = strings.junkSummary("${analysis.junkFiles.size}", analysis.emptyFolders.size),
                    style = MonoTnumStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Icon(
                  imageVector = NexoIcons.DeleteOutline,
                  contentDescription = null,
                  tint = NexoTheme.semantic.evaRed,
                  modifier = Modifier.size(28.dp),
                )
              }
            }
          }

          ChamferedTechCard(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            chamfer = 10.dp,
            accentGlow = true,
            onClick = { expandedSection = 3 },
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Spacer(modifier = Modifier.height(4.dp))
              BroomCleanVector(modifier = Modifier.size(82.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = onCleanJunk,
                enabled = !cleanupState.isRunning,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(44.dp)
                  .testTag("analyze_clean_junk_button"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
              ) {
                Text(
                  text = strings.cleanBtn.uppercase(),
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelLarge,
                )
              }
            }
          }
        }
      }
    }

    // 1. Duplicate Files (3-Phase MD5 Funnel)
    item {
      AnalyzerAccordionSection(
        title = "01 // ${strings.duplicateFilesTitle.uppercase()}",
        subtitle = strings.duplicateSummary(analysis.duplicateGroups.size, FormatUtils.formatBytes(analysis.duplicateWasteBytes)),
        icon = NexoIcons.FileCopy,
        expanded = expandedSection == 0,
        highlighted = analysis.duplicateGroups.isNotEmpty(),
        onToggle = { expandedSection = if (expandedSection == 0) -1 else 0 },
      ) {
        if (duplicateProgress.isRunning) {
          val phaseText = when (duplicateProgress.phase) {
            1 -> strings.dupPhase1Label(duplicateProgress.processed, duplicateProgress.total)
            2 -> strings.dupPhase2Label(duplicateProgress.processed, duplicateProgress.total)
            else -> strings.dupPhase3Label(duplicateProgress.processed, duplicateProgress.total)
          }
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = phaseText.uppercase(),
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
              progress = { duplicateProgress.fraction },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp)),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.outline,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        } else if (analysis.duplicateSummaryStats.totalScannedFiles > 0) {
          val stats = analysis.duplicateSummaryStats
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = strings.dupFunnelStatsBadge(
                stats.phase1SizeCandidates,
                stats.phase2StructuralCandidates,
                stats.phase3ConfirmedDuplicates,
              ),
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onRescanDuplicates,
            enabled = !duplicateProgress.isRunning && !analysis.isScanning,
            modifier = Modifier.testTag("duplicate_funnel_scan_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.rescanDuplicatesBtn.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }

        if (analysis.duplicateGroups.isEmpty()) {
          Text(
            strings.noDuplicatesFound,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onSmartSelectDuplicates,
              modifier = Modifier
                .weight(1f)
                .testTag("duplicate_smart_select_button"),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
              shape = RoundedCornerShape(6.dp),
            ) {
              Icon(NexoIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(strings.smartSelectTitle.uppercase(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Button(
              onClick = onDeleteSelectedDuplicates,
              enabled = selectedDuplicates.isNotEmpty() && !cleanupState.isRunning,
              modifier = Modifier
                .weight(1f)
                .testTag("duplicate_delete_selected_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = NexoTheme.semantic.evaRed,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(strings.cleanSelected(selectedDuplicates.size).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          analysis.duplicateGroups.take(30).forEach { group ->
            DuplicateGroupCard(
              group = group,
              selectedPaths = selectedDuplicates,
              removingPaths = cleanupState.removingPaths,
              onToggle = onToggleDuplicate,
            )
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }
    }

    // 2. Similar Photos (dHash + EXIF)
    item {
      AnalyzerAccordionSection(
        title = "02 // ${strings.similarPhotosTitle.uppercase()}",
        subtitle = strings.similarPhotosSubtitle(
          analysis.similarPhotoGroups.size,
          FormatUtils.formatBytes(analysis.similarPhotosWasteBytes),
        ),
        icon = NexoIcons.BurstMode,
        expanded = expandedSection == 1,
        highlighted = analysis.similarPhotoGroups.isNotEmpty(),
        onToggle = { expandedSection = if (expandedSection == 1) -1 else 1 },
      ) {
        if (similarScanState.isScanning) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "${strings.similarPhotosTitle.uppercase()} · ${similarScanState.processed}/${similarScanState.total}",
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.secondary,
            )
            LinearProgressIndicator(
              progress = { similarScanState.fraction },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp)),
              color = MaterialTheme.colorScheme.secondary,
              trackColor = MaterialTheme.colorScheme.outline,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.tapToPreviewPhotoHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          TextButton(
            onClick = onScanSimilarPhotos,
            enabled = !similarScanState.isScanning && !analysis.isScanning,
            modifier = Modifier.testTag("scan_similar_photos_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.scanSimilarPhotosBtn.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }

        if (analysis.similarPhotoGroups.isEmpty()) {
          Text(
            text = strings.noSimilarPhotosFound,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onSmartSelectSimilarPhotos,
              modifier = Modifier
                .weight(1f)
                .testTag("similar_photos_smart_select"),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
              shape = RoundedCornerShape(6.dp),
            ) {
              Icon(NexoIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(5.dp))
              Text(strings.smartSelectPhotosBtn.uppercase(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Button(
              onClick = onDeleteSelectedSimilarPhotos,
              enabled = selectedSimilarPhotos.isNotEmpty() && !cleanupState.isRunning,
              modifier = Modifier
                .weight(1f)
                .testTag("similar_photos_delete_selected"),
              colors = ButtonDefaults.buttonColors(
                containerColor = NexoTheme.semantic.evaRed,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(strings.cleanSelectedPhotosBtn(selectedSimilarPhotos.size).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          analysis.similarPhotoGroups.take(25).forEach { group ->
            SimilarPhotoGroupCard(
              group = group,
              selectedPaths = selectedSimilarPhotos,
              removingPaths = cleanupState.removingPaths,
              onToggleSelect = onToggleSimilarPhoto,
              onPreviewPhoto = { previewSimilarPhoto = it },
            )
            Spacer(modifier = Modifier.height(10.dp))
          }
        }
      }
    }

    // 3. Large Files (> 50 MB)
    item {
      AnalyzerAccordionSection(
        title = "03 // ${strings.largeFilesTitle.uppercase()} (> 50 MB)",
        subtitle = strings.filesCount(analysis.largeFiles.size),
        icon = NexoIcons.Storage,
        expanded = expandedSection == 2,
        onToggle = { expandedSection = if (expandedSection == 2) -1 else 2 },
      ) {
        if (analysis.largeFiles.isEmpty()) {
          Text(strings.noLargeFiles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          analysis.largeFiles.take(30).forEach { file ->
            AnimatedVisibility(
              visible = file.path !in cleanupState.removingPaths,
              exit = slideOutHorizontally(tween(320)) { -it } + shrinkVertically(tween(320)) + fadeOut(tween(320)),
            ) {
              Column {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${FormatUtils.formatBytes(file.sizeBytes)} · ${file.path}", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  IconButton(onClick = { onTrashLargeFile(file.path) }) {
                    Icon(NexoIcons.DeleteOutline, contentDescription = strings.moveToTrashCd, tint = NexoTheme.semantic.evaOrange)
                  }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
              }
            }
          }
        }
      }
    }

    // 4. Junk & Cache
    item {
      AnalyzerAccordionSection(
        title = "04 // ${strings.junkCacheTitle.uppercase()}",
        subtitle = strings.junkSummary(FormatUtils.formatBytes(analysis.junkTotalBytes), analysis.emptyFolders.size),
        icon = NexoIcons.CleaningServices,
        expanded = expandedSection == 3,
        onToggle = { expandedSection = if (expandedSection == 3) -1 else 3 },
      ) {
        if (analysis.junkFiles.isEmpty() && analysis.emptyFolders.isEmpty()) {
          Text(strings.emptyJunkBanner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          Text(
            text = "${strings.spaceFreedLabel}: ${FormatUtils.formatBytes(analysis.junkTotalBytes)}",
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          analysis.junkFiles.take(20).forEach { junk ->
            AnimatedVisibility(
              visible = junk.path !in cleanupState.removingPaths,
              exit = slideOutHorizontally(tween(320)) { -it } + shrinkVertically(tween(320)) + fadeOut(tween(320)),
            ) {
              Text(
                text = "> ${junk.name} (${FormatUtils.formatBytes(junk.sizeBytes)})",
                style = MonoTnumStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = onCleanJunk,
            enabled = !cleanupState.isRunning,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(strings.cleanBtn.uppercase(), fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 5. App Folders & Recent
    item {
      AnalyzerAccordionSection(
        title = "05 // ${strings.appFoldersTitle(analysis.appFolders.size).uppercase()}",
        subtitle = strings.recent7DaysTitle(analysis.recentlyCreatedFiles.size),
        icon = NexoIcons.FolderSpecial,
        expanded = expandedSection == 4,
        onToggle = { expandedSection = if (expandedSection == 4) -1 else 4 },
      ) {
        Text(strings.appFoldersTitle(analysis.appFolders.size).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        analysis.appFolders.take(10).forEach { folder ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenFolderPath(folder.path) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(folder.folderName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(strings.filesCount(folder.itemCount), style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(FormatUtils.formatBytes(folder.sizeBytes), style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary)
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(NexoIcons.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.recentNewFilesTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        analysis.recentlyCreatedFiles.take(8).forEach { file ->
          Text(
            text = "> ${file.name} (${FormatUtils.formatBytes(file.sizeBytes)})",
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp),
          )
        }
      }
    }

    // 6. Recycle Bin
    item {
      AnalyzerAccordionSection(
        title = "06 // ${strings.recycleBinTitle.uppercase()}",
        subtitle = strings.recoverableCount(recycleBinItems.size),
        icon = NexoIcons.RestoreFromTrash,
        expanded = expandedSection == 5,
        highlighted = recycleBinItems.isNotEmpty(),
        onToggle = { expandedSection = if (expandedSection == 5) -1 else 5 },
      ) {
        if (recycleBinItems.isEmpty()) {
          Text(strings.recycleBinEmpty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            Button(
              onClick = onEmptyRecycleBin,
              colors = ButtonDefaults.buttonColors(
                containerColor = NexoTheme.semantic.evaRed,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
              Text(strings.emptyBinBtn.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          recycleBinItems.forEach { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatDate(item.deletedAt)}", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Button(
                onClick = { onRestoreRecycleItem(item) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              ) {
                Text(strings.restore.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
              IconButton(onClick = { onDeleteRecycleForever(item) }) {
                Icon(NexoIcons.DeleteForever, contentDescription = strings.deleteForever, tint = NexoTheme.semantic.evaRed)
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
          }
        }
      }
    }
  }

  previewSimilarPhoto?.let { photo ->
    SimilarPhotoPreviewDialog(
      photo = photo,
      isSelected = photo.path in selectedSimilarPhotos,
      onToggleSelect = { onToggleSimilarPhoto(photo.path) },
      onEditInNexo = {
        val targetPath = photo.path
        previewSimilarPhoto = null
        onEditPhotoPath(targetPath)
      },
      onDismiss = { previewSimilarPhoto = null },
    )
  }

  if (cleanupState.showConfirmation) {
    Dialog(onDismissRequest = onDismissCleanupConfirmation) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = NexoTheme.semantic.modalSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("cleanup_confirmation_dialog"),
      ) {
        Column(
          modifier = Modifier.padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AnimatedVisibility(
            visible = true,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(NexoIcons.CheckCircle, contentDescription = strings.cleanupCompletedTitle, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
          }
          Text(
            text = cleanupState.lastActionLabel.ifBlank { strings.cleanupCompletedTitle }.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = strings.spaceRecovered(FormatUtils.formatBytes(cleanupState.lastFreedBytes)),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (cleanupState.undoRecycleIds.isNotEmpty()) {
              OutlinedButton(
                onClick = onUndoLastCleanup,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, NexoTheme.semantic.evaOrange),
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(strings.undoTrashBtn.uppercase(), color = NexoTheme.semantic.evaOrange, fontWeight = FontWeight.Bold)
              }
            }
            Button(
              onClick = onDismissCleanupConfirmation,
              modifier = Modifier.weight(1f),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(strings.doneBtn.uppercase(), fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DuplicateGroupCard(
  group: DuplicateGroup,
  selectedPaths: Set<String>,
  removingPaths: Set<String>,
  onToggle: (String) -> Unit,
) {
  val strings = LocalStrings.current
  var groupExpanded by remember { mutableStateOf(true) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
      .padding(10.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { groupExpanded = !groupExpanded },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.duplicateGroupTitle(group.files.size, FormatUtils.formatBytes(group.sizeBytes)).uppercase(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "MD5: ${group.md5}",
          style = MonoTnumStyle,
          color = MaterialTheme.colorScheme.secondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = if (groupExpanded) NexoIcons.ExpandLess else NexoIcons.ExpandMore,
        contentDescription = strings.expandCollapseCd,
        tint = MaterialTheme.colorScheme.primary,
      )
    }

    AnimatedVisibility(visible = groupExpanded) {
      Column(modifier = Modifier.padding(top = 6.dp)) {
        group.files.forEachIndexed { index, file ->
          AnimatedVisibility(
            visible = file.path !in removingPaths,
            exit = slideOutHorizontally(tween(320)) { -it } + shrinkVertically(tween(320)) + fadeOut(tween(320)),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(file.path) }
                .padding(vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = file.path in selectedPaths,
                onCheckedChange = { onToggle(file.path) },
                colors = CheckboxDefaults.colors(
                  checkedColor = MaterialTheme.colorScheme.primary,
                  checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                  uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              )
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                  )
                  if (index == 0) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                      Text(
                        text = strings.keepOriginalBadge.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
                Text(
                  text = file.path,
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SimilarPhotoGroupCard(
  group: SimilarPhotoGroup,
  selectedPaths: Set<String>,
  removingPaths: Set<String>,
  onToggleSelect: (String) -> Unit,
  onPreviewPhoto: (SimilarPhotoItem) -> Unit,
) {
  val strings = LocalStrings.current
  val visibleItems = group.items.filter { it.path !in removingPaths }
  if (visibleItems.isEmpty()) return

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            text = strings.similarityBadge(group.maxSimilarityPercent).uppercase(),
            style = MonoTnumStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        if (group.isBurstGroup) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(NexoTheme.semantic.evaOrange.copy(alpha = 0.18f))
              .border(1.dp, NexoTheme.semantic.evaOrange, RoundedCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 2.dp),
          ) {
            Text(
              text = strings.burstGroupBadge.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = NexoTheme.semantic.evaOrange,
            )
          }
        }
      }
      Text(
        text = FormatUtils.formatBytes(group.recoverableBytes),
        style = MonoTnumStyle,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = PaddingValues(vertical = 2.dp),
    ) {
      items(visibleItems, key = { it.path }) { photo ->
        SimilarPhotoThumbnailTile(
          photo = photo,
          isSelected = photo.path in selectedPaths,
          onToggleSelect = { onToggleSelect(photo.path) },
          onPreview = { onPreviewPhoto(photo) },
        )
      }
    }
  }
}

@Composable
private fun SimilarPhotoThumbnailTile(
  photo: SimilarPhotoItem,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onPreview: () -> Unit,
) {
  val strings = LocalStrings.current
  var thumb by remember(photo.path) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(photo.path) {
    thumb = withContext(Dispatchers.IO) {
      decodeThumbnailSafe(photo.path, 300)
    }
  }

  Column(
    modifier = Modifier
      .width(136.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(
        width = if (photo.isBestShot || isSelected) 1.5.dp else 1.dp,
        color = when {
          photo.isBestShot -> MaterialTheme.colorScheme.primary
          isSelected -> NexoTheme.semantic.evaRed
          else -> MaterialTheme.colorScheme.outline
        },
        shape = RoundedCornerShape(8.dp),
      )
      .clickable(onClick = onPreview),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(118.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      val bmp = thumb
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = photo.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(NexoIcons.Image, contentDescription = photo.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
      }

      if (photo.isBestShot) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
          Text(
            text = strings.bestShotBadge.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .size(30.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
          .clickable(onClick = onToggleSelect),
        contentAlignment = Alignment.Center,
      ) {
        Checkbox(
          checked = isSelected,
          onCheckedChange = { onToggleSelect() },
          colors = CheckboxDefaults.colors(
            checkedColor = NexoTheme.semantic.evaRed,
            checkmarkColor = MaterialTheme.colorScheme.onError,
          ),
        )
      }
    }

    Column(modifier = Modifier.padding(8.dp)) {
      Text(
        text = photo.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${photo.megapixelsLabel} · ${FormatUtils.formatBytes(photo.sizeBytes)}",
        style = MonoTnumStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun SimilarPhotoPreviewDialog(
  photo: SimilarPhotoItem,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onEditInNexo: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var bmp by remember(photo.path) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(photo.path) {
    bmp = withContext(Dispatchers.IO) {
      decodeThumbnailSafe(photo.path, 1280)
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = NexoTheme.semantic.modalSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(photo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              text = "${photo.width}×${photo.height} · ${FormatUtils.formatBytes(photo.sizeBytes)}",
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(NexoIcons.Close, contentDescription = strings.close)
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
          val previewBmp = bmp
          if (previewBmp != null) {
            Image(
              bitmap = previewBmp.asImageBitmap(),
              contentDescription = photo.name,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onToggleSelect,
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, if (isSelected) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = (if (isSelected) strings.keepOriginalBadge else strings.delete).uppercase(),
              color = if (isSelected) MaterialTheme.colorScheme.primary else NexoTheme.semantic.evaRed,
              fontWeight = FontWeight.Bold,
            )
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

@Composable
private fun RadarScanCard(
  stageIndex: Int,
  progressMessage: String,
) {
  val infinite = rememberInfiniteTransition(label = "radarSweep")
  val sweepAngle by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
    label = "radarRotation",
  )
  val strings = LocalStrings.current
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val outline = MaterialTheme.colorScheme.outline
  val stages = listOf(
    strings.chartPhotos,
    strings.chartVideos,
    strings.chartApps,
    if (strings.isEnglish) "MD5 + dHash" else "MD5 + Fotos dHash",
    strings.junkCacheTitle.replace("\n", " "),
  )

  BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Canvas(modifier = Modifier.size(84.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(color = outline.copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(1.4.dp.toPx()))
        drawCircle(color = secondary.copy(alpha = 0.5f), radius = radius * 0.66f, center = center, style = Stroke(1.2.dp.toPx()))
        drawCircle(color = primary.copy(alpha = 0.6f), radius = radius * 0.33f, center = center, style = Stroke(1.2.dp.toPx()))
        rotate(sweepAngle, pivot = center) {
          drawArc(
            brush = Brush.sweepGradient(listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0.72f))),
            startAngle = 0f,
            sweepAngle = 95f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
          )
        }
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(progressMessage.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        stages.forEachIndexed { idx, label ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            when {
              stageIndex > idx -> Icon(NexoIcons.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(14.dp))
              stageIndex == idx -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp, color = NexoTheme.semantic.evaOrange)
              else -> Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(outline.copy(alpha = 0.4f)))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = label,
              style = MonoTnumStyle,
              color = if (stageIndex >= idx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AnalyzerAccordionSection(
  title: String,
  subtitle: String,
  icon: ImageVector,
  expanded: Boolean,
  highlighted: Boolean = false,
  onToggle: () -> Unit,
  content: @Composable () -> Unit,
) {
  BichromaticCard(
    modifier = Modifier.fillMaxWidth(),
    accentBorder = expanded || highlighted,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconContainerBadge(icon = icon, contentDescription = null, highlighted = expanded || highlighted)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
          Text(subtitle, style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
          imageVector = if (expanded) NexoIcons.ExpandLess else NexoIcons.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          Spacer(modifier = Modifier.height(10.dp))
          content()
        }
      }
    }
  }
}

@Composable
private fun TwinDuplicatesVector(modifier: Modifier = Modifier) {
  val primary = MaterialTheme.colorScheme.primary
  val surface = MaterialTheme.colorScheme.surface
  val secondary = MaterialTheme.colorScheme.secondary
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cardW = w * 0.44f
    val cardH = h * 0.78f

    rotate(degrees = -12f, pivot = Offset(w * 0.34f, h * 0.52f)) {
      drawRect(
        color = secondary.copy(alpha = 0.72f),
        topLeft = Offset(w * 0.12f, h * 0.10f),
        size = Size(cardW, cardH),
      )
      drawLine(color = surface, start = Offset(w * 0.20f, h * 0.34f), end = Offset(w * 0.46f, h * 0.34f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Square)
      drawLine(color = surface, start = Offset(w * 0.20f, h * 0.50f), end = Offset(w * 0.40f, h * 0.50f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Square)
    }

    rotate(degrees = 12f, pivot = Offset(w * 0.66f, h * 0.50f)) {
      drawRect(
        color = primary,
        topLeft = Offset(w * 0.44f, h * 0.12f),
        size = Size(cardW, cardH),
      )
      drawLine(color = surface, start = Offset(w * 0.52f, h * 0.36f), end = Offset(w * 0.78f, h * 0.36f), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Square)
      drawLine(color = surface, start = Offset(w * 0.52f, h * 0.52f), end = Offset(w * 0.72f, h * 0.52f), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Square)
    }
  }
}

@Composable
private fun BroomCleanVector(modifier: Modifier = Modifier) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val surface = MaterialTheme.colorScheme.surface
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height

    fun drawSparkle(cx: Float, cy: Float, r: Float) {
      val sparkle = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.25f, cy - r * 0.25f)
        lineTo(cx + r, cy)
        lineTo(cx + r * 0.25f, cy + r * 0.25f)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.25f, cy + r * 0.25f)
        lineTo(cx - r, cy)
        lineTo(cx - r * 0.25f, cy - r * 0.25f)
        close()
      }
      drawPath(sparkle, color = primary)
    }
    drawSparkle(w * 0.26f, h * 0.24f, 6.dp.toPx())
    drawSparkle(w * 0.18f, h * 0.40f, 3.8.dp.toPx())
    drawSparkle(w * 0.76f, h * 0.28f, 4.2.dp.toPx())

    rotate(degrees = 28f, pivot = Offset(w * 0.52f, h * 0.54f)) {
      drawRect(color = primary, topLeft = Offset(w * 0.47f, h * 0.10f), size = Size(w * 0.08f, h * 0.38f))
      drawRect(color = primary.copy(alpha = 0.85f), topLeft = Offset(w * 0.37f, h * 0.44f), size = Size(w * 0.28f, h * 0.09f))
      val bristles = Path().apply {
        moveTo(w * 0.37f, h * 0.53f)
        lineTo(w * 0.65f, h * 0.53f)
        lineTo(w * 0.74f, h * 0.86f)
        lineTo(w * 0.28f, h * 0.86f)
        close()
      }
      drawPath(bristles, color = secondary)
      for (t in listOf(0.42f, 0.51f, 0.60f)) {
        drawLine(color = surface, start = Offset(w * t, h * 0.74f), end = Offset(w * (t + (t - 0.51f) * 0.25f), h * 0.87f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Square)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun AnalyzeScreenPreview() {
  NexoFilesTheme {
    AnalyzeScreen(
      analysis = StorageAnalysisResult(
        deviceTotalBytes = 128_000_000_000L,
        breakdown = CategoryBreakdown(
          photosBytes = 5_000_000_000L,
          videosBytes = 6_200_000_000L,
          appsBytes = 800_000_000L,
          docsBytes = 1_500_000_000L,
          othersBytes = 3_400_000_000L,
        ),
        largeFiles = listOf(LocalFileItem("pelicula_4k.mkv", "/storage/emulated/0/Movies/pelicula_4k.mkv", false, 1_850_000_000L, System.currentTimeMillis())),
      ),
      selectedDuplicates = emptySet(),
      recycleBinItems = emptyList(),
      onRescan = {},
      onToggleDuplicate = {},
      onSmartSelectDuplicates = {},
      onDeleteSelectedDuplicates = {},
      onCleanJunk = {},
      onTrashLargeFile = {},
      onOpenFolderPath = {},
      onRestoreRecycleItem = {},
      onDeleteRecycleForever = {},
      onEmptyRecycleBin = {},
    )
  }
}
