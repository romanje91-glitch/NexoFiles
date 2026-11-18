import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")

# 1. Subir versión a v1.1.0 (versionCode 2) en app/build.gradle.kts o build.gradle
for gf in ["app/build.gradle.kts", "app/build.gradle"]:
    gp = os.path.join(base_dir, gf)
    if os.path.exists(gp):
        gt = open(gp).read()
        gt = re.sub(r'versionCode\s*=?\s*\d+', 'versionCode = 2', gt)
        gt = re.sub(r'versionName\s*=?\s*"[^"]+"', 'versionName = "1.1.0"', gt)
        open(gp, "w").write(gt)
        print("1. Versión actualizada a v1.1.0 (versionCode 2).")

# 2. ToolsAndNetworkViewModel.kt: Respaldo automático en Bóveda SIN pedir passphrase
vm_path = os.path.join(java_dir, "ui/viewmodel/ToolsAndNetworkViewModel.kt")
vm = open(vm_path).read()

# Reemplazar validación de passphrase para usar llave hermética automática si viene vacía
vm = vm.replace(
    'if (passphrase.length < 4) {\n            emitMessage("La contraseña de respaldo debe tener al menos 4 caracteres")\n            return\n        }',
    'val effectivePassphrase = if (passphrase.length < 4) "NEXO_HERMETIC_AUTO_KEY_2026" else passphrase'
)
vm = vm.replace('exportAllVaultToNxv(passphrase,', 'exportAllVaultToNxv(effectivePassphrase,')
vm = vm.replace('exportSelectedVaultToNxv(targets, passphrase,', 'exportSelectedVaultToNxv(targets, effectivePassphrase,')
vm = vm.replace('importNxvToVault(downloaded, passphrase)', 'importNxvToVault(downloaded, if (passphrase.length < 4) "NEXO_HERMETIC_AUTO_KEY_2026" else passphrase)')

# Disparar backupVaultToDrive automáticamente al terminar addUriToVault
if 'backupVaultToDrive("NEXO_HERMETIC_AUTO_KEY_2026")' not in vm:
    target_add = "fun addUriToVault(uri: Uri) {"
    idx_add = vm.find(target_add)
    idx_end = vm.find("fun openVaultItem(", idx_add)
    if idx_add != -1 and idx_end != -1:
        block = vm[idx_add:idx_end]
        block = block.replace(
            "_vaultState.value = _vaultState.value.copy(isBusy = false)",
            "_vaultState.value = _vaultState.value.copy(isBusy = false)\n                if (com.example.ui.components.NexoSecureVaultManager.autoBackupOnImport) { backupVaultToDrive(\"NEXO_HERMETIC_AUTO_KEY_2026\") }",
            1
        )
        vm = vm[:idx_add] + block + vm[idx_end:]

open(vm_path, "w").write(vm)
print("2. ToolsAndNetworkViewModel configurado con Auto-Backup sin passphrase.")

# 3. GoogleDriveRepository.kt: Ocultar respaldos de bóveda usando propiedades privadas/appData
gd_path = os.path.join(java_dir, "data/repo/GoogleDriveRepository.kt")
gd = open(gd_path).read()
gd = gd.replace(
    'const val VAULT_DRIVE_FOLDER_NAME = "NexoVault_Encrypted"',
    'const val VAULT_DRIVE_FOLDER_NAME = ".nexo_system_appdata_vault"'
)
open(gd_path, "w").write(gd)
print("3. GoogleDriveRepository configurado con contenedor oculto.")

# 4. MainActivity.kt: Desactivar swipe accidental del HorizontalPager + BackHandler reactivo global
main_path = os.path.join(java_dir, "MainActivity.kt")
mc = open(main_path).read()

# Evitar que el gesto lateral de Android mueva el HorizontalPager en vez de retroceder carpeta
if "userScrollEnabled = false" not in mc:
    mc = mc.replace(
        "HorizontalPager(\n                    state = pagerState,",
        "HorizontalPager(\n                    state = pagerState,\n                    userScrollEnabled = false,"
    )

# Hacer que el BackHandler global use explorerVm.navigateUp() directamente
mc = re.sub(
    r'BackHandler\(enabled = activeTabId == "FILES".*?BackHandler\(enabled = activeTabId != "HOME" && activeTabId != "FILES" && !drawerState\.isOpen\) \{',
    'BackHandler(enabled = (activeTabId != "HOME" || explorerState.currentPath != explorerState.rootPath) && !drawerState.isOpen) {',
    mc,
    flags=re.DOTALL
)
open(main_path, "w").write(mc)
print("4. MainActivity actualizado (gestos de retroceso 100% nativos en toda la app).")

# 5. FilesScreen.kt: Soporte para .webp en el lector, botón de Buscador Web y barra compacta en horizontal
fp = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(fp).read()

# Incluir .webp junto con .cbz y .cbr y añadir host del navegador web
ft = ft.replace(
    '(item.name.lowercase().endsWith(".cbz") || item.name.lowercase().endsWith(".cbr"))',
    'NexoComicViewerState.isSupportedComicOrWebp(java.io.File(item.path))'
)
if "NexoWebBrowserHost()" not in ft:
    ft = ft.replace("NexoComicViewerHost()", "NexoComicViewerHost()\n            NexoWebBrowserHost()", 1)

open(fp, "w").write(ft)
print("5. FilesScreen actualizado con soporte .webp y NexoWebBrowserHost.")

# 6. NexoAdvancedModules.kt: Añadir botón "WEB" en la barra de pestañas del explorador
adv_path = os.path.join(java_dir, "ui/components/NexoAdvancedModules.kt")
if os.path.exists(adv_path):
    adv = open(adv_path).read()
    if "NexoWebBrowserState.open()" not in adv:
        adv = adv.replace(
            'Text("+ TAB"',
            'Text("🌐 WEB", color = NexoEvaThemeManager.primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp).clickable { NexoWebBrowserState.open() })\n            Text("+ TAB"'
        )
        open(adv_path, "w").write(adv)
        print("6. Botón de Buscador Web añadido a la barra superior.")

# 7. VideoPlayerScreen.kt: Hacer que la barra superior e inferior se oculten/muestren al tocar el video
vp_path = os.path.join(java_dir, "ui/screens/VideoPlayerScreen.kt")
if os.path.exists(vp_path):
    vp = open(vp_path).read()
    if "var showControlsOverlay by remember" not in vp:
        vp = vp.replace(
            "var playbackSpeed by remember { mutableFloatStateOf(1f) }",
            "var playbackSpeed by remember { mutableFloatStateOf(1f) }\n    var showControlsOverlay by remember { mutableStateOf(true) }"
        )
        vp = vp.replace(
            "// NERV HUD Top bar\n        Surface(",
            "// NERV HUD Top bar\n        if (showControlsOverlay) Surface("
        )
        vp = vp.replace(
            "// Video viewport\n        Box(\n            modifier = Modifier\n                .weight(1f)\n                .fillMaxWidth()",
            "// Video viewport\n        Box(\n            modifier = Modifier\n                .weight(1f)\n                .fillMaxWidth()\n                .clickable { showControlsOverlay = !showControlsOverlay }"
        )
        open(vp_path, "w").write(vp)
        print("7. VideoPlayerScreen actualizado con controles auto-ocultables al tocar.")

# 8. SettingsScreen.kt: Conectar el botón original de 5. SYSTEM & ABOUT al descargador OTA real y quitar tarjeta extra
sp = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(sp).read()
st = st.replace("item { NexoAutoUpdateSettingsCard() }\n        ", "")
st = st.replace("item { NexoUiScaleSettingsCard() }\n        ", "")
st = re.sub(
    r'onClick\s*=\s*\{\s*updateCheckMessage\s*=\s*"\$\{strings\.aboutNexoTitle\}[^"]*"\s*\}',
    'onClick = { NexoOtaUpdater.downloadAndInstallUpdate(context) { msg -> updateCheckMessage = msg } }',
    st
)
open(sp, "w").write(st)
print("8. SettingsScreen conectado al motor OTA en 5. SYSTEM & ABOUT.")

# 9. AppStrings.kt: Limpiar textos largos explicativos de seguridad y telemetría
str_path = os.path.join(java_dir, "ui/i18n/AppStrings.kt")
if os.path.exists(str_path):
    ap = open(str_path).read()
    ap = re.sub(r'Authenticate with the active security method[^\"]*', 'Autenticación requerida', ap)
    ap = re.sub(r'Lock method changes \(Biometric/PIN/Pattern/Password\)[^\"]*', '', ap)
    ap = ap.replace('3-PHASE MD5 FUNNEL • DHASH SIMILARITY TELEMETRY', 'STORAGE ANALYZER')
    ap = ap.replace('AES-256-GCM encryption • Hermetic internal viewers', 'PRIVATE VAULT')
    ap = ap.replace('HERMETIC MODE • DIRECT INTERNAL VIEWER', 'ENCRYPTED')
    open(str_path, "w").write(ap)
    print("9. Textos explicativos de seguridad eliminados.")
