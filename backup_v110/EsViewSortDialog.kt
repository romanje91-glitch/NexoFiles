package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme

@Composable
fun EsViewSortDialog(
  currentMode: String,
  sortBy: String,
  sortAsc: Boolean,
  showSortSection: Boolean = true,
  onSelectMode: (String) -> Unit,
  onSelectSort: (String, Boolean) -> Unit = { _, _ -> },
  onDismiss: () -> Unit,
) {
  val viewOptions = listOf(
    listOf("LARGE_ICON" to "Large Icon", "MEDIUM_ICON" to "Medium Icon", "SMALL_ICON" to "Small Icon"),
    listOf("LARGE_LIST" to "Large List", "MEDIUM_LIST" to "Medium List", "SMALL_LIST" to "Small List"),
    listOf("LARGE_DETAIL" to "Large Detail", "MEDIUM_DETAIL" to "Medium Detail", "SMALL_DETAIL" to "Small Detail"),
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NexoTheme.semantic.modalSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    shape = CutCornerShape(8.dp),
    title = {
      Text(
        text = "MODO DE VISTA // HUD",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewOptions.forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            row.forEach { (key, label) ->
              val selected = currentMode == key
              val tileShape = CutCornerShape(4.dp)
              Column(
                modifier = Modifier
                  .weight(1f)
                  .clip(tileShape)
                  .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surface
                  )
                  .border(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = tileShape,
                  )
                  .clickable { onSelectMode(key) }
                  .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  imageVector = if (key.endsWith("_ICON")) NexoIcons.GridView else NexoIcons.ViewList,
                  contentDescription = label,
                  tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = label.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }
        if (showSortSection) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
          Text(
            text = "ORDENAMIENTO TÁCTICO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
          val sortCols = listOf("NAME" to "NOMBRE", "TYPE" to "TIPO", "SIZE" to "TAMAÑO")
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            sortCols.forEach { (key, title) ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(true to "↑", false to "↓").forEach { (asc, arrow) ->
                  val active = sortBy == key && sortAsc == asc
                  val cellShape = CutCornerShape(4.dp)
                  Box(
                    modifier = Modifier
                      .size(38.dp)
                      .clip(cellShape)
                      .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                      .border(
                        width = 1.dp,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = cellShape,
                      )
                      .clickable { onSelectSort(key, asc) },
                    contentAlignment = Alignment.Center,
                  ) {
                    Text(
                      text = arrow,
                      style = MonoTnumStyle,
                      fontWeight = FontWeight.Bold,
                      color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
      Button(
        onClick = onDismiss,
        shape = CutCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      ) {
        Text("CERRAR", fontWeight = FontWeight.Bold)
      }
    },
  )
}
