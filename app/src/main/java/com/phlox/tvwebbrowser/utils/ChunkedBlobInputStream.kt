package com.phlox.tvwebbrowser.utils

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Bounded bridge: JavaScript retries when the consumer has not drained the queue. */
internal class ChunkedBlobInputStream(private val expectedSize: Long, private val onClosed: () -> Unit = {}) : InputStream() {
    private val chunks = ArrayBlockingQueue<ByteArray>(4)
    private var current = ByteArray(0)
    private var position = 0
    private var offered = 0L
    @Volatile private var finished = false
    @Volatile private var closed = false
    @Volatile private var failure: String? = null
    var cancelled: () -> Boolean = { false }
    fun isAborted(): Boolean = closed || failure != null

    @Synchronized fun offer(bytes: ByteArray): Boolean {
        if (closed || finished || failure != null) throw IOException("Blob transfer closed")
        if (bytes.isEmpty() || bytes.size > 64 * 1024 || offered + bytes.size > expectedSize)
            throw IOException("Invalid blob chunk")
        if (!chunks.offer(bytes)) return false
        offered += bytes.size
        return true
    }

    @Synchronized fun finish() {
        if (offered != expectedSize) failure = "Incomplete blob transfer"
        finished = true
    }

    fun abort() { failure = "Blob transfer aborted" }

    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 255
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (true) {
            if (closed || cancelled()) throw IOException("Blob transfer cancelled")
            failure?.let { throw IOException(it) }
            if (position < current.size) {
                val count = minOf(length, current.size - position)
                current.copyInto(buffer, offset, position, position + count)
                position += count
                return count
            }
            val next = chunks.poll(100, TimeUnit.MILLISECONDS)
            if (next != null) {
                current = next
                position = 0
            } else if (finished && chunks.isEmpty()) {
                failure?.let { throw IOException(it) }
                return -1
            } else if (System.nanoTime() >= deadline) throw IOException("Blob transfer timed out")
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        chunks.clear()
        onClosed()
    }
}
