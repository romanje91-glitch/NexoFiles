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

# Quitar de arriba (antes de val toolsVm)
if observer_block in ma:
    ma = ma.replace(observer_block + "\n", "", 1)

# Insertarlo justo después de la línea donde se declara val toolsVm
lines = ma.splitlines()
new_lines = []
inserted = False
for line in lines:
    new_lines.append(line)
    if not inserted and "val toolsVm" in line and "viewModel" in line:
        new_lines.append(observer_block)
        inserted = True

with open(ma_path, "w", encoding="utf-8") as f:
    f.write("\n".join(new_lines) + "\n")
print("Observador movido debajo de val toolsVm:", inserted)
