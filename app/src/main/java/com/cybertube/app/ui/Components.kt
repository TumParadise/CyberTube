package com.cybertube.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cybertube.app.core.Store
import com.cybertube.app.core.VideoItem
import java.util.Locale

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun fmtDur(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun fmtMs(ms: Long): String = fmtDur(ms / 1000).ifEmpty { "0:00" }

fun fmtCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}

@Composable
fun VideoRow(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(156.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Cy.surface2)
                .border(BorderStroke(1.dp, Cy.dim.copy(alpha = 0.35f)), RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = item.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            val d = fmtDur(item.durationSec)
            if (d.isNotEmpty()) {
                Text(
                    d,
                    color = Cy.green,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Cy.bg.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Cy.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 3.dp))
            Text(
                item.channel,
                style = MaterialTheme.typography.bodySmall,
                color = Cy.cyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(
                fmtCount(item.views).let { if (it.isEmpty()) "" else "$it views" },
                item.uploaded ?: "",
            ).filter { it.isNotEmpty() }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = Cy.dim, maxLines = 1)
            }
        }
    }
}

@Composable
fun Avatar(url: String?, size: Int = 36, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .width(size.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Cy.surface2)
            .border(1.dp, Cy.green.copy(alpha = 0.6f), CircleShape),
    )
}

@Composable
fun SubscribeButton(url: String?, name: String, avatar: String?) {
    if (url == null) return
    val on = Store.isSubscribed(url)
    OutlinedButton(
        onClick = { Store.toggleSub(url, name, avatar) },
        border = BorderStroke(1.dp, if (on) Cy.dim else Cy.green),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (on) Cy.dim else Cy.green),
    ) {
        Text(if (on) "SUBSCRIBED" else "SUBSCRIBE", fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Cy.green,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
