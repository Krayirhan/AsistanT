package com.example.asistant.screens

import android.content.Intent
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import android.util.Log
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
import com.example.asistant.services.ApiService
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
    var restartListening by remember { mutableStateOf(0) }

    // Backend Piper TTS (erkek ses)
    val api = remember { ApiService.getInstance(context) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!isActive) return@rememberLauncherForActivityResult
        val text = if (result.resultCode == android.app.Activity.RESULT_OK)
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim() ?: ""
        else ""

        if (text.isNotBlank()) {
            lastRecognized = text
            voiceState = VoiceState.PROCESSING
            statusText = "Düşünüyor..."
            viewModel.sendMessage(text)
        } else {
            restartListening++
        }
    }

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

    LaunchedEffect(chatHistory.size, isLoading) {
        if (!isLoading && isActive && voiceState == VoiceState.PROCESSING) {
            val msg = chatHistory.lastOrNull { !it.isUser && !it.isLoading }
            if (msg != null && msg.content != lastResponse) {
                lastResponse = msg.content
                val clean = msg.content.replace(Regex("[*_`#>]"), "")
                    .replace(Regex("\\p{So}"), "")
                    .trim().take(500)
                voiceState = VoiceState.SPEAKING
                statusText = "Konuşuyor..."

                // Backend Piper TTS ile seslendir
                try {
                    val wavBytes = api.tts(clean)
                    if (wavBytes != null) {
                        mediaPlayer?.release()
                        val tmpFile = java.io.File.createTempFile("voice_tts_", ".wav", context.cacheDir)
                        tmpFile.writeBytes(wavBytes)
                        val mp = MediaPlayer()
                        mediaPlayer = mp
                        mp.setDataSource(tmpFile.absolutePath)
                        mp.setOnCompletionListener {
                            it.release()
                            tmpFile.delete()
                            mediaPlayer = null
                            scope.launch { delay(600); if (isActive) restartListening++ }
                        }
                        mp.setOnErrorListener { p, _, _ ->
                            p.release()
                            tmpFile.delete()
                            mediaPlayer = null
                            scope.launch { if (isActive) restartListening++ }
                            true
                        }
                        mp.prepare()
                        mp.start()
                    } else {
                        // Backend TTS ulaşılamıyor — sessiz devam
                        delay(1000); if (isActive) restartListening++
                    }
                } catch (e: Exception) {
                    Log.w("VoiceScreen", "TTS hatası: ${e.message}")
                    delay(1000); if (isActive) restartListening++
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == VoiceState.LISTENING) 1.3f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val micColor = when (voiceState) {
        VoiceState.LISTENING  -> MaterialTheme.colorScheme.primary
        VoiceState.PROCESSING -> Color(0xFFFF9800)
        VoiceState.SPEAKING   -> MaterialTheme.colorScheme.tertiary
        VoiceState.IDLE       -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusColor = when (voiceState) {
        VoiceState.LISTENING  -> MaterialTheme.colorScheme.primary
        VoiceState.PROCESSING -> Color(0xFFFF9800)
        VoiceState.SPEAKING   -> MaterialTheme.colorScheme.tertiary
        VoiceState.IDLE       -> MaterialTheme.colorScheme.onSurfaceVariant
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
        mediaPlayer?.release()
        mediaPlayer = null
        voiceState = VoiceState.IDLE
        statusText = "Başlamak için mikrofona dokun"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canlı Konuşma") },
                navigationIcon = {
                    IconButton(onClick = { stopSession(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (lastRecognized.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Siz", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(lastRecognized, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (lastResponse.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("AI", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                lastResponse.take(300) + if (lastResponse.length > 300) "..." else "",
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp
                            )
                        }
                    }
                }
                if (lastRecognized.isBlank() && lastResponse.isBlank()) {
                    Icon(
                        Icons.Filled.Mic, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "AI ile sesli konuş\nTürkçe komutlar, ampul ve TV kontrolü",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                }
            }

            // Mikrofon butonu + pulse
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 24.dp)) {
                if (voiceState == VoiceState.LISTENING) {
                    Box(
                        modifier = Modifier.size(190.dp).scale(pulse).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    )
                    Box(
                        modifier = Modifier.size(145.dp).scale(pulse).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
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