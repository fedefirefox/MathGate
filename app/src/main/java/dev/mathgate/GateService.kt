package dev.mathgate

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Watches foreground app transitions, without reading screen content.
 * When allowance is used, launches MathGate in front of Instagram so the user's
 * time spent solving math is NOT billed as Instagram foreground time.
 */
class GateService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var instagramForeground = false
    private var launchingGate = false

    private val poller = object : Runnable {
        override fun run() {
            if (instagramForeground) enforceLimit()
            handler.postDelayed(this, 1_500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == GateState.INSTAGRAM) {
            instagramForeground = true
            enforceLimit()
        } else if (pkg == packageName) {
            instagramForeground = false
            launchingGate = false
        } else {
            // Ignore transient keyboard/system-UI windows, but recognize switching apps.
            val klass = event.className?.toString().orEmpty()
            if (klass.contains("InputMethod", ignoreCase = true) ||
                klass.contains("SoftInputWindow", ignoreCase = true) ||
                pkg == "com.android.systemui") return
            instagramForeground = false
            launchingGate = false
        }
    }

    private fun enforceLimit() {
        if (launchingGate) return
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) return
        if (!GateState.needsChallenge(this)) return
        launchingGate = true
        instagramForeground = false
        try {
            startActivity(Intent(this, ChallengeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        } catch (_: Exception) {
            // Fail closed if this Android build prevents background activity launches.
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            handler.postDelayed({ launchingGate = false }, 1_000L)
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }
}
