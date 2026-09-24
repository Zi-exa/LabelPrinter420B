package com.raphael.labelprinter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintContinuationTest {
    @Test
    fun printIntentResumesExactlyOnceAfterPdfSelection() {
        val continuation = PrintContinuation()

        continuation.waitForPdf()

        assertTrue(continuation.consumeAfterPdfAccepted())
        assertFalse(continuation.consumeAfterPdfAccepted())
    }
}
