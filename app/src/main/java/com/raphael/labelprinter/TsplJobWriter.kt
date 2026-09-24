package com.raphael.labelprinter

import java.io.OutputStream

object TsplJobWriter {
    fun write(
        output: OutputStream,
        bitmap: ByteArray,
        pixelWidth: Int,
        pixelHeight: Int,
        copies: Int = 1,
        density: Int = 8,
        speed: Int = 3,
    ) {
        require(pixelWidth > 0 && pixelHeight > 0)
        require(copies in 1..99)
        require(density in 0..15)
        require(speed in 1..6)

        val rowBytes = (pixelWidth + 7) / 8
        require(bitmap.size == rowBytes * pixelHeight)

        val header =
            "SIZE 100 mm,150 mm\r\n" +
                "GAP 2 mm,0 mm\r\n" +
                "DIRECTION 1,0\r\n" +
                "REFERENCE 0,0\r\n" +
                "DENSITY $density\r\n" +
                "SPEED $speed\r\n" +
                "CLS\r\n" +
                "BITMAP 0,0,$rowBytes,$pixelHeight,0,"
        output.write(header.toByteArray(Charsets.US_ASCII))

        // TSPL spec: bit 0 = printed (black), 1 = white — invert from internal 1=black
        val tsplBitmap = ByteArray(bitmap.size) { i -> (bitmap[i].toInt().inv() and 0xFF).toByte() }

        var offset = 0
        while (offset < tsplBitmap.size) {
            val count = minOf(4_096, tsplBitmap.size - offset)
            output.write(tsplBitmap, offset, count)
            offset += count
        }

        output.write("\r\nPRINT $copies,1\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }
}
