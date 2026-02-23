package com.example.asistant.services

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * WhatsApp mesajlarını otomatik gönderir.
 *
 * Akış:
 * 1. PhoneActionsHandler.sendWhatsApp() → shouldAutoSend = true
 * 2. WhatsApp deep-link ile açılır, mesaj metin kutusuna yazılır
 * 3. Bu servis WhatsApp ekranı değişimini algılar
 * 4. Gönder butonunu bulur ve tıklar
 *
 * Kullanıcının Erişilebilirlik ayarlarından bu servisi etkinleştirmesi gerekir.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    companion object {
        private const val TAG = "WASendService"
        @Volatile var shouldAutoSend = false

        private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        // WhatsApp sürümüne göre değişen send button ID'leri
        private val SEND_BUTTON_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/conversation_send_btn",
        )
        private val SEND_BUTTON_IDS_W4B = listOf(
            "com.whatsapp.w4b:id/send",
            "com.whatsapp.w4b:id/send_btn",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 8

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!shouldAutoSend) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in WA_PACKAGES) return

        // Ekran değişimi algılandı — send butonunu ara
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            retryCount = 0
            handler.removeCallbacksAndMessages(null)
            attemptSend(pkg)
        }
    }

    private fun attemptSend(pkg: String) {
        if (!shouldAutoSend || retryCount >= maxRetries) {
            if (retryCount >= maxRetries) {
                Log.w(TAG, "Send butonu $maxRetries denemede bulunamadı")
                shouldAutoSend = false
            }
            return
        }

        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != pkg) {
            retryCount++
            handler.postDelayed({ attemptSend(pkg) }, 300)
            return
        }

        val ids = if (pkg == "com.whatsapp.w4b") SEND_BUTTON_IDS_W4B else SEND_BUTTON_IDS

        // 1) View ID ile ara
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val btn = nodes.firstOrNull { it.isEnabled && it.isClickable }
            if (btn != null) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Send tıklandı (id: $id), retry: $retryCount")
                shouldAutoSend = false
                retryCount = 0
                return
            }
        }

        // 2) Content description ile ara ("Send", "Gönder")
        val byDesc = findNodeByDesc(root, listOf("Send", "Gönder", "send"))
        if (byDesc != null) {
            byDesc.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Send tıklandı (contentDesc), retry: $retryCount")
            shouldAutoSend = false
            retryCount = 0
            return
        }

        // 3) Bulunamadı — tekrar dene
        retryCount++
        handler.postDelayed({ attemptSend(pkg) }, 300)
    }

    private fun findNodeByDesc(root: AccessibilityNodeInfo, descs: List<String>): AccessibilityNodeInfo? {
        for (desc in descs) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            val btn = nodes.firstOrNull { it.isEnabled && it.isClickable }
            if (btn != null) return btn
        }
        // DFS
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val cd = child.contentDescription?.toString() ?: ""
            if (descs.any { cd.equals(it, ignoreCase = true) } && child.isEnabled && child.isClickable) {
                return child
            }
            val found = findNodeByDesc(child, descs)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        shouldAutoSend = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
