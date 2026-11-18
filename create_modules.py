import os, re

base = os.path.expanduser("~/nexofiles_build/app/src/main/java/com/example")
comp_dir = os.path.join(base, "ui/components")
os.makedirs(comp_dir, exist_ok=True)

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

// 1. ESCALA DE TAMAÑO GLOBAL Y GESTOS
object NexoUiScaleManager {
    private const val PREFS = "nexo_ui_scale_prefs"
    private const val KEY_SCALE = "ui_scale_factor"
    var scale by mutableFloatStateOf(1.0f)
    var gesturesEnabled by mutableStateOf(true)

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scale = sp.getFloat(KEY_SCALE, 0.85f).coerceIn(0.65f, 1.25f)
        gesturesEnabled = sp.getBoolean("gestures_enabled", true)
    }

    fun updateScale(context: Context, newScale: Float) {
        val clamped = newScale.coerceIn(0.65f, 1.25f)
        scale = clamped
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SCALE, clamped).apply()
    }
}

fun Modifier.nexoPinchToScale(context: Context): Modifier = this.pointerInput(Unit) {
    if (!NexoUiScaleManager.gesturesEnabled) return@pointerInput
    detectTransformGestures { _, _, zoom, _ ->
        if (zoom != 1.0f) {
            NexoUiScaleManager.updateScale(context, NexoUiScaleManager.scale * zoom)
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

    fun openOrSwitchTab(path: String) {
        val cleanTitle = File(path).name.ifEmpty { "HOME" }
        val existing = tabs.find { it.path == path }
        if (existing != null) {
            activeTabId = existing.id
        } else {
            val newId = "tab_" + System.currentTimeMillis()
            tabs.add(NexoFolderTab(id = newId, title = cleanTitle.take(16), path = path, isClosable = true))
            activeTabId = newId
        }
    }

    fun updateActiveTabPath(path: String) {
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            val current = tabs[idx]
            val title = if (!current.isClosable && path == "/storage/emulated/0") "HOME" else File(path).name.ifEmpty { "ROOT" }
            tabs[idx] = current.copy(path = path, title = title.take(16))
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
            .background(Color(0xFF0E121A))
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar pestaña",
                            tint = Color(0xFFFF4D6D),
                            modifier = Modifier
                                .size(14.dp)
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
                val title = File(currentPath).name.ifEmpty { "HOME" }.take(16)
                NexoTabsState.tabs.add(NexoFolderTab(id = newId, title = title, path = currentPath, isClosable = true))
                NexoTabsState.activeTabId = newId
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nueva pestaña", tint = Color(0xFF00FF66), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("TAB", color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 3. MOTOR DE MINIATURAS GLOBALES (IMAGEN, VIDEO, PDF, CBZ)
object NexoThumbCache {
    private val cache = object : LruCache<String, Bitmap>(60) {}
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
                while (opts.outWidth / sample > 180 || opts.outHeight / sample > 180) sample *= 2
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
                                val b = Bitmap.createBitmap(160, 210, Bitmap.Config.ARGB_8888)
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
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
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
    size: Dp = 40.dp,
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

// 4. LECTOR NATIVO DE CÓMICS (.CBZ / .CBR)
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
                errorMsg = "Archivo CBR/RAR comprimido con cabecera propietaria o archivo dañado (${e.localizedMessage ?: "Error"})."
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
                    Text("VISOR DE CÓMIC NEXO", color = Color(0xFF00FF66), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

// 5. DIÁLOGO DE ELIMINACIÓN SELECTIVA EN LA BÓVEDA (3 OPCIONES)
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
            Text("ELIMINACIÓN TÁCTICA DE BÓVEDA", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selecciona dónde deseas eliminar \"$fileName\":", color = Color.White, fontSize = 13.sp)
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
'''

with open(os.path.join(comp_dir, "NexoAdvancedModules.kt"), "w") as f:
    f.write(module_code)
print("OK: NexoAdvancedModules.kt creado exitosamente.")

def show_lines(rel_path, pattern, radius=12):
    full = os.path.join(base, rel_path)
    lines = open(full).read().splitlines()
    print(f"\n--- {rel_path} ({len(lines)}L) ---")
    for i, line in enumerate(lines):
        if re.search(pattern, line):
            start = max(0, i - 2)
            end = min(len(lines), i + radius)
            for j in range(start, end):
                print(f"{j+1}: {lines[j]}")
            print("...")

show_lines("MainActivity.kt", r"fun executeInternalViewer|fun openFileSmart", 18)
show_lines("ui/screens/FilesScreen.kt", r"fun FilesScreen\(|BreadcrumbBar\(|fun FileGridItemCard|fun FileListItemCard", 10)
show_lines("ui/screens/VaultScreen.kt", r"fun VaultScreen\(|deleteFromVault|VaultDriveBackupModal\(", 12)
show_lines("ui/screens/SettingsScreen.kt", r"fun SettingsScreen\(", 15)
