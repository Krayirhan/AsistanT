package com.example.asistant.models

data class DeviceStatus(
    val isConnected: Boolean = false,
    val lightOn: Boolean = false,
    val lightBrightness: Int = 100,
    val lightColor: String = "beyaz",
    val tvConnected: Boolean = false,
    val aiReady: Boolean = false,
    val rawResponse: Map<String, Any?> = emptyMap()
)

