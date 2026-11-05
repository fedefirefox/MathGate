package dev.mathgate

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object GateState {
    const val INSTAGRAM = "com.instagram.android"
    const val BASE_MINUTES = 15
    const val EXTRA_MINUTES = 5
    const val MAX_EXTENSIONS = 2

    private fun preferences(context: Context) =
        context.getSharedPreferences("mathgate", Context.MODE_PRIVATE)

    private fun key(): String = LocalDate.now().toString()

    fun solved(context: Context): Int =
        preferences(context).getInt("solved_" + key(), 0).coerceIn(0, MAX_EXTENSIONS)

    fun grant(context: Context) {
        preferences(context).edit().putInt(
            "solved_" + key(), (solved(context) + 1).coerceAtMost(MAX_EXTENSIONS)
        ).apply()
    }

    fun allowedMs(context: Context): Long =
        (BASE_MINUTES + EXTRA_MINUTES * solved(context)) * 60_000L

    fun usageGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    /** Uses the Android usage history; not an anti-tamper mechanism. */
    fun usedMs(context: Context): Long {
        if (!usageGranted(context)) return 0
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val beginning = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        return manager.queryAndAggregateUsageStats(beginning, now)[INSTAGRAM]
            ?.totalTimeInForeground?.coerceAtLeast(0L) ?: 0L
    }

    data class Problem(val title: String, val prompt: String, val expected: Double, val tolerance: Double, val hint: String)
    val problems = listOf(
        Problem(
            "Vector calculus · Gauss theorem",
            "Calculate the OUTWARD flux of F = (x³, y³, z³) through the unit sphere x² + y² + z² = 1. Enter a decimal approximation.",
            12.0 * Math.PI / 5.0, 0.02,
            "Use the divergence theorem and integrate 3r² over the unit ball."
        ),
        Problem(
            "Differential equations · Resonance",
            "Solve y'' − 3y' + 2y = eˣ, with y(0) = 0 and y'(0) = 0. What is y(1)? Enter a decimal approximation.",
            Math.E * Math.E - 2.0 * Math.E, 0.01,
            "The forcing term resonates with a homogeneous solution."
        )
    )

    fun currentProblem(context: Context): Problem =
        problems[solved(context).coerceIn(0, problems.lastIndex)]

    fun correct(input: String, problem: Problem): Boolean {
        val normalized = input.trim().replace(',', '.')
        val number = normalized.toDoubleOrNull() ?: return false
        return number.isFinite() && abs(number - problem.expected) <= problem.tolerance
    }
}
