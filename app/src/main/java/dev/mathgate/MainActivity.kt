package dev.mathgate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun hasAccessibility(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == GateService::class.java.name
            }
    }

    private fun render() {
        val page = GateUi.root(this)
        setContentView(GateUi.scroll(this, page))
        val access = GateState.hasUsageAccess(this)
        val guard = hasAccessibility()
        val used = GateState.usedMs(this)
        val quota = GateState.allowanceMs(this)
        val earned = GateState.bonusCount(this)

        GateUi.add(page, GateUi.text(this, "●  MATHGATE     /     INSTAGRAM", 14f, GateUi.TEAL, true), bottom = 18)
        GateUi.add(page, GateUi.text(this, "LESS SCROLL.\nMORE SOLVING.", 33f, GateUi.INK, true))
        GateUi.add(page, GateUi.text(this,
            "20 minutes included. Each solved calculus challenge earns another 5 minutes.",
            15f, GateUi.MUTED), bottom = 22)

        val summary = GateUi.panel(this, true)
        GateUi.add(summary, GateUi.text(this, "TODAY'S INSTAGRAM TIME", 12f, GateUi.LIME, true))
        GateUi.add(summary, GateUi.text(this,
            if (used < 0) "Permission needed" else
                "${GateState.timeLabel(used)} / ${GateState.timeLabel(quota)}",
            if (used < 0) 22f else 30f, GateUi.WHITE, true), top = 10, bottom = 10)
        GateUi.add(summary, GateUi.progress(this,
            if (used >= 0) used.toFloat() / quota else 0f), bottom = 10)
        GateUi.add(summary, GateUi.text(this,
            if (!access) "Grant Usage Access to read system time"
            else "${GateState.timeLabel(GateState.remainingMs(this))} remaining • ${earned} challenges solved",
            14f, GateUi.WHITE))
        GateUi.add(page, summary, bottom = 22)

        val active = access && guard
        val state = GateUi.panel(this)
        GateUi.add(state, GateUi.text(this,
            if (active) "●  LOCK ACTIVE" else "●  SETUP INCOMPLETE",
            15f, if (active) GateUi.TEAL else GateUi.CORAL, true))
        GateUi.add(state, GateUi.text(this,
            if (active) "Instagram is interrupted when today's allowance is exhausted."
            else "Both Android permissions are necessary for reliable interception.",
            14f, GateUi.MUTED))
        GateUi.add(page, state, bottom = 18)

        GateUi.add(page, GateUi.button(this,
            if (access) "✓  System Usage Access granted" else "1 · Grant system Usage Access",
            if (access) GateUi.SOFT else GateUi.INK,
            if (access) GateUi.INK else GateUi.WHITE) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }, bottom = 8)
        GateUi.add(page, GateUi.button(this,
            if (guard) "✓  Instagram guard enabled" else "2 · Enable Instagram guard",
            if (guard) GateUi.SOFT else GateUi.TEAL,
            if (guard) GateUi.INK else GateUi.WHITE) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, bottom = 22)

        GateUi.add(page, GateUi.text(this, "THE RULES", 13f, GateUi.TEAL, true), bottom = 5)
        GateUi.add(page, GateUi.text(this,
            "01   20 minutes of Instagram free each day.\n" +
            "02   At the limit, MathGate opens a full-screen equation.\n" +
            "03   Correct numeric answer = +5 minutes.\n" +
            "04   Repeat when the earned time runs out.",
            15f, GateUi.INK), bottom = 16)
        GateUi.add(page, GateUi.text(this,
            "There is no in-app pause/disable switch. Android Settings can still revoke permissions, and uninstalling removes the guard. System usage events are used; Samsung Digital Wellbeing totals can differ slightly.",
            12f, GateUi.MUTED), bottom = 8)
        GateUi.add(page, GateUi.button(this, "Refresh usage", GateUi.SOFT, GateUi.INK) { render() })
    }
}
