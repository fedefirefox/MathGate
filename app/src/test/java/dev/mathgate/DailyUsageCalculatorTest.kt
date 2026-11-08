package dev.mathgate

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyUsageCalculatorTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val instagram = "com.instagram.android"
    private fun at(day: LocalDate, hour: Int, minute: Int) =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun e(time: Long, pkg: String?, kind: Int) =
        DailyUsageCalculator.Event(time, pkg, kind)

    @Test fun lastNightsThirtyMinutesDoNotCarryOver() {
        val yesterday = LocalDate.of(2026, 11, 7)
        val today = yesterday.plusDays(1)
        val now = at(today, 0, 6)
        val events = listOf(
            e(at(yesterday, 23, 0), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(at(yesterday, 23, 30), instagram, DailyUsageCalculator.ACTIVITY_PAUSED)
        )
        assertEquals(0L, DailyUsageCalculator.todayMs(
            events, at(today, 0, 0), now, instagram))
    }

    @Test fun crossingMidnightCountsOnlyMinutesAfterMidnight() {
        val yesterday = LocalDate.of(2026, 11, 7)
        val today = yesterday.plusDays(1)
        val events = listOf(
            e(at(yesterday, 23, 50), instagram, DailyUsageCalculator.ACTIVITY_RESUMED)
        )
        assertEquals(6 * 60_000L, DailyUsageCalculator.todayMs(
            events, at(today, 0, 0), at(today, 0, 6), instagram))
    }

    @Test fun yesterdayAndTodaySessionsAreNeverCombinedAcrossDayBoundary() {
        val yesterday = LocalDate.of(2026, 11, 7)
        val today = yesterday.plusDays(1)
        val events = listOf(
            e(at(yesterday, 23, 12), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(at(yesterday, 23, 35), instagram, DailyUsageCalculator.ACTIVITY_PAUSED),
            e(at(today, 0, 1), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(at(today, 0, 3), "com.android.settings",
                DailyUsageCalculator.ACTIVITY_RESUMED)
        )
        assertEquals(2 * 60_000L, DailyUsageCalculator.todayMs(
            events, at(today, 0, 0), at(today, 0, 6), instagram))
    }

    @Test fun explicitAndroidRolloverEventsCountNewDayOnly() {
        val yesterday = LocalDate.of(2026, 11, 7)
        val today = yesterday.plusDays(1)
        val midnight = at(today, 0, 0)
        val events = listOf(
            e(at(yesterday, 23, 30), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(midnight, instagram, DailyUsageCalculator.END_OF_DAY),
            e(midnight, instagram, DailyUsageCalculator.CONTINUE_PREVIOUS_DAY)
        )
        assertEquals(6 * 60_000L, DailyUsageCalculator.todayMs(
            events, midnight, at(today, 0, 6), instagram))
    }

    @Test fun switchToDifferentAppStopsChargingInstagram() {
        val today = LocalDate.of(2026, 11, 8)
        val events = listOf(
            e(at(today, 0, 2), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(at(today, 0, 5), "dev.mathgate", DailyUsageCalculator.ACTIVITY_RESUMED)
        )
        assertEquals(3 * 60_000L, DailyUsageCalculator.todayMs(
            events, at(today, 0, 0), at(today, 0, 10), instagram))
    }

    @Test fun screenLockStopsForegroundUsage() {
        val today = LocalDate.of(2026, 11, 8)
        val events = listOf(
            e(at(today, 0, 2), instagram, DailyUsageCalculator.ACTIVITY_RESUMED),
            e(at(today, 0, 4), "android", DailyUsageCalculator.SCREEN_NON_INTERACTIVE)
        )
        assertEquals(2 * 60_000L, DailyUsageCalculator.todayMs(
            events, at(today, 0, 0), at(today, 0, 10), instagram))
    }

    @Test fun foregroundCanRecoverAnAppResumedBeforeMidnight() {
        val yesterday = LocalDate.of(2026, 11, 7)
        assertEquals(instagram, DailyUsageCalculator.foreground(listOf(
            e(at(yesterday, 23, 59), instagram,
                DailyUsageCalculator.ACTIVITY_RESUMED)
        )))
    }

    @Test fun localMidnightHonorsDaylightSavingSpringForward() {
        val now = at(LocalDate.of(2026, 3, 9), 0, 10)
        val todayStart = DailyUsageCalculator.localMidnight(now, zone)
        val yesterdayStart = DailyUsageCalculator.previousLocalMidnight(now, zone)
        assertEquals(23 * 3_600_000L, todayStart - yesterdayStart)
    }

    @Test fun localMidnightHonorsDaylightSavingFallBack() {
        val now = at(LocalDate.of(2026, 11, 2), 0, 10)
        val todayStart = DailyUsageCalculator.localMidnight(now, zone)
        val yesterdayStart = DailyUsageCalculator.previousLocalMidnight(now, zone)
        assertEquals(25 * 3_600_000L, todayStart - yesterdayStart)
    }

    @Test fun zeroUsageAtExactMidnight() {
        val today = LocalDate.of(2026, 11, 8)
        val midnight = at(today, 0, 0)
        val events = listOf(e(at(today.minusDays(1), 23, 45), instagram,
            DailyUsageCalculator.ACTIVITY_RESUMED))
        assertEquals(0L, DailyUsageCalculator.todayMs(events, midnight, midnight, instagram))
    }
}
