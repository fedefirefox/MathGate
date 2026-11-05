package dev.mathgate

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.security.SecureRandom
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Today's Instagram usage is measured from Android's system usage history.
 * Samsung Digital Wellbeing does not expose its proprietary counter through a public API.
 */
object GateState {
    const val INSTAGRAM = "com.instagram.android"
    const val FREE_MINUTES = 20
    const val BONUS_MINUTES = 5
    private const val PREFS = "mathgate_v2"
    private val rng = SecureRandom()
    private var cachedDay = ""
    private var cachedAt = 0L
    private var cachedUsage = 0L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun ensureDay(c: Context) {
        val p = prefs(c)
        if (p.getString("day", null) != today()) {
            p.edit().putString("day", today()).putInt("bonuses", 0)
                .remove("challengeSeed")
                .remove("activeProblemId")
                .remove("activeProblemLevel")
                .remove("activeProblemFamily")
                .remove("rewardTicket")
                .remove("rewardProblem")
                .apply()
        }
    }

    fun bonusCount(c: Context): Int {
        ensureDay(c)
        return prefs(c).getInt("bonuses", 0).coerceAtLeast(0)
    }

    fun difficulty(c: Context): String {
        val level = prefs(c).getString("difficulty", "standard")
        return if (level in listOf("foundation", "standard", "advanced")) level!! else "standard"
    }

    fun setDifficulty(c: Context, level: String) {
        require(level in listOf("foundation", "standard", "advanced"))
        prefs(c).edit().putString("difficulty", level).apply()
    }

    fun allowanceMs(c: Context): Long =
        (FREE_MINUTES.toLong() + BONUS_MINUTES.toLong() * bonusCount(c)) * 60_000L

    /** ACTIVITY_RESUMED / PAUSED event intervals, clipped to local midnight.
     * Reading an event window starting 24h earlier handles sessions crossing midnight.
     * Results may differ slightly from the Digital Wellbeing UI on some Samsung builds.
     */
    @Synchronized
    fun usedMs(c: Context): Long {
        if (!hasUsageAccess(c)) return -1L
        val day = today()
        val nowTick = SystemClock.elapsedRealtime()
        if (day == cachedDay && nowTick >= cachedAt && nowTick - cachedAt < 1000L) {
            return cachedUsage
        }
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val usage = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usage.queryEvents(midnight - 86_400_000L, now) ?: return -1L
        val event = UsageEvents.Event()
        var openAt: Long? = null
        var total = 0L

        fun close(at: Long) {
            val start = openAt ?: return
            val clippedStart = max(midnight, start)
            val clippedEnd = at.coerceAtMost(now)
            if (clippedEnd > clippedStart) total += clippedEnd - clippedStart
            openAt = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val time = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.packageName == INSTAGRAM) {
                        if (openAt == null) openAt = time
                    } else if (openAt != null) {
                        close(time)
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == INSTAGRAM) close(time)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> close(time)
            }
        }
        close(now)
        cachedDay = day
        cachedAt = nowTick
        cachedUsage = total.coerceAtLeast(0L)
        return cachedUsage
    }

    fun remainingMs(c: Context): Long {
        val used = usedMs(c)
        return if (used < 0L) 0L else (allowanceMs(c) - used).coerceAtLeast(0L)
    }

    fun needsChallenge(c: Context): Boolean {
        val used = usedMs(c)
        return used < 0L || used >= allowanceMs(c)
    }

    /**
     * A verified answer offers, but does not yet award, the five-minute reward.
     * An unclaimed reward survives switching apps until the local day changes.
     */
    @Synchronized
    fun offerReward(c: Context, verifiedProblemId: String): String? {
        ensureDay(c)
        if (!hasUsageAccess(c) || !needsChallenge(c)) return null
        val p = prefs(c)
        val existing = p.getString("rewardTicket", null)
        if (!existing.isNullOrBlank()) return existing
        // Only the currently displayed challenge can create a reward ticket.
        val activeId = p.getString("activeProblemId", null)
        if (activeId.isNullOrBlank() || activeId != verifiedProblemId) return null
        val ticket = UUID.randomUUID().toString()
        val accepted = p.edit()
            .putString("rewardTicket", ticket)
            .putString("rewardProblem", verifiedProblemId.take(80))
            .putString("rewardLevel", p.getString("activeProblemLevel", difficulty(c)))
            .putString("rewardFamily", p.getString("activeProblemFamily", "unknown"))
            .commit()
        return if (accepted) ticket else null
    }

    fun pendingReward(c: Context): String? {
        ensureDay(c)
        return prefs(c).getString("rewardTicket", null)
    }

    /**
     * Crediting is an atomic, one-time local action triggered ONLY by the
     * user's Claim +5:00 button. Never credit on correct-answer detection.
     */
    private fun historyKey(level: String): String =
        "completedProblemIds_" + level

    /** Credits +5:00 and records the solved question in the same transaction. */
    @Synchronized
    fun claimReward(c: Context, ticket: String): Boolean {
        ensureDay(c)
        if (!hasUsageAccess(c)) return false
        val p = prefs(c)
        val pending = p.getString("rewardTicket", null)
        if (pending.isNullOrBlank() || pending != ticket) return false
        val completedId = p.getString("rewardProblem", null)
        val completedLevel = p.getString("rewardLevel", "standard") ?: "standard"
        val completedFamily = p.getString("rewardFamily", "unknown") ?: "unknown"
        if (completedId.isNullOrBlank()) return false

        val old = p.getStringSet(historyKey(completedLevel), emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        old.add(completedId)

        val count = p.getInt("bonuses", 0).coerceIn(0, 100_000)
        return p.edit()
            .putInt("bonuses", count + 1)
            .putStringSet(historyKey(completedLevel), old.toSet())
            .putString("lastSolvedProblemId", completedId)
            .putString("lastSolvedFamily", completedFamily)
            .remove("activeProblemId")
            .remove("activeProblemLevel")
            .remove("activeProblemFamily")
            .remove("rewardTicket")
            .remove("rewardProblem")
            .remove("rewardLevel")
            .remove("rewardFamily")
            .remove("challengeSeed")
            .commit()
    }

    /**
     * Stable until solved: reopening Instagram, leaving MathGate, switching
     * activities or making an incorrect attempt does not reroll a challenge.
     * Only a CLAIMED reward changes the question and completed-ID history.
     */
    @Synchronized
    fun problem(c: Context): CalculusProblem {
        ensureDay(c)
        val p = prefs(c)
        val bank = try { LibraryStore.current(c).problems } catch (_: Exception) { emptyList() }

        val activeId = p.getString("activeProblemId", null)
        if (!activeId.isNullOrBlank()) {
            bank.firstOrNull { it.id == activeId }?.let { return it }
        }

        val selectedLevel = difficulty(c)
        if (bank.isNotEmpty()) {
            val options = bank.filter { it.level == selectedLevel }
            if (options.isNotEmpty()) {
                val key = historyKey(selectedLevel)
                var completed = p.getStringSet(key, emptySet())?.toSet() ?: emptySet()

                // A new shuffled cycle starts only once every question at the
                // selected difficulty has already been completed.
                if (options.all { it.id in completed }) {
                    p.edit().remove(key).commit()
                    completed = emptySet()
                }
                val picked = QuestionSelection.choose(
                    options.map {
                        QuestionSelection.Item(it.id, QuestionSelection.family(it.topic))
                    },
                    completed,
                    p.getString("lastSolvedProblemId", null),
                    p.getString("lastSolvedFamily", null),
                    Random(rng.nextLong())
                )
                val next = options.first { it.id == picked.id }
                p.edit()
                    .putString("activeProblemId", next.id)
                    .putString("activeProblemLevel", selectedLevel)
                    .putString("activeProblemFamily", picked.family)
                    .commit()
                return next
            }
        }

        // Safe offline fallback if the shipped problem bank cannot be parsed.
        // Keep the generated question stable across the challenge session.
        var seed = p.getInt("challengeSeed", 0)
        if (seed == 0) {
            seed = rng.nextInt(Int.MAX_VALUE - 1) + 1
            p.edit().putInt("challengeSeed", seed).commit()
        }
        val fallback = ProblemBank.generate(seed).copy(
            id = "fallback-" + seed, level = selectedLevel
        )
        p.edit()
            .putString("activeProblemId", fallback.id)
            .putString("activeProblemLevel", selectedLevel)
            .putString("activeProblemFamily", QuestionSelection.family(fallback.topic))
            .commit()
        return fallback
    }

    fun timeLabel(ms: Long): String {
        val mins = ms.coerceAtLeast(0L) / 60_000L
        val secs = (ms.coerceAtLeast(0L) / 1000L) % 60L
        return "%d:%02d".format(java.util.Locale.US, mins, secs)
    }
}

data class CalculusProblem(
    val topic: String,
    val prompt: String,
    val expected: Double,
    val hint: String,
    val latex: String = "",
    val tolerance: Double = 0.0,
    val level: String = "standard",
    val id: String = "generated"
) {
    fun check(input: String): Boolean {
        val value = AnswerParser.parse(input) ?: return false
        if (!value.isFinite()) return false
        val threshold = if (tolerance > 0.0) tolerance else max(0.008, abs(expected) * 0.0015)
        return abs(value - expected) <= threshold
    }
}

/** Parameterized numerical-answer problems from undergraduate Analysis I/II.
 * No false claim of automatic symbolic proof verification.
 */
object ProblemBank {
    fun generate(seed: Int): CalculusProblem {
        val r = Random(seed)
        val a = r.nextInt(2, 7)
        val b = r.nextInt(1, 6)
        val c = r.nextInt(1, 5)
        return when (r.nextInt(8)) {
            0 -> CalculusProblem(
                "VECTOR CALCULUS · GAUSS",
                "Compute the outward flux of F = (${a}x³, ${b}y³, ${c}z³) through the unit sphere x² + y² + z² = 1.",
                4.0 * Math.PI * (a + b + c) / 5.0,
                "Use the divergence theorem and the moments of the unit ball."
            )
            1 -> {
                val rx = r.nextInt(2, 5)
                val ry = r.nextInt(1, 4)
                CalculusProblem(
                    "VECTOR CALCULUS · GREEN",
                    "Evaluate ∮(-${a}y dx + ${b}x dy) counterclockwise around the ellipse x²/${rx * rx} + y²/${ry * ry} = 1.",
                    (a + b) * Math.PI * rx * ry,
                    "Use Green's theorem and the area of an ellipse."
                )
            }
            2 -> {
                val side = r.nextInt(2, 6)
                CalculusProblem(
                    "MULTIVARIABLE · DOUBLE INTEGRALS",
                    "Evaluate ∫∫(${a}x² + ${b}y²)dA on the triangle x≥0, y≥0, x+y≤${side}.",
                    (a + b) * Math.pow(side.toDouble(), 4.0) / 12.0,
                    "Use y from 0 to L−x, then x from 0 to L."
                )
            }
            3 -> {
                val decay = r.nextInt(2, 6)
                CalculusProblem(
                    "ANALYSIS · IMPROPER INTEGRALS",
                    "Evaluate ∫₀^∞ t³ exp(−${decay}t)dt. Give your answer numerically.",
                    6.0 / Math.pow(decay.toDouble(), 4.0),
                    "Use integration by parts or Γ(4)."
                )
            }
            4 -> {
                val p = r.nextInt(2, 6)
                val q = r.nextInt(2, 6)
                val u = r.nextInt(2, 8)
                val v = r.nextInt(2, 8)
                val det = (4 * p * q - 1).toDouble()
                val x = (2.0 * q * u - v) / det
                val y = (2.0 * p * v - u) / det
                CalculusProblem(
                    "MULTIVARIABLE · OPTIMIZATION",
                    "Determine the global MINIMUM VALUE of f(x,y) = ${p}x² + ${q}y² + xy − ${u}x − ${v}y over ℝ².",
                    p * x * x + q * y * y + x * y - u * x - v * y,
                    "Set ∇f=0 and check positive definiteness of the Hessian."
                )
            }
            5 -> {
                val mu = r.nextInt(2, 6)
                val d = (mu - 1).toDouble()
                val answer = (Math.exp(mu.toDouble()) - Math.E) / (d * d) - Math.E / d
                CalculusProblem(
                    "DIFFERENTIAL EQUATIONS · RESONANCE",
                    "Given y″−${mu + 1}y′+${mu}y=e^t, with y(0)=0 and y′(0)=0, compute y(1).",
                    answer,
                    "The forcing resonates with one homogeneous solution; use t·e^t."
                )
            }
            6 -> {
                val outer = r.nextInt(2, 5)
                CalculusProblem(
                    "MULTIVARIABLE · POLAR INTEGRALS",
                    "Compute ∫∫(${a}x² + ${b}y²)dA over the annulus 1≤x²+y²≤${outer * outer}.",
                    (a + b) * Math.PI * (Math.pow(outer.toDouble(), 4.0) - 1.0) / 4.0,
                    "Convert to polar coordinates, including the Jacobian r."
                )
            }
            else -> {
                val q = r.nextInt(2, 6)
                CalculusProblem(
                    "ANALYSIS · INFINITE SERIES",
                    "Calculate Σ[n(n+1)/${q}ⁿ] for n=1,2,... to infinity. Enter a decimal approximation.",
                    2.0 * q * q / Math.pow((q - 1).toDouble(), 3.0),
                    "Differentiate the geometric series twice."
                )
            }
        }
    }
}
