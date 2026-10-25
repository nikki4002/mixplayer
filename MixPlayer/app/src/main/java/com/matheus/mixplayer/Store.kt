package com.matheus.mixplayer

import android.content.Context
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
