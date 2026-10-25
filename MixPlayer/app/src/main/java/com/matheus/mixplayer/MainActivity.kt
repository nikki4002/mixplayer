package com.matheus.mixplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MixApp(controller, store)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                controller = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }
}

// ---------- permissões ----------

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, audioPermission()) == PackageManager.PERMISSION_GRANTED

fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPermission(), Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(audioPermission())

// ---------- player ----------

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
    )
    .build()

fun playList(player: Player?, songs: List<Song>, start: Int) {
    val p = player ?: return
    if (songs.isEmpty()) return
    p.setMediaItems(songs.map { it.toMediaItem() }, start.coerceAtLeast(0), 0L)
    p.prepare()
    p.play()
}

class PlayerUi {
    var isPlaying by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var mediaId by mutableStateOf("")
}

@Composable
fun rememberPlayerUi(player: Player?): PlayerUi {
    val ui = remember { PlayerUi() }
    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose { }
        }
        fun sync() {
            ui.isPlaying = player.isPlaying
            ui.title = player.mediaMetadata.title?.toString().orEmpty()
            ui.artist = player.mediaMetadata.artist?.toString().orEmpty()
            ui.mediaId = player.currentMediaItem?.mediaId.orEmpty()
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }
    return ui
}

// ---------- telas ----------

@Composable
fun MixApp(controller: MediaController?, store: Store) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasAudioPermission(context) }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(requiredPermissions())
    }
    LaunchedEffect(granted) {
        if (granted) songs = withContext(Dispatchers.IO) { MusicRepository.load(context) }
    }

    val ui = rememberPlayerUi(controller)

    Scaffold(
        bottomBar = {
            Column {
                if (ui.title.isNotEmpty()) PlayerBar(ui, controller)
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("♪") },
                        label = { Text("Músicas") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("♥") },
                        label = { Text("Mix") },
                    )
                }
            }
        }
    ) { padding ->
        if (!granted) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text("Preciso de permissão para ler as músicas do celular.")
                Button(
                    onClick = { launcher.launch(requiredPermissions()) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Permitir") }
                Text(
                    "Se o botão não abrir nada, ative a permissão em Configurações > Apps > MixPlayer.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else if (tab == 0) {
            SongsScreen(songs, store, controller, ui.mediaId, Modifier.padding(padding))
        } else {
            MixScreen(songs, store, controller, Modifier.padding(padding))
        }
    }
}

@Composable
fun PlayerBar(ui: PlayerUi, controller: Player?) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    ui.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { controller?.seekToPreviousMediaItem() }) { Text("⏮") }
            TextButton(onClick = {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }) { Text(if (ui.isPlaying) "⏸" else "▶") }
            TextButton(onClick = { controller?.seekToNextMediaItem() }) { Text("⏭") }
        }
    }
}

@Composable
fun SongsScreen(
    songs: List<Song>,
    store: Store,
    controller: Player?,
    currentId: String,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var favs by remember { mutableStateOf(store.favs()) }
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
                ListItem(
                    headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        TextButton(onClick = { favs = store.toggleFav(song.key) }) {
                            Text(if (song.key in favs) "★" else "☆")
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
}

@Composable
fun MixScreen(
    songs: List<Song>,
    store: Store,
    controller: Player?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(store.userName) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var friendCode by remember { mutableStateOf(store.friendCode) }

    val friend = remember(friendCode) { friendCode?.let { MixEngine.decode(it) } }
    val me = remember(songs, friend) {
        MixEngine.buildProfile(store.userName, songs, store.plays(), store.favs())
    }
    val mix = remember(me, friend, songs) {
        if (friend != null) MixEngine.buildMix(me, friend, songs) else emptyList()
    }
    val compat = remember(me, friend) { friend?.let { MixEngine.compat(me, it) } }
    val playable = remember(mix) { mix.mapNotNull { it.song } }

    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            Text(
                "Mix",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Troque códigos com um amigo e crie uma playlist com o gosto dos dois.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    store.userName = it
                },
                label = { Text("Seu nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    val profile =
                        MixEngine.buildProfile(name, songs, store.plays(), store.favs())
                    if (profile.tracks.isEmpty()) {
                        error = "Ouça algumas músicas ou favorite com ★ antes de compartilhar."
                    } else {
                        error = null
                        val text = "Vamos fazer um Mix! Cole este código no MixPlayer:\n" +
                            MixEngine.encode(profile)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartilhar meu código"))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Compartilhar meu código") }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Cole aqui o código do amigo") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Button(
                onClick = {
                    val code = MixEngine.extract(input)
                    if (code == null || MixEngine.decode(code) == null) {
                        error = "Código inválido. Cole o texto completo que seu amigo mandou."
                    } else {
                        error = null
                        store.friendCode = code
                        friendCode = code
                        input = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Criar Mix") }
        }
        error?.let {
            item {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (friend != null && compat != null) {
            item {
                Text(
                    "Compatibilidade com ${friend.name}: $compat%",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    "${mix.size} músicas no Mix, ${playable.size} estão no seu celular.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Button(onClick = { playList(controller, playable, 0) }) { Text("Tocar Mix") }
                    OutlinedButton(onClick = {
                        store.friendCode = null
                        friendCode = null
                    }) { Text("Sair do Mix") }
                }
            }
            itemsIndexed(mix) { _, item ->
                val who = when (item.origin) {
                    Origin.ME -> "você"
                    Origin.FRIEND -> friend.name
                    Origin.BOTH -> "os dois"
                }
                val local = item.song
                ListItem(
                    headlineContent = {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            item.artist + " · " + who +
                                if (local == null) " · não está no seu celular" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .alpha(if (local == null) 0.5f else 1f)
                        .clickable(enabled = local != null) {
                            local?.let { s ->
                                playList(controller, playable, playable.indexOfFirst { it.id == s.id })
                            }
                        },
                )
            }
        }
    }
}
