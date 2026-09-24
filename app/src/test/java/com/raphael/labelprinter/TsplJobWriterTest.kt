package com.raphael.labelprinter

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TsplJobWriterTest {
    @Test
    fun `writes label setup bitmap payload and print command`() {
        val output = ByteArrayOutputStream()

        TsplJobWriter.write(
            output = output,
            bitmap = byteArrayOf(0xa1.toByte()),
            pixelWidth = 8,
            pixelHeight = 1,
            copies = 2,
        )

        val expected = buildList<Byte> {
            addAll(
                (
                    "SIZE 100 mm,150 mm\r\n" +
                        "GAP 2 mm,0 mm\r\n" +
                        "DIRECTION 1,0\r\n" +
                        "REFERENCE 0,0\r\n" +
                        "DENSITY 8\r\n" +
                        "SPEED 3\r\n" +
                        "CLS\r\n" +
                        "BITMAP 0,0,1,1,0,"
                ).toByteArray(Charsets.US_ASCII).toList(),
            )
            add(0xa1.toByte())
            addAll("\r\nPRINT 2,1\r\n".toByteArray(Charsets.US_ASCII).toList())
        }.toByteArray()

        assertArrayEquals(expected, output.toByteArray())
    }
}
