package com.raphael.labelprinter

import kotlin.math.roundToInt

data class FittedRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object PrintGeometry {
    fun fit(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FittedRect {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(targetWidth > 0 && targetHeight > 0)

        val scale = minOf(
            targetWidth.toDouble() / sourceWidth,
            targetHeight.toDouble() / sourceHeight,
        )
        val width = (sourceWidth * scale).roundToInt().coerceAtMost(targetWidth)
        val height = (sourceHeight * scale).roundToInt().coerceAtMost(targetHeight)
        return FittedRect(
            left = (targetWidth - width) / 2,
            top = (targetHeight - height) / 2,
            width = width,
            height = height,
        )
    }
}
