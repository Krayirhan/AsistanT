package com.example.asistant.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    val haptic = LocalHapticFeedback.current
    var lastAct by remember { mutableStateOf("") }
    var chanText by remember { mutableStateOf("") }
    var volText by remember { mutableStateOf("") }

    val TvCard = MaterialTheme.colorScheme.surfaceVariant
    val TvBtn = MaterialTheme.colorScheme.surface
    val TvAccent = MaterialTheme.colorScheme.primary

    fun btn(label: String, block: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        lastAct = label; block()
    }
    fun tvBtn(button: String, label: String = button) = btn(label) { viewModel.tvButton(button) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TV Kumanda")
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (status.tvConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // DURUM + GUC
            TvSection(TvCard) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (status.tvConnected) "Bağlı" else "Kapalı", color = if (status.tvConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (lastAct.isNotBlank()) Text(lastAct, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundBtn("Aç", MaterialTheme.colorScheme.tertiary.copy(0.7f)) { btn("TV açılıyor") { viewModel.tvOn() } }
                        RoundBtn("Power", TvBtn) { tvBtn("POWER") }
                        RoundBtn("Kapat", MaterialTheme.colorScheme.error.copy(0.8f)) { btn("TV kapatılıyor") { viewModel.tvOff() } }
                    }
                }
            }

            // SES
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("SES")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TvIconBtn(52.dp, TvBtn, { btn("Ses -") { viewModel.tvVolDown() } }) { Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                        TvIconBtn(52.dp, TvBtn, { btn("Sessiz") { viewModel.tvMute() } }) { Icon(Icons.AutoMirrored.Filled.VolumeOff, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                        TvIconBtn(52.dp, TvBtn, { btn("Ses +") { viewModel.tvVolUp() } }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TinyTextField(volText, { if (it.length <= 3) volText = it.filter(Char::isDigit) }, "0-100", Modifier.weight(1f), TvBtn, TvAccent) {
                            volText.toIntOrNull()?.let { v -> btn("Ses: $v") { viewModel.tvSetVolume(v) } }
                        }
                        SmallBtn("Ayarla", TvAccent) { volText.toIntOrNull()?.let { v -> btn("Ses: $v") { viewModel.tvSetVolume(v) } } }
                    }
                }
            }

            // KANAL
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("KANAL")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TvIconBtn(52.dp, TvBtn, { btn("CH -") { viewModel.tvChannelDown() } }) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                        SmallBtn("GUIDE", TvAccent) { tvBtn("GUIDE") }
                        SmallBtn("LIST", TvAccent) { tvBtn("LIST") }
                        TvIconBtn(52.dp, TvBtn, { btn("CH +") { viewModel.tvChannelUp() } }) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TinyTextField(chanText, { if (it.length <= 4) chanText = it.filter(Char::isDigit) }, "Kanal no", Modifier.weight(1f), TvBtn, TvAccent) {
                            chanText.toIntOrNull()?.let { ch -> btn("Kanal: $ch") { viewModel.tvSetChannel(ch) } }
                        }
                        SmallBtn("Git", TvAccent) { chanText.toIntOrNull()?.let { ch -> btn("Kanal: $ch") { viewModel.tvSetChannel(ch) } } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..8).forEach { ch ->
                            FilterChip(
                                selected = false, onClick = { btn("Kanal $ch") { viewModel.tvSetChannel(ch) } },
                                label = { Text("$ch", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(containerColor = TvBtn), border = null
                            )
                        }
                    }
                }
            }

            // NAVIGASYON D-PAD
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("NAVIGASYON")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        TvIconBtn(54.dp, TvBtn, { tvBtn("UP", "Yukarı") }) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurface) }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvIconBtn(54.dp, TvBtn, { tvBtn("LEFT", "Sol") }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurface) }
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(TvAccent, TvAccent.copy(0.7f)))),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { tvBtn("ENTER", "OK") }, modifier = Modifier.fillMaxSize()) {
                                    Text("OK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TvIconBtn(54.dp, TvBtn, { tvBtn("RIGHT", "Sağ") }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface) }
                        }
                        Spacer(Modifier.height(4.dp))
                            TvIconBtn(54.dp, TvBtn, { tvBtn("DOWN", "Aşağı") }) { Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("HOME" to "Ana", "BACK" to "Geri", "EXIT" to "Çıkış", "MENU" to "Menü", "QMENU" to "Q", "INFO" to "Bilgi").forEach { (b, l) ->
                            TvTextBtn(l, TvBtn) { tvBtn(b) }
                        }
                    }
                }
            }

            // MEDYA
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("MEDYA")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TvIconBtn(48.dp, TvBtn, { tvBtn("REWIND", "Geri Sar") }) { Icon(Icons.Filled.FastRewind, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                        TvIconBtn(48.dp, TvBtn, { tvBtn("PLAY", "Oynat") }) { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                        TvIconBtn(56.dp, TvAccent, { tvBtn("PAUSE", "Durdur") }) { Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                        TvIconBtn(48.dp, TvBtn, { tvBtn("STOP", "Dur") }) { Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                        TvIconBtn(48.dp, TvBtn, { tvBtn("FASTFORWARD", "İleri Sar") }) { Icon(Icons.Filled.FastForward, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                        TvIconBtn(48.dp, MaterialTheme.colorScheme.error.copy(0.7f), { tvBtn("RECORD", "Kayıt") }) { Icon(Icons.Filled.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // RENK TUSLARI
            TvSection(TvCard) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("RENK")
                    listOf("RED" to Color(0xFFE53935), "GREEN" to Color(0xFF43A047), "YELLOW" to Color(0xFFFDD835), "BLUE" to Color(0xFF1E88E5)).forEach { (b, c) ->
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { tvBtn(b) }, modifier = Modifier.fillMaxSize()) {}
                        }
                    }
                }
            }

            // NUMPAD
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("NUMPAD")
                    listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","-")).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                val webosKey = when (key) { "*" -> "ASTERISK"; "-" -> "DASH"; else -> key }
                                Box(Modifier.weight(1f)) {
                                    Button(
                                        onClick = { tvBtn(webosKey, key) }, modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = TvBtn), contentPadding = PaddingValues(0.dp)
                                    ) { Text(key, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }

            // SMART TV
            TvSection(TvCard) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("SMART TV")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Netflix" to "NETFLIX", "Amazon" to "AMAZON", "Uyg." to "MYAPPS", "Son" to "RECENT").forEach { (l, b) ->
                            FilterChip(selected = false, onClick = { tvBtn(b, l) }, label = { Text(l, fontSize = 12.sp, textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = TvAccent.copy(0.3f), borderWidth = 1.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Giriş" to "INPUT_HUB", "Program" to "PROGRAM", "Ekran K." to "SCREEN_REMOTE", "SAP" to "SAP").forEach { (l, b) ->
                            FilterChip(selected = false, onClick = { tvBtn(b, l) }, label = { Text(l, fontSize = 12.sp, textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(containerColor = TvBtn), border = null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Yardimci composable'lar

@Composable
private fun TvSection(cardColor: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = cardColor, tonalElevation = 2.dp) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun TvIconBtn(size: Dp = 52.dp, color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TvTextBtn(text: String, btnColor: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(btnColor), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundBtn(text: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
private fun SmallBtn(text: String, accentColor: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TinyTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, btnColor: Color, accentColor: Color, onDone: () -> Unit) {
    TextField(
        value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }), modifier = modifier, singleLine = true, shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = btnColor, unfocusedContainerColor = btnColor,
            focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = accentColor, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
    )
}