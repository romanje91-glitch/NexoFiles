import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")

# 1. Corregir MainActivity.kt (limpiar error de tipeo en BackHandler)
main_path = os.path.join(java_dir, "MainActivity.kt")
mc = open(main_path).read()
mc = mc.replace("otfaith", "currentPath")
# Asegurar BackHandler limpio y sin conflictos
mc = re.sub(
    r'BackHandler\(enabled = \(activeTabId != "HOME" \|\|.*?\}\n\s*\}\n',
    'BackHandler(enabled = (activeTabId == "FILES" && explorerVm.uiState.value.currentPath != "/storage/emulated/0") || activeTabId != "HOME") {\n        if (activeTabId == "FILES") explorerVm.navigateUp()\n    }\n',
    mc,
    flags=re.DOTALL
)
open(main_path, "w").write(mc)
print("1. MainActivity corregido.")

# 2. Corregir ToolsAndNetworkViewModel.kt (declarar effectivePassphrase)
vm_path = os.path.join(java_dir, "ui/viewmodel/ToolsAndNetworkViewModel.kt")
vm = open(vm_path).read()
if "val effectivePassphrase" not in vm:
    vm = vm.replace(
        "fun backupVaultToDrive(",
        "val effectivePassphrase = \"NEXO_HERMETIC_AUTO_KEY_2026\"\n    fun backupVaultToDrive("
    )
open(vm_path, "w").write(vm)
print("2. ToolsAndNetworkViewModel corregido.")

