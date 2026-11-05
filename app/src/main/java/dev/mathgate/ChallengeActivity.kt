package dev.mathgate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText

/** A foreground Activity stops Instagram's foreground clock while mathematics is solved. */
class ChallengeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
    }

    override fun onResume() {
        super.onResume()
        val pending = GateState.pendingReward(this)
        if (pending != null) {
            showReward(pending)
        } else {
            draw()
        }
    }

    private fun showReward(ticket: String) {
        startActivity(Intent(this, RewardActivity::class.java).apply {
            putExtra(RewardActivity.EXTRA_TICKET, ticket)
        })
        finish()
    }

    private fun draw() {
        val screen = GateUi.root(this)
        setContentView(GateUi.scroll(this, screen))
        GateUi.add(screen, GateUi.text(this,
            "●  MATHGATE  /  ACCESS GATE", 13f, GateUi.TEAL, true),
            bottom = 12)
        GateUi.add(screen, GateUi.text(this,
            "TIME'S UP.\nEARN +05:00.", 33f, GateUi.INK, true))
        val used = GateState.usedMs(this)
        val allowed = GateState.allowanceMs(this)
        GateUi.add(screen, GateUi.text(this,
            if (used < 0) "Usage permission missing"
            else "Today: " + GateState.timeLabel(used) +
                " of " + GateState.timeLabel(allowed) + " used",
            14f, GateUi.MUTED), top = 5, bottom = 18)

        if (!GateState.hasUsageAccess(this)) {
            GateUi.add(screen, GateUi.text(this,
                "Enable Android Usage Access so MathGate can read Instagram screen time.",
                16f, GateUi.CORAL), bottom = 12)
            GateUi.add(screen, GateUi.button(this, "Grant Usage Access") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        } else if (!GateState.needsChallenge(this)) {
            GateUi.add(screen, GateUi.text(this,
                "You still have Instagram time available.",
                16f), bottom = 14)
            GateUi.add(screen, GateUi.button(this,
                "Open Instagram →") { openInstagram() })
        } else {
            val question = GateState.problem(this)
            val card = GateUi.panel(this)
            GateUi.add(card, GateUi.text(this,
                question.topic.uppercase() + "   ·   " +
                    question.level.uppercase(),
                12f, GateUi.TEAL, true), bottom = 12)
            GateUi.add(card, GateUi.text(this, question.prompt,
                16f, GateUi.MUTED), bottom = 7)
            if (question.latex.isNotBlank()) {
                GateUi.add(card, MathRenderer.create(this, question.latex),
                    bottom = 10)
            }
            GateUi.add(card, GateUi.text(this,
                "YOUR NUMERICAL ANSWER", 13f, GateUi.INK, true), bottom = 5)
            val entry = EditText(this).apply {
                hint = "e.g. 2*pi, sqrt(3)/2, 1.234"
                setSingleLine(true)
                textSize = 20f
                setTextColor(GateUi.INK)
                setHintTextColor(GateUi.MUTED)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            GateUi.add(card, entry, bottom = 11)
            val message = GateUi.text(this,
                "Type a decimal or an expression using pi, e, sqrt, exp, sin, cos, ln.",
                13f, GateUi.MUTED)
            GateUi.add(card, message, bottom = 12)
            GateUi.add(card, GateUi.button(this, "VERIFY ANSWER →",
                GateUi.LIME, GateUi.INK) {
                if (question.check(entry.text.toString())) {
                    val ticket = GateState.offerReward(this, question.id)
                    if (ticket != null) showReward(ticket) else draw()
                } else {
                    message.text = "Not correct. Hint: " + question.hint
                    message.setTextColor(GateUi.CORAL)
                }
            })
            GateUi.add(screen, card, bottom = 16)
        }
        GateUi.add(screen, GateUi.button(this,
            "Leave Instagram", GateUi.SOFT, GateUi.INK) {
            goHome()
        }, bottom = 11)
        GateUi.add(screen, GateUi.text(this,
            "No skip or pause control. The next question appears when the earned five minutes are used.",
            12f, GateUi.MUTED))
    }

    private fun openInstagram() {
        val launch = packageManager.getLaunchIntentForPackage(GateState.INSTAGRAM)
        if (launch == null) { goHome(); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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
