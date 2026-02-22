package com.example.asistant.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel
import com.example.asistant.widgets.ChatBubble
import kotlinx.coroutines.launch

val BgColor = Color(0xFF1A1A2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    // ── Sesli Komut (Google Speech Recognition — tr-TR) ───────────────────────
    // Sonuç gelince direkt LLM'e gönderilir (aynı /chat pipeline → Tapo/TV kontrol)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)   // → /chat → PC LLM (Tapo/TV intent dahil)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(chatHistory.size - 1)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Geçmişi Temizle", color = Color.White) },
            text = { Text("Tüm sohbet geçmişi silinecek. Emin misiniz?", color = Color.White.copy(0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearChatHistory()
                    showClearDialog = false
                }) { Text("Evet", color = Color(0xFF7C3AED)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E2E)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💬 AI Sohbet", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Temizle", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2E))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Bir şey sor...", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && !isLoading) {
                            viewModel.sendMessage(input.trim())
                            input = ""
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A3E),
                        unfocusedContainerColor = Color(0xFF2A2A3E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C3AED),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (input.isNotBlank() && !isLoading) {
                            viewModel.sendMessage(input.trim())
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !isLoading
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (input.isNotBlank() && !isLoading) Color(0xFF7C3AED) else Color.Gray
                    )
                }
                // ── Mikrofon — Türkçe sesli komut (tr-TR) ──────────────────
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Komutunuzu söyleyin...")
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        speechLauncher.launch(intent)
                    },
                    enabled = !isLoading
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Sesli komut",
                        tint = if (!isLoading) Color(0xFF7C3AED) else Color.Gray
                    )
                }
            }
        }
    ) { padding ->
        if (chatHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤖", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Merhaba! Sana nasıl yardımcı olabilirim?",
                        color = Color.White.copy(0.6f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatHistory, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg.content,
                        isUser = msg.isUser
                    )
                }
            }
        }
    }
}

