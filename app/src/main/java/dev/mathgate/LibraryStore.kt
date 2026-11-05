package dev.mathgate

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Data-only GitHub updates. No scripts, HTML, executable code, or arbitrary URLs
 * are loaded from the repository. All mathematics is rendered locally as MathML.
 */
object LibraryStore {
    private const val FILE_NAME = "mathgate_problem_bank.json"
    private const val ASSET = "problem_bank.json"
    private const val MAX_BYTES = 256 * 1024
    private const val URL_RAW =
        "https://raw.githubusercontent.com/fedefirefox/MathGate/main/app/src/main/assets/problem_bank.json"
    private val VALID_LEVELS = setOf("foundation", "standard", "advanced")

    data class Bank(val revision: Int, val problems: List<CalculusProblem>)

    private fun validate(content: String): Bank {
        require(content.length <= MAX_BYTES) { "Library is too large" }
        val json = JSONObject(content)
        require(json.getInt("schema_version") == 1) { "Unsupported library format" }
        val revision = json.getInt("revision")
        require(revision > 0) { "Invalid revision" }
        val entries = json.getJSONArray("problems")
        require(entries.length() in 12..500) { "Invalid number of problems" }
        val ids = mutableSetOf<String>()
        val parsed = mutableListOf<CalculusProblem>()
        for (i in 0 until entries.length()) {
            val item = entries.getJSONObject(i)
            val id = item.getString("id")
            val level = item.getString("level")
            val topic = item.getString("topic")
            val prompt = item.getString("prompt")
            val latex = item.getString("latex")
            val answer = item.getDouble("answer")
            val tolerance = item.getDouble("tolerance")
            val hint = item.getString("hint")
            require(id.length in 3..80 && ids.add(id)) { "Duplicate or invalid ID" }
            require(level in VALID_LEVELS) { "Unknown difficulty" }
            require(topic.length in 3..100 && prompt.length in 5..1000)
            require(latex.length in 3..1500 && hint.length in 3..500)
            require(latex.count { it == '{' } == latex.count { it == '}' })
            require(answer.isFinite() && tolerance.isFinite() && tolerance > 0.0 && tolerance <= 1.0)
            parsed.add(
                CalculusProblem(
                    topic = topic,
                    prompt = prompt,
                    expected = answer,
                    hint = hint,
                    latex = latex,
                    tolerance = tolerance,
                    level = level,
                    id = id
                )
            )
        }
        require(VALID_LEVELS.all { level -> parsed.count { it.level == level } >= 5 })
        return Bank(revision, parsed)
    }

    private fun packaged(context: Context): Bank {
        val raw = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return validate(raw)
    }

    fun current(context: Context): Bank {
        val bundled = packaged(context)
        val cached = File(context.filesDir, FILE_NAME)
        if (!cached.exists() || cached.length() > MAX_BYTES) return bundled
        return try {
            val downloaded = validate(cached.readText(Charsets.UTF_8))
            if (downloaded.revision > bundled.revision) downloaded else bundled
        } catch (_: Exception) {
            bundled
        }
    }

    fun choose(context: Context, seed: Int, level: String): CalculusProblem? {
        return try {
            val candidates = current(context).problems.filter { it.level == level }
            if (candidates.isEmpty()) null
            else candidates[((seed.toLong() and 0x7fffffffL) % candidates.size.toLong()).toInt()]
        } catch (_: Exception) {
            null
        }
    }

    fun count(context: Context): Int = try {
        current(context).problems.size
    } catch (_: Exception) { 0 }

    fun revision(context: Context): Int = try {
        current(context).revision
    } catch (_: Exception) { 0 }

    /** Runs on a worker thread and calls back with a user-visible result. */
    fun checkForUpdates(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        Thread {
            var connection: HttpsURLConnection? = null
            try {
                val source = URL(URL_RAW)
                connection = source.openConnection() as HttpsURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "GitHub HTTP " + connection.responseCode
                }
                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buf = ByteArray(4096)
                    while (true) {
                        val count = input.read(buf)
                        if (count < 0) break
                        require(out.size() + count <= MAX_BYTES) {
                            "GitHub file is too large"
                        }
                        out.write(buf, 0, count)
                    }
                }
                val raw = out.toByteArray().toString(Charsets.UTF_8)
                val downloaded = validate(raw)
                val current = current(app)
                if (downloaded.revision <= current.revision) {
                    callback("Question library is up to date · revision " + current.revision +
                        " · " + current.problems.size + " problems")
                } else {
                    val temp = File(app.filesDir, FILE_NAME + ".tmp")
                    val destination = File(app.filesDir, FILE_NAME)
                    temp.writeText(raw, Charsets.UTF_8)
                    if (!temp.renameTo(destination)) {
                        // Atomic rename ordinarily succeeds within app-private storage.
                        temp.copyTo(destination, overwrite = true)
                        temp.delete()
                    }
                    callback("Updated to revision " + downloaded.revision + " · " +
                        downloaded.problems.size + " problems available")
                }
            } catch (err: Exception) {
                callback("Couldn't download questions: " +
                    (err.message ?: "network unavailable") + ". Offline problems remain available.")
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
