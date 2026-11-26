package com.cybertube.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.cybertube.app.App
import com.cybertube.app.core.Quality
import com.cybertube.app.core.Store
import com.cybertube.app.core.VideoDetail
import com.cybertube.app.core.VideoItem
import com.cybertube.app.core.YtRepo
import com.cybertube.app.player.datasource.ChunkedDataSource
import com.cybertube.app.player.datasource.YoutubeHttpDataSource
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PState {
    data object Idle : PState
    data class Loading(val item: VideoItem) : PState
    data class Ready(
        val item: VideoItem,
        val detail: VideoDetail,
        val qualities: List<Quality>,
        val selected: Int,
    ) : PState
    /** [resumeMs] = where "retry" should continue from. */
    data class Error(val item: VideoItem, val message: String, val resumeMs: Long = 0L) : PState
}

/** Single app-wide player. UI, notification and background playback all share this one instance. */
object PlayerHolder {
    private var exo: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow<PState>(PState.Idle)
    val state: StateFlow<PState> = _state

    @Synchronized
    fun player(ctx: Context): ExoPlayer {
        exo?.let { return it }
        // Preload further ahead than the default (50s): keep 30-120s buffered, resume after 1.5s.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 1_500, 3_000)
            .build()
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && Store.autoplay.value) next(App.instance)
            }

            override fun onPlayerError(error: PlaybackException) {
                val s = _state.value
                if (s !is PState.Ready) return
                YtRepo.clearMemo() // stream links may have expired: fetch fresh ones on retry
                val live = s.qualities[s.selected].hls != null
                // live: fell too far behind the window -> just rejoin the live edge, links are still valid
                if (live && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition()
                    p.prepare()
                    return
                }
                val pos = if (live) 0L else p.currentPosition.coerceAtLeast(0L)
                // Live position is meaningless (always window-relative), so progress is measured by
                // wall-clock time instead; otherwise the "30 s of progress" rule fails after the 1st retry.
                val clock = if (live) android.os.SystemClock.elapsedRealtime() else pos
                if (RecoveryPolicy.canRetry(error.errorCode, clock, lastResumeMs)) {
                    lastResumeMs = clock
                    recover(App.instance, s, pos)
                } else {
                    _state.value = PState.Error(s.item, "เล่นไม่สำเร็จ (${error.errorCodeName})", pos)
                }
            }
        })
        exo = p
        return p
    }

    private fun ensureService(app: Context) {
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    // ---------- disk cache: replay / seek back without downloading again ----------
    private var cache: SimpleCache? = null
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val PURGE_EVERY_MS = 6L * 60 * 60 * 1000
    private var lastPurge = 0L

    // Own lock: opening a big cache can take a while and must never block player() on the main thread.
    private val cacheLock = Any()

    /** Opens the cache on first use (can be slow): call from a background thread. */
    fun cache(ctx: Context): SimpleCache = synchronized(cacheLock) {
        cache?.let { return@synchronized it }
        val app = ctx.applicationContext
        val c = SimpleCache(
            java.io.File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(Store.cacheMb.intValue * 1024L * 1024L),
            StandaloneDatabaseProvider(app),
        )
        cache = c
        c
    }

    suspend fun cacheBytes(ctx: Context): Long = withContext(Dispatchers.IO) { cache(ctx).cacheSpace }

    /** Delete cached videos that have not been watched for 7 days. Newer ones stay. */
    fun purgeOldCache(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPurge < PURGE_EVERY_MS) return // at most once per 6 hours
        lastPurge = now
        scope.launch(Dispatchers.IO) {
            runCatching {
                val c = cache(ctx)
                val limit = System.currentTimeMillis() - WEEK_MS
                for (key in c.keys.toList()) {
                    val spans = c.getCachedSpans(key)
                    if (spans.isNotEmpty() && spans.all { it.lastTouchTimestamp < limit }) c.removeResource(key)
                }
            }
        }
    }

    /** Remove every cached video (stops playback first so nothing is in use). */
    suspend fun clearCache(ctx: Context) {
        if (_state.value !is PState.Idle) clear(ctx)
        withContext(Dispatchers.IO) {
            runCatching {
                val c = cache(ctx)
                for (key in c.keys.toList()) c.removeResource(key)
            }
        }
    }

    /** Stable cache key (video + format) because YouTube stream URLs change every time they are fetched. */
    private fun cacheKey(videoUrl: String, streamUrl: String): String? {
        val u = runCatching { Uri.parse(streamUrl) }.getOrNull() ?: return null
        val itag = u.getQueryParameter("itag") ?: return null
        val vid = runCatching { Uri.parse(videoUrl).getQueryParameter("v") }.getOrNull()
            ?: videoUrl.substringAfterLast('/')
        val tags = u.getQueryParameter("xtags")?.let { ":$it" } ?: ""
        return "yt:$vid:$itag$tags"
    }

    /** Videos played before the current one (for "previous"). */
    private val back = ArrayDeque<VideoItem>()

    private fun currentItem(): VideoItem? = when (val s = _state.value) {
        is PState.Ready -> s.item
        is PState.Loading -> s.item
        is PState.Error -> s.item
        PState.Idle -> null
    }

    /** Next = first suggested ("up next") video we have not just played. */
    fun next(ctx: Context) {
        val s = _state.value as? PState.Ready ?: return
        val seen = back.map { it.url }.toSet() + s.item.url
        val n = s.detail.related.firstOrNull { it.url !in seen }
            ?: s.detail.related.firstOrNull { it.url != s.item.url }
            ?: return
        play(ctx, n)
    }

    /** Previous = restart if more than 5s in, otherwise go back to the last video. */
    fun previous(ctx: Context) {
        val p = player(ctx)
        if (p.currentPosition > 5_000L) { p.seekTo(0L); return }
        val prev = back.removeLastOrNull()
        if (prev == null) { p.seekTo(0L); return }
        play(ctx, prev, remember = false)
    }

    /** Where the last automatic retry of the current video resumed, null = not retried yet. */
    private var lastResumeMs: Long? = null

    /**
     * Fetch fresh stream links and continue where playback stopped, keeping the chosen quality and
     * play/pause. Not a new "play": history and the previous-video queue stay untouched.
     */
    private fun recover(app: Context, s: PState.Ready, positionMs: Long) {
        val old = s.qualities[s.selected]
        job?.cancel()
        player(app).stop()
        _state.value = PState.Loading(s.item)
        job = scope.launch {
            try {
                delay(1_500) // give a dropped connection a moment to come back
                val (detail, qs) = loadWithRetry(s.item.url)
                if (qs.isEmpty()) throw IllegalStateException("No playable stream found")
                val same = qs.indexOfFirst { it.label == old.label }
                val sel = if (same >= 0) same else YtRepo.defaultIndex(qs)
                _state.value = PState.Ready(detail.item, detail, qs, sel)
                // live streams rejoin at the live edge; playWhenReady is left as the user had it
                val startMs = if (qs[sel].hls != null) C.TIME_UNSET else positionMs
                start(app, detail, qs[sel], startMs, player(app).playWhenReady)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = PState.Error(s.item, friendlyError(e), positionMs)
            }
        }
    }

    /** Load a video (metadata + streams) and start playing it. */
    fun play(ctx: Context, item: VideoItem, remember: Boolean = true, startMs: Long = 0L) {
        val app = ctx.applicationContext
        val cur = currentItem()
        if (remember && cur != null && cur.url != item.url) {
            back.addLast(cur)
            while (back.size > 50) back.removeFirst()
        }
        job?.cancel()
        lastResumeMs = null
        player(app).stop()
        _state.value = PState.Loading(item)
        ensureService(app)
        Store.addHistory(item)
        purgeOldCache(app)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { cache(app) } // open the disk cache off the main thread
                val (detail, qs) = loadWithRetry(item.url)
                if (qs.isEmpty()) throw IllegalStateException("No playable stream found")
                val sel = YtRepo.defaultIndex(qs)
                _state.value = PState.Ready(detail.item, detail, qs, sel)
                start(app, detail, qs[sel], startMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = PState.Error(item, friendlyError(e))
            }
        }
    }

    /** YouTube sometimes refuses a request at random (bot checks / PoToken): try once more with fresh data. */
    private suspend fun loadWithRetry(url: String): Pair<VideoDetail, List<Quality>> {
        try {
            return YtRepo.load(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ContentNotAvailableException) {
            YtRepo.clearMemo()
            delay(1_500)
            return YtRepo.load(url)
        }
    }

    private fun friendlyError(e: Throwable): String {
        val raw = e.message ?: e.javaClass.simpleName
        val unavailable = raw.contains("UNPLAYABLE", ignoreCase = true) ||
            raw.contains("not available", ignoreCase = true)
        return if (unavailable)
            "YouTube ไม่ยอมให้เล่นวิดีโอนี้ อาจถูกจำกัดตามประเทศ/IP (เช่นเมื่อเปิด VPN) หรือถูกระบบตรวจจับบอทปฏิเสธ " +
                "ลองปิด VPN แล้วกดลองใหม่\n($raw)"
        else raw
    }

    fun selectQuality(ctx: Context, index: Int) {
        val s = _state.value as? PState.Ready ?: return
        if (index !in s.qualities.indices || index == s.selected) return
        val p = player(ctx)
        val pos = p.currentPosition
        _state.value = s.copy(selected = index)
        lastResumeMs = null // new stream: automatic retry may be used again
        // keep the user's play/pause state: switching quality while paused must not start playback
        start(ctx.applicationContext, s.detail, s.qualities[index], pos, p.playWhenReady)
    }

    fun clear(ctx: Context) {
        job?.cancel()
        player(ctx).apply { stop(); clearMediaItems() }
        back.clear()
        _state.value = PState.Idle
        val app = ctx.applicationContext
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
    }

    private fun start(app: Context, d: VideoDetail, q: Quality, startMs: Long, playWhenReady: Boolean = true) {
        val meta = MediaMetadata.Builder()
            .setTitle(d.item.title)
            .setArtist(d.item.channel)
            .setArtworkUri(d.item.thumb?.let { Uri.parse(it) })
            .build()
        fun item(url: String) = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(meta)
            .setCustomCacheKey(cacheKey(d.item.url, url))
            .build()

        val cached = CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(
                ChunkedDataSource.Factory(
                    YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(true)
                )
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val progressive = ProgressiveMediaSource.Factory(cached)
        val v = q.video
        val a = q.audio
        val h = q.hls
        val source: MediaSource = when {
            h != null -> HlsMediaSource.Factory(
                YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(false)
            ).createMediaSource(item(h))
            v != null && a != null -> MergingMediaSource(
                progressive.createMediaSource(item(v)),
                progressive.createMediaSource(item(a)),
            )
            v != null -> progressive.createMediaSource(item(v))
            a != null -> progressive.createMediaSource(item(a))
            else -> return
        }
        val p = player(app)
        p.setMediaSource(source, if (h != null) C.TIME_UNSET else startMs)
        p.prepare()
        p.playWhenReady = playWhenReady
    }
}
