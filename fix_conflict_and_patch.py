import os, re, shutil

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")
backup_dir = os.path.join(base_dir, "backup_steps_2_to_5")

# 1. Eliminar declaracion duplicada de ComicViewer en NexoAdvancedModules.kt
adv_path = os.path.join(java_dir, "ui/components/NexoAdvancedModules.kt")
if os.path.exists(adv_path):
    adv = open(adv_path).read()
    adv = re.sub(r"object NexoComicViewerState \{.*?data class CloudOnlyVaultGhost", "data class CloudOnlyVaultGhost", adv, flags=re.DOTALL)
    open(adv_path, "w").write(adv)

# 2. Restaurar pantallas limpias desde backup
for rel in ["ui/screens/FilesScreen.kt", "ui/screens/VaultScreen.kt", "ui/screens/SettingsScreen.kt"]:
    bak = os.path.join(backup_dir, os.path.basename(rel))
    dst = os.path.join(java_dir, rel)
    if os.path.exists(bak):
        shutil.copy2(bak, dst)

# 3. Parchear FilesScreen.kt
fp = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(fp).read()
if "import com.example.ui.components.*" not in ft:
    ft = ft.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

# BackHandler nativo de Android para retroceder carpeta por carpeta
ft = ft.replace(
    "var renameTarget by remember",
    "androidx.activity.compose.BackHandler(enabled = uiState.currentPath.isNotBlank() && uiState.currentPath != \"/storage/emulated/0\") { onNavigateUp() }\n    var renameTarget by remember",
    1
)

# Barra de pestañas arriba de la fila del Breadcrumb (sin encimar texto)
ft = ft.replace(
    "// Top Breadcrumb HUD bar + Layout & Hidden toggles",
    "NexoComicViewerHost()\n            NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)\n            // Top Breadcrumb HUD bar + Layout & Hidden toggles",
    1
)

# Abrir .cbz / .cbr con el lector avanzado
ft = ft.replace(
    "fun handleItemClick(item: LocalFileItem) {",
    "fun handleItemClick(item: LocalFileItem) {\n        if (!item.isDirectory && (item.name.lowercase().endsWith(\".cbz\") || item.name.lowercase().endsWith(\".cbr\"))) {\n            NexoComicViewerState.open(java.io.File(item.path))\n            return\n        }",
    1
)

# Miniaturas en vista Cuadricula (Grid)
ft = re.sub(
    r"(IconContainerBadge\(\s*icon\s*=\s*glyph,\s*contentDescription\s*=\s*item\.name,\s*size\s*=\s*44\.dp,\s*highlighted\s*=\s*isSelected\s*\|\|\s*item\.isDirectory,\s*\))",
    r"NexoFileThumbnailOrFallback(file = java.io.File(item.path), size = 44.dp) { \1 }",
    ft, count=1
)

# Miniaturas en vista Lista
idx = ft.find("imageVector = folderGlyph,")
if idx != -1:
    start = ft.rfind("Icon(", 0, idx)
    depth = 0
    end = start
    for i in range(start, len(ft)):
        if ft[i] == "(": depth += 1
        elif ft[i] == ")":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    ft = ft[:start] + "NexoFileThumbnailOrFallback(file = java.io.File(item.path), size = iconBoxSize) { " + ft[start:end] + " }" + ft[end:]

open(fp, "w").write(ft)

# 4. Parchear VaultScreen.kt (Panel hibrido + Menu de 3 opciones al eliminar)
vp = os.path.join(java_dir, "ui/screens/VaultScreen.kt")
vt = open(vp).read()
if "import com.example.ui.components.*" not in vt:
    vt = vt.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

vt = vt.replace(
    "var showDriveBackupModal by remember { mutableStateOf(false) }",
    "var showDriveBackupModal by remember { mutableStateOf(false) }\n    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }",
    1
)
vt = vt.replace("onClick = { onDeleteVaultItem(item) },", "onClick = { pendingSelectiveDeleteItem = item },")
vt = vt.replace(
    "items(state.items, key = { it.id })",
    "item { NexoSecureVaultSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, onOpenDriveBackupModal = { showDriveBackupModal = true }) }\n                items(state.items, key = { it.id })",
    1
)
vt = vt.replace(
    "if (showDriveBackupModal) {",
    """if (pendingSelectiveDeleteItem != null) {
        val t = pendingSelectiveDeleteItem!!
        NexoVaultSelectiveDeleteDialog(
            fileName = t.originalName,
            onDismiss = { pendingSelectiveDeleteItem = null },
            onSelectOption = { opt ->
                when (opt) {
                    NexoVaultDeleteOption.LOCAL_ONLY -> { NexoSecureVaultManager.markEncryptedAndHiddenInDrive(context, t.id, t.originalName, t.sizeBytes); onDeleteVaultItem(t) }
                    NexoVaultDeleteOption.CLOUD_ONLY -> { NexoSecureVaultManager.removeGhost(context, t.id); showDriveBackupModal = true }
                    NexoVaultDeleteOption.BOTH -> { NexoSecureVaultManager.removeGhost(context, t.id); onDeleteVaultItem(t) }
                }
                pendingSelectiveDeleteItem = null
            }
        )
    }
    if (showDriveBackupModal) {""",
    1
)
open(vp, "w").write(vt)

# 5. Parchear SettingsScreen.kt (Temas EVA y Auto-Update dentro del LazyColumn)
sp = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(sp).read()
if "import com.example.ui.components.*" not in st:
    st = st.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

st = st.replace(
    "// BLOQUE 1: AJUSTES GENERALES",
    "item { NexoEvaThemeSelectorCard() }\n        item { NexoAutoUpdateSettingsCard() }\n        item { NexoUiScaleSettingsCard() }\n        // BLOQUE 1: AJUSTES GENERALES",
    1
)
open(sp, "w").write(st)
print("Parche limpio aplicado con exito.")
