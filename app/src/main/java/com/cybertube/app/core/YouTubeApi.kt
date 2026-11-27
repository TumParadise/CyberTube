package com.cybertube.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * YouTube Data API v3 (ใช้เฉพาะตอนผู้ใช้เชื่อมบัญชีเอง) ส่วนค้นหา/เล่นวิดีโอยังใช้ YtRepo เหมือนเดิม
 * ใช้ OAuth access token แบบอ่านอย่างเดียว ไม่ต้องมี API key
 */
object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val MAX_PAGES = 20 // 20 หน้า x 50 = สูงสุด 1,000 ช่อง

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class ApiError(val code: Int, message: String) : Exception(message)

    data class Me(val id: String?, val name: String?, val avatar: String?)

    private fun get(token: String, path: String): JSONObject {
        val req = Request.Builder()
            .url(BASE + path)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }
                    .getOrNull() ?: r.message
                throw ApiError(r.code, msg)
            }
            return JSONObject(body)
        }
    }

    private fun thumb(snippet: JSONObject): String? =
        snippet.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url")
            ?.takeIf { it.isNotEmpty() }

    /** ช่องของบัญชีที่ล็อกอิน (บัญชีที่ไม่มีช่องจะได้ค่าว่างทั้งหมด) */
    suspend fun me(token: String): Me = withContext(Dispatchers.IO) {
        val items = get(token, "/channels?part=snippet&mine=true").optJSONArray("items")
        val first = items?.optJSONObject(0)
        val sn = first?.optJSONObject("snippet")
        Me(first?.optString("id")?.ifEmpty { null }, sn?.optString("title")?.ifEmpty { null }, sn?.let(::thumb))
    }

    suspend fun subscriptions(token: String): List<Sub> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Sub>()
        var page: String? = null
        var pages = 0
        do {
            val q = "/subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical" +
                (page?.let { "&pageToken=$it" } ?: "")
            val json = get(token, q)
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val sn = items.getJSONObject(i).optJSONObject("snippet") ?: continue
                    val id = sn.optJSONObject("resourceId")?.optString("channelId").orEmpty()
                    if (id.isEmpty()) continue
                    out += Sub("https://www.youtube.com/channel/$id", sn.optString("title", id), thumb(sn))
                }
            }
            page = json.optString("nextPageToken", "").ifEmpty { null }
            pages++
        } while (page != null && pages < MAX_PAGES)
        out
    }

    fun describe(e: Throwable): String = when {
        e is ApiError && e.code == 401 -> "สิทธิ์หมดอายุ ลองเชื่อมต่อใหม่อีกครั้ง"
        e is ApiError && e.code == 403 ->
            "Google ปฏิเสธ (403): ตรวจว่าเปิด YouTube Data API v3 ในโปรเจกต์แล้ว และโควตายังไม่หมด — ${e.message}"
        e is ApiError -> "YouTube API แจ้งข้อผิดพลาด (${e.code}): ${e.message}"
        e is java.io.IOException -> "เชื่อมต่ออินเทอร์เน็ตไม่ได้"
        else -> e.message ?: "เกิดข้อผิดพลาดที่ไม่ทราบสาเหตุ"
    }
}
