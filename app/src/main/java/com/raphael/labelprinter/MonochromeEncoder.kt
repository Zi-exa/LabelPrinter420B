package com.raphael.labelprinter

object MonochromeEncoder {
    fun encode(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        threshold: Int = 160,
    ): ByteArray = encodeInternal(argbPixels, width, height, threshold, dither = false)

    fun encodeWithDithering(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        threshold: Int = 160,
    ): ByteArray = encodeInternal(argbPixels, width, height, threshold, dither = true)

    private fun encodeInternal(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        threshold: Int,
        dither: Boolean,
    ): ByteArray {
        require(width > 0 && height > 0)
        require(argbPixels.size == width * height)
        require(threshold in 0..255)

        val rowBytes = (width + 7) / 8
        val output = ByteArray(rowBytes * height)

        if (!dither) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = argbPixels[y * width + x]
                    val alpha = color ushr 24
                    if (alpha < 128) continue

                    val red = color ushr 16 and 0xff
                    val green = color ushr 8 and 0xff
                    val blue = color and 0xff
                    val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                    if (luminance < threshold) {
                        val byteIndex = y * rowBytes + x / 8
                        output[byteIndex] = (output[byteIndex].toInt() or (0x80 ushr (x % 8))).toByte()
                    }
                }
            }
            return output
        }

        // Floyd-Steinberg dithering for higher quality (ultra mode)
        val luminance = FloatArray(width * height)
        for (i in argbPixels.indices) {
            val color = argbPixels[i]
            val alpha = color ushr 24
            if (alpha < 128) {
                luminance[i] = 255f
            } else {
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                luminance[i] = (red * 299 + green * 587 + blue * 114) / 1000f
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val old = luminance[idx]
                val newVal = if (old < threshold) 0f else 255f
                val error = old - newVal

                if (newVal == 0f) {
                    val byteIndex = y * rowBytes + x / 8
                    output[byteIndex] = (output[byteIndex].toInt() or (0x80 ushr (x % 8))).toByte()
                }

                if (x + 1 < width) luminance[idx + 1] += error * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) luminance[idx + width - 1] += error * 3f / 16f
                    luminance[idx + width] += error * 5f / 16f
                    if (x + 1 < width) luminance[idx + width + 1] += error * 1f / 16f
                }
            }
        }
        return output
    }
}
