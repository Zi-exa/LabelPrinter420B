package com.raphael.labelprinter

object MonochromeEncoder {
    fun encode(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        threshold: Int = 160,
    ): ByteArray {
        require(width > 0 && height > 0)
        require(argbPixels.size == width * height)
        require(threshold in 0..255)

        val rowBytes = (width + 7) / 8
        val output = ByteArray(rowBytes * height)
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
}
