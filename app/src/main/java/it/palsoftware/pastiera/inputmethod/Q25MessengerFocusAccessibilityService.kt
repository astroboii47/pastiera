package it.palsoftware.pastiera.inputmethod

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import it.palsoftware.pastiera.SettingsManager

class Q25MessengerFocusAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRefocus: Runnable? = null
    private var lastRefocusAtMs: Long = 0L
    private var armedUntilMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        Log.d(TAG, "Messenger focus helper connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != MESSENGER_PACKAGE) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (!SettingsManager.getQ25MessengerFocusFixEnabled(this)) return
            if (isComposerClick(event.source)) {
                armedUntilMs = System.currentTimeMillis() + ARMED_WINDOW_MS
                Log.d(TAG, "Armed Messenger composer refocus")
                scheduleRefocus()
            }
            return
        }

        if (!SettingsManager.getQ25MessengerFocusFixEnabled(this)) return
        if (System.currentTimeMillis() > armedUntilMs) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> scheduleRefocus()
        }
    }

    override fun onInterrupt() {
        pendingRefocus?.let { handler.removeCallbacks(it) }
        pendingRefocus = null
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && SettingsManager.getSymAsCtrlInTextFields(this) && isTitanSymKey(event)) {
            symAsCtrlPhysicalHoldActive = event.action == KeyEvent.ACTION_DOWN
            PhysicalKeyboardInputMethodService.setAccessibilitySymAsCtrlHoldState(symAsCtrlPhysicalHoldActive)
        }
        return false
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        symAsCtrlPhysicalHoldActive = false
        super.onDestroy()
    }

    private fun scheduleRefocus() {
        val now = System.currentTimeMillis()
        if (now - lastRefocusAtMs < REFOCUS_COOLDOWN_MS) return
        pendingRefocus?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingRefocus = null
            if (refocusComposerIfNeeded()) {
                return@Runnable
            }
            handler.postDelayed({ refocusComposerIfNeeded() }, REFOCUS_RETRY_DELAY_MS)
        }
        pendingRefocus = runnable
        Log.d(TAG, "Scheduling Messenger composer refocus")
        handler.postDelayed(runnable, REFOCUS_DELAY_MS)
    }

    private fun refocusComposerIfNeeded(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != MESSENGER_PACKAGE) return false
        val composer = findMessengerComposer(root) ?: return false
        if (composer.isFocused) {
            Log.d(TAG, "Composer already focused")
            return true
        }

        val clicked = composer.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            composer.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (clicked) {
            lastRefocusAtMs = System.currentTimeMillis()
            Log.d(TAG, "Clicked Messenger composer to restore focus")
        } else {
            Log.d(TAG, "Failed to click Messenger composer")
        }
        return clicked
    }

    private fun isComposerClick(source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        if (isComposerNode(source)) return true

        val root = rootInActiveWindow ?: return false
        val composer = findMessengerComposer(root) ?: return false
        val sourceBounds = Rect()
        val composerBounds = Rect()
        source.getBoundsInScreen(sourceBounds)
        composer.getBoundsInScreen(composerBounds)
        return Rect.intersects(sourceBounds, composerBounds)
    }

    private fun findMessengerComposer(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isComposerNode(node)) return node

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findMessengerComposer(child)
            if (match != null) return match
        }
        return null
    }

    private fun isComposerNode(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() != "android.widget.EditText") return false
        val description = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        return description.equals("Type a message", ignoreCase = true) ||
            text.equals("Message", ignoreCase = true)
    }

    companion object {
        private const val TAG = "Q25MessengerFocus"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"
        private const val REFOCUS_DELAY_MS = 90L
        private const val REFOCUS_RETRY_DELAY_MS = 140L
        private const val REFOCUS_COOLDOWN_MS = 450L
        private const val ARMED_WINDOW_MS = 1_200L
        private const val SCANCODE_TITAN2_SYM = 253

        @Volatile
        private var activeService: Q25MessengerFocusAccessibilityService? = null

        @Volatile
        private var symAsCtrlPhysicalHoldActive: Boolean = false

        private fun isTitanSymKey(event: KeyEvent): Boolean {
            return event.scanCode == SCANCODE_TITAN2_SYM ||
                event.keyCode == KeyEvent.KEYCODE_SYM
        }

        fun isSymAsCtrlPhysicalHoldActive(): Boolean = symAsCtrlPhysicalHoldActive

        fun requestMessengerRefocus(context: Context, packageName: String?) {
            if (packageName != MESSENGER_PACKAGE) return
            if (!SettingsManager.getQ25MessengerFocusFixEnabled(context)) return
            val service = activeService
            if (service == null) {
                Log.d(TAG, "Refocus requested but accessibility helper is not connected")
                return
            }
            service.armedUntilMs = System.currentTimeMillis() + ARMED_WINDOW_MS
            service.scheduleRefocus()
        }
    }
}
