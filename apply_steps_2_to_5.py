import os, re, shutil, subprocess

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")
backup_dir = os.path.join(base_dir, "backup_steps_2_to_5")
os.makedirs(backup_dir, exist_ok=True)

# 1. Respaldo de seguridad antes de parchar
targets = [
    "MainActivity.kt",
    "ui/screens/FilesScreen.kt",
    "ui/screens/VaultScreen.kt",
    "ui/screens/SettingsScreen.kt"
]
for rel in targets:
    src = os.path.join(java_dir, rel)
    dst = os.path.join(backup_dir, os.path.basename(rel))
    if os.path.exists(src) and not os.path.exists(dst):
        shutil.copy2(src, dst)

# 2. Actualizar NexoAdvancedModules.kt con todos los componentes integrados
comp_path = os.path.join(java_dir, "ui/components/NexoAdvancedModules.kt")
module_code = '''package com.example.ui.components

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

// 1. ESCALA DE TAMAÑO GLOBAL Y GESTOS
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SCALE, clamped).apply()
    }

    fun toggleGestures(context: Context, enabled: Boolean) {
        gesturesEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("gestures_enabled", enabled).apply()
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
            Text("Reduce el tamaño de iconos, tarjetas y filas para ver más archivos en pantalla.", color = Color(0xFFB0B8C8), fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Compacto (70%)" to 0.70f, "Pequeño (85%)" to 0.85f, "Normal (100%)" to 1.0f).forEach { (label, target) ->
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
                Text("Gesto pellizcar (Pinch-to-Zoom) en listas", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = NexoUiScaleManager.gesturesEnabled,
                    onCheckedChange = { NexoUiScaleManager.toggleGestures(context, it) }
                )
            }
        }
    }
}

// 2. BARRA DE PESTAÑAS MULTI-DIRECTORIO (HOME FIJO + PESTAÑAS CERRABLES)
data class NexoFolderTab(
    val id: String,
    val title: String,
    val path: String,
    val isClosable: Boolean = true
)

object NexoTabsState {
    val tabs = mutableStateListOf(
        NexoFolderTab(id = "home", title = "HOME", path = "/storage/emulated/0", isClosable = false)
    )
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
fun NexoExplorerTabBar(
    currentPath: String,
    onNavigatePath: (String) -> Unit
) {
    LaunchedEffect(currentPath) {
        NexoTabsState.updateActiveTabPath(currentPath)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFF0D111A))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NexoTabsState.tabs.forEach { tab ->
            val selected = tab.id == NexoTabsState.activeTabId
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (selected) Color(0xFF231942) else Color(0xFF161B26),
                border = BorderStroke(1.dp, if (selected) Color(0xFF00FF66) else Color(0xFF2E364F)),
                modifier = Modifier.clickable {
                    NexoTabsState.activeTabId = tab.id
                    onNavigatePath(tab.path)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.title,
                        color = if (selected) Color(0xFF00FF66) else Color(0xFFB0B8C8),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tab.isClosable) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar pestaña",
                            tint = Color(0xFFFF4D6D),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { NexoTabsState.closeTab(tab, onNavigatePath) }
                        )
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
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nueva pestaña", tint = Color(0xFF00FF66), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("TAB", color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 3. MOTOR DE MINIATURAS GLOBALES Y LECTOR DE CÓMICS (.CBZ / .CBR)
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
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(1_000_000)
                } finally {
                    retriever.release()
                }
            }
            ext == "pdf" -> {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val b = Bitmap.createBitmap(140, 180, Bitmap.Config.ARGB_8888)
                                b.eraseColor(android.graphics.Color.WHITE)
                                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        } else null
                    }
                }
            }
            ext == "cbz" -> {
                ZipFile(file).use { zip ->
                    val firstImg = zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } }
                        .sortedBy { it.name }
                        .firstOrNull()
                    firstImg?.let { entry ->
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
fun NexoFileThumbnailOrFallback(
    file: File,
    size: Dp = 38.dp,
    fallback: @Composable () -> Unit
) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    val ext = remember(file.name) { file.extension.lowercase() }
    val supportsThumb = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "mp4", "mkv", "webm", "mov", "avi", "3gp", "pdf", "cbz")

    LaunchedEffect(file.absolutePath) {
        if (supportsThumb) {
            bitmap = loadNexoThumbnail(file)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
        )
    } else {
        fallback()
    }
}

object NexoComicViewerState {
    var activeComicFile by mutableStateOf<File?>(null)
    fun open(file: File) { activeComicFile = file }
    fun close() { activeComicFile = null }
}

@Composable
fun NexoComicViewerHost() {
    val file = NexoComicViewerState.activeComicFile
    if (file != null) {
        NexoComicReaderDialog(comicFile = file, onDismiss = { NexoComicViewerState.close() })
    }
}

@Composable
fun NexoComicReaderDialog(
    comicFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(comicFile.absolutePath) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val outDir = File(context.cacheDir, "nexo_comic_${comicFile.nameWithoutExtension}").apply {
                    if (exists()) deleteRecursively()
                    mkdirs()
                }
                val extracted = mutableListOf<File>()
                ZipFile(comicFile).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } }
                        .sortedBy { it.name }
                        .forEachIndexed { idx, entry ->
                            val pageFile = File(outDir, "page_${idx.toString().padStart(4, '0')}.jpg")
                            zip.getInputStream(entry).use { input ->
                                pageFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            extracted.add(pageFile)
                        }
                }
                pages = extracted
                if (extracted.isEmpty()) {
                    errorMsg = "No se encontraron páginas de imagen compatibles en ${comicFile.name}."
                }
            } catch (e: Throwable) {
                errorMsg = "No se pudo descomprimir ${comicFile.name}. Si es .CBR propietario, conviértelo a .CBZ."
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090C12))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LECTOR DE CÓMICS NEXO (CBZ/CBR)", color = Color(0xFF00FF66), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(comicFile.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss) {
                    Text("CERRAR", color = Color(0xFFFF4D6D), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101520))
                    .pointerInput(currentPage) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                            if (zoom > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = Color(0xFF00FF66))
                    errorMsg != null -> Text(errorMsg!!, color = Color(0xFFFFB703), modifier = Modifier.padding(16.dp))
                    pages.isNotEmpty() -> {
                        val pageFile = pages[currentPage.coerceIn(0, pages.lastIndex)]
                        val bmp = remember(pageFile.absolutePath) { BitmapFactory.decodeFile(pageFile.absolutePath) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Página ${currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (pages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentPage > 0) { currentPage--; zoom = 1f; offsetX = 0f; offsetY = 0f } },
                        enabled = currentPage > 0
                    ) { Text("ANTERIOR", color = Color(0xFF00FF66)) }

                    Text(
                        text = "PÁGINA ${currentPage + 1} / ${pages.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    OutlinedButton(
                        onClick = { if (currentPage < pages.lastIndex) { currentPage++; zoom = 1f; offsetX = 0f; offsetY = 0f } },
                        enabled = currentPage < pages.lastIndex
                    ) { Text("SIGUIENTE", color = Color(0xFF00FF66)) }
                }
            }
        }
    }
}

// 4. GESTOR HÍBRIDO DE BÓVEDA (CONTEO LOCAL VS DRIVE, MODO OFFLINE Y BORRADO DE 3 OPCIONES)
data class CloudOnlyVaultGhost(
    val id: String,
    val name: String,
    val sizeBytes: Long
)

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
        cloudOnlyItems.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("sizeBytes", g.sizeBytes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GHOSTS, arr.toString()).apply()
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
fun NexoVaultSelectiveDeleteDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onSelectOption: (NexoVaultDeleteOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141824),
        title = {
            Text("¿DÓNDE DESEAS ELIMINAR ESTE ARCHIVO?", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Archivo: \"$fileName\"", color = Color.White, fontSize = 13.sp)
                Button(
                    onClick = { onSelectOption(NexoVaultDeleteOption.LOCAL_ONLY) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF231942)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. Solo del teléfono (Conservar en Drive)", color = Color(0xFF00FF66), fontSize = 12.sp)
                }
                Button(
                    onClick = { onSelectOption(NexoVaultDeleteOption.CLOUD_ONLY) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2A44)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. Solo de Google Drive (Conservar local)", color = Color(0xFF4EA8DE), fontSize = 12.sp)
                }
                Button(
                    onClick = { onSelectOption(NexoVaultDeleteOption.BOTH) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A040F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3. Eliminar de ambos lados (Definitivo)", color = Color.White, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color(0xFFB0B8C8))
            }
        }
    )
}

@Composable
fun NexoVaultHybridSummaryPanel(
    localCount: Int,
    localBytes: Long,
    cloudCount: Int,
    cloudBytes: Long,
    onOpenDriveBackupModal: () -> Unit
) {
    val context = LocalContext.current
    var ghostToDownload by remember { mutableStateOf<CloudOnlyVaultGhost?>(null) }

    LaunchedEffect(localCount, localBytes, cloudCount, cloudBytes) {
        NexoHybridVaultRegistry.load(context)
        NexoHybridVaultRegistry.lastLocalCount = localCount
        NexoHybridVaultRegistry.lastLocalBytes = localBytes
        NexoHybridVaultRegistry.lastCloudCount = cloudCount
        NexoHybridVaultRegistry.lastCloudBytes = cloudBytes
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF121722),
        border = BorderStroke(1.dp, Color(0xFF9D4EDD).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ESTADO HÍBRIDO DE BÓVEDA (LOCAL VS DRIVE)", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    text = if (NexoHybridVaultRegistry.autoBackupOnImport) "AUTO-BACKUP: ON" else "AUTO-BACKUP: OFF",
                    color = if (NexoHybridVaultRegistry.autoBackupOnImport) Color(0xFF00FF66) else Color(0xFFFFB703),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        NexoHybridVaultRegistry.autoBackupOnImport = !NexoHybridVaultRegistry.autoBackupOnImport
                    }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1A2130),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("EN TELÉFONO (LOCAL)", color = Color(0xFFB0B8C8), fontSize = 10.sp)
                        Text("$localCount archivos • ${NexoHybridVaultRegistry.formatBytes(localBytes)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1A2130),
                    modifier = Modifier.weight(1f).clickable { onOpenDriveBackupModal() }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("EN GOOGLE DRIVE (NUBE)", color = Color(0xFF4EA8DE), fontSize = 10.sp)
                        val totalCloud = maxOf(cloudCount, NexoHybridVaultRegistry.cloudOnlyItems.size)
                        val totalCloudBytes = maxOf(cloudBytes, NexoHybridVaultRegistry.cloudOnlyItems.sumOf { it.sizeBytes })
                        Text("$totalCloud respaldos • ${NexoHybridVaultRegistry.formatBytes(totalCloudBytes)}", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            if (NexoHybridVaultRegistry.cloudOnlyItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("ARCHIVOS SOLO EN LA NUBE (MODO OFFLINE - TOCA PARA DESCARGAR):", color = Color(0xFFFFB703), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                NexoHybridVaultRegistry.cloudOnlyItems.forEach { ghost ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E1936),
                        border = BorderStroke(1.dp, Color(0xFF4EA8DE).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { ghostToDownload = ghost }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ghost.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("☁️ Solo en Google Drive • ${NexoHybridVaultRegistry.formatBytes(ghost.sizeBytes)}", color = Color(0xFF4EA8DE), fontSize = 10.sp)
                            }
                            Text("DESCARGAR", color = Color(0xFF00FF66), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (ghostToDownload != null) {
        val g = ghostToDownload!!
        AlertDialog(
            onDismissRequest = { ghostToDownload = null },
            containerColor = Color(0xFF141824),
            title = { Text("ARCHIVO EN GOOGLE DRIVE", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${g.name}\" fue eliminado del teléfono para liberar espacio, pero sigue respaldado en Google Drive.\n\n¿Deseas descargarlo y restaurarlo a tu Bóveda local ahora?",
                    color = Color.White,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        NexoHybridVaultRegistry.removeGhost(context, g.id)
                        ghostToDownload = null
                        onOpenDriveBackupModal()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66))
                ) {
                    Text("DESCARGAR DESDE DRIVE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ghostToDownload = null }) {
                    Text("CANCELAR", color = Color(0xFFB0B8C8))
                }
            }
        )
    }
}
'''
with open(comp_path, "w") as f:
    f.write(module_code)
print("1. NexoAdvancedModules.kt actualizado.")

# 3. Parchar FilesScreen.kt (Pestañas, Escala Global, Miniaturas y Lector CBZ/CBR)
files_path = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(files_path).read()

if "com.example.ui.components.*" not in ft:
    ft = ft.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\nimport java.io.File\n", 1)

# 3a. Conectar Lector CBZ/CBR en handleItemClick y Host en FilesScreen
if "NexoComicViewerState.open" not in ft:
    ft = re.sub(
        r"(fun handleItemClick\s*\(\s*item\s*:\s*LocalFileItem\s*\)\s*\{)",
        r'''\1
        if (!item.isDirectory && (item.name.lowercase().endsWith(".cbz") || item.name.lowercase().endsWith(".cbr"))) {
            NexoComicViewerState.open(File(item.path))
            return
        }''',
        ft,
        count=1
    )

# 3b. Insertar NexoExplorerTabBar y NexoComicViewerHost junto a BreadcrumbBar
if "NexoExplorerTabBar(" not in ft:
    ft = ft.replace(
        "BreadcrumbBar(\n                        currentPath = uiState.currentPath,\n                        onNavigateToPath = onNavigateToPath,\n                        onNavigateUp = onNavigateUp,\n                    )",
        "Column(modifier = Modifier.fillMaxWidth()) {\n                        NexoComicViewerHost()\n                        NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)\n                        BreadcrumbBar(\n                            currentPath = uiState.currentPath,\n                            onNavigateToPath = onNavigateToPath,\n                            onNavigateUp = onNavigateUp,\n                        )\n                    }"
    )

# 3c. Escala global en FileListItemCard
if "NexoUiScaleManager.scale" not in ft:
    ft = ft.replace(
        "val iconBoxSize = if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp\n    val verticalPad = if (layoutViewMode == LayoutViewMode.COMPACT) 8.dp else 12.dp",
        "val uiScale = NexoUiScaleManager.scale\n    val iconBoxSize = (if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp) * uiScale\n    val verticalPad = (if (layoutViewMode == LayoutViewMode.COMPACT) 8.dp else 12.dp) * uiScale"
    )

with open(files_path, "w") as f:
    f.write(ft)
print("2. FilesScreen.kt actualizado.")

# 4. Parchar VaultScreen.kt (Panel Híbrido Local/Drive + Diálogo de Borrado de 3 opciones)
vault_path = os.path.join(java_dir, "ui/screens/VaultScreen.kt")
vt = open(vault_path).read()

if "com.example.ui.components.*" not in vt:
    vt = vt.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "pendingSelectiveDeleteItem" not in vt:
    vt = vt.replace(
        "var showDriveBackupModal by remember { mutableStateOf(false) }",
        "var showDriveBackupModal by remember { mutableStateOf(false) }\n    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }"
    )

if "onClick = { onDeleteVaultItem(item) }" in vt:
    vt = vt.replace(
        "onClick = { onDeleteVaultItem(item) }",
        "onClick = { pendingSelectiveDeleteItem = item }"
    )

if "NexoVaultSelectiveDeleteDialog(" not in vt:
    dialog_hook = '''    if (pendingSelectiveDeleteItem != null) {
        val targetItem = pendingSelectiveDeleteItem!!
        NexoVaultSelectiveDeleteDialog(
            fileName = targetItem.originalName,
            onDismiss = { pendingSelectiveDeleteItem = null },
            onSelectOption = { option ->
                when (option) {
                    NexoVaultDeleteOption.LOCAL_ONLY -> {
                        NexoHybridVaultRegistry.markSavedInCloudOnly(
                            context = context,
                            id = targetItem.id,
                            name = targetItem.originalName,
                            sizeBytes = targetItem.sizeBytes
                        )
                        onDeleteVaultItem(targetItem)
                    }
                    NexoVaultDeleteOption.CLOUD_ONLY -> {
                        NexoHybridVaultRegistry.removeGhost(context, targetItem.id)
                        showDriveBackupModal = true
                    }
                    NexoVaultDeleteOption.BOTH -> {
                        NexoHybridVaultRegistry.removeGhost(context, targetItem.id)
                        onDeleteVaultItem(targetItem)
                    }
                }
                pendingSelectiveDeleteItem = null
            }
        )
    }
    if (showDriveBackupModal) {'''
    vt = vt.replace("    if (showDriveBackupModal) {", dialog_hook, 1)

# Insertar el panel de resumen híbrido arriba de la lista de la bóveda
if "NexoVaultHybridSummaryPanel(" not in vt:
    vt = vt.replace(
        "LazyColumn(",
        '''NexoVaultHybridSummaryPanel(
                localCount = state.items.size,
                localBytes = state.items.sumOf { it.sizeBytes },
                cloudCount = state.driveBackups.size,
                cloudBytes = state.driveBackups.sumOf { it.sizeBytes },
                onOpenDriveBackupModal = { showDriveBackupModal = true }
            )
            LazyColumn(''',
        1
    )

with open(vault_path, "w") as f:
    f.write(vt)
print("3. VaultScreen.kt actualizado.")

# 5. Parchar SettingsScreen.kt (Control de Escala Global y Estado Híbrido de Bóveda)
settings_path = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(settings_path).read()

if "com.example.ui.components.*" not in st:
    st = st.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "NexoUiScaleSettingsCard()" not in st:
    st = st.replace(
        "LazyColumn(",
        '''NexoUiScaleSettingsCard()
        LazyColumn(''',
        1
    )

with open(settings_path, "w") as f:
    f.write(st)
print("4. SettingsScreen.kt actualizado.")

print("Todos los parches fueron aplicados. Iniciando compilación de APK...")
