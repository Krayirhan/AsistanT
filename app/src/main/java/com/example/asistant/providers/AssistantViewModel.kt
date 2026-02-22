package com.example.asistant.providers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asistant.models.ChatMessage
import com.example.asistant.models.DeviceStatus
import com.example.asistant.services.ApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiService.getInstance(application)

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _baseUrl = MutableStateFlow("http://26.207.206.2:8766")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            _baseUrl.value = api.getBaseUrl()
            startAutoRefresh()  // URL yüklendikten sonra başlat
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

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val userMsg = ChatMessage(content = message, isUser = true)
            val loadingMsg = ChatMessage(content = "⏳ Yanıt bekleniyor...", isUser = false, isLoading = true)
            _chatHistory.value = _chatHistory.value + userMsg + loadingMsg
            _isLoading.value = true

            try {
                val resp = api.chat(message)
                val reply = resp["response"]?.toString()
                    ?: resp["reply"]?.toString()
                    ?: resp["message"]?.toString()
                    ?: "Yanıt alınamadı."
                _chatHistory.value = _chatHistory.value.dropLast(1) +
                        ChatMessage(content = reply, isUser = false)
            } catch (e: Exception) {
                _chatHistory.value = _chatHistory.value.dropLast(1) +
                        ChatMessage(content = "❌ Hata: ${e.message}", isUser = false)
                _lastError.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            api.clearHistory()
            _chatHistory.value = emptyList()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                val history = api.getHistory()
                val messages = mutableListOf<ChatMessage>()
                for (item in history) {
                    val role = item["role"]?.toString() ?: ""
                    val content = item["content"]?.toString() ?: ""
                    if (content.isNotBlank()) {
                        messages.add(ChatMessage(content = content, isUser = role == "user"))
                    }
                }
                if (messages.isNotEmpty()) {
                    _chatHistory.value = messages
                }
            } catch (_: Exception) {}
        }
    }

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

    private fun tvAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _lastError.value = e.message }
        }
    }
}

