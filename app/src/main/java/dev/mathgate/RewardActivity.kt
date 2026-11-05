package dev.mathgate

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-size meme in a scrollable content area, plus a fixed bottom claim action.
 * Android 15+ navigation/status bar insets are applied to the outer container.
 * This is a REAL screen-layout fix, not a modification of the meme image.
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
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GateUi.BG)
        }
        setContentView(surface)

        // Samsung One UI on Android 15/16 draws app content behind the
        // three-button navigation area. Protect both the status and nav bars.
        surface.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(0, safe.top, 0, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    0, insets.systemWindowInsetTop, 0,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
        surface.requestApplyInsets()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                GateUi.dp(this@RewardActivity, 24),
                GateUi.dp(this@RewardActivity, 12),
                GateUi.dp(this@RewardActivity, 24),
                GateUi.dp(this@RewardActivity, 20)
            )
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(content)
        }
        surface.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        GateUi.add(content, GateUi.text(this, "●   MATHGATE  /  VICTORY",
            13f, GateUi.TEAL, true), bottom = 7)
        val title = GateUi.text(this, "CONGRATS! 🐾",
            33f, GateUi.CORAL, true)
        GateUi.add(content, title, bottom = 4)
        title.alpha = 0.1f
        title.animate().alpha(1f).setDuration(300L).start()
        GateUi.add(content, GateUi.text(this,
            "Correct answer. The Feline Mathematics Council approves.",
            15f, GateUi.INK), bottom = 13)

        val meme = CatMemeStore.daily(this)
        val card = GateUi.panel(this)
        GateUi.add(card, GateUi.text(this,
            "TODAY'S AI CAT MEME  ·  " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
                    .uppercase(Locale.US),
            12f, GateUi.TEAL, true), bottom = 9)

        val bitmap = CatMemeStore.bitmap(this, meme)
        if (bitmap != null) {
            val image = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(GateUi.SOFT)
                clipToOutline = false
                contentDescription = "Entire mathematics cat meme; no cropping"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            // Crucially: no hard-coded 255dp height or clamp. MATCH_PARENT
            // width + WRAP_CONTENT height preserves the bitmap aspect ratio.
            card.addView(image, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        } else {
            GateUi.add(card, GateUi.text(this, "🐈  ∫dx  ✓",
                39f, GateUi.TEAL, true), bottom = 10)
        }

        GateUi.add(card, GateUi.text(this,
            meme.title.uppercase(Locale.US),
            19f, GateUi.INK, true), top = 12, bottom = 4)
        GateUi.add(card, GateUi.text(this, meme.message,
            14f, GateUi.MUTED), bottom = 9)
        GateUi.add(content, card, bottom = 12)

        val reward = GateUi.panel(this, true)
        GateUi.add(reward, GateUi.text(this,
            "REWARD READY  ·  NOT YET CREDITED",
            12f, GateUi.LIME, true), bottom = 3)
        GateUi.add(reward, GateUi.text(this,
            "+05:00 INSTAGRAM TIME", 23f, GateUi.WHITE, true))
        GateUi.add(content, reward, bottom = 8)

        // Pinned footer is always outside the scrollable body. Its bottom is
        // above the navigation inset, regardless of the length of the meme.
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                GateUi.dp(this@RewardActivity, 24),
                GateUi.dp(this@RewardActivity, 8),
                GateUi.dp(this@RewardActivity, 24),
                GateUi.dp(this@RewardActivity, 10)
            )
            setBackgroundColor(GateUi.BG)
            elevation = GateUi.dp(this@RewardActivity, 8).toFloat()
        }
        GateUi.add(footer, GateUi.button(this,
            "CLAIM +5:00  ·  OPEN INSTAGRAM",
            GateUi.LIME, GateUi.INK) { claim() }, bottom = 5)
        GateUi.add(footer, GateUi.button(this,
            "Leave without claiming",
            GateUi.SOFT, GateUi.INK) { leave() })
        surface.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
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
        } else leave()
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
