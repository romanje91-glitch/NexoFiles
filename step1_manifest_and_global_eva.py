import os, re

base_dir = os.path.expanduser("~/nexofiles_build/app/src/main")

# 1. AndroidManifest.xml: Evitar reinicio y pantalla negra al rotar horizontal/vertical
manifest_path = os.path.join(base_dir, "AndroidManifest.xml")
mf = open(manifest_path).read()
if "android:configChanges=" not in mf:
    mf = mf.replace(
        'android:name=".MainActivity"',
        'android:name=".MainActivity"\n            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"'
    )
    open(manifest_path, "w").write(mf)
    print("1. AndroidManifest.xml actualizado (rotacion fluida sin pantalla negra).")
else:
    print("1. AndroidManifest.xml ya tenia configChanges.")

# 2. NexoEvaThemes.kt: Paletas completas (Primario, Secundario, Superficie, Borde, Fondo) para las 8 Unidades EVA
eva_path = os.path.join(base_dir, "java/com/example/ui/components/NexoEvaThemes.kt")
open(eva_path, "w").write("""package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

data class EvaUnitPalette(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val bgPrimary: Color,
    val surfaceCard: Color,
    val surfaceTactical: Color,
    val border: Color
)

object NexoEvaThemeManager {
    private const val PREFS = "nexo_eva_theme_prefs"
    private const val KEY_THEME = "eva_unit_theme"

    var currentTheme by mutableStateOf("EVA-01")

    val units = listOf(
        EvaUnitPalette("EVA-00", "Unidad 00 / EVA-00 (Prototipo Ámbar)", Color(0xFFFFB703), Color(0xFFFB8500), Color(0xFFE0E1DD), Color(0xFF12100B), Color(0xFF1D1912), Color(0xFF262118), Color(0xFF6B5314)),
        EvaUnitPalette("EVA-01", "Unidad 01 / EVA-01 (Shinji • Morado/Verde)", Color(0xFF00FF66), Color(0xFF9D4EDD), Color(0xFFFF6D00), Color(0xFF0A0D14), Color(0xFF121722), Color(0xFF1A2030), Color(0xFF4A2574)),
        EvaUnitPalette("EVA-02", "Unidad 02 / EVA-02 (Asuka • Rojo/Naranja)", Color(0xFFFF2A54), Color(0xFFFF7B00), Color(0xFFFFB703), Color(0xFF14090B), Color(0xFF221014), Color(0xFF2E161B), Color(0xFF7A1C2E)),
        EvaUnitPalette("EVA-03", "Unidad 03 / EVA-03 (Negro Táctico/Acero)", Color(0xFF4EA8DE), Color(0xFFADB5BD), Color(0xFFE63946), Color(0xFF0B0D10), Color(0xFF14181D), Color(0xFF1D2229), Color(0xFF3D4957)),
        EvaUnitPalette("EVA-04", "Unidad 04 / EVA-04 (Plateado/Cromo)", Color(0xFFE9ECEF), Color(0xFF90E0EF), Color(0xFFCED4DA), Color(0xFF0F1114), Color(0xFF181B20), Color(0xFF22262D), Color(0xFF5C677D)),
        EvaUnitPalette("EVA-05", "Unidad 05 / EVA-05 (Rebuild • Verde/Ámbar)", Color(0xFF80ED99), Color(0xFFFFB703), Color(0xFF57CC99), Color(0xFF0B1310), Color(0xFF12201B), Color(0xFF1A2D26), Color(0xFF2D6A4F)),
        EvaUnitPalette("EVA-08", "Unidad 08 / EVA-08 (Rebuild • Rosa/Cian)", Color(0xFFFF4D94), Color(0xFF00F5D4), Color(0xFFFFB703), Color(0xFF140A12), Color(0xFF22121E), Color(0xFF2F1929), Color(0xFF802352)),
        EvaUnitPalette("EVA-13", "Unidad 13 / EVA-13 (Rebuild • Violeta/Oro)", Color(0xFFC77DFF), Color(0xFFFFD166), Color(0xFF00BBF9), Color(0xFF0D0914), Color(0xFF171124), Color(0xFF211833), Color(0xFF5A189A))
    )

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentTheme = sp.getString(KEY_THEME, "EVA-01") ?: "EVA-01"
    }

    fun setTheme(context: Context, themeName: String) {
        currentTheme = themeName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, themeName).apply()
    }

    val activePalette: EvaUnitPalette
        get() = units.firstOrNull { it.id == currentTheme } ?: units[1]

    val primaryColor: Color get() = activePalette.primary
    val surfaceColor: Color get() = activePalette.surfaceCard
}
""")
print("2. NexoEvaThemes.kt actualizado con las 8 Unidades EVA.")

# 3. Theme.kt: Conectar tanto MaterialTheme.colorScheme como NexoTheme.semantic a la Unidad EVA seleccionada
theme_path = os.path.join(base_dir, "java/com/example/ui/theme/Theme.kt")
tc = open(theme_path).read()

# Reemplazar la funcion NexoFilesTheme completa para que aplique toda la paleta EVA globalmente
idx = tc.find("@Composable\nfun NexoFilesTheme(")
if idx != -1:
    tc = tc[:idx] + """@Composable
fun NexoFilesTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    darkTheme: Boolean = resolveDarkTheme(themeMode),
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.ui.components.NexoEvaThemeManager.init(context)
    }
    val eva = com.example.ui.components.NexoEvaThemeManager.activePalette
    val baseScheme = if (darkTheme) NexoHudDarkColorScheme else NexoHudLightColorScheme
    val colorScheme = baseScheme.copy(
        primary = eva.primary,
        secondary = eva.secondary,
        tertiary = eva.accent,
        background = if (darkTheme) eva.bgPrimary else baseScheme.background,
        surface = if (darkTheme) eva.surfaceCard else baseScheme.surface,
        surfaceVariant = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceVariant,
        surfaceContainerLowest = if (darkTheme) eva.surfaceCard else baseScheme.surfaceContainerLowest,
        surfaceContainerLow = if (darkTheme) eva.bgPrimary else baseScheme.surfaceContainerLow,
        surfaceContainer = if (darkTheme) eva.surfaceCard else baseScheme.surfaceContainer,
        surfaceContainerHigh = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceContainerHigh,
        surfaceContainerHighest = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceContainerHighest,
        outline = if (darkTheme) eva.border else baseScheme.outline,
        outlineVariant = eva.secondary.copy(alpha = 0.65f)
    )
    val baseSemantic = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val semanticColors = baseSemantic.copy(
        evaGreen = eva.primary,
        evaPurple = eva.secondary,
        evaOrange = eva.accent,
        warning = eva.accent,
        primarySoft = eva.primary.copy(alpha = 0.18f),
        secondarySoft = eva.secondary.copy(alpha = 0.22f),
        tacticalOrange = eva.accent,
        tacticalAmber = eva.accent,
        tacticalSurface = if (darkTheme) eva.surfaceTactical else baseSemantic.tacticalSurface,
        modalSurface = if (darkTheme) eva.surfaceCard else baseSemantic.modalSurface,
        hudBorder = if (darkTheme) eva.border else baseSemantic.hudBorder
    )
    CompositionLocalProvider(LocalNexoSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexoTypography,
            shapes = NexoHudShapes,
            content = content,
        )
    }
}
"""
    open(theme_path, "w").write(tc)
    print("3. Theme.kt conectado globalmente a las 8 Unidades EVA.")
