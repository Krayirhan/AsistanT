package com.example.asistant.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel
import com.example.asistant.widgets.ColorPickerGrid
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    var brightness by remember { mutableIntStateOf(status.lightBrightness) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(status.lightBrightness) {
        brightness = status.lightBrightness
    }

    val bulbColor by animateColorAsState(
        targetValue = if (status.lightOn) Color(0xFFFFD600) else Color(0xFF555555),
        animationSpec = tween(400),
        label = "bulb_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💡 Ampul Kontrolü", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Ampul İkonu
            Icon(
                imageVector = Icons.Filled.EmojiObjects,
                contentDescription = "Ampul",
                tint = bulbColor,
                modifier = Modifier.size(100.dp)
            )

            Text(
                text = if (status.lightOn) "Açık" else "Kapalı",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Toggle Switch
            Switch(
                checked = status.lightOn,
                onCheckedChange = { viewModel.toggleLight() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7C3AED),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF2A2A2A)
                ),
                modifier = Modifier.size(width = 80.dp, height = 40.dp)
            )

            HorizontalDivider(color = Color.White.copy(0.1f))

            // Parlaklık
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("☀️ Parlaklık", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("$brightness%", color = Color(0xFF7C3AED), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { value ->
                        brightness = value.toInt()
                        debounceJob?.cancel()
                        debounceJob = scope.launch {
                            delay(500)
                            viewModel.setLightBrightness(brightness)
                        }
                    },
                    valueRange = 1f..100f,
                    enabled = status.lightOn,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF2A2A2A)
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(0.1f))

            // Renk Seçici
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "🎨 Renk Seç",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                ColorPickerGrid(
                    selectedColor = status.lightColor,
                    onColorSelected = { renk ->
                        if (status.lightOn) {
                            viewModel.setLightColor(renk, brightness)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!status.lightOn) {
                    Text(
                        "⚠️ Renk seçmek için ampulu açın",
                        color = Color.Yellow.copy(0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

