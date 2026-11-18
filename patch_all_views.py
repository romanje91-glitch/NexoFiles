import os

# 1. Crear el componente global EsViewSortDialog.kt en com.example.ui.components
comp_dir = "app/src/main/java/com/example/ui/components"
os.makedirs(comp_dir, exist_ok=True)
dialog_file = os.path.join(comp_dir, "EsViewSortDialog.kt")

dialog_source = """package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EsViewSortDialog(
    currentMode: String,
    sortBy: String,
    sortAsc: Boolean,
    showSortSection: Boolean = true,
    onSelectMode: (String) -> Unit,
    onSelectSort: (String, Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val viewOptions = listOf(
        listOf("LARGE_ICON" to "Large Icon", "MEDIUM_ICON" to "Medium Icon", "SMALL_ICON" to "Small Icon"),
        listOf("LARGE_LIST" to "Large List", "MEDIUM_LIST" to "Medium List", "SMALL_LIST" to "Small List"),
        listOf("LARGE_DETAIL" to "Large Detail", "MEDIUM_DETAIL" to "Medium Detail", "SMALL_DETAIL" to "Small Detail")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("View", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewOptions.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { (key, label) ->
                            val selected = currentMode == key
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (selected) 1.5.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectMode(key) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (key.endsWith("_ICON")) NexoIcons.GridView else NexoIcons.ViewList,
                                    contentDescription = label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                if (showSortSection) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Sort",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    val sortCols = listOf("NAME" to "Name", "TYPE" to "Type", "SIZE" to "Size")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        sortCols.forEach { (key, title) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, style = MaterialTheme.typography.labelSmall)
                                listOf(true to "↑", false to "↓").forEach { (asc, arrow) ->
                                    val active = sortBy == key && sortAsc == asc
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(
                                                width = if (active) 1.5.dp else 0.5.dp,
                                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectSort(key, asc) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = arrow,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
"""
with open(dialog_file, "w", encoding="utf-8") as f:
    f.write(dialog_source)
print("1/4 EsViewSortDialog.kt creado")

# 2. Actualizar GoogleDriveScreen.kt
gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

if "import com.example.ui.components.EsViewSortDialog" not in gd:
    gd = gd.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.EsViewSortDialog\nimport androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.padding\n", 1)

gd_state = """var gridView by remember { mutableStateOf(false) }
    var showEsViewDialog by remember { mutableStateOf(false) }
    var esViewMode by remember { mutableStateOf("MEDIUM_LIST") }
    var esSortBy by remember { mutableStateOf("NAME") }
    var esSortAsc by remember { mutableStateOf(true) }"""

if "var gridView by remember { mutableStateOf(false) }" in gd:
    gd = gd.replace("var gridView by remember { mutableStateOf(false) }", gd_state, 1)

gd = gd.replace("gridView = !gridView", "showEsViewDialog = true")
gd = gd.replace(
    "items(linkedCloudServers.filter { it.protocol == RemoteProtocol.WEBDAV.name }",
    "items(emptyList<com.example.data.model.RemoteServerConfig>()"
)

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

gd_dialog_call = """
    if (showEsViewDialog) {
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
"""
anchor_gd = "@Composable\nprivate fun CloudBottomAction("
if anchor_gd in gd:
    pos = gd.rfind("}", 0, gd.find(anchor_gd))
    gd = gd[:pos] + gd_dialog_call + "\n}\n\n" + gd[gd.find(anchor_gd):]

with open(gd_path, "w", encoding="utf-8") as f:
    f.write(gd)
print("2/4 GoogleDriveScreen.kt actualizado")

# 3. Actualizar NetworkScreen.kt para abrir EsViewSortDialog
net_path = "app/src/main/java/com/example/ui/screens/NetworkScreen.kt"
with open(net_path, "r", encoding="utf-8") as f:
    net = f.read()

if "import com.example.ui.components.EsViewSortDialog" not in net:
    net = net.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.EsViewSortDialog\n", 1)

net_state = """var compactView by remember { mutableStateOf(false) }
    var showNetEsViewDialog by remember { mutableStateOf(false) }
    var netEsViewMode by remember { mutableStateOf("MEDIUM_LIST") }"""

if "var compactView by remember { mutableStateOf(false) }" in net:
    net = net.replace("var compactView by remember { mutableStateOf(false) }", net_state, 1)

net = net.replace("compactView = !compactView", "showNetEsViewDialog = true")

net_dialog_call = """
    if (showNetEsViewDialog) {
        EsViewSortDialog(
            currentMode = netEsViewMode,
            sortBy = "NAME",
            sortAsc = true,
            showSortSection = false,
            onSelectMode = {
                netEsViewMode = it
                compactView = it.startsWith("SMALL_") || it.endsWith("_ICON")
                showNetEsViewDialog = false
            },
            onDismiss = { showNetEsViewDialog = false }
        )
    }
"""
anchor_net = "@Composable\nprivate fun NetworkBottomAction("
if anchor_net in net:
    pos = net.rfind("}", 0, net.find(anchor_net))
    net = net[:pos] + net_dialog_call + "\n}\n\n" + net[net.find(anchor_net):]

with open(net_path, "w", encoding="utf-8") as f:
    f.write(net)
print("3/4 NetworkScreen.kt actualizado")

# 4. Actualizar FilesScreen.kt para abrir EsViewSortDialog al pulsar el botón de vista
fs_path = "app/src/main/java/com/example/ui/screens/FilesScreen.kt"
with open(fs_path, "r", encoding="utf-8") as f:
    fs = f.read()

if "import com.example.ui.components.EsViewSortDialog" not in fs:
    fs = fs.replace("package com.example.ui.screens\n", "package com.example.ui.screens\n\nimport com.example.ui.components.EsViewSortDialog\n", 1)

old_cycle = """val entries = LayoutViewMode.entries
                        val next = entries[(entries.indexOf(layoutViewMode) + 1) % entries.size]
                        onChangeLayoutMode(next)"""

if old_cycle in fs:
    fs = fs.replace(
        "Scaffold(",
        "var showFilesEsViewDialog by remember { mutableStateOf(false) }\n    var filesEsViewMode by remember { mutableStateOf(\"MEDIUM_LIST\") }\n    if (showFilesEsViewDialog) {\n        EsViewSortDialog(\n            currentMode = filesEsViewMode,\n            sortBy = \"NAME\",\n            sortAsc = true,\n            showSortSection = false,\n            onSelectMode = {\n                filesEsViewMode = it\n                val mapped = when {\n                    it.endsWith(\"_ICON\") -> LayoutViewMode.GRID\n                    it.startsWith(\"SMALL_\") -> LayoutViewMode.COMPACT\n                    it.endsWith(\"_DETAIL\") -> LayoutViewMode.DETAILED\n                    else -> LayoutViewMode.LIST\n                }\n                onChangeLayoutMode(mapped)\n                showFilesEsViewDialog = false\n            },\n            onDismiss = { showFilesEsViewDialog = false }\n        )\n    }\n    Scaffold(",
        1
    )
    fs = fs.replace(old_cycle, "showFilesEsViewDialog = true", 1)

with open(fs_path, "w", encoding="utf-8") as f:
    f.write(fs)
print("4/4 FilesScreen.kt actualizado")
