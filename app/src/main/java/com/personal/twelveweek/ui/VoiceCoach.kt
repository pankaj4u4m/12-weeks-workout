package com.personal.twelveweek.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Thin wrapper around Android's on-device TextToSpeech for short guided-
 * session cues ("Halfway there", "5 seconds remaining", "Up next: Squats").
 * Each [speak] flushes any cue still queued/playing (`QUEUE_FLUSH`) so a late
 * cue never stacks behind a stale one — the newest cue always wins.
 *
 * [isEnabled] is checked on every [speak] call (not just at construction) so
 * flipping the Settings toggle mid-session takes effect immediately.
 */
class VoiceCoach(context: Context, private val isEnabled: () -> Boolean = { true }) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String) {
        if (!ready || !isEnabled()) return
        runCatching {
            tts.language = Locale.getDefault()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
        }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}

/** Creates a [VoiceCoach] scoped to the current composition — shut down
 *  automatically when the caller leaves composition (e.g. exiting the
 *  guided session), so the TTS engine is never left running in the
 *  background. [isEnabled] is re-read on every [VoiceCoach.speak] call, not
 *  just here, so it can be a live Settings-backed value. */
@Composable
fun rememberVoiceCoach(isEnabled: () -> Boolean = { true }): VoiceCoach {
    val context = LocalContext.current
    val coach = remember { VoiceCoach(context, isEnabled) }
    DisposableEffect(Unit) {
        onDispose { coach.shutdown() }
    }
    return coach
}
