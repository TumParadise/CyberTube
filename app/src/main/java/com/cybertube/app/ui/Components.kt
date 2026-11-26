package com.cybertube.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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

private val isoDate = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T.*")
private val relDate = Regex("(\\d+)\\s*([A-Za-z]+)\\s+ago")
private val thMonths = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

/** ISO date -> "17 ต.ค. 2569"; "2 mo ago" -> "2 เดือนที่แล้ว"; anything else passes through. */
fun fmtDate(s: String?): String {
    if (s == null) return ""
    isoDate.matchEntire(s)?.let { m ->
        val y = m.groupValues[1].toInt() + 543
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo in 1..12) return "$d ${thMonths[mo - 1]} $y"
    }
    relDate.find(s)?.let { m ->
        val n = m.groupValues[1]
        val u = m.groupValues[2].lowercase()
        val unit = when {
            u.startsWith("mo") -> "เดือน"
            u.startsWith("mi") || u == "m" -> "นาที"
            u.startsWith("s") -> "วินาที"
            u.startsWith("h") -> "ชั่วโมง"
            u.startsWith("d") -> "วัน"
            u.startsWith("w") -> "สัปดาห์"
            u.startsWith("y") -> "ปี"
            else -> null
        }
        if (unit != null) return "$n $unit" + "ที่แล้ว"
    }
    return s
}

fun fmtMs(ms: Long): String = fmtDur(ms / 1000).ifEmpty { "0:00" }

/** Thai-style short numbers: 1.2 พัน / 3.4 หมื่น / 1.9 แสน / 34.4 ล้าน / 1.2 พันล้าน */
fun fmtCount(n: Long): String {
    if (n < 0) return ""
    fun f(v: Double, unit: String) = String.format(Locale.US, "%.1f", v).removeSuffix(".0") + " " + unit
    return when {
        n >= 1_000_000_000 -> f(n / 1e9, "พันล้าน")
        n >= 1_000_000 -> f(n / 1e6, "ล้าน")
        n >= 100_000 -> f(n / 1e5, "แสน")
        n >= 10_000 -> f(n / 1e4, "หมื่น")
        n >= 1_000 -> f(n / 1e3, "พัน")
        else -> n.toString()
    }
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
                fmtCount(item.views).let { if (it.isEmpty()) "" else "ดู $it ครั้ง" },
                fmtDate(item.uploaded),
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
        Text(if (on) "ติดตามแล้ว" else "ติดตาม", fontSize = 12.sp)
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

/** Thin YouTube-style seek bar: played (green) over buffered (cyan) over track. Touch area is taller than the line. */
@Composable
fun SeekBar(
    progress: Float,
    buffered: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = (off.x / size.width).coerceIn(0f, 1f)
                    onSeek(f); onSeekFinished(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        last = (off.x / size.width).coerceIn(0f, 1f); onSeek(last)
                    },
                    onDragEnd = { onSeekFinished(last) },
                    onDragCancel = { onSeekFinished(last) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(last)
                    },
                )
            }
    ) {
        val cy = size.height / 2
        val th = 3.dp.toPx()
        val r = CornerRadius(th / 2, th / 2)
        val top = cy - th / 2
        drawRoundRect(Cy.dim.copy(alpha = 0.35f), Offset(0f, top), Size(size.width, th), r)
        drawRoundRect(Cy.cyan.copy(alpha = 0.55f), Offset(0f, top), Size(size.width * buffered.coerceIn(0f, 1f), th), r)
        val x = size.width * progress.coerceIn(0f, 1f)
        drawRoundRect(Cy.green, Offset(0f, top), Size(x, th), r)
        drawCircle(Cy.green, radius = 5.dp.toPx(), center = Offset(x, cy))
    }
}

/** App logo (play icon) in the colours of the current team. */
@Composable
fun CyberLogo(size: Dp, modifier: Modifier = Modifier, team: Team = Cy.team.value) {
    Image(
        painter = painterResource(team.logoRes),
        contentDescription = "CyberTube",
        modifier = modifier.size(size),
    )
}

/** Logo + "CyberTube" wordmark (Cyber in white, Tube in the team colour) + optional version line. */
@Composable
fun BrandMark(logoSize: Dp, textSize: Int, modifier: Modifier = Modifier, version: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CyberLogo(logoSize)
        Spacer(Modifier.width(8.dp))
        Column {
            Row {
                Text("Cyber", color = Color.White, fontSize = textSize.sp, fontWeight = FontWeight.Bold)
                Text("Tube", color = Cy.cyan, fontSize = textSize.sp, fontWeight = FontWeight.Bold)
            }
            if (version != null) Text(version, color = Cy.dim, fontSize = (textSize * 0.5f).sp)
        }
    }
}
