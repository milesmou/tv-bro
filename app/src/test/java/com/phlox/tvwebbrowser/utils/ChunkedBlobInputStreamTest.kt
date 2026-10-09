package com.phlox.tvwebbrowser.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChunkedBlobInputStreamTest {
    @Test fun appliesBackpressureAndPreservesBytes() {
        val stream = ChunkedBlobInputStream(5)
        repeat(4) { assertTrue(stream.offer(byteArrayOf(it.toByte()))) }
        assertFalse(stream.offer(byteArrayOf(4)))
        assertEquals(0, stream.read())
        assertTrue(stream.offer(byteArrayOf(4)))
        stream.finish()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), stream.readBytes())
        assertEquals(-1, stream.read())
    }

    @Test fun rejectsIncompleteOversizedAbortedAndCancelledTransfers() {
        fun fails(block: () -> Unit) {
            try { block(); fail("Expected transfer failure") } catch (_: IOException) { }
        }
        val incomplete = ChunkedBlobInputStream(2)
        incomplete.offer(byteArrayOf(1))
        incomplete.finish()
        fails { incomplete.read() }
        fails { ChunkedBlobInputStream(1).offer(byteArrayOf(1, 2)) }
        val aborted = ChunkedBlobInputStream(1)
        aborted.abort()
        fails { aborted.read() }
        val cancelled = ChunkedBlobInputStream(1)
        cancelled.cancelled = { true }
        fails { cancelled.read() }
        var closes = 0
        val closed = ChunkedBlobInputStream(0) { closes++ }
        closed.close()
        closed.close()
        assertEquals(1, closes)
        fails { closed.offer(byteArrayOf(1)) }
    }
}
