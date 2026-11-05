package dev.mathgate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Celebratory, full-screen reward dialog shown AFTER a correct answer and
 * BEFORE the actual five-minute Instagram allowance is credited.
 */
class RewardActivity : Activity() {
    companion object {
        const val EXTRA_TICKET = "dev.mathgate.EXTRA_REWARD_TICKET"
    }

    private var claiming = false
    private var ticket: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GateUi.styleBars(this)
        ticket = intent.getStringExtra(EXTRA_TICKET)
    }

    override fun onResume() {
        super.onResume()
        val pending = GateState.pendingReward(this)
        if (pending.isNullOrEmpty() || pending != ticket) {
            leave()
            return
        }
        draw()
    }

    private fun draw() {
        val screen = GateUi.root(this)
        setContentView(GateUi.scroll(this, screen))

        GateUi.add(screen, GateUi.text(this,
            "●   MATHGATE   /   VICTORY", 13f, GateUi.TEAL, true),
            bottom = 12)
        val title = GateUi.text(this, "CONGRATS! 🐾",
            37f, GateUi.CORAL, true)
        GateUi.add(screen, title, bottom = 5)
        title.alpha = 0.3f
        title.scaleX = 0.85f
        title.scaleY = 0.85f
        title.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400L).start()

        GateUi.add(screen, GateUi.text(this,
            "Your answer is correct. The Feline Mathematics Council has issued its verdict.",
            16f, GateUi.INK), bottom = 15)

        val meme = CatMemeStore.daily(this)
        val card = GateUi.panel(this)
        GateUi.add(card, GateUi.text(this,
            "TODAY'S AI CAT MEME  ·  " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                    .uppercase(Locale.US),
            12f, GateUi.TEAL, true), bottom = 11)

        val bitmap = CatMemeStore.bitmap(this, meme)
        if (bitmap != null) {
            val artwork = ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = GateUi.shape(this@RewardActivity, GateUi.SOFT, 18)
                clipToOutline = true
                contentDescription = "AI-generated illustration of a cat celebrating a mathematics solution"
            }
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val screenWidth = resources.displayMetrics.widthPixels -
                GateUi.dp(this, 88)
            val heightPx = (screenWidth / aspect).toInt()
                .coerceIn(GateUi.dp(this, 155), GateUi.dp(this, 255))
            card.addView(artwork, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                heightPx
            ))
        } else {
            GateUi.add(card, GateUi.text(this, "🐈‍⬛ 📚 ✨",
                46f, GateUi.TEAL, true), bottom = 9)
        }

        GateUi.add(card, GateUi.text(this,
            meme.title.uppercase(Locale.US),
            21f, GateUi.INK, true), top = 15, bottom = 6)
        GateUi.add(card, GateUi.text(this,
            meme.message, 15f, GateUi.MUTED), bottom = 12)
        GateUi.add(screen, card, bottom = 16)

        val reward = GateUi.panel(this, true)
        GateUi.add(reward, GateUi.text(this,
            "REWARD AWAITING CONFIRMATION",
            12f, GateUi.LIME, true), bottom = 5)
        GateUi.add(reward, GateUi.text(this,
            "+05:00  INSTAGRAM TIME",
            27f, GateUi.WHITE, true))
        GateUi.add(reward, GateUi.text(this,
            "These minutes have NOT been added yet. Claim to unlock.",
            13f, GateUi.WHITE))
        GateUi.add(screen, reward, bottom = 13)

        GateUi.add(screen, GateUi.button(this,
            "CLAIM +5:00  ·  OPEN INSTAGRAM",
            GateUi.LIME, GateUi.INK) { claim() }, bottom = 10)
        GateUi.add(screen, GateUi.button(this,
            "Leave without claiming",
            GateUi.SOFT, GateUi.INK) { leave() }, bottom = 10)
        GateUi.add(screen, GateUi.text(this,
            "No additional time is credited until you claim. " +
                "The same reward will be waiting if you leave and return today.",
            12f, GateUi.MUTED))
    }

    private fun claim() {
        if (claiming) return
        claiming = true
        val accepted = ticket?.let { GateState.claimReward(this, it) } == true
        if (!accepted) {
            claiming = false
            leave()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(GateState.INSTAGRAM)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(launch)
            finish()
        } else {
            leave()
        }
    }

    private fun leave() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { leave() }
}
