package dev.mathgate

import org.junit.Assert.*
import org.junit.Test

class GuardRulesTest {
    private fun blocks(
        foreground: Boolean = true,
        locked: Boolean = false,
        permitted: Boolean = true,
        used: Long = 0L,
        allowed: Long = 1_200_000L,
        test: Boolean = false
    ) = GuardRules.shouldBlock(foreground, locked, permitted, used, allowed, test)

    @Test fun twentyMinuteThresholdBlocks() {
        assertFalse(blocks(used = 1_199_999L))
        assertTrue(blocks(used = 1_200_000L))
        assertTrue(blocks(used = 1_800_000L))
    }

    @Test fun solvingExtendsAllowanceByFiveMinutes() {
        assertFalse(blocks(used = 1_210_000L, allowed = 1_500_000L))
        assertTrue(blocks(used = 1_500_000L, allowed = 1_500_000L))
    }

    @Test fun missingUsagePermissionFailsClosed() {
        assertTrue(blocks(permitted = false))
        assertTrue(blocks(used = -1L))
    }

    @Test fun explicitTestAlwaysBlocksInstagramWithoutAddingTime() {
        assertTrue(blocks(test = true, used = 0L))
        assertFalse(blocks(test = true, foreground = false))
    }

    @Test fun neverDisplaysOverOtherAppsOrLockscreen() {
        assertFalse(blocks(foreground = false, used = 3_000_000L))
        assertFalse(blocks(locked = true, used = 3_000_000L))
    }
}
