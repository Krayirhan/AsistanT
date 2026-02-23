package com.example.asistant.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import com.example.asistant.notes.Note
import com.example.asistant.notes.NoteCategory
import com.example.asistant.notes.NoteRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Telefon yerel aksiyonlarÄ± â€” tamamen cihazda, sÄ±fÄ±r dÄ±ÅŸ veri.
 *
 * Desteklenen komutlar:
 *   Fener       â†’ "feneri aÃ§ / kapat"
 *   Pil         â†’ "pil durumu / ÅŸarj kaÃ§ta"
 *   WiFi        â†’ "wifi durumu / internete baÄŸlÄ± mÄ±yÄ±m"
 *   FotoÄŸraf    â†’ "fotoÄŸraf Ã§ek / selfie Ã§ek"
 *   Not         â†’ "not al: ..."
 *   Medya       â†’ "mÃ¼ziÄŸi durdur / sÄ±radaki ÅŸarkÄ± / Ã¶nceki ÅŸarkÄ±"
 *   Alarm       â†’ "sabah 7'ye alarm kur / 7:30 alarm"
 *   ZamanlayÄ±cÄ± â†’ "10 dakika zamanlayÄ±cÄ± kur"
 *   Arama       â†’ "anneyi ara / 05xx'i ara"
 *   WhatsApp    â†’ "ahmet'e yaz: merhaba / ahmet'e mesaj at"
 */
object PhoneActionsHandler {

    private const val TAG = "PhoneActions"
    private var torchCameraId: String? = null

    enum class ActionType {
        FLASHLIGHT_ON, FLASHLIGHT_OFF,
        BATTERY, WIFI,
        CAMERA,
        NOTE,
        MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREV,
        ALARM, TIMER,
        CALL, WHATSAPP,
    }

    data class PhoneAction(
        val type:  ActionType,
        val arg1:  String = "",   // contact / time / duration / note text
        val arg2:  String = "",   // message for whatsapp / am-pm marker
    )

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // Intent Tespiti
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private fun t(text: String) = text.lowercase(Locale.forLanguageTag("tr-TR")).trim()

    fun detect(text: String): PhoneAction? {
        val s = t(text)

        // â”€â”€ Fener â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""(el\s*)?fener[i]?\s*aÃ§|torch\s*aÃ§""").containsMatchIn(s))
            return PhoneAction(ActionType.FLASHLIGHT_ON)
        if (Regex("""(el\s*)?fener[i]?\s*kapat|torch\s*kapat""").containsMatchIn(s))
            return PhoneAction(ActionType.FLASHLIGHT_OFF)

        // â”€â”€ Pil / Åžarj â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""(pil|batarya|ÅŸarj)\s*(durumu|kaÃ§ta|ne\s*kadar|kaÃ§\s*(?:ta|te|da|de)?|seviyesi)?""")
                .containsMatchIn(s) && Regex("""pil|batarya|ÅŸarj""").containsMatchIn(s))
            return PhoneAction(ActionType.BATTERY)

        // â”€â”€ WiFi / Ä°nternet â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""(wifi|wi-fi|internet|kablosuz)\s*(durumu|baÄŸlÄ±\s*mÄ±|ne|hÄ±z|sinyal|aÃ§Ä±k\s*mÄ±)""")
                .containsMatchIn(s))
            return PhoneAction(ActionType.WIFI)

        // â”€â”€ FotoÄŸraf / Selfie â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""(fotoÄŸraf|resim)\s*Ã§ek|selfie\s*Ã§ek|kamera\s*aÃ§""").containsMatchIn(s))
            return PhoneAction(ActionType.CAMERA)

        // â”€â”€ Not al â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val noteM = Regex("""(?:not\s*al|not\s*et|kaydet)\s*[:ï¼š]\s*(.+)""").find(s)
        if (noteM != null) return PhoneAction(ActionType.NOTE, noteM.groupValues[1].trim())

        // â”€â”€ Medya â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""(mÃ¼zik[i]?|ÅŸarkÄ±[yÄ±]*)\s*(durdur|duraklat|pause)""").containsMatchIn(s))
            return PhoneAction(ActionType.MEDIA_PAUSE)
        if (Regex("""(mÃ¼zik[i]?|ÅŸarkÄ±[yÄ±]*)\s*(devam|baÅŸlat|Ã§al|play)""").containsMatchIn(s))
            return PhoneAction(ActionType.MEDIA_PLAY)
        if (Regex("""(sÄ±radaki|sonraki|ileri)\s*(ÅŸarkÄ±|parÃ§a)|ÅŸarkÄ±yÄ±\s*geÃ§|next""").containsMatchIn(s))
            return PhoneAction(ActionType.MEDIA_NEXT)
        if (Regex("""(Ã¶nceki|geri)\s*(ÅŸarkÄ±|parÃ§a)|previous""").containsMatchIn(s))
            return PhoneAction(ActionType.MEDIA_PREV)

        // â”€â”€ ZamanlayÄ±cÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""zamanlayÄ±cÄ±|timer|kronometre|geri\s*sayÄ±m""").containsMatchIn(s)) {
            val sec = parseDuration(s)
            return PhoneAction(ActionType.TIMER, sec.toString())
        }

        // â”€â”€ Alarm â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (Regex("""alarm\s*kur|alarm\s*ayarla|\balarma?\b.*\d|^\d.*alarm""").containsMatchIn(s)) {
            val (timeStr, period) = parseAlarmTime(s)
            return PhoneAction(ActionType.ALARM, timeStr, period)
        }

        // â”€â”€ WhatsApp mesajÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Ã–rnekler: "babam yaz", "babama yaz", "babam yaz: merhaba", "baba yaz"
        val waM = Regex("""(.+?)\s+(?:whatsapp|wp)['a]?\s*(?:tan|ten|'tan|'ten)?\s*(?:yaz|mesaj\s*at|gÃ¶nder)(?:\s*[:ï¼š]\s*(.+))?$""").find(s)
            ?: Regex("""(.+?)'?[eaÃ¼Ä±iou]+\s+(?:yaz|mesaj\s*(?:at|gÃ¶nder|yolla))(?:\s*[:ï¼š]\s*(.+))?$""").find(s)
            ?: Regex("""(.+?)\s+yaz\s*[:ï¼š]\s*(.+)$""").find(s)
            ?: Regex("""^(.+?)\s+(?:yaz|mesaj\s*(?:at|gÃ¶nder|yolla))\s*(?:[:ï¼š]\s*(.+))?$""").find(s)   // "babam yaz" â€” ek zorunluluÄŸu yok
        if (waM != null) {
            val contact = waM.groupValues[1].trim().replace(Regex("""['''\s]+$"""), "").trim()
            val msg = waM.groupValues.getOrNull(2)?.trim() ?: ""
            if (contact.length >= 2 && !isExcluded(contact))
                return PhoneAction(ActionType.WHATSAPP, contact, msg)
        }

        // â”€â”€ Telefon aramasÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val callM = Regex("""(.+?)'?(?:[Ä±iuÃ¼]|yi?|yÄ±?|Ã¼|u)?\s+ara(?:yalÄ±m|yÄ±n|Ð¼Ð°|ma)?$""").find(s)
            ?: Regex("""^(?:ara|telefon\s*et|call)\s+(.+)$""").find(s)
        if (callM != null) {
            val contact = (callM.groupValues.getOrNull(1) ?: callM.groupValues.getOrNull(2) ?: "").trim()
            if (contact.length >= 2 && !isExcluded(contact))
                return PhoneAction(ActionType.CALL, contact)
        }

        return null
    }

    /** ATLAS adı, uygulama adı, zamirler, hatırlatıcı kelimeleri gibi yanlış eşleşmeleri filtrele */
    private fun isExcluded(name: String): Boolean {
        val n = name.lowercase(Locale.forLanguageTag("tr-TR")).trim()
        // Uygulama & sistem kelimeleri
        val appWords = Regex(
            """asistan|atlas|furkan|servis|uygulama|netflix|spotify|youtube|tv|televizyon|haber|müzik|şarkı|
              |alarm|timer|zamanlayıcı|not|liste|hatırlatıcı|hatırlat|oluştur|kur|ekle|için|kadar|yarın|bugün|akşam|sabah|öğlen"""
                .trimMargin("|")
        )
        if (appWords.containsMatchIn(n)) return true
        // Zamirler & belirsiz isimler
        val pronouns = setOf(
            "bana", "sana", "ona", "bize", "size", "onlara",
            "ben", "sen", "o", "biz", "siz", "onlar",
            "bunu", "ÅŸunu", "onu", "bunlarÄ±", "bir", "ÅŸey",
            "burada", "orada", "ÅŸurada", "ÅŸun"
        )
        if (n in pronouns) return true
        // Rakam iÃ§eriyorsa isim deÄŸil (saat, tarih vs.)
        if (n.any { it.isDigit() }) return true
        // 4+ kelimeden oluÅŸan ifade isim olamaz
        if (n.split(Regex("""\s+""")).size >= 4) return true
        return false
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // Eylem Ã‡alÄ±ÅŸtÄ±rma â€” TÃ¼rkÃ§e yanÄ±t dÃ¶ndÃ¼rÃ¼r
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    fun execute(context: Context, action: PhoneAction): String = when (action.type) {
        ActionType.FLASHLIGHT_ON  -> { setTorch(context, true);  "ðŸ”¦ Fener aÃ§Ä±ldÄ±." }
        ActionType.FLASHLIGHT_OFF -> { setTorch(context, false); "ðŸ”¦ Fener kapatÄ±ldÄ±." }
        ActionType.BATTERY        -> getBattery(context)
        ActionType.WIFI           -> getWifi(context)
        ActionType.CAMERA         -> openCamera(context)
        ActionType.NOTE           -> saveNote(context, action.arg1)
        ActionType.MEDIA_PAUSE    -> { media(context, KeyEvent.KEYCODE_MEDIA_PAUSE);    "â¸ MÃ¼zik duraklatÄ±ldÄ±." }
        ActionType.MEDIA_PLAY     -> { media(context, KeyEvent.KEYCODE_MEDIA_PLAY);     "â–¶ MÃ¼zik oynatÄ±lÄ±yor." }
        ActionType.MEDIA_NEXT     -> { media(context, KeyEvent.KEYCODE_MEDIA_NEXT);     "â­ SÄ±radaki ÅŸarkÄ±." }
        ActionType.MEDIA_PREV     -> { media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS); "â® Ã–nceki ÅŸarkÄ±." }
        ActionType.ALARM          -> setAlarm(context, action.arg1, action.arg2)
        ActionType.TIMER          -> setTimer(context, action.arg1.toLongOrNull() ?: 300L)
        ActionType.CALL           -> makeCall(context, action.arg1)
        ActionType.WHATSAPP       -> sendWhatsApp(context, action.arg1, action.arg2)
    }

    // â”€â”€ Fener â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun setTorch(context: Context, on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = torchCameraId ?: cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchCameraId = id
            cm.setTorchMode(id, on)
        } catch (e: Exception) { Log.e(TAG, "Torch: ${e.message}") }
    }

    // â”€â”€ Pil â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun getBattery(context: Context): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level   = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            val icon = when {
                charging    -> "âš¡"
                level >= 80 -> "ðŸŸ¢"
                level >= 30 -> "ðŸŸ¡"
                else        -> "ðŸ”´"
            }
            val status = if (charging) "Åžarj oluyor" else "Pil"
            "$icon $status: %$level"
        } catch (e: Exception) { "Pil bilgisi alÄ±namadÄ±." }
    }

    // â”€â”€ WiFi â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun getWifi(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wm.isWifiEnabled) return "ðŸ“¡ WiFi kapalÄ±."
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            @Suppress("DEPRECATION")
            val ssid = info.ssid?.trim('"') ?: "?"
            val quality = when {
                info.rssi >= -50 -> "MÃ¼kemmel"
                info.rssi >= -60 -> "Ä°yi"
                info.rssi >= -70 -> "Orta"
                else             -> "ZayÄ±f"
            }
            "ðŸ“¡ WiFi: $ssid â€” $quality (${info.rssi} dBm)"
        } catch (e: Exception) { "WiFi bilgisi alÄ±namadÄ±." }
    }

    // â”€â”€ Kamera â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun openCamera(context: Context): String {
        return try {
            context.startActivity(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "ðŸ“· Kamera aÃ§Ä±lÄ±yor."
        } catch (_: Exception) { "Kamera aÃ§Ä±lamadÄ±." }
    }

    // â”€â”€ Not â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun saveNote(context: Context, text: String): String {
        if (text.isBlank()) return "Ne not almamÄ± istiyorsun?"
        return try {
            val note = Note(
                body     = text,
                category = NoteCategory.detect(text)
            )
            NoteRepository.save(context, note)
            NoteRepository.invalidateCache()
            "ðŸ“ Not alÄ±ndÄ±: \"$text\""
        } catch (_: Exception) { "Not kaydedilemedi." }
    }

    // â”€â”€ Medya â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun media(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   keyCode))
    }

    // â”€â”€ Alarm â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun setAlarm(context: Context, timeStr: String, period: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                putExtra(AlarmClock.EXTRA_MESSAGE, "ATLAS")
                if (timeStr.isNotEmpty()) {
                    val parts  = timeStr.split(":")
                    var hour   = parts[0].trim().toIntOrNull() ?: 7
                    val minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
                    if (period == "pm" && hour < 12) hour += 12
                    if (period == "am" && hour == 12) hour  = 0
                    putExtra(AlarmClock.EXTRA_HOUR,    hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                }
            }
            context.startActivity(intent)
            val display = if (timeStr.isNotEmpty()) "saat $timeStr iÃ§in" else ""
            "â° Alarm $display kuruldu!"
        } catch (e: Exception) { "Alarm kurulamadÄ±: ${e.message}" }
    }

    // â”€â”€ ZamanlayÄ±cÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun setTimer(context: Context, seconds: Long): String {
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(AlarmClock.EXTRA_LENGTH,   seconds.toInt())
                    putExtra(AlarmClock.EXTRA_SKIP_UI,  true)
                    putExtra(AlarmClock.EXTRA_MESSAGE,  "ATLAS")
                }
            )
            val m = seconds / 60; val s = seconds % 60
            val display = when { m == 0L -> "$s saniye"; s == 0L -> "$m dakika"; else -> "$m dakika $s saniye" }
            "â± $display zamanlayÄ±cÄ± kuruldu!"
        } catch (_: Exception) { "ZamanlayÄ±cÄ± kurulamadÄ±." }
    }

    // â”€â”€ Telefon aramasÄ± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun makeCall(context: Context, contactName: String): String {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "âš ï¸ Rehber izni verilmemiÅŸ. LÃ¼tfen Ayarlar'dan Rehber iznini aÃ§."
        }
        val number = lookupContact(context, contactName)
            ?: return "âš ï¸ '$contactName' rehberde bulunamadÄ±. Rehberdeki tam adÄ±nÄ± sÃ¶yler misin?"

        // CALL_PHONE izni varsa direkt ara, yoksa dialer aÃ§
        return try {
            val uri = Uri.parse("tel:$number")
            if (context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                context.startActivity(
                    Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "ðŸ“ž $contactName aranÄ±yor..."
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "ðŸ“ž Arama ekranÄ± aÃ§Ä±ldÄ± â€” telefon izni verin direkt arasÄ±n."
            }
        } catch (_: Exception) { "Arama aÃ§Ä±lamadÄ±." }
    }

    // â”€â”€ WhatsApp â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private fun sendWhatsApp(context: Context, contactName: String, message: String): String {
        // Ä°zin kontrolÃ¼
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "⚠️ Rehber izni verilmemiş. Lütfen Ayarlar → Uygulamalar → ATLAS → İzinler'den Rehber iznini aç."
        }
        val number = lookupContact(context, contactName)
        if (number == null) {
            return "âš ï¸ '$contactName' rehberde bulunamadÄ±. " +
                   "Rehberdeki tam adÄ±nÄ± sÃ¶yler misin?"
        }
        return try {
            val clean   = number.replace(Regex("[^0-9+]"), "")
            val encoded = Uri.encode(message)
            val uri     = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=$encoded")
            // Accessibility servisi WhatsApp yÃ¼klenir yÃ¼klenmez gÃ¶nder butonuna basacak
            WhatsAppAutoSendService.shouldAutoSend = true
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val msgInfo = if (message.isNotEmpty()) ": \"$message\"" else ""
            "ðŸ’¬ $contactName'a gÃ¶nderiliyor$msgInfo"
        } catch (_: Exception) { "WhatsApp aÃ§Ä±lamadÄ±. YÃ¼klÃ¼ mÃ¼?" }
    }

    // â”€â”€ Rehber arama â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Case-insensitive + TÃ¼rkÃ§e iyelik eki soyma + fallback tam tara
    private fun lookupContact(context: Context, name: String): String? {
        if (name.isBlank()) return null

        // Ä°zin kontrolÃ¼
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS izni verilmemiÅŸ â€” rehber aranamÄ±yor")
            return null
        }

        val trLocale = Locale.forLanguageTag("tr-TR")
        val uri  = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val col  = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val nameLower = name.lowercase(trLocale)

        // TÃ¼rkÃ§e iyelik eki -m soyma:  babamâ†’baba, annemâ†’anne, abimâ†’abi
        val stem = if (nameLower.length > 2 && nameLower.endsWith("m")) nameLower.dropLast(1) else null
        val searchTerms = listOfNotNull(nameLower, stem)

        return try {
            // 1) SQL sorgularÄ± â€” hÄ±zlÄ±
            for (term in searchTerms) {
                // Tam eÅŸleÅŸme: orijinal, Title Case, UPPER
                for (variant in listOf(term, term.replaceFirstChar { it.uppercase(trLocale) }, term.uppercase(trLocale))) {
                    val c = context.contentResolver.query(uri, proj, "$col = ?", arrayOf(variant), null)
                    c?.use {
                        if (it.moveToFirst())
                            return it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    }
                }
                // LIKE kÄ±smi eÅŸleÅŸme (SQLite LIKE ASCII case-insensitive)
                val c2 = context.contentResolver.query(uri, proj, "$col LIKE ?", arrayOf("%$term%"), null)
                c2?.use {
                    if (it.moveToFirst())
                        return it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                }
            }

            // 2) Fallback: tÃ¼m rehberi tara â€” TÃ¼rkÃ§e locale ile lowercase karÅŸÄ±laÅŸtÄ±r
            val cursor = context.contentResolver.query(uri, proj, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val dn = it.getString(nameIdx)?.lowercase(trLocale) ?: continue
                    for (term in searchTerms) {
                        if (dn == term || dn.contains(term)) {
                            Log.d(TAG, "Rehber fallback eÅŸleÅŸti: '$name' â†’ '$dn'")
                            return it.getString(numIdx)
                        }
                    }
                }
            }

            Log.w(TAG, "Rehber: '$name' bulunamadÄ± (aranan: $searchTerms)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Rehber hatasÄ±: ${e.message}")
            null
        }
    }

    // â”€â”€ YardÄ±mcÄ±lar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** "sabah 7:30", "akÅŸam 10", "7:30" gibi stringleri ayrÄ±ÅŸtÄ±rÄ±r */
    private fun parseAlarmTime(text: String): Pair<String, String> {
        val timeMatch = Regex("""(\d{1,2})(?:[:.:](\d{2}))?""").find(text)
        val timeStr = if (timeMatch != null) {
            val h = timeMatch.groupValues[1]
            val m = timeMatch.groupValues[2].ifEmpty { "00" }
            "$h:$m"
        } else ""
        val period = when {
            Regex("""akÅŸam|gece|pm""").containsMatchIn(text) -> "pm"
            Regex("""sabah|Ã¶ÄŸleden\s*Ã¶nce|am""").containsMatchIn(text) -> "am"
            else -> ""
        }
        return Pair(timeStr, period)
    }

    /** "10 dakika 30 saniye", "1 saat" â†’ saniye sayÄ±sÄ± */
    fun parseDuration(text: String): Long {
        var total = 0L
        Regex("""(\d+(?:[.,]\d+)?)\s*(saat|sa|dakika|dk|dak|saniye|sn)""")
            .findAll(text).forEach {
                val num = it.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                total += when (it.groupValues[2]) {
                    "saat", "sa"          -> (num * 3600).toLong()
                    "dakika", "dk", "dak" -> (num * 60).toLong()
                    "saniye", "sn"        -> num.toLong()
                    else                  -> 0L
                }
            }
        if (total == 0L) {
            total = Regex("""(\d+)""").find(text)?.groupValues?.get(1)
                ?.toLongOrNull()?.times(60) ?: 300L
        }
        return total
    }
}

