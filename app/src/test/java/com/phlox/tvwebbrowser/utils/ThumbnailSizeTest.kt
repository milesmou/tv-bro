package com.phlox.tvwebbrowser.utils

import org.junit.Assert.*
import org.junit.Test

class ThumbnailSizeTest {
    @Test fun fourKPreviewHasEnoughPixelsWithoutFullScreenAllocation() {
        val (width, height) = ThumbnailSize.calculate(3840, 2160, 3840)
        assertEquals(1229, width)
        assertEquals(691, height)
        assertTrue(width.toLong() * height * 2 < 2 * 1024 * 1024)
        assertEquals(16.0 / 9, width.toDouble() / height, 0.003)
    }

    @Test fun previewAdaptsToResolutionAndNeverUpscalesSource() {
        assertEquals(614 to 345, ThumbnailSize.calculate(1920, 1080, 1920))
        assertEquals(400 to 300, ThumbnailSize.calculate(400, 300, 3840))
    }
}
