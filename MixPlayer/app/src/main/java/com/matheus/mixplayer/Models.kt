package com.matheus.mixplayer

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
) {
    val key: String get() = keyOf(title, artist)
}

fun normalize(s: String): String = s.lowercase().trim().replace(Regex("\\s+"), " ")

/** Chave usada para comparar músicas entre dois celulares diferentes. */
fun keyOf(title: String, artist: String): String = normalize(title) + "|" + normalize(artist)
