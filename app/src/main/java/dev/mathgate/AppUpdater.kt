package dev.mathgate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * One-tap update preparation:
 * fetch official GitHub release metadata -> HTTPS download -> validate size,
 * SHA-256 and SAME Android signing certificate -> launch system installer.
 *
 * Android still requires explicit user approval, and different APK signers
 * CANNOT replace the installed application. No silent installation.
 */
object AppUpdater {
    private const val API =
        "https://api.github.com/repos/fedefirefox/MathGate/releases/latest"
    private const val DOWNLOAD_BASE =
        "https://github.com/fedefirefox/MathGate/releases/download/"
    private const val MAX_APK_BYTES = 32L * 1024 * 1024
    private val TAG_PATTERN = Regex("v[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")
    private val HASH_PATTERN = Regex("[a-f0-9]{64}")

    data class Prepared(
        val tag: String,
        val apk: File,
        val sha256: String
    )

    private fun version(v: String): Long {
        val match = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").matchEntire(v)
            ?: return -1L
        val parts = match.groupValues.drop(1).map { it.toLong() }
        return parts[0] * 1_000_000L + parts[1] * 1_000L + parts[2]
    }

    fun downloadNewest(context: Context, callback: (Result<Prepared>) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                callback(Result.success(downloadAndVerify(app)))
            } catch (t: Exception) {
                callback(Result.failure(t))
            }
        }.start()
    }

    private fun downloadAndVerify(context: Context): Prepared {
        val apiConnection = URL(API).openConnection() as HttpsURLConnection
        val metadata: JSONObject
        try {
            apiConnection.connectTimeout = 12000
            apiConnection.readTimeout = 12000
            apiConnection.setRequestProperty("Accept", "application/vnd.github+json")
            require(apiConnection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub API returned HTTP " + apiConnection.responseCode
            }
            val data = apiConnection.inputStream.bufferedReader().use { it.readText() }
            require(data.length < 100_000) { "Unexpected release metadata size" }
            metadata = JSONObject(data)
        } finally {
            apiConnection.disconnect()
        }

        val tag = metadata.getString("tag_name")
        require(TAG_PATTERN.matches(tag)) { "Unsupported GitHub release tag" }
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        require(version(tag) > version(installed.versionName.orEmpty())) {
            "MathGate is already up to date (v" + installed.versionName.orEmpty() + ")."
        }

        val expectedName = "MathGate-" + tag + ".apk"
        val assets = metadata.getJSONArray("assets")
        var downloadUrl: String? = null
        var expectedHash: String? = null
        var expectedSize = -1L
        for (n in 0 until assets.length()) {
            val item = assets.getJSONObject(n)
            if (item.optString("name") != expectedName) continue
            downloadUrl = item.getString("browser_download_url")
            val sha = item.optString("digest")
            require(sha.startsWith("sha256:"))
            expectedHash = sha.removePrefix("sha256:").lowercase()
            expectedSize = item.getLong("size")
            break
        }
        val url = downloadUrl ?: error("No installable APK in the latest GitHub release")
        val digest = expectedHash ?: error("GitHub APK is missing its SHA-256 digest")
        require(HASH_PATTERN.matches(digest))
        require(expectedSize in 100_000..MAX_APK_BYTES)
        require(url == DOWNLOAD_BASE + tag + "/" + expectedName) {
            "Unexpected download URL; refusing non-MathGate host"
        }

        val folder = File(context.filesDir, "updates")
        require(folder.isDirectory || folder.mkdirs())
        val temporary = File(folder, "candidate.tmp")
        val destination = File(folder, "MathGate-update.apk")
        val connection = URL(url).openConnection() as HttpsURLConnection
        val md = MessageDigest.getInstance("SHA-256")
        var count = 0L
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "APK download HTTP " + connection.responseCode
            }
            require(connection.url.protocol == "https") { "Insecure redirect refused" }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= MAX_APK_BYTES) { "APK exceeds maximum size" }
                        md.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            temporary.delete()
            throw e
        } finally {
            connection.disconnect()
        }

        try {
            require(count == expectedSize) { "APK length differs from GitHub metadata" }
            val actualHash = md.digest().joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
            require(MessageDigest.isEqual(
                digest.toByteArray(Charsets.US_ASCII),
                actualHash.toByteArray(Charsets.US_ASCII)
            )) { "APK checksum mismatch; update rejected" }
            require(temporary.renameTo(destination) || run {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
                true
            }) { "Could not store the APK" }
            verifyInstalledSigner(context, destination)
            return Prepared(tag, destination, digest)
        } catch (e: Exception) {
            temporary.delete()
            destination.delete()
            throw e
        }
    }

    private fun verifyInstalledSigner(context: Context, candidate: File) {
        val pm = context.packageManager
        val current = pm.getPackageInfo(
            context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
        )
        val incoming = pm.getPackageArchiveInfo(
            candidate.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: error("Downloaded file is not a valid Android APK")
        require(incoming.packageName == context.packageName) {
            "This APK belongs to a different Android package"
        }
        require(incoming.longVersionCode > current.longVersionCode) {
            "Downloaded APK is not newer than the installed version"
        }
        val a = current.signingInfo?.apkContentsSigners
            ?: error("Could not inspect installed signing certificate")
        val b = incoming.signingInfo?.apkContentsSigners
            ?: error("Could not inspect update signing certificate")
        require(a.size == b.size && a.isNotEmpty() &&
            a.indices.all { i ->
                MessageDigest.isEqual(a[i].toByteArray(), b[i].toByteArray())
            }
        ) {
            "Update signing key differs from the installed APK. Android will NOT " +
                "upgrade it in place. One-time reinstall is required until a " +
                "permanent release signing key is configured. No data was changed."
        }
    }

    fun mayInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:" + activity.packageName)
            }
        )
    }

    fun launchInstaller(activity: Activity, prepared: Prepared) {
        require(prepared.apk.isFile && prepared.apk.length() in 100_000..MAX_APK_BYTES)
        // Recheck the signing certificate on the current installed package.
        verifyInstalledSigner(activity, prepared.apk)
        val uri = FileProvider.getUriForFile(
            activity,
            activity.packageName + ".updates",
            prepared.apk
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}
