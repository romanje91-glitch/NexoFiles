# 1. Hacer instantáneas las transiciones del NavHost en MainActivity.kt
ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

nav_imports = """import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
"""
if "import androidx.compose.animation.EnterTransition" not in ma:
    ma = ma.replace("package com.example\n", "package com.example\n\n" + nav_imports, 1)

old_navhost = """NavHost(
                    navController = navController,
                    startDestination = NexoRoutes.HOME,
                    modifier = Modifier.weight(1f)
                )"""

new_navhost = """NavHost(
                    navController = navController,
                    startDestination = NexoRoutes.HOME,
                    modifier = Modifier.weight(1f),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                )"""

if old_navhost in ma:
    ma = ma.replace(old_navhost, new_navhost, 1)
    print("1/3 MainActivity.kt: Transiciones instantáneas aplicadas en NavHost")
else:
    # Respaldo por si la indentación difiere ligeramente
    ma = ma.replace(
        "startDestination = NexoRoutes.HOME,",
        "startDestination = NexoRoutes.HOME,\n                    enterTransition = { EnterTransition.None },\n                    exitTransition = { ExitTransition.None },\n                    popEnterTransition = { EnterTransition.None },\n                    popExitTransition = { ExitTransition.None },",
        1
    )
    print("1/3 MainActivity.kt: Transiciones instantáneas aplicadas (fallback)")

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)

# 2. Mantener sesión vinculada en ToolsAndNetworkViewModel.kt cuando ya existe cuenta guardada
vm_path = "app/src/main/java/com/example/ui/viewmodel/ToolsAndNetworkViewModel.kt"
with open(vm_path, "r", encoding="utf-8") as f:
    vm = f.read()

old_email_collect = """container.preferences.driveAccountEmail.collect { email ->
                _driveState.value = _driveState.value.copy(accountEmail = email)
                _googlePhotosState.value = _googlePhotosState.value.copy(accountEmail = email)
            }"""

new_email_collect = """container.preferences.driveAccountEmail.collect { email ->
                val hasAccount = !email.isNullOrBlank()
                _driveState.value = _driveState.value.copy(
                    accountEmail = email,
                    isAuthorized = hasAccount
                )
                _googlePhotosState.value = _googlePhotosState.value.copy(
                    accountEmail = email,
                    isAuthorized = hasAccount
                )
            }"""

if old_email_collect in vm:
    vm = vm.replace(old_email_collect, new_email_collect, 1)
    print("2/3 ToolsAndNetworkViewModel.kt: Persistencia de estado conectado aplicada")

with open(vm_path, "w", encoding="utf-8") as f:
    f.write(vm)

# 3. Auto-renovar token silenciosamente al entrar a GoogleDriveScreen si isAuthorized == true y accessToken == null
gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

silent_auth_hook = """LaunchedEffect(driveState.isAuthorized, driveState.accessToken) {
        if (driveState.isAuthorized && driveState.accessToken.isNullOrBlank() && activity != null) {
            onConnectDrive(activity)
        }
    }
    LaunchedEffect(activeCloudTab, photosState.isAuthorized, photosState.accessToken) {
        if (activeCloudTab == 1 && photosState.isAuthorized && photosState.accessToken.isNullOrBlank() && activity != null) {
            onConnectGooglePhotos(activity)
        }
    }"""

if "LaunchedEffect(driveState.isAuthorized, driveState.accessToken)" not in gd and "val activity = " in gd:
    pos = gd.find("\n", gd.find("val activity = "))
    gd = gd[:pos+1] + "    " + silent_auth_hook + "\n" + gd[pos+1:]
    with open(gd_path, "w", encoding="utf-8") as f:
        f.write(gd)
    print("3/3 GoogleDriveScreen.kt: Renovación automática de token añadida")
