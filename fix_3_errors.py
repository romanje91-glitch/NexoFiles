# 1. Corregir coma doble en openFileSmart y LocalLifecycleOwner en MainActivity.kt
ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

ma = ma.replace(",\n    , preferInternal: Boolean = false", ",\n        preferInternal: Boolean = false")
ma = ma.replace(",, preferInternal: Boolean = false", ", preferInternal: Boolean = false")
ma = ma.replace("        lifecycle.addObserver(observer)\n        onDispose { lifecycle.removeObserver(observer) }",
                "        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current\n        owner.lifecycle.addObserver(observer)\n        onDispose { owner.lifecycle.removeObserver(observer) }")
# Por si quedó lifecycle sin LocalLifecycleOwner en el DisposableEffect:
ma = ma.replace(
    "    androidx.compose.runtime.DisposableEffect(Unit) {\n        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\n            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {\n                toolsVm.lockVault()\n            }\n        }\n        lifecycle.addObserver(observer)\n        onDispose { lifecycle.removeObserver(observer) }\n    }",
    "    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current\n    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {\n        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\n            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {\n                toolsVm.lockVault()\n            }\n        }\n        lifecycleOwner.lifecycle.addObserver(observer)\n        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }\n    }"
)
with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)
print("1/3 MainActivity.kt corregido")

# 2. Mover LaunchedEffect debajo de activeCloudTab en GoogleDriveScreen.kt
gd_path = "app/src/main/java/com/example/ui/screens/GoogleDriveScreen.kt"
with open(gd_path, "r", encoding="utf-8") as f:
    gd = f.read()

hook = """LaunchedEffect(driveState.isAuthorized, driveState.accessToken) {
        if (driveState.isAuthorized && driveState.accessToken.isNullOrBlank() && activity != null) {
            onConnectDrive(activity)
        }
    }
    LaunchedEffect(activeCloudTab, photosState.isAuthorized, photosState.accessToken) {
        if (activeCloudTab == 1 && photosState.isAuthorized && photosState.accessToken.isNullOrBlank() && activity != null) {
            onConnectGooglePhotos(activity)
        }
    }"""

if hook in gd:
    gd = gd.replace("    " + hook + "\n", "", 1)
    target = "var activeCloudTab by remember { mutableIntStateOf(0) }"
    if target not in gd:
        target = "var activeCloudTab by remember { mutableStateOf(0) }"
    if target in gd:
        gd = gd.replace(target, target + "\n    " + hook, 1)
with open(gd_path, "w", encoding="utf-8") as f:
    f.write(gd)
print("2/3 GoogleDriveScreen.kt corregido")

# 3. Corregir strings.vaultUnlockBtn en VaultScreen.kt
vs_path = "app/src/main/java/com/example/ui/screens/VaultScreen.kt"
with open(vs_path, "r", encoding="utf-8") as f:
    vs = f.read()

vs = vs.replace("strings.vaultUnlockBtn", '"Unlock Vault"')
with open(vs_path, "w", encoding="utf-8") as f:
    f.write(vs)
print("3/3 VaultScreen.kt corregido")
