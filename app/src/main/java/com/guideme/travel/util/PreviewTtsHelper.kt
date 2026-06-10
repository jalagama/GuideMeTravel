package com.guideme.travel.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class PreviewTtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts?.language = Locale.getDefault()
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!ready || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview-${System.currentTimeMillis()}")
        onDone()
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
