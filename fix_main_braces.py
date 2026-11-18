import os, re

base_dir = os.path.expanduser("~/nexofiles_build")
java_dir = os.path.join(base_dir, "app/src/main/java/com/example")

# 1. Reparar llaves exactas en MainActivity.kt (entre composable(NexoRoutes.HOME) y HorizontalPager)
main_path = os.path.join(java_dir, "MainActivity.kt")
mc = open(main_path).read()

clean_home_header = """composable(NexoRoutes.HOME) {
                val activeTabId = bottomTabs.getOrNull(pagerState.currentPage)?.id ?: "HOME"
                BackHandler(enabled = (activeTabId != "HOME" || explorerState.currentPath != "/storage/emulated/0") && !drawerState.isOpen) {
                    when {
                        explorerState.currentPath != "/storage/emulated/0" && explorerVm.navigateUp() -> Unit
                        activeTabId == "DRIVE" && driveState.isAuthorized && toolsVm.navigateDriveUp() -> Unit
                        else -> {
                            val homeIndex = bottomTabs.indexOfFirst { it.id == "HOME" }.coerceAtLeast(0)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = homeIndex,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                }

                HorizontalPager("""

mc = re.sub(
    r'composable\(NexoRoutes\.HOME\)\s*\{.*?HorizontalPager\(',
    clean_home_header,
    mc,
    count=1,
    flags=re.DOTALL
)
open(main_path, "w").write(mc)
print("1. Llaves de MainActivity.kt restauradas y balanceadas.")

# 2. Vincular URL de actualizaciones OTA a https://github.com/romanje91-glitch/NexoFiles
ota_path = os.path.join(java_dir, "ui/components/NexoVaultAndComicAdvanced.kt")
if os.path.exists(ota_path):
    ot = open(ota_path).read()
    ot = ot.replace(
        "https://github.com/nexofiles/releases/latest/download/NexoFiles.apk",
        "https://github.com/romanje91-glitch/NexoFiles/releases/latest/download/NexoFiles.apk"
    )
    open(ota_path, "w").write(ot)
    print("2. Servidor OTA vinculado a github.com/romanje91-glitch/NexoFiles.")
