package com.cybertube.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cybertube.app.player.PlayerHolder
import kotlin.math.roundToInt

/**
 * Small draggable video window that floats above every page of the app.
 * Uses the same ExoPlayer as the full player, so playback never restarts when minimizing/expanding.
 * Drop it inside the content Box; the full-size wrapper does not intercept touches.
 */
@Composable
fun FloatingPlayer(onExpand: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { PlayerHolder.player(ctx) }
    val playing = rememberIsPlaying(player)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = 200.dp
        val h = 112.dp
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val maxX = (with(density) { maxWidth.toPx() } - wPx).coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - hPx).coerceAtLeast(0f)

        // NaN = not moved yet -> bottom-right corner
        var x by remember { mutableFloatStateOf(Float.NaN) }
        var y by remember { mutableFloatStateOf(Float.NaN) }
        val cx = (if (x.isNaN()) maxX - margin else x).coerceIn(0f, maxX)
        val cy = (if (y.isNaN()) maxY - margin else y).coerceIn(0f, maxY)

        Box(
            Modifier
                .offset { IntOffset(cx.roundToInt(), cy.roundToInt()) }
                .size(w, h)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Cy.green.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x = ((if (x.isNaN()) maxX - margin else x) + drag.x).coerceIn(0f, maxX)
                        y = ((if (y.isNaN()) maxY - margin else y) + drag.y).coerceIn(0f, maxY)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
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
            IconButton(
                onClick = { if (playing) player.pause() else player.play() },
                modifier = Modifier.align(Alignment.BottomStart).size(36.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "เล่น/หยุด", tint = Cy.green,
                )
            }
            IconButton(
                onClick = { PlayerHolder.clear(ctx) },
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
            ) {
                Icon(Icons.Filled.Close, "ปิด", tint = Cy.text)
            }
        }
    }
}
