package com.raphael.labelprinter

enum class PrintQuality(
    val displayName: String,
    val threshold: Int,
    val density: Int,
    val speed: Int,
    val useDithering: Boolean,
) {
    DRAFT("Draft - Hemat", threshold = 140, density = 6, speed = 4, useDithering = false),
    NORMAL("Normal", threshold = 160, density = 8, speed = 3, useDithering = false),
    SHARP("Tajam", threshold = 180, density = 12, speed = 2, useDithering = false),
    ULTRA("Ultra Tajam", threshold = 160, density = 12, speed = 2, useDithering = true);

    companion object {
        fun fromOrdinal(ordinal: Int): PrintQuality = entries.getOrElse(ordinal) { NORMAL }
    }
}
