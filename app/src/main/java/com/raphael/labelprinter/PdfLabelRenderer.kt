package com.raphael.labelprinter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.FileNotFoundException
import java.io.OutputStream

object PdfLabelRenderer {
    const val LABEL_WIDTH_DOTS = 800
    const val LABEL_HEIGHT_DOTS = 1200

    fun pageCount(context: Context, uri: Uri): Int =
        openRenderer(context, uri) { renderer -> renderer.pageCount }

    fun printAll(
        context: Context,
        uri: Uri,
        output: OutputStream,
        copies: Int = 1,
        onPage: (page: Int, total: Int) -> Unit,
    ): Int = openRenderer(context, uri) { renderer ->
        val total = renderer.pageCount
        require(total > 0) { "PDF tidak memiliki halaman" }
        for (index in 0 until total) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Print dibatalkan")
            }
            renderer.openPage(index).use { page ->
                val bitmapData = renderMonochrome(page)
                onPage(index + 1, total)
                TsplJobWriter.write(
                    output = output,
                    bitmap = bitmapData,
                    pixelWidth = LABEL_WIDTH_DOTS,
                    pixelHeight = LABEL_HEIGHT_DOTS,
                    copies = copies,
                )
            }
        }
        total
    }

    private fun renderMonochrome(page: PdfRenderer.Page): ByteArray {
        val bitmap = Bitmap.createBitmap(
            LABEL_WIDTH_DOTS,
            LABEL_HEIGHT_DOTS,
            Bitmap.Config.ARGB_8888,
        )
        return try {
            bitmap.eraseColor(Color.WHITE)
            val fitted = PrintGeometry.fit(
                sourceWidth = page.width,
                sourceHeight = page.height,
                targetWidth = LABEL_WIDTH_DOTS,
                targetHeight = LABEL_HEIGHT_DOTS,
            )
            val destination = Rect(
                fitted.left,
                fitted.top,
                fitted.left + fitted.width,
                fitted.top + fitted.height,
            )
            page.render(bitmap, destination, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            val pixels = IntArray(LABEL_WIDTH_DOTS * LABEL_HEIGHT_DOTS)
            bitmap.getPixels(
                pixels,
                0,
                LABEL_WIDTH_DOTS,
                0,
                0,
                LABEL_WIDTH_DOTS,
                LABEL_HEIGHT_DOTS,
            )
            MonochromeEncoder.encode(
                argbPixels = pixels,
                width = LABEL_WIDTH_DOTS,
                height = LABEL_HEIGHT_DOTS,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private inline fun <T> openRenderer(
        context: Context,
        uri: Uri,
        block: (PdfRenderer) -> T,
    ): T {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("PDF tidak ditemukan")
        return descriptor.use {
            PdfRenderer(it).use(block)
        }
    }
}
