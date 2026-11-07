package dev.mathgate

/**
 * Pure safety decision: guard always blocks an Instagram foreground window
 * when screen-time access is missing, when time expired, or during an explicit
 * temporary lock test. No bonus time is granted by test mode.
 */
object GuardRules {
    fun shouldBlock(
        instagramForeground: Boolean,
        screenLocked: Boolean,
        usageGranted: Boolean,
        usedMs: Long,
        allowanceMs: Long,
        testActive: Boolean
    ): Boolean {
        if (!instagramForeground || screenLocked) return false
        if (testActive) return true
        if (!usageGranted || usedMs < 0L) return true
        return usedMs >= allowanceMs
    }
}
