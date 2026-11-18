import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")
backup_dir = os.path.join(base_dir, "backup_steps_2_to_5")

# Restaurar archivos limpios de respaldo
for rel in ["ui/screens/FilesScreen.kt", "ui/screens/VaultScreen.kt", "ui/screens/SettingsScreen.kt"]:
    bak = os.path.join(backup_dir, os.path.basename(rel))
    dst = os.path.join(java_dir, rel)
    if os.path.exists(bak):
        os.system(f"cp -f {bak} {dst}")

# 1. FilesScreen.kt
files_path = os.path.join(java_dir, "ui/screens/FilesScreen.kt")
ft = open(files_path).read()

if "import com.example.ui.components.*" not in ft:
    ft = ft.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

# Lector de cómics al hacer clic en .cbz / .cbr
if "NexoComicViewerState.open" not in ft:
    target = "fun handleItemClick(item: LocalFileItem) {"
    replacement = target + "\n        if (!item.isDirectory && (item.name.lowercase().endsWith(\".cbz\") || item.name.lowercase().endsWith(\".cbr\"))) {\n            NexoComicViewerState.open(java.io.File(item.path))\n            return\n        }"
    ft = ft.replace(target, replacement)

# Pestañas y host de cómics arriba de la barra de ruta
if "NexoExplorerTabBar(" not in ft:
    ft = ft.replace(
        "BreadcrumbBar(",
        "NexoComicViewerHost()\n                        NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)\n                        BreadcrumbBar(",
        1
    )

# Retroceso universal por carpeta (BackHandler integrado al sistema)
if "BackHandler" not in ft:
    ft = ft.replace(
        "val uiState by viewModel.uiState.collectAsState()",
        "val uiState by viewModel.uiState.collectAsState()\n    androidx.activity.compose.BackHandler(enabled = uiState.currentPath != \"/storage/emulated/0\") { onNavigateUp() }"
    )

open(files_path, "w").write(ft)
print("FilesScreen parcheado.")

# 2. VaultScreen.kt
vault_path = os.path.join(java_dir, "ui/screens/VaultScreen.kt")
vt = open(vault_path).read()

if "import com.example.ui.components.*" not in vt:
    vt = vt.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "NexoSecureVaultSummaryPanel(" not in vt:
    vt = vt.replace(
        "LazyColumn(",
        "NexoSecureVaultSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, onOpenDriveBackupModal = { showDriveBackupModal = true })\n            LazyColumn(",
        1
    )

open(vault_path, "w").write(vt)
print("VaultScreen parcheado.")

# 3. SettingsScreen.kt
settings_path = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
st = open(settings_path).read()

if "import com.example.ui.components.*" not in st:
    st = st.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.*\n", 1)

if "NexoEvaThemeSelectorCard(" not in st:
    st = st.replace(
        "LazyColumn(",
        "NexoEvaThemeSelectorCard()\n        NexoAutoUpdateSettingsCard()\n        LazyColumn(",
        1
    )

open(settings_path, "w").write(st)
print("SettingsScreen parcheado.")
