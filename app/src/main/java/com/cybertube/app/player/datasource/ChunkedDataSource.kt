package com.cybertube.app.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * YouTube slows down a request that has no end ("bytes=N-"). This asks for the stream in bounded
 * pieces instead, while the player still sees one continuous stream.
 * The total size comes from the "clen" URL parameter; without it (and without an explicit length)
 * the request is passed through unchanged.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = CHUNK_SIZE,
) : DataSource {
    private var spec: DataSpec? = null // non-null only while reading in pieces
    private var total = 0L // bytes this open() has to deliver
    private var done = 0L // bytes delivered so far
    private var chunkRead = 0L // bytes delivered by the current piece

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = null
        val length = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else {
            val clen = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
            if (clen == null || clen <= dataSpec.position) return upstream.open(dataSpec)
            clen - dataSpec.position
        }
        spec = dataSpec
        total = length
        done = 0L
        openChunk(dataSpec)
        return total
    }

    private fun openChunk(s: DataSpec) {
        chunkRead = 0L
        upstream.open(s.subrange(done, minOf(chunkSize, total - done)))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val s = spec ?: return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        while (done < total) {
            val n = upstream.read(buffer, offset, minOf(length.toLong(), total - done).toInt())
            if (n != C.RESULT_END_OF_INPUT) {
                done += n
                chunkRead += n
                return n
            }
            if (chunkRead == 0L) break // the server has nothing more: do not loop forever
            upstream.close()
            openChunk(s)
        }
        return C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    companion object {
        const val CHUNK_SIZE = 10L * 1024 * 1024
    }
}
