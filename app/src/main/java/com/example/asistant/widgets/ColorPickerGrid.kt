package com.example.asistant.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ColorOption(
    val name: String,
    val color: Color
)

val colorOptions = listOf(
    ColorOption("beyaz", Color(0xFFFFFFFF)),
    ColorOption("sıcak beyaz", Color(0xFFFFD9A0)),
    ColorOption("sarı", Color(0xFFFFEB3B)),
    ColorOption("turuncu", Color(0xFFFF9800)),
    ColorOption("kırmızı", Color(0xFFF44336)),
    ColorOption("pembe", Color(0xFFE91E63)),
    ColorOption("mor", Color(0xFF9C27B0)),
    ColorOption("mavi", Color(0xFF2196F3)),
    ColorOption("turkuaz", Color(0xFF00BCD4)),
    ColorOption("yeşil", Color(0xFF4CAF50)),
    ColorOption("nane", Color(0xFF80CBC4)),
    ColorOption("lavanta", Color(0xFFB39DDB))
)

@Composable
fun ColorPickerGrid(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        colorOptions.chunked(4).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { opt ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val isSelected = opt.name == selectedColor
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(opt.color)
                                .then(
                                    if (isSelected)
                                        Modifier.border(3.dp, Color(0xFF7C3AED), CircleShape)
                                    else Modifier
                                )
                                .clickable { onColorSelected(opt.name) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = opt.name.replaceFirstChar { it.uppercase() },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

