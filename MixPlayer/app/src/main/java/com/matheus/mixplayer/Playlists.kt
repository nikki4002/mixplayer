package com.matheus.mixplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/** Janela para escolher a playlist (ou criar uma nova) e adicionar a música. */
@Composable
fun AddToPlaylistDialog(song: Song, store: Store, onDismiss: () -> Unit) {
    val names = remember { store.playlists().keys.sorted() }
    var newName by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar à playlist") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                names.forEach { name ->
                    TextButton(onClick = {
                        store.addToPlaylist(name, song.key)
                        onDismiss()
                    }) { Text(name) }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nova playlist") },
                    singleLine = true,
                )
                TextButton(onClick = {
                    val n = newName.trim()
                    if (n.isEmpty()) {
                        info = "Digite um nome."
                    } else {
                        store.createPlaylist(n)
                        store.addToPlaylist(n, song.key)
                        onDismiss()
                    }
                }) { Text("Criar e adicionar") }
                info?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
fun PlaylistsScreen(
    songs: List<Song>,
    store: Store,
    controller: Player?,
    modifier: Modifier = Modifier,
) {
    var playlists by remember { mutableStateOf(store.playlists()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val byKey = remember(songs) { songs.associateBy { it.key } }

    Column(modifier.padding(horizontal = 16.dp)) {
        val name = selected
        if (name == null || name !in playlists) {
            // ---------- lista de playlists ----------
            Text(
                "Playlists",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome da nova playlist") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val n = newName.trim()
                    if (n.isEmpty()) {
                        error = "Digite um nome."
                    } else if (!store.createPlaylist(n)) {
                        error = "Já existe uma playlist com esse nome."
                    } else {
                        error = null
                        newName = ""
                        playlists = store.playlists()
                    }
                }) { Text("Criar") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (playlists.isEmpty()) {
                Text(
                    "Nenhuma playlist ainda. Crie uma acima e adicione músicas pela aba Músicas (botão ⋮).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(playlists.keys.sorted(), key = { it }) { n ->
                    val count = playlists[n].orEmpty().count { it in byKey }
                    ListItem(
                        headlineContent = { Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$count músicas") },
                        modifier = Modifier.clickable {
                            selected = n
                            error = null
                        },
                    )
                }
            }
        } else {
            // ---------- dentro de uma playlist ----------
            val listSongs = playlists[name].orEmpty().mapNotNull { byKey[it] }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TextButton(onClick = { selected = null }) { Text("← Voltar") }
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Button(
                    onClick = { playList(controller, listSongs, 0) },
                    enabled = listSongs.isNotEmpty(),
                ) { Text("Tocar") }
                OutlinedButton(
                    onClick = { playList(controller, listSongs.shuffled(), 0) },
                    enabled = listSongs.isNotEmpty(),
                ) { Text("Aleatório") }
                TextButton(onClick = { confirmDelete = true }) { Text("Apagar playlist") }
            }
            if (listSongs.isEmpty()) {
                Text(
                    "Essa playlist está vazia. Na aba Músicas, toque em ⋮ e escolha Adicionar à playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(listSongs, key = { _, s -> s.id }) { index, song ->
                    ListItem(
                        headlineContent = {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                store.removeFromPlaylist(name, song.key)
                                playlists = store.playlists()
                            }) { Text("✕") }
                        },
                        modifier = Modifier.clickable { playList(controller, listSongs, index) },
                    )
                }
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Apagar playlist?") },
                    text = { Text("A playlist \"$name\" será removida. As músicas continuam no celular.") },
                    confirmButton = {
                        TextButton(onClick = {
                            store.deletePlaylist(name)
                            playlists = store.playlists()
                            selected = null
                            confirmDelete = false
                        }) { Text("Apagar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
                    },
                )
            }
        }
    }
}
