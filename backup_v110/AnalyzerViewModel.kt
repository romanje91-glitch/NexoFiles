package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.db.RecycleBinEntity
import com.example.data.model.DuplicateScanProgress
import com.example.data.model.SimilarPhotosScanState
import com.example.data.model.StorageAnalysisResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class CleanupAnimationState(
  val isRunning: Boolean = false,
  val scanningPath: String = "",
  val freedBytesCounter: Long = 0L,
  val removingPaths: Set<String> = emptySet(),
  val showConfirmation: Boolean = false,
  val lastFreedBytes: Long = 0L,
  val lastActionLabel: String = "",
  val undoRecycleIds: List<Long> = emptyList(),
)

class AnalyzerViewModel(private val container: AppContainer) : ViewModel() {

  private val _analysis = MutableStateFlow(StorageAnalysisResult())
  val analysis: StateFlow<StorageAnalysisResult> = _analysis.asStateFlow()

  private val _duplicateProgress = MutableStateFlow(DuplicateScanProgress())
  val duplicateProgress: StateFlow<DuplicateScanProgress> = _duplicateProgress.asStateFlow()

  private val _similarScanState = MutableStateFlow(SimilarPhotosScanState())
  val similarScanState: StateFlow<SimilarPhotosScanState> = _similarScanState.asStateFlow()

  private val _selectedDuplicates = MutableStateFlow<Set<String>>(emptySet())
  val selectedDuplicates: StateFlow<Set<String>> = _selectedDuplicates.asStateFlow()

  private val _selectedSimilarPhotos = MutableStateFlow<Set<String>>(emptySet())
  val selectedSimilarPhotos: StateFlow<Set<String>> = _selectedSimilarPhotos.asStateFlow()

  private val _cleanupState = MutableStateFlow(CleanupAnimationState())
  val cleanupState: StateFlow<CleanupAnimationState> = _cleanupState.asStateFlow()

  val recycleBinItems: StateFlow<List<RecycleBinEntity>> = container.localFiles.observeRecycleBin()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _messages = MutableSharedFlow<String>()
  val messages = _messages.asSharedFlow()

  init {
    runScan()
  }

  fun runScan() {
    if (_analysis.value.isScanning) return
    viewModelScope.launch {
      _analysis.value = _analysis.value.copy(
        isScanning = true,
        scanStageIndex = 0,
        scanProgressMessage = "INICIANDO RADAR DE SECTORES…",
      )
      _duplicateProgress.value = DuplicateScanProgress(isRunning = true, phase = 1, processed = 0, total = 1)
      _similarScanState.value = _similarScanState.value.copy(isScanning = true, processed = 0, total = 1)

      val result = container.analyzer.runFullAnalysis(
        onStageProgress = { stage, msg ->
          _analysis.value = _analysis.value.copy(
            scanStageIndex = stage,
            scanProgressMessage = msg,
          )
        },
        onDuplicateFunnelProgress = { prog ->
          _duplicateProgress.value = prog
        },
        onSimilarPhotosProgress = { processed, total ->
          _similarScanState.value = _similarScanState.value.copy(
            isScanning = true,
            processed = processed,
            total = total,
          )
        },
      )

      _analysis.value = result
      _duplicateProgress.value = _duplicateProgress.value.copy(isRunning = false)
      _similarScanState.value = SimilarPhotosScanState(
        isScanning = false,
        processed = _similarScanState.value.total,
        total = _similarScanState.value.total,
        groups = result.similarPhotoGroups,
      )

      _selectedDuplicates.value = result.duplicateGroups.flatMap { group ->
        group.files.drop(1).map { it.path }
      }.toSet()

      _selectedSimilarPhotos.value = result.similarPhotoGroups.flatMap { group ->
        group.items.filterNot { it.isBestShot }.map { it.path }
      }.toSet()
    }
  }

  fun runDedicatedDuplicateScan() {
    if (_duplicateProgress.value.isRunning || _analysis.value.isScanning) return
    viewModelScope.launch {
      val roots = container.localFiles.getAvailableVolumes()
        .map { File(it.second) }
        .filter { it.exists() }
        .ifEmpty { listOf(File(container.localFiles.rootStoragePath)) }

      _duplicateProgress.value = DuplicateScanProgress(isRunning = true, phase = 1, processed = 0, total = 1)
      val summary = container.duplicateFinder.scanDuplicates(
        roots = roots,
        maxDepth = 10,
        includeHidden = false,
        onProgress = { prog -> _duplicateProgress.value = prog },
      )
      _duplicateProgress.value = _duplicateProgress.value.copy(isRunning = false)
      _analysis.value = _analysis.value.copy(
        duplicateGroups = summary.groups,
        duplicateSummaryStats = summary,
      )
      val smart = summary.groups.flatMap { group ->
        group.files.drop(1).map { it.path }
      }.toSet()
      _selectedDuplicates.value = smart
      emitMessage("Embudo MD5 completado: ${summary.groups.size} grupo(s) idéntico(s)")
    }
  }

  fun runDedicatedSimilarPhotosScan() {
    if (_similarScanState.value.isScanning || _analysis.value.isScanning) return
    viewModelScope.launch {
      _similarScanState.value = SimilarPhotosScanState(isScanning = true, processed = 0, total = 1)
      val groups = container.similarPhotos.scanSimilarPhotos { processed, total ->
        _similarScanState.value = _similarScanState.value.copy(
          isScanning = true,
          processed = processed,
          total = total,
        )
      }
      _similarScanState.value = SimilarPhotosScanState(
        isScanning = false,
        processed = _similarScanState.value.total,
        total = _similarScanState.value.total,
        groups = groups,
      )
      _analysis.value = _analysis.value.copy(similarPhotoGroups = groups)
      val smart = groups.flatMap { grp ->
        grp.items.filterNot { it.isBestShot }.map { it.path }
      }.toSet()
      _selectedSimilarPhotos.value = smart
      emitMessage("Escaneo perceptual completado: ${groups.size} serie(s) de fotos similares")
    }
  }

  fun toggleDuplicateSelection(path: String) {
    val next = _selectedDuplicates.value.toMutableSet()
    if (!next.add(path)) next.remove(path)
    _selectedDuplicates.value = next
  }

  fun smartSelectDuplicates() {
    val smart = _analysis.value.duplicateGroups.flatMap { group ->
      group.files.drop(1).map { it.path }
    }.toSet()
    _selectedDuplicates.value = smart
    emitMessage("Selección inteligente aplicada (${smart.size} copias)")
  }

  fun deleteSelectedDuplicates(permanent: Boolean = false) {
    val targets = _selectedDuplicates.value.toList()
    if (targets.isEmpty()) return
    val sizeByPath = _analysis.value.duplicateGroups
      .flatMap { it.files }
      .associate { it.path to it.sizeBytes }
    val estimatedBytes = targets.sumOf { sizeByPath[it] ?: 0L }
    runAnimatedCleanup(
      targets = targets,
      estimatedBytes = estimatedBytes,
      permanent = permanent,
      label = "Duplicados limpiados",
    )
  }

  fun toggleSimilarPhotoSelection(path: String) {
    val next = _selectedSimilarPhotos.value.toMutableSet()
    if (!next.add(path)) next.remove(path)
    _selectedSimilarPhotos.value = next
  }

  fun smartSelectSimilarPhotos() {
    val smart = _analysis.value.similarPhotoGroups.flatMap { group ->
      group.items.filterNot { it.isBestShot }.map { it.path }
    }.toSet()
    _selectedSimilarPhotos.value = smart
    emitMessage("Mejores tomas conservadas (${smart.size} copias seleccionadas)")
  }

  fun deleteSelectedSimilarPhotos(permanent: Boolean = false) {
    val targets = _selectedSimilarPhotos.value.toList()
    if (targets.isEmpty()) return
    val sizeByPath = _analysis.value.similarPhotoGroups
      .flatMap { it.items }
      .associate { it.path to it.sizeBytes }
    val estimatedBytes = targets.sumOf { sizeByPath[it] ?: 0L }
    runAnimatedCleanup(
      targets = targets,
      estimatedBytes = estimatedBytes,
      permanent = permanent,
      label = "Fotos similares limpiadas",
    )
  }

  fun cleanJunkAndCache() {
    val junkPaths = _analysis.value.junkFiles.map { it.path }
    val emptyPaths = _analysis.value.emptyFolders.map { it.path }
    val allTargets = junkPaths + emptyPaths
    if (allTargets.isEmpty()) {
      emitMessage("No hay archivos basura ni carpetas vacías para limpiar")
      return
    }
    runAnimatedCleanup(
      targets = allTargets,
      estimatedBytes = _analysis.value.junkTotalBytes,
      permanent = true,
      label = "Limpieza rápida completada",
    )
  }

  fun moveLargeFileToTrash(path: String) {
    val size = _analysis.value.largeFiles.firstOrNull { it.path == path }?.sizeBytes ?: 0L
    runAnimatedCleanup(
      targets = listOf(path),
      estimatedBytes = size,
      permanent = false,
      label = "Archivo enviado a la Papelera",
    )
  }

  private fun runAnimatedCleanup(
    targets: List<String>,
    estimatedBytes: Long,
    permanent: Boolean,
    label: String,
  ) {
    viewModelScope.launch {
      _cleanupState.value = CleanupAnimationState(
        isRunning = true,
        scanningPath = targets.firstOrNull().orEmpty(),
        freedBytesCounter = 0L,
      )
      val sampleTargets = targets.take(12)
      var runningSum = 0L
      val stepBytes = if (sampleTargets.isNotEmpty()) estimatedBytes / sampleTargets.size else estimatedBytes
      for (path in sampleTargets) {
        runningSum = (runningSum + stepBytes).coerceAtMost(estimatedBytes)
        _cleanupState.value = _cleanupState.value.copy(
          scanningPath = path,
          freedBytesCounter = runningSum,
          removingPaths = _cleanupState.value.removingPaths + path,
        )
        delay(55)
      }
      delay(220)

      val useTrash = !permanent && container.preferences.allSettings.first().recycleBinEnabled
      val beforeIds = if (useTrash) container.localFiles.observeRecycleBin().first().map { it.id }.toSet() else emptySet()
      val res = if (useTrash) {
        container.localFiles.moveToRecycleBin(targets)
      } else {
        container.localFiles.deletePermanently(targets)
      }
      val newIds = if (useTrash) {
        container.localFiles.observeRecycleBin().first().map { it.id }.filter { it !in beforeIds }
      } else {
        emptyList()
      }

      res.onSuccess { count ->
        _cleanupState.value = CleanupAnimationState(
          isRunning = false,
          showConfirmation = true,
          lastFreedBytes = estimatedBytes,
          lastActionLabel = "$label ($count)",
          undoRecycleIds = newIds,
        )
        runScan()
      }.onFailure { err ->
        _cleanupState.value = CleanupAnimationState()
        emitMessage("Error en limpieza: ${err.localizedMessage}")
      }
    }
  }

  fun dismissCleanupConfirmation() {
    _cleanupState.value = _cleanupState.value.copy(showConfirmation = false, undoRecycleIds = emptyList())
  }

  fun undoLastCleanup() {
    val ids = _cleanupState.value.undoRecycleIds
    if (ids.isEmpty()) return
    viewModelScope.launch {
      val currentItems = container.localFiles.observeRecycleBin().first()
      currentItems.filter { it.id in ids }.forEach {
        container.localFiles.restoreFromRecycleBin(it)
      }
      _cleanupState.value = _cleanupState.value.copy(showConfirmation = false, undoRecycleIds = emptyList())
      emitMessage("Elementos restaurados desde la Papelera")
      runScan()
    }
  }

  fun restoreRecycleItem(item: RecycleBinEntity) {
    viewModelScope.launch {
      container.localFiles.restoreFromRecycleBin(item)
        .onSuccess { emitMessage("Restaurado: ${item.fileName}") }
        .onFailure { emitMessage("No se pudo restaurar: ${it.localizedMessage}") }
    }
  }

  fun deleteRecycleItemForever(item: RecycleBinEntity) {
    viewModelScope.launch {
      container.localFiles.deleteRecycleItemPermanently(item)
        .onSuccess { emitMessage("Eliminado permanentemente") }
    }
  }

  fun emptyRecycleBin() {
    viewModelScope.launch {
      container.localFiles.emptyRecycleBin()
        .onSuccess { emitMessage("Papelera vaciada por completo") }
    }
  }

  private fun emitMessage(msg: String) {
    viewModelScope.launch { _messages.emit(msg) }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return AnalyzerViewModel(container) as T
    }
  }
}
