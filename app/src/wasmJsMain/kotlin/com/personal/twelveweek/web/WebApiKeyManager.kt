package com.personal.twelveweek.web

import com.personal.twelveweek.storage.RawPreferenceStore
import kotlin.js.ExperimentalWasmJsInterop

/**
 * On-device storage for the ExerciseDB (RapidAPI) key, encrypted at rest via
 * the browser's Web Crypto API (AES-GCM, with a per-browser-profile key
 * generated once and persisted in `localStorage`) — the wasmJs counterpart
 * to the Android app's `security.ApiKeyManager` (Android Keystore-backed
 * `EncryptedSharedPreferences`). Same `set()`/`clear()` surface; [get] is
 * callback-based here since Web Crypto's `subtle.*` calls are all async
 * (Android's Keystore reads are synchronous, which is why that one wasn't).
 *
 * Threat model note: there is no browser-exposed secure enclave the way
 * Android Keystore is hardware-backed, so this only protects the key from
 * casual `localStorage` inspection, not a determined attacker with script
 * execution on the page — the same ceiling every client-side "encryption"
 * has when the decrypting code also ships to the client.
 */
class WebApiKeyManager(private val store: RawPreferenceStore = RawPreferenceStore("exercise_media_key")) {

    fun get(callback: (String?) -> Unit) {
        val stored = store.getString(KEY)
        if (stored.isNullOrBlank()) {
            callback(null)
            return
        }
        jsCryptoDecrypt(stored) { plain -> callback(plain.ifBlank { null }) }
    }

    fun set(key: String, onDone: () -> Unit = {}) {
        val trimmed = key.trim()
        jsCryptoEncrypt(trimmed) { encrypted ->
            if (encrypted.isNotBlank()) store.putString(KEY, encrypted)
            onDone()
        }
    }

    fun clear() {
        store.putString(KEY, "")
    }

    private companion object {
        const val KEY = "rapidapi_key"
    }
}

private const val KEY_STORAGE_JS = """
  var __k = localStorage.getItem('twelve_week_crypto_key_v1');
  var keyBytes;
  if (__k) {
    var bin = atob(__k);
    keyBytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) keyBytes[i] = bin.charCodeAt(i);
  } else {
    keyBytes = crypto.getRandomValues(new Uint8Array(32));
    var b = '';
    for (var i = 0; i < keyBytes.length; i++) b += String.fromCharCode(keyBytes[i]);
    localStorage.setItem('twelve_week_crypto_key_v1', btoa(b));
  }
"""

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(plaintext, onResult) => { (async () => { try { $KEY_STORAGE_JS var key = await crypto.subtle.importKey('raw', keyBytes, {name:'AES-GCM'}, false, ['encrypt']); var iv = crypto.getRandomValues(new Uint8Array(12)); var enc = new TextEncoder().encode(plaintext); var cipherBuf = await crypto.subtle.encrypt({name:'AES-GCM', iv:iv}, key, enc); var cipherBytes = new Uint8Array(cipherBuf); var combined = new Uint8Array(iv.length + cipherBytes.length); combined.set(iv, 0); combined.set(cipherBytes, iv.length); var s = ''; for (var i = 0; i < combined.length; i++) s += String.fromCharCode(combined[i]); onResult(btoa(s)); } catch (e) { onResult(''); } })(); }"
)
private external fun jsCryptoEncrypt(plaintext: String, onResult: (String) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(ciphertextB64, onResult) => { (async () => { try { var raw = localStorage.getItem('twelve_week_crypto_key_v1'); if (!raw) { onResult(''); return; } var bin0 = atob(raw); var keyBytes = new Uint8Array(bin0.length); for (var i = 0; i < bin0.length; i++) keyBytes[i] = bin0.charCodeAt(i); var key = await crypto.subtle.importKey('raw', keyBytes, {name:'AES-GCM'}, false, ['decrypt']); var combinedBin = atob(ciphertextB64); var combined = new Uint8Array(combinedBin.length); for (var i = 0; i < combinedBin.length; i++) combined[i] = combinedBin.charCodeAt(i); var iv = combined.slice(0, 12); var cipherBytes = combined.slice(12); var plainBuf = await crypto.subtle.decrypt({name:'AES-GCM', iv:iv}, key, cipherBytes); onResult(new TextDecoder().decode(plainBuf)); } catch (e) { onResult(''); } })(); }"
)
private external fun jsCryptoDecrypt(ciphertextB64: String, onResult: (String) -> Unit)
