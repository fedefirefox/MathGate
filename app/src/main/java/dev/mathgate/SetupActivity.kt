package dev.mathgate

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Guided permission setup for Android/Samsung. No app can toggle these permissions itself. */
class SetupActivity : Activity() {
    private var openedAppInfo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
        openedAppInfo = savedInstanceState?.getBoolean("openedAppInfo") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openedAppInfo", openedAppInfo)
        super.onSaveInstanceState(outState)
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

    private fun line(parent: android.widget.LinearLayout, message: String) {
        GateUi.add(parent, GateUi.text(this, message, 14f, GateUi.MUTED), bottom = 9)
    }

    private fun render() {
        val usage = GateState.hasUsageAccess(this)
        val guard = accessibilityEnabled()
        val ready = usage && guard
        val root = GateUi.root(this)
        setContentView(GateUi.scroll(this, root))
        GateUi.add(root, GateUi.text(this,
            "●  MATHGATE  /  FIRST-TIME SETUP", 13f, GateUi.TEAL, true), bottom = 12)
        GateUi.add(root, GateUi.text(this,
            if (ready) "INSTAGRAM GUARD\nREADY." else "ACTIVATE YOUR\nINSTAGRAM GUARD.",
            31f, GateUi.INK, true), bottom = 7)
        line(root, "Follow this one-time checklist. Return to MathGate after each Settings screen. We check the actual Android permissions automatically.")

        val summary = GateUi.panel(this, true)
        GateUi.add(summary, GateUi.text(this,
            if (ready) "✓  ALL REQUIRED PERMISSIONS ENABLED" else "PERMISSION CHECKLIST",
            13f, GateUi.LIME, true), bottom = 10)
        GateUi.add(summary, GateUi.progress(this,
            (if (usage) 0.5f else 0f) + (if (guard) 0.5f else 0f)), bottom = 10)
        GateUi.add(summary, GateUi.text(this,
            (if (usage) "✓ " else "○ ") + "System Usage Access\n" +
                (if (guard) "✓ " else "○ ") + "Accessibility Instagram Guard",
            15f, GateUi.WHITE))
        GateUi.add(root, summary, bottom = 15)

        val one = GateUi.panel(this)
        GateUi.add(one, GateUi.text(this,
            "01  ·  USAGE ACCESS " + (if (usage) "✓" else ""),
            15f, GateUi.TEAL, true), bottom = 6)
        line(one, if (usage) "Permission granted and verified."
            else "Tap below → find MathGate → switch Permit usage access ON → return here.")
        GateUi.add(one, GateUi.button(this,
            if (usage) "✓ Usage Access granted" else "Open Usage Access settings",
            if (usage) GateUi.SOFT else GateUi.TEAL,
            if (usage) GateUi.INK else GateUi.WHITE) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        GateUi.add(root, one, bottom = 13)

        val two = GateUi.panel(this)
        GateUi.add(two, GateUi.text(this, "02  ·  UNLOCK SAMSUNG RESTRICTED SETTINGS",
            15f, GateUi.TEAL, true), bottom = 6)
        line(two, "On a sideloaded APK, Samsung first blocks the Accessibility switch. This must be allowed manually on MathGate's App info page:")
        GateUi.add(two, GateUi.text(this,
            "Settings → Apps → MathGate → ⋮ (top right) → Allow restricted settings → confirm with PIN/fingerprint.",
            15f, GateUi.INK, true), bottom = 7)
        line(two, "This option is NOT on the Accessibility page. Android doesn't provide an API to grant it or read its state directly.")
        GateUi.add(two, GateUi.button(this,
            "Open MathGate App info (step 2)", GateUi.INK, GateUi.WHITE) {
            openedAppInfo = true
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        })
        GateUi.add(root, two, bottom = 13)

        val three = GateUi.panel(this)
        GateUi.add(three, GateUi.text(this,
            "03  ·  ENABLE INSTAGRAM GUARD " + (if (guard) "✓" else ""),
            15f, GateUi.TEAL, true), bottom = 6)
        line(three, if (guard) "MathGate Instagram Guard is ON and verified."
        else "Open Accessibility → Installed apps → MathGate Instagram Guard → On. Accept the system warning, then return here.")
        GateUi.add(three, GateUi.button(this,
            if (guard) "✓ Accessibility enabled" else "Open Accessibility settings (step 3)",
            if (guard) GateUi.SOFT else GateUi.TEAL,
            if (guard) GateUi.INK else GateUi.WHITE) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        if (openedAppInfo && !guard) {
            GateUi.add(three, GateUi.text(this,
                "If Android says 'App was denied access,' go back to step 2 and select Allow restricted settings.",
                13f, GateUi.CORAL), top = 8)
        }
        GateUi.add(root, three, bottom = 14)

        if (ready) {
            GateUi.add(root, GateUi.button(this,
                "✓ FINISH SETUP — OPEN MATHGATE", GateUi.LIME, GateUi.INK) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, bottom = 14)
        } else {
            line(root, "Finish all required permissions before using the blocker. No skip or in-app disable option.")
        }
        GateUi.add(root, GateUi.text(this,
            "Accessibility is sensitive: MathGate monitors foreground package events, not messages or screen contents. Android always lets you revoke it in Settings.",
            12f, GateUi.MUTED))
    }
}
