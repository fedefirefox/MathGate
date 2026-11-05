package dev.mathgate

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GateService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundInstagram = false
    private var overlay: View? = null
    private var checking = false
    private lateinit var wm: WindowManager

    private val tick = object : Runnable {
        override fun run() {
            if (foregroundInstagram) refreshGate()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == "com.android.systemui") return
            foregroundInstagram = pkg == GateState.INSTAGRAM
            if (foregroundInstagram) refreshGate() else dismiss()
        }
    }

    private fun refreshGate() {
        if (!GateState.usageGranted(this)) { dismiss(); return }
        val over = GateState.usedMs(this) >= GateState.allowedMs(this)
        if (over) showGate() else dismiss()
    }

    private fun background() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(32, 16, 78), Color.rgb(105, 42, 151), Color.rgb(27, 11, 57))
    )

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(0, 12, 0, 12)
    }

    private fun showGate() {
        if (overlay != null) return
        val problem = GateState.currentProblem(this)
        val mint = Color.rgb(118, 255, 203)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 65, 42, 35)
            background = background()
        }
        panel.addView(label("✦ MATHGATE", 18f, mint, true))
        panel.addView(label(
            if (GateState.solved(this) >= GateState.MAX_EXTENSIONS)
                "Daily limit reached" else "Unlock your next 5 minutes", 29f, Color.WHITE, true
        ))
        panel.addView(label(
            if (GateState.solved(this) >= GateState.MAX_EXTENSIONS)
                "You've used your full Instagram allowance for today. Come back tomorrow."
            else "One serious problem. One numeric answer. No multiple choice.",
            16f, Color.rgb(236, 218, 255)
        ))
        if (GateState.solved(this) < GateState.MAX_EXTENSIONS) {
            panel.addView(label(problem.title.uppercase(), 14f, mint, true))
            panel.addView(label(problem.prompt, 19f, Color.WHITE))
            val entry = EditText(this).apply {
                hint = "Your decimal answer (e.g. 1.234)"
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
            }
            panel.addView(entry)
            val feedback = label("Answers are checked locally.", 14f, Color.rgb(236, 218, 255))
            panel.addView(feedback)
            panel.addView(Button(this).apply {
                text = "Check answer →"
                isAllCaps = false
                setOnClickListener {
                    if (GateState.correct(entry.text.toString(), problem)) {
                        GateState.grant(this@GateService)
                        dismiss()
                    } else {
                        feedback.text = "Not quite. Try again. Hint: ${problem.hint}"
                        feedback.setTextColor(Color.rgb(255, 190, 172))
                    }
                }
            })
        }
        panel.addView(Button(this).apply {
            text = "Leave Instagram"
            isAllCaps = false
            setOnClickListener {
                dismiss()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        })
        val scroll = ScrollView(this).apply { fillViewport = true; addView(panel) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        try {
            wm.addView(scroll, params)
            overlay = scroll
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun dismiss() {
        overlay?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlay = null
        }
    }

    override fun onInterrupt() { dismiss() }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        dismiss()
        super.onDestroy()
    }
}
