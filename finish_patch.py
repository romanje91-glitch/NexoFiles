import re

# 1. ToolsAndNetworkViewModel.kt: Mantener isAuthorized = true cuando ya existe driveAccountEmail
vm_path = "app/src/main/java/com/example/ui/viewmodel/ToolsAndNetworkViewModel.kt"
with open(vm_path, "r", encoding="utf-8") as f:
    vm = f.read()

vm_new = re.sub(
    r'container\.preferences\.driveAccountEmail\.collect\s*\{\s*email\s*->[\s\S]*?\}',
    '''container.preferences.driveAccountEmail.collect { email ->
                val hasAccount = !email.isNullOrBlank()
                _driveState.value = _driveState.value.copy(accountEmail = email, isAuthorized = hasAccount)
                _googlePhotosState.value = _googlePhotosState.value.copy(accountEmail = email, isAuthorized = hasAccount)
            }''',
    vm,
    count=1
)
if vm_new != vm:
    with open(vm_path, "w", encoding="utf-8") as f:
        f.write(vm_new)
    print("OK 1/2: Sesión persistente Google Drive / Photos aplicada")

# 2. MainActivity.kt: Apertura directa en visores internos para la Bóveda
ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

ma_new = re.sub(
    r'fun openFileSmart\(([\s\S]*?)\)\s*\{',
    r'''fun openFileSmart(\1, preferInternal: Boolean = false) {
        if (preferInternal && !forceChooser) {
            val ext = file.extension.lowercase()
            val internalHandler = when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "internal:image"
                "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> "internal:video"
                "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" -> "internal:audio"
                "pdf" -> "internal:pdf"
                else -> "internal:text"
            }
            executeInternalViewer(internalHandler, file, siblingGallery.ifEmpty { listOf(file.absolutePath) })
            return
        }''',
    ma,
    count=1
)
ma_new = ma_new.replace(
    "openFileSmart(tempFile, listOf(tempFile.absolutePath))",
    "openFileSmart(tempFile, listOf(tempFile.absolutePath), preferInternal = true)"
)
ma_new = ma_new.replace(
    "onBack = { navController.popBackStack() }) },",
    "onBack = { toolsVm.lockVault(); navController.popBackStack() }) },"
)

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma_new)
print("OK 2/2: Visor interno directo en Bóveda aplicado")
