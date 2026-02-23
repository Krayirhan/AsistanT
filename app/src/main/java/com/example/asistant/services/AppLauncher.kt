package com.example.asistant.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.util.regex.Pattern

/**
 * Telefon uygulama başlatıcı.
 * "telefonda spotify aç" → Spotify'ı telefondan açar
 * "tvde spotify aç"      → TV komutu olarak işaretler (ViewModel TV'ye gönderir)
 */
object AppLauncher {

    private const val TAG = "AppLauncher"

    enum class IntentContext { PHONE, TV, AMBIGUOUS }

    data class AppIntent(
        val appKey:      String,           // "spotify"
        val displayName: String,           // "Spotify"
        val packageName: String,           // "com.spotify.music"
        val context:     IntentContext,
        val fallbackUrl: String? = null,   // web fallback
    )

    // ── Uygulama Sözlüğü ────────────────────────────────────────────────────
    // Türkçe anahtar kelimeler → (displayName, packageName, fallbackUrl)
    private val APP_MAP: Map<String, Triple<String, String, String?>> = mapOf(
        // Müzik
        "spotify"         to Triple("Spotify",       "com.spotify.music",              "https://open.spotify.com"),
        "youtube müzik"   to Triple("YouTube Music", "com.google.android.apps.youtube.music", null),
        "music"           to Triple("YouTube Music", "com.google.android.apps.youtube.music", null),

        // Video
        "youtube"         to Triple("YouTube",       "com.google.android.youtube",     "https://youtube.com"),
        "netflix"         to Triple("Netflix",        "com.netflix.mediaclient",        "https://netflix.com"),
        "amazon"          to Triple("Amazon Prime",  "com.amazon.avod.thirdpartyclient","https://primevideo.com"),
        "amazon prime"    to Triple("Amazon Prime",  "com.amazon.avod.thirdpartyclient","https://primevideo.com"),
        "twitch"          to Triple("Twitch",         "tv.twitch.android.app",          "https://twitch.tv"),
        "disney"          to Triple("Disney+",        "com.disney.disneyplus",          "https://disneyplus.com"),
        "tubi"            to Triple("Tubi",           "com.tubitv",                     null),

        // Sosyal
        "instagram"       to Triple("Instagram",     "com.instagram.android",          null),
        "whatsapp"        to Triple("WhatsApp",      "com.whatsapp",                   null),
        "twitter"         to Triple("Twitter / X",   "com.twitter.android",            "https://x.com"),
        "x"               to Triple("Twitter / X",   "com.twitter.android",            "https://x.com"),
        "tiktok"          to Triple("TikTok",        "com.zhiliaoapp.musically",       "https://tiktok.com"),
        "telegram"        to Triple("Telegram",      "org.telegram.messenger",         null),
        "facebook"        to Triple("Facebook",      "com.facebook.katana",            "https://facebook.com"),
        "snapchat"        to Triple("Snapchat",      "com.snapchat.android",           null),
        "linkedin"        to Triple("LinkedIn",      "com.linkedin.android",           "https://linkedin.com"),
        "reddit"          to Triple("Reddit",        "com.reddit.frontpage",           "https://reddit.com"),

        // Google
        "harita"          to Triple("Haritalar",     "com.google.android.apps.maps",   "https://maps.google.com"),
        "maps"            to Triple("Haritalar",     "com.google.android.apps.maps",   "https://maps.google.com"),
        "chrome"          to Triple("Chrome",        "com.android.chrome",             null),
        "gmail"           to Triple("Gmail",         "com.google.android.gm",          "https://gmail.com"),
        "drive"           to Triple("Google Drive",  "com.google.android.apps.docs",   "https://drive.google.com"),
        "meet"            to Triple("Google Meet",   "com.google.android.apps.tachyon",null),
        "takvim"          to Triple("Takvim",        "com.google.android.calendar",    null),
        "translate"       to Triple("Çevirici",      "com.google.android.apps.translate",null),
        "fotoğraflar"     to Triple("Fotoğraflar",   "com.google.android.apps.photos", null),
        "fotolar"         to Triple("Fotoğraflar",   "com.google.android.apps.photos", null),

        // Sistem / Araçlar
        "kamera"          to Triple("Kamera",        "android.media.action.IMAGE_CAPTURE", null),
        "galeri"          to Triple("Galeri",        "com.google.android.apps.photos", null),
        "ayarlar"         to Triple("Ayarlar",       "com.android.settings",           null),
        "hesap makinesi"  to Triple("Hesap Makinesi","com.android.calculator2",        null),
        "dosyalar"        to Triple("Dosyalar",      "com.google.android.documentsui", null),
        "tarayıcı"        to Triple("Chrome",        "com.android.chrome",             null),
        "mesajlar"        to Triple("Mesajlar",      "com.android.mms",                null),
        "sms"             to Triple("Mesajlar",      "com.android.mms",                null),
        "telefon"         to Triple("Telefon",       "com.android.phone",              null),
        "müzik"           to Triple("Müzik",         "com.google.android.music",       null),

        // Oyun / Diğer
        "clash"           to Triple("Clash of Clans","com.supercell.clashofclans",     null),
        "pubg"            to Triple("PUBG",          "com.tencent.ig",                 null),
        "fortnite"        to Triple("Fortnite",      "com.epicgames.fortnite",         null),
    )

    // ── TV-özel uygulamalar (öneki olmadan TV'ye git) ─────────────────────
    // Bunlar hem telefondan hem TV'den açılabilir ama TV-native tercihli.
    val TV_NATIVE_APPS = setOf(
        "netflix", "amazon", "amazon prime", "disney", "twitch",
        "youtube", "spotify", "dazn", "hbo", "hbo max",
        "apple tv", "crunchyroll", "tubi", "plex"
    )

    // ── Regex Desenleri ───────────────────────────────────────────────────
    // Telefon bağlamı: "telefonda X aç", "telefonumda X aç", "telefondan X aç"
    private val PHONE_PREFIX = Pattern.compile(
        """(?:telefon[a-zäöüşğçı]*\s+|phone\s+|mobil[a-z]*\s+)""",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    // TV bağlamı: "tvde X aç", "televizyonda X aç", "TV'de X aç"
    private val TV_PREFIX = Pattern.compile(
        """(?:tv[a-zäöüşğçı']*\s+|televizyon[a-zäöüşğçı']*\s+|ekran[a-z]*\s+)""",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    // Aç eylemi sonları
    private val OPEN_SUFFIX = Pattern.compile(
        """(?:\s+aç[a-zäöüşğçı]*|\s+başlat[a-zäöüşğçı]*|\s+open|\s+launch|\s+çalıştır[a-zäöüşğçı]*)$""",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    // ── Ana Tespit Fonksiyonu ─────────────────────────────────────────────

    /**
     * Metni analiz et; telefon komutu varsa AppIntent döner, yoksa null.
     *
     * @param text Kullanıcı mesajı
     * @return AppIntent veya null
     */
    fun detect(text: String): AppIntent? {
        val t = text.trim().lowercase()

        // Bağlamı tespit et
        val hasPhonePrefix = PHONE_PREFIX.matcher(t).find()
        val hasTvPrefix    = TV_PREFIX.matcher(t).find()

        // "aç" eylemi var mı?
        val hasOpenSuffix = OPEN_SUFFIX.matcher(t).find()
        val hasOpen = hasOpenSuffix
                || t.contains(" aç") || t.endsWith("aç")
                || t.contains(" başlat")
                || t.contains("open ") || t.contains("launch ")

        if (!hasOpen) return null

        // Prefix'i ve "aç" ifadesini temizle, ortada kalan app adını bul
        val cleaned = t
            .replace(PHONE_PREFIX.toRegex(), "")
            .replace(TV_PREFIX.toRegex(), "")
            .replace(OPEN_SUFFIX.toRegex(), "")
            .replace(Regex("""\s+aç[ıioöuü]?$"""), "")
            .trim()

        // Uzun isimleri önce dene
        val appKey = APP_MAP.keys.sortedByDescending { it.length }
            .firstOrNull { cleaned.contains(it) }
            ?: return null

        val (displayName, packageName, fallbackUrl) = APP_MAP[appKey] ?: return null

        val ctx = when {
            hasPhonePrefix -> IntentContext.PHONE
            hasTvPrefix    -> IntentContext.TV
            appKey in TV_NATIVE_APPS -> IntentContext.AMBIGUOUS  // her ikisi zaten TV command sayılır
            else           -> IntentContext.PHONE  // varsayılan: telefonda aç
        }

        return AppIntent(appKey, displayName, packageName, ctx, fallbackUrl)
    }

    // ── Uygulama Açma ─────────────────────────────────────────────────────

    /**
     * Verilen AppIntent'i Android'de başlat.
     * @return true = başlatıldı, false = uygulama yüklü değil ve fallback da yok
     */
    fun launch(context: android.content.Context, intent: AppIntent): Boolean {
        // Özel action'lar (kamera gibi)
        val specialActions = setOf("android.media.action.IMAGE_CAPTURE")
        if (intent.packageName in specialActions) {
            return try {
                val i = Intent(intent.packageName).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(i)
                true
            } catch (e: Exception) { false }
        }

        // Normal paket ile dene
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(intent.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.i(TAG, "Uygulama açıldı: ${intent.packageName}")
                true
            } else {
                // Yüklü değil — fallback URL
                tryFallback(context, intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Uygulama başlatılamadı: ${e.message}")
            tryFallback(context, intent)
        }
    }

    private fun tryFallback(context: android.content.Context, intent: AppIntent): Boolean {
        val url = intent.fallbackUrl ?: return false
        return try {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(web)
            Log.i(TAG, "Web fallback açıldı: $url")
            true
        } catch (e: Exception) { false }
    }

    fun isInstalled(context: android.content.Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }
}
