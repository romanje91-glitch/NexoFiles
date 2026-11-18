package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object NexoUiScaleManager {
    private const val PREFS = "nexo_ui_scale_prefs"
    private const val KEY_SCALE = "ui_scale_factor"
    var scale by mutableFloatStateOf(0.85f)
    var gesturesEnabled by mutableStateOf(true)

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scale = sp.getFloat(KEY_SCALE, 0.85f).coerceIn(0.65f, 1.20f)
        gesturesEnabled = sp.getBoolean("gestures_enabled", true)
    }

    fun updateScale(context: Context, newScale: Float) {
        val clamped = newScale.coerceIn(0.65f, 1.20f)
        scale = clamped
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_SCALE, clamped).apply()
    }

    fun toggleGestures(context: Context, enabled: Boolean) {
        gesturesEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("gestures_enabled", enabled).apply()
    }
}

@Composable
fun NexoUiScaleSettingsCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NexoUiScaleManager.init(context) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141824),
        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ESCALA GLOBAL DE INTERFAZ Y GESTOS", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("70%" to 0.70f, "85%" to 0.85f, "100%" to 1.0f).forEach { (label, target) ->
                    val active = kotlin.math.abs(NexoUiScaleManager.scale - target) < 0.05f
                    OutlinedButton(
                        onClick = { NexoUiScaleManager.updateScale(context, target) },
                        border = BorderStroke(1.dp, if (active) Color(0xFF00FF66) else Color(0xFF3A415A)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (active) Color(0xFF00FF66) else Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Gesto pellizcar en listas", color = Color.White, fontSize = 12.sp)
                Switch(checked = NexoUiScaleManager.gesturesEnabled, onCheckedChange = { NexoUiScaleManager.toggleGestures(context, it) })
            }
        }
    }
}

data class NexoFolderTab(val id: String, val title: String, val path: String, val isClosable: Boolean = true)

object NexoTabsState {
    val tabs = mutableStateListOf(NexoFolderTab(id = "home", title = "HOME", path = "/storage/emulated/0", isClosable = false))
    var activeTabId by mutableStateOf("home")

    fun updateActiveTabPath(path: String) {
        if (path.isBlank()) return
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            val current = tabs[idx]
            val title = if (!current.isClosable && path == "/storage/emulated/0") "HOME" else File(path).name.ifEmpty { "ROOT" }
            tabs[idx] = current.copy(path = path, title = title.take(15))
        }
    }

    fun closeTab(tab: NexoFolderTab, onNavigate: (String) -> Unit) {
        if (!tab.isClosable) return
        val idx = tabs.indexOf(tab)
        if (idx >= 0) {
            tabs.removeAt(idx)
            if (activeTabId == tab.id) {
                val fallback = tabs.getOrNull((idx - 1).coerceAtLeast(0)) ?: tabs.first()
                activeTabId = fallback.id
                onNavigate(fallback.path)
            }
        }
    }
}

@Composable
fun NexoExplorerTabBar(currentPath: String, onNavigatePath: (String) -> Unit) {
    LaunchedEffect(currentPath) { NexoTabsState.updateActiveTabPath(currentPath) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF0D111A)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NexoTabsState.tabs.forEach { tab ->
            val selected = tab.id == NexoTabsState.activeTabId
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) Color(0xFF231942) else Color(0xFF161B26),
                border = BorderStroke(1.dp, if (selected) Color(0xFF00FF66) else Color(0xFF2E364F)),
                modifier = Modifier.clickable { NexoTabsState.activeTabId = tab.id; onNavigatePath(tab.path) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.title, color = if (selected) Color(0xFF00FF66) else Color(0xFFB0B8C8), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tab.isClosable) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color(0xFFFF4D6D), modifier = Modifier.size(13.dp).clickable { NexoTabsState.closeTab(tab, onNavigatePath) })
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF161B26),
            border = BorderStroke(1.dp, Color(0xFF9D4EDD)),
            modifier = Modifier.clickable {
                val newId = "tab_" + System.currentTimeMillis()
                val title = File(currentPath).name.ifEmpty { "HOME" }.take(15)
                NexoTabsState.tabs.add(NexoFolderTab(id = newId, title = title, path = currentPath, isClosable = true))
                NexoTabsState.activeTabId = newId
            }
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = "Nueva", tint = Color(0xFF00FF66), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("TAB", color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

object NexoThumbCache {
    private val cache = object : LruCache<String, Bitmap>(64) {}
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
}

suspend fun loadNexoThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || !file.isFile) return@withContext null
    val key = "${file.absolutePath}_${file.lastModified()}"
    NexoThumbCache.get(key)?.let { return@withContext it }
    val ext = file.extension.lowercase()
    val bmp: Bitmap? = try {
        when {
            ext in listOf("jpg", "jpeg", "png", "webp", "bmp") -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var sample = 1
                while (opts.outWidth / sample > 160 || opts.outHeight / sample > 160) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            ext in listOf("mp4", "mkv", "webm", "mov", "avi", "3gp") -> {
                val retriever = MediaMetadataRetriever()
                try { retriever.setDataSource(file.absolutePath); retriever.getFrameAtTime(1_000_000) } finally { retriever.release() }
            }
            ext == "pdf" -> {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { r ->
                        if (r.pageCount > 0) r.openPage(0).use { page ->
                            Bitmap.createBitmap(140, 180, Bitmap.Config.ARGB_8888).also { b ->
                                b.eraseColor(android.graphics.Color.WHITE)
                                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        } else null
                    }
                }
            }
            ext == "cbz" -> {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } }
                        .sortedBy { it.name }.firstOrNull()?.let { entry ->
                            zip.getInputStream(entry).use { input ->
                                val bytes = input.readBytes()
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
                            }
                        }
                }
            }
            else -> null
        }
    } catch (_: Throwable) { null }
    if (bmp != null) NexoThumbCache.put(key, bmp)
    bmp
}

@Composable
fun NexoFileThumbnailOrFallback(file: File, size: Dp = 38.dp, fallback: @Composable () -> Unit) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    val ext = remember(file.name) { file.extension.lowercase() }
    val ok = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "mp4", "mkv", "webm", "mov", "avi", "3gp", "pdf", "cbz")
    LaunchedEffect(file.absolutePath) { if (ok) bitmap = loadNexoThumbnail(file) }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)).border(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
    } else fallback()
}

data class CloudOnlyVaultGhost(val id: String, val name: String, val sizeBytes: Long)

object NexoHybridVaultRegistry {
    private const val PREFS = "nexo_hybrid_vault_prefs"
    private const val KEY_GHOSTS = "cloud_only_ghosts"
    val cloudOnlyItems = mutableStateListOf<CloudOnlyVaultGhost>()
    var autoBackupOnImport by mutableStateOf(true)
    var lastLocalCount by mutableIntStateOf(0)
    var lastLocalBytes by mutableLongStateOf(0L)
    var lastCloudCount by mutableIntStateOf(0)
    var lastCloudBytes by mutableLongStateOf(0L)

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoBackupOnImport = sp.getBoolean("auto_backup", true)
        val raw = sp.getString(KEY_GHOSTS, "[]") ?: "[]"
        cloudOnlyItems.clear()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cloudOnlyItems.add(CloudOnlyVaultGhost(o.getString("id"), o.getString("name"), o.optLong("sizeBytes", 0L)))
            }
        } catch (_: Throwable) {}
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        cloudOnlyItems.forEach { g -> arr.put(JSONObject().apply { put("id", g.id); put("name", g.name); put("sizeBytes", g.sizeBytes) }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GHOSTS, arr.toString()).apply()
    }

    fun markSavedInCloudOnly(context: Context, id: String, name: String, sizeBytes: Long) {
        if (cloudOnlyItems.none { it.id == id || it.name == name }) {
            cloudOnlyItems.add(CloudOnlyVaultGhost(id, name, sizeBytes))
            save(context)
        }
    }

    fun removeGhost(context: Context, idOrName: String) {
        cloudOnlyItems.removeAll { it.id == idOrName || it.name == idOrName }
        save(context)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }
}

enum class NexoVaultDeleteOption { LOCAL_ONLY, CLOUD_ONLY, BOTH }

@Composable
fun NexoVaultSelectiveDeleteDialog(fileName: String, onDismiss: () -> Unit, onSelectOption: (NexoVaultDeleteOption) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141824),
        title = { Text("ELIMINAR ARCHIVO", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileName, color = Color.White, fontSize = 13.sp)
                Button(onClick = { onSelectOption(NexoVaultDeleteOption.LOCAL_ONLY) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF231942)), modifier = Modifier.fillMaxWidth()) { Text("1. Solo del telefono", color = Color(0xFF00FF66), fontSize = 12.sp) }
                Button(onClick = { onSelectOption(NexoVaultDeleteOption.CLOUD_ONLY) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2A44)), modifier = Modifier.fillMaxWidth()) { Text("2. Solo de Google Drive", color = Color(0xFF4EA8DE), fontSize = 12.sp) }
                Button(onClick = { onSelectOption(NexoVaultDeleteOption.BOTH) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A040F)), modifier = Modifier.fillMaxWidth()) { Text("3. Eliminar de ambos lados", color = Color.White, fontSize = 12.sp) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR", color = Color(0xFFB0B8C8)) } }
    )
}

@Composable
fun NexoVaultHybridSummaryPanel(localCount: Int, localBytes: Long, cloudCount: Int, cloudBytes: Long, onOpenDriveBackupModal: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(localCount, localBytes, cloudCount) { NexoHybridVaultRegistry.load(context) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF121722),
        border = BorderStroke(1.dp, Color(0xFF9D4EDD).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ESTADO HIBRIDO DE BOVEDA (LOCAL VS DRIVE)", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1A2130), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("LOCAL", color = Color(0xFFB0B8C8), fontSize = 10.sp)
                        Text("$localCount archivos (${NexoHybridVaultRegistry.formatBytes(localBytes)})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1A2130), modifier = Modifier.weight(1f).clickable { onOpenDriveBackupModal() }) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("GOOGLE DRIVE", color = Color(0xFF4EA8DE), fontSize = 10.sp)
                        val totalCloud = maxOf(cloudCount, NexoHybridVaultRegistry.cloudOnlyItems.size)
                        Text("$totalCloud respaldos", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
