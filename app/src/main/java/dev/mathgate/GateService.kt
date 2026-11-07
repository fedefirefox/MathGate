package dev.mathgate

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Instagram blocking is enforced by an ACCESSIBILITY OVERLAY, NOT a background
 * Activity launch. Android may silently prevent startActivity() from a service,
 * whereas TYPE_ACCESSIBILITY_OVERLAY can intercept touches above Instagram.
 *
 * The user taps an overlay button to open the challenge from a user gesture.
 * When a screen cannot be displayed, go HOME rather than silently allowing IG.
 */
class GateService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var instagramForeground = false
    private var lastTestMode: Boolean? = null
    private var lastUsageMissing: Boolean? = null

    private val poller = object : Runnable {
        override fun run() {
            try {
                GateDiagnostics.heartbeat(this@GateService)
                val foreground = GateState.foregroundPackage(this@GateService)
                if (foreground == GateState.INSTAGRAM) {
                    instagramForeground = true
                    GateDiagnostics.instagramDetected(this@GateService)
                } else if (foreground != null) {
                    instagramForeground = false
                }
                enforceOrDismiss()
            } catch (error: Exception) {
                GateDiagnostics.recordError(this@GateService,
                    "Guard poll: " + (error.message ?: "unknown error"))
            } finally {
                handler.postDelayed(this, 1_500L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        GateDiagnostics.connected(this)
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val packageNameObserved = event.packageName?.toString() ?: return

        when (packageNameObserved) {
            GateState.INSTAGRAM -> {
                instagramForeground = true
                GateDiagnostics.instagramDetected(this)
                enforceOrDismiss()
            }
            packageName -> {
                // An accessibility overlay can report this package too. Only an
                // ACTUAL MathGate Activity should clear Instagram foreground.
                val cls = event.className?.toString().orEmpty()
                if (cls.contains("MainActivity") ||
                    cls.contains("ChallengeActivity") ||
                    cls.contains("RewardActivity") ||
                    cls.contains("SetupActivity")) {
                    instagramForeground = false
                    hideOverlay()
                }
            }
            else -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    !isTransientSystemWindow(packageNameObserved, event.className?.toString())) {
                    instagramForeground = false
                    hideOverlay()
                }
            }
        }
    }

    private fun isTransientSystemWindow(pkg: String, klass: String?): Boolean =
        pkg == "com.android.systemui" ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("honeyboard", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true) ||
            klass.orEmpty().contains("InputMethod", ignoreCase = true) ||
            klass.orEmpty().contains("SoftInputWindow", ignoreCase = true)

    private fun enforceOrDismiss() {
        if (!instagramForeground) {
            hideOverlay()
            return
        }
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) {
            hideOverlay()
            return
        }
        val isTest = GateDiagnostics.isLockTestActive(this)
        val usageMissing = !GateState.hasUsageAccess(this)
        val used = GateState.usedMs(this)
        val overLimit = GuardRules.shouldBlock(
            instagramForeground = instagramForeground,
            screenLocked = false,
            usageGranted = !usageMissing,
            usedMs = used,
            allowanceMs = GateState.allowanceMs(this),
            testActive = isTest
        )
        if (!overLimit) {
            hideOverlay()
            return
        }
        if (overlay != null &&
            lastTestMode == isTest &&
            lastUsageMissing == usageMissing) return
        hideOverlay()
        showOverlay(isTest, usageMissing)
    }

    private fun showOverlay(test: Boolean, usageMissing: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(GateUi.INK)
            setPadding(dp(28), dp(40), dp(28), dp(40))
            isClickable = true
            isFocusable = true
        }

        fun addLabel(s: String, size: Float, color: Int) {
            val view = TextView(this).apply {
                text = s
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }
            layout.addView(view, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        fun addAction(label: String, onClick: () -> Unit) {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 18f
                setTextColor(GateUi.INK)
                background = GateUi.shape(this@GateService, GateUi.LIME, 17)
                setOnClickListener { onClick() }
            }
            layout.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(65)
            ).apply { topMargin = dp(20) })
        }

        addLabel("●  MATHGATE / INSTAGRAM", 17f, GateUi.LIME)
        addLabel(if (test) "LOCK TEST PASSED"
                 else if (usageMissing) "USAGE ACCESS REQUIRED"
                 else "20 MINUTES USED.\nEARN +05:00.",
            31f, Color.WHITE)

        addLabel(if (test)
            "The accessibility overlay is blocking Instagram. Your real daily allowance has not changed."
        else if (usageMissing)
            "MathGate cannot read actual Instagram usage. Restore Usage Access before using Instagram."
        else
            "Solve one Analysis I/II problem to unlock another five minutes. Instagram remains blocked until then.",
            16f, Color.WHITE)

        addAction(if (test) "Return to MathGate"
                  else if (usageMissing) "Fix MathGate permissions"
                  else "Solve a math problem →") {
            if (test) GateDiagnostics.endLockTest(this)
            val target = when {
                test -> MainActivity::class.java
                usageMissing -> SetupActivity::class.java
                else -> ChallengeActivity::class.java
            }
            try {
                startActivity(Intent(this, target).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
                // Keep the overlay until the actual app-foreground event arrives.
            } catch (error: Exception) {
                GateDiagnostics.recordError(this,
                    "Couldn't open challenge: " + (error.message ?: "blocked"))
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        val leave = Button(this).apply {
            text = "Leave Instagram"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                if (test) GateDiagnostics.endLockTest(this@GateService)
                hideOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        layout.addView(leave, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(10) })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP
        }

        try {
            windowManager.addView(layout, params)
            overlay = layout
            lastTestMode = test
            lastUsageMissing = usageMissing
            GateDiagnostics.recordOverlay(this)
        } catch (error: Exception) {
            overlay = null
            GateDiagnostics.recordError(this,
                "Accessibility overlay failed: " + (error.message ?: "unknown"))
            // Fail closed: interrupt Instagram, rather than only logging error.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun hideOverlay() {
        val current = overlay
        overlay = null
        lastTestMode = null
        lastUsageMissing = null
        if (current != null) {
            try { windowManager.removeView(current) }
            catch (error: Exception) {
                GateDiagnostics.recordError(this,
                    "Overlay removal failed: " + (error.message ?: "unknown"))
            }
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        hideOverlay()
        GateDiagnostics.recordError(this, "Accessibility service disconnected")
        super.onDestroy()
    }
}
