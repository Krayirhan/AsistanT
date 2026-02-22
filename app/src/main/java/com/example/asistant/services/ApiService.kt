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
        private const val DEFAULT_URL = "http://26.207.206.2:8766"
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

    suspend fun getBaseUrl(): String =
        context.dataStore.data.map { prefs -> prefs[KEY_BASE_URL] ?: DEFAULT_URL }.first()

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BASE_URL] = url }
    }

    private suspend fun get(url: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(body, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bilinmeyen hata"))
        }
    }

    private suspend fun post(url: String, body: Map<String, Any?>): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(responseBody, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bilinmeyen hata"))
        }
    }

    private suspend fun postEmpty(url: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val requestBody = "{}".toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(body, type) ?: mapOf("success" to response.isSuccessful)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Bilinmeyen hata"))
        }
    }

    // --- Health ---
    suspend fun health(): Map<String, Any?> = get("${getBaseUrl()}/health")

    /** Test Et butonu için — DataStore'u beklemeden doğrudan verilen URL'yi test eder. */
    suspend fun healthAt(baseUrl: String): Map<String, Any?> = get("$baseUrl/health")

    // --- Chat ---
    suspend fun chat(message: String): Map<String, Any?> =
        post("${getBaseUrl()}/chat", mapOf("message" to message))

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

    // --- STT (PC Whisper) ---
    // Android'de kayıt edilen sesi base64 ile PC'ye gönderir,
    // PC'nin Whisper modeli Türkçe metne çevirir.
    // ChatScreen Google STT kullanır; bu metot alternatif/fallback için.
    suspend fun stt(audioBase64: String, sampleRate: Int = 16000): Map<String, Any?> =
        post("${getBaseUrl()}/stt", mapOf("audio_base64" to audioBase64, "sample_rate" to sampleRate))
}
