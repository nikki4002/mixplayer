package com.matheus.mixplayer

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

data class ProfileTrack(val title: String, val artist: String, val score: Int)

data class Profile(val name: String, val tracks: List<ProfileTrack>)

enum class Origin { ME, FRIEND, BOTH }

data class MixItem(
    val title: String,
    val artist: String,
    val origin: Origin,
    /** Música correspondente na biblioteca deste celular (null = não está aqui). */
    val song: Song?,
)

object MixEngine {
    const val PREFIX = "MIX1:"
    private const val MAX_TRACKS = 60

    /** Perfil de gosto = músicas mais ouvidas (cada reprodução vale 1, cada favorito vale 5). */
    fun buildProfile(
        name: String,
        library: List<Song>,
        plays: Map<String, Int>,
        favs: Set<String>,
    ): Profile {
        val scored = library
            .distinctBy { it.key }
            .map { it to (plays[it.key] ?: 0) + if (it.key in favs) 5 else 0 }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_TRACKS)
        return Profile(name, scored.map { ProfileTrack(it.first.title, it.first.artist, it.second) })
    }

    fun encode(profile: Profile): String {
        val arr = JSONArray()
        profile.tracks.forEach {
            arr.put(JSONArray().put(it.title).put(it.artist).put(it.score))
        }
        val json = JSONObject().put("v", 1).put("n", profile.name).put("t", arr).toString()
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.encodeToString(
            bos.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    /** Acha o código "MIX1:..." dentro de um texto colado (mesmo com mensagem em volta). */
    fun extract(text: String): String? {
        val i = text.indexOf(PREFIX)
        if (i < 0) return null
        val code = text.substring(i).takeWhile { !it.isWhitespace() }
        return if (code.length > PREFIX.length) code else null
    }

    fun decode(text: String): Profile? {
        val code = extract(text) ?: return null
        return try {
            val bytes = Base64.decode(
                code.removePrefix(PREFIX),
                Base64.URL_SAFE or Base64.NO_WRAP,
            )
            val json = GZIPInputStream(bytes.inputStream()).use {
                String(it.readBytes(), Charsets.UTF_8)
            }
            val o = JSONObject(json)
            val arr = o.getJSONArray("t")
            val tracks = (0 until arr.length()).map {
                val t = arr.getJSONArray(it)
                ProfileTrack(t.getString(0), t.getString(1), t.getInt(2))
            }
            Profile(o.optString("n", "Amigo"), tracks)
        } catch (e: Exception) {
            null
        }
    }

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / minOf(a.size, b.size)
    }

    /** Compatibilidade de 0 a 100: 60% artistas em comum + 40% músicas em comum. */
    fun compat(a: Profile, b: Profile): Int {
        val artistsA = a.tracks.map { normalize(it.artist) }.toSet()
        val artistsB = b.tracks.map { normalize(it.artist) }.toSet()
        val keysA = a.tracks.map { keyOf(it.title, it.artist) }.toSet()
        val keysB = b.tracks.map { keyOf(it.title, it.artist) }.toSet()
        val raw = 0.6 * overlap(artistsA, artistsB) + 0.4 * overlap(keysA, keysB)
        return (raw * 100).roundToInt().coerceIn(0, 100)
    }

    /** Músicas dos dois primeiro, depois alterna uma sua / uma do amigo. */
    fun buildMix(me: Profile, friend: Profile, library: List<Song>): List<MixItem> {
        val byKey = library.associateBy { it.key }
        val meKeys = me.tracks.map { keyOf(it.title, it.artist) }.toSet()
        val frKeys = friend.tracks.map { keyOf(it.title, it.artist) }.toSet()

        fun item(t: ProfileTrack, origin: Origin) =
            MixItem(t.title, t.artist, origin, byKey[keyOf(t.title, t.artist)])

        val both = me.tracks.filter { keyOf(it.title, it.artist) in frKeys }.map { item(it, Origin.BOTH) }
        val onlyMe = me.tracks.filter { keyOf(it.title, it.artist) !in frKeys }.map { item(it, Origin.ME) }
        val onlyFr = friend.tracks.filter { keyOf(it.title, it.artist) !in meKeys }.map { item(it, Origin.FRIEND) }

        val mixed = mutableListOf<MixItem>()
        val n = maxOf(onlyMe.size, onlyFr.size)
        for (i in 0 until n) {
            if (i < onlyMe.size) mixed += onlyMe[i]
            if (i < onlyFr.size) mixed += onlyFr[i]
        }
        return both + mixed
    }
}
