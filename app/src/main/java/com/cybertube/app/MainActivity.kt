package com.cybertube.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.cybertube.app.ui.FloatingPlayer
import com.cybertube.app.ui.SettingsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.cybertube.app.core.VideoItem
import com.cybertube.app.player.PState
import com.cybertube.app.player.PlayerHolder
import com.cybertube.app.ui.ChannelScreen
import com.cybertube.app.ui.CyberTheme
import com.cybertube.app.ui.Cy
import com.cybertube.app.ui.LibraryScreen
import com.cybertube.app.ui.MiniPlayer
import com.cybertube.app.ui.PlayerScreen
import com.cybertube.app.ui.SearchScreen
import com.cybertube.app.ui.SubsScreen

class MainActivity : ComponentActivity() {
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent { CyberTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    val st by PlayerHolder.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var channelUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun play(v: VideoItem) {
        PlayerHolder.play(ctx, v)
        playerOpen = true
    }

    // Minimized video becomes a small draggable window; audio-only / loading falls back to the bottom bar.
    val ready = st as? PState.Ready
    val hasVideo = ready != null && ready.qualities[ready.selected].let { it.video != null || it.hls != null }
    val floating = !playerOpen && hasVideo

    LaunchedEffect(playerOpen) { if (!playerOpen) fullscreen = false }
    BackHandler(enabled = playerOpen && !fullscreen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && channelUrl != null) { channelUrl = null }

    // Player lives INSIDE the Scaffold content, so the bottom menu stays visible on every page
    // and the status bar inset is respected (no overlap with Android's icons).
    Scaffold(
        containerColor = Cy.bg,
        bottomBar = {
            if (!fullscreen) {
                Column {
                    if (st !is PState.Idle && !playerOpen && !floating) MiniPlayer(onOpen = { playerOpen = true })
                    BottomNav(
                        selected = if (channelUrl == null) tab else -1,
                        onSelect = { tab = it; channelUrl = null; playerOpen = false },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Cy.bg)) {
            val ch = channelUrl
            if (ch != null) {
                ChannelScreen(ch, onBack = { channelUrl = null }, onPlay = ::play)
            } else when (tab) {
                0 -> SearchScreen(onPlay = ::play)
                1 -> SubsScreen(onPlay = ::play, onChannel = { channelUrl = it })
                2 -> LibraryScreen(onPlay = ::play)
                else -> SettingsScreen()
            }

            if (floating) FloatingPlayer(onExpand = { playerOpen = true })

            if (playerOpen) {
                PlayerScreen(
                    fullscreen = fullscreen,
                    onFullscreenChange = { fullscreen = it },
                    onClose = { playerOpen = false },
                    onOpenChannel = { channelUrl = it; playerOpen = false },
                    onPlay = ::play,
                )
            }
        }
    }
}

/** Compact bottom menu (52dp, icon 20dp, label 9sp). */
@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("ค้นหา", Icons.Filled.Search, 0),
        Triple("ติดตาม", Icons.Filled.Subscriptions, 1),
        Triple("ประวัติ", Icons.Filled.History, 2),
        Triple("ตั้งค่า", Icons.Filled.Settings, 3),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cy.surface)
            .navigationBarsPadding()
            .height(52.dp),
    ) {
        items.forEach { (label, icon, idx) ->
            val on = selected == idx
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(idx) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, label, tint = if (on) Cy.green else Cy.dim, modifier = Modifier.size(20.dp))
                Text(label, color = if (on) Cy.green else Cy.dim, fontSize = 9.sp)
            }
        }
    }
}
