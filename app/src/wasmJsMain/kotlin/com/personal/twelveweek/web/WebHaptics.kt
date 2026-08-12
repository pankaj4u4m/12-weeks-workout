package com.personal.twelveweek.web

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Haptic + audible completion feedback for the guided session — wasmJs
 * counterparts to the Android app's `buzz()` (Android Vibrator) and
 * `playCompletionSound()` (Android ToneGenerator). [webBuzz] degrades
 * silently where the Vibration API isn't available (notably iOS Safari — a
 * known, already-documented platform gap, not a bug here). [webBeep] uses
 * the Web Audio API directly since there's no browser "tone generator";
 * both are best-effort and never throw.
 */
fun webBuzz() {
    runCatching { jsVibrate() }
}

fun webCompletionBeep() {
    runCatching { jsBeep() }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { try { if (navigator.vibrate) navigator.vibrate([0,250,150,250]); } catch (e) {} }")
private external fun jsVibrate()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { try { var Ctx = window.AudioContext || window.webkitAudioContext; var ctx = new Ctx(); var osc = ctx.createOscillator(); var gain = ctx.createGain(); osc.connect(gain); gain.connect(ctx.destination); osc.frequency.value = 880; gain.gain.setValueAtTime(0.15, ctx.currentTime); osc.start(); osc.stop(ctx.currentTime + 0.3); setTimeout(function() { ctx.close(); }, 500); } catch (e) {} }")
private external fun jsBeep()
