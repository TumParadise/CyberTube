package com.cybertube.app.ui

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cybertube.app.core.Store
import com.cybertube.app.core.VideoItem
import com.cybertube.app.player.PState
import com.cybertube.app.player.PlayerHolder
import kotlinx.coroutines.delay

@Composable
private fun FullscreenEffect(on: Boolean) {
    val act = LocalContext.current.findActivity()
    DisposableEffect(on) {
        val w = act?.window
        if (act != null && w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            if (on) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (act != null && w != null && on) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun rememberIsPlaying(player: Player): Boolean {
    var playing by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        playing = player.isPlaying
        onDispose { player.removeListener(l) }
    }
    return playing
}

@Composable
fun PlayerScreen(
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlay: (VideoItem) -> Unit,
) {
    val ctx = LocalContext.current
    val st by PlayerHolder.state.collectAsState()
    FullscreenEffect(fullscreen)
    BackHandler(enabled = fullscreen) { onFullscreenChange(false) }

    Column(Modifier.fillMaxSize().background(Cy.bg)) {
        Box(
            if (fullscreen) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        ) {
            when (val s = st) {
                is PState.Idle -> {}
                is PState.Loading -> {
                    AsyncImage(s.item.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cy.green)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "ย่อหน้าต่าง", tint = Cy.green)
                    }
                }
                is PState.Error -> {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text("เกิดข้อผิดพลาด: ${s.message}", color = Cy.red, fontSize = 12.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.padding(4.dp))
                        Button(onClick = { PlayerHolder.play(ctx, s.item, startMs = s.resumeMs) }) { Text("ลองใหม่") }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "ย่อหน้าต่าง", tint = Cy.green)
                    }
                }
                is PState.Ready -> VideoSurface(s, fullscreen, onClose, { onFullscreenChange(!fullscreen) })
            }
        }

        if (!fullscreen) {
            val item = when (val s = st) {
                is PState.Ready -> s.item
                is PState.Loading -> s.item
                is PState.Error -> s.item
                else -> null
            }
            val ready = st as? PState.Ready
            LazyColumn(Modifier.fillMaxSize()) {
                if (item != null) {
                    item {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title, color = Cy.text, style = MaterialTheme.typography.titleMedium)
                            val meta = listOf(
                                fmtCount(item.views).let { if (it.isEmpty()) "" else "ดู $it ครั้ง" },
                                fmtDate(item.uploaded),
                                ready?.detail?.likes?.let { if (it >= 0) "ถูกใจ ${fmtCount(it)}" else "" } ?: "",
                            ).filter { it.isNotEmpty() }.joinToString(" • ")
                            if (meta.isNotEmpty()) Text(meta, color = Cy.dim, fontSize = 12.sp)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).clickable(enabled = item.channelUrl != null) { item.channelUrl?.let(onOpenChannel) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(ready?.detail?.channelAvatar, 36)
                                Spacer(Modifier.width(10.dp))
                                Text(item.channel, color = Cy.cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SubscribeButton(item.channelUrl, item.channel, ready?.detail?.channelAvatar)
                        }
                    }
                }
                if (ready != null) {
                    if (ready.detail.description.isNotBlank()) {
                        item {
                            var expanded by remember(ready.item.url) { mutableStateOf(false) }
                            Text(
                                ready.detail.description,
                                color = Cy.dim,
                                fontSize = 12.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                            )
                        }
                    }
                    item { SectionLabel("// วิดีโอถัดไป") }
                    items(ready.detail.related, key = { it.url }) { v ->
                        VideoRow(v, onClick = { onPlay(v) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSurface(
    s: PState.Ready,
    fullscreen: Boolean,
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.player(ctx) }
    val isPlaying = rememberIsPlaying(player)
    val quality = s.qualities[s.selected]
    val audioOnly = quality.video == null && quality.hls == null

    var show by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var buf by remember { mutableStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var qMenu by remember { mutableStateOf(false) }
    var sMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(player.playbackParameters.speed) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            if (drag == null) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
            }
            buf = player.bufferedPosition
            delay(400)
        }
    }
    LaunchedEffect(show, isPlaying) {
        if (show && isPlaying) {
            delay(3500)
            show = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { show = !show },
                    onDoubleTap = { off ->
                        val delta = if (off.x < size.width / 2) -10_000L else 10_000L
                        player.seekByMs(delta)
                        show = true
                    },
                )
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (audioOnly) {
            AsyncImage(s.item.thumb, null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
            Text(
                "โหมดเสียงอย่างเดียว", color = Cy.green, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 90.dp),
            )
        }
        if (buffering) {
            CircularProgressIndicator(color = Cy.green, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }

        if (show) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

            // top bar
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (fullscreen) onToggleFullscreen() else onClose() }) {
                    Icon(
                        if (fullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.KeyboardArrowDown,
                        "ย่อหน้าต่าง", tint = Cy.green,
                    )
                }
                Text(
                    if (fullscreen) s.item.title else "",
                    color = Cy.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // audio-only shortcut
                val audioIdx = s.qualities.indexOfFirst { it.video == null && it.hls == null }
                if (audioIdx >= 0) {
                    IconButton(onClick = {
                        if (audioOnly) PlayerHolder.selectQuality(ctx, YtDefault(s)) else PlayerHolder.selectQuality(ctx, audioIdx)
                    }) { Icon(Icons.Filled.Headphones, "เสียงอย่างเดียว", tint = if (audioOnly) Cy.green else Cy.text) }
                }
                Box {
                    IconButton(onClick = { sMenu = true }) { Icon(Icons.Filled.Speed, "ความเร็ว", tint = Cy.text) }
                    DropdownMenu(expanded = sMenu, onDismissRequest = { sMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                            DropdownMenuItem(
                                text = { Text("${sp}x", color = if (sp == speed) Cy.green else Cy.text) },
                                onClick = { player.setPlaybackSpeed(sp); speed = sp; sMenu = false },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { qMenu = true }) { Icon(Icons.Filled.Tune, "ความคมชัด", tint = Cy.text) }
                    DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                        s.qualities.forEachIndexed { i, q ->
                            DropdownMenuItem(
                                text = { Text(q.label, color = if (i == s.selected) Cy.green else Cy.text) },
                                onClick = { PlayerHolder.selectQuality(ctx, i); qMenu = false },
                            )
                        }
                    }
                }
            }

            // centre controls
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlayerHolder.previous(ctx) }) {
                    Icon(Icons.Filled.SkipPrevious, "วิดีโอก่อนหน้า", tint = Cy.text, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                    Icon(Icons.Filled.Replay10, "-10s", tint = Cy.text, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "play/pause", tint = Cy.green, modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    player.seekByMs(10_000L)
                }) {
                    Icon(Icons.Filled.Forward10, "+10s", tint = Cy.text, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { PlayerHolder.next(ctx) }) {
                    Icon(Icons.Filled.SkipNext, "วิดีโอถัดไป", tint = Cy.text, modifier = Modifier.size(30.dp))
                }
            }

            // bottom bar
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmtMs(pos), color = Cy.text, fontSize = 11.sp)
                SeekBar(
                    progress = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    buffered = if (dur > 0) (buf.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onSeek = { drag = it },
                    onSeekFinished = { f ->
                        val t = (f * dur).toLong()
                        player.seekTo(t); pos = t; drag = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(fmtMs(dur), color = Cy.text, fontSize = 11.sp)
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        "เต็มจอ", tint = Cy.text,
                    )
                }
            }
        }
    }
}

/** Index to go back to when leaving audio-only mode (uses the Settings quality when it is a video height). */
private fun YtDefault(s: PState.Ready): Int {
    val pref = Store.defaultHeight.intValue
    val target = if (pref > 0) pref else 720
    val vids = s.qualities.withIndex().filter { it.value.video != null && it.value.height > 0 }
    vids.firstOrNull { it.value.height <= target }?.let { return it.index }
    vids.lastOrNull()?.let { return it.index }
    val j = s.qualities.indexOfFirst { it.video != null || it.hls != null }
    return if (j >= 0) j else 0
}

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val st by PlayerHolder.state.collectAsState()
    val player = remember { PlayerHolder.player(ctx) }
    val playing = rememberIsPlaying(player)
    val item = when (val s = st) {
        is PState.Ready -> s.item
        is PState.Loading -> s.item
        is PState.Error -> s.item
        else -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cy.surface2)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            item.thumb, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(64.dp).aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Cy.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.channel, color = Cy.dim, fontSize = 10.sp, maxLines = 1)
        }
        if (st is PState.Ready) {
            IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "play/pause", tint = Cy.green)
            }
        }
        IconButton(onClick = { PlayerHolder.clear(ctx) }) {
            Icon(Icons.Filled.Close, "close", tint = Cy.dim)
        }
    }
}

/**
 * Relative seek that also works on live streams: there duration is C.TIME_UNSET (negative), and
 * clamping it with coerceAtLeast(0) used to send every seek to the start of the live window.
 */
private fun Player.seekByMs(deltaMs: Long) {
    val target = currentPosition + deltaMs
    val d = duration
    seekTo(if (d == C.TIME_UNSET || d <= 0L) target.coerceAtLeast(0L) else target.coerceIn(0L, d))
}
