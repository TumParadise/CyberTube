package com.cybertube.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cybertube.app.player.PlayerHolder
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybertube.app.BuildConfig
import com.cybertube.app.core.Store
import com.cybertube.app.core.YouTubeApi
import androidx.compose.foundation.layout.Arrangement
import com.cybertube.app.account.AccountState
import com.cybertube.app.account.GoogleAccountProvider
import com.cybertube.app.account.GoogleAuth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

private data class QOpt(val height: Int, val label: String)

private val qualityOptions = listOf(
    QOpt(0, "อัตโนมัติ (สูงสุดไม่เกิน 720p)"),
    QOpt(144, "144p"),
    QOpt(240, "240p"),
    QOpt(360, "360p"),
    QOpt(480, "480p"),
    QOpt(720, "720p"),
    QOpt(1080, "1080p"),
    QOpt(1440, "1440p"),
    QOpt(2160, "สูงสุดเท่าที่มี"),
    QOpt(-1, "เสียงอย่างเดียว"),
)

private data class OssLib(val name: String, val license: String, val url: String, val note: String)

private val ossLibs = listOf(
    OssLib("NewPipeExtractor", "GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor", "ดึงข้อมูลวิดีโอจาก YouTube"),
    OssLib("NewPipe (ดัดแปลงโค้ดบางส่วน)", "GPL-3.0", "https://github.com/TeamNewPipe/NewPipe", "ระบบ PoToken และตัวดึงสตรีม YouTube"),
)

@Composable
fun SettingsScreen() {
    val cur = Store.defaultHeight.intValue
    val uri = LocalUriHandler.current
    var showTeamPicker by remember { mutableStateOf(false) }

    if (showTeamPicker) TeamPickerDialog(onDismiss = { showTeamPicker = false })

    Box(Modifier.fillMaxSize().background(Cy.bg)) {
    Image(
        painter = painterResource(Cy.team.value.bgRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Cy.bg.copy(alpha = 0.45f), Cy.bg.copy(alpha = 0.72f), Cy.bg.copy(alpha = 0.85f)))
        )
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BrandMark(
                logoSize = 44.dp, textSize = 24, version = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        item { SectionLabel("// ตั้งค่า") }
        item { TeamSettingRow(onClick = { showTeamPicker = true }) }

        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("ความคมชัดเริ่มต้น", color = Cy.text, style = MaterialTheme.typography.titleSmall)
                Text(
                    "ทุกวิดีโอจะเริ่มเล่นที่ความคมชัดนี้ ถ้าวิดีโอไม่มีระดับที่เลือก จะใช้ระดับที่ใกล้เคียงและต่ำกว่าแทน",
                    color = Cy.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }
        }
        items(qualityOptions, key = { it.height }) { o ->
            Row(
                Modifier.fillMaxWidth().clickable { Store.setDefaultHeight(o.height) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = cur == o.height,
                    onClick = { Store.setDefaultHeight(o.height) },
                    colors = RadioButtonDefaults.colors(selectedColor = Cy.green, unselectedColor = Cy.dim),
                )
                Text(o.label, color = if (cur == o.height) Cy.green else Cy.text, fontSize = 13.sp)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { Store.setAutoplay(!Store.autoplay.value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("เล่นวิดีโอถัดไปอัตโนมัติ", color = Cy.text, style = MaterialTheme.typography.titleSmall)
                    Text("เมื่อวิดีโอจบ จะเล่นวิดีโอแนะนำอันดับแรกต่อให้เอง", color = Cy.dim, fontSize = 11.sp)
                }
                Switch(
                    checked = Store.autoplay.value,
                    onCheckedChange = { Store.setAutoplay(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cy.bg, checkedTrackColor = Cy.green,
                        uncheckedThumbColor = Cy.dim, uncheckedTrackColor = Cy.surface2,
                    ),
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)); SectionLabel("// บัญชี YouTube") }
        item { YouTubeAccountSection() }

        item { Spacer(Modifier.height(12.dp)); SectionLabel("// แคชวิดีโอ") }
        item { CacheSection() }

        item { Spacer(Modifier.height(12.dp)); SectionLabel("// เกี่ยวกับแอป") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CyberLogo(40.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("CyberTube", color = Cy.green, style = MaterialTheme.typography.titleMedium)
                        Text("เวอร์ชัน ${BuildConfig.VERSION_NAME}", color = Cy.dim, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("ผู้สร้าง: ", color = Cy.dim, fontSize = 13.sp)
                    Text("Tum", color = Cy.cyan, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "แอปดู YouTube ที่ไม่เป็นทางการ ไม่ได้เกี่ยวข้องกับ YouTube หรือ Google " +
                        "รายการช่องที่ติดตามและประวัติการดูเก็บไว้ในเครื่องนี้เท่านั้น โดยประวัติการดูที่เก่ากว่า 7 วันจะถูกลบอัตโนมัติ",
                    color = Cy.dim, fontSize = 11.sp,
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)); SectionLabel("// โอเพนซอร์ส") }
        item {
            Text(
                "CyberTube นำโค้ดบางส่วนของ NewPipe มาดัดแปลงและใช้ NewPipeExtractor ซึ่งทั้งคู่อยู่ภายใต้สัญญาอนุญาต GPL-3.0 " +
                    "แอปนี้จึงเผยแพร่ภายใต้ GPL-3.0 เช่นกัน (แตะเพื่อเปิดหน้าโปรเจกต์):",
                color = Cy.dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        items(ossLibs, key = { it.name }) { lib ->
            Column(
                Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(lib.url) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lib.name, color = Cy.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(lib.license, color = Cy.cyan, fontSize = 11.sp)
                }
                Text(lib.note, color = Cy.dim, fontSize = 11.sp)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
}

@Composable
private fun YouTubeAccountSection() {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val provider = remember { GoogleAccountProvider() }
    var accountState by remember { mutableStateOf(provider.currentState()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<GoogleAuth.DeviceCode?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var editClient by remember { mutableStateOf(false) }
    var idInput by remember { mutableStateOf(GoogleAuth.clientId.value) }
    var secretInput by remember { mutableStateOf(GoogleAuth.clientSecret.value) }
    val configured = GoogleAuth.configured
    val showSetup = !configured || editClient

    fun connect() {
        status = null
        busy = true
        job = scope.launch {
            try {
                val dc = GoogleAuth.requestDeviceCode()
                code = dc
                GoogleAuth.pollForToken(dc)
                code = null
                accountState = provider.signIn()
            } catch (e: CancellationException) {
                code = null
                throw e
            } catch (e: Throwable) {
                code = null
                accountState = AccountState.Error(YouTubeApi.describe(e))
            } finally {
                busy = false
            }
        }
    }

    fun importSubs() {
        status = null
        busy = true
        scope.launch {
            provider.importSubscriptions().fold(
                onSuccess = { n ->
                    status = if (n > 0) "นำเข้าช่องที่ติดตามใหม่ $n ช่อง" else "ไม่มีช่องใหม่ให้นำเข้า (มีครบแล้ว)"
                },
                onFailure = { e ->
                    status = YouTubeApi.describe(e)
                    if (e is GoogleAuth.AuthError && e.fatal) accountState = provider.disconnect()
                },
            )
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Cy.text, unfocusedTextColor = Cy.text, cursorColor = Cy.green,
        focusedBorderColor = Cy.green, unfocusedBorderColor = Cy.dim,
        focusedLabelColor = Cy.green, unfocusedLabelColor = Cy.dim,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            "เชื่อมต่อบัญชี Google",
            color = Cy.text,
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "นำเข้าช่องที่คุณติดตามบน YouTube มาไว้ในแอป ขอสิทธิ์แบบอ่านอย่างเดียว " +
                "ยืนยันผ่านเบราว์เซอร์ด้วยรหัส ไม่ต้องพึ่ง Google Services ในเครื่อง (ใช้กับ microG ได้) " +
                "ระบบเล่นของ CyberTube ยังทำงานแยกจากบัญชี",
            color = Cy.dim,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))

        val dc = code
        if (showSetup) {
            Text(
                "ใส่ Client ID / Client Secret ของ OAuth client ประเภท \"TVs and Limited Input devices\" " +
                    "จาก Google Cloud (ดู SETUP_GOOGLE.md)",
                color = Cy.dim, fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = idInput, onValueChange = { idInput = it },
                label = { Text("Client ID", fontSize = 11.sp) },
                singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = secretInput, onValueChange = { secretInput = it },
                label = { Text("Client Secret", fontSize = 11.sp) },
                singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    GoogleAuth.setClient(idInput.trim(), secretInput.trim())
                    editClient = false
                    status = null
                },
                enabled = idInput.isNotBlank() && secretInput.isNotBlank(),
                border = BorderStroke(1.dp, Cy.green),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
            ) { Text("บันทึก", fontSize = 12.sp) }
        } else if (dc != null) {
            Text("1. เปิดหน้านี้ในเบราว์เซอร์ (มือถือหรือคอมก็ได้)", color = Cy.text, fontSize = 12.sp)
            Text(dc.verificationUrl, color = Cy.cyan, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("2. ใส่รหัสนี้แล้วเลือกบัญชีที่เพิ่มเป็น Test user", color = Cy.text, fontSize = 12.sp)
            Text(
                dc.userCode, color = Cy.green, fontSize = 26.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(dc.verificationUrl) },
                    border = BorderStroke(1.dp, Cy.green),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
                ) { Text("เปิดหน้าเว็บ", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(dc.userCode)) },
                    border = BorderStroke(1.dp, Cy.green),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
                ) { Text("คัดลอกรหัส", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { job?.cancel(); busy = false; code = null; status = "ยกเลิกแล้ว" },
                    border = BorderStroke(1.dp, Cy.dim),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.dim),
                ) { Text("ยกเลิก", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(6.dp))
            Text("กำลังรอการยืนยัน...", color = Cy.green, fontSize = 11.sp)
        } else if (busy) {
            Text("กำลังทำงาน...", color = Cy.green, fontSize = 12.sp)
        } else when (val state = accountState) {
            AccountState.SignedOut, AccountState.Connecting -> {
                OutlinedButton(
                    onClick = { connect() },
                    border = BorderStroke(1.dp, Cy.green),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
                ) { Text("เชื่อมต่อบัญชี Google", fontSize = 12.sp) }
            }

            is AccountState.SignedIn -> {
                Text(
                    "เชื่อมต่อแล้ว: " + (state.account.displayName ?: "บัญชี Google"),
                    color = Cy.green, fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importSubs() },
                        border = BorderStroke(1.dp, Cy.green),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
                    ) { Text("นำเข้าช่องที่ติดตาม", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            accountState = provider.disconnect()
                            status = "ออกจากระบบแล้ว (เพิกถอนสิทธิ์เพิ่มเติมได้ที่หน้าบัญชี Google)"
                        },
                        border = BorderStroke(1.dp, Cy.dim),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.dim),
                    ) { Text("ออกจากระบบ", fontSize = 12.sp) }
                }
            }

            is AccountState.Error -> {
                Text(state.message, color = Cy.dim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { accountState = provider.currentState(); status = null },
                    border = BorderStroke(1.dp, Cy.green),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
                ) { Text("ลองอีกครั้ง", fontSize = 12.sp) }
            }
        }

        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Cy.cyan, fontSize = 11.sp)
        }

        if (!showSetup && !busy && dc == null) {
            TextButton(onClick = { idInput = GoogleAuth.clientId.value; secretInput = GoogleAuth.clientSecret.value; editClient = true }) {
                Text("แก้ไข Client ID / Secret", color = Cy.dim, fontSize = 11.sp)
            }
        }
    }
}

private val cacheOptions = listOf(256, 512, 1024, 2048)

private fun fmtMb(bytes: Long): String = String.format(Locale.US, "%.0f MB", bytes / 1048576.0)

@Composable
private fun CacheSection() {
    val ctx: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    var used by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { used = PlayerHolder.cacheBytes(ctx) }
    val cur = Store.cacheMb.intValue

    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            "วิดีโอที่เคยดูจะถูกเก็บไว้ในเครื่อง ดูซ้ำหรือเลื่อนย้อนกลับจะไม่ต้องโหลดใหม่ " +
                "ข้อมูลที่ไม่ได้เปิดดูเกิน 7 วันจะถูกลบอัตโนมัติ",
            color = Cy.dim, fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ใช้พื้นที่อยู่ ${fmtMb(used)}", color = Cy.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        PlayerHolder.clearCache(ctx)
                        used = PlayerHolder.cacheBytes(ctx)
                    }
                },
                border = BorderStroke(1.dp, Cy.green),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cy.green),
            ) { Text("ล้างแคช", fontSize = 12.sp) }
        }
        Text("ขนาดแคชสูงสุด (มีผลเมื่อเปิดแอปใหม่)", color = Cy.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
    cacheOptions.forEach { mb ->
        Row(
            Modifier.fillMaxWidth().clickable { Store.setCacheMb(mb) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = cur == mb,
                onClick = { Store.setCacheMb(mb) },
                colors = RadioButtonDefaults.colors(selectedColor = Cy.green, unselectedColor = Cy.dim),
            )
            Text(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB", color = if (cur == mb) Cy.green else Cy.text, fontSize = 13.sp)
        }
    }
}
