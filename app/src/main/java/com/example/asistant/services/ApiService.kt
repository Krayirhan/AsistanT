package com.example.asistant.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ApiService private constructor(private val context: Context) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_API_KEY  = stringPreferencesKey("api_key")
        private const val DEFAULT_URL = "http://192.168.0.13:8766"
        @Volatile
        private var INSTANCE: ApiService? = null

        fun getInstance(context: Context): ApiService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiService(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Cached values — DataStore her istekte okunmaz
    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedApiKey: String? = null

    suspend fun getBaseUrl(): String {
        cachedBaseUrl?.let { return it }
        val url = context.dataStore.data.map { prefs -> prefs[KEY_BASE_URL] ?: DEFAULT_URL }.first()
        cachedBaseUrl = url
        // WakeWordService SharedPreferences ile okur — senkronize et
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("base_url", url).apply()
        return url
    }

    suspend fun getApiKey(): String =
        cachedApiKey ?: context.dataStore.data.map { prefs -> prefs[KEY_API_KEY] ?: "" }.first().also { cachedApiKey = it }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BASE_URL] = url }
        cachedBaseUrl = url
        // WakeWordService SharedPreferences ile okur — senkronize et
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("base_url", url).apply()
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_API_KEY] = key }
        cachedApiKey = key
        // WakeWordService de okuyabilsin
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("api_key", key).apply()
    }

    private suspend fun buildRequest(urlStr: String): Request.Builder {
        val builder = Request.Builder().url(urlStr)
        val key = getApiKey()
        if (key.isNotBlank()) builder.addHeader("X-API-Key", key)
        return builder
    }

    private suspend fun get(url: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "HTTP ${response.code}: $body"
                    )
                }
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(body, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bağlantı hatası"))
        }
    }

    private suspend fun post(url: String, body: Map<String, Any?>): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody(jsonMediaType)
            val request = buildRequest(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "HTTP ${response.code}: $responseBody"
                    )
                }
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(responseBody, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bağlantı hatası"))
        }
    }

    private suspend fun postEmpty(url: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val requestBody = "{}".toRequestBody(jsonMediaType)
            val request = buildRequest(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "HTTP ${response.code}: $body"
                    )
                }
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(body, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bağlantı hatası"))
        }
    }

    // --- Health ---
    suspend fun health(): Map<String, Any?> = get("${getBaseUrl()}/health")

    /** Test Et butonu için — DataStore'u beklemeden doğrudan verilen URL'yi test eder. */
    suspend fun healthAt(baseUrl: String): Map<String, Any?> = get("$baseUrl/health")

    // --- Chat ---
    suspend fun chat(message: String): Map<String, Any?> =
        post("${getBaseUrl()}/chat", mapOf("message" to message))

    /**
     * SSE token yayını: POST /chat/stream → "data: {"token":"..."}" satırları.
     * Her token onToken() callback'ine iletilir.
     * @return true = akış tamamlandı, false = hata / bağlantı kesildi.
     */
    suspend fun chatStream(message: String, onToken: suspend (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(mapOf("message" to message))
                val requestBody = json.toRequestBody(jsonMediaType)
                val request = buildRequest("${getBaseUrl()}/chat/stream").post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val source = response.body?.source() ?: return@withContext false
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("data: [DONE]") -> return@withContext true
                            line.startsWith("data: ") -> {
                                val data = line.removePrefix("data: ").trim()
                                if (data.isBlank()) continue
                                try {
                                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                                    val obj: Map<String, Any?> = gson.fromJson(data, type) ?: continue
                                    val token = obj["token"] as? String ?: continue
                                    withContext(Dispatchers.Main) { onToken(token) }
                                } catch (_: Exception) { /* malformed frame — skip */ }
                            }
                            // else: event: / id: / comment lines — ignore
                        }
                    }
                    true
                }
            } catch (_: Exception) { false }
        }

    suspend fun clearHistory(): Map<String, Any?> = postEmpty("${getBaseUrl()}/chat/clear")

    suspend fun getHistory(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}/chat/history").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                gson.fromJson<List<Map<String, Any?>>>(body, type) ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- Durum ---
    suspend fun getStatus(): Map<String, Any?> = get("${getBaseUrl()}/durum")

    // --- Tapo ---
    suspend fun tapoOn(): Map<String, Any?> = get("${getBaseUrl()}/tapo/ac")
    suspend fun tapoOff(): Map<String, Any?> = get("${getBaseUrl()}/tapo/kapat")
    suspend fun tapoStatus(): Map<String, Any?> = get("${getBaseUrl()}/tapo/durum")
    suspend fun tapoBrightness(level: Int): Map<String, Any?> =
        post("${getBaseUrl()}/tapo/parlaklik", mapOf("level" to level))
    suspend fun tapoColor(renk: String, parlaklik: Int): Map<String, Any?> =
        post("${getBaseUrl()}/tapo/renk", mapOf("renk" to renk, "parlaklik" to parlaklik))

    // --- TV ---
    suspend fun tvOn(): Map<String, Any?> = get("${getBaseUrl()}/tv/ac")
    suspend fun tvOff(): Map<String, Any?> = get("${getBaseUrl()}/tv/kapat")
    suspend fun tvVolUp(): Map<String, Any?> = get("${getBaseUrl()}/tv/ses/artir")
    suspend fun tvVolDown(): Map<String, Any?> = get("${getBaseUrl()}/tv/ses/azalt")
    suspend fun tvMute(): Map<String, Any?> = get("${getBaseUrl()}/tv/sessiz")
    suspend fun tvSetVolume(v: Int): Map<String, Any?> =
        post("${getBaseUrl()}/tv/ses", mapOf("seviye" to v))
    suspend fun tvChannelUp(): Map<String, Any?> = get("${getBaseUrl()}/tv/kanal/artir")
    suspend fun tvChannelDown(): Map<String, Any?> = get("${getBaseUrl()}/tv/kanal/azalt")
    suspend fun tvSetChannel(n: Int): Map<String, Any?> =
        post("${getBaseUrl()}/tv/kanal", mapOf("numara" to n))

    suspend fun tvButton(button: String): Map<String, Any?> =
        post("${getBaseUrl()}/tv/button", mapOf("button" to button))

    // --- Hatırlatıcılar ---
    /** Tetiklenen hatırlatıcıları çek ve sunucudan sil. */
    suspend fun getRemindersPending(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}/reminders/pending").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(body, type) ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                (map["pending"] as? List<Map<String, Any?>>) ?: emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getReminders(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}/reminders").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val map: Map<String, Any?> = gson.fromJson(body, type) ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                (map["reminders"] as? List<Map<String, Any?>>) ?: emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addReminder(seconds: Int, text: String): Map<String, Any?> =
        post("${getBaseUrl()}/reminders", mapOf("seconds" to seconds, "text" to text))

    suspend fun cancelReminder(rid: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}/reminders/$rid").delete().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(body, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: ""))
        }
    }

    // --- Rutinler ---
    suspend fun getRoutines(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${getBaseUrl()}/routines").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                gson.fromJson<List<Map<String, Any?>>>(body, type) ?: emptyList()
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun runRoutine(name: String): Map<String, Any?> =
        post("${getBaseUrl()}/routines/run", mapOf("name" to name))

    // --- PC Kontrol ---
    suspend fun pcSleep(): Map<String, Any?>    = get("${getBaseUrl()}/pc/uyut")
    suspend fun pcShutdown(): Map<String, Any?> = get("${getBaseUrl()}/pc/kapat")
    suspend fun pcShutdownCancel(): Map<String, Any?> = get("${getBaseUrl()}/pc/kapat/iptal")
    suspend fun pcVolume(level: Int): Map<String, Any?> =
        post("${getBaseUrl()}/pc/ses", mapOf("level" to level))
    // Android'de kayıt edilen sesi base64 ile PC'ye gönderir,
    // PC'nin Whisper modeli Türkçe metne çevirir.
    // ChatScreen Google STT kullanır; bu metot alternatif/fallback için.
    suspend fun stt(audioBase64: String, sampleRate: Int = 16000): Map<String, Any?> =
        post("${getBaseUrl()}/stt", mapOf("audio_base64" to audioBase64, "sample_rate" to sampleRate))

    /**
     * Backend Piper TTS: Metni sese çevirip WAV byte array olarak döner.
     * null dönerse TTS kullanılamıyor demektir (fallback: Google TTS).
     */
    suspend fun tts(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("text" to text))
            val requestBody = json.toRequestBody(jsonMediaType)
            val request = buildRequest("${getBaseUrl()}/tts").post(requestBody).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        } catch (_: Exception) { null }
    }

    // --- Notlar (Android → Backend senkronizasyon) ---

    /**
     * Telefondaki tüm notları backend'e senkronize et.
     * Backend bu verileri LLM context olarak kullanır
     * ("ilk notumda ne yazıyor?", "market listemde ne var?" gibi sorularda).
     */
    suspend fun syncNotes(notes: List<Map<String, Any?>>): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("notes" to notes))
            val requestBody = json.toRequestBody(jsonMediaType)
            val request = buildRequest("${getBaseUrl()}/notes/sync").post(requestBody).build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) { false }
    }
}
