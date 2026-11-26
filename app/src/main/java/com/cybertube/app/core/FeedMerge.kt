package com.cybertube.app.core

/** Merges per-channel video lists into one feed, newest first (pure logic). */
object FeedMerge {
    /**
     * [time] returns the upload time in ms, 0 = unknown (sorted last).
     * YouTube only gives rough ages ("2 weeks ago"), so many videos tie: the lists are interleaved
     * first and the sort is stable, which keeps tied videos alternating between channels.
     */
    fun <T> newestFirst(lists: List<List<T>>, time: (T) -> Long, key: (T) -> String): List<T> {
        val mixed = mutableListOf<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (l in lists) if (i < l.size) mixed += l[i]
        return mixed.distinctBy(key).sortedByDescending(time)
    }
}
