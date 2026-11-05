package dev.mathgate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.accessibility.AccessibilityManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun action(value: String, onClick: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(118, 60, 231))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

    private fun accessibilityGranted(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == GateService::class.java.name }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(45), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(19, 14, 48), Color.rgb(74, 32, 113), Color.rgb(19, 14, 48)))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)
        val white = Color.WHITE
        val mint = Color.rgb(123, 255, 205)
        root.addView(text("✦ MATHGATE", 18f, mint, true))
        root.addView(text("Earn your scroll.", 35f, white, true))
        root.addView(text("Instagram time is earned with real calculus, not another dismissible reminder.", 15f, Color.rgb(215, 204, 237)))

        val minutes = GateState.usedMs(this) / 60_000L
        val allowed = GateState.allowedMs(this) / 60_000L
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(Color.rgb(50, 35, 92), 24)
        }
        val margin = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(25); bottomMargin = dp(16)
        }
        root.addView(status, margin)
        status.addView(text("TODAY'S INSTAGRAM USAGE", 13f, mint, true))
        status.addView(text("${minutes} / ${allowed} min", 30f, white, true))
        status.addView(text("${GateState.solved(this)} / 2 extra sessions earned", 15f, white))
        root.addView(text("ACCESS SETUP", 18f, white, true))

        val usageOk = GateState.usageGranted(this)
        val accessibilityOk = accessibilityGranted()
        root.addView(text(if (usageOk) "✓ Usage access enabled" else "○ Usage access required", 15f,
            if (usageOk) mint else Color.rgb(255, 200, 140)))
        root.addView(action("1 · Grant usage access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(text(if (accessibilityOk) "✓ Blocking service enabled" else "○ Blocking service required", 15f,
            if (accessibilityOk) mint else Color.rgb(255, 200, 140)))
        root.addView(action("2 · Enable MathGate accessibility service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(text("THE RULES", 18f, white, true))
        root.addView(text("15 minutes free per day. Then solve one Analysis I/II challenge for 5 more minutes. Two extensions maximum (25 minutes total). Answer with a decimal approximation; a small numerical tolerance is accepted.", 15f, Color.rgb(232, 218, 250)))
        root.addView(text("Security note: Android accessibility and usage permissions can be disabled in Settings; this is a personal friction tool, not an unbreakable device policy.", 13f, Color.rgb(255, 210, 184)))
        root.addView(action("Refresh today's usage") { render() })
    }
}
