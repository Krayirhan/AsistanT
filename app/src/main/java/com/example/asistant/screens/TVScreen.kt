package com.example.asistant.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel

val TvBg = Color(0xFF0A0A0A)
val TvCard = Color(0xFF1A1A1A)
val TvButton = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    val haptic = LocalHapticFeedback.current

    var volumeText by remember { mutableStateOf("") }
    var channelText by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf("") }

    fun hapticAction(block: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        block()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📺 TV Kumanda", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TvBg)
            )
        },
        containerColor = TvBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Durum & Güç
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TvCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (status.tvConnected) "📺 TV Bağlı" else "📴 TV Kapalı",
                            color = if (status.tvConnected) Color(0xFF4FC3F7) else Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (lastAction.isNotBlank()) {
                            Text(
                                text = lastAction,
                                color = Color.White.copy(0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // WoL - Aç
                        Button(
                            onClick = {
                                hapticAction { viewModel.tvOn() }
                                lastAction = "TV açılıyor..."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("⚡ Aç", fontSize = 13.sp) }

                        // Kapat
                        Button(
                            onClick = {
                                hapticAction { viewModel.tvOff() }
                                lastAction = "TV kapatılıyor..."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("⏻ Kapat", fontSize = 13.sp) }
                    }
                }
            }

            // Ses Kontrolü
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TvCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔊 Ses Kontrolü", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                hapticAction { viewModel.tvVolDown() }
                                lastAction = "Ses azaltıldı"
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TvButton)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Ses -", tint = Color.White)
                        }

                        FilledIconButton(
                            onClick = {
                                hapticAction { viewModel.tvMute() }
                                lastAction = "Sessiz"
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF424242))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Sessiz", tint = Color.White)
                        }

                        FilledIconButton(
                            onClick = {
                                hapticAction { viewModel.tvVolUp() }
                                lastAction = "Ses artırıldı"
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TvButton)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Ses +", tint = Color.White)
                        }
                    }

                    // Ses sayı alanı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = volumeText,
                            onValueChange = { if (it.length <= 3) volumeText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("0–100", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                volumeText.toIntOrNull()?.let { v ->
                                    hapticAction { viewModel.tvSetVolume(v) }
                                    lastAction = "Ses: $v"
                                }
                            }),
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                volumeText.toIntOrNull()?.let { v ->
                                    hapticAction { viewModel.tvSetVolume(v) }
                                    lastAction = "Ses: $v"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Ayarla") }
                    }
                }
            }

            // Kanal Kontrolü
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TvCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📡 Kanal Kontrolü", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                hapticAction { viewModel.tvChannelDown() }
                                lastAction = "Kanal -"
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TvButton)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Kanal -", tint = Color.White)
                        }

                        Text("KANAL", color = Color.White.copy(0.5f), fontSize = 13.sp)

                        FilledIconButton(
                            onClick = {
                                hapticAction { viewModel.tvChannelUp() }
                                lastAction = "Kanal +"
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TvButton)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Kanal +", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = channelText,
                            onValueChange = { if (it.length <= 3) channelText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("Kanal no", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                channelText.toIntOrNull()?.let { ch ->
                                    hapticAction { viewModel.tvSetChannel(ch) }
                                    lastAction = "Kanal: $ch"
                                }
                            }),
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                channelText.toIntOrNull()?.let { ch ->
                                    hapticAction { viewModel.tvSetChannel(ch) }
                                    lastAction = "Kanal: $ch"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Git") }
                    }

                    // Hızlı Kanallar
                    Text("Hızlı Kanallar", color = Color.White.copy(0.6f), fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3, 4, 5, 6, 7, 8).forEach { ch ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    hapticAction { viewModel.tvSetChannel(ch) }
                                    lastAction = "Kanal: $ch"
                                },
                                label = { Text("$ch", color = Color.White, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = TvButton,
                                    selectedContainerColor = Color(0xFF7C3AED)
                                ),
                                border = null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF2A2A2A),
    unfocusedContainerColor = Color(0xFF2A2A2A),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF7C3AED),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

