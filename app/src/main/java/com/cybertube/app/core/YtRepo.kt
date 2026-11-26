package com.cybertube.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import android.net.Uri
import androidx.core.text.HtmlCompat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val durationSec: Long,
    val views: Long,
    val thumb: String?,
    val uploaded: String?,
    val savedAt: Long = 0L, // when it entered the watch history (ms), 0 = not from history
    val uploadedAt: Long = 0L, // approximate upload time (ms) for sorting the feed, 0 = unknown
)

data class ResultPage(val items: List<VideoItem>, val next: Page?)

data class VideoDetail(
    val item: VideoItem,
    val description: String,
    val likes: Long,
    val channelAvatar: String?,
    val related: List<VideoItem>,
)

/** One playable option. video+audio = merged streams, video only = muxed, audio only = background mode. */
data class Quality(
    val label: String,
    val height: Int,
    val video: String?,
    val audio: String?,
    val hls: String?,
)

data class ChannelData(
    val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long,
    val description: String?,
    val tab: ListLinkHandler?,
    val items: List<VideoItem>,
    val next: Page?,
)

/** All YouTube access in the app goes through here (and only YouTube). */
object YtRepo {
    private val yt get() = ServiceList.YouTube

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun bestThumb(images: List<Image>?): String? =
        images?.maxByOrNull { it.height }?.url

    private fun toItem(s: StreamInfoItem) = VideoItem(
        url = s.url,
        title = s.name ?: "",
        channel = s.uploaderName ?: "",
        channelUrl = s.uploaderUrl,
        durationSec = s.duration,
        views = s.viewCount,
        thumb = bestThumb(s.thumbnails),
        uploaded = s.textualUploadDate,
        uploadedAt = if (s.streamType == StreamType.LIVE_STREAM || s.streamType == StreamType.AUDIO_LIVE_STREAM)
            System.currentTimeMillis() // live now counts as newest
        else s.uploadDate?.instant?.toEpochMilli() ?: 0L,
    )

    private fun streamItems(list: List<Any?>) =
        list.filterIsInstance<StreamInfoItem>().map(::toItem).distinctBy { it.url } // LazyColumn keys must be unique

    // ---------- search ----------
    private fun searchHandler(q: String) = yt.searchQHFactory.fromQuery(q, listOf("videos"), "")

    suspend fun search(q: String): ResultPage = io {
        val info = SearchInfo.getInfo(yt, searchHandler(q))
        ResultPage(streamItems(info.relatedItems), info.nextPage)
    }

    suspend fun searchMore(q: String, page: Page): ResultPage = io {
        val p = SearchInfo.getMoreItems(yt, searchHandler(q), page)
        ResultPage(streamItems(p.items), p.nextPage)
    }

    suspend fun suggestions(q: String): List<String> = io {
        yt.suggestionExtractor.suggestionList(q) ?: emptyList()
    }

    // ---------- video ----------
    // Re-opening a video within 30 min reuses the extractor result (no new requests / PoToken work).
    private val memo = object : LinkedHashMap<String, Pair<Long, Pair<VideoDetail, List<Quality>>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Pair<VideoDetail, List<Quality>>>>?) =
            size > 20
    }

    fun clearMemo() = synchronized(memo) { memo.clear() }

    suspend fun load(url: String): Pair<VideoDetail, List<Quality>> {
        val now = System.currentTimeMillis()
        synchronized(memo) { memo[url] }?.let { if (now - it.first < 30 * 60_000L) return it.second }
        val r = loadFresh(url)
        synchronized(memo) { memo[url] = now to r }
        return r
    }

    private suspend fun loadFresh(url: String): Pair<VideoDetail, List<Quality>> = io {
        val info = StreamInfo.getInfo(yt, url)
        val item = VideoItem(
            url = info.url ?: url,
            title = info.name ?: "",
            channel = info.uploaderName ?: "",
            channelUrl = info.uploaderUrl,
            durationSec = info.duration,
            views = info.viewCount,
            thumb = bestThumb(info.thumbnails),
            uploaded = info.textualUploadDate,
        )
        val detail = VideoDetail(
            item = item,
            description = cleanDescription(info.description),
            likes = info.likeCount,
            channelAvatar = bestThumb(info.uploaderAvatars),
            related = streamItems(info.relatedItems ?: emptyList()),
        )
        detail to buildQualities(info)
    }

    private fun isDefaultAudioTrack(s: AudioStream): Boolean {
        val tags = runCatching { Uri.parse(s.content).getQueryParameter("xtags") }.getOrNull()
            ?.lowercase() ?: return true // no xtags = the normal track
        val extra = listOf("dubbed", "descriptive", "secondary", "drc=1")
        return extra.none { tags.contains(it) }
    }

    private fun buildQualities(info: StreamInfo): List<Quality> {
        val progressive = { s: Stream -> s.isUrl && s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // A video can carry several audio tracks (original, dubbed, descriptive, "stable volume" copies),
        // marked in the stream URL by the xtags parameter. Only the default track may be played,
        // otherwise a second track can sound on top of the real audio.
        val allAudios = info.audioStreams.filter(progressive)
        val audios = allAudios.filter(::isDefaultAudioTrack).ifEmpty { allAudios }

        fun bestAudio(mp4: Boolean): AudioStream? =
            audios.filter { (it.format == MediaFormat.M4A) == mp4 }.maxByOrNull { it.averageBitrate }
                ?: audios.maxByOrNull { it.averageBitrate }

        fun pick(list: List<VideoStream>): List<VideoStream> =
            list.groupBy { it.resolution }.map { (_, g) ->
                g.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: g.first()
            }

        val out = mutableListOf<Quality>()

        // High quality: video-only + best compatible audio (merged at playback)
        val merged = pick(info.videoOnlyStreams.filter(progressive)).sortedByDescending { it.height }
        val mergedQs = merged.mapNotNull { v ->
            val a = bestAudio(v.format == MediaFormat.MPEG_4) ?: return@mapNotNull null
            Quality(v.resolution ?: "${v.height}p", v.height, v.content, a.content, null)
        }
        // Classic muxed streams (360p/720p) that merged list doesn't already cover
        val coveredHeights = mergedQs.map { it.height }.toSet()
        val muxedQs = pick(info.videoStreams.filter(progressive))
            .filter { it.height !in coveredHeights }
            .map { Quality(it.resolution ?: "${it.height}p", it.height, it.content, null, null) }

        out += (mergedQs + muxedQs).sortedByDescending { it.height }

        if (out.isEmpty() && !info.hlsUrl.isNullOrEmpty()) {
            out += Quality("ถ่ายทอดสด (HLS)", 0, null, null, info.hlsUrl)
        }
        bestAudio(true)?.let { out += Quality("เสียงอย่างเดียว", 0, null, it.content, null) }
        return out
    }

    /** Start quality from Settings: best option <= preferred height (0 = auto 720p, -1 = audio only). */
    fun defaultIndex(qs: List<Quality>): Int {
        val pref = Store.defaultHeight.intValue
        if (pref == -1) {
            val a = qs.indexOfFirst { it.video == null && it.hls == null }
            if (a >= 0) return a
        }
        val target = if (pref > 0) pref else 720
        val vids = qs.withIndex().filter { it.value.height > 0 } // already sorted high -> low
        vids.firstOrNull { it.value.height <= target }?.let { return it.index }
        vids.lastOrNull()?.let { return it.index }
        return 0
    }

    private fun cleanDescription(d: Description?): String {
        if (d == null) return ""
        val raw = d.content ?: return ""
        // type is an int constant (1 = HTML) or an enum depending on extractor version: compare by string
        val t = d.type.toString()
        val isHtml = t == "1" || t.contains("HTML", ignoreCase = true)
        val text = if (isHtml)
            HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        else raw
        return text.replace(Regex("</?a[^>]*>"), "").replace("&amp;", "&").trim()
    }

    // ---------- channel ----------
    suspend fun channel(url: String): ChannelData = io {
        val ci = ChannelInfo.getInfo(yt, url)
        val tab = ci.tabs.firstOrNull { it.contentFilters.firstOrNull() == ChannelTabs.VIDEOS }
        var items = emptyList<VideoItem>()
        var next: Page? = null
        if (tab != null) {
            val ti = ChannelTabInfo.getInfo(yt, tab)
            items = streamItems(ti.relatedItems)
            next = ti.nextPage
        }
        ChannelData(
            url = ci.url ?: url,
            name = ci.name ?: "",
            avatar = bestThumb(ci.avatars),
            subscribers = ci.subscriberCount,
            description = ci.description,
            tab = tab,
            items = items,
            next = next,
        )
    }

    suspend fun channelMore(tab: ListLinkHandler, page: Page): ResultPage = io {
        val p = ChannelTabInfo.getMoreItems(yt, tab, page)
        ResultPage(streamItems(p.items), p.nextPage)
    }
}
