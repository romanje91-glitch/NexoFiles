import re

ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

# 1. Eliminar coma doble/suelta antes de preferInternal
ma = re.sub(r'forceChooser:\s*Boolean\s*=\s*false,\s*,\s*preferInternal', 'forceChooser: Boolean = false,\n        preferInternal', ma)

# 2. Usar this@MainActivity.lifecycle en el DisposableEffect (no requiere contexto @Composable)
ma = ma.replace(
    "val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current\n    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {\n        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\n            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {\n                toolsVm.lockVault()\n            }\n        }\n        lifecycleOwner.lifecycle.addObserver(observer)\n        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }\n    }",
    "androidx.compose.runtime.DisposableEffect(Unit) {\n        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->\n            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {\n                toolsVm.lockVault()\n            }\n        }\n        this@MainActivity.lifecycle.addObserver(observer)\n        onDispose { this@MainActivity.lifecycle.removeObserver(observer) }\n    }"
)

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)
print("MainActivity.kt corregido")
