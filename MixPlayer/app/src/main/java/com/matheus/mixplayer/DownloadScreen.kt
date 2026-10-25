package com.matheus.mixplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadScreen(onDownloaded: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ArchiveItem>>(emptyList()) }
    var selected by remember { mutableStateOf<ArchiveItem?>(null) }
    var files by remember { mutableStateOf<List<ArchiveFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var done by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            message = null
            try {
                results = withContext(Dispatchers.IO) { ArchiveApi.search(query) }
                if (results.isEmpty()) message = "Nada encontrado. Tente outro nome ou gênero."
            } catch (e: Exception) {
                message = "Falha na busca. Confira sua internet."
            }
            loading = false
        }
    }

    fun openItem(item: ArchiveItem) {
        scope.launch {
            loading = true
            message = null
            try {
                files = withContext(Dispatchers.IO) { ArchiveApi.files(item.id) }
                selected = item
                if (files.isEmpty()) message = "Esse item não tem arquivos MP3."
            } catch (e: Exception) {
                message = "Falha ao abrir o item. Confira sua internet."
            }
            loading = false
        }
    }

    fun download(item: ArchiveItem, file: ArchiveFile) {
        scope.launch {
            busy = busy + file.name
            message = null
            val ok = withContext(Dispatchers.IO) {
                ArchiveApi.download(context, item.id, file, item.creator)
            }
            busy = busy - file.name
            if (ok) {
                done = done + file.name
                onDownloaded()
            } else {
                message = "Não consegui baixar \"${file.title}\"."
            }
        }
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        Text(
            "Baixar músicas",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Músicas do Internet Archive com licença livre (Creative Commons) ou de selos de música livre. " +
                "Confira a licença na página do item em archive.org. " +
                "Os arquivos vão para a pasta Músicas/MixPlayer.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val current = selected
        if (current == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar artista, álbum ou gênero") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { search() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (loading) "Buscando..." else "Buscar") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    selected = null
                    files = emptyList()
                    message = null
                }) { Text("← Voltar") }
                Column {
                    Text(
                        current.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(current.creator, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        if (current == null) {
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(item.creator, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.clickable(enabled = !loading) { openItem(item) },
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.name }) { file ->
                    val mb = if (file.sizeBytes > 0) " · %.1f MB".format(file.sizeBytes / 1_048_576.0) else ""
                    ListItem(
                        headlineContent = {
                            Text(file.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text("MP3$mb") },
                        trailingContent = {
                            TextButton(
                                onClick = { download(current, file) },
                                enabled = file.name !in busy && file.name !in done,
                            ) {
                                Text(
                                    when {
                                        file.name in done -> "✓ Baixada"
                                        file.name in busy -> "Baixando..."
                                        else -> "⬇ Baixar"
                                    }
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
