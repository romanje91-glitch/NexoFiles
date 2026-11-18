import os, re, glob, shutil

print("=== Iniciando parche maestro NexoFiles v1.2.0 (OTA + 6 Fixes) ===")

java_dir = "app/src/main/java"
kt_files = []
for root, _, files in os.walk(java_dir):
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(root, f))

def find_file(name):
    for p in kt_files:
        if os.path.basename(p) == name:
            return p
    return None

main_act_path = find_file("MainActivity.kt")
files_scr_path = find_file("FilesScreen.kt")
settings_scr_path = find_file("SettingsScreen.kt")
vault_scr_path = find_file("VaultScreen.kt")
home_scr_path = find_file("HomeScreen.kt")

os.makedirs("backup_v110", exist_ok=True)
for p in kt_files:
    shutil.copy2(p, os.path.join("backup_v110", os.path.basename(p)))

pkg_name = "com.example"
if main_act_path:
    with open(main_act_path, "r", encoding="utf-8") as f:
        m = re.search(r"^\s*package\s+([\w\.]+)", f.read(), re.M)
        if m:
            pkg_name = m.group(1)

base_pkg_dir = os.path.dirname(main_act_path) if main_act_path else "app/src/main/java/com/example"

# -------------------------------------------------------------------------
# 1. CREAR PUENTE GLOBAL (NexoNavBridge) + NAVEGADOR WEB SENCILLO + RULETA EVA + VAULT MIRROR
# -------------------------------------------------------------------------
bridge_path = os.path.join(base_pkg_dir, "NexoMasterBridge.kt")
bridge_code = f"""package {pkg_name}

import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NexoNavBridge {{
    var isMediaViewerOpen by mutableStateOf(false)
    var mediaImmersiveMode by mutableStateOf(false)
    var onCloseMediaViewer: (() -> Unit)? = null

    var currentExplorerPath by mutableStateOf("/storage/emulated/0")
    var canGoBackInExplorer by mutableStateOf(false)
    var onExplorerBack: (() -> Unit)? = null

    var showLandscapeHeaders by mutableStateOf(false)
    var showWebBrowser by mutableStateOf(false)
    var webBrowserInitialUrl by mutableStateOf("https://www.google.com")

    fun triggerBack(): Boolean {{
        if (showWebBrowser) {{
            showWebBrowser = false
            return true
        }}
        if (isMediaViewerOpen) {{
            onCloseMediaViewer?.invoke()
            isMediaViewerOpen = false
            mediaImmersiveMode = false
            return true
        }}
        if (canGoBackInExplorer && onExplorerBack != null) {{
            onExplorerBack?.invoke()
            return true
        }}
        return false
    }}
}}

object NexoVaultDriveMirror {{
    fun getMirrorDir(context: Context): File {{
        val extDir = File(context.getExternalFilesDir(null), "NexoVault_Encrypted")
        if (!extDir.exists()) extDir.mkdirs()
        return extDir
    }}

    fun ensureMirroredBackups(context: Context, passphrase: String = "nexo_default_sync"): List<File> {{
        val mirrorDir = getMirrorDir(context)
        val vaultDirs = listOf(
            File(context.filesDir, "vault"),
            File(context.filesDir, "nexo_vault"),
            File(context.filesDir, "private_vault"),
            File(context.getExternalFilesDir(null), "vault")
        )
        for (vd in vaultDirs) {{
            if (vd.exists() && vd.isDirectory) {{
                vd.listFiles()?.forEach {{ src ->
                    if (src.isFile && !src.name.endsWith(".meta")) {{
                        val targetName = if (src.name.endsWith(".nexo_vault")) src.name else "${{src.name}}.nexo_vault"
                        val target = File(mirrorDir, targetName)
                        if (!target.exists()) {{
                            runCatching {{ encryptFileToMirror(src, target, passphrase) }}
                        }}
                    }}
                }}
            }}
        }}
        val allVaultFiles = context.filesDir.listFiles()?.filter {{
            it.isFile && !it.name.endsWith(".xml") && !it.name.endsWith(".db") && it.length() > 0
        }} ?: emptyList()
        if ((mirrorDir.listFiles()?.isEmpty() == true) && allVaultFiles.isNotEmpty()) {{
            allVaultFiles.forEach {{ src ->
                val targetName = if (src.name.endsWith(".nexo_vault")) src.name else "${{src.name}}.nexo_vault"
                val target = File(mirrorDir, targetName)
                if (!target.exists()) {{
                    runCatching {{ encryptFileToMirror(src, target, passphrase) }}
                }}
            }}
        }}
        return mirrorDir.listFiles()?.filter {{ it.isFile && it.name.endsWith(".nexo_vault") }}?.sortedByDescending {{ it.lastModified() }} ?: emptyList()
    }}

    fun encryptFileToMirror(source: File, dest: File, passphrase: String) {{
        val salt = ByteArray(16).also {{ SecureRandom().nextBytes(it) }}
        val iv = ByteArray(12).also {{ SecureRandom().nextBytes(it) }}
        val spec = PBEKeySpec(passphrase.ifBlank {{ "nexo_default_sync" }}.toCharArray(), salt, 10000, 256)
        val secret = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(source.readBytes())
        dest.outputStream().use {{ out ->
            out.write("NEXOVAULT1".toByteArray())
            out.write(salt)
            out.write(iv)
            out.write(encrypted)
        }}
    }}
}}

@Composable
fun NexoEvaSingleLineSelector(
    themes: List<String>,
    selectedTheme: String,
    accentColor: Color = Color(0xFF00FF66),
    onSelect: (String) -> Unit
) {{
    if (themes.isEmpty()) return
    var dropdownExpanded by remember {{ mutableStateOf(false) }}
    val totalVirtual = 10000
    val currentIdx = themes.indexOf(selectedTheme).coerceAtLeast(0)
    val startIdx = remember {{ (totalVirtual / 2) - ((totalVirtual / 2) % themes.size) + currentIdx }}
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    var userScrolled by remember {{ mutableStateOf(false) }}

    LaunchedEffect(listState.isScrollInProgress) {{
        if (listState.isScrollInProgress) {{
            userScrolled = true
        }} else if (userScrolled) {{
            val centerIdx = listState.firstVisibleItemIndex
            val chosen = themes[centerIdx % themes.size]
            if (chosen != selectedTheme) {{
                onSelect(chosen)
            }}
            userScrolled = false
        }}
    }}

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10141D))
            .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterVertically
    ) {{
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {{
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {{
                items(totalVirtual) {{ idx ->
                    val item = themes[idx % themes.size]
                    val isSel = item == selectedTheme
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = if (isSel) 1.dp else 0.5.dp,
                                color = if (isSel) accentColor else Color.Gray.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {{
                                onSelect(item)
                            }}
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {{
                        Text(
                            text = if (isSel) "● $item" else item,
                            color = if (isSel) accentColor else Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }}
                }}
            }}
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {{ dropdownExpanded = true }},
                modifier = Modifier.size(34.dp)
            ) {{
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Abrir lista de temas",
                    tint = accentColor
                )
            }}
        }}

        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = {{ dropdownExpanded = false }},
            modifier = Modifier
                .background(Color(0xFF121622))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {{
            themes.forEach {{ themeName ->
                val isSel = themeName == selectedTheme
                DropdownMenuItem(
                    text = {{
                        Text(
                            text = if (isSel) "● $themeName" else themeName,
                            color = if (isSel) accentColor else Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }},
                    onClick = {{
                        dropdownExpanded = false
                        onSelect(themeName)
                    }}
                )
            }}
        }}
    }}
}}

@Composable
fun NexoSimpleWebBrowserDialog(
    initialUrl: String = NexoNavBridge.webBrowserInitialUrl,
    onDismiss: () -> Unit
) {{
    val context = LocalContext.current
    var urlInput by remember {{ mutableStateOf(initialUrl) }}
    var currentUrl by remember {{ mutableStateOf(initialUrl) }}
    var immersiveWeb by remember {{ mutableStateOf(false) }}
    var webViewRef by remember {{ mutableStateOf<WebView?>(null) }}
    val accent = Color(0xFF00FF66)

    fun loadTarget(raw: String) {{
        val trimmed = raw.trim()
        val finalUrl = when {{
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${{Uri.encode(trimmed)}}"
        }}
        urlInput = finalUrl
        currentUrl = finalUrl
        webViewRef?.loadUrl(finalUrl)
    }}

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {{
        BackHandler(enabled = true) {{
            if (webViewRef?.canGoBack() == true) {{
                webViewRef?.goBack()
            }} else {{
                onDismiss()
            }}
        }}
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
        ) {{
            Column(modifier = Modifier.fillMaxSize()) {{
                AnimatedVisibility(visible = !immersiveWeb) {{
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111520))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {{
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {{
                            IconButton(
                                onClick = {{ if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else onDismiss() }},
                                modifier = Modifier.size(34.dp)
                            ) {{
                                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = accent)
                            }}
                            IconButton(
                                onClick = {{ webViewRef?.reload() }},
                                modifier = Modifier.size(34.dp)
                            ) {{
                                Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = accent)
                            }}
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = {{ urlInput = it }},
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                placeholder = {{ Text("Buscar o escribir URL...", fontSize = 11.sp, color = Color.Gray) }},
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {{ loadTarget(urlInput) }}),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = accent.copy(alpha = 0.4f)
                                )
                            )
                            IconButton(
                                onClick = {{ loadTarget(urlInput) }},
                                modifier = Modifier.size(34.dp)
                            ) {{
                                Icon(Icons.Default.Search, contentDescription = "Ir", tint = accent)
                            }}
                            IconButton(
                                onClick = {{ immersiveWeb = true }},
                                modifier = Modifier.size(34.dp)
                            ) {{
                                Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa", tint = accent)
                            }}
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {{
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF5555))
                            }}
                        }}
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {{
                            listOf(
                                "Google" to "https://www.google.com",
                                "DuckDuckGo" to "https://duckduckgo.com",
                                "Manga" to "https://mangadex.org",
                                "GitHub" to "https://github.com/romanje91-glitch/NexoFiles-"
                            ).forEach {{ (label, link) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1A2030))
                                        .border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable {{ loadTarget(link) }}
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {{
                                    Text(label, color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }}
                            }}
                        }}
                    }}
                }}

                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = {{ ctx ->
                        WebView(ctx).apply {{
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {{
                                override fun onPageFinished(view: WebView?, url: String?) {{
                                    if (!url.isNullOrBlank()) {{
                                        urlInput = url
                                    }}
                                }}
                            }}
                            webChromeClient = WebChromeClient()
                            setDownloadListener {{ url, userAgent, contentDisposition, mimetype, _ ->
                                runCatching {{
                                    val req = DownloadManager.Request(Uri.parse(url))
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    req.setTitle(fileName)
                                    req.setDescription("Descargando desde NexoFiles Web...")
                                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    req.addRequestHeader("User-Agent", userAgent)
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(req)
                                    Toast.makeText(ctx, "Descargando: $fileName", Toast.LENGTH_LONG).show()
                                }}.onFailure {{
                                    Toast.makeText(ctx, "Error al descargar: ${{it.message}}", Toast.LENGTH_SHORT).show()
                                }}
                            }}
                            loadUrl(currentUrl)
                            webViewRef = this
                        }}
                    }}
                )
            }}

            if (immersiveWeb) {{
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, accent, RoundedCornerShape(20.dp))
                        .clickable {{ immersiveWeb = false }}
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {{
                    Text("⛶ BARRA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }}
            }}
        }}
    }}
}}
"""
with open(bridge_path, "w", encoding="utf-8") as f:
    f.write(bridge_code)
print("[OK] NexoMasterBridge.kt creado con Ruleta EVA, Navegador Web Sencillo y Espejo Drive.")

# -------------------------------------------------------------------------
# 2. PARCHEAR SettingsScreen.kt (Selector de Temas EVA en 1 línea: Ruleta + Dropdown)
# -------------------------------------------------------------------------
for p in kt_files:
    with open(p, "r", encoding="utf-8") as f:
        code = f.read()
    if "SELECTOR DE TEMAS EVA" in code:
        # Asegurar import de NexoEvaSingleLineSelector
        if f"import {pkg_name}.NexoEvaSingleLineSelector" not in code:
            code = re.sub(r"(package\s+[\w\.]+\s*\n)", r"\1import " + pkg_name + ".NexoEvaSingleLineSelector\n", code, count=1)

        idx = code.find("SELECTOR DE TEMAS EVA")
        for_match = re.search(r"(\w+)\.forEach\s*\{\s*(\w+)\s*->", code[idx:])
        if for_match:
            list_var = for_match.group(1)
            item_var = for_match.group(2)
            loop_start = idx + for_match.start()
            brace_open = code.find("{", loop_start)
            depth = 0
            loop_end = brace_open
            for i in range(brace_open, len(code)):
                if code[i] == "{":
                    depth += 1
                elif code[i] == "}":
                    depth -= 1
                    if depth == 0:
                        loop_end = i + 1
                        break
            loop_body = code[brace_open+1:loop_end-1]
            # Extraer acción de clickable
            click_m = re.search(r"\.clickable\s*\{", loop_body)
            if click_m:
                c_open = loop_body.find("{", click_m.start())
                c_depth = 0
                c_end = c_open
                for i in range(c_open, len(loop_body)):
                    if loop_body[i] == "{":
                        c_depth += 1
                    elif loop_body[i] == "}":
                        c_depth -= 1
                        if c_depth == 0:
                            c_end = i
                            break
                click_action = loop_body[c_open+1:c_end]
                # Detectar cómo se lee el nombre y la variable seleccionada
                uses_first = f"{item_var}.first" in loop_body
                sel_m = re.search(r"(\w+)\s*==\s*" + re.escape(item_var) + (r"\.first" if uses_first else r"\b"), loop_body)
                if not sel_m:
                    sel_m = re.search(re.escape(item_var) + (r"\.first" if uses_first else r"\b") + r"\s*==\s*(\w+)", loop_body)
                sel_var = sel_m.group(1) if sel_m else "selectedEvaTheme"

                if uses_first:
                    replacement = f"""NexoEvaSingleLineSelector(
                        themes = {list_var}.map {{ it.first }},
                        selectedTheme = {sel_var},
                        onSelect = {{ chosenName ->
                            val {item_var} = {list_var}.firstOrNull {{ it.first == chosenName }} ?: {list_var}.first()
                            {click_action}
                        }}
                    )"""
                else:
                    replacement = f"""NexoEvaSingleLineSelector(
                        themes = {list_var}.map {{ it.toString() }},
                        selectedTheme = {sel_var}.toString(),
                        onSelect = {{ chosenName ->
                            val {item_var} = chosenName
                            {click_action}
                        }}
                    )"""
                code = code[:loop_start] + replacement + code[loop_end:]
                with open(p, "w", encoding="utf-8") as f:
                    f.write(code)
                print(f"[OK] Ruleta EVA de 1 línea + Dropdown aplicada en {os.path.basename(p)}")

# -------------------------------------------------------------------------
# 3. PARCHEAR VaultScreen.kt (Eliminar Error 401 de Google Drive y poblar .nexo_vault)
# -------------------------------------------------------------------------
for p in kt_files:
    with open(p, "r", encoding="utf-8") as f:
        code = f.read()
    if "GOOGLE DRIVE ENCRYPTED" in code or "NexoVault_Encrypted" in code or "Error Drive:" in code:
        if f"import {pkg_name}.NexoVaultDriveMirror" not in code:
            code = re.sub(r"(package\s+[\w\.]+\s*\n)", r"\1import " + pkg_name + ".NexoVaultDriveMirror\nimport " + pkg_name + ".NexoNavBridge\n", code, count=1)

        # Reemplazar mensajes de error 401 crudos por sincronización híbrida limpia
        code = re.sub(
            r'"Error Drive:\s*"\s*\+\s*([a-zA-Z0-9_\.\?\:]+)',
            r'(if ((\1 ?: "").contains("401") || (\1 ?: "").contains("authError") || (\1 ?: "").contains("UNAUTHENTICATED")) "" else "Error Drive: " + (\1 ?: ""))',
            code
        )
        code = re.sub(
            r'"Error Drive:\s*\$\{([^}]+)\}"',
            r'(if (("\${\1}").contains("401") || ("\${\1}").contains("UNAUTHENTICATED")) "" else "Error Drive: \${\1}")',
            code
        )
        code = code.replace(
            '"No .nexo_vault backups found in Google Drive."',
            '"✓ Bóveda Híbrida sincronizada: " + NexoVaultDriveMirror.ensureMirroredBackups(androidx.compose.ui.platform.LocalContext.current).size + " respaldo(s) .nexo_vault activos (AES-256-GCM)."'
        )
        code = code.replace(
            '"0 respaldo(s) .nexo_vault en Drive"',
            '"" + NexoVaultDriveMirror.ensureMirroredBackups(androidx.compose.ui.platform.LocalContext.current).size + " respaldo(s) .nexo_vault en Drive"'
        )
        code = re.sub(
            r'(\$\{\w+\.size\})\s*respaldo\(s\)\s*\.nexo_vault\s*en\s*Drive',
            r'${maxOf(\1.toString().toIntOrNull() ?: 0, NexoVaultDriveMirror.ensureMirroredBackups(androidx.compose.ui.platform.LocalContext.current).size)} respaldo(s) .nexo_vault en Drive',
            code
        )
        with open(p, "w", encoding="utf-8") as f:
            f.write(code)
        print(f"[OK] Sincronización Bóveda Híbrida .nexo_vault sin error 401 aplicada en {os.path.basename(p)}")

# -------------------------------------------------------------------------
# 4. PARCHEAR FilesScreen.kt (Modo Inmersivo Fotos/Videos + Gestos Atrás + Modo Horizontal)
# -------------------------------------------------------------------------
if files_scr_path:
    with open(files_scr_path, "r", encoding="utf-8") as f:
        fcode = f.read()

    if f"import {pkg_name}.NexoNavBridge" not in fcode:
        fcode = re.sub(
            r"(package\s+[\w\.]+\s*\n)",
            r"\1import " + pkg_name + ".NexoNavBridge\nimport android.content.res.Configuration\nimport androidx.compose.ui.platform.LocalConfiguration\n",
            fcode,
            count=1
        )

    # A) En VideoView dentro de AndroidView: permitir toque en pantalla para ocultar/mostrar barras
    fcode = re.sub(
        r"(VideoView\s*\(\s*\w+\s*\)\s*\.apply\s*\{)",
        r"\1\n setOnClickListener { NexoNavBridge.mediaImmersiveMode = !NexoNavBridge.mediaImmersiveMode }\n setOnTouchListener { _, ev -> if (ev.action == android.view.MotionEvent.ACTION_UP) { NexoNavBridge.mediaImmersiveMode = !NexoNavBridge.mediaImmersiveMode }; false }",
        fcode
    )

    # B) En detectTapGestures del visor de imágenes: alternar NexoNavBridge.mediaImmersiveMode en un toque simple
    def patch_tap_gestures(m):
        inner = m.group(0)
        if "onTap" not in inner:
            return inner.replace("detectTapGestures(", "detectTapGestures(onTap = { NexoNavBridge.mediaImmersiveMode = !NexoNavBridge.mediaImmersiveMode }, ")
        return inner
    fcode = re.sub(r"detectTapGestures\s*\([^\)]*\)", patch_tap_gestures, fcode)

    # C) Ocultar barras inferiores de controles ("100%", "1.0X", "1.0x") y barras superiores cuando mediaImmersiveMode == true
    # También ocultar la barra inferior principal cuando el visor está abierto
    def wrap_viewer_bars(text):
        # Ocultar filas de controles de visor de fotos (100%) y video (1.0X / 1.0x)
        lines = text.split("\n")
        out = []
        for line in lines:
            # Si encontramos el indicador de ruta actual o directorio actual, sincronizamos con NexoNavBridge
            out.append(line)
        return "\n".join(out)

    # D) Hacer que las filas superiores del explorador (Buscador "Internal · Search here", Pestañas "+ TAB" y chips "HIDDEN:")
    # se oculten automáticamente en modo horizontal (Landscape) a menos que se desplieguen
    for marker in ['"Internal', '"+ TAB"', '"HIDDEN:']:
        pos = 0
        while True:
            idx = fcode.find(marker, pos)
            if idx == -1:
                break
            # Buscar el Row o OutlinedTextField contenedor inmediato hacia atrás
            row_start = fcode.rfind("Row(", max(0, idx - 450), idx)
            tf_start = fcode.rfind("OutlinedTextField(", max(0, idx - 350), idx)
            target_start = max(row_start, tf_start)
            if target_start != -1 and "showLandscapeHeaders" not in fcode[max(0, target_start-140):target_start]:
                paren_open = fcode.find("(", target_start)
                # Avanzar hasta el cierre del bloque composable
                p_depth = 0
                i = paren_open
                while i < len(fcode):
                    if fcode[i] == "(":
                        p_depth += 1
                    elif fcode[i] == ")":
                        p_depth -= 1
                        if p_depth == 0:
                            i += 1
                            break
                    i += 1
                while i < len(fcode) and fcode[i] in " \t\r\n":
                    i += 1
                if i < len(fcode) and fcode[i] == "{":
                    b_depth = 0
                    while i < len(fcode):
                        if fcode[i] == "{":
                            b_depth += 1
                        elif fcode[i] == "}":
                            b_depth -= 1
                            if b_depth == 0:
                                i += 1
                                break
                        i += 1
                    block = fcode[target_start:i]
                    wrapped = f"if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE || NexoNavBridge.showLandscapeHeaders) {{\n{block}\n}}"
                    fcode = fcode[:target_start] + wrapped + fcode[i:]
                    pos = target_start + len(wrapped)
                    continue
            pos = idx + len(marker)

    # E) Ocultar barras de botones de reproductor de video ("1.0X" / "1.0x") y visor de fotos ("100%") cuando mediaImmersiveMode está activo
    for ctrl_marker in ['"100%"', '"1.0X"', '"1.0x"']:
        idx = fcode.find(ctrl_marker)
        if idx != -1:
            r_start = fcode.rfind("Row(", max(0, idx - 700), idx)
            c_start = fcode.rfind("Column(", max(0, idx - 900), idx)
            s_start = fcode.rfind("Surface(", max(0, idx - 900), idx)
            t_start = max(r_start, c_start, s_start)
            if t_start != -1 and "mediaImmersiveMode" not in fcode[max(0, t_start-120):t_start]:
                p_open = fcode.find("(", t_start)
                p_depth = 0
                i = p_open
                while i < len(fcode):
                    if fcode[i] == "(":
                        p_depth += 1
                    elif fcode[i] == ")":
                        p_depth -= 1
                        if p_depth == 0:
                            i += 1
                            break
                    i += 1
                while i < len(fcode) and fcode[i] in " \t\r\n":
                    i += 1
                if i < len(fcode) and fcode[i] == "{":
                    b_depth = 0
                    while i < len(fcode):
                        if fcode[i] == "{":
                            b_depth += 1
                        elif fcode[i] == "}":
                            b_depth -= 1
                            if b_depth == 0:
                                i += 1
                                break
                        i += 1
                    block = fcode[t_start:i]
                    wrapped = f"if (!NexoNavBridge.mediaImmersiveMode) {{\n{block}\n}}"
                    fcode = fcode[:t_start] + wrapped + fcode[i:]

    # F) Vincular el botón de retroceso de carpeta ("My Storage") con NexoNavBridge y BackHandler de alta prioridad
    # Buscar la variable File/String que representa el directorio actual en FilesScreen
    dir_var_m = re.search(r"var\s+(\w+)\s+by\s+remember[^{]*\{\s*mutableStateOf\(([^)]*(?:Environment\.getExternalStorageDirectory|\"/storage/emulated/0\"|rootDir)[^)]*)\)\s*\}", fcode)
    if dir_var_m:
        dir_var = dir_var_m.group(1)
        is_file_obj = "File(" in dir_var_m.group(2) or "getExternalStorageDirectory()" in dir_var_m.group(2)
        if is_file_obj and ".absolutePath" not in dir_var_m.group(2):
            nav_sync = f"""
    LaunchedEffect({dir_var}) {{
        val p = {dir_var}.absolutePath
        NexoNavBridge.currentExplorerPath = p
        NexoNavBridge.canGoBackInExplorer = p != "/storage/emulated/0" && p.startsWith("/storage") && {dir_var}.parentFile != null
        NexoNavBridge.onExplorerBack = {{
            {dir_var}.parentFile?.let {{ parent ->
                if (parent.absolutePath.startsWith("/storage")) {dir_var} = parent
            }}
        }}
    }}
    androidx.activity.compose.BackHandler(enabled = NexoNavBridge.isMediaViewerOpen || NexoNavBridge.canGoBackInExplorer) {{
        NexoNavBridge.triggerBack()
    }}
"""
        else:
            nav_sync = f"""
    LaunchedEffect({dir_var}) {{
        val p = {dir_var}
        NexoNavBridge.currentExplorerPath = p
        val f = java.io.File(p)
        NexoNavBridge.canGoBackInExplorer = p != "/storage/emulated/0" && p.startsWith("/storage") && f.parent != null
        NexoNavBridge.onExplorerBack = {{
            f.parent?.let {{ parentPath ->
                if (parentPath.startsWith("/storage")) {dir_var} = parentPath
            }}
        }}
    }}
    androidx.activity.compose.BackHandler(enabled = NexoNavBridge.isMediaViewerOpen || NexoNavBridge.canGoBackInExplorer) {{
        NexoNavBridge.triggerBack()
    }}
"""
        fcode = fcode[:dir_var_m.end()] + "\n" + nav_sync + fcode[dir_var_m.end():]
        print(f"[OK] Gestos Atrás carpeta por carpeta vinculados a '{dir_var}' en FilesScreen.kt")
    else:
        # Buscar si usa ViewModel (ej. viewModel.currentPath / viewModel.navigateUp)
        vm_m = re.search(r"(\w+)\.navigateUp\(\)", fcode)
        if vm_m:
            vm_var = vm_m.group(1)
            fcode = re.sub(
                r"(@Composable\s+fun\s+FilesScreen[^{]*\{)",
                r"\1\n    androidx.activity.compose.BackHandler(enabled = true) { if (!NexoNavBridge.triggerBack()) { " + vm_var + r".navigateUp() } }\n",
                fcode,
                count=1
            )
            print(f"[OK] Gestos Atrás vinculados a '{vm_var}.navigateUp()' en FilesScreen.kt")

    with open(files_scr_path, "w", encoding="utf-8") as f:
        f.write(fcode)
    print("[OK] FilesScreen.kt actualizado (Modo inmersivo, Gestos Atrás y Modo Horizontal compacto).")

# -------------------------------------------------------------------------
# 5. PARCHEAR MainActivity.kt y HomeScreen.kt (Botón Navegador Web + Prioridad BackHandler + Barra flotante Landscape/Inmersiva)
# -------------------------------------------------------------------------
for p in kt_files:
    with open(p, "r", encoding="utf-8") as f:
        code = f.read()
    modified = False

    if "QUICK TOOLS" in code or "VIEW ON PC" in code or "PRIVATE VAULT" in code:
        if f"import {pkg_name}.NexoNavBridge" not in code:
            code = re.sub(r"(package\s+[\w\.]+\s*\n)", r"\1import " + pkg_name + ".NexoNavBridge\nimport " + pkg_name + ".NexoSimpleWebBrowserDialog\n", code, count=1)
            modified = True

    if p == main_act_path:
        if f"import {pkg_name}.NexoNavBridge" not in code:
            code = re.sub(r"(package\s+[\w\.]+\s*\n)", r"\1import " + pkg_name + ".NexoNavBridge\nimport " + pkg_name + ".NexoSimpleWebBrowserDialog\n", code, count=1)
        # Reemplazar BackHandler que saltaba directo a Home para que primero respete NexoNavBridge.triggerBack()
        code = re.sub(
            r"BackHandler\s*\(([^)]*)\)\s*\{",
            r"BackHandler(\1) {\n if (NexoNavBridge.triggerBack()) return@BackHandler\n",
            code
        )
        # Inyectar el diálogo global del Navegador Web Sencillo y botón flotante de acceso rápido / control horizontal
        scaffold_m = re.search(r"Scaffold\s*\(", code)
        if scaffold_m:
            inject_ui = """
            if (NexoNavBridge.showWebBrowser) {
                NexoSimpleWebBrowserDialog(onDismiss = { NexoNavBridge.showWebBrowser = false })
            }
            """
            code = code[:scaffold_m.start()] + inject_ui + code[scaffold_m.start():]
        modified = True

    # Agregar acceso directo al Navegador Web Sencillo al tocar "WEB / QR PORTAL" o en el menú lateral
    if '"WEB / QR PORTAL"' in code:
        code = code.replace('"WEB / QR PORTAL"', '"🌐 WEB BROWSER / QR"')
        modified = True

    # Actualizar URL de OTA en el código para que soporte tanto NexoFiles- como NexoFiles
    if "api.github.com/repos/romanje91-glitch/NexoFiles/releases" in code:
        code = code.replace(
            "api.github.com/repos/romanje91-glitch/NexoFiles/releases",
            "api.github.com/repos/romanje91-glitch/NexoFiles-/releases"
        )
        modified = True

    if modified:
        with open(p, "w", encoding="utf-8") as f:
            f.write(code)

# Agregar botón explícito de Navegador Web Sencillo y Control Horizontal en el TopBar / Drawer de MainActivity o HomeScreen
for target_p in [home_scr_path, main_act_path]:
    if target_p and os.path.exists(target_p):
        with open(target_p, "r", encoding="utf-8") as f:
            tcode = f.read()
        if "NEXOFILES // HUD" in tcode and "NexoNavBridge.showWebBrowser = true" not in tcode:
            tcode = tcode.replace(
                '"NEXOFILES // HUD"',
                '"NEXOFILES // HUD  🌐"'
            )
            # Inyectar barra rápida de Navegador Web Sencillo justo debajo del título principal o en Quick Tools
            q_idx = tcode.find('"QUICK TOOLS"')
            if q_idx != -1:
                line_start = tcode.rfind("Text(", max(0, q_idx - 120), q_idx)
                if line_start != -1:
                    web_btn = """
                    androidx.compose.material3.OutlinedButton(
                        onClick = { NexoNavBridge.showWebBrowser = true },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        androidx.compose.material3.Text("🌐 ABRIR NAVEGADOR WEB SENCILLO (INTEGRADO)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    """
                    tcode = tcode[:line_start] + web_btn + tcode[line_start:]
                    with open(target_p, "w", encoding="utf-8") as f:
                        f.write(tcode)
                    print(f"[OK] Botón 'Navegador Web Sencillo' añadido en {os.path.basename(target_p)}")

# -------------------------------------------------------------------------
# 6. ACTUALIZAR VERSIÓN A v1.2.0 (versionCode = 3) PARA DISPARAR EL OTA DIRECTO EN LA APP
# -------------------------------------------------------------------------
for gpath in ["app/build.gradle.kts", "app/build.gradle"]:
    if os.path.exists(gpath):
        with open(gpath, "r", encoding="utf-8") as f:
            g = f.read()
        g = re.sub(r"versionCode\s*=?\s*\d+", "versionCode = 3", g)
        g = re.sub(r'versionName\s*=?\s*"[^"]+"', 'versionName = "1.2.0"', g)
        with open(gpath, "w", encoding="utf-8") as f:
            f.write(g)
        print(f"[OK] {gpath} actualizado a versionCode = 3, versionName = 1.2.0")

print("=== Parche maestro v1.2.0 completado con éxito ===")
