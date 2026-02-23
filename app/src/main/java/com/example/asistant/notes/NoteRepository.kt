package com.example.asistant.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════════════════
// Renk paleti — Google Keep benzeri 8 renk
// ══════════════════════════════════════════════════════════════════════════════

object NoteColors {
    val palette = listOf(
        0xFF1E1E2E.toLong(), // Koyu (varsayılan)
        0xFF3B1D5E.toLong(), // Mor
        0xFF1A3A2A.toLong(), // Yeşil
        0xFF1A2C3A.toLong(), // Mavi
        0xFF2A1A3A.toLong(), // Çivit
        0xFF3A1A1A.toLong(), // Kırmızı
        0xFF3A2A1A.toLong(), // Kahve
        0xFF2A2A10.toLong(), // Zeytinyağı
    )
    val names = listOf("Koyu", "Mor", "Yeşil", "Mavi", "Çivit", "Kırmızı", "Kahve", "Zeytin")
    val accents = listOf(
        0xFF7C3AED.toLong(),
        0xFFAB47BC.toLong(),
        0xFF4CAF50.toLong(),
        0xFF29B6F6.toLong(),
        0xFF7986CB.toLong(),
        0xFFEF5350.toLong(),
        0xFFFF8A65.toLong(),
        0xFFD4E157.toLong(),
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Kategori
// ══════════════════════════════════════════════════════════════════════════════

enum class NoteCategory(val label: String, val emoji: String, val keywords: List<String>) {
    ALISVERIS(
        "Alışveriş", "🛒",
        listOf("al", "satın", "market", "ekmek", "süt", "yumurta", "meyve", "sebze", "alış", "dükkan", "liste", "lazım", "gerek")
    ),
    FIKIR(
        "Fikir", "💡",
        listOf("fikir", "proje", "plan", "düşünce", "öğren", "araştır", "dene", "geliştir", "uygula", "keşfet")
    ),
    HATIRLATICI(
        "Hatırlatıcı", "⏰",
        listOf("unutma", "hatırla", "önemli", "acil", "randevu", "toplantı", "deadline", "son gün", "teslim", "saat", "tarih", "yarın", "bugün", "hafta")
    ),
    KISISEL(
        "Kişisel", "👤",
        listOf("ben", "benim", "kişisel", "günlük", "hisset", "duygu", "aile", "arkadaş")
    ),
    IS(
        "İş", "💼",
        listOf("iş", "toplantı", "rapor", "sunum", "müşteri", "ofis", "görev")
    ),
    GENEL("Genel", "📌", emptyList());

    companion object {
        fun detect(text: String): NoteCategory {
            if (text.isBlank()) return GENEL
            val lower = text.lowercase(Locale.forLanguageTag("tr-TR"))
            values().forEach { cat ->
                if (cat == GENEL) return@forEach
                if (cat.keywords.any { lower.contains(it) }) return cat
            }
            return GENEL
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Model
// ══════════════════════════════════════════════════════════════════════════════

data class Note(
    val id:         String        = UUID.randomUUID().toString(),
    val title:      String        = "",
    val body:       String        = "",
    val colorIndex: Int           = 0,
    val category:   NoteCategory  = NoteCategory.GENEL,
    val tags:       List<String>  = emptyList(),
    val pinned:     Boolean       = false,
    val createdAt:  Long          = System.currentTimeMillis(),
    val modifiedAt: Long          = System.currentTimeMillis()
) {
    val isEmpty  get() = title.isBlank() && body.isBlank()
    val preview  get() = body.take(140).ifBlank { "(boş not)" }
    val wordCount get() = (title + " " + body).trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val charCount get() = body.length

    fun formattedDate(pattern: String = "dd MMM HH:mm"): String =
        SimpleDateFormat(pattern, Locale.forLanguageTag("tr-TR")).format(Date(modifiedAt))

    fun toJson(): JSONObject = JSONObject().apply {
        put("id",         id)
        put("title",      title)
        put("body",       body)
        put("colorIndex", colorIndex)
        put("category",   category.name)
        put("tags",       JSONArray(tags))
        put("pinned",     pinned)
        put("createdAt",  createdAt)
        put("modifiedAt", modifiedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Note {
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            val tags    = (0 until tagsArr.length()).map { tagsArr.getString(it) }
            val cat     = runCatching { NoteCategory.valueOf(o.optString("category", "GENEL")) }.getOrDefault(NoteCategory.GENEL)
            return Note(
                id         = o.optString("id",        UUID.randomUUID().toString()),
                title      = o.optString("title",     ""),
                body       = o.optString("body",      ""),
                colorIndex = o.optInt("colorIndex",   0),
                category   = cat,
                tags       = tags,
                pinned     = o.optBoolean("pinned",   false),
                createdAt  = o.optLong("createdAt",   System.currentTimeMillis()),
                modifiedAt = o.optLong("modifiedAt",  System.currentTimeMillis())
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Repository — tek gerçek veri kaynağı
// ══════════════════════════════════════════════════════════════════════════════

object NoteRepository {

    private const val FILE = "notes.json"
    private var cache: MutableList<Note>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun getAll(ctx: Context): List<Note> {
        if (cache != null) return cache!!
        val f = file(ctx)
        if (!f.exists()) { cache = mutableListOf(); return cache!! }
        return runCatching {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            cache = (0 until arr.length()).map { Note.fromJson(arr.getJSONObject(it)) }.toMutableList()
            cache!!
        }.getOrElse { cache = mutableListOf(); cache!! }
    }

    private fun flush(ctx: Context) {
        val arr = JSONArray()
        cache?.forEach { arr.put(it.toJson()) }
        file(ctx).writeText(arr.toString(2), Charsets.UTF_8)
    }

    fun save(ctx: Context, note: Note) {
        getAll(ctx)
        val idx = cache!!.indexOfFirst { it.id == note.id }
        if (idx >= 0) cache!![idx] = note else cache!!.add(0, note)
        flush(ctx)
    }

    fun delete(ctx: Context, id: String) {
        getAll(ctx)
        cache!!.removeAll { it.id == id }
        flush(ctx)
    }

    fun deleteAll(ctx: Context, ids: Set<String>) {
        getAll(ctx)
        cache!!.removeAll { it.id in ids }
        flush(ctx)
    }

    fun clearAll(ctx: Context) {
        cache = mutableListOf()
        flush(ctx)
    }

    fun togglePin(ctx: Context, id: String) {
        getAll(ctx)
        val idx = cache!!.indexOfFirst { it.id == id }
        if (idx >= 0) {
            cache!![idx] = cache!![idx].copy(pinned = !cache!![idx].pinned, modifiedAt = System.currentTimeMillis())
            flush(ctx)
        }
    }

    /** Eski notlar.txt dosyasını JSON'a tek seferlik migrate eder */
    fun migrateFromTxt(ctx: Context) {
        val old = File(ctx.filesDir, "notlar.txt")
        if (!old.exists() || file(ctx).exists()) return
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("tr-TR"))
        val re  = Regex("""^\[?(?:PIN\])?\[?([^\]]+)]\s*(.*)$""")
        val now = System.currentTimeMillis()
        old.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val m    = re.find(line.trim())
            val body = m?.groupValues?.getOrNull(2)?.trim() ?: line.trim()
            if (body.isNotBlank()) {
                val ts = runCatching { sdf.parse(m?.groupValues?.get(1) ?: "")?.time ?: now }.getOrDefault(now)
                save(ctx, Note(body = body, category = NoteCategory.detect(body), createdAt = ts, modifiedAt = ts))
            }
        }
        old.renameTo(File(ctx.filesDir, "notlar_backup.txt"))
    }

    fun invalidateCache() { cache = null }
}
