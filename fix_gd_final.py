gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

# 1. Importar EsViewSortDialog
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

# 3. Conectar botón View al diálogo y ocultar servidores WebDAV fallidos
gd = gd.replace("gridView = !gridView", "showEsViewDialog = true")
gd = gd.replace("it.protocol == RemoteProtocol.WEBDAV.name", "false")

# 4. Ordenar visibleDriveFiles directamente dentro de items(...) sin remember (evita error de @Composable en LazyGridScope)
old_items = "items(visibleDriveFiles, key = { it.id }) { file ->"
new_items = """items(
                visibleDriveFiles.sortedWith(
                    compareByDescending<com.example.data.model.DriveFileItem> { it.isFolder }
                        .thenComparator { a, b ->
                            val cmp = when (esSortBy) {
                                "SIZE" -> a.sizeBytes.compareTo(b.sizeBytes)
                                "TYPE" -> a.name.substringAfterLast('.', "").compareTo(b.name.substringAfterLast('.', ""), ignoreCase = true)
                                else -> a.name.compareTo(b.name, ignoreCase = true)
                            }
                            if (esSortAsc) cmp else -cmp
                        }
                ),
                key = { it.id }
            ) { file ->"""

gd = gd.replace(old_items, new_items, 1)

# 5. Ajustar densidad visual según el modo elegido
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
print("GoogleDriveScreen.kt listo")
