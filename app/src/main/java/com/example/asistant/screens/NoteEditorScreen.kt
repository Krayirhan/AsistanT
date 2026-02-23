package com.example.asistant.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistant.notes.NoteCategory
import com.example.asistant.notes.NoteColors
import com.example.asistant.notes.NoteRepository
import com.example.asistant.notes.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String?,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mevcut notu yukle veya yeni olustur
    val existing = remember(noteId) {
        if (noteId != null) NoteRepository.getAll(ctx).firstOrNull { it.id == noteId } else null
    }

    var title       by remember { mutableStateOf(existing?.title ?: "") }
    var body        by remember { mutableStateOf(existing?.body  ?: "") }
    var colorIndex  by remember { mutableIntStateOf(existing?.colorIndex ?: 0) }
    var tagInput    by remember { mutableStateOf("") }
    var tags        by remember { mutableStateOf(existing?.tags ?: emptyList()) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTagInput    by remember { mutableStateOf(false) }
    var saved           by remember { mutableStateOf(false) }

    val noteId2 = remember { existing?.id ?: UUID.randomUUID().toString() }
    val noteBg  = Color(NoteColors.palette[colorIndex])
    val accent  = Color(NoteColors.accents[colorIndex])
    val bodyFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Otomatik kategori
    val autoCategory = remember(title, body) { NoteCategory.detect("$title $body") }

    // Otomatik kaydetme - 1.5 sn bekledikten sonra
    LaunchedEffect(title, body, colorIndex, tags) {
        saved = false
        delay(1500)
        if (title.isNotBlank() || body.isNotBlank()) {
            val note = Note(
                id         = noteId2,
                title      = title.trim(),
                body       = body,
                colorIndex = colorIndex,
                category   = autoCategory,
                tags       = tags,
                pinned     = existing?.pinned ?: false,
                createdAt  = existing?.createdAt ?: System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            NoteRepository.save(ctx, note)
            NoteRepository.invalidateCache()
            saved = true
        }
    }

    fun saveAndBack() {
        scope.launch {
            if (title.isNotBlank() || body.isNotBlank()) {
                val note = Note(
                    id         = noteId2,
                    title      = title.trim(),
                    body       = body,
                    colorIndex = colorIndex,
                    category   = autoCategory,
                    tags       = tags,
                    pinned     = existing?.pinned ?: false,
                    createdAt  = existing?.createdAt ?: System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )
                NoteRepository.save(ctx, note)
                NoteRepository.invalidateCache()
            }
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { saveAndBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White.copy(0.8f))
                    }
                },
                actions = {
                    // Kaydedildi gostergesi
                    AnimatedVisibility(visible = saved, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Kaydedildi", color = accent, fontSize = 12.sp)
                        }
                    }
                    // Etiket butonu
                    IconButton(onClick = { showTagInput = !showTagInput }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label, "Etiket",
                            tint = if (tags.isNotEmpty()) accent else Color.White.copy(0.5f)
                        )
                    }
                    // Renk butonu
                    IconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Icon(Icons.Filled.Palette, "Renk", tint = accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = noteBg)
            )
        },
        containerColor = noteBg
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState)
        ) {
            // Renk secici
            AnimatedVisibility(visible = showColorPicker) {
                ColorPickerBar(
                    selected = colorIndex,
                    onSelect = { colorIndex = it; showColorPicker = false },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Baslik alani
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                decorationBox = { inner ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (title.isBlank()) Text("Başlık", color = Color.White.copy(0.3f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = Color.White.copy(0.06f), modifier = Modifier.padding(horizontal = 20.dp))

            // Kategori + meta bilgi
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("${autoCategory.emoji} ${autoCategory.label}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(existing?.formattedDate() ?: "Yeni not", color = Color.White.copy(0.35f), fontSize = 11.sp)
                if (body.isNotBlank()) {
                    Text("\u2022", color = Color.White.copy(0.2f), fontSize = 11.sp)
                    Text("${body.trim().split(Regex("\\s+")).count { it.isNotBlank() }} kelime", color = Color.White.copy(0.35f), fontSize = 11.sp)
                }
            }

            // Etiketler
            if (tags.isNotEmpty() || showTagInput) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tags.forEach { tag ->
                        TagChip(tag = tag, accent = accent, onRemove = { tags = tags - tag })
                    }
                }
            }

            // Etiket giris alani
            AnimatedVisibility(visible = showTagInput) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = accent, modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            if (tagInput.isBlank()) Text("Etiket ekle (Enter ile onayla)", color = Color.White.copy(0.3f), fontSize = 13.sp)
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (tagInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val t = tagInput.trim()
                                if (t.isNotBlank() && !tags.contains(t)) tags = tags + t
                                tagInput = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) { Icon(Icons.Filled.Add, "Ekle", tint = accent, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // Govde alani
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = TextStyle(color = Color.White.copy(0.9f), fontSize = 15.sp, lineHeight = 24.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 300.dp)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        if (body.isBlank()) Text("Notunuzu buraya yazın...", color = Color.White.copy(0.25f), fontSize = 15.sp)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus)
            )

            // Karakter sayaci
            if (body.isNotBlank()) {
                Text(
                    "${body.length} karakter",
                    color = Color.White.copy(0.2f), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End).padding(end = 20.dp, bottom = 20.dp)
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// Renk secici
@Composable
private fun ColorPickerBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NoteColors.palette.forEachIndexed { idx, colorLong ->
            val isSelected = idx == selected
            Box(
                modifier = Modifier.size(if (isSelected) 38.dp else 32.dp)
                    .clip(CircleShape).background(Color(colorLong))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(NoteColors.accents[idx]) else Color.White.copy(0.2f),
                        shape = CircleShape
                    ).clickable { onSelect(idx) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Filled.Check, null, tint = Color(NoteColors.accents[idx]), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Etiket chip
@Composable
private fun TagChip(tag: String, accent: Color, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(accent.copy(0.15f))
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("#$tag", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onRemove, modifier = Modifier.size(16.dp)) {
            Icon(Icons.Filled.Close, "Kaldır", tint = accent.copy(0.7f), modifier = Modifier.size(10.dp))
        }
    }
}

// BasicTextField wrapper (Material3 uyumlu)
@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        decorationBox = decorationBox,
        modifier = modifier,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(0.7f))
    )
}