package com.example.asistant.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistant.models.DeviceStatus

@Composable
fun DeviceStatusBar(
    status: DeviceStatus,
    onToggleLight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E2E), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ampul
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleLight() }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiObjects,
                contentDescription = "Ampul",
                tint = if (status.lightOn) Color(0xFFFFD600) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (status.lightOn) "Açık" else "Kapalı",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }

        Divider()

        // TV
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = "TV",
                tint = if (status.tvConnected) Color(0xFF4FC3F7) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (status.tvConnected) "Bağlı" else "Kapalı",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }

        Divider()

        // AI
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (status.aiReady) Color(0xFF4CAF50) else Color(0xFFE53935))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (status.aiReady) "AI Hazır" else "AI Bekle",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.15f))
    )
}

