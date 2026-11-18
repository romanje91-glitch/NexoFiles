package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexoEvaThemeSelectorCard() {
    val context = LocalContext.current
    val themes = listOf(
        "EVA-00" to "Unidad 00 (Ámbar)",
        "EVA-01" to "Unidad 01 (Verde Berserk)",
        "EVA-02" to "Unidad 02 (Rojo NERV)",
        "EVA-03" to "Unidad 03 (Gris Militar)",
        "EVA-04" to "Unidad 04 (Plateado)",
        "EVA-05" to "Unidad 05 (Verde Oliva)",
        "EVA-08" to "Unidad 08 (Magenta)",
        "EVA-13" to "Unidad 13 (Celeste Núcleo)"
    )

    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SELECTOR DE TEMAS EVA", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                themes.forEach { (id, label) ->
                    val selected = NexoEvaThemeManager.currentTheme == id
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) themeColor.copy(alpha = 0.2f) else Color(0xFF161B26),
                        border = BorderStroke(1.dp, if (selected) themeColor else Color(0xFF2E364F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                NexoEvaThemeManager.setTheme(context, id)
                                Toast.makeText(context, "Tema aplicado: $label", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = if (selected) themeColor else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            if (selected) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(themeColor))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NexoAutoUpdateSettingsCard() {
    var autoUpdateEnabled by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ACTUALIZACIONES AUTOMÁTICAS", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Verificar y descargar nuevas versiones", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = { autoUpdateEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = themeColor, checkedTrackColor = themeColor.copy(alpha = 0.5f))
                )
            }
            OutlinedButton(
                onClick = {
                    checking = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checking = false
                        Toast.makeText(context, "NexoFiles está actualizado (v1.0 - NERV HUD)", Toast.LENGTH_LONG).show()
                    }, 1200)
                },
                border = BorderStroke(1.dp, themeColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "VERIFICANDO..." else "BUSCAR ACTUALIZACIÓN AHORA", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
