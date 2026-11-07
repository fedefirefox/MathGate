package dev.mathgate

import android.content.Context

/** Local-only diagnostic telemetry; no remote analytics. */
object GateDiagnostics {
    private const val PREFS = "mathgate_guard_health"
    private fun p(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun connected(c: Context) {
        p(c).edit()
            .putLong("connectedAt", System.currentTimeMillis())
            .putString("lastAction", "Accessibility service connected")
            .apply()
        heartbeat(c)
    }

    fun heartbeat(c: Context) {
        p(c).edit().putLong("lastHeartbeat", System.currentTimeMillis()).apply()
    }

    fun instagramDetected(c: Context) {
        p(c).edit().putLong("instagramDetectedAt", System.currentTimeMillis()).apply()
    }

    fun recordOverlay(c: Context) {
        val prefs = p(c)
        prefs.edit()
            .putLong("overlayShownAt", System.currentTimeMillis())
            .putInt("overlayCount", prefs.getInt("overlayCount", 0) + 1)
            .putString("lastAction", "Instagram blocked with accessibility overlay")
            .apply()
    }

    fun recordError(c: Context, description: String) {
        p(c).edit()
            .putString("lastAction", description.take(160))
            .putLong("lastErrorAt", System.currentTimeMillis())
            .apply()
    }

    fun lastAction(c: Context): String =
        p(c).getString("lastAction", "No service activity recorded").orEmpty()

    fun lastHeartbeatMs(c: Context): Long = p(c).getLong("lastHeartbeat", 0L)
    fun lastInstagramAt(c: Context): Long = p(c).getLong("instagramDetectedAt", 0L)
    fun lastOverlayAt(c: Context): Long = p(c).getLong("overlayShownAt", 0L)
    fun lastErrorAt(c: Context): Long = p(c).getLong("lastErrorAt", 0L)
    fun overlayCount(c: Context): Int = p(c).getInt("overlayCount", 0)

    fun serviceResponsive(c: Context): Boolean {
        val time = lastHeartbeatMs(c)
        val age = System.currentTimeMillis() - time
        return time > 0L && age in 0L..8_000L
    }

    fun startLockTest(c: Context) {
        p(c).edit()
            .putLong("testUntil", System.currentTimeMillis() + 90_000L)
            .putString("lastAction", "90-second blocking test armed")
            .apply()
    }

    fun isLockTestActive(c: Context): Boolean =
        System.currentTimeMillis() < p(c).getLong("testUntil", 0L)

    fun endLockTest(c: Context) {
        p(c).edit().remove("testUntil").apply()
    }

    fun elapsedLabel(timestamp: Long): String {
        if (timestamp <= 0L) return "never"
        val difference = System.currentTimeMillis() - timestamp
        if (difference < 0L) return "clock changed"
        val seconds = difference / 1000L
        return when {
            seconds < 60L -> seconds.toString() + "s ago"
            seconds < 3_600L -> (seconds / 60L).toString() + "m ago"
            else -> (seconds / 3_600L).toString() + "h ago"
        }
    }
}
