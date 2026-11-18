import os, re, shutil

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")
backup_dir = os.path.join(base_dir, "backup_steps_2_to_5")

for rel in ["MainActivity.kt", "ui/screens/FilesScreen.kt", "ui/screens/VaultScreen.kt", "ui/screens/SettingsScreen.kt"]:
    bak = os.path.join(backup_dir, os.path.basename(rel))
    dst = os.path.join(java_dir, rel)
    if os.path.exists(bak):
        shutil.copy2(bak, dst)

# 1. FilesScreen.kt
fp = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(fp).read()
if "import com.example.ui.components.*" not in ft:
    ft = ft.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)
ft = re.sub(
    r"(fun handleItemClick\s*\(\s*item\s*:\s*LocalFileItem\s*\)\s*\{)",
    r'\1\n        if (!item.isDirectory && (item.name.lowercase().endsWith(".cbz") || item.name.lowercase().endsWith(".cbr"))) { NexoComicViewerState.open(java.io.File(item.path)); return }',
    ft, count=1
)
ft = ft.replace(
    "BreadcrumbBar(\n                        currentPath = uiState.currentPath,\n                        onNavigateToPath = onNavigateToPath,\n                        onNavigateUp = onNavigateUp,\n                    )",
    "Column(modifier = Modifier.fillMaxWidth()) {\n                        NexoComicViewerHost()\n                        NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)\n                        BreadcrumbBar(currentPath = uiState.currentPath, onNavigateToPath = onNavigateToPath, onNavigateUp = onNavigateUp)\n                    }"
)
ft = ft.replace(
    "val iconBoxSize = if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp\n    val verticalPad = if (layoutViewMode == LayoutViewMode.COMPACT) 8.dp else 12.dp",
    "val uiScale = NexoUiScaleManager.scale\n    val iconBoxSize = (if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp) * uiScale\n    val verticalPad = (if (layoutViewMode == LayoutViewMode.COMPACT) 8.dp else 12.dp) * uiScale"
)
open(fp, "w").write(ft)

# 2. VaultScreen.kt
vp = os.path.join(java_dir, "ui/screens/VaultScreen.kt")
vt = open(vp).read()
if "import com.example.ui.components.*" not in vt:
    vt = vt.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)
vt = vt.replace(
    "var showDriveBackupModal by remember { mutableStateOf(false) }",
    "var showDriveBackupModal by remember { mutableStateOf(false) }\n    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }"
)
vt = vt.replace("onClick = { onDeleteVaultItem(item) }", "onClick = { pendingSelectiveDeleteItem = item }")
vt = vt.replace(
    "    if (showDriveBackupModal) {",
    """    if (pendingSelectiveDeleteItem != null) {
        val targetItem = pendingSelectiveDeleteItem!!
        NexoVaultSelectiveDeleteDialog(
            fileName = targetItem.originalName,
            onDismiss = { pendingSelectiveDeleteItem = null },
            onSelectOption = { option ->
                when (option) {
                    NexoVaultDeleteOption.LOCAL_ONLY -> {
                        NexoHybridVaultRegistry.markSavedInCloudOnly(context, targetItem.id, targetItem.originalName, targetItem.sizeBytes)
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
    if (showDriveBackupModal) {""", 1
)
vt = vt.replace(
    "LazyColumn(",
    "NexoVaultHybridSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, cloudBytes = 0L, onOpenDriveBackupModal = { showDriveBackupModal = true })\n            LazyColumn(", 1
)
open(vp, "w").write(vt)

# 3. SettingsScreen.kt
sp = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(sp).read()
if "import com.example.ui.components.*" not in st:
    st = st.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)
st = st.replace("LazyColumn(", "NexoUiScaleSettingsCard()\n        LazyColumn(", 1)
open(sp, "w").write(st)
print("Pantallas actualizadas correctamente.")
