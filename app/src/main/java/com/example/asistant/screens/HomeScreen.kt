package com.example.asistant.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.providers.AssistantViewModel
import com.example.asistant.widgets.DeviceStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToLight: () -> Unit,
    onNavigateToTV: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToVoice: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    viewModel: AssistantViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startWakeWordService()
        else viewModel.syncWakeWordState()
    }

    LaunchedEffect(Unit) {
        viewModel.syncWakeWordState()
        viewModel.refreshStatus()
    }

    lastError?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ATLAS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.size(10.dp).clip(CircleShape)
                                .background(if (status.isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            data class CardItem(val icon: ImageVector, val title: String, val subtitle: String, val action: () -> Unit, val active: Boolean = false)

            val lightStatus = if (status.lightOn) "${status.lightBrightness}% \u2022 ${status.lightColor}" else "Kapalı"
            val tvStatus = if (status.tvConnected) "Bağlı" else "Kapalı"

            val cards = listOf(
                CardItem(Icons.AutoMirrored.Filled.Chat, "Sohbet", "AI ile konuş", onNavigateToChat),
                CardItem(Icons.Filled.EmojiObjects, "Ampul", lightStatus, onNavigateToLight, status.lightOn),
                CardItem(Icons.Filled.Tv, "TV", tvStatus, onNavigateToTV, status.tvConnected),
                CardItem(Icons.Filled.Settings, "Ayarlar", "Bağlantı ve Bilgi", onNavigateToSettings),
                CardItem(Icons.Filled.EditNote, "Notlar", "Sesli notlarım", onNavigateToNotes),
            )

            cards.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        DashboardCard(icon = card.icon, title = card.title, subtitle = card.subtitle, active = card.active, onClick = card.action, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // Canli Konusma karti
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToVoice() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Canlı Konuşma", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sesli sohbet modu", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f), fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.5f))
                }
            }

            // Uyandirma Sozcugu karti
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (wakeWordEnabled) { viewModel.stopWakeWordService() }
                    else {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startWakeWordService()
                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (wakeWordEnabled) MaterialTheme.colorScheme.tertiary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        if (wakeWordEnabled) Icons.Filled.MicExternalOn else Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = if (wakeWordEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hey ATLAS", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (wakeWordEnabled) "Arka planda dinleniyor" else "Uyandırma sözcüğü kapalı",
                            color = if (wakeWordEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = {
                            if (wakeWordEnabled) viewModel.stopWakeWordService()
                            else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startWakeWordService()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiary.copy(0.3f)
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            DeviceStatusBar(status = status, onToggleLight = { viewModel.toggleLight() }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DashboardCard(
    icon: ImageVector,
    title: String = "",
    subtitle: String,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}