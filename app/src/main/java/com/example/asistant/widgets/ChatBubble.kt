package com.example.asistant.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val UserBubbleColor = Color(0xFF7C3AED)
val AiBubbleColor = Color(0xFF1E293B)

@Composable
fun ChatBubble(
    message: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(if (isUser) UserBubbleColor else AiBubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isUser) {
                // Kullanıcı mesajları kısa olur, sade göster
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // AI mesajları Markdown ile göster
                MarkdownText(text = message)
            }
        }
    }
}

/** Basit Markdown renderer: **kalın**, *italik*, `kod`, ```blok```, # başlık, - liste */
@Composable
fun MarkdownText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val codeBlockRegex = Regex("```(?:[a-zA-Z]*\\n)?([\\s\\S]*?)```")
        val parts = mutableListOf<Pair<String, Boolean>>() // içerik, kodBlok mu?

        var lastEnd = 0
        for (match in codeBlockRegex.findAll(text)) {
            if (match.range.first > lastEnd)
                parts.add(text.substring(lastEnd, match.range.first) to false)
            parts.add(match.groupValues[1].trim() to true)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) parts.add(text.substring(lastEnd) to false)
        if (parts.isEmpty()) parts.add(text to false)

        for ((content, isCode) in parts) {
            if (isCode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D1117))
                        .padding(8.dp)
                ) {
                    Text(
                        text = content,
                        color = Color(0xFF58D68D),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            } else {
                for (line in content.split("\n")) {
                    if (line.isBlank()) {
                        Spacer(Modifier.height(2.dp))
                        continue
                    }
                    val t = line.trim()
                    when {
                        t.startsWith("### ") -> MdHeading(t.removePrefix("### "), 14.sp.value)
                        t.startsWith("## ")  -> MdHeading(t.removePrefix("## "),  15.sp.value)
                        t.startsWith("# ")   -> MdHeading(t.removePrefix("# "),   16.sp.value)
                        t == "---" || t == "***" -> Box(
                            Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.2f))
                        )
                        t.matches(Regex("^[-*] .+")) -> Row {
                            Text("• ", color = Color(0xFF7C3AED), fontSize = 15.sp)
                            Text(parseInline(t.substring(2)), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                        t.matches(Regex("^\\d+\\. .+")) -> {
                            val dot = t.indexOf(". ")
                            Row {
                                Text("${t.substring(0, dot + 1)} ", color = Color(0xFF7C3AED), fontSize = 15.sp)
                                Text(parseInline(t.substring(dot + 2)), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                        else -> Text(parseInline(t), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MdHeading(text: String, sizeSp: Float) {
    Text(
        text = parseInline(text),
        color = Color.White,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (sizeSp + 6).sp
    )
}

/** **kalın**, *italik*, `inline kod` → AnnotatedString */
fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`")
    var lastEnd = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) append(text.substring(lastEnd, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            match.groupValues[2].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[2]) }
            match.groupValues[3].isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace,
                    background = Color(0xFF0D1117), color = Color(0xFF58D68D))) {
                    append(match.groupValues[3])
                }
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) append(text.substring(lastEnd))
}

