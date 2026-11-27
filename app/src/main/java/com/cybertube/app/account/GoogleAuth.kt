package com.cybertube.app.account

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OAuth 2.0 Device Code flow ของ Google: ไม่ใช้ Google Play Services / microG เลย
 * แอปแสดงรหัส -> ผู้ใช้ยืนยันที่ google.com/device ในเบราว์เซอร์ -> แอปได้ token แบบอ่านอย่างเดียว
 *
 * - Client ID/Secret (ประเภท "TVs and Limited Input devices") ผู้ใช้ใส่เองในหน้าตั้งค่า
 * - Refresh token เก็บในโฟลเดอร์ noBackupFilesDir (ไม่ถูกรวมใน auto-backup) ส่วน access token เก็บในหน่วยความจำเท่านั้น
 */
object GoogleAuth {
    private const val OAUTH = "https://oauth2.googleapis.com"
    private const val DEVICE_URL = OAUTH + "/device/code"
    private const val TOKEN_URL = OAUTH + "/token"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val DEFAULT_VERIFY_URL = "https://www.google.com/device"

    class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresInSec: Int,
        val intervalSec: Int,
    )

    /** [fatal] = สิทธิ์ใช้ต่อไม่ได้แล้ว ต้องเชื่อมต่อใหม่ */
    class AuthError(val code: String, message: String) : Exception(message) {
        val fatal: Boolean get() = code == "invalid_grant" || code == "no_refresh"
    }

    val clientId = mutableStateOf("")
    val clientSecret = mutableStateOf("")
    val configured: Boolean get() = clientId.value.isNotBlank() && clientSecret.value.isNotBlank()

    private lateinit var prefs: SharedPreferences
    private lateinit var tokenFile: File
    private var access: String? = null
    private var accessExpiry = 0L

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("cybertube_oauth", Context.MODE_PRIVATE)
        tokenFile = File(ctx.noBackupFilesDir, "g_refresh")
        clientId.value = prefs.getString("client_id", "") ?: ""
        clientSecret.value = prefs.getString("client_secret", "") ?: ""
    }

    fun setClient(id: String, secret: String) {
        clientId.value = id
        clientSecret.value = secret
        prefs.edit().putString("client_id", id).putString("client_secret", secret).apply()
    }

    fun hasRefreshToken(): Boolean = ::tokenFile.isInitialized && tokenFile.exists()

    fun signOut() {
        access = null
        accessExpiry = 0L
        if (::tokenFile.isInitialized) tokenFile.delete()
    }

    private suspend fun post(url: String, vararg fields: Pair<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                runCatching { JSONObject(text) }.getOrElse {
                    JSONObject().put("error", "bad_response")
                        .put("error_description", "Google ตอบกลับผิดรูปแบบ (${r.code})")
                }
            }
        }

    private fun explain(err: String, j: JSONObject): String = when (err) {
        "invalid_client", "unauthorized_client" ->
            "Client ID/Secret ไม่ถูกต้อง หรือสร้าง client ผิดประเภท (ต้องเป็น TVs and Limited Input devices)"
        "access_denied" -> "คุณปฏิเสธการให้สิทธิ์ หรืออีเมลนี้ยังไม่ได้เพิ่มเป็น Test user"
        "invalid_grant" -> "สิทธิ์ถูกเพิกถอนหรือหมดอายุ ให้เชื่อมต่อบัญชีใหม่อีกครั้ง"
        "expired_token" -> "รหัสหมดอายุ ลองเชื่อมต่อใหม่"
        else -> j.optString("error_description", err).ifEmpty { err }
    }

    suspend fun requestDeviceCode(): DeviceCode {
        val j = post(DEVICE_URL, "client_id" to clientId.value, "scope" to SCOPE)
        val err = j.optString("error", "")
        if (err.isNotEmpty()) throw AuthError(err, explain(err, j))
        return DeviceCode(
            deviceCode = j.getString("device_code"),
            userCode = j.getString("user_code"),
            verificationUrl = j.optString("verification_url", j.optString("verification_uri", DEFAULT_VERIFY_URL)),
            expiresInSec = j.optInt("expires_in", 1800),
            intervalSec = j.optInt("interval", 5),
        )
    }

    private fun store(j: JSONObject) {
        access = j.getString("access_token")
        accessExpiry = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000
        j.optString("refresh_token", "").takeIf { it.isNotEmpty() }?.let { tokenFile.writeText(it) }
    }

    /** รอจนผู้ใช้ยืนยันรหัส (ยกเลิกได้ด้วยการ cancel coroutine) สำเร็จ = เก็บ token แล้ว */
    suspend fun pollForToken(dc: DeviceCode) {
        var interval = dc.intervalSec.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val j = post(
                TOKEN_URL,
                "client_id" to clientId.value,
                "client_secret" to clientSecret.value,
                "device_code" to dc.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            )
            when (val err = j.optString("error", "")) {
                "" -> { store(j); return }
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                else -> throw AuthError(err, explain(err, j))
            }
        }
        throw AuthError("expired_token", explain("expired_token", JSONObject()))
    }

    /** access token ที่ใช้ได้ (ขอใหม่ด้วย refresh token เมื่อหมดอายุ) */
    suspend fun accessToken(): String {
        access?.let { if (System.currentTimeMillis() < accessExpiry) return it }
        val refresh = if (hasRefreshToken()) tokenFile.readText().trim() else ""
        if (refresh.isEmpty()) throw AuthError("no_refresh", "ยังไม่ได้เชื่อมต่อบัญชี Google")
        val j = post(
            TOKEN_URL,
            "client_id" to clientId.value,
            "client_secret" to clientSecret.value,
            "refresh_token" to refresh,
            "grant_type" to "refresh_token",
        )
        val err = j.optString("error", "")
        if (err.isNotEmpty()) {
            if (err == "invalid_grant") signOut()
            throw AuthError(err, explain(err, j))
        }
        store(j)
        return access!!
    }
}
