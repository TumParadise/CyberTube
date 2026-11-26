package com.cybertube.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cybertube.app.core.Store

/** Settings row "เปลี่ยนทีม (Theme Team)" — opens [TeamPickerDialog]. */
@Composable
fun TeamSettingRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Cy.surface.copy(alpha = 0.88f))
            .border(1.dp, Cy.green.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Palette, null, tint = Cy.green, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("เปลี่ยนทีม (Theme Team)", color = Cy.text, style = MaterialTheme.typography.titleSmall)
            Text("ตอนนี้: ${Cy.team.value.label}", color = Cy.dim, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Cy.text)
    }
}

/**
 * Two-team picker. Tapping a card previews that team live (the whole app recolours behind the dialog);
 * "ตกลง" saves it, "ยกเลิก" / back restores the team that was active when the dialog opened.
 */
@Composable
fun TeamPickerDialog(onDismiss: () -> Unit) {
    val original = remember { Cy.team.value }
    var pick by remember { mutableStateOf(original) }

    fun cancel() {
        Cy.team.value = original
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Cy.surface)
                .border(1.dp, Cy.green.copy(alpha = 0.55f), shape)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, null, tint = Cy.text, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("เปลี่ยนทีม (Theme Team)", color = Cy.text, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Team.values().forEach { t ->
                    TeamCard(
                        team = t,
                        selected = pick == t,
                        modifier = Modifier.weight(1f),
                    ) {
                        pick = t
                        Cy.team.value = t // live preview
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = ::cancel,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Cy.dim),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.text),
                ) { Text("ยกเลิก") }
                Button(
                    onClick = {
                        Store.setTeam(pick)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Cy.green, contentColor = Color.Black),
                ) { Text("ตกลง", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val accent = team.accent
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), Color(0xFF02040A))))
            .border(if (selected) 2.5.dp else 1.dp, accent.copy(alpha = if (selected) 1f else 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CyberLogo(60.dp, team = team)
            Spacer(Modifier.height(10.dp))
            Text(
                team.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(team.desc, color = Color(0xFFB8C4CC), fontSize = 10.sp, textAlign = TextAlign.Center)
        }
        // check mark (top-right) — filled when selected, empty ring otherwise
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent)
                .border(1.5.dp, if (selected) accent else Color(0xFF6B7780), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp))
        }
    }
}
