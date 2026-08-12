package com.personal.twelveweek.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Opens a native browser file picker restricted to `.json`, reads the
 * chosen file as text, and calls [onResult] with its contents (empty string
 * if the user cancels or the read fails) — the wasmJs counterpart to the
 * Android app's `ActivityResultContracts.GetContent()` import flow. A fresh
 * `<input type=file>` is created per call and never attached to the DOM;
 * the browser still shows its native picker UI when `.click()` is called on
 * a detached element.
 */
@OptIn(ExperimentalWasmJsInterop::class)
fun pickJsonFile(onResult: (String) -> Unit) {
    jsPickJsonFile(onResult)
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(onResult) => { try { var input = document.createElement('input'); input.type = 'file'; input.accept = '.json,application/json'; input.onchange = function() { var file = input.files && input.files[0]; if (!file) { onResult(''); return; } var reader = new FileReader(); reader.onload = function() { onResult(String(reader.result || '')); }; reader.onerror = function() { onResult(''); }; reader.readAsText(file); }; input.click(); } catch (e) { onResult(''); } }"
)
private external fun jsPickJsonFile(onResult: (String) -> Unit)
