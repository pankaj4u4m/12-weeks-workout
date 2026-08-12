package com.personal.twelveweek.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Guided-session voice cues via the browser's Web Speech API
 * (`SpeechSynthesisUtterance` / `window.speechSynthesis`) — the wasmJs
 * counterpart to the Android app's `ui.VoiceCoach` (Android on-device
 * TextToSpeech). Same "newest cue wins" behavior: cancels whatever's still
 * queued/playing before speaking a new one, so a late cue never stacks
 * behind a stale one. [isEnabled] is re-checked on every [speak] call so
 * flipping the Settings voice toggle mid-session takes effect immediately.
 * Silently degrades to a no-op on browsers without `speechSynthesis`
 * (checked inline in the JS body, not detectable from Kotlin).
 */
class WebVoiceCoach(private val isEnabled: () -> Boolean = { true }) {
    fun speak(text: String) {
        if (!isEnabled()) return
        runCatching { jsSpeak(text) }
    }

    /** Stops whatever's currently playing — used when the user mutes
     *  mid-utterance, so the cue cuts off immediately. */
    fun stop() {
        runCatching { jsCancelSpeech() }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(text) => { try { if (!('speechSynthesis' in window)) return; window.speechSynthesis.cancel(); var u = new SpeechSynthesisUtterance(text); window.speechSynthesis.speak(u); } catch (e) {} }")
private external fun jsSpeak(text: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { try { if ('speechSynthesis' in window) window.speechSynthesis.cancel(); } catch (e) {} }")
private external fun jsCancelSpeech()
