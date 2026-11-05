package dev.mathgate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Daily AI-created cat illustrations and captions.
 *
 * The APK's bundled assets are trusted as local code assets. GitHub catalogue
 * and artwork updates MUST match a manifest signed by the owner's offline key.
 */
object CatMemeStore {
    private const val FILE = "mathgate_cat_memes.json"
    private const val SOURCE = "cat_memes.json"
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
            val image = obj.getString("image")
            val title = obj.getString("title")
            val message = obj.getString("message")
            require(id.length in 2..40 && seen.add(id))
            require(SAFE_NAME.matches(image))
            require(title.length in 4..100 && message.length in 8..350)
            list.add(Meme(id, image, title, message))
        }
        return Catalogue(version, list)
    }

    private fun included(context: Context): Catalogue =
        parse(context.assets.open(SOURCE).bufferedReader(Charsets.UTF_8).use { it.readText() })

    private fun cachePath(context: Context) = File(context.filesDir, FILE)

    private fun local(context: Context, filename: String) =
        File(File(context.filesDir, "cat_art"), filename)

    private fun bundledBytes(context: Context, filename: String): ByteArray? = try {
        if (!SAFE_NAME.matches(filename)) null
        else context.assets.open("memes/" + filename).use { it.readBytes() }
    } catch (_: Exception) { null }

    private fun signedImageBytes(
        context: Context, filename: String, signed: ContentTrust.Bundle
    ): ByteArray? {
        if (!SAFE_NAME.matches(filename)) return null
        val path = "app/src/main/assets/memes/" + filename
        val localFile = local(context, filename)
        if (localFile.isFile && localFile.length() in 128..MAX_IMAGE_BYTES.toLong()) {
            val bytes = try { localFile.readBytes() } catch (_: Exception) { null }
            if (bytes != null && signed.matches(path, bytes)) return bytes
        }
        val bundled = bundledBytes(context, filename)
        return if (bundled != null && signed.matches(path, bundled)) bundled else null
    }

    fun current(context: Context): Catalogue {
        val packaged = included(context)
        val saved = cachePath(context)
        if (!saved.isFile() || saved.length() > MAX_CATALOGUE_BYTES) return packaged
        return try {
            val signed = ContentTrust.cached(context) ?: return packaged
            val content = saved.readBytes()
            if (!signed.matches(ContentTrust.CAT_PATH, content)) return packaged
            val new = parse(content.toString(Charsets.UTF_8))
            if (new.revision > packaged.revision &&
                new.memes.all { signedImageBytes(context, it.image, signed) != null }
            ) new else packaged
        } catch (_: Exception) { packaged }
    }

    fun daily(context: Context): Meme {
        val catalogue = current(context)
        val p = context.getSharedPreferences("mathgate_cat_of_day", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val existing = if (p.getString("day", null) == today)
            p.getString("id", null) else null
        val selected = catalogue.memes.firstOrNull { it.id == existing } ?: run {
            val index = Math.floorMod(
                LocalDate.now().toEpochDay(), catalogue.memes.size.toLong()
            ).toInt()
            catalogue.memes[index]
        }
        p.edit().putString("day", today).putString("id", selected.id).apply()
        return selected
    }

    fun bitmap(context: Context, meme: Meme): Bitmap? {
        if (!SAFE_NAME.matches(meme.image)) return null
        return try {
            val packaged = included(context)
            val active = current(context)
            val downloaded = active.revision > packaged.revision &&
                active.memes.any { it.id == meme.id }
            val bytes = if (downloaded) {
                ContentTrust.cached(context)?.let {
                    signedImageBytes(context, meme.image, it)
                }
            } else {
                bundledBytes(context, meme.image)
            }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (_: Exception) { null }
    }

    private fun downloadImage(
        context: Context, filename: String, signed: ContentTrust.Bundle
    ) {
        if (signedImageBytes(context, filename, signed) != null) return
        val path = "app/src/main/assets/memes/" + filename
        val bytes = ContentTrust.download(path, MAX_IMAGE_BYTES)
        require(signed.matches(path, bytes)) {
            "Cat artwork rejected: invalid owner-signed SHA-256"
        }
        require(bytes.size >= 128)
        require(bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()))
        require(bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 64..1600 && options.outHeight in 64..1600)
        val dir = File(context.filesDir, "cat_art")
        require(dir.isDirectory || dir.mkdirs())
        val temp = File(dir, filename + ".tmp")
        val dest = File(dir, filename)
        temp.writeBytes(bytes)
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
    }

    fun checkForUpdates(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        Thread {
            try {
                val signed = ContentTrust.fetch()
                val bytes = ContentTrust.download(ContentTrust.CAT_PATH, MAX_CATALOGUE_BYTES)
                require(signed.matches(ContentTrust.CAT_PATH, bytes)) {
                    "Cat catalogue rejected: invalid owner signature or checksum"
                }
                val incoming = parse(bytes.toString(Charsets.UTF_8))
                val existing = current(app)
                if (incoming.revision <= existing.revision) {
                    callback("Cat memes current: revision " + existing.revision +
                        " · " + existing.memes.size + " daily captions")
                    return@Thread
                }
                val images = incoming.memes.map { it.image }.distinct()
                require(images.size <= 30)
                for (image in images) downloadImage(app, image, signed)
                val temp = File(app.filesDir, FILE + ".tmp")
                val target = cachePath(app)
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                ContentTrust.save(app, signed)
                callback("Owner-signed cat pack installed: revision " +
                    incoming.revision + " · " + incoming.memes.size + " memes")
            } catch (e: Exception) {
                callback("Cat update NOT installed: " +
                    (e.message ?: "offline") + ". Bundled memes remain safe.")
            }
        }.start()
    }
}
