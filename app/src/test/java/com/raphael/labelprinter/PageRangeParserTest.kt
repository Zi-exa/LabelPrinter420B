package com.raphael.labelprinter

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangeParserTest {
    @Test
    fun `blank returns all`() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse(null, 3))
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("", 3))
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("   ", 3))
    }

    @Test
    fun `single page`() {
        assertEquals(listOf(2), PageRangeParser.parse("2", 5))
    }

    @Test
    fun `range`() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("1-3", 5))
    }

    @Test
    fun `comma separated mixed`() {
        assertEquals(listOf(1, 3, 5, 6, 7), PageRangeParser.parse("1,3,5-7", 10))
    }

    @Test
    fun `dedup preserves order`() {
        assertEquals(listOf(3, 1, 2), PageRangeParser.parse("3,1-2,3", 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of bounds throws`() {
        PageRangeParser.parse("6", 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reversed range throws`() {
        PageRangeParser.parse("3-1", 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero throws`() {
        PageRangeParser.parse("0", 5)
    }

    @Test
    fun `spaces around`() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse(" 1 - 3 ", 5))
    }
}
