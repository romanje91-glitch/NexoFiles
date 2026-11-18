ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

observer_block = """            androidx.compose.runtime.DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        toolsVm.lockVault()
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
            }"""

# 1. Quitar cualquier ocurrencia anterior de observer_block
ma = ma.replace(observer_block + "\n", "")
ma = ma.replace(observer_block, "")

# 2. Insertarlo justo antes de 'val navController = rememberNavController()' donde todos los ViewModels ya están inicializados
target = "val navController = rememberNavController()"
if target in ma:
    ma = ma.replace(target, observer_block.strip() + "\n    " + target, 1)
    print("OK: Observador ON_STOP colocado antes de navController")
else:
    print("WARN: No se encontró navController")

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)
