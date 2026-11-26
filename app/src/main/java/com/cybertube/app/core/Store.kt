package com.cybertube.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.cybertube.app.ui.Cy
import com.cybertube.app.ui.Team
import org.json.JSONArray
import org.json.JSONObject

data class Sub(val url: String, val name: String, val avatar: String?)

/** Local-only storage: subscriptions + watch history (no account needed). */
object Store {
    private lateinit var sp: SharedPreferences
    val subs = mutableStateListOf<Sub>()
    val history = mutableStateListOf<VideoItem>()

    /** Preferred start quality in px height. 0 = auto (<=720p), -1 = audio only. */
    val defaultHeight = mutableIntStateOf(0)

    /** Max size of the on-disk video cache in MB (takes effect on next app start). */
    val cacheMb = mutableIntStateOf(512)

    /** Selected look ("ทีม"). The live value is Cy.team; this only persists it. */
    fun setTeam(t: Team) {
        Cy.team.value = t
        sp.edit().putString("team", t.key).apply()
    }

    fun setCacheMb(mb: Int) {
        cacheMb.intValue = mb
        sp.edit().putInt("cache_mb", mb).apply()
    }

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** History entries older than 7 days are deleted; newer ones are kept. */
    fun purgeExpiredHistory() {
        val limit = System.currentTimeMillis() - WEEK_MS
        val before = history.size
        history.removeAll { it.savedAt in 1L until limit }
        if (history.size != before) saveHistory()
    }

    /** Play the next suggested video automatically when one ends. */
    val autoplay = mutableStateOf(true)

    fun setAutoplay(on: Boolean) {
        autoplay.value = on
        sp.edit().putBoolean("autoplay", on).apply()
    }

    fun setDefaultHeight(h: Int) {
        defaultHeight.intValue = h
        sp.edit().putInt("default_height", h).apply()
    }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("cybertube", Context.MODE_PRIVATE)
        defaultHeight.intValue = sp.getInt("default_height", 0)
        autoplay.value = sp.getBoolean("autoplay", true)
        cacheMb.intValue = sp.getInt("cache_mb", 512)
        Cy.team.value = Team.fromKey(sp.getString("team", null))
        runCatching {
            val a = JSONArray(sp.getString("subs", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                subs.add(Sub(o.getString("url"), o.getString("name"), o.optString("avatar", "").ifEmpty { null }))
            }
        }
        var needSave = false // entries from older versions have no timestamp yet
        runCatching {
            val a = JSONArray(sp.getString("history", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                history.add(
                    VideoItem(
                        url = o.getString("url"),
                        title = o.getString("title"),
                        channel = o.optString("channel", ""),
                        channelUrl = o.optString("channelUrl", "").ifEmpty { null },
                        durationSec = o.optLong("dur", 0),
                        views = o.optLong("views", -1),
                        thumb = o.optString("thumb", "").ifEmpty { null },
                        uploaded = o.optString("up", "").ifEmpty { null },
                        savedAt = o.optLong("ts", 0L).let { if (it > 0) it else { needSave = true; System.currentTimeMillis() } },
                    )
                )
            }
        }
        purgeExpiredHistory()
        if (needSave) saveHistory() // persist the start date so the 7 days really count down
    }

    fun isSubscribed(url: String?) = url != null && subs.any { it.url == url }

    fun toggleSub(url: String, name: String, avatar: String?) {
        val i = subs.indexOfFirst { it.url == url }
        if (i >= 0) subs.removeAt(i) else subs.add(Sub(url, name, avatar))
        saveSubs()
    }

    fun addHistory(v: VideoItem) {
        val limit = System.currentTimeMillis() - WEEK_MS
        history.removeAll { it.url == v.url || it.savedAt in 1L until limit } // also drops expired entries
        history.add(0, v.copy(savedAt = System.currentTimeMillis()))
        while (history.size > 200) history.removeAt(history.size - 1)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun saveSubs() {
        val a = JSONArray()
        subs.forEach {
            a.put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar ?: ""))
        }
        sp.edit().putString("subs", a.toString()).apply()
    }

    private fun saveHistory() {
        val a = JSONArray()
        history.forEach {
            a.put(
                JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                    .put("channelUrl", it.channelUrl ?: "").put("dur", it.durationSec)
                    .put("views", it.views).put("thumb", it.thumb ?: "").put("up", it.uploaded ?: "")
                    .put("ts", it.savedAt)
            )
        }
        sp.edit().putString("history", a.toString()).apply()
    }
}
