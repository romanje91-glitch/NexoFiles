package com.example.data.model

import android.graphics.drawable.Drawable

enum class SortBy(val label: String) {
  DATE("Fecha"),
  NAME("Nombre"),
  SIZE("Tamaño"),
}

enum class ThemeMode(val label: String, val description: String) {
  DARK("NERV HUD (Noche)", "Negro cibernético y acentos EVA-01 / EVA-02"),
  SYSTEM("Sistema", "Alterna Día/Noche según el dispositivo"),
  LIGHT("NERV HUD (Día)", "Superficie táctica clara con acentos EVA"),
}

enum class FileCategory(val title: String) {
  IMAGES("Imágenes"),
  VIDEOS("Videos"),
  DOCUMENTS("Documentos"),
  MUSIC("Música"),
  APKS("Apps"),
}

enum class ClipboardAction {
  COPY,
  MOVE,
}

data class FileClipboard(
  val paths: List<String>,
  val action: ClipboardAction,
)

data class StorageStats(
  val totalBytes: Long = 0L,
  val usedBytes: Long = 0L,
  val freeBytes: Long = 0L,
  val rootPath: String = "/storage/emulated/0",
  val hasAllFilesAccess: Boolean = false,
) {
  val usedPercent: Int
    get() = if (totalBytes > 0L) ((usedBytes.toDouble() / totalBytes) * 100.0).toInt().coerceIn(0, 100) else 0
}

data class LocalFileItem(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val lastModified: Long,
  val childCount: Int = 0,
  val mimeType: String = "*/*",
  val isHidden: Boolean = name.startsWith("."),
  val canRead: Boolean = true,
  val canWrite: Boolean = true,
)

data class FileProperties(
  val item: LocalFileItem,
  val childFiles: Int,
  val childFolders: Int,
  val totalSizeBytes: Long,
  val md5: String,
  val sha256: String,
  val permissionsString: String,
)

data class CategoryBreakdown(
  val photosBytes: Long = 0L,
  val videosBytes: Long = 0L,
  val appsBytes: Long = 0L,
  val docsBytes: Long = 0L,
  val othersBytes: Long = 0L,
)

data class DuplicateGroup(
  val md5: String,
  val sizeBytes: Long,
  val files: List<LocalFileItem>,
  val partialHash: String = "",
)

data class DuplicateScanProgress(
  val isRunning: Boolean = false,
  val phase: Int = 0, // 1 = Exact size, 2 = 8KB Header+Tail SHA-256, 3 = Full MD5
  val processed: Int = 0,
  val total: Int = 0,
  val candidatesRemaining: Int = 0,
  val phaseMessage: String = "",
) {
  val fraction: Float
    get() = if (total > 0) (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DuplicateScanSummary(
  val groups: List<DuplicateGroup> = emptyList(),
  val totalScannedFiles: Int = 0,
  val phase1SizeCandidates: Int = 0,
  val phase2StructuralCandidates: Int = 0,
  val phase3ConfirmedDuplicates: Int = 0,
)

data class SimilarPhotoItem(
  val id: String,
  val name: String,
  val path: String,
  val uriString: String = "",
  val width: Int,
  val height: Int,
  val sizeBytes: Long,
  val dateTakenMs: Long,
  val dHash: Long,
  val sharpnessScore: Double,
  val isBestShot: Boolean = false,
) {
  val megapixelsLabel: String
    get() {
      val mp = (width.toLong() * height.toLong()) / 1_000_000.0
      return if (mp >= 0.1) String.format(java.util.Locale.US, "%.1f MP", mp) else "${width}×${height}"
    }
}

data class SimilarPhotoGroup(
  val id: String,
  val maxSimilarityPercent: Int,
  val minHammingDistance: Int,
  val items: List<SimilarPhotoItem>,
  val isBurstGroup: Boolean = false,
) {
  val recoverableBytes: Long
    get() = items.filterNot { it.isBestShot }.sumOf { it.sizeBytes }
}

data class SimilarPhotosScanState(
  val isScanning: Boolean = false,
  val processed: Int = 0,
  val total: Int = 0,
  val statusMessage: String = "",
  val groups: List<SimilarPhotoGroup> = emptyList(),
) {
  val fraction: Float
    get() = if (total > 0) (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

  val recoverableBytes: Long
    get() = groups.sumOf { it.recoverableBytes }
}

data class AppFolderInfo(
  val folderName: String,
  val path: String,
  val sizeBytes: Long,
  val itemCount: Int,
)

data class StorageAnalysisResult(
  val breakdown: CategoryBreakdown = CategoryBreakdown(),
  val deviceTotalBytes: Long = 0L,
  val deviceUsedBytes: Long = 0L,
  val duplicateGroups: List<DuplicateGroup> = emptyList(),
  val duplicateSummaryStats: DuplicateScanSummary = DuplicateScanSummary(),
  val similarPhotoGroups: List<SimilarPhotoGroup> = emptyList(),
  val largeFiles: List<LocalFileItem> = emptyList(),
  val junkFiles: List<LocalFileItem> = emptyList(),
  val emptyFolders: List<LocalFileItem> = emptyList(),
  val appFolders: List<AppFolderInfo> = emptyList(),
  val recentlyCreatedFiles: List<LocalFileItem> = emptyList(),
  val isScanning: Boolean = false,
  val scanStageIndex: Int = 0,
  val scanProgressMessage: String = "",
) {
  val duplicateWasteBytes: Long
    get() = duplicateGroups.sumOf { group ->
      (group.files.size - 1).coerceAtLeast(0) * group.sizeBytes
    }

  val similarPhotosWasteBytes: Long
    get() = similarPhotoGroups.sumOf { it.recoverableBytes }

  val junkTotalBytes: Long
    get() = junkFiles.sumOf { it.sizeBytes }

  val largeFilesTotalBytes: Long
    get() = largeFiles.sumOf { it.sizeBytes }
}

data class ArchiveEntryInfo(
  val name: String,
  val sizeBytes: Long,
  val isDirectory: Boolean,
  val compressedSize: Long = -1L,
)

data class DriveFileItem(
  val id: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Long,
  val modifiedTime: String,
  val isFolder: Boolean,
)

data class InstalledAppInfo(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val apkPath: String,
  val apkSizeBytes: Long,
  val isSystemApp: Boolean,
  val installedAt: Long,
  val icon: Drawable? = null,
)

enum class RemoteProtocol(val label: String, val defaultPort: Int) {
  FTP("FTP", 21),
  FTPS("FTPS", 21),
  SFTP("SFTP", 22),
  WEBDAV("WebDAV", 80),
  SMB("LAN / SMB", 445),
}

enum class FtpMode(val label: String) {
  ACTIVE("Active"),
  PASSIVE("Passive"),
}

enum class ServerEncoding(val label: String, val charsetName: String) {
  AUTO("AUTO", "UTF-8"),
  UTF_8("UTF-8", "UTF-8"),
  ISO_8859_1("ISO-8859-1", "ISO-8859-1"),
  WINDOWS_1252("Windows-1252", "windows-1252"),
}

enum class LayoutViewMode(val label: String) {
  LIST("Lista"),
  GRID("Cuadrícula"),
  COMPACT("Compacto"),
  DETAILED("Detallado"),
}

enum class SearchEngineMode(val label: String, val description: String) {
  AUTO("Automático", "Combina índice rápido y escaneo profundo"),
  FAST_INDEX("Índice rápido", "Búsqueda veloz en directorios principales"),
  DEEP_SCAN("Búsqueda profunda", "Explora todo el árbol de almacenamiento"),
}

enum class AppLanguageOption(val code: String, val label: String) {
  AUTO("AUTO", "Automático"),
  ES("ES", "Español"),
  EN("EN", "English"),
}

enum class AutoBackupFrequency(val label: String) {
  MANUAL("Manual"),
  DAILY("Diario"),
  WIFI_AUTO("Wi-Fi automático"),
}

enum class CloudProviderType(val label: String, val subtitle: String) {
  GOOGLE_DRIVE("Google Drive", "API v3 · Credential Manager"),
  GOOGLE_PHOTOS("Google Photos", "Picker API · Library Append"),
  WEBDAV("WebDAV / Nextcloud", "Servidor privado HTTP/HTTPS"),
  DROPBOX("Dropbox", "Enlace vía WebDAV o cuenta"),
  ONEDRIVE("OneDrive", "Enlace vía WebDAV / Carpeta nube"),
}

data class PickerSessionInfo(
  val sessionId: String,
  val pickerUri: String,
  val mediaItemsSet: Boolean = false,
  val pollIntervalMs: Long = 2500L,
)

data class GooglePhotoMediaItem(
  val id: String,
  val filename: String,
  val mimeType: String,
  val baseUrl: String,
  val width: Int = 0,
  val height: Int = 0,
  val createTime: String = "",
  val cachedFilePath: String? = null,
)

enum class TacticalFilterPreset {
  ORIGINAL,
  VIVID,
  HIGH_CONTRAST,
  MONO,
  NIGHT_VIOLET,
}

enum class CropAspectPreset(val ratio: Float?) {
  FREE(null),
  SQUARE_1_1(1f),
  STANDARD_4_3(4f / 3f),
  WIDE_16_9(16f / 9f),
}

enum class PhotoExportFormat(val ext: String, val mimeType: String) {
  JPEG("jpg", "image/jpeg"),
  PNG("png", "image/png"),
}

data class NormalizedCropRect(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 1f,
  val bottom: Float = 1f,
) {
  val isFullFrame: Boolean
    get() = left <= 0.005f && top <= 0.005f && right >= 0.995f && bottom >= 0.995f
}

data class PhotoEditParams(
  val brightness: Float = 0f, // -100f..100f
  val contrast: Float = 1f, // 0.5f..1.8f
  val saturation: Float = 1f, // 0f..2f
  val warmth: Float = 0f, // -100f..100f
  val rotationDegrees: Int = 0, // 0, 90, 180, 270
  val flipHorizontal: Boolean = false,
  val flipVertical: Boolean = false,
  val filterPreset: TacticalFilterPreset = TacticalFilterPreset.ORIGINAL,
  val cropAspect: CropAspectPreset = CropAspectPreset.FREE,
  val cropRect: NormalizedCropRect = NormalizedCropRect(),
) {
  val hasChanges: Boolean
    get() = brightness != 0f ||
      contrast != 1f ||
      saturation != 1f ||
      warmth != 0f ||
      (rotationDegrees % 360) != 0 ||
      flipHorizontal ||
      flipVertical ||
      filterPreset != TacticalFilterPreset.ORIGINAL ||
      !cropRect.isFullFrame
}

data class LanDevice(
  val ipAddress: String,
  val hostname: String,
  val openPorts: List<Int>,
  val services: List<String>,
)

data class RemoteFileEntry(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val modifiedAt: Long,
)

enum class VaultLockMethod(val id: String) {
  BIOMETRIC("BIOMETRIC"),
  PIN("PIN"),
  PATTERN("PATTERN"),
  PASSWORD("PASSWORD"),
}

data class VaultFileItem(
  val id: String,
  val originalName: String,
  val originalPath: String,
  val vaultFilePath: String,
  val sizeBytes: Long,
  val mimeType: String,
  val addedAt: Long,
)

data class VaultBackupEntry(
  val driveFile: DriveFileItem,
  val originalName: String,
  val encryptedAtMs: Long = 0L,
)

data class VaultPortalState(
  val isRunning: Boolean = false,
  val url: String = "",
  val token: String = "",
  val port: Int = 8899,
  val expiresAtMs: Long = 0L,
  val itemCount: Int = 0,
  val lastEvent: String = "",
)

data class SenderStagedFile(
  val name: String,
  val path: String,
  val sizeBytes: Long,
)

data class GestureBinding(
  val id: String,
  val gestureShape: String, // e.g. "V", "O", "L", "Z", "S", "PERSONALIZADO"
  val label: String,
  val targetRouteOrPath: String,
)

data class DrawerShortcutConfig(
  val id: String,
  val label: String,
  val enabled: Boolean,
)

data class AllSettingsState(
  // Bloque 1: Pantalla
  val themeMode: ThemeMode = ThemeMode.DARK,
  val showHiddenFiles: Boolean = true,
  val sortBy: SortBy = SortBy.NAME,
  val sortAscending: Boolean = true,
  val showOnlyFoldersInHistory: Boolean = false,
  val showFileTransferStation: Boolean = true,
  val showNewFilesOnHome: Boolean = true,
  val showSdCardStatusBar: Boolean = true,
  val showThumbnails: Boolean = true,
  val showAppIconOnFolders: Boolean = true,
  val showSdSizeInHistory: Boolean = false,
  val usbConnectPrompt: Boolean = true,
  val hideHomeWhatsNewCard: Boolean = false,
  val showFloatingClipboardButton: Boolean = true,
  val appLanguage: AppLanguageOption = AppLanguageOption.ES,
  // Bloque 1: Limpieza
  val clearHistoryOnExit: Boolean = false,
  val clearCacheOnExit: Boolean = false,
  // Bloque 1: Directorios
  val homeDirectory: String = "/storage/emulated/0",
  val bluetoothReceiveDir: String = "/storage/emulated/0/Download/Bluetooth",
  val downloadDirectory: String = "/storage/emulated/0/Download",
  // Bloque 1: Motor de búsqueda
  val searchEngineMode: SearchEngineMode = SearchEngineMode.AUTO,
  val showSearchBarOnHome: Boolean = true,
  // Bloque 1: Notificaciones
  val dismissNotifOnTaskFinish: Boolean = false,
  val notifyApkInstallPermissions: Boolean = true,
  val notifyNewFilesCreated: Boolean = true,
  val remindNewCameraPhotos: Boolean = false,
  // Bloque 1: Widget flotante de registro
  val floatingLoggerEnabled: Boolean = false,
  val floatingLoggerFormats: String = "apk,pdf,zip,mp4,jpg",
  val floatingLoggerSourceApp: String = "Todas",
  // Bloque 2: Gestión de Diseño
  val layoutViewMode: LayoutViewMode = LayoutViewMode.DETAILED,
  val drawerOrderIds: List<String> = listOf("HOME", "FILES", "ANALYZE", "SENDER", "DRIVE", "GOOGLE_PHOTOS", "EXTERNAL_CLOUDS", "VIEW_ON_PC", "VAULT", "RECYCLE_BIN"),
  val drawerHiddenIds: Set<String> = emptySet(),
  val toolbarEnabledActions: List<String> = listOf("COPY", "MOVE", "RENAME", "ZIP", "SHARE", "DELETE", "FTP", "WEBDAV", "SMB", "SENDER", "VIEW_PC"),
  // Bloque 3: Seguridad y Respaldo
  val vaultBiometricEnabled: Boolean = true,
  val vaultLockMethod: VaultLockMethod = VaultLockMethod.BIOMETRIC,
  val vaultSecretHash: String = "",
  val masterPinHash: String = "",
  val autoSyncRemoteEdits: Boolean = true,
  // Bloque 4: Ajustes de Herramientas
  val autoBackupApkBeforeUninstall: Boolean = false,
  val appBackupDirectory: String = "/storage/emulated/0/NexoFiles/Backed-up Apps",
  val cleanResidualAppFolders: Boolean = true,
  val disableBuiltInDownloader: Boolean = false,
  val multithreadTransferEnabled: Boolean = true,
  val initialWindows: List<String> = listOf("/storage/emulated/0", "/storage/emulated/0/Download"),
  val recycleBinEnabled: Boolean = true,
  val gesturesEnabled: Boolean = false,
  val gestureBindings: List<GestureBinding> = emptyList(),
  // Sender
  val senderNickname: String = android.os.Build.MODEL ?: "Dispositivo Android",
  val senderSavePath: String = "/storage/emulated/0/NexoShare",
  val senderSameWifiMode: Boolean = true,
  // Bloque 5: Sistema
  val autoCheckUpdates: Boolean = true,
)
