package com.example.asistant.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Android Text-to-Speech motoru — arka plan servisi için.
 * Compose lifecycle'ından bağımsız, servis içinde kullanılmak üzere tasarlanmıştır.
 */
class PhoneTtsEngine(private val context: Context) {

    private val TAG = "PhoneTtsEngine"
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val speakQueue = mutableListOf<Pair<String, (() -> Unit)?>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                    Log.w(TAG, "Türkçe TTS desteklenmiyor, İngilizce kullanılıyor")
                }

                // Erkek ses seç
                try {
                    val voices = tts?.voices
                    val maleVoice = voices?.firstOrNull { v ->
                        v.locale.language == "tr" &&
                        (v.name.contains("male", ignoreCase = true) ||
                         v.name.contains("erkek", ignoreCase = true) ||
                         v.name.contains("-d-", ignoreCase = true) ||
                         v.name.contains("#male", ignoreCase = true))
                    }
                    if (maleVoice != null) {
                        tts?.voice = maleVoice
                        Log.i(TAG, "Erkek ses seçildi: ${maleVoice.name}")
                    } else {
                        // Erkek ses bulunamadı — düşük pitch ile telafi et
                        Log.i(TAG, "Erkek ses yok, pitch=0.8 ile devam")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Voice seçim hatası: ${e.message}")
                }

                tts?.setSpeechRate(1.05f)
                tts?.setPitch(0.8f)   // erkek ses tonu — düşük pitch
                isReady = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })

                // Kuyruktaki bekleyenleri işle
                synchronized(speakQueue) {
                    speakQueue.forEach { (text, cb) -> doSpeak(text, cb) }
                    speakQueue.clear()
                }
                Log.i(TAG, "TTS motoru hazır")
            } else {
                Log.e(TAG, "TTS başlatılamadı: $status")
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val clean = text
            .replace(Regex("[\\*\\#\\`]"), "")   // markdown işaretlerini temizle
            .take(500)                             // çok uzun metni kes
        if (isReady) {
            doSpeak(clean, onDone)
        } else {
            synchronized(speakQueue) { speakQueue.add(clean to onDone) }
        }
    }

    private fun doSpeak(text: String, onDone: (() -> Unit)?) {
        val utteranceId = System.currentTimeMillis().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { if (u == utteranceId) onDone?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(u: String?) { if (u == utteranceId) onDone?.invoke() }
            override fun onError(u: String?, errorCode: Int) { if (u == utteranceId) onDone?.invoke() }
        })
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        // speak() başarısız olduysa callback asla gelmez — hemen tetikle
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS speak başarısız (code=$result)")
            onDone?.invoke()
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.i(TAG, "TTS motoru kapatıldı")
    }
}
