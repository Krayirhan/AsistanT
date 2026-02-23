package com.example.asistant.providers

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asistant.models.ChatMessage
import com.example.asistant.models.DeviceStatus
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.asistant.notes.NoteRepository
import com.example.asistant.services.ApiService
import com.example.asistant.services.AppLauncher
import com.example.asistant.services.PhoneActionsHandler
import com.example.asistant.services.NotificationHelper
import com.example.asistant.services.WakeWordService
import com.example.asistant.services.dataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val api  = ApiService.getInstance(application)
    private val ctx  = application.applicationContext
    private val gson = Gson()

    private val KEY_CHAT_HISTORY = stringPreferencesKey("chat_history_json")

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _baseUrl = MutableStateFlow("http://192.168.0.13:8766")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _reminders = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
    val reminders: StateFlow<List<Map<String, Any?>>> = _reminders.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(WakeWordService.isRunning)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var reminderPollJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    // ── Çok turlu sohbet durumu (WhatsApp + Arama) ────────────────────────────
    private sealed class ConvState {
        object None : ConvState()
        data class WaitingMessage(val contact: String) : ConvState()
        data class WaitingConfirm(val contact: String, val message: String) : ConvState()
        data class WaitingCallConfirm(val contact: String) : ConvState()
    }
    private val convLock = Any()
    private var convState: ConvState = ConvState.None

    init {
        viewModelScope.launch {
            _baseUrl.value = api.getBaseUrl()
            loadChatHistoryFromStore()
            startAutoRefresh()
            startReminderPolling()
            syncNotesToBackend()
        }
    }

    // ── Not senkronizasyonu (Android → Backend) ──────────────────────────────
    /** Telefondaki tüm notları backend'e gönderir, LLM context için. */
    private fun syncNotesToBackend() {
        viewModelScope.launch {
            try {
                val notes = NoteRepository.getAll(ctx)
                val notesList = notes.map { note ->
                    mapOf<String, Any?>(
                        "id"         to note.id,
                        "title"      to note.title,
                        "body"       to note.body,
                        "category"   to note.category.name,
                        "tags"       to note.tags,
                        "pinned"     to note.pinned,
                        "createdAt"  to note.createdAt,
                        "modifiedAt" to note.modifiedAt
                    )
                }
                val ok = api.syncNotes(notesList)
                if (ok) Log.d("ATLAS", "Notlar backend'e senkronize edildi (${notes.size} not)")
                else   Log.w("ATLAS", "Not senkronizasyonu başarısız")
            } catch (e: Exception) {
                Log.w("ATLAS", "Not senkronizasyon hatası: ${e.message}")
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshStatus()
                delay(30_000)
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val resp = api.getStatus()
                val ampul = resp["ampul"] as? Map<*, *>
                val tv = resp["tv"] as? Map<*, *>
                val ai = resp["ai"] as? Map<*, *>
                _status.value = DeviceStatus(
                    isConnected = true,
                    lightOn = ampul?.get("acik") as? Boolean
                        ?: ampul?.get("on") as? Boolean ?: false,
                    lightBrightness = (ampul?.get("parlaklik") as? Double)?.toInt()
                        ?: (ampul?.get("brightness") as? Double)?.toInt() ?: 100,
                    lightColor = ampul?.get("renk")?.toString()
                        ?: ampul?.get("color")?.toString() ?: "beyaz",
                    tvConnected = tv?.get("bagli") as? Boolean
                        ?: tv?.get("connected") as? Boolean ?: false,
                    aiReady = ai?.get("hazir") as? Boolean
                        ?: ai?.get("ready") as? Boolean ?: false,
                    rawResponse = resp
                )
            } catch (e: Exception) {
                _status.value = _status.value.copy(isConnected = false)
                _lastError.value = e.message
            }
        }
    }

    /** true = servis başlatıldı, false = izin eksik (UI izin iste) */
    fun startWakeWordService(): Boolean {
        // İzin kontrolü
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            _lastError.value = "Mikrofon izni gerekli — lütfen izin verin"
            return false
        }
        try {
            val intent = Intent(ctx, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            _wakeWordEnabled.value = true
        } catch (e: Exception) {
            _lastError.value = "Servis başlatılamadı: ${e.message}"
            _wakeWordEnabled.value = false
        }
        return true
    }

    fun stopWakeWordService() {
        try {
            val intent = Intent(ctx, WakeWordService::class.java).apply {
                action = WakeWordService.ACTION_STOP
            }
            ctx.startService(intent)
        } catch (_: Exception) {}
        _wakeWordEnabled.value = false
    }

    fun toggleWakeWord(): Boolean {
        return if (_wakeWordEnabled.value) {
            stopWakeWordService(); true
        } else {
            startWakeWordService()
        }
    }

    /** Servisin gerçek durumunu kontrol et (toggle switch senkronizasyonu) */
    fun syncWakeWordState() {
        _wakeWordEnabled.value = WakeWordService.isRunning
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val userMsg = ChatMessage(content = message, isUser = true)

            // ── Çok turlu WhatsApp sohbeti ÖNCE kontrol et ───────────────────
            val currentConv = synchronized(convLock) { convState }
            val s = message.lowercase(java.util.Locale("tr")).trim()

            when (currentConv) {
                is ConvState.WaitingMessage -> {
                    val msg = message.trim()
                    if (msg.isBlank()) {
                        val reply = ChatMessage(content = "Mesaj anlaşılamadı, tekrar yazar mısın?", isUser = false)
                        appendChat(userMsg, reply); return@launch
                    }
                    synchronized(convLock) { convState = ConvState.WaitingConfirm(currentConv.contact, msg) }
                    val displayContact = currentConv.contact.replaceFirstChar { it.uppercaseChar() }
                    val reply = ChatMessage(
                        content = "📩 $displayContact'a **\"$msg\"** göndermemi ister misin?\n_(Evet / Hayır)_",
                        isUser = false
                    )
                    appendChat(userMsg, reply); return@launch
                }
                is ConvState.WaitingConfirm -> {
                    val yes = Regex("""^(evet|gönder|yolla|göndersene|tamam|olur|yap|ok)\b""").containsMatchIn(s)
                    val no  = Regex("""^(hayır|iptal|vazgeç|dur|istemiyorum|gerek\s*yok|yok|olmaz)\b""").containsMatchIn(s)
                    when {
                        yes -> {
                            synchronized(convLock) { convState = ConvState.None }
                            val action = PhoneActionsHandler.PhoneAction(
                                PhoneActionsHandler.ActionType.WHATSAPP,
                                currentConv.contact,
                                currentConv.message
                            )
                            val replyText = PhoneActionsHandler.execute(ctx, action)
                            val reply = ChatMessage(content = replyText, isUser = false)
                            appendChat(userMsg, reply)
                        }
                        no -> {
                            synchronized(convLock) { convState = ConvState.None }
                            val reply = ChatMessage(content = "❌ Mesaj iptal edildi.", isUser = false)
                            appendChat(userMsg, reply)
                        }
                        else -> {
                            val reply = ChatMessage(
                                content = "Anlamadım. \"Gönder\" veya \"Hayır\" yazar mısın?",
                                isUser = false
                            )
                            appendChat(userMsg, reply)
                        }
                    }
                    return@launch
                }
                is ConvState.WaitingCallConfirm -> {
                    val yes = Regex("""^(evet|ara|tamam|olur|yap|ok)\b""").containsMatchIn(s)
                    val no  = Regex("""^(hayır|iptal|vazgeç|dur|istemiyorum|yok|olmaz)\b""").containsMatchIn(s)
                    when {
                        yes -> {
                            synchronized(convLock) { convState = ConvState.None }
                            val action = PhoneActionsHandler.PhoneAction(
                                PhoneActionsHandler.ActionType.CALL,
                                currentConv.contact
                            )
                            val replyText = PhoneActionsHandler.execute(ctx, action)
                            val reply = ChatMessage(content = replyText, isUser = false)
                            appendChat(userMsg, reply)
                        }
                        no -> {
                            synchronized(convLock) { convState = ConvState.None }
                            val reply = ChatMessage(content = "❌ Arama iptal edildi.", isUser = false)
                            appendChat(userMsg, reply)
                        }
                        else -> {
                            val reply = ChatMessage(
                                content = "Anlamadım. \"Ara\" veya \"İptal\" yazar mısın?",
                                isUser = false
                            )
                            appendChat(userMsg, reply)
                        }
                    }
                    return@launch
                }
                else -> { /* ConvState.None → normal akış */ }
            }

            // ── Telefon yerel aksiyonu mu? (fener, pil, alarm, arama, not …) ──
            val phoneAction = PhoneActionsHandler.detect(message)
            if (phoneAction != null) {
                if (phoneAction.type == PhoneActionsHandler.ActionType.CALL) {
                    val displayContact = phoneAction.arg1.replaceFirstChar { it.uppercaseChar() }
                    synchronized(convLock) { convState = ConvState.WaitingCallConfirm(phoneAction.arg1) }
                    val reply = ChatMessage(
                        content = "📞 **$displayContact**'ı aramak istiyorsun, emin misin?\n_(Evet / Hayır)_",
                        isUser = false
                    )
                    appendChat(userMsg, reply)
                    return@launch
                }
                if (phoneAction.type == PhoneActionsHandler.ActionType.WHATSAPP) {
                    val displayContact = phoneAction.arg1.replaceFirstChar { it.uppercaseChar() }
                    if (phoneAction.arg2.isEmpty()) {
                        synchronized(convLock) { convState = ConvState.WaitingMessage(phoneAction.arg1) }
                        val reply = ChatMessage(
                            content = "💬 $displayContact'a ne yazmamı istersin?",
                            isUser = false
                        )
                        appendChat(userMsg, reply)
                    } else {
                        synchronized(convLock) { convState = ConvState.WaitingConfirm(phoneAction.arg1, phoneAction.arg2) }
                        val reply = ChatMessage(
                            content = "📩 $displayContact'a **\"${phoneAction.arg2}\"** göndermemi ister misin?\n_(Evet / Hayır)_",
                            isUser = false
                        )
                        appendChat(userMsg, reply)
                    }
                    return@launch
                }
                val replyText = PhoneActionsHandler.execute(ctx, phoneAction)
                val replyMsg  = ChatMessage(content = replyText, isUser = false)
                appendChat(userMsg, replyMsg); return@launch
            }
            // ── Telefon uygulama komutu mu? ──────────────────────────────
            // Sadece PHONE context → telefonda aç
            // TV veya AMBIGUOUS → backend'e gönder (TV'de launch_app yapılır)
            val appIntent = AppLauncher.detect(message)
            if (appIntent != null && appIntent.context == AppLauncher.IntentContext.PHONE) {
                val launched = AppLauncher.launch(ctx, appIntent)
                val replyText = if (launched)
                    "📱 ${appIntent.displayName} açılıyor!"
                else
                    "📱 ${appIntent.displayName} bulunamadı veya yüklü değil."
                appendChat(userMsg, ChatMessage(content = replyText, isUser = false)); return@launch
            }

            val loadingMsg = ChatMessage(content = "⏳ Yanıt bekleniyor...", isUser = false, isLoading = true)
            _chatHistory.value = _chatHistory.value + userMsg + loadingMsg
            _isLoading.value = true

            try {
                // Sohbet öncesi notları senkronize et (LLM context için)
                syncNotesToBackend()

                // ── SSE token yayını ──────────────────────────────────────
                var streamedText = ""
                var lastUiUpdateMs = 0L
                val streamOk = api.chatStream(message) { token ->
                    streamedText += token
                    // Yanıtı en fazla 50ms'de bir güncelle — her token'da
                    // tam liste rebuild yerine throttle edilmiş güncelleme
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs >= 50L) {
                        lastUiUpdateMs = now
                        val current = _chatHistory.value.dropLast(1)
                        _chatHistory.value = current +
                                ChatMessage(content = streamedText, isUser = false, isLoading = true)
                    }
                }

                val reply: String
                if (!streamOk || streamedText.isBlank()) {
                    // SSE başarısız → klasik POST /chat fallback
                    val resp = api.chat(message)
                    val error = resp["error"]?.toString()
                    if (resp["success"] == false && error != null) {
                        val friendlyMsg = when {
                            error.contains("401") -> "🔑 API anahtarı geçersiz. Ayarlardan güncelleyin."
                            error.contains("503") -> "⚠️ Sunucu henüz hazır değil. Biraz bekleyin."
                            error.contains("timeout", ignoreCase = true) ||
                            error.contains("connect", ignoreCase = true) -> "📡 Sunucuya bağlanılamıyor. PC açık ve aynı ağda mı?"
                            else -> "❌ Sunucu hatası: $error"
                        }
                        val newHistory = _chatHistory.value.dropLast(1) +
                                ChatMessage(content = friendlyMsg, isUser = false)
                        _chatHistory.value = newHistory
                        saveChatHistoryToStore(newHistory)
                        return@launch
                    }
                    reply = resp["response"]?.toString()
                        ?: resp["reply"]?.toString()
                        ?: resp["message"]?.toString()
                        ?: "Yanıt alınamadı."
                } else {
                    reply = streamedText
                }

                val newHistory = _chatHistory.value.dropLast(1) +
                        ChatMessage(content = reply, isUser = false)
                _chatHistory.value = newHistory
                saveChatHistoryToStore(newHistory)
            } catch (e: Exception) {
                val friendlyMsg = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("connect", ignoreCase = true) == true ->
                        "📡 Sunucuya bağlanılamıyor. PC açık ve aynı ağda mı?"
                    else -> "❌ Hata: ${e.message}"
                }
                val newHistory = _chatHistory.value.dropLast(1) +
                        ChatMessage(content = friendlyMsg, isUser = false)
                _chatHistory.value = newHistory
                _lastError.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Kullanıcı + bot mesajını sohbet geçmişine ekle ve kaydet */
    private suspend fun appendChat(userMsg: ChatMessage, botMsg: ChatMessage) {
        val newHistory = _chatHistory.value + userMsg + botMsg
        _chatHistory.value = newHistory
        saveChatHistoryToStore(newHistory)
    }

    /**
     * Backend Piper TTS ile metni seslendir.
     * WAV byte array alınıp MediaPlayer ile çalınır.
     * Başarısız olursa sessizce atlanır (fallback yok — metin zaten ekranda).
     */
    private fun playTts(text: String) {
        viewModelScope.launch {
            try {
                // Markdown/emoji temizle, max 500 karakter
                val clean = text
                    .replace(Regex("[*#`_~\\[\\]()]"), "")
                    .replace(Regex("\\p{So}"), "")
                    .trim()
                    .take(500)
                if (clean.isBlank()) return@launch

                val wavBytes = api.tts(clean) ?: return@launch

                // Önceki sesi durdur
                mediaPlayer?.release()
                mediaPlayer = null

                // Geçici dosyaya yaz ve çal
                val tmpFile = java.io.File.createTempFile("tts_", ".wav", ctx.cacheDir)
                tmpFile.writeBytes(wavBytes)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tmpFile.absolutePath)
                    setOnCompletionListener {
                        it.release()
                        tmpFile.delete()
                        mediaPlayer = null
                    }
                    setOnErrorListener { mp, _, _ ->
                        mp.release()
                        tmpFile.delete()
                        mediaPlayer = null
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w("AssistantVM", "TTS çalma hatası: ${e.message}")
            }
        }
    }

    /** TTS sesini durdur */
    fun stopTts() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            api.clearHistory()
            _chatHistory.value = emptyList()
            ctx.dataStore.edit { prefs -> prefs[KEY_CHAT_HISTORY] = "[]" }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                val history  = api.getHistory()
                val messages = mutableListOf<ChatMessage>()
                for (item in history) {
                    val role    = item["role"]?.toString() ?: ""
                    val content = item["content"]?.toString() ?: ""
                    if (content.isNotBlank()) {
                        messages.add(ChatMessage(content = content, isUser = role == "user"))
                    }
                }
                if (messages.isNotEmpty()) {
                    _chatHistory.value = messages
                    saveChatHistoryToStore(messages)
                }
            } catch (_: Exception) {}
        }
    }

    // ── Chat Kalıcılığı ───────────────────────────────────────────────────────────

    private suspend fun loadChatHistoryFromStore() {
        try {
            val json = ctx.dataStore.data
                .map { prefs -> prefs[KEY_CHAT_HISTORY] ?: "[]" }
                .first()
            val type = object : TypeToken<List<SimpleChatMessage>>() {}.type
            val stored: List<SimpleChatMessage> = gson.fromJson(json, type) ?: emptyList()
            if (stored.isNotEmpty()) {
                _chatHistory.value = stored.map {
                    ChatMessage(content = it.content, isUser = it.isUser)
                }
            }
        } catch (e: Exception) {
            Log.w("ViewModel", "Chat geçmişi yüklenemedi: ${e.message}")
        }
    }

    private suspend fun saveChatHistoryToStore(messages: List<ChatMessage>) {
        try {
            val simple = messages
                .filter { !it.isLoading }
                .takeLast(100)
                .map { SimpleChatMessage(it.content, it.isUser) }
            ctx.dataStore.edit { prefs ->
                prefs[KEY_CHAT_HISTORY] = gson.toJson(simple)
            }
        } catch (e: Exception) {
            Log.w("ViewModel", "Chat geçmişi kaydedilemedi: ${e.message}")
        }
    }

    // ── Hatırlatıcı ───────────────────────────────────────────────────────────────

    private fun startReminderPolling() {
        reminderPollJob?.cancel()
        reminderPollJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                checkPendingReminders()
                refreshActiveReminders()
            }
        }
    }

    private suspend fun checkPendingReminders() {
        try {
            val pending = api.getRemindersPending()
            pending.forEach { reminder ->
                val text = reminder["text"]?.toString() ?: "Hatırlatıcı"
                NotificationHelper.showReminderNotification(
                    ctx,
                    title = "⏰ Hatırlatıcı",
                    body  = text,
                    id    = (reminder["id"]?.hashCode() ?: System.currentTimeMillis().toInt())
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun refreshActiveReminders() {
        try { _reminders.value = api.getReminders() } catch (_: Exception) {}
    }

    fun addReminder(seconds: Int, text: String) {
        viewModelScope.launch {
            try { api.addReminder(seconds, text); refreshActiveReminders() }
            catch (e: Exception) { _lastError.value = e.message }
        }
    }

    fun cancelReminder(rid: String) {
        viewModelScope.launch {
            try { api.cancelReminder(rid); refreshActiveReminders() }
            catch (e: Exception) { _lastError.value = e.message }
        }
    }

    // ── Rutin / Sahne ───────────────────────────────────────────────────────────────

    fun runRoutine(name: String) {
        viewModelScope.launch {
            try {
                val resp = api.runRoutine(name)
                val msg  = resp["result"]?.toString() ?: "Rutin çalıştırıldı."
                val newHistory = _chatHistory.value +
                        ChatMessage(content = msg, isUser = false)
                _chatHistory.value = newHistory
                saveChatHistoryToStore(newHistory)
                refreshStatus()
            } catch (e: Exception) { _lastError.value = e.message }
        }
    }

    // ── PC Kontrol ────────────────────────────────────────────────────────────────────

    fun pcSleep()    { viewModelScope.launch { try { api.pcSleep()    } catch (e: Exception) { _lastError.value = e.message } } }
    fun pcShutdown() { viewModelScope.launch { try { api.pcShutdown() } catch (e: Exception) { _lastError.value = e.message } } }
    fun pcShutdownCancel() { viewModelScope.launch { try { api.pcShutdownCancel() } catch (e: Exception) { _lastError.value = e.message } } }
    fun pcVolume(level: Int) { viewModelScope.launch { try { api.pcVolume(level) } catch (e: Exception) { _lastError.value = e.message } } }

    fun toggleLight() {
        viewModelScope.launch {
            try {
                // İyimser güncelleme: fiziksel anahtar beklemeden UI'yi hemen yansıt
                val optimistic = !_status.value.lightOn
                _status.value = _status.value.copy(lightOn = optimistic)
                if (optimistic) api.tapoOn() else api.tapoOff()
                delay(3000)  // Tapo'nun fiziksel olarak anahtaró yapması 2-5s sürer
                refreshStatus()
            } catch (e: Exception) {
                _lastError.value = e.message
                refreshStatus()  // hata durumunda gerçek durumu geri yükle
            }
        }
    }

    fun setLightBrightness(level: Int) {
        viewModelScope.launch {
            try {
                api.tapoBrightness(level)
                _status.value = _status.value.copy(lightBrightness = level)
            } catch (e: Exception) {
                _lastError.value = e.message
            }
        }
    }

    fun setLightColor(renk: String, parlaklik: Int) {
        viewModelScope.launch {
            try {
                api.tapoColor(renk, parlaklik)
                _status.value = _status.value.copy(lightColor = renk)
            } catch (e: Exception) {
                _lastError.value = e.message
            }
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch {
            api.saveBaseUrl(url)
            _baseUrl.value = url
            refreshStatus()
        }
    }

    // ── TV Kontrol ────────────────────────────────────────────────────────────
    fun tvOn()          { tvAction { api.tvOn() } }
    fun tvOff()         { tvAction { api.tvOff() } }
    fun tvVolUp()       { tvAction { api.tvVolUp() } }
    fun tvVolDown()     { tvAction { api.tvVolDown() } }
    fun tvMute()        { tvAction { api.tvMute() } }
    fun tvSetVolume(v: Int)   { tvAction { api.tvSetVolume(v) } }
    fun tvChannelUp()   { tvAction { api.tvChannelUp() } }
    fun tvChannelDown() { tvAction { api.tvChannelDown() } }
    fun tvSetChannel(ch: Int) { tvAction { api.tvSetChannel(ch) } }
    fun tvButton(btn: String) { tvAction { api.tvButton(btn) } }

    private fun tvAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _lastError.value = e.message }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

/** DataStore JSON serileştirmesi için sade model. */
private data class SimpleChatMessage(val content: String, val isUser: Boolean)

