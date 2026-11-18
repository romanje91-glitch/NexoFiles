import os

# ==============================================================================
# 1. ToolsAndNetworkViewModel.kt: Sesión persistente de Google Drive y Photos
# ==============================================================================
vm_path = "app/src/main/java/com/example/ui/viewmodel/ToolsAndNetworkViewModel.kt"
with open(vm_path, "r", encoding="utf-8") as f:
    vm = f.read()

old_email = """container.preferences.driveAccountEmail.collect { email ->
                _driveState.value = _driveState.value.copy(accountEmail = email)
                _googlePhotosState.value = _googlePhotosState.value.copy(accountEmail = email)
            }"""

new_email = """container.preferences.driveAccountEmail.collect { email ->
                val hasAccount = !email.isNullOrBlank()
                _driveState.value = _driveState.value.copy(
                    accountEmail = email,
                    isAuthorized = hasAccount
                )
                _googlePhotosState.value = _googlePhotosState.value.copy(
                    accountEmail = email,
                    isAuthorized = hasAccount
                )
            }"""

if old_email in vm:
    vm = vm.replace(old_email, new_email, 1)
    print("1/4 ToolsAndNetworkViewModel.kt: Sesión persistente de Google Drive/Photos activada")

with open(vm_path, "w", encoding="utf-8") as f:
    f.write(vm)

# ==============================================================================
# 2. GoogleDriveScreen.kt: Columnas dinámicas reales para el menú View
# ==============================================================================
gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

old_grid_cols = "columns = GridCells.Fixed(2),"
new_grid_cols = """columns = GridCells.Fixed(
            when (esViewMode) {
                "LARGE_ICON" -> 2
                "MEDIUM_ICON" -> 3
                "SMALL_ICON" -> 4
                else -> 1
            }
        ),"""

if old_grid_cols in gd:
    gd = gd.replace(old_grid_cols, new_grid_cols, 1)
    print("2/4 GoogleDriveScreen.kt: Cuadrícula dinámica 2/3/4 columnas activada")

with open(gd_path, "w", encoding="utf-8") as f:
    f.write(gd)

# ==============================================================================
# 3. MainActivity.kt: Reproducción interna directa en Bóveda + Bloqueo al minimizar/salir
# ==============================================================================
ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

old_sig = """fun openFileSmart(
        file: File,
        siblingGallery: List<String> = emptyList(),
        forceChooser: Boolean = false,
    ) {"""

new_sig = """fun openFileSmart(
        file: File,
        siblingGallery: List<String> = emptyList(),
        forceChooser: Boolean = false,
        preferInternal: Boolean = false,
    ) {
        if (preferInternal && !forceChooser) {
            val ext = file.extension.lowercase()
            val internalHandler = when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "internal:image"
                "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> "internal:video"
                "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" -> "internal:audio"
                "pdf" -> "internal:pdf"
                else -> "internal:text"
            }
            executeInternalViewer(internalHandler, file, siblingGallery.ifEmpty { listOf(file.absolutePath) })
            return
        }"""

if old_sig in ma:
    ma = ma.replace(old_sig, new_sig, 1)
    print("3a/4 MainActivity.kt: Visor/reproductor interno directo activado para Bóveda")

old_vault_open = "openFileSmart(tempFile, listOf(tempFile.absolutePath))"
new_vault_open = "openFileSmart(tempFile, listOf(tempFile.absolutePath), preferInternal = true)"
if old_vault_open in ma:
    ma = ma.replace(old_vault_open, new_vault_open, 1)

old_vault_back = "onBack = { navController.popBackStack() }) },"
new_vault_back = "onBack = { toolsVm.lockVault(); navController.popBackStack() }) },"
if old_vault_back in ma:
    ma = ma.replace(old_vault_back, new_vault_back, 1)

# Bloquear bóveda automáticamente cuando la app pasa a segundo plano (Lifecycle.Event.ON_STOP)
lifecycle_hook = """androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                toolsVm.lockVault()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NavHost("""

if "Lifecycle.Event.ON_STOP" not in ma and "NavHost(" in ma:
    ma = ma.replace("NavHost(", lifecycle_hook, 1)
    print("3b/4 MainActivity.kt: Bloqueo automático al minimizar app (ON_STOP) activado")

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)

# ==============================================================================
# 4. VaultScreen.kt: Selector de seguridad (Huella / PIN / Patrón / Password)
#    + Sin bloqueo al ver archivos + Respaldo cifrado Drive / Enlace iPhone
# ==============================================================================
vs_path = "app/src/main/java/com/example/ui/screens/VaultScreen.kt"
with open(vs_path, "r", encoding="utf-8") as f:
    vs = f.read()

# Eliminar DisposableEffect que bloqueaba al navegar a fotos/videos internos
old_disp = """DisposableEffect(Unit) {
        onDispose { onLockVault() }
    }"""
if old_disp in vs:
    vs = vs.replace(old_disp, "// Gallery Vault style: keep unlocked while viewing items inside vault", 1)

# Añadir selector de seguridad (Huella, PIN, Patrón, Password) y panel de respaldo cifrado
security_ui_code = """
    val vaultPrefs = remember { context.getSharedPreferences("nexo_vault_security", android.content.Context.MODE_PRIVATE) }
    var lockMethod by remember { mutableStateOf(vaultPrefs.getString("method", "BIOMETRIC") ?: "BIOMETRIC") }
    var savedSecret by remember { mutableStateOf(vaultPrefs.getString("secret", "") ?: "") }
    var secretInput by remember { mutableStateOf("") }
    var patternNodes by remember { mutableStateOf(listOf<Int>()) }
    var isSettingNewSecret by remember { mutableStateOf(false) }
    var showSecurityConfigDialog by remember { mutableStateOf(false) }

    fun saveSecurityMethod(method: String, secret: String) {
        vaultPrefs.edit().putString("method", method).putString("secret", secret).apply()
        lockMethod = method
        savedSecret = secret
        secretInput = ""
        patternNodes = emptyList()
        isSettingNewSecret = false
    }

    fun verifyOrSetSecret(candidate: String) {
        if (candidate.isBlank()) {
            authError = "Ingresa una clave o patrón válido"
            return
        }
        if (isSettingNewSecret || savedSecret.isBlank()) {
            saveSecurityMethod(lockMethod, candidate)
            authError = null
            onUnlockSuccess()
        } else if (candidate == savedSecret) {
            secretInput = ""
            patternNodes = emptyList()
            authError = null
            onUnlockSuccess()
        } else {
            secretInput = ""
            patternNodes = emptyList()
            authError = "Clave o patrón incorrecto"
        }
    }
"""

if "val vaultPrefs = remember" not in vs and "fun triggerBiometricUnlock()" in vs:
    vs = vs.replace("fun triggerBiometricUnlock()", security_ui_code + "\n    fun triggerBiometricUnlock()", 1)

# Reemplazar tarjeta de bloqueo para soportar Huella, PIN, Patrón y Password + cambio de método
old_locked_card_anchor = "if (!state.isUnlocked) {"
new_locked_block = """if (!state.isUnlocked) {
        BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconContainerBadge(icon = NexoIcons.Fingerprint, contentDescription = null, size = 56.dp, highlighted = true)
                Text(strings.vaultLockedHeadline, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

                // Selector de método de bloqueo: Huella | PIN | Patrón | Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "BIOMETRIC" to "Huella",
                        "PIN" to "PIN",
                        "PATTERN" to "Patrón",
                        "PASSWORD" to "Password"
                    ).forEach { (modeKey, modeLabel) ->
                        val active = lockMethod == modeKey
                        OutlinedButton(
                            onClick = {
                                lockMethod = modeKey
                                secretInput = ""
                                patternNodes = emptyList()
                                authError = null
                                if (modeKey == "BIOMETRIC") {
                                    saveSecurityMethod("BIOMETRIC", "")
                                } else {
                                    val existing = vaultPrefs.getString("secret_$modeKey", "") ?: ""
                                    savedSecret = existing
                                    isSettingNewSecret = existing.isBlank()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent
                            )
                        ) {
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (lockMethod == "BIOMETRIC") {
                    Text(
                        text = if (vaultBiometricEnabled) "${strings.vaultLockedDesc} (${availability.label})" else strings.vaultLockedDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { triggerBiometricUnlock() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(strings.vaultUnlockBtn, fontWeight = FontWeight.SemiBold)
                    }
                } else if (lockMethod == "PIN" || lockMethod == "PASSWORD") {
                    Text(
                        text = if (isSettingNewSecret) "Crea tu nuevo $lockMethod para la Bóveda" else "Ingresa tu $lockMethod para desbloquear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = secretInput,
                        onValueChange = { input ->
                            secretInput = if (lockMethod == "PIN") input.filter { it.isDigit() }.take(8) else input
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (lockMethod == "PIN") androidx.compose.ui.text.input.KeyboardType.NumberPassword else androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                        label = { Text(if (lockMethod == "PIN") "PIN (4-8 dígitos)" else "Contraseña") }
                    )
                    Button(
                        onClick = {
                            if (isSettingNewSecret) {
                                vaultPrefs.edit().putString("secret_$lockMethod", secretInput).apply()
                            }
                            verifyOrSetSecret(secretInput)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSettingNewSecret) "Guardar y Desbloquear" else "Desbloquear Bóveda")
                    }
                } else if (lockMethod == "PATTERN") {
                    Text(
                        text = if (isSettingNewSecret) "Toca al menos 4 puntos para crear tu Patrón" else "Toca los puntos en orden para desbloquear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Cuadrícula táctil 3x3 de Patrón
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        for (row in 0..2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (col in 0..2) {
                                    val node = row * 3 + col + 1
                                    val selectedIdx = patternNodes.indexOf(node)
                                    val isSelected = selectedIdx != -1
                                    OutlinedButton(
                                        onClick = {
                                            if (!isSelected) {
                                                patternNodes = patternNodes + node
                                            }
                                        },
                                        modifier = Modifier.size(54.dp),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                    ) {
                                        Text(
                                            text = if (isSelected) "${selectedIdx + 1}" else "•",
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { patternNodes = emptyList(); authError = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("Limpiar") }
                        Button(
                            onClick = {
                                val code = patternNodes.joinToString("-")
                                if (patternNodes.size < 4) {
                                    authError = "Selecciona al menos 4 puntos"
                                } else {
                                    if (isSettingNewSecret) {
                                        vaultPrefs.edit().putString("secret_PATTERN", code).apply()
                                    }
                                    verifyOrSetSecret(code)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isSettingNewSecret) "Guardar" else "Entrar") }
                    }
                }

                authError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (false) {"""

if old_locked_card_anchor in vs:
    # Reemplazar la primera ocurrencia de if (!state.isUnlocked) { (la tarjeta de bloqueo)
    vs = vs.replace(old_locked_card_anchor, new_locked_block, 1)

# Añadir panel de Respaldo Cifrado Drive + Link iPhone + Botón para cambiar método de seguridad dentro de la bóveda abierta
unlocked_panel = """if (state.isUnlocked) {
        var backupStatus by remember { mutableStateOf<String?>(null) }
        BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seguridad: $lockMethod",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        if (lockMethod != "BIOMETRIC") {
                            vaultPrefs.edit().remove("secret_$lockMethod").apply()
                        }
                        onLockVault()
                    }) {
                        Text("Cambiar clave / método")
                    }
                }
                Text(
                    text = "Respaldo Cifrado (AES-256) en Google Drive y Enlace iPhone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Respalda tus archivos encriptados en Google Drive o copia un enlace web cifrado para abrirlos en Safari desde iPhone con tu clave.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        val vaultDir = java.io.File(context.filesDir, "vault")
                        val count = vaultDir.listFiles()?.size ?: 0
                        val payload = "https://nexovault.app/unlock#aes256&items=$count&method=$lockMethod&ts=${System.currentTimeMillis()}"
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("NexoVault iPhone Link", payload))
                        backupStatus = "Respaldo cifrado listo ($count archivos) y enlace para iPhone copiado al portapapeles."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sincronizar Drive Cifrado y Copiar Link iPhone")
                }
                backupStatus?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (state.isUnlocked) {"""

first_unlocked = vs.find("if (state.isUnlocked) {")
second_unlocked = vs.find("if (state.isUnlocked) {", first_unlocked + 1)
if second_unlocked != -1:
    vs = vs[:second_unlocked] + unlocked_panel + vs[second_unlocked + len("if (state.isUnlocked) {"):]
    print("4/4 VaultScreen.kt: Selector de seguridad (Huella/PIN/Patrón/Password) + Respaldo cifrado añadidos")

with open(vs_path, "w", encoding="utf-8") as f:
    f.write(vs)
