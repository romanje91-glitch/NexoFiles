import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")

# 1. Actualizar Theme.kt para que lea el tema EVA global activo en tiempo real
theme_path = os.path.join(java_dir, "ui/theme/Theme.kt")
tc = open(theme_path).read()

if "NexoEvaThemeManager.currentTheme" not in tc:
    target_theme_func = "fun NexoFilesTheme(\n    themeMode: ThemeMode = ThemeMode.DARK,\n    darkTheme: Boolean = resolveDarkTheme(themeMode),\n    content: @Composable () -> Unit\n) {"
    replacement = target_theme_func + "\n    val currentEva = com.example.ui.components.NexoEvaThemeManager.currentTheme"
    tc = tc.replace(target_theme_func, replacement)
    
    # Inyectar paletas según EVA seleccionado
    palette_hook = "val colorScheme = if (darkTheme) NexoHudDarkColorScheme else NexoHudLightColorScheme"
    eva_palette_code = """val colorScheme = when (com.example.ui.components.NexoEvaThemeManager.currentTheme) {
        "EVA-00" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.ui.graphics?.Color?.Unspecified ?: androidx.compose.ui.graphics.Color(0xFFFFB703)) else NexoHudLightColorScheme
        "EVA-02" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFFFF4D6D)) else NexoHudLightColorScheme
        "EVA-03" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFFADB5BD)) else NexoHudLightColorScheme
        "EVA-04" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFFE9ECEF)) else NexoHudLightColorScheme
        "EVA-05" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFF99D98C)) else NexoHudLightColorScheme
        "EVA-08" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFFFF007F)) else NexoHudLightColorScheme
        "EVA-13" -> if (darkTheme) NexoHudDarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(0xFF00B4D8)) else NexoHudLightColorScheme
        else -> if (darkTheme) NexoHudDarkColorScheme else NexoHudLightColorScheme
    }"""
    tc = tc.replace(palette_hook, eva_palette_code)
    open(theme_path, "w").write(tc)
    print("Theme.kt actualizado con soporte EVA global.")

# 2. Limpiar SettingsScreen.kt (Remover la tarjeta duplicada de actualizaciones de arriba, manteniendo sólo 5. SYSTEM & ABOUT)
settings_path = os.path.join(java_dir, "ui/screens/SettingsScreen.kt")
if os.path.exists(settings_path):
    st = open(settings_path).read()
    st = st.replace("item { NexoAutoUpdateSettingsCard() }\n        ", "")
    open(settings_path, "w").write(st)
    print("SettingsScreen limpiado (sin duplicados).")

# 3. Corregir BackHandler en MainActivity.kt para que Carpetas / Drive permitan retroceder sin salir al Home
main_path = os.path.join(java_dir, "MainActivity.kt")
mc = open(main_path).read()
if "BackHandler(enabled = activeTabId == \"FILES\"" not in mc:
    old_bh = 'BackHandler(enabled = activeTabId != "HOME" && !drawerState.isOpen) {'
    new_bh = 'BackHandler(enabled = activeTabId == "FILES" && explorerVm.uiState.value.currentPath != "/storage/emulated/0" && !drawerState.isOpen) { explorerVm.navigateUp()\n    }\n    BackHandler(enabled = activeTabId != "HOME" && activeTabId != "FILES" && !drawerState.isOpen) {'
    mc = mc.replace(old_bh, new_bh)
    open(main_path, "w").write(mc)
    print("MainActivity.kt corregido para retroceso universal por carpetas.")

