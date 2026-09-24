package com.raphael.labelprinter

internal class PrintContinuation {
    private var printAfterPdfSelection = false

    fun waitForPdf() {
        printAfterPdfSelection = true
    }

    fun consumeAfterPdfAccepted(): Boolean {
        return printAfterPdfSelection.also { printAfterPdfSelection = false }
    }

    fun cancel() {
        printAfterPdfSelection = false
    }
}
