package dev.mathgate

import java.time.Instant
import java.time.ZoneId

/**
 * Daily Instagram usage from timestamped foreground events, clipped STRICTLY
 * to the current local calendar day. No daily UsageStats bucket is ever used:
 * Android may expand aggregated query bounds to whole interval periods.
 *
 * The lookback starts at the previous LOCAL midnight, not 24h before today,
 * so it works on 23- and 25-hour daylight-saving transition days.
 *
 * Integer event kinds correspond to Android UsageEvents.Event constants.
 * 3 (END_OF_DAY) and 4 (CONTINUE_PREVIOUS_DAY) are internal Android rollover
 * markers observed in some versions; public lifecycle events also suffice.
 */
object DailyUsageCalculator {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val END_OF_DAY = 3
    const val CONTINUE_PREVIOUS_DAY = 4
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 18
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26

    data class Event(
        val timeMs: Long,
        val packageName: String?,
        val kind: Int
    )

    fun localMidnight(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun previousLocalMidnight(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().minusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun foreground(events: Iterable<Event>): String? {
        var foreground: String? = null
        for (event in events) {
            when (event.kind) {
                ACTIVITY_RESUMED, CONTINUE_PREVIOUS_DAY ->
                    foreground = event.packageName
                ACTIVITY_PAUSED, ACTIVITY_STOPPED ->
                    if (foreground == event.packageName) foreground = null
                END_OF_DAY, SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN,
                DEVICE_SHUTDOWN -> foreground = null
            }
        }
        return foreground
    }

    fun todayMs(
        events: Iterable<Event>,
        todayMidnightMs: Long,
        nowMs: Long,
        instagramPackage: String
    ): Long {
        require(nowMs >= todayMidnightMs)
        var opened: Long? = null
        var total = 0L

        fun finish(at: Long) {
            val started = opened ?: return
            val clippedStart = maxOf(started, todayMidnightMs)
            val clippedEnd = minOf(at, nowMs)
            if (clippedEnd > clippedStart) {
                total += clippedEnd - clippedStart
            }
            opened = null
        }

        for (event in events) {
            if (event.timeMs > nowMs) break
            when (event.kind) {
                ACTIVITY_RESUMED, CONTINUE_PREVIOUS_DAY -> {
                    if (event.packageName == instagramPackage) {
                        if (opened == null) opened = event.timeMs
                    } else {
                        finish(event.timeMs)
                    }
                }
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> {
                    if (event.packageName == instagramPackage) finish(event.timeMs)
                }
                END_OF_DAY, SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN,
                DEVICE_SHUTDOWN -> finish(event.timeMs)
            }
        }
        finish(nowMs)
        return total.coerceAtLeast(0L)
    }
}
