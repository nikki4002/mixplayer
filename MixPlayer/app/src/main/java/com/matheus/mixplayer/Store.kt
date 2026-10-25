package com.matheus.mixplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda histórico de reprodução, favoritos, nome e o último código de amigo. */
class Store(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("mix_store", Context.MODE_PRIVATE)

    fun plays(): Map<String, Int> {
        val raw = prefs.getString("plays", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getInt(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun addPlay(key: String) {
        val m = plays().toMutableMap()
        m[key] = (m[key] ?: 0) + 1
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("plays", o.toString()).apply()
    }

    fun favs(): Set<String> = (prefs.getStringSet("favs", emptySet()) ?: emptySet()).toSet()

    fun toggleFav(key: String): Set<String> {
        val s = favs().toMutableSet()
        if (!s.add(key)) s.remove(key)
        prefs.edit().putStringSet("favs", s).apply()
        return s
    }

    // ---------- playlists (nome -> chaves das músicas) ----------

    fun playlists(): Map<String, List<String>> {
        val raw = prefs.getString("playlists", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val m = LinkedHashMap<String, List<String>>()
            o.keys().forEach { name ->
                val a = o.getJSONArray(name)
                m[name] = (0 until a.length()).map { a.getString(it) }
            }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePlaylists(m: Map<String, List<String>>) {
        val o = JSONObject()
        m.forEach { (name, keys) -> o.put(name, JSONArray(keys)) }
        prefs.edit().putString("playlists", o.toString()).apply()
    }

    /** false se o nome é vazio ou já existe. */
    @Synchronized
    fun createPlaylist(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val m = playlists().toMutableMap()
        if (n in m) return false
        m[n] = emptyList()
        savePlaylists(m)
        return true
    }

    @Synchronized
    fun deletePlaylist(name: String) {
        val m = playlists().toMutableMap()
        m.remove(name)
        savePlaylists(m)
    }

    @Synchronized
    fun addToPlaylist(name: String, key: String) {
        val m = playlists().toMutableMap()
        val list = m[name] ?: emptyList()
        if (key !in list) m[name] = list + key
        savePlaylists(m)
    }

    @Synchronized
    fun removeFromPlaylist(name: String, key: String) {
        val m = playlists().toMutableMap()
        m[name] = (m[name] ?: emptyList()).filter { it != key }
        savePlaylists(m)
    }

    var userName: String
        get() = prefs.getString("name", "Eu") ?: "Eu"
        set(v) {
            prefs.edit().putString("name", v).apply()
        }

    var friendCode: String?
        get() = prefs.getString("friend", null)
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove("friend") else e.putString("friend", v)
            e.apply()
        }
}
