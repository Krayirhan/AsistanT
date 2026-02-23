package com.example.asistant.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer as VoskRecognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import com.example.asistant.services.PhoneActionsHandler
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * Arka Plan Uyandırma Servisi — "Hey ATLAS"
 *
 * IDLE_WAKE  → Vosk (AudioRecord, offline, SIFIR bip sesi)
 * COMMAND    → Vosk durdurulur, Google STT tek seferlik başlar (ADJUST_MUTE)
 * PROCESSING → Backend isteği
 * SPEAKING   → TTS yanıtı, sonra tekrar IDLE_WAKE
 *
 * İlk kurulum: ~35MB Türkçe Vosk modeli indirilir, filesDir'e kaydedilir.
 * Sonraki çalışmalarda tamamen offline.
 */
class WakeWordService : Service() {

    companion object {
        const val TAG               = "WakeWordService"
        const val NOTIF_ID          = 9001
        const val ACTION_STOP       = "com.example.asistant.STOP_WAKE"
        const val WAKE_CHANNEL_ID   = "asistan_wake_service"
        const val WAKE_CHANNEL_NAME = "ATLAS Sesli Servis"

        private const val MODEL_URL    = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
        private const val MODEL_DIR    = "vosk-model-tr"
        private const val MODEL_MARKER = "vosk.conf"   // zip içinde her zaman bu dosya var

        @Volatile var isRunning = false
            private set
    }

    private enum class State { LOADING, IDLE_WAKE, COMMAND, PROCESSING, SPEAKING }

    // ── Çok turlu sohbet (WhatsApp onay akışı) ────────────────────────
    private sealed class ConvState {
        object None : ConvState()
        data class WaitingMessage(val contact: String) : ConvState()
        data class WaitingConfirm(val contact: String, val message: String) : ConvState()
    }
    @Volatile private var convState: ConvState = ConvState.None

    @Volatile private var destroyed = false
    private var state = State.LOADING
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Vosk ──────────────────────────────────────────────────────────
    private var voskModel   : Model? = null
    private var voskService : SpeechService? = null
    private val modelDir    get() = File(filesDir, MODEL_DIR)

    // ── SpeechRecognizer (yalnızca COMMAND fazı) ──────────────────────
    private var recognizer  : SpeechRecognizer? = null
    private var ttsEngine   : PhoneTtsEngine?   = null
    private var wakeMediaPlayer: MediaPlayer? = null
    private var consecutiveErrors = 0

    // ── Bip bastırma ──────────────────────────────────────────────────
    private lateinit var audioManager: AudioManager
    private var beepMuted = false
    private val BEEP_STREAMS = intArrayOf(
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_DTMF,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── Desenler ──────────────────────────────────────────────────────
    private val WAKE_PATTERNS = listOf(
        Pattern.compile("""hey\s+furkan""",              Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""hey\s+atlas""",              Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""furkan\s+atlas""",            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""atlas[\u0131i\u0131m]*\s*aç""",      Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""merhaba\s+atlas""",           Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
    )
    private val STOP_PATTERNS = listOf(
        Pattern.compile("""kendini\s+kapat""",     Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""atlas[ıi]\s+kapat""", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""servisi\s+durdur""",    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
        Pattern.compile("""dinlemeyi\s+kapat""",   Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
    )

    // ══════════════════════════════════════════════════════════════════
    // Service Yaşam Döngüsü
    // ══════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        destroyed  = false
        isRunning  = true
        Log.i(TAG, "WakeWordService başlatılıyor (Vosk modu)...")

        createWakeChannel()
        showForegroundNotification("Başlatılıyor...")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        ttsEngine    = PhoneTtsEngine(applicationContext)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO izni yok")
            piperSpeak("Mikrofon izni verilmemiş, lütfen ayarlardan izin verin.")
            mainHandler.postDelayed({ safeStop() }, 5000)
            return
        }

        scope.launch { prepareVoskModel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { safeStop(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "WakeWordService durduruluyor...")
        destroyed = true
        isRunning  = false

        restoreVolume()
        mainHandler.removeCallbacksAndMessages(null)
        stopVosk()
        destroyCommandRecognizer()
        wakeMediaPlayer?.release(); wakeMediaPlayer = null
        ttsEngine?.stop(); ttsEngine?.destroy(); ttsEngine = null
        voskModel?.close(); voskModel = null
        scope.cancel()

        Log.i(TAG, "WakeWordService durduruldu")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun safeStop() {
        destroyed = true; isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ══════════════════════════════════════════════════════════════════
    // Vosk Model Yönetimi
    // ══════════════════════════════════════════════════════════════════

    private suspend fun prepareVoskModel() {
        val marker = File(modelDir, MODEL_MARKER)
        if (!marker.exists()) {
            mainHandler.post { updateNotification("Model indiriliyor (ilk kurulum ~35MB)...") }
            val ok = downloadAndExtractModel()
            if (!ok) {
                Log.e(TAG, "Model indirilemedi — 60s sonra tekrar deneniyor")
                mainHandler.post { updateNotification("Model indirilemedi — tekrar deneniyor...") }
                mainHandler.postDelayed({ scope.launch { prepareVoskModel() } }, 60_000)
                return
            }
        }

        mainHandler.post { updateNotification("Model yükleniyor...") }
        try {
            voskModel = Model(modelDir.absolutePath)
            mainHandler.post { if (!destroyed) startVoskListening() }
        } catch (e: Exception) {
            Log.e(TAG, "Model yüklenemedi: ${e.message} — silip tekrar indiriyorum")
            modelDir.deleteRecursively()
            mainHandler.postDelayed({ scope.launch { prepareVoskModel() } }, 5_000)
        }
    }

    /** OkHttp + ZipInputStream ile modeli indirip filesDir'e açar. */
    private fun downloadAndExtractModel(): Boolean = try {
        val req = Request.Builder().url(MODEL_URL).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) { Log.e(TAG, "HTTP ${resp.code}"); return false }
            modelDir.mkdirs()
            ZipInputStream(resp.body!!.byteStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val rel = entry.name.split("/").drop(1).joinToString("/")
                        if (rel.isNotEmpty()) {
                            val out = File(modelDir, rel)
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { zis.copyTo(it, 32 * 1024) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Log.i(TAG, "Model çıkartıldı: ${modelDir.absolutePath}")
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Download hatası: ${e.message}")
        modelDir.deleteRecursively()
        false
    }

    // ══════════════════════════════════════════════════════════════════
    // Vosk Dinleme — IDLE_WAKE (SıFıR bip sesi!)
    // ══════════════════════════════════════════════════════════════════

    private fun startVoskListening() {
        if (destroyed || voskModel == null) return
        stopVosk()
        try {
            val grammar  = """["hey furkan", "hey atlas", "furkan atlas", "merhaba atlas", "atlas", "[unk]"]"""
            val vRec     = VoskRecognizer(voskModel, 16000f, grammar)
            voskService  = SpeechService(vRec, 16000f)
            voskService?.startListening(voskListener)
            state             = State.IDLE_WAKE
            consecutiveErrors = 0
            updateNotification("\"Hey ATLAS\" deyin")
            Log.i(TAG, "Vosk aktif — bip yok")
        } catch (e: Exception) {
            Log.e(TAG, "Vosk başlatılamadı: ${e.message}")
            mainHandler.postDelayed({ if (!destroyed) startVoskListening() }, 3000)
        }
    }

    private fun stopVosk() {
        try { voskService?.stop(); voskService?.shutdown() }
        catch (e: Exception) { Log.w(TAG, "Vosk stop: ${e.message}") }
        voskService = null
    }

    private val voskListener = object : VoskRecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (hypothesis == null || state != State.IDLE_WAKE || destroyed) return
            val text = parseVoskJson(hypothesis)
            if (text.isNotEmpty() && isWakeWord(text)) {
                Log.i(TAG, "Wake word (partial): \"$text\"")
                mainHandler.post { if (!destroyed && state == State.IDLE_WAKE) transitionToCommand() }
            }
        }

        override fun onResult(hypothesis: String?) {
            if (hypothesis == null || state != State.IDLE_WAKE || destroyed) return
            val text = parseVoskJson(hypothesis)
            if (text.isNotEmpty() && isWakeWord(text)) {
                Log.i(TAG, "Wake word (result): \"$text\"")
                mainHandler.post { if (!destroyed && state == State.IDLE_WAKE) transitionToCommand() }
            }
        }

        override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)

        override fun onError(exception: Exception?) {
            Log.w(TAG, "Vosk hata: ${exception?.message}")
            if (!destroyed && state == State.IDLE_WAKE)
                mainHandler.postDelayed({ if (!destroyed) startVoskListening() }, 2000)
        }

        override fun onTimeout() {
            if (!destroyed && state == State.IDLE_WAKE)
                mainHandler.post { if (!destroyed) startVoskListening() }
        }
    }

    private fun parseVoskJson(hypothesis: String): String = try {
        val j = JSONObject(hypothesis)
        j.optString("text", j.optString("partial", "")).trim().lowercase()
    } catch (_: Exception) { "" }

    // ══════════════════════════════════════════════════════════════════
    // Komut Dinleme — SpeechRecognizer (tek seferlik, bip muted)
    // ══════════════════════════════════════════════════════════════════

    private fun startCommandListening() {
        if (destroyed) return
        destroyCommandRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            startVoskListening(); return
        }
        try {
            muteRecognizerBeep()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(commandListener)
            recognizer?.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                }
            )
            updateNotification("Komut bekleniyor...")
            scheduleCommandTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "Komut STT başlatılamadı: ${e.message}")
            restoreVolume()
            startVoskListening()
        }
    }

    private fun destroyCommandRecognizer() {
        try { recognizer?.cancel(); recognizer?.destroy() }
        catch (e: Exception) { Log.w(TAG, "Recognizer destroy: ${e.message}") }
        recognizer = null
    }

    private val TIMEOUT_TOKEN = Object()

    private fun scheduleCommandTimeout() {
        mainHandler.postAtTime({
            if (!destroyed && state == State.COMMAND) {
                Log.d(TAG, "Komut timeout — Vosk'a dönülüyor")
                destroyCommandRecognizer(); restoreVolume(); startVoskListening()
            }
        }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + 12_000)
    }

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
            mainHandler.postDelayed({ restoreVolume() }, 400)
        }

        override fun onResults(results: Bundle?) {
            if (destroyed) return
            mainHandler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) { startVoskListening(); return }
            Log.d(TAG, "Komut: \"$text\"")
            processCommand(text)
        }

        override fun onError(error: Int) {
            if (destroyed) return
            mainHandler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            restoreVolume()
            consecutiveErrors++
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { safeStop(); return }
            destroyCommandRecognizer()
            startVoskListening()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partial: Bundle?) {}
        override fun onEvent(type: Int, params: Bundle?) {}
    }

    // ══════════════════════════════════════════════════════════════════
    // Bip Bastırma
    // ══════════════════════════════════════════════════════════════════

    private fun muteRecognizerBeep() {
        if (beepMuted) return
        try {
            BEEP_STREAMS.forEach { audioManager.adjustStreamVolume(it, AudioManager.ADJUST_MUTE, 0) }
            beepMuted = true
        } catch (e: Exception) { Log.w(TAG, "Mute: ${e.message}") }
    }

    private fun restoreVolume() {
        if (!beepMuted) return
        try {
            BEEP_STREAMS.forEach { audioManager.adjustStreamVolume(it, AudioManager.ADJUST_UNMUTE, 0) }
        } catch (e: Exception) { Log.w(TAG, "Unmute: ${e.message}") }
        finally { beepMuted = false }
    }

    // ══════════════════════════════════════════════════════════════════
    // Durum Makinesi
    // ══════════════════════════════════════════════════════════════════

    private fun transitionToCommand() {
        if (destroyed || state == State.COMMAND) return
        state = State.COMMAND
        convState = ConvState.None   // önceki çok turlu bağlamı temizle
        stopVosk()
        updateNotification("Sizi dinliyorum...")
        piperSpeak("Evet?", onDone = {
            if (!destroyed) mainHandler.post {
                if (!destroyed && state == State.COMMAND) startCommandListening()
            }
        })
    }

    private fun processCommand(command: String) {
        if (destroyed) return
        state = State.PROCESSING
        updateNotification("İşleniyor: ${command.take(40)}...")
        destroyCommandRecognizer()

        if (isStopCommand(command)) {
            piperSpeak("Tamam, kapanıyorum.")
            mainHandler.postDelayed({ safeStop() }, 3000)
            return
        }

        // ── Çok turlu sohbet durumunu ÖNCE kontrol et ─────────────────────
        val currentConv = convState
        val s = command.lowercase(java.util.Locale("tr")).trim()

        when (currentConv) {
            is ConvState.WaitingMessage -> {
                // Kullanıcı mesaj içeriği söyledi
                val msg = command.trim()
                if (msg.isBlank()) {
                    speakQuestion("Mesaj anlaşılamadı, tekrar söyler misin?")
                    return
                }
                convState = ConvState.WaitingConfirm(currentConv.contact, msg)
                val displayContact = currentConv.contact.replaceFirstChar { it.uppercaseChar() }
                speakQuestion("$displayContact'a \"$msg\" göndermemi ister misin?")
                return
            }
            is ConvState.WaitingConfirm -> {
                val yes = Regex("""^(evet|gönder|yolla|göndersene|tamam|olur|yap|ok)\b""").containsMatchIn(s)
                val no  = Regex("""^(hayır|iptal|vazgeç|dur|istemiyorum|gerek\s*yok|yok|olmaz)\b""").containsMatchIn(s)
                when {
                    yes -> {
                        convState = ConvState.None
                        val action = PhoneActionsHandler.PhoneAction(
                            PhoneActionsHandler.ActionType.WHATSAPP,
                            currentConv.contact,
                            currentConv.message
                        )
                        val reply = PhoneActionsHandler.execute(applicationContext, action)
                        speakResponse(reply)
                    }
                    no -> {
                        convState = ConvState.None
                        speakResponse("Tamam, mesaj iptal edildi.")
                    }
                    else -> {
                        // Belirsiz yanıt — tekrar sor, durumu koru
                        speakQuestion("Anlamadım. \"Gönder\" veya \"hayır\" der misin?")
                    }
                }
                return
            }
            else -> { /* ConvState.None → normal akış */ }
        }

        // ── Normal komut akışı ─────────────────────────────────────────────
        val phoneAction = PhoneActionsHandler.detect(command)
        if (phoneAction != null) {
            if (phoneAction.type == PhoneActionsHandler.ActionType.WHATSAPP) {
                val displayContact = phoneAction.arg1.replaceFirstChar { it.uppercaseChar() }
                if (phoneAction.arg2.isEmpty()) {
                    // Mesaj belirtilmedi → sor, durumu kaydet
                    convState = ConvState.WaitingMessage(phoneAction.arg1)
                    speakQuestion("$displayContact'a ne yazmamı istersin?")
                } else {
                    // Mesaj var → onay iste
                    convState = ConvState.WaitingConfirm(phoneAction.arg1, phoneAction.arg2)
                    speakQuestion("$displayContact'a \"${phoneAction.arg2}\" göndermemi ister misin?")
                }
                return
            }
            val reply = PhoneActionsHandler.execute(applicationContext, phoneAction)
            speakResponse(reply)
            return
        }
        val appIntent = AppLauncher.detect(command)
        if (appIntent != null && appIntent.context == AppLauncher.IntentContext.PHONE) {
            val launched = AppLauncher.launch(applicationContext, appIntent)
            speakResponse(if (launched) "${appIntent.displayName} açılıyor" else "${appIntent.displayName} bulunamadı")
            return
        }

        scope.launch {
            val reply = try {
                val body = JSONObject().put("message", command).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = Request.Builder().url("${getBaseUrl()}/chat").post(body)
                val apiKey = getApiKey()
                if (apiKey.isNotBlank()) reqBuilder.addHeader("X-API-Key", apiKey)
                http.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        "Sunucu hatası: HTTP ${resp.code}"
                    } else {
                        val json = JSONObject(resp.body?.string() ?: "{}")
                        json.optString("response", json.optString("message", "Yanıt alınamadı."))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat API: ${e.message}")
                "Üzgünüm, sunucuya bağlanamıyorum."
            }
            if (!destroyed) mainHandler.post { if (!destroyed) speakResponse(reply) }
        }
    }

    private fun speakResponse(text: String) {
        if (destroyed) return
        state = State.SPEAKING
        updateNotification("Konuşuyor...")
        piperSpeak(text, onDone = {
            if (!destroyed) mainHandler.post { if (!destroyed) startVoskListening() }
        })
    }

    /**
     * speakQuestion — çok turlu akış için: TTS bittikten sonra
     * wake-word'e DÖNMEDİ, hemen COMMAND (STT) dinlemesine geçer.
     * Böylece kullanıcı "Hey ATLAS" demeden doğrudan cevap söyleyebilir.
     */
    private fun speakQuestion(text: String) {
        if (destroyed) return
        state = State.SPEAKING
        updateNotification("Soru soruluyor...")
        piperSpeak(text, onDone = {
            if (!destroyed) mainHandler.post {
                if (!destroyed) {
                    state = State.COMMAND
                    startCommandListening()
                }
            }
        })
    }

    // ══════════════════════════════════════════════════════════════════
    // Bildirim
    // ══════════════════════════════════════════════════════════════════

    private fun createWakeChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(WAKE_CHANNEL_ID) != null) return
        val ch = NotificationChannel(WAKE_CHANNEL_ID, WAKE_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            .apply { description = "ATLAS arka plan sesli servis"; setShowBadge(false) }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val stop = android.app.PendingIntent.getService(
            this, 0,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, WAKE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ATLAS")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stop)
            .build()
    }

    private fun showForegroundNotification(subtitle: String) {
        val n = buildNotification(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIF_ID, n)
    }

    private fun updateNotification(text: String) {
        if (destroyed) return
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text)) }
        catch (e: Exception) { Log.w(TAG, "Bildirim: ${e.message}") }
    }

    // ══════════════════════════════════════════════════════════════════
    // Yardımcılar
    // ══════════════════════════════════════════════════════════════════

    private fun isWakeWord(text: String)    = WAKE_PATTERNS.any { it.matcher(text).find() }
    private fun isStopCommand(text: String) = STOP_PATTERNS.any  { it.matcher(text).find() }
    private fun getBaseUrl() = getSharedPreferences("settings", MODE_PRIVATE)
        .getString("base_url", "http://192.168.0.13:8766") ?: "http://192.168.0.13:8766"
    private fun getApiKey() = getSharedPreferences("settings", MODE_PRIVATE)
        .getString("api_key", "") ?: ""

    // ══════════════════════════════════════════════════════════════════
    // Backend Piper TTS (erkek ses) — Google TTS fallback
    // ══════════════════════════════════════════════════════════════════

    /**
     * Backend Piper TTS ile metni seslendir.
     * Başarısız olursa PhoneTtsEngine (Google TTS) fallback.
     */
    private fun piperSpeak(text: String, onDone: (() -> Unit)? = null) {
        if (destroyed) return
        scope.launch {
            try {
                val clean = text.replace(Regex("[*#`_~\\[\\]()]"), "")
                    .replace(Regex("\\p{So}"), "").trim().take(500)
                if (clean.isBlank()) { onDone?.let { mainHandler.post(it) }; return@launch }

                val json = org.json.JSONObject().put("text", clean).toString()
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val reqBuilder = okhttp3.Request.Builder().url("${getBaseUrl()}/tts").post(body)
                val apiKey = getApiKey()
                if (apiKey.isNotBlank()) reqBuilder.addHeader("X-API-Key", apiKey)

                val wavBytes = http.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }

                if (wavBytes != null && !destroyed) {
                    val tmpFile = File(cacheDir, "wake_tts_${System.currentTimeMillis()}.wav")
                    tmpFile.writeBytes(wavBytes)
                    mainHandler.post {
                        if (destroyed) { tmpFile.delete(); return@post }
                        try {
                            wakeMediaPlayer?.release()
                            wakeMediaPlayer = MediaPlayer().apply {
                                setDataSource(tmpFile.absolutePath)
                                setOnCompletionListener {
                                    it.release()
                                    tmpFile.delete()
                                    wakeMediaPlayer = null
                                    onDone?.invoke()
                                }
                                setOnErrorListener { mp, _, _ ->
                                    mp.release()
                                    tmpFile.delete()
                                    wakeMediaPlayer = null
                                    onDone?.invoke()
                                    true
                                }
                                prepare()
                                start()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaPlayer hatası: ${e.message}")
                            tmpFile.delete()
                            wakeMediaPlayer = null
                            // Fallback: Google TTS
                            ttsEngine?.speak(text, onDone)
                        }
                    }
                } else {
                    // Backend ulaşılamıyor — fallback Google TTS
                    Log.w(TAG, "Piper TTS başarısız, Google TTS fallback")
                    mainHandler.post { ttsEngine?.speak(text, onDone) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "piperSpeak hata: ${e.message}")
                mainHandler.post { ttsEngine?.speak(text, onDone) }
            }
        }
    }
}
