package com.raphael.labelprinter

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MonochromeEncoderTest {
    @Test
    fun `packs black dots most significant bit first`() {
        val black = 0xff000000.toInt()
        val white = 0xffffffff.toInt()
        val pixels = intArrayOf(black, white, black, white, white, white, white, black)

        val encoded = MonochromeEncoder.encode(pixels, width = 8, height = 1)

        assertArrayEquals(byteArrayOf(0xa1.toByte()), encoded)
    }

    @Test
    fun `leaves unused row bits white`() {
        val black = 0xff000000.toInt()
        val white = 0xffffffff.toInt()

        val encoded = MonochromeEncoder.encode(
            intArrayOf(black, white, black),
            width = 3,
            height = 1,
        )

        assertArrayEquals(byteArrayOf(0xa0.toByte()), encoded)
    }
}
