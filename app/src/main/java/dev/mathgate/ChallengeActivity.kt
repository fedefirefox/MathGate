package dev.mathgate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText

/** Foreground activity pauses Instagram's usage counter while the user solves math. */
class ChallengeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
    }

    override fun onResume() {
        super.onResume()
        draw()
    }

    private fun draw() {
        val page = GateUi.root(this)
        setContentView(GateUi.scroll(this, page))
        GateUi.add(page, GateUi.text(this, "●  MATHGATE   /   ACCESS GATE", 13f, GateUi.TEAL, true), bottom = 14)
        GateUi.add(page, GateUi.text(this, "TIME'S UP.\nEARN +05:00.", 32f, GateUi.INK, true))
        val used = GateState.usedMs(this)
        val limit = GateState.allowanceMs(this)
        GateUi.add(page, GateUi.text(this,
            if (used >= 0) "Today: ${GateState.timeLabel(used)} of ${GateState.timeLabel(limit)} used"
            else "Usage permission required",
            15f, GateUi.MUTED), top = 6, bottom = 20)

        if (!GateState.hasUsageAccess(this)) {
            GateUi.add(page, GateUi.text(this,
                "MathGate cannot read your Instagram activity until Android Usage Access is enabled. Instagram stays locked.",
                16f, GateUi.CORAL), bottom = 12)
            GateUi.add(page, GateUi.button(this, "Grant Usage Access") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        } else if (!GateState.needsChallenge(this)) {
            GateUi.add(page, GateUi.text(this,
                "You have time remaining. No additional problem is required yet.",
                16f), bottom = 14)
            GateUi.add(page, GateUi.button(this, "Open Instagram →") { openInstagram() })
        } else {
            val problem = GateState.problem(this)
            val panel = GateUi.panel(this)
            GateUi.add(panel, GateUi.text(this, problem.topic, 12f, GateUi.TEAL, true), bottom = 12)
            GateUi.add(panel, GateUi.text(this, problem.prompt, 21f, GateUi.INK, true), bottom = 14)
            GateUi.add(panel, GateUi.text(this, "ENTER A NUMERIC ANSWER", 12f, GateUi.MUTED, true), bottom = 3)
            val answer = EditText(this).apply {
                hint = "e.g. 2.718"
                setSingleLine(true)
                textSize = 23f
                setTextColor(GateUi.INK)
                setHintTextColor(GateUi.MUTED)
                inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            GateUi.add(panel, answer, bottom = 11)
            val message = GateUi.text(this,
                "Give the numerical result, not the derivation. Small rounding errors are accepted.",
                13f, GateUi.MUTED)
            GateUi.add(panel, message, bottom = 12)
            GateUi.add(panel, GateUi.button(this, "VERIFY ANSWER →", GateUi.LIME, GateUi.INK) {
                if (problem.check(answer.text.toString())) {
                    if (GateState.earnFiveMinutes(this)) {
                        openInstagram()
                    } else {
                        draw()
                    }
                } else {
                    message.text = "Not correct. Hint: ${problem.hint}"
                    message.setTextColor(GateUi.CORAL)
                }
            })
            GateUi.add(page, panel, bottom = 18)
        }

        GateUi.add(page, GateUi.button(this, "Leave Instagram", GateUi.SOFT, GateUi.INK) {
            goHome()
        }, bottom = 12)
        GateUi.add(page, GateUi.text(this,
            "No skip or disable control. The next challenge appears after your new five-minute allowance is exhausted.",
            12f, GateUi.MUTED))
    }

    private fun openInstagram() {
        val launch = packageManager.getLaunchIntentForPackage(GateState.INSTAGRAM)
        if (launch == null) {
            GateUi.add(
                findViewById<android.view.ViewGroup>(android.R.id.content)
                    .getChildAt(0) as? android.widget.LinearLayout ?: return,
                GateUi.text(this, "Instagram could not be found on this phone.", 14f, GateUi.CORAL)
            )
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(launch)
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { goHome() }
}
