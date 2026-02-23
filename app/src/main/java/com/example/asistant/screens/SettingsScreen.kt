package com.example.asistant.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.asistant.APP_PERMISSIONS
import com.example.asistant.providers.AssistantViewModel
import com.example.asistant.services.ApiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val api = ApiService.getInstance(context)
    val scope = rememberCoroutineScope()
    val currentUrl by viewModel.baseUrl.collectAsState()

    var ipText by remember(currentUrl) {
        val cleaned = currentUrl.removePrefix("https://").removePrefix("http://")
        val parts = cleaned.split(":")
        mutableStateOf(parts.getOrElse(0) { "26.207.206.2" })
    }
    var portText by remember(currentUrl) {
        if (currentUrl.startsWith("https://")) {
            mutableStateOf("443")
        } else {
            val cleaned = currentUrl.removePrefix("http://")
            val parts = cleaned.split(":")
            mutableStateOf(parts.getOrElse(1) { "8766" })
        }
    }

    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var healthInfo by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var apiKeyText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apiKeyText = api.getApiKey() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
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
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Backend Baglantisi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backend Bağlantısı", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = ipText, onValueChange = { ipText = it },
                        label = { Text("PC IP Adresi") },
                        placeholder = { Text("26.207.206.2") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    OutlinedTextField(
                        value = portText, onValueChange = { portText = it.filter { c -> c.isDigit() } },
                        label = { Text("Port (443 = HTTPS tünel)") },
                        placeholder = { Text("8766") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    OutlinedTextField(
                        value = apiKeyText, onValueChange = { apiKeyText = it.trim() },
                        label = { Text("API Anahtarı") },
                        placeholder = { Text("Test Et ile otomatik alınır") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )

                    testResult?.let { result ->
                        val isSuccess = result.contains("Bağlandı") || result.contains("Kaydedildi")
                        Text(
                            text = result,
                            color = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isTesting = true; testResult = null
                                scope.launch {
                                    try {
                                        val testUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                                        val resp = api.healthAt(testUrl)
                                        testResult = if (resp.containsKey("error")) {
                                            "Bağlanamadı: ${resp["error"]}"
                                        } else {
                                            healthInfo = resp
                                            val serverKey = resp["api_key"]?.toString() ?: ""
                                            if (serverKey.isNotBlank()) { apiKeyText = serverKey; api.saveApiKey(serverKey) }
                                            "Bağlandı! ${resp["version"] ?: ""}"
                                        }
                                    } catch (e: Exception) { testResult = "Hata: ${e.message}" }
                                    finally { isTesting = false }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            enabled = !isTesting
                        ) {
                            if (isTesting) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Test Et")
                        }
                        Button(
                            onClick = {
                                val newUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                                scope.launch { viewModel.saveBaseUrl(newUrl); if (apiKeyText.isNotBlank()) api.saveApiKey(apiKeyText) }
                                testResult = "Kaydedildi: $newUrl"
                            },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Kaydet") }
                    }
                }
            }

            // Health bilgisi
            if (healthInfo.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sistem Bilgisi", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        InfoRow("API Versiyonu", healthInfo["version"]?.toString() ?: "-")
                        InfoRow("Tapo Durumu", healthInfo["tapo"]?.toString() ?: "-")
                        InfoRow("TV Durumu", healthInfo["tv"]?.toString() ?: "-")
                        InfoRow("LLM Durumu", healthInfo["llm"]?.toString() ?: "-")
                    }
                }
            }

            // Izin durumu
            PermissionsCard()

            // Uygulama Hakkinda
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Uygulama Hakkında", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    InfoRow("Versiyon", "1.0.0")
                    val builtUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                    InfoRow("Backend", builtUrl)
                    Button(
                        onClick = {
                            val url = if (portText == "443") "https://$ipText/docs" else "http://$ipText:$portText/docs"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Swagger Docs Aç") }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current

    val permLabels = mapOf(
        Manifest.permission.RECORD_AUDIO       to "Mikrofon (sesli komut ve wake word)",
        Manifest.permission.READ_CONTACTS      to "Rehber (arama ve WhatsApp)",
        Manifest.permission.CALL_PHONE         to "Telefon (doğrudan arama)",
        Manifest.permission.CAMERA             to "Kamera (fotoğraf ve el feneri)",
        Manifest.permission.POST_NOTIFICATIONS to "Bildirimler (durum bildirimleri)",
    )

    val statuses = remember {
        APP_PERMISSIONS.map { perm ->
            val label = permLabels[perm] ?: perm.substringAfterLast('.')
            val granted = context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            Triple(perm, label, granted)
        }
    }

    val anyDenied = statuses.any { !it.third }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Uygulama İzinleri", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            statuses.forEach { (_, label, granted) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (granted) {
                        Icon(Icons.Filled.Check, contentDescription = "Verildi", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Warning, contentDescription = "Verilmedi", tint = Color(0xFFFFA726), modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (anyDenied) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text(
                    "Eksik izinler bazı özellikleri devre dışı bırakır. Uygulama ayarlarından tüm izinleri manuel olarak verebilirsiniz.",
                    color = Color(0xFFFFA726), fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA726))
                ) { Text("Uygulama Ayarlarını Aç") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.4f),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)