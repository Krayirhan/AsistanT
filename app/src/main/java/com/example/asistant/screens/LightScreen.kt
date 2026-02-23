package com.example.asistant.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

    LaunchedEffect(status.lightBrightness) { brightness = status.lightBrightness }

    val bulbColor by animateColorAsState(
        targetValue = if (status.lightOn) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
        animationSpec = tween(400), label = "bulb_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ampul Kontrolü") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Ampul ikonu
            Icon(
                imageVector = Icons.Filled.EmojiObjects,
                contentDescription = "Ampul",
                tint = bulbColor,
                modifier = Modifier.size(100.dp)
            )

            Text(
                text = if (status.lightOn) "Açık" else "Kapalı",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold
            )

            // Toggle Switch
            Switch(
                checked = status.lightOn,
                onCheckedChange = { viewModel.toggleLight() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.size(width = 80.dp, height = 40.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Parlaklik
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Parlaklık", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("$brightness%", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { value ->
                            brightness = value.toInt()
                            debounceJob?.cancel()
                            debounceJob = scope.launch { delay(500); viewModel.setLightBrightness(brightness) }
                        },
                        valueRange = 1f..100f, enabled = status.lightOn,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Preset Modlar
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preset Modlar", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class Preset(val label: String, val renk: String, val pct: Int)
                        val presets = listOf(
                            Preset("Gece", "turuncu", 15),
                            Preset("Film", "mor", 40),
                            Preset("Çalışma", "beyaz", 100),
                            Preset("Okuma", "sıcak beyaz", 70),
                        )
                        presets.forEach { preset ->
                            Button(
                                onClick = {
                                    if (status.lightOn) { viewModel.setLightColor(preset.renk, preset.pct); brightness = preset.pct }
                                },
                                modifier = Modifier.weight(1f), enabled = status.lightOn,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                                ),
                                contentPadding = PaddingValues(4.dp)
                            ) { Text(preset.label, fontSize = 11.sp, maxLines = 1) }
                        }
                    }
                }
            }

            // Renk secici
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Renk Seç", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    ColorPickerGrid(
                        selectedColor = status.lightColor,
                        onColorSelected = { renk -> if (status.lightOn) viewModel.setLightColor(renk, brightness) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!status.lightOn) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Renk seçmek için ampulü açın",
                            color = Color(0xFFFFA726), fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}