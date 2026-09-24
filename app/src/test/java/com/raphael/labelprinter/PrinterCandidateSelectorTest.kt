package com.raphael.labelprinter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterCandidateSelectorTest {
    private val devices = listOf(
        PrinterCandidate("Headset", "00:00:00:00:00:01"),
        PrinterCandidate("XP-420B", "00:00:00:00:00:42"),
    )

    @Test
    fun `saved printer address wins`() {
        val selected = PrinterCandidateSelector.select(
            candidates = devices,
            savedAddress = "00:00:00:00:00:01",
        )

        assertEquals("00:00:00:00:00:01", selected?.address)
    }

    @Test
    fun `xprinter is selected automatically when nothing is saved`() {
        val selected = PrinterCandidateSelector.select(
            candidates = devices,
            savedAddress = null,
        )

        assertEquals("00:00:00:00:00:42", selected?.address)
    }

    @Test
    fun `unrelated bluetooth devices are ignored`() {
        val selected = PrinterCandidateSelector.select(
            candidates = listOf(PrinterCandidate("Headset", "00:00:00:00:00:01")),
            savedAddress = null,
        )

        assertNull(selected)
    }

    @Test
    fun `failed saved address is not selected automatically again`() {
        val selected = PrinterCandidateSelector.select(
            candidates = devices,
            savedAddress = "00:00:00:00:00:01",
            excludedAddress = "00:00:00:00:00:01",
        )

        assertEquals("00:00:00:00:00:42", selected?.address)
    }
}
