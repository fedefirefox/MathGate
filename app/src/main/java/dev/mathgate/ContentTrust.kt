package dev.mathgate

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.net.ssl.HttpsURLConnection

/**
 * Signed, data-only updates. The P-256 public key is pinned in the installed
 * APK; the PRIVATE key is held by the repository owner, never on GitHub.
 *
 * An attacker able to change GitHub files cannot create acceptable updates
 * without the owner's separate signing key. This does not protect a device
 * whose APK or operating system has already been compromised.
 */
object ContentTrust {
    const val PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfDZDJTdMOZ+ZoOK4i6YxqUhajzppprbCvj4phfmNrJ/ytnLCBLaWnznkH5PhFkeNfTmcX7YB0DTg3itn/sRzsA=="

    const val QUESTION_PATH = "app/src/main/assets/problem_bank.json"
    const val CAT_PATH = "app/src/main/assets/cat_memes.json"
    private const val IMAGE_PREFIX = "app/src/main/assets/memes/"
    private const val REPOSITORY = "fedefirefox/MathGate"
    private const val RAW_ROOT = "https://raw.githubusercontent.com/" +
        REPOSITORY + "/main/"
    private const val MANIFEST_PATH = "security/asset_manifest.json"
    private const val SIGNATURE_PATH = "security/asset_manifest.sig"
    private const val MAX_MANIFEST = 16 * 1024
    private const val MAX_SIGNATURE = 2048
    private const val MANIFEST_CACHE = "mathgate_signed_manifest.json"
    private const val SIGNATURE_CACHE = "mathgate_signed_manifest.sig"
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val SAFE_IMAGE = Regex("cat_[0-9]{2}\\.webp")

    data class Bundle(
        val manifestBytes: ByteArray,
        val signatureBytes: ByteArray,
        val revision: Int,
        val hashes: Map<String, String>
    ) {
        fun matches(path: String, content: ByteArray): Boolean {
            val expected = hashes[path] ?: return false
            val digest = MessageDigest.getInstance("SHA-256").digest(content)
            val actual = digest.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            return MessageDigest.isEqual(
                expected.toByteArray(Charsets.US_ASCII),
                actual.toByteArray(Charsets.US_ASCII)
            )
        }
    }

    private fun validPath(path: String): Boolean =
        path == QUESTION_PATH || path == CAT_PATH ||
            (path.startsWith(IMAGE_PREFIX) &&
                SAFE_IMAGE.matches(path.removePrefix(IMAGE_PREFIX)))

    private fun verify(manifest: ByteArray, signatureText: ByteArray): Bundle {
        require(manifest.size in 64..MAX_MANIFEST) { "Invalid manifest size" }
        require(signatureText.size in 32..MAX_SIGNATURE) { "Invalid signature size" }
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_BASE64, Base64.NO_WRAP))
        )
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(publicKey)
        sig.update(manifest)
        val rawSignature = Base64.decode(
            signatureText.toString(Charsets.US_ASCII).trim(),
            Base64.NO_WRAP
        )
        require(sig.verify(rawSignature)) { "Owner signature is invalid" }

        val payload = JSONObject(manifest.toString(Charsets.UTF_8))
        require(payload.getInt("schema_version") == 1)
        require(payload.getString("algorithm") == "SHA256withECDSA-P256")
        require(payload.getString("repository") == REPOSITORY)
        val revision = payload.getInt("revision")
        require(revision >= 1)
        val data = payload.getJSONObject("files")
        require(data.length() in 4..60) { "Invalid signed file list" }
        val hashes = HashMap<String, String>()
        val names = data.keys()
        while (names.hasNext()) {
            val path = names.next()
            val digest = data.getString(path)
            require(validPath(path) && SHA256.matches(digest)) {
                "Unsafe signed path or checksum"
            }
            hashes[path] = digest
        }
        require(hashes.containsKey(QUESTION_PATH))
        require(hashes.containsKey(CAT_PATH))
        return Bundle(manifest, signatureText, revision, hashes)
    }

    /** Offline integrity validation before accepting cached GitHub data. */
    fun cached(context: Context): Bundle? = try {
        val manifest = File(context.filesDir, MANIFEST_CACHE)
        val signature = File(context.filesDir, SIGNATURE_CACHE)
        if (!manifest.isFile || !signature.isFile ||
            manifest.length() > MAX_MANIFEST ||
            signature.length() > MAX_SIGNATURE) null
        else verify(manifest.readBytes(), signature.readBytes())
    } catch (_: Exception) { null }

    fun download(path: String, maximum: Int): ByteArray {
        require(path == MANIFEST_PATH || path == SIGNATURE_PATH || validPath(path))
        require(maximum in 1..(512 * 1024))
        val connection = URL(RAW_ROOT + path).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/octet-stream")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub HTTP " + connection.responseCode
            }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= maximum) { "Data size limit exceeded" }
                    out.write(buffer, 0, count)
                }
            }
            return out.toByteArray()
        } finally { connection.disconnect() }
    }

    /** Never trust remote JSON without this pinned-key check. */
    fun fetch(): Bundle = verify(
        download(MANIFEST_PATH, MAX_MANIFEST),
        download(SIGNATURE_PATH, MAX_SIGNATURE)
    )

    @Synchronized
    fun save(context: Context, newBundle: Bundle) {
        val current = cached(context)
        require(current == null || newBundle.revision >= current.revision) {
            "Signed manifest rollback is blocked"
        }
        val manifest = File(context.filesDir, MANIFEST_CACHE)
        val signature = File(context.filesDir, SIGNATURE_CACHE)
        val nextManifest = File(context.filesDir, MANIFEST_CACHE + ".tmp")
        val nextSignature = File(context.filesDir, SIGNATURE_CACHE + ".tmp")
        nextManifest.writeBytes(newBundle.manifestBytes)
        nextSignature.writeBytes(newBundle.signatureBytes)
        if (!nextSignature.renameTo(signature)) {
            nextSignature.copyTo(signature, overwrite = true)
            nextSignature.delete()
        }
        if (!nextManifest.renameTo(manifest)) {
            nextManifest.copyTo(manifest, overwrite = true)
            nextManifest.delete()
        }
    }
}
