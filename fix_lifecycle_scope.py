ma_path = "app/src/main/java/com/example/MainActivity.kt"
with open(ma_path, "r", encoding="utf-8") as f:
    ma = f.read()

old_block = """DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        toolsVm.lockVault()
                    }
                }
                androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.addObserver(observer)
                onDispose { androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.removeObserver(observer) }
            }"""

new_block = """val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                toolsVm.lockVault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }"""

ma = ma.replace("androidx.compose.runtime." + old_block, new_block)
ma = ma.replace(old_block, new_block)

with open(ma_path, "w", encoding="utf-8") as f:
    f.write(ma)
print("OK: lifecycleOwner movido al ámbito @Composable")
