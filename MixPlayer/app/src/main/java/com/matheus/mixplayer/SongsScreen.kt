package com.matheus.mixplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun SongsScreen(
    songs: List<Song>,
    store: Store,
    controller: Player?,
    currentId: String,
    onDelete: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var favs by remember { mutableStateOf(store.favs()) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    val shown = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar música ou artista") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (songs.isEmpty()) {
            Text("Nenhuma música encontrada no celular.", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(shown, key = { _, s -> s.id }) { index, song ->
                val current = song.id.toString() == currentId
                var expanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { favs = store.toggleFav(song.key) }) {
                                Text(if (song.key in favs) "★" else "☆")
                            }
                            Box {
                                TextButton(onClick = { expanded = true }) { Text("⋮") }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Adicionar à playlist") },
                                        onClick = {
                                            expanded = false
                                            addTarget = song
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apagar do celular") },
                                        onClick = {
                                            expanded = false
                                            deleteTarget = song
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable { playList(controller, shown, index) },
                )
            }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistDialog(song, store) { addTarget = null }
    }

    deleteTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Apagar música?") },
            text = {
                Text("\"${song.title}\" será apagada do celular. Isso não dá para desfazer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(song)
                    deleteTarget = null
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            },
        )
    }
}
