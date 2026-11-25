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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.cybertube.app.core.Quality
import com.cybertube.app.core.Store
import com.cybertube.app.core.VideoDetail
import com.cybertube.app.core.VideoItem
import com.cybertube.app.core.YtRepo
import com.cybertube.app.player.datasource.YoutubeHttpDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    data class Error(val item: VideoItem, val message: String) : PState
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
        val p = ExoPlayer.Builder(ctx.applicationContext)
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
            override fun onPlayerError(error: PlaybackException) {
                val s = _state.value
                if (s is PState.Ready) {
                    _state.value = PState.Error(s.item, "Playback error: ${error.errorCodeName}")
                }
            }
        })
        exo = p
        return p
    }

    private fun ensureService(app: Context) {
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    /** Load a video (metadata + streams) and start playing it. */
    fun play(ctx: Context, item: VideoItem) {
        val app = ctx.applicationContext
        job?.cancel()
        player(app).stop()
        _state.value = PState.Loading(item)
        ensureService(app)
        Store.addHistory(item)
        job = scope.launch {
            try {
                val (detail, qs) = YtRepo.load(item.url)
                if (qs.isEmpty()) throw IllegalStateException("No playable stream found")
                val sel = YtRepo.defaultIndex(qs)
                _state.value = PState.Ready(detail.item, detail, qs, sel)
                start(app, detail, qs[sel], 0L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = PState.Error(item, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun selectQuality(ctx: Context, index: Int) {
        val s = _state.value as? PState.Ready ?: return
        if (index !in s.qualities.indices || index == s.selected) return
        val p = player(ctx)
        val pos = p.currentPosition
        _state.value = s.copy(selected = index)
        start(ctx.applicationContext, s.detail, s.qualities[index], pos)
    }

    fun clear(ctx: Context) {
        job?.cancel()
        player(ctx).apply { stop(); clearMediaItems() }
        _state.value = PState.Idle
        val app = ctx.applicationContext
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
    }

    private fun start(app: Context, d: VideoDetail, q: Quality, startMs: Long) {
        val meta = MediaMetadata.Builder()
            .setTitle(d.item.title)
            .setArtist(d.item.channel)
            .setArtworkUri(d.item.thumb?.let { Uri.parse(it) })
            .build()
        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaMetadata(meta).build()

        val progressive = ProgressiveMediaSource.Factory(
            YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(true)
        )
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
        p.setMediaSource(source, startMs)
        p.prepare()
        p.playWhenReady = true
    }
}
