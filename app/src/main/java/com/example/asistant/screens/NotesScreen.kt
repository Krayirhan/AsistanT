package com.example.asistant.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistant.notes.NoteCategory
import com.example.asistant.notes.NoteColors
import com.example.asistant.notes.NoteRepository
import com.example.asistant.notes.Note

// Siralama modlari
enum class NoteSortMode(val label: String) {
    DATE_DESC("Yeni"),
    DATE_ASC("Eski"),
    TITLE("Başlık"),
    CATEGORY("Kategori"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenEditor: (String?) -> Unit
) {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { NoteRepository.migrateFromTxt(ctx) }

    var notes           by remember { mutableStateOf(NoteRepository.getAll(ctx)) }
    var searchQuery     by remember { mutableStateOf("") }
    var searchVisible   by remember { mutableStateOf(false) }
    var filterCategory  by remember { mutableStateOf<NoteCategory?>(null) }
    var sortMode        by remember { mutableStateOf(NoteSortMode.DATE_DESC) }
    var gridMode        by remember { mutableStateOf(true) }
    var selected        by remember { mutableStateOf(setOf<String>()) }
    var menuExpanded    by remember { mutableStateOf(false) }
    var sortExpanded    by remember { mutableStateOf(false) }
    var clearConfirm    by remember { mutableStateOf(false) }
    var deleteConfirm   by remember { mutableStateOf(false) }
    val selMode = selected.isNotEmpty()

    fun reload() { NoteRepository.invalidateCache(); notes = NoteRepository.getAll(ctx) }

    val displayed = remember(notes, searchQuery, filterCategory, sortMode) {
        var list = notes.filter { note ->
            (searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.body.contains(searchQuery, ignoreCase = true) ||
                note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            ) && (filterCategory == null || note.category == filterCategory)
        }
        list = when (sortMode) {
            NoteSortMode.DATE_DESC -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.modifiedAt })
            NoteSortMode.DATE_ASC  -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.modifiedAt })
            NoteSortMode.TITLE     -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.title.ifBlank { it.body } })
            NoteSortMode.CATEGORY  -> list.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.category.label })
        }
        list
    }

    val totalCount  = notes.size
    val pinnedCount = notes.count { it.pinned }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (selMode) {
                            Text("${selected.size} seçildi", fontWeight = FontWeight.Bold)
                        } else {
                            Column {
                                Text("Notlarım", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "$totalCount not${if (pinnedCount > 0) " - $pinnedCount sabitlemiş" else ""}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (selMode) {
                            IconButton(onClick = { selected = emptySet() }) {
                                Icon(Icons.Filled.Close, "İptal")
                            }
                        } else {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                            }
                        }
                    },
                    actions = {
                        if (selMode) {
                            IconButton(onClick = {
                                selected.forEach { NoteRepository.togglePin(ctx, it) }
                                selected = emptySet(); reload()
                            }) { Icon(Icons.Filled.PushPin, "Sabitle") }
                            IconButton(onClick = { deleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                                Icon(
                                    if (searchVisible) Icons.Filled.SearchOff else Icons.Filled.Search,
                                    "Ara", tint = if (searchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { sortExpanded = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, "Sirala")
                                }
                                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    NoteSortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label, color = if (sortMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                            onClick = { sortMode = mode; sortExpanded = false },
                                            leadingIcon = { if (sortMode == mode) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { gridMode = !gridMode }) {
                                Icon(
                                    if (gridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                    "Gorunum degistir"
                                )
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, "Menu")
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    DropdownMenuItem(
                                        text = { Text("Tümünü Kopyala") },
                                        onClick = {
                                            menuExpanded = false
                                            val all = notes.joinToString("\n\n") { n -> "${if (n.title.isNotBlank()) n.title + "\n" else ""}${n.body}" }
                                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Notlar", all))
                                        },
                                        enabled = notes.isNotEmpty()
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tümünü Sil", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuExpanded = false; clearConfirm = true },
                                        enabled = notes.isNotEmpty()
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                // Arama cubugu
                AnimatedVisibility(visible = searchVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        placeholder = { Text("Başlık, içerik veya etiket...") },
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(14.dp), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = if (searchQuery.isNotBlank()) {{ IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Clear, "Temizle") } }} else null
                    )
                }
                // Kategori filtreleri
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { NCatChip("Tumu", null, filterCategory == null) { filterCategory = null } }
                    items(NoteCategory.values()) { cat ->
                        val count = notes.count { it.category == cat }
                        if (count > 0) NCatChip("${cat.emoji} ${cat.label}", count, filterCategory == cat) {
                            filterCategory = if (filterCategory == cat) null else cat
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
            }
        },
        floatingActionButton = {
            if (!selMode) {
                FloatingActionButton(
                    onClick = { onOpenEditor(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) { Icon(Icons.Filled.Add, "Yeni not", tint = Color.White) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (displayed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (searchQuery.isNotBlank() || filterCategory != null) Icons.Filled.SearchOff else Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotBlank() || filterCategory != null) "Eşleşen not yok" else "Henüz not yok",
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (searchQuery.isNotBlank() || filterCategory != null) "Belki filtreyi temizlemeyi dene"
                        else "Sağ alttaki + ile not oluştur",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                    if (searchQuery.isNotBlank() || filterCategory != null) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { searchQuery = ""; filterCategory = null },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Filtreyi Temizle") }
                    }
                }
            }
        } else if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayed, key = { it.id }) { note ->
                    NoteGridCard(note = note, selected = note.id in selected, selMode = selMode,
                        onClick = { if (selMode) { selected = if (note.id in selected) selected - note.id else selected + note.id } else onOpenEditor(note.id) },
                        onLongClick = { selected = selected + note.id }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayed, key = { _, n -> n.id }) { _, note ->
                    NoteListCard(note = note, searchQuery = searchQuery, selected = note.id in selected, selMode = selMode,
                        onClick = { if (selMode) { selected = if (note.id in selected) selected - note.id else selected + note.id } else onOpenEditor(note.id) },
                        onLongClick = { selected = selected + note.id },
                        onPin = { NoteRepository.togglePin(ctx, note.id); reload() },
                        onShare = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, buildString { if (note.title.isNotBlank()) append(note.title + "\n"); append(note.body) }) }, "Paylas")) },
                        onDelete = { NoteRepository.delete(ctx, note.id); reload() }
                    )
                }
            }
        }
    }

    // Secili silme onay
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("${selected.size} notu sil?", fontWeight = FontWeight.Bold) },
            text  = { Text("Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { NoteRepository.deleteAll(ctx, selected); selected = emptySet(); deleteConfirm = false; reload() }) { Text("Sil", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("İptal") } }
        )
    }
    // Tumu sil onay
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Tüm notları sil?", fontWeight = FontWeight.Bold) },
            text  = { Text("$totalCount notun tamamı kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { NoteRepository.clearAll(ctx); clearConfirm = false; reload() }) { Text("Tümünü Sil", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("İptal") } }
        )
    }
}

// Grid kart
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteGridCard(note: Note, selected: Boolean, selMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bg     = Color(NoteColors.palette[note.colorIndex])
    val accent = Color(NoteColors.accents[note.colorIndex])
    val alpha  by animateFloatAsState(if (selMode && !selected) 0.5f else 1f, label = "")

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).alpha(alpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(if (note.pinned) 6.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${note.category.emoji}", fontSize = 14.sp)
                    if (note.pinned) Icon(Icons.Filled.PushPin, null, tint = accent, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (note.title.isNotBlank()) {
                    Text(note.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
                Text(note.preview, color = Color.White.copy(0.7f), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                if (note.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.tags.take(2).forEach { tag -> Text("#$tag", color = accent.copy(0.8f), fontSize = 9.sp) }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(note.formattedDate(), color = Color.White.copy(0.3f), fontSize = 9.sp)
            }
            if (selected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

// Liste kart
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListCard(
    note: Note, searchQuery: String, selected: Boolean, selMode: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
    onPin: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit
) {
    val bg     = Color(NoteColors.palette[note.colorIndex])
    val accent = Color(NoteColors.accents[note.colorIndex])
    val alpha  by animateFloatAsState(if (selMode && !selected) 0.5f else 1f, label = "")

    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(if (note.pinned) 6.dp else 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)).align(Alignment.CenterVertically))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(note.formattedDate(), color = accent.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(0.15f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("${note.category.emoji} ${note.category.label}", color = accent, fontSize = 9.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (note.pinned) Icon(Icons.Filled.PushPin, null, tint = Color(0xFFFFA726), modifier = Modifier.size(12.dp).padding(end = 2.dp))
                        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                if (note.title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(note.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(3.dp))
                Text(note.preview, color = Color.White.copy(0.75f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (note.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        note.tags.take(3).forEach { tag -> Text("#$tag", color = accent.copy(0.7f), fontSize = 10.sp) }
                    }
                }
                if (!selMode) {
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${note.wordCount} kelime", color = Color.White.copy(0.25f), fontSize = 10.sp)
                        Row {
                            IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.PushPin, "Sabitle", tint = if (note.pinned) Color(0xFFFFA726) else Color.White.copy(0.3f), modifier = Modifier.size(14.dp)) }
                            IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Share, "Paylas", tint = Color.White.copy(0.3f), modifier = Modifier.size(14.dp)) }
                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error.copy(0.5f), modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// Kategori filtre chip
@Composable
private fun NCatChip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = if (selected) primary.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) primary else MaterialTheme.colorScheme.outline.copy(0.3f)
    val textColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = textColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (count != null) Text("($count)", color = textColor.copy(0.6f), fontSize = 10.sp)
        }
    }
}