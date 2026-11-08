package dev.mathgate

import java.time.Instant
import java.time.ZoneId

/**
 * Derives actual-day foreground usage from Android lifecycle events.
 *
 * Android events are per Activity, not per app. Closing ANY Instagram
 * Activity used to end the whole app session, even when a different
 * Instagram Activity was already resumed (e.g. feed -> Reels).
 * Track the most recently resumed Activity class to avoid this undercount.
 *
 * Android does not expose the underlying Activity instance ID to ordinary
 * third-party apps, so rare overlapping instances of the SAME class cannot
 * be fully disambiguated. Samsung Digital Wellbeing has private counters.
 */
object DailyUsageCalculator {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val END_OF_DAY = 3
    const val CONTINUE_PREVIOUS_DAY = 4
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27

    data class Event(
        val timeMs: Long,
        val packageName: String?,
        val kind: Int,
        val className: String? = null
    )

    private data class Foreground(
        val pkg: String? = null,
        val activityClass: String? = null
    )

    fun localMidnight(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun previousLocalMidnight(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().minusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    private fun transition(current: Foreground, e: Event): Foreground {
        return when (e.kind) {
            ACTIVITY_RESUMED, CONTINUE_PREVIOUS_DAY ->
                if (e.packageName == null) current
                else Foreground(e.packageName, e.className)

            ACTIVITY_PAUSED, ACTIVITY_STOPPED ->
                // Ignore a late PAUSED/STOPPED from an older Activity of
                // Instagram if a newer Instagram Activity is already resumed.
                if (current.pkg == e.packageName &&
                    (current.activityClass == null || e.className == null ||
                        current.activityClass == e.className)) Foreground()
                else current

            END_OF_DAY, SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN,
            DEVICE_SHUTDOWN, DEVICE_STARTUP -> Foreground()

            // KEYGUARD_HIDDEN is Android event 18, NOT keyguard shown.
            // Unlocking does not itself terminate a foreground app.
            else -> current
        }
    }

    fun foreground(events: Iterable<Event>): String? {
        var state = Foreground()
        for (event in events) {
            state = transition(state, event)
        }
        return state.pkg
    }

    fun todayMs(
        events: Iterable<Event>,
        todayMidnightMs: Long,
        nowMs: Long,
        instagramPackage: String
    ): Long {
        require(nowMs >= todayMidnightMs)
        var state = Foreground()
        var openedAt: Long? = null
        var total = 0L

        fun close(at: Long) {
            val started = openedAt ?: return
            val clippedStart = maxOf(started, todayMidnightMs)
            val clippedEnd = minOf(at, nowMs)
            if (clippedEnd > clippedStart) total += clippedEnd - clippedStart
            openedAt = null
        }

        for (event in events) {
            if (event.timeMs > nowMs) break
            val before = state.pkg == instagramPackage
            val afterState = transition(state, event)
            val after = afterState.pkg == instagramPackage
            if (before && !after) close(event.timeMs)
            if (!before && after) openedAt = event.timeMs
            state = afterState
        }
        if (state.pkg == instagramPackage) close(nowMs)
        return total.coerceAtLeast(0L)
    }
}
