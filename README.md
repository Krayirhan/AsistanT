# AsistanT — Android Mobil Uygulama

AI Sesli Asistan'ın Android uygulaması. PC'deki backend'e bağlanarak sesli sohbet, akıllı ampul kontrolü ve TV yönetimi sağlar.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)](https://developer.android.com/jetpack/compose)

---

## Özellikler

- 💬 **AI Sohbet** — Türkçe sohbet, Markdown destekli mesajlar
- 🎙️ **Canlı Konuşma** — Sesli sohbet modu (STT → AI → TTS döngüsü)
- 💡 **Akıllı Ampul** — Tapo ampul kontrolü (aç/kapat, parlaklık, renk)
- 📺 **TV Kontrol** — LG Smart TV (ses, kanal, açma/kapama)
- ⚙️ **Ayarlar** — Backend IP/port yapılandırması, bağlantı testi

## Ekran Görüntüleri

| Ana Ekran | Sohbet | Canlı Konuşma |
|-----------|--------|---------------|
| 2x2 kart grid | Markdown mesajlar | Sesli döngü |

---

## Kurulum

### Gereksinimler
- Android Studio (Ladybug+)
- Android 7.0+ (API 24) cihaz
- PC'de [AI Asistan Backend](https://github.com/Krayirhan/Asistan) çalışıyor olmalı

### 1. Klonla & Aç

```bash
git clone https://github.com/Krayirhan/AsistanT.git
```

Android Studio'da **File → Open** → klasörü seç.

### 2. APK Oluştur

Android Studio'da **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Veya terminal:
```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Telefona Kur & Ayarla

1. APK'yı telefona yükle
2. Uygulama → **Ayarlar** (⚙️)
3. IP: `PC'nin LAN IP adresi` (ör: `192.168.1.100`)
4. Port: `8766`
5. **Test Et** → Bağlantı başarılı ✅ → **Kaydet**

---

## Teknik Detaylar

| Bileşen | Teknoloji |
|---------|-----------|
| UI | Jetpack Compose + Material3 |
| HTTP | Retrofit + OkHttp (120s timeout) |
| Mimari | ViewModel + StateFlow |
| TTS | Android TextToSpeech (Türkçe) |
| STT | Android SpeechRecognizer |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

## Proje Yapısı

```
app/src/main/java/com/example/asistant/
├── MainActivity.kt          → NavHost, yönlendirme
├── providers/
│   └── AssistantViewModel.kt → Tüm API çağrıları, state yönetimi
├── screens/
│   ├── HomeScreen.kt         → Ana ekran (2x2 grid + sesli konuşma kartı)
│   ├── ChatScreen.kt         → AI sohbet ekranı
│   ├── VoiceScreen.kt        → Canlı sesli konuşma modu
│   ├── LightScreen.kt        → Akıllı ampul kontrolü
│   ├── TVScreen.kt           → TV kontrolü
│   └── SettingsScreen.kt     → Backend bağlantı ayarları
├── network/
│   └── ApiService.kt         → Retrofit API tanımları
└── components/
    ├── ChatBubble.kt         → Markdown mesaj baloncuğu
    └── ColorPickerGrid.kt    → Ampul renk seçici
```

## Backend

Bu uygulama tek başına çalışmaz — PC'deki AI backend'e ihtiyaç duyar:  
👉 [Krayirhan/Asistan](https://github.com/Krayirhan/Asistan)

Backend'i başlatmak için PC'de:
```powershell
.\baslat.ps1
```
