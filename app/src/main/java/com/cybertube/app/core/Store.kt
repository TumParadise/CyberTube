package com.cybertube.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class Sub(val url: String, val name: String, val avatar: String?)

/** Local-only storage: subscriptions + watch history (no account needed). */
object Store {
    private lateinit var sp: SharedPreferences
    val subs = mutableStateListOf<Sub>()
    val history = mutableStateListOf<VideoItem>()

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("cybertube", Context.MODE_PRIVATE)
        runCatching {
            val a = JSONArray(sp.getString("subs", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                subs.add(Sub(o.getString("url"), o.getString("name"), o.optString("avatar", "").ifEmpty { null }))
            }
        }
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
                    )
                )
            }
        }
    }

    fun isSubscribed(url: String?) = url != null && subs.any { it.url == url }

    fun toggleSub(url: String, name: String, avatar: String?) {
        val i = subs.indexOfFirst { it.url == url }
        if (i >= 0) subs.removeAt(i) else subs.add(Sub(url, name, avatar))
        saveSubs()
    }

    fun addHistory(v: VideoItem) {
        history.removeAll { it.url == v.url }
        history.add(0, v)
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
            )
        }
        sp.edit().putString("history", a.toString()).apply()
    }
}
