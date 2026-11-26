package com.cybertube.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cybertube.app.MainActivity
import com.cybertube.app.R

/** Keeps playback alive in the background + shows the media notification / lock-screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    /** Wraps the shared player so the notification shows previous / next and routes them to our queue. */
    private inner class QueuePlayer(p: Player) : ForwardingPlayer(p) {
        private val extra = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*extra).build()

        override fun isCommandAvailable(command: Int): Boolean =
            command in extra || super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true

        override fun seekToNext() = PlayerHolder.next(this@PlaybackService)
        override fun seekToNextMediaItem() = PlayerHolder.next(this@PlaybackService)
        override fun seekToPrevious() = PlayerHolder.previous(this@PlaybackService)
        override fun seekToPreviousMediaItem() = PlayerHolder.previous(this@PlaybackService)
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_stat_logo) }
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val s = MediaSession.Builder(this, QueuePlayer(PlayerHolder.player(this)))
            .setSessionActivity(open)
            .build()
        session = s
        addSession(s)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away: stop playing.
        PlayerHolder.clear(this)
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
