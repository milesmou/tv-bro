package com.phlox.tvwebbrowser.utils

import kotlin.math.roundToInt

object ThumbnailSize {
    // Matches the preview panel; keep one source pixel per displayed pixel, including 4K.
    const val SCREEN_FRACTION = 0.32f

    fun calculate(width: Int, height: Int, screenWidth: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && screenWidth > 0)
        val targetWidth = minOf(width, (screenWidth * SCREEN_FRACTION).roundToInt().coerceAtLeast(1))
        return targetWidth to (height.toDouble() * targetWidth / width).roundToInt().coerceAtLeast(1)
    }
}
