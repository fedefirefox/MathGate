package dev.mathgate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout

/**
 * Samsung/Android sideloaded-APK onboarding.
 *
 * IMPORTANT: One UI may hide "Allow restricted settings" until the user
 * FIRST opens the blocked Accessibility service and dismisses its warning.
 * The real sequence is Accessibility -> App info -> Accessibility again.
 *
 * Android exposes actual Usage Access and Accessibility permission state.
 * It does NOT expose a read-only API for "Allow restricted settings".
 * Visited screens are guidance milestones, never treated as proof of access.
 */
class SetupActivity : Activity() {
    private val steps by lazy {
        getSharedPreferences("mathgate_setup_navigation", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun accessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == GateService::class.java.name
        }
    }

    private fun openAccessibility(firstAttempt: Boolean) {
        if (firstAttempt) {
            steps.edit().putBoolean("tried_accessibility_first", true).apply()
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        steps.edit().putBoolean("opened_app_info", true).apply()
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun addText(parent: LinearLayout, content: String, emphatic: Boolean = false) {
        GateUi.add(parent, GateUi.text(
            this, content,
            if (emphatic) 15f else 14f,
            if (emphatic) GateUi.INK else GateUi.MUTED,
            emphatic
        ), bottom = 9)
    }

    private fun step(title: String): LinearLayout = GateUi.panel(this).also { box ->
        GateUi.add(box, GateUi.text(this, title, 14f, GateUi.TEAL, true), bottom = 6)
    }

    private fun render() {
        val usage = GateState.hasUsageAccess(this)
        val guard = accessibilityEnabled()
        val ready = usage && guard
        val triedFirst = steps.getBoolean("tried_accessibility_first", false)
        val visitedInfo = steps.getBoolean("opened_app_info", false)

        val root = GateUi.root(this)
        setContentView(GateUi.scroll(this, root))
        GateUi.add(root, GateUi.text(this,
            "●  MATHGATE  /  SAMSUNG SETUP",
            13f, GateUi.TEAL, true), bottom = 12)
        GateUi.add(root, GateUi.text(this,
            if (ready) "INSTAGRAM GUARD\nREADY." else "SET UP YOUR\nINSTAGRAM GUARD.",
            30f, GateUi.INK, true), bottom = 6)
        addText(root,
            "Follow the Android screens in order. On some Samsung devices, the first " +
            "blocked Accessibility attempt is needed before the three-dot menu appears.")

        val summary = GateUi.panel(this, true)
        GateUi.add(summary, GateUi.text(this,
            if (ready) "✓ PROTECTION READY" else "REQUIRED PERMISSIONS",
            13f, GateUi.LIME, true), bottom = 9)
        GateUi.add(summary, GateUi.progress(this,
            (if (usage) 0.5f else 0f) + (if (guard) 0.5f else 0f)), bottom = 8)
        GateUi.add(summary, GateUi.text(this,
            (if (usage) "✓ " else "○ ") + "Usage Access\n" +
            (if (guard) "✓ " else "○ ") + "MathGate Accessibility Guard",
            15f, GateUi.WHITE))
        GateUi.add(root, summary, bottom = 15)

        val first = step("01  ·  GRANT USAGE ACCESS" + if (usage) "  ✓" else "")
        addText(first, if (usage) "Permission verified."
            else "Open Usage Access, find MathGate and enable Permit usage access.")
        GateUi.add(first, GateUi.button(this,
            if (usage) "✓ Usage Access enabled" else "Open Usage Access",
            if (usage) GateUi.SOFT else GateUi.TEAL,
            if (usage) GateUi.INK else GateUi.WHITE
        ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        GateUi.add(root, first, bottom = 12)

        // Visit Accessibility FIRST to trigger the restrictive-permission dialog.
        val second = step("02  ·  ACCESSIBILITY — FIRST VISIT" +
            if (guard) "  ✓" else "")
        addText(second, if (guard) "Accessibility is already enabled. Steps 3–4 aren't needed."
        else "Open Accessibility → Installed apps → MathGate Instagram Guard. " +
            "Tap the disabled entry or try turning it ON. When Samsung says " +
            "'App was denied access', tap Close/OK and return to MathGate.")
        if (!guard) {
            GateUi.add(second, GateUi.button(this,
                if (triedFirst) "Retry initial Accessibility visit"
                else "Try Accessibility first",
                GateUi.TEAL, GateUi.WHITE
            ) { openAccessibility(firstAttempt = true) })
            addText(second, if (triedFirst)
                "Accessibility page opened. This records a visit, not proof you tapped the blocked switch."
            else "Do this before App info: otherwise the three-dot menu may be missing.")
        }
        GateUi.add(root, second, bottom = 12)

        val third = step("03  ·  APP INFO — ALLOW RESTRICTED SETTINGS")
        if (guard) {
            addText(third, "Already allowed; your guard is active.")
        } else {
            addText(third,
                "After dismissing the blocked-access warning, open " +
                "MathGate App info → ⋮ (top-right) → Allow restricted settings. " +
                "Confirm with fingerprint or PIN, then return here.")
            if (triedFirst) {
                GateUi.add(third, GateUi.button(this,
                    "Open MathGate App info",
                    GateUi.INK, GateUi.WHITE
                ) { openAppInfo() })
                addText(third,
                    "If the ⋮ menu isn't there, return to step 2 and actually tap the blocked service first.")
            } else {
                addText(third, "First complete step 2 to reveal this Samsung setting.")
            }
        }
        GateUi.add(root, third, bottom = 12)

        val fourth = step("04  ·  ACCESSIBILITY — RETURN AND ENABLE" +
            if (guard) "  ✓" else "")
        if (guard) {
            addText(fourth, "Permission verified: MathGate Instagram Guard is ON.")
        } else if (visitedInfo) {
            addText(fourth,
                "Now return to Accessibility → Installed apps → MathGate Instagram Guard " +
                "and switch it ON. Accept Android's confirmation, then return to MathGate.")
            GateUi.add(fourth, GateUi.button(this,
                "Return to Accessibility and enable",
                GateUi.TEAL, GateUi.WHITE
            ) { openAccessibility(firstAttempt = false) })
            addText(fourth,
                "Still denied? Go back to step 3 and check that you confirmed Allow restricted settings.")
        } else {
            addText(fourth,
                "After step 3, return here and use the button to enable the guard. " +
                "This final permission is checked automatically.")
        }
        GateUi.add(root, fourth, bottom = 15)

        if (ready) {
            GateUi.add(root, GateUi.button(this,
                "✓ FINISH SETUP — OPEN MATHGATE",
                GateUi.LIME, GateUi.INK
            ) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, bottom = 13)
        } else {
            addText(root,
                "The first Accessibility visit and App info visit are navigation steps; " +
                "MathGate reports success only when Usage Access and Accessibility are really ON.")
        }

        GateUi.add(root, GateUi.text(this,
            "Accessibility is sensitive. Only grant it to an app you trust. " +
            "Android Settings can always revoke these permissions.",
            12f, GateUi.MUTED))
    }
}
