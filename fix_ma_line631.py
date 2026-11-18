ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Eliminar el bloque DisposableEffect que quedó fuera de contexto @Composable alrededor de la línea 631
cleaned = []
skip = False
depth = 0
for i, line in enumerate(lines):
    if "DisposableEffect(" in line and "ON_STOP" in "".join(lines[i:i+10]):
        skip = True
        depth = line.count("{") - line.count("}")
        continue
    if skip:
        depth += line.count("{") - line.count("}")
        if depth <= 0:
            skip = False
        continue
    cleaned.append(line)

ma = "".join(cleaned)

# Registrar el observador ON_STOP directamente dentro de setContent { ... } (contexto @Composable raíz)
if "setContent {" in ma and "ON_STOP" not in ma:
    ma = ma.replace(
        "setContent {",
        """setContent {
            androidx.compose.runtime.DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        toolsVm.lockVault()
                    }
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
            }""",
        1
    )

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)
print("Línea 631 limpia y observador ON_STOP movido a setContent")
