package com.cybertube.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
    var channelUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun play(v: VideoItem) {
        PlayerHolder.play(ctx, v)
        playerOpen = true
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && channelUrl != null) { channelUrl = null }

    Box(Modifier.fillMaxSize().background(Cy.bg)) {
        Scaffold(
            containerColor = Cy.bg,
            bottomBar = {
                Column {
                    if (st !is PState.Idle) MiniPlayer(onOpen = { playerOpen = true })
                    NavigationBar(containerColor = Cy.surface) {
                        val items = listOf(
                            Triple("SEARCH", Icons.Filled.Search, 0),
                            Triple("SUBS", Icons.Filled.Subscriptions, 1),
                            Triple("HISTORY", Icons.Filled.History, 2),
                        )
                        items.forEach { (label, icon, idx) ->
                            NavigationBarItem(
                                selected = tab == idx && channelUrl == null,
                                onClick = { tab = idx; channelUrl = null },
                                icon = { Icon(icon, label) },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Cy.green,
                                    selectedTextColor = Cy.green,
                                    indicatorColor = Cy.surface2,
                                    unselectedIconColor = Cy.dim,
                                    unselectedTextColor = Cy.dim,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val ch = channelUrl
                if (ch != null) {
                    ChannelScreen(ch, onBack = { channelUrl = null }, onPlay = ::play)
                } else when (tab) {
                    0 -> SearchScreen(onPlay = ::play)
                    1 -> SubsScreen(onPlay = ::play, onChannel = { channelUrl = it })
                    else -> LibraryScreen(onPlay = ::play)
                }
            }
        }

        if (playerOpen) {
            PlayerScreen(
                onClose = { playerOpen = false },
                onOpenChannel = { channelUrl = it; playerOpen = false },
                onPlay = ::play,
            )
        }
    }
}
