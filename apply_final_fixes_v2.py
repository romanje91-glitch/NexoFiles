import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")

# 1. FilesScreen.kt
files_path = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(files_path).read()

if "import com.example.ui.components.*" not in ft:
    ft = ft.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

# Interceptar apertura de .cbz / .cbr de forma segura
if "NexoComicViewerState.open" not in ft:
    target_pattern = "fun handleItemClick(item: LocalFileItem) {"
    replacement = target_pattern + "\n        if (!item.isDirectory && (item.name.lowercase().endsWith(\".cbz\") || item.name.lowercase().endsWith(\".cbr\"))) {\n            NexoComicViewerState.open(java.io.File(item.path))\n            return\n        }"
    ft = ft.replace(target_pattern, replacement)

# Pestañas y host de cómics sobre la barra de ruta
if "NexoExplorerTabBar(" not in ft:
    ft = ft.replace(
        "BreadcrumbBar(",
        "NexoComicViewerHost()\n                        NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)\n                        BreadcrumbBar(",
        1
    )

# Escala global de interfaz
if "NexoUiScaleManager.scale" not in ft:
    ft = ft.replace(
        "val iconBoxSize = if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp",
        "val uiScale = NexoUiScaleManager.scale\n    val iconBoxSize = (if (layoutViewMode == LayoutViewMode.COMPACT) 38.dp else 50.dp) * uiScale"
    )

open(files_path, "w").write(ft)
print("FilesScreen actualizado.")

# 2. VaultScreen.kt
vault_path = os.path.join(java_dir, "ui/screens/VaultScreen.kt")
vt = open(vault_path).read()

if "import com.example.ui.components.*" not in vt:
    vt = vt.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "pendingSelectiveDeleteItem" not in vt:
    vt = vt.replace(
        "var showDriveBackupModal by remember { mutableStateOf(false) }",
        "var showDriveBackupModal by remember { mutableStateOf(false) }\n    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }"
    )

vt = vt.replace(
    "onDeleteVaultItem = onDeleteVaultItem,",
    "onDeleteVaultItem = { item -> pendingSelectiveDeleteItem = item },"
)

if "NexoVaultSelectiveDeleteDialog(" not in vt:
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
    if (showDriveBackupModal) {""",
        1
    )

if "NexoVaultHybridSummaryPanel(" not in vt:
    vt = vt.replace(
        "LazyColumn(",
        "NexoVaultHybridSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, cloudBytes = 0L, onOpenDriveBackupModal = { showDriveBackupModal = true })\n            LazyColumn(",
        1
    )

open(vault_path, "w").write(vt)
print("VaultScreen actualizado.")

# 3. SettingsScreen.kt
settings_path = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(settings_path).read()

if "import com.example.ui.components.*" not in st:
    st = st.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "NexoUiScaleSettingsCard(" not in st:
    st = st.replace(
        "LazyColumn(",
        "NexoUiScaleSettingsCard()\n        LazyColumn(",
        1
    )

open(settings_path, "w").write(st)
print("SettingsScreen actualizado.")
