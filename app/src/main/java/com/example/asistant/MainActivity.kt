package com.example.asistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.asistant.providers.AssistantViewModel
import com.example.asistant.screens.*
import com.example.asistant.services.NotificationHelper

/** Uygulamanın gerektirdiği tüm runtime izinleri — tek bir listede. */
val APP_PERMISSIONS: Array<String> = buildList {
    // Mikrofon — wake word servisi + sesli komut
    add(Manifest.permission.RECORD_AUDIO)
    // Rehber okuma — arama / WhatsApp yönlendirme
    add(Manifest.permission.READ_CONTACTS)
    // Doğrudan telefon araması (ACTION_CALL)
    add(Manifest.permission.CALL_PHONE)
    // Kamera — el feneri (CameraManager) + fotoğraf çekme
    add(Manifest.permission.CAMERA)
    // Android 13+ bildirim izni
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFF9E86FF),
    onSecondary = Color.White,
    background = Color(0xFF121218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B22),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF252432),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF49454F),
    outlineVariant = Color(0xFF332F38),
    error = Color(0xFFEF5350),
    tertiary = Color(0xFF4CAF50),
)

class MainActivity : ComponentActivity() {

    /** Tüm izinleri tek seferde isteyen launcher. */
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // İzin sonuçlarını (isteğe bağlı) loglayabilir ya da kullanıcıya bildirebilirsiniz.
        // Kritik işlemler (arama, mikrofon) kendi akışlarında canlı kontrol yapacak.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bildirim kanalını oluştur (Android 8+)
        NotificationHelper.createChannel(this)

        // Eksik izinleri tespit et ve tek bir sistem diyaloğuyla iste
        val missing = APP_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing)
        }

        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val sharedViewModel: AssistantViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToLight = { navController.navigate("light") },
                onNavigateToTV = { navController.navigate("tv") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToVoice = { navController.navigate("voice") },
                onNavigateToNotes = { navController.navigate("notes") },
                viewModel = sharedViewModel
            )
        }
        composable("chat") {
            ChatScreen(
                onBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }
        composable("light") {
            LightScreen(
                onBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }
        composable("tv") {
            TVScreen(
                onBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }
        composable("voice") {
            VoiceScreen(
                onBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }
        composable("notes") {
            NotesScreen(
                onBack = { navController.popBackStack() },
                onOpenEditor = { noteId ->
                    navController.navigate("note_editor/${noteId ?: "new"}")
                }
            )
        }
        composable("note_editor/{noteId}") { backStack ->
            val rawId = backStack.arguments?.getString("noteId")
            NoteEditorScreen(
                noteId = if (rawId == "new") null else rawId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}