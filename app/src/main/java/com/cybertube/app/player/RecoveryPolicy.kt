package com.cybertube.app.player

/** Decides whether a playback error may be fixed by fetching fresh stream links (pure logic). */
object RecoveryPolicy {
    /** Playback must get this far past the last recovery point before another automatic retry. */
    const val MIN_PROGRESS_MS = 30_000L

    /** Media3 reserves 2000..2999 for I/O errors (expired link = bad HTTP status, lost network, ...). */
    fun isIoError(errorCode: Int): Boolean = errorCode in 2000..2999

    /**
     * [lastResumeMs] = where the previous automatic retry resumed this video, null = none yet.
     * If the user seeked back before the last resume point, retrying is allowed again (the old
     * check went negative and blocked retries forever). An error right at the resume point
     * (difference 0) is still refused, so a permanently broken stream cannot loop.
     */
    fun canRetry(errorCode: Int, positionMs: Long, lastResumeMs: Long?): Boolean =
        isIoError(errorCode) &&
            (lastResumeMs == null || positionMs < lastResumeMs || positionMs - lastResumeMs >= MIN_PROGRESS_MS)
}
