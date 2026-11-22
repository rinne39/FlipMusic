package com.youflip.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlin.math.min

/**
 * googlevideo.com throttles / rejects long single-range reads. Like NewPipe, we read the file in
 * fixed-size chunks using the `range=` query parameter instead of a Range header.
 * Non-googlevideo URLs are passed straight through.
 */
class ChunkedYoutubeDataSource(private val upstream: HttpDataSource) : BaseDataSource(true) {

    private var spec: DataSpec? = null
    private var passthrough = false
    private var position = 0L
    private var endExclusive = Long.MAX_VALUE
    private var chunkEnd = 0L
    private var requestNumber = 0
    private var upstreamOpen = false
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        passthrough = dataSpec.uri.host?.endsWith("googlevideo.com") != true
        if (passthrough) {
            val len = upstream.open(dataSpec)
            upstreamOpen = true
            transferStarted(dataSpec)
            return len
        }
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        position = dataSpec.position
        endExclusive = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.position + dataSpec.length
            total != null -> total
            else -> Long.MAX_VALUE
        }
        openChunk()
        transferStarted(dataSpec)
        return if (endExclusive == Long.MAX_VALUE) C.LENGTH_UNSET.toLong() else endExclusive - position
    }

    private fun openChunk() {
        val base = spec!!
        chunkEnd = min(position + CHUNK_SIZE, endExclusive)
        val chunkUri = base.uri.buildUpon()
            .appendQueryParameter("range", "$position-${chunkEnd - 1}")
            .appendQueryParameter("rn", (requestNumber++).toString())
            .build()
        upstream.open(
            base.buildUpon()
                .setUri(chunkUri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
        )
        upstreamOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) {
            return upstream.read(buffer, offset, length).also { if (it > 0) bytesTransferred(it) }
        }
        if (position >= endExclusive) return C.RESULT_END_OF_INPUT
        var read = upstream.read(buffer, offset, min(length.toLong(), chunkEnd - position).toInt())
        if (read == C.RESULT_END_OF_INPUT || position >= chunkEnd) {
            val chunkWasShort = position < chunkEnd
            upstream.close()
            upstreamOpen = false
            if (chunkWasShort || position >= endExclusive) return C.RESULT_END_OF_INPUT
            openChunk()
            read = upstream.read(buffer, offset, min(length.toLong(), chunkEnd - position).toInt())
            if (read == C.RESULT_END_OF_INPUT) return read
        }
        position += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            if (upstreamOpen) upstream.close()
        } finally {
            upstreamOpen = false
            if (spec != null) transferEnded()
            spec = null
        }
    }

    class Factory(private val upstream: HttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedYoutubeDataSource(upstream.createDataSource())
    }

    companion object {
        private const val CHUNK_SIZE = 512L * 1024
    }
}
