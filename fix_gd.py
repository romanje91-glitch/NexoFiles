gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

# 1. Imports
gd = gd.replace(
    "package com.example.ui.screens\n",
    "package com.example.ui.screens\n\nimport com.example.ui.components.EsViewSortDialog\nimport androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.padding\n",
    1
)

# 2. Estados
gd_state = """var gridView by remember { mutableStateOf(false) }
    var showEsViewDialog by remember { mutableStateOf(false) }
    var esViewMode by remember { mutableStateOf("MEDIUM_LIST") }
    var esSortBy by remember { mutableStateOf("NAME") }
    var esSortAsc by remember { mutableStateOf(true) }"""

gd = gd.replace("var gridView by remember { mutableStateOf(false) }", gd_state, 1)

# 3. Acción del botón View y ocultar WebDAV
gd = gd.replace("gridView = !gridView", "showEsViewDialog = true")
gd = gd.replace(
    "items(linkedCloudServers.filter { it.protocol == RemoteProtocol.WEBDAV.name }",
    "items(emptyList<com.example.data.model.RemoteServerConfig>()"
)

# 4. Cuadrícula/Lista dinámica en visibleDriveFiles
target_items = "items(visibleDriveFiles, key = { it.id }) { file ->"
replacement_items = """val sortedDriveFiles = remember(visibleDriveFiles, esSortBy, esSortAsc) {
                val base = when (esSortBy) {
                    "SIZE" -> visibleDriveFiles.sortedBy { it.sizeBytes }
                    "TYPE" -> visibleDriveFiles.sortedBy { it.name.substringAfterLast('.', "") }
                    else -> visibleDriveFiles.sortedBy { it.name.lowercase() }
                }
                val directed = if (esSortAsc) base else base.reversed()
                directed.sortedByDescending { it.isFolder }
            }
            if (esViewMode.endsWith("_ICON")) {
                val cols = when (esViewMode) {
                    "LARGE_ICON" -> 2
                    "SMALL_ICON" -> 4
                    else -> 3
                }
                items(sortedDriveFiles.chunked(cols)) { rowFiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowFiles.forEach { file ->
                            BichromaticCard(
                                modifier = Modifier.weight(1f),
                                onClick = { if (file.isFolder) onOpenFolder(file) else onDownloadFile(file) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(if (cols == 4) 8.dp else 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconContainerBadge(
                                        icon = if (file.isFolder) NexoIcons.Folder else NexoIcons.InsertDriveFile,
                                        contentDescription = null,
                                        highlighted = file.isFolder,
                                        size = if (cols == 2) 48.dp else if (cols == 4) 32.dp else 40.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        repeat(cols - rowFiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(sortedDriveFiles, key = { it.id }) { file ->"""

if target_items in gd:
    idx = gd.find(target_items)
    brace_start = gd.find("{", idx)
    count = 0
    end_idx = brace_start
    for i in range(brace_start, len(gd)):
        if gd[i] == "{":
            count += 1
        elif gd[i] == "}":
            count -= 1
            if count == 0:
                end_idx = i
                break
    block = gd[idx:end_idx+1]
    new_block = block.replace(target_items, replacement_items, 1) + "\n            }"
    gd = gd[:idx] + new_block + gd[end_idx+1:]

# 5. Insertar EsViewSortDialog justo antes de showLinkCloudProviderDialog (dentro de la función Composable principal)
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
print("GoogleDriveScreen.kt corregido con éxito")
