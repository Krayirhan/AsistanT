package com.example.asistant.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val SettingsBg = Color(0xFF0F0F1A)
    val SettingsCard = Color(0xFF1E1E2E)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Ayarlar", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsBg)
            )
        },
        containerColor = SettingsBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Backend Bağlantısı
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SettingsCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🌐 Backend Bağlantısı", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("PC IP Adresi", color = Color.Gray) },
                        placeholder = { Text("26.207.206.2", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { c -> c.isDigit() } },
                        label = { Text("Port (443 = HTTPS tünel)", color = Color.Gray) },
                        placeholder = { Text("8766", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    testResult?.let { result ->
                        val isSuccess = result.startsWith("✅")
                        Text(
                            text = result,
                            color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE53935),
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    try {
                                        val testUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                                        val resp = api.healthAt(testUrl)
                                        testResult = if (resp.containsKey("error")) {
                                            "❌ Bağlanamadı: ${resp["error"]}"
                                        } else {
                                            healthInfo = resp
                                            "✅ Bağlandı! ${resp["version"] ?: ""}"
                                        }
                                    } catch (e: Exception) {
                                        testResult = "❌ Hata: ${e.message}"
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED)),
                            enabled = !isTesting
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    color = Color(0xFF7C3AED),
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("🔌 Test Et")
                            }
                        }

                        Button(
                            onClick = {
                                val newUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                                viewModel.saveBaseUrl(newUrl)
                                testResult = "✅ Kaydedildi: $newUrl"
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text("💾 Kaydet")
                        }
                    }
                }
            }

            // Health Bilgisi
            if (healthInfo.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SettingsCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📊 Sistem Bilgisi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        InfoRow("API Versiyonu", healthInfo["version"]?.toString() ?: "-")
                        InfoRow("Tapo Durumu", healthInfo["tapo"]?.toString() ?: "-")
                        InfoRow("TV Durumu", healthInfo["tv"]?.toString() ?: "-")
                        InfoRow("LLM Durumu", healthInfo["llm"]?.toString() ?: "-")
                    }
                }
            }

            // Uygulama Hakkında
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SettingsCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ℹ️ Uygulama Hakkında", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    InfoRow("Versiyon", "1.0.0")
                    val builtUrl = if (portText == "443") "https://$ipText" else "http://$ipText:$portText"
                    InfoRow("Backend", builtUrl)

                    Button(
                        onClick = {
                            val url = if (portText == "443") "https://$ipText/docs" else "http://$ipText:$portText/docs"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text("🔗 Swagger Docs'u Aç")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(0.6f), fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF7C3AED),
    unfocusedBorderColor = Color.White.copy(0.2f),
    cursorColor = Color(0xFF7C3AED)
)