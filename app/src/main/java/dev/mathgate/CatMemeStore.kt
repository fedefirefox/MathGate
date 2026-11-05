package dev.mathgate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

/**
 * A deterministic daily meme with offline AI-generated image assets and a
 * versioned, data-only catalogue hosted in the user's GitHub repository.
 * No automatic generative inference happens on the phone.
 */
object CatMemeStore {
    private const val FILE = "mathgate_cat_memes.json"
    private const val SOURCE = "cat_memes.json"
    private const val BASE =
        "https://raw.githubusercontent.com/fedefirefox/MathGate/main/app/src/main/assets/"
    private const val MAX_CATALOGUE_BYTES = 64 * 1024
    private const val MAX_IMAGE_BYTES = 350 * 1024
    private val SAFE_NAME = Regex("cat_[0-9]{2}\\.webp")

    data class Meme(val id: String, val image: String,
                    val title: String, val message: String)
    data class Catalogue(val revision: Int, val memes: List<Meme>)

    private fun parse(raw: String): Catalogue {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_CATALOGUE_BYTES)
        val data = JSONObject(raw)
        require(data.getInt("schema_version") == 1)
        val version = data.getInt("revision")
        require(version > 0)
        val arr = data.getJSONArray("memes")
        require(arr.length() in 2..100)
        val seen = mutableSetOf<String>()
        val list = ArrayList<Meme>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("id")
            val img = obj.getString("image")
            val title = obj.getString("title")
            val message = obj.getString("message")
            require(id.length in 2..40 && seen.add(id))
            require(SAFE_NAME.matches(img))
            require(title.length in 4..100)
            require(message.length in 8..350)
            list.add(Meme(id, img, title, message))
        }
        return Catalogue(version, list)
    }

    private fun included(context: Context): Catalogue =
        parse(context.assets.open(SOURCE).bufferedReader(Charsets.UTF_8).use { it.readText() })

    fun current(context: Context): Catalogue {
        val packaged = included(context)
        val saved = File(context.filesDir, FILE)
        if (!saved.isFile() || saved.length() > MAX_CATALOGUE_BYTES) return packaged
        return try {
            val new = parse(saved.readText(Charsets.UTF_8))
            if (new.revision > packaged.revision &&
                new.memes.all { imageAvailable(context, it.image) }) new else packaged
        } catch (_: Exception) { packaged }
    }

    fun daily(context: Context): Meme {
        val catalogue = current(context)
        val settings = context.getSharedPreferences("mathgate_cat_of_day", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val remembered = if (settings.getString("day", null) == today)
            settings.getString("id", null) else null
        val selected = catalogue.memes.firstOrNull { it.id == remembered } ?: run {
            val position = Math.floorMod(LocalDate.now().toEpochDay(), catalogue.memes.size.toLong())
            catalogue.memes[position.toInt()]
        }
        settings.edit().putString("day", today).putString("id", selected.id).apply()
        return selected
    }

    private fun local(context: Context, image: String) =
        File(File(context.filesDir, "cat_art"), image)

    private fun imageAvailable(context: Context, name: String): Boolean {
        if (!SAFE_NAME.matches(name)) return false
        if (local(context, name).isFile) return true
        return try {
            context.assets.open("memes/" + name).use { true }
        } catch (_: Exception) { false }
    }

    fun bitmap(context: Context, meme: Meme): Bitmap? {
        if (!SAFE_NAME.matches(meme.image)) return null
        return try {
            val local = local(context, meme.image)
            if (local.isFile) {
                BitmapFactory.decodeFile(local.absolutePath)
            } else {
                context.assets.open("memes/" + meme.image).use {
                    BitmapFactory.decodeStream(it)
                }
            }
        } catch (_: Exception) { null }
    }

    private fun request(url: String, maximum: Int): ByteArray {
        val con = URL(url).openConnection() as HttpsURLConnection
        try {
            con.connectTimeout = 8_000
            con.readTimeout = 8_000
            con.instanceFollowRedirects = false
            con.setRequestProperty("Accept", "application/octet-stream, application/json")
            require(con.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub responded with HTTP " + con.responseCode
            }
            val out = ByteArrayOutputStream()
            con.inputStream.use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= maximum) { "GitHub file exceeds size limit" }
                    out.write(buffer, 0, n)
                }
            }
            return out.toByteArray()
        } finally { con.disconnect() }
    }

    private fun downloadImage(context: Context, image: String) {
        if (imageAvailable(context, image)) return
        val bytes = request(BASE + "memes/" + image, MAX_IMAGE_BYTES)
        require(bytes.size >= 128)
        require(bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
        require(bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()))
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth in 64..1600 && opts.outHeight in 64..1600)
        val folder = File(context.filesDir, "cat_art")
        require(folder.isDirectory || folder.mkdirs())
        val temp = File(folder, image + ".tmp")
        temp.writeBytes(bytes)
        val dest = File(folder, image)
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
    }

    /** Download future AI-generated meme images and captions as DATA; never code. */
    fun checkForUpdates(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                val raw = request(BASE + SOURCE, MAX_CATALOGUE_BYTES).toString(Charsets.UTF_8)
                val incoming = parse(raw)
                val existing = current(app)
                if (incoming.revision <= existing.revision) {
                    callback("Cat memes current: revision " + existing.revision +
                        " · " + existing.memes.size + " daily captions")
                    return@Thread
                }
                val names = incoming.memes.map { it.image }.distinct()
                require(names.size <= 30) { "Too many artwork files in one pack" }
                for (image in names) downloadImage(app, image)
                val temp = File(app.filesDir, FILE + ".tmp")
                temp.writeText(raw, Charsets.UTF_8)
                val dest = File(app.filesDir, FILE)
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
                callback("Cat meme pack updated: revision " + incoming.revision +
                    " · " + incoming.memes.size + " memes")
            } catch (e: Exception) {
                callback("Meme update failed (" + (e.message ?: "offline") +
                    "). Bundled cat memes still work.")
            }
        }.start()
    }
}
