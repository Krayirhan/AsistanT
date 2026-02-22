package com.example.asistant.screens

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoading by viewModel.isLoading.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()

    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var statusText by remember { mutableStateOf("Başlamak için mikrofona dokun") }
    var lastRecognized by remember { mutableStateOf("") }
    var lastResponse by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }

    // Döngüsel referanssız yeniden başlatma sinyali
    var restartListening by remember { mutableStateOf(0) }

    // TTS
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale("tr", "TR")
            }
        }
        ttsInstance = t
        onDispose { t.stop(); t.shutdown() }
    }

    // STT launcher — kendi içinde referans vermeden sadece state değiştirir
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!isActive) return@rememberLauncherForActivityResult
        val text = if (result.resultCode == android.app.Activity.RESULT_OK)
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim() ?: ""
        else ""

        if (text.isNotBlank()) {
            lastRecognized = text
            voiceState = VoiceState.PROCESSING
            statusText = "Düşünüyor..."
            viewModel.sendMessage(text)
        } else {
            // Boş sonuç → tekrar dinle sinyali
            restartListening++
        }
    }

    // Restart sinyali gelince yeniden dinle
    LaunchedEffect(restartListening) {
        if (restartListening > 0 && isActive) {
            delay(300)
            if (isActive) {
                voiceState = VoiceState.LISTENING
                statusText = "Dinleniyor..."
                speechLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşun...")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                )
            }
        }
    }

    // AI yanıtı gelince TTS → bitince tekrar dinle
    LaunchedEffect(chatHistory.size, isLoading) {
        if (!isLoading && isActive && voiceState == VoiceState.PROCESSING) {
            val msg = chatHistory.lastOrNull { !it.isUser && !it.isLoading }
            if (msg != null && msg.content != lastResponse) {
                lastResponse = msg.content
                val clean = msg.content.replace(Regex("[*_`#>]"), "").trim()
                voiceState = VoiceState.SPEAKING
                statusText = "Konuşuyor..."
                val t = ttsInstance
                if (t != null) {
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            scope.launch {
                                delay(600)
                                if (isActive) restartListening++
                            }
                        }
                        override fun onError(id: String?) {
                            scope.launch { if (isActive) restartListening++ }
                        }
                    })
                    t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tts1")
                } else {
                    delay(1000)
                    if (isActive) restartListening++
                }
            }
        }
    }

    // Pulse animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == VoiceState.LISTENING) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val micColor = when (voiceState) {
        VoiceState.LISTENING  -> Color(0xFF7C3AED)
        VoiceState.PROCESSING -> Color(0xFFFF9800)
        VoiceState.SPEAKING   -> Color(0xFF4CAF50)
        VoiceState.IDLE       -> Color(0xFF2A2A3E)
    }
    val statusColor = when (voiceState) {
        VoiceState.LISTENING  -> Color(0xFF7C3AED)
        VoiceState.PROCESSING -> Color(0xFFFF9800)
        VoiceState.SPEAKING   -> Color(0xFF4CAF50)
        VoiceState.IDLE       -> Color.Gray
    }

    fun startSession() {
        isActive = true
        voiceState = VoiceState.LISTENING
        statusText = "Dinleniyor..."
        speechLauncher.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşun...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    fun stopSession() {
        isActive = false
        ttsInstance?.stop()
        voiceState = VoiceState.IDLE
        statusText = "Başlamak için mikrofona dokun"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎙️ Canlı Konuşma", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { stopSession(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F1A))
            )
        },
        containerColor = Color(0xFF0F0F1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Konuşma kartları
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (lastRecognized.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(Color(0xFF7C3AED).copy(alpha = 0.15f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Siz", color = Color(0xFF7C3AED), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(lastRecognized, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (lastResponse.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(Color(0xFF1E293B))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("AI", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                lastResponse.take(300) + if (lastResponse.length > 300) "…" else "",
                                color = Color.White, fontSize = 15.sp
                            )
                        }
                    }
                }
                if (lastRecognized.isBlank() && lastResponse.isBlank()) {
                    Text("🎙️", fontSize = 64.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "AI ile sesli konuş\nTürkçe komutlar, ampul & TV kontrolü",
                        color = Color.White.copy(0.4f), fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Mikrofon butonu + pulse
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                if (voiceState == VoiceState.LISTENING) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.10f))
                    )
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED).copy(alpha = 0.16f))
                    )
                }
                FloatingActionButton(
                    onClick = { if (!isActive) startSession() else stopSession() },
                    modifier = Modifier.size(110.dp),
                    containerColor = micColor,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "Mikrofon",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            // Durum metni
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }
    }
}
