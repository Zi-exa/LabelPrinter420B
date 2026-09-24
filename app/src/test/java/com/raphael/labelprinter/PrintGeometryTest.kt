package com.raphael.labelprinter

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintGeometryTest {
    @Test
    fun `fit centers full source page without cropping`() {
        val fitted = PrintGeometry.fit(
            sourceWidth = 298,
            sourceHeight = 420,
            targetWidth = 799,
            targetHeight = 1199,
        )

        assertEquals(0, fitted.left)
        assertEquals(36, fitted.top)
        assertEquals(799, fitted.width)
        assertEquals(1126, fitted.height)
    }
}
