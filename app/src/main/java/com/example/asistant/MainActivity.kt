package com.example.asistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF7C3AED),
    secondary = Color(0xFF9D74F0),
    background = Color(0xFF0F0F1A),
    surface = Color(0xFF1E1E2E),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }
}