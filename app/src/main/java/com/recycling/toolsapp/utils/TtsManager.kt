package com.recycling.toolsapp.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import com.serial.port.utils.Loge
import java.util.Locale

object TtsManager {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun initialize(context: Context, onInitListener: TextToSpeech.OnInitListener) {
        if (tts == null) {
            tts = TextToSpeech(context, onInitListener)
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        tts?.let { t ->
            if (isInitialized) {
                t.speak(text, queueMode, null, null)
            } else {
                Loge.d("TTS not initialized yet.")
            }
        }
    }

    fun setLanguage(locale: Locale): Comparable<*> {
        return tts?.setLanguage(locale) ?: false
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun setIsInitialized(flag: Boolean) {
        isInitialized = flag
    }
}
