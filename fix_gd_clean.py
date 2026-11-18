gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

# 1. Imports
gd = gd.replace(
    "package com.example.ui.screens\n",
    "package com.example.ui.screens\n\nimport com.example.ui.components.EsViewSortDialog\n",
    1
)

# 2. Estados de vista y ordenamiento
gd_state = """var gridView by remember { mutableStateOf(false) }
    var showEsViewDialog by remember { mutableStateOf(false) }
    var esViewMode by remember { mutableStateOf("MEDIUM_LIST") }
    var esSortBy by remember { mutableStateOf("NAME") }
    var esSortAsc by remember { mutableStateOf(true) }"""

gd = gd.replace("var gridView by remember { mutableStateOf(false) }", gd_state, 1)

# 3. Conectar botón View al diálogo y ocultar WebDAV
gd = gd.replace("gridView = !gridView", "showEsViewDialog = true")
gd = gd.replace(
    "items(linkedCloudServers.filter { it.protocol == RemoteProtocol.WEBDAV.name }",
    "items(emptyList<com.example.data.model.RemoteServerConfig>()"
)

# 4. Ordenar visibleDriveFiles dinámicamente sin romper llaves del LazyColumn
old_items = "items(visibleDriveFiles, key = { it.id }) { file ->"
new_items = """val sortedDriveFiles = remember(visibleDriveFiles, esSortBy, esSortAsc) {
                val base = when (esSortBy) {
                    "SIZE" -> visibleDriveFiles.sortedBy { it.sizeBytes }
                    "TYPE" -> visibleDriveFiles.sortedBy { it.name.substringAfterLast('.', "") }
                    else -> visibleDriveFiles.sortedBy { it.name.lowercase() }
                }
                val directed = if (esSortAsc) base else base.reversed()
                directed.sortedByDescending { it.isFolder }
            }
            items(sortedDriveFiles, key = { it.id }) { file ->"""

gd = gd.replace(old_items, new_items, 1)

# 5. Ajustar densidad visual según modo elegido (Compact / Detail / Icon)
gd = gd.replace(
    ".padding(horizontal = 14.dp, vertical = 10.dp)",
    ".padding(horizontal = if (esViewMode.startsWith(\"SMALL_\")) 10.dp else 14.dp, vertical = if (esViewMode.startsWith(\"SMALL_\")) 4.dp else if (esViewMode.startsWith(\"LARGE_\")) 16.dp else 10.dp)",
    1
)

# 6. Insertar EsViewSortDialog justo antes de showLinkCloudProviderDialog
gd_dialog_call = """if (showEsViewDialog) {
        EsViewSortDialog(
            currentMode = esViewMode,
            sortBy = esSortBy,
            sortAsc = esSortAsc,
            onSelectMode = {
                esViewMode = it
                gridView = it.endsWith("_ICON")
                showEsViewDialog = false
            },
            onSelectSort = { by, asc ->
                esSortBy = by
                esSortAsc = asc
                showEsViewDialog = false
            },
            onDismiss = { showEsViewDialog = false }
        )
    }
    if (showLinkCloudProviderDialog)"""

gd = gd.replace("if (showLinkCloudProviderDialog)", gd_dialog_call, 1)

with open(gd_path, "w", encoding="utf-8") as f:
    f.write(gd)
print("GoogleDriveScreen.kt parchado limpiamente")
