package dev.mathgate

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * A normal APK cannot install code updates silently.
 * Checks official GitHub Releases and opens the release in the browser.
 * APK updates require a consistent signing certificate.
 */
object ReleaseChecker {
    private const val ENDPOINT =
        "https://api.github.com/repos/fedefirefox/MathGate/releases/latest"
    private const val RELEASES_URL =
        "https://github.com/fedefirefox/MathGate/releases"
    private const val BUILDS_URL =
        "https://github.com/fedefirefox/MathGate/actions"

    fun check(context: Context, callback: (String, String?) -> Unit) {
        Thread {
            var con: HttpsURLConnection? = null
            try {
                con = URL(ENDPOINT).openConnection() as HttpsURLConnection
                con.connectTimeout = 8_000
                con.readTimeout = 8_000
                con.setRequestProperty("Accept", "application/vnd.github+json")
                when (con.responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> callback(
                        "No public APK release has been published yet. Builds are on GitHub Actions.",
                        BUILDS_URL
                    )
                    HttpURLConnection.HTTP_OK -> {
                        val payload = con.inputStream.bufferedReader().use { it.readText() }
                        val release = JSONObject(payload)
                        val tag = release.optString("tag_name", "")
                        val link = release.optString("html_url", "")
                        require(link.startsWith(RELEASES_URL + "/tag/"))
                        val installed = context.packageManager.getPackageInfo(
                            context.packageName, 0
                        ).versionName.orEmpty()
                        if (parseVersion(tag) > parseVersion(installed)) {
                            callback("App update " + tag + " available on GitHub. " +
                                "Android will ask you to approve installation.", link)
                        } else {
                            callback("Installed version " + installed +
                                " is current. Latest published release: " + tag, link)
                        }
                    }
                    else -> callback("GitHub returned HTTP " + con.responseCode, RELEASES_URL)
                }
            } catch (e: Exception) {
                callback("Could not check GitHub releases: " +
                    (e.message ?: "offline"), RELEASES_URL)
            } finally { con?.disconnect() }
        }.start()
    }

    private fun parseVersion(value: String): Long {
        val m = Regex("""v?(\d+)\.(\d+)\.(\d+)""").find(value) ?: return 0L
        return (m.groupValues[1].toLongOrNull() ?: 0) * 1_000_000L +
            (m.groupValues[2].toLongOrNull() ?: 0) * 1_000L +
            (m.groupValues[3].toLongOrNull() ?: 0)
    }

    fun open(context: Context, url: String) {
        if (!url.startsWith(RELEASES_URL) && url != BUILDS_URL) return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
