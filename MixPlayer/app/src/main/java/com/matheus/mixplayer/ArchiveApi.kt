package com.matheus.mixplayer

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArchiveItem(val id: String, val title: String, val creator: String)

data class ArchiveFile(val name: String, val title: String, val sizeBytes: Long)

/** Busca e baixa músicas livres do Internet Archive (archive.org). Chamar fora da thread principal. */
object ArchiveApi {
    private const val UA = "MixPlayer/1.0 (Android)"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", UA)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun first(v: Any?): String? = when (v) {
        null, JSONObject.NULL -> null
        is JSONArray -> if (v.length() > 0) v.optString(0) else null
        else -> v.toString()
    }

    /** Só áudios com licença Creative Commons ou de coleções de música livre. */
    fun search(query: String): List<ArchiveItem> {
        val clean = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        val q = "($clean) AND mediatype:audio AND " +
            "(licenseurl:*creativecommons* OR collection:netlabels OR collection:freemusicarchive)"
        val url = "https://archive.org/advancedsearch.php?q=" + enc(q) +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator" +
            "&sort%5B%5D=downloads+desc&rows=30&page=1&output=json"
        val docs = JSONObject(get(url)).getJSONObject("response").getJSONArray("docs")
        return (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            val id = d.getString("identifier")
            ArchiveItem(
                id = id,
                title = first(d.opt("title")) ?: id,
                creator = first(d.opt("creator")) ?: "Artista desconhecido",
            )
        }
    }

    fun files(id: String): List<ArchiveFile> {
        val arr = JSONObject(get("https://archive.org/metadata/" + enc(id)))
            .optJSONArray("files") ?: return emptyList()
        val out = mutableListOf<ArchiveFile>()
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            val name = f.optString("name")
            if (!name.endsWith(".mp3", ignoreCase = true)) continue
            val title = f.optString("title").ifBlank {
                name.substringAfterLast('/').substringBeforeLast('.')
            }
            out += ArchiveFile(name, title, f.optString("size").toLongOrNull() ?: 0L)
            if (out.size >= 100) break
        }
        return out
    }

    /** Baixa direto para Músicas/MixPlayer, já registrado na biblioteca do celular. */
    fun download(context: Context, id: String, file: ArchiveFile, artist: String): Boolean {
        val path = file.name.split("/").joinToString("/") { enc(it).replace("+", "%20") }
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val safe = file.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100).ifBlank { "musica" }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safe.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.TITLE, file.title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MixPlayer")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            val conn = URL("https://archive.org/download/${enc(id)}/$path")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", UA)
            try {
                conn.inputStream.use { input ->
                    resolver.openOutputStream(uri)!!.use { out -> input.copyTo(out) }
                }
            } finally {
                conn.disconnect()
            }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
