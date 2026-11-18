package com.example.data.prefs

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AllSettingsState
import com.example.data.model.AppLanguageOption
import com.example.data.model.GestureBinding
import com.example.data.model.LayoutViewMode
import com.example.data.model.SearchEngineMode
import com.example.data.model.SortBy
import com.example.data.model.ThemeMode
import com.example.data.model.VaultLockMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexofiles_prefs")

class UserPreferencesRepository(private val context: Context) {

  private val defaultStorageRoot: String
    get() = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

  private object Keys {
    val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
    val SORT_BY = stringPreferencesKey("sort_by")
    val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
    val VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric")
    val VAULT_LOCK_METHOD = stringPreferencesKey("vault_lock_method")
    val VAULT_SECRET_HASH = stringPreferencesKey("vault_secret_hash")
    val DRIVE_EMAIL = stringPreferencesKey("drive_email")
    val DRIVE_NAME = stringPreferencesKey("drive_name")
    val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
    val THEME_MODE = stringPreferencesKey("theme_mode")

    // Bloque 1: Pantalla
    val SHOW_ONLY_FOLDERS_HISTORY = booleanPreferencesKey("show_only_folders_history")
    val SHOW_FILE_TRANSFER_STATION = booleanPreferencesKey("show_file_transfer_station")
    val SHOW_NEW_FILES_HOME = booleanPreferencesKey("show_new_files_home")
    val SHOW_SD_STATUS_BAR = booleanPreferencesKey("show_sd_status_bar")
    val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
    val SHOW_APP_ICON_FOLDERS = booleanPreferencesKey("show_app_icon_folders")
    val SHOW_SD_SIZE_HISTORY = booleanPreferencesKey("show_sd_size_history")
    val USB_CONNECT_PROMPT = booleanPreferencesKey("usb_connect_prompt")
    val HIDE_HOME_WHATS_NEW = booleanPreferencesKey("hide_home_whats_new")
    val SHOW_FLOATING_CLIPBOARD = booleanPreferencesKey("show_floating_clipboard")
    val APP_LANGUAGE = stringPreferencesKey("app_language")

    // Bloque 1: Limpieza
    val CLEAR_HISTORY_ON_EXIT = booleanPreferencesKey("clear_history_on_exit")
    val CLEAR_CACHE_ON_EXIT = booleanPreferencesKey("clear_cache_on_exit")

    // Bloque 1: Directorios
    val HOME_DIRECTORY = stringPreferencesKey("home_directory")
    val BLUETOOTH_RECEIVE_DIR = stringPreferencesKey("bluetooth_receive_dir")
    val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")

    // Bloque 1: Busqueda
    val SEARCH_ENGINE_MODE = stringPreferencesKey("search_engine_mode")
    val SHOW_SEARCH_BAR_HOME = booleanPreferencesKey("show_search_bar_home")

    // Bloque 1: Notificaciones
    val DISMISS_NOTIF_ON_FINISH = booleanPreferencesKey("dismiss_notif_on_finish")
    val NOTIFY_APK_PERMISSIONS = booleanPreferencesKey("notify_apk_permissions")
    val NOTIFY_NEW_FILES = booleanPreferencesKey("notify_new_files")
    val REMIND_NEW_CAMERA_PHOTOS = booleanPreferencesKey("remind_new_camera_photos")

    // Bloque 1: Widget flotante de registro
    val FLOATING_LOGGER_ENABLED = booleanPreferencesKey("floating_logger_enabled")
    val FLOATING_LOGGER_FORMATS = stringPreferencesKey("floating_logger_formats")
    val FLOATING_LOGGER_SOURCE_APP = stringPreferencesKey("floating_logger_source_app")

    // Bloque 2: Diseño
    val LAYOUT_VIEW_MODE = stringPreferencesKey("layout_view_mode")
    val DRAWER_ORDER_CSV = stringPreferencesKey("drawer_order_csv")
    val DRAWER_HIDDEN_CSV = stringPreferencesKey("drawer_hidden_csv")
    val TOOLBAR_ACTIONS_CSV = stringPreferencesKey("toolbar_actions_csv")

    // Bloque 3: Seguridad
    val MASTER_PIN_HASH = stringPreferencesKey("master_pin_hash")
    val AUTO_SYNC_REMOTE_EDITS = booleanPreferencesKey("auto_sync_remote_edits")

    // Bloque 4: Herramientas
    val AUTO_BACKUP_APK_UNINSTALL = booleanPreferencesKey("auto_backup_apk_uninstall")
    val APP_BACKUP_DIRECTORY = stringPreferencesKey("app_backup_directory")
    val CLEAN_RESIDUAL_APP_FOLDERS = booleanPreferencesKey("clean_residual_app_folders")
    val DISABLE_BUILTIN_DOWNLOADER = booleanPreferencesKey("disable_builtin_downloader")
    val MULTITHREAD_TRANSFER = booleanPreferencesKey("multithread_transfer")
    val INITIAL_WINDOWS_CSV = stringPreferencesKey("initial_windows_csv")
    val RECYCLE_BIN_ENABLED = booleanPreferencesKey("recycle_bin_enabled")
    val GESTURES_ENABLED = booleanPreferencesKey("gestures_enabled")
    val GESTURE_BINDINGS_JSON = stringPreferencesKey("gesture_bindings_json")

    // Sender
    val SENDER_NICKNAME = stringPreferencesKey("sender_nickname")
    val SENDER_SAVE_PATH = stringPreferencesKey("sender_save_path")
    val SENDER_SAME_WIFI_MODE = booleanPreferencesKey("sender_same_wifi_mode")

    // Bloque 5: Sistema
    val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
  }

  val showHiddenFiles: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_HIDDEN_FILES] ?: true }
  val sortBy: Flow<SortBy> = context.dataStore.data.map { prefs ->
    runCatching { SortBy.valueOf(prefs[Keys.SORT_BY] ?: SortBy.NAME.name) }.getOrDefault(SortBy.NAME)
  }
  val sortAscending: Flow<Boolean> = context.dataStore.data.map { it[Keys.SORT_ASCENDING] ?: true }
  val vaultBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VAULT_BIOMETRIC] ?: true }
  val driveAccountEmail: Flow<String?> = context.dataStore.data.map { it[Keys.DRIVE_EMAIL] }
  val driveAccountName: Flow<String?> = context.dataStore.data.map { it[Keys.DRIVE_NAME] }
  val webServerPort: Flow<Int> = context.dataStore.data.map { it[Keys.WEB_SERVER_PORT] ?: 8080 }
  val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
    runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name) }
      .getOrDefault(ThemeMode.DARK)
  }

  val allSettings: Flow<AllSettingsState> = context.dataStore.data.map { prefs ->
    val root = defaultStorageRoot
    val defaultDrawer = listOf("HOME", "FILES", "ANALYZE", "SENDER", "DRIVE", "GOOGLE_PHOTOS", "EXTERNAL_CLOUDS", "VIEW_ON_PC", "VAULT", "RECYCLE_BIN")
    val defaultToolbar = listOf("COPY", "MOVE", "RENAME", "ZIP", "SHARE", "DELETE", "FTP", "WEBDAV", "SMB", "SENDER", "VIEW_PC")
    val resolvedMethod = runCatching {
      VaultLockMethod.valueOf(prefs[Keys.VAULT_LOCK_METHOD] ?: VaultLockMethod.BIOMETRIC.name)
    }.getOrDefault(VaultLockMethod.BIOMETRIC)
    AllSettingsState(
      themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK),
      showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: true,
      sortBy = runCatching { SortBy.valueOf(prefs[Keys.SORT_BY] ?: SortBy.NAME.name) }.getOrDefault(SortBy.NAME),
      sortAscending = prefs[Keys.SORT_ASCENDING] ?: true,
      showOnlyFoldersInHistory = prefs[Keys.SHOW_ONLY_FOLDERS_HISTORY] ?: false,
      showFileTransferStation = prefs[Keys.SHOW_FILE_TRANSFER_STATION] ?: true,
      showNewFilesOnHome = prefs[Keys.SHOW_NEW_FILES_HOME] ?: true,
      showSdCardStatusBar = prefs[Keys.SHOW_SD_STATUS_BAR] ?: true,
      showThumbnails = prefs[Keys.SHOW_THUMBNAILS] ?: true,
      showAppIconOnFolders = prefs[Keys.SHOW_APP_ICON_FOLDERS] ?: true,
      showSdSizeInHistory = prefs[Keys.SHOW_SD_SIZE_HISTORY] ?: false,
      usbConnectPrompt = prefs[Keys.USB_CONNECT_PROMPT] ?: true,
      hideHomeWhatsNewCard = prefs[Keys.HIDE_HOME_WHATS_NEW] ?: false,
      showFloatingClipboardButton = prefs[Keys.SHOW_FLOATING_CLIPBOARD] ?: true,
      appLanguage = runCatching { AppLanguageOption.valueOf(prefs[Keys.APP_LANGUAGE] ?: AppLanguageOption.AUTO.name) }.getOrDefault(AppLanguageOption.AUTO),
      clearHistoryOnExit = prefs[Keys.CLEAR_HISTORY_ON_EXIT] ?: false,
      clearCacheOnExit = prefs[Keys.CLEAR_CACHE_ON_EXIT] ?: false,
      homeDirectory = prefs[Keys.HOME_DIRECTORY] ?: root,
      bluetoothReceiveDir = prefs[Keys.BLUETOOTH_RECEIVE_DIR] ?: "$root/Download/Bluetooth",
      downloadDirectory = prefs[Keys.DOWNLOAD_DIRECTORY] ?: "$root/Download",
      searchEngineMode = runCatching { SearchEngineMode.valueOf(prefs[Keys.SEARCH_ENGINE_MODE] ?: SearchEngineMode.AUTO.name) }.getOrDefault(SearchEngineMode.AUTO),
      showSearchBarOnHome = prefs[Keys.SHOW_SEARCH_BAR_HOME] ?: true,
      dismissNotifOnTaskFinish = prefs[Keys.DISMISS_NOTIF_ON_FINISH] ?: false,
      notifyApkInstallPermissions = prefs[Keys.NOTIFY_APK_PERMISSIONS] ?: true,
      notifyNewFilesCreated = prefs[Keys.NOTIFY_NEW_FILES] ?: true,
      remindNewCameraPhotos = prefs[Keys.REMIND_NEW_CAMERA_PHOTOS] ?: false,
      floatingLoggerEnabled = prefs[Keys.FLOATING_LOGGER_ENABLED] ?: false,
      floatingLoggerFormats = prefs[Keys.FLOATING_LOGGER_FORMATS] ?: "apk,pdf,zip,mp4,jpg",
      floatingLoggerSourceApp = prefs[Keys.FLOATING_LOGGER_SOURCE_APP] ?: "Todas",
      layoutViewMode = runCatching { LayoutViewMode.valueOf(prefs[Keys.LAYOUT_VIEW_MODE] ?: LayoutViewMode.DETAILED.name) }.getOrDefault(LayoutViewMode.DETAILED),
      drawerOrderIds = prefs[Keys.DRAWER_ORDER_CSV]?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { defaultDrawer } ?: defaultDrawer,
      drawerHiddenIds = prefs[Keys.DRAWER_HIDDEN_CSV]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
      toolbarEnabledActions = prefs[Keys.TOOLBAR_ACTIONS_CSV]?.split(',')?.filter { it.isNotBlank() } ?: defaultToolbar,
      vaultBiometricEnabled = prefs[Keys.VAULT_BIOMETRIC] ?: (resolvedMethod == VaultLockMethod.BIOMETRIC),
      vaultLockMethod = resolvedMethod,
      vaultSecretHash = prefs[Keys.VAULT_SECRET_HASH] ?: "",
      masterPinHash = prefs[Keys.MASTER_PIN_HASH] ?: "",
      autoSyncRemoteEdits = prefs[Keys.AUTO_SYNC_REMOTE_EDITS] ?: true,
      autoBackupApkBeforeUninstall = prefs[Keys.AUTO_BACKUP_APK_UNINSTALL] ?: false,
      appBackupDirectory = prefs[Keys.APP_BACKUP_DIRECTORY] ?: "$root/NexoFiles/Backed-up Apps",
      cleanResidualAppFolders = prefs[Keys.CLEAN_RESIDUAL_APP_FOLDERS] ?: true,
      disableBuiltInDownloader = prefs[Keys.DISABLE_BUILTIN_DOWNLOADER] ?: false,
      multithreadTransferEnabled = prefs[Keys.MULTITHREAD_TRANSFER] ?: true,
      initialWindows = prefs[Keys.INITIAL_WINDOWS_CSV]?.split('|')?.filter { it.isNotBlank() } ?: listOf(root, "$root/Download"),
      recycleBinEnabled = prefs[Keys.RECYCLE_BIN_ENABLED] ?: true,
      gesturesEnabled = prefs[Keys.GESTURES_ENABLED] ?: false,
      gestureBindings = parseGestureBindings(prefs[Keys.GESTURE_BINDINGS_JSON].orEmpty()),
      senderNickname = prefs[Keys.SENDER_NICKNAME] ?: (Build.MODEL ?: "Nexo Device"),
      senderSavePath = prefs[Keys.SENDER_SAVE_PATH] ?: "$root/NexoShare",
      senderSameWifiMode = prefs[Keys.SENDER_SAME_WIFI_MODE] ?: true,
      autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
    )
  }

  suspend fun setShowHiddenFiles(show: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
  }

  suspend fun setSortBy(sortBy: SortBy) {
    context.dataStore.edit { it[Keys.SORT_BY] = sortBy.name }
  }

  suspend fun setSortAscending(asc: Boolean) {
    context.dataStore.edit { it[Keys.SORT_ASCENDING] = asc }
  }

  suspend fun setVaultBiometric(enabled: Boolean) {
    context.dataStore.edit {
      it[Keys.VAULT_BIOMETRIC] = enabled
      if (enabled) {
        it[Keys.VAULT_LOCK_METHOD] = VaultLockMethod.BIOMETRIC.name
      }
    }
  }

  /**
   * Configures the active Vault lock method exclusively from Settings.
   * For BIOMETRIC, keeps existing hash untouched or clears if requested;
   * for PIN, PATTERN, or PASSWORD, stores the salted SHA-256 digest of rawSecret.
   */
  suspend fun setVaultSecurityConfig(method: VaultLockMethod, rawSecret: String? = null) {
    context.dataStore.edit { prefs ->
      prefs[Keys.VAULT_LOCK_METHOD] = method.name
      prefs[Keys.VAULT_BIOMETRIC] = (method == VaultLockMethod.BIOMETRIC)
      if (method == VaultLockMethod.BIOMETRIC) {
        prefs[Keys.VAULT_SECRET_HASH] = ""
      } else if (!rawSecret.isNullOrBlank()) {
        prefs[Keys.VAULT_SECRET_HASH] = hashSecret(rawSecret)
      }
    }
  }

  fun verifyVaultSecret(rawSecret: String, storedHash: String): Boolean {
    if (storedHash.isBlank()) return false
    return hashSecret(rawSecret) == storedHash
  }

  suspend fun setDriveAccount(email: String?, name: String?) {
    context.dataStore.edit {
      if (email == null) it.remove(Keys.DRIVE_EMAIL) else it[Keys.DRIVE_EMAIL] = email
      if (name == null) it.remove(Keys.DRIVE_NAME) else it[Keys.DRIVE_NAME] = name
    }
  }

  suspend fun setWebServerPort(port: Int) {
    context.dataStore.edit { it[Keys.WEB_SERVER_PORT] = port.coerceIn(1024, 65535) }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun updateBooleanSetting(keyName: String, value: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[booleanPreferencesKey(keyName)] = value
    }
  }

  suspend fun updateStringSetting(keyName: String, value: String) {
    context.dataStore.edit { prefs ->
      prefs[stringPreferencesKey(keyName)] = value
    }
  }

  suspend fun setAppLanguage(lang: AppLanguageOption) = updateStringSetting("app_language", lang.name)
  suspend fun setHomeDirectory(path: String) = updateStringSetting("home_directory", path)
  suspend fun setBluetoothReceiveDir(path: String) = updateStringSetting("bluetooth_receive_dir", path)
  suspend fun setDownloadDirectory(path: String) = updateStringSetting("download_directory", path)
  suspend fun setSearchEngineMode(mode: SearchEngineMode) = updateStringSetting("search_engine_mode", mode.name)
  suspend fun setLayoutViewMode(mode: LayoutViewMode) = updateStringSetting("layout_view_mode", mode.name)
  suspend fun setAppBackupDirectory(path: String) = updateStringSetting("app_backup_directory", path)
  suspend fun setSenderNickname(nickname: String) = updateStringSetting("sender_nickname", nickname)
  suspend fun setSenderSavePath(path: String) = updateStringSetting("sender_save_path", path)
  suspend fun setSenderSameWifiMode(enabled: Boolean) = updateBooleanSetting("sender_same_wifi_mode", enabled)

  val defaultAppMappings: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
    prefs.asMap().entries
      .mapNotNull { (key, value) ->
        val name = key.name
        if (name.startsWith(DEFAULT_APP_PREFIX) && value is String && value.isNotBlank()) {
          name.removePrefix(DEFAULT_APP_PREFIX) to value
        } else {
          null
        }
      }.toMap()
  }

  suspend fun setDefaultAppForExtension(extension: String, handlerId: String) {
    val cleanExt = extension.trim().lowercase().removePrefix(".")
    if (cleanExt.isEmpty()) return
    context.dataStore.edit { prefs ->
      prefs[stringPreferencesKey("$DEFAULT_APP_PREFIX$cleanExt")] = handlerId
    }
  }

  suspend fun clearAllDefaultApps(): Int {
    var removed = 0
    context.dataStore.edit { prefs ->
      val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith(DEFAULT_APP_PREFIX) }
      removed = keysToRemove.size
      keysToRemove.forEach { prefs.remove(it) }
    }
    return removed
  }

  companion object {
    const val DEFAULT_APP_PREFIX = "default_app_ext_"
  }

  suspend fun setDrawerConfig(orderIds: List<String>, hiddenIds: Set<String>) {
    context.dataStore.edit {
      it[Keys.DRAWER_ORDER_CSV] = orderIds.joinToString(",")
      it[Keys.DRAWER_HIDDEN_CSV] = hiddenIds.joinToString(",")
    }
  }

  suspend fun setToolbarActions(actions: List<String>) {
    context.dataStore.edit { it[Keys.TOOLBAR_ACTIONS_CSV] = actions.joinToString(",") }
  }

  suspend fun setInitialWindows(paths: List<String>) {
    context.dataStore.edit { it[Keys.INITIAL_WINDOWS_CSV] = paths.joinToString("|") }
  }

  suspend fun setMasterPin(pinPlain: String) {
    val hash = if (pinPlain.isBlank()) "" else hashSecret(pinPlain)
    context.dataStore.edit { it[Keys.MASTER_PIN_HASH] = hash }
  }

  fun verifyPin(pinPlain: String, storedHash: String): Boolean {
    if (storedHash.isBlank()) return true
    return hashSecret(pinPlain) == storedHash
  }

  suspend fun saveGestureBinding(binding: GestureBinding) {
    val current = parseGestureBindings(context.dataStore.data.first()[Keys.GESTURE_BINDINGS_JSON].orEmpty())
      .filterNot { it.id == binding.id || it.gestureShape == binding.gestureShape } + binding
    context.dataStore.edit { it[Keys.GESTURE_BINDINGS_JSON] = encodeGestureBindings(current) }
  }

  suspend fun deleteGestureBinding(id: String) {
    val current = parseGestureBindings(context.dataStore.data.first()[Keys.GESTURE_BINDINGS_JSON].orEmpty())
      .filterNot { it.id == id }
    context.dataStore.edit { it[Keys.GESTURE_BINDINGS_JSON] = encodeGestureBindings(current) }
  }

  suspend fun resetToDefaults() {
    val email = context.dataStore.data.first()[Keys.DRIVE_EMAIL]
    val name = context.dataStore.data.first()[Keys.DRIVE_NAME]
    context.dataStore.edit { prefs ->
      prefs.clear()
      if (email != null) prefs[Keys.DRIVE_EMAIL] = email
      if (name != null) prefs[Keys.DRIVE_NAME] = name
    }
  }

  suspend fun exportSettingsJson(bookmarksJson: JSONArray, serversJson: JSONArray): String {
    val state = allSettings.first()
    return JSONObject().apply {
      put("app", "NexoFiles")
      put("version", 1)
      put("themeMode", state.themeMode.name)
      put("showHiddenFiles", state.showHiddenFiles)
      put("sortBy", state.sortBy.name)
      put("layoutViewMode", state.layoutViewMode.name)
      put("homeDirectory", state.homeDirectory)
      put("downloadDirectory", state.downloadDirectory)
      put("appBackupDirectory", state.appBackupDirectory)
      put("senderNickname", state.senderNickname)
      put("senderSavePath", state.senderSavePath)
      put("recycleBinEnabled", state.recycleBinEnabled)
      put("drawerOrderIds", JSONArray(state.drawerOrderIds))
      put("toolbarEnabledActions", JSONArray(state.toolbarEnabledActions))
      put("bookmarks", bookmarksJson)
      put("servers", serversJson)
    }.toString(2)
  }

  suspend fun importSettingsJson(jsonText: String): JSONObject {
    val obj = JSONObject(jsonText)
    context.dataStore.edit { prefs ->
      obj.optString("themeMode").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME_MODE] = it }
      if (obj.has("showHiddenFiles")) prefs[Keys.SHOW_HIDDEN_FILES] = obj.optBoolean("showHiddenFiles", true)
      obj.optString("sortBy").takeIf { it.isNotBlank() }?.let { prefs[Keys.SORT_BY] = it }
      obj.optString("layoutViewMode").takeIf { it.isNotBlank() }?.let { prefs[Keys.LAYOUT_VIEW_MODE] = it }
      obj.optString("homeDirectory").takeIf { it.isNotBlank() }?.let { prefs[Keys.HOME_DIRECTORY] = it }
      obj.optString("downloadDirectory").takeIf { it.isNotBlank() }?.let { prefs[Keys.DOWNLOAD_DIRECTORY] = it }
      obj.optString("appBackupDirectory").takeIf { it.isNotBlank() }?.let { prefs[Keys.APP_BACKUP_DIRECTORY] = it }
      obj.optString("senderNickname").takeIf { it.isNotBlank() }?.let { prefs[Keys.SENDER_NICKNAME] = it }
      obj.optString("senderSavePath").takeIf { it.isNotBlank() }?.let { prefs[Keys.SENDER_SAVE_PATH] = it }
      if (obj.has("recycleBinEnabled")) prefs[Keys.RECYCLE_BIN_ENABLED] = obj.optBoolean("recycleBinEnabled", true)
      obj.optJSONArray("drawerOrderIds")?.let { arr ->
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        if (list.isNotEmpty()) prefs[Keys.DRAWER_ORDER_CSV] = list.joinToString(",")
      }
      obj.optJSONArray("toolbarEnabledActions")?.let { arr ->
        val list = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        prefs[Keys.TOOLBAR_ACTIONS_CSV] = list.joinToString(",")
      }
    }
    return obj
  }

  private fun hashSecret(secret: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(("nexofiles_salt_$secret").toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun parseGestureBindings(json: String): List<GestureBinding> {
    if (json.isBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      (0 until arr.length()).map { idx ->
        val o = arr.getJSONObject(idx)
        GestureBinding(
          id = o.optString("id"),
          gestureShape = o.optString("shape"),
          label = o.optString("label"),
          targetRouteOrPath = o.optString("target"),
        )
      }
    }.getOrDefault(emptyList())
  }

  private fun encodeGestureBindings(list: List<GestureBinding>): String {
    val arr = JSONArray()
    list.forEach { b ->
      arr.put(
        JSONObject().apply {
          put("id", b.id)
          put("shape", b.gestureShape)
          put("label", b.label)
          put("target", b.targetRouteOrPath)
        }
      )
    }
    return arr.toString()
  }
}
