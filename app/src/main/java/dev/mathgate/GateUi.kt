package dev.mathgate

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Bright editorial palette: off-white, graphite, electric lime and coral. */
object GateUi {
    val BG = Color.rgb(247, 249, 244)
    val INK = Color.rgb(16, 42, 46)
    val WHITE = Color.WHITE
    val CARD = Color.rgb(255, 255, 255)
    val SOFT = Color.rgb(231, 239, 233)
    val LIME = Color.rgb(222, 249, 100)
    val TEAL = Color.rgb(0, 144, 131)
    val CORAL = Color.rgb(245, 98, 76)
    val MUTED = Color.rgb(92, 118, 119)

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun shape(ctx: Context, color: Int, radius: Int = 20): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius).toFloat()
        }

    fun panel(ctx: Context, dark: Boolean = false) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 20))
        background = shape(ctx, if (dark) INK else CARD, 22)
    }

    fun root(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 24), dp(ctx, 45), dp(ctx, 24), dp(ctx, 28))
        setBackgroundColor(BG)
    }

    fun scroll(ctx: Context, child: LinearLayout) =
        ScrollView(ctx).apply { isFillViewport = true; addView(child) }

    fun text(ctx: Context, value: String, size: Float,
             color: Int = INK, strong: Boolean = false) = TextView(ctx).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(if (strong) "sans-serif-medium" else "sans-serif",
            if (strong) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
    }

    fun button(ctx: Context, label: String,
               backgroundColor: Int = INK,
               foreground: Int = WHITE,
               action: () -> Unit) = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(foreground)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = shape(ctx, backgroundColor, 13)
        minHeight = dp(ctx, 54)
        setOnClickListener { action() }
    }

    fun add(parent: LinearLayout, child: View, top: Int = 0, bottom: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(parent.context, top)
            bottomMargin = dp(parent.context, bottom)
        })
    }

    fun progress(ctx: Context, fraction: Float) =
        ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (fraction.coerceIn(0f, 1f) * max).toInt()
            progressTintList = ColorStateList.valueOf(LIME)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(55, 84, 86))
            minimumHeight = dp(ctx, 7)
        }

    fun styleBars(activity: Activity) {
        activity.window.statusBarColor = BG
        activity.window.navigationBarColor = BG
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
}
